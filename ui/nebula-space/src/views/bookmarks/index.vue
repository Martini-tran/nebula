<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import SpaceSidebar from './components/SpaceSidebar.vue'
import BookmarkCard from './components/BookmarkCard.vue'
import BookmarkDialog from './components/BookmarkDialog.vue'
import NameDialog from './components/NameDialog.vue'
import StateBlock from '../../components/StateBlock.vue'
import {
  createFolder,
  createTag,
  deleteBookmark,
  deleteFolder,
  deleteTag,
  exportChromeBookmarks,
  fetchBookmarks,
  importChromeBookmarks,
  renameFolder,
  updateBookmarkStatus,
} from '../../api/space'
import { useSpaceStore } from '../../stores/space'
import {
  BookmarkStatus,
  type Bookmark,
  type BookmarkPageQuery,
  type EntityId,
  type ExportScope,
  type Folder,
  type SpaceTag,
} from '../../types/space'
import { formatCount } from '../../utils/format'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { isSameFilter, type SpaceFilter } from './filter'

const PAGE_SIZE = 24
/** 新建标签时轮流取色，免去让用户挑颜色 */
const TAG_COLORS = ['#4f46e5', '#0d9488', '#d97706', '#db2777', '#2563eb', '#65a30d', '#7c3aed', '#dc2626']

const space = useSpaceStore()

const filter = ref<SpaceFilter>({ kind: 'all' })
const keyword = ref('')
const page = ref(1)

const bookmarks = ref<Bookmark[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

const heading = computed(() => {
  const f = filter.value
  switch (f.kind) {
    case 'all':
      return '全部书签'
    case 'uncategorized':
      return '未分类'
    case 'archived':
      return '已归档'
    case 'folder':
      return space.findFolder(f.id)?.name ?? '目录'
    case 'tag':
      return `# ${space.tags.find((tag) => String(tag.id) === String(f.id))?.name ?? '标签'}`
  }
  return ''
})

/** 侧栏选择 → 查询条件；除「已归档」外只看正常状态的书签 */
const buildQuery = (): BookmarkPageQuery => {
  const query: BookmarkPageQuery = {
    pageNum: page.value,
    pageSize: PAGE_SIZE,
    keyword: keyword.value.trim() || undefined,
    status: BookmarkStatus.NORMAL,
  }
  const f = filter.value
  if (f.kind === 'uncategorized') query.folderId = 0
  if (f.kind === 'archived') query.status = BookmarkStatus.ARCHIVED
  if (f.kind === 'folder') query.folderId = f.id
  if (f.kind === 'tag') query.tagId = f.id
  return query
}

/** 快速切换筛选时丢弃过期响应，避免旧结果覆盖新结果 */
let requestSeq = 0

const loadBookmarks = async () => {
  const seq = ++requestSeq
  loading.value = true
  loadError.value = ''
  try {
    const result = await fetchBookmarks(buildQuery())
    if (seq !== requestSeq) return
    bookmarks.value = result?.records ?? []
    total.value = Number(result?.total ?? 0)
  } catch (error) {
    if (seq !== requestSeq) return
    loadError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

const selectFilter = (next: SpaceFilter) => {
  if (isSameFilter(filter.value, next)) return
  filter.value = next
  page.value = 1
  loadBookmarks()
}

let keywordTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    page.value = 1
    loadBookmarks()
  }, 300)
})

const goPage = (next: number) => {
  page.value = Math.min(Math.max(1, next), totalPages.value)
  loadBookmarks()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ── 书签 ──
const bookmarkDialogOpen = ref(false)
const editingBookmark = ref<Bookmark | null>(null)

const defaultFolderId = computed<EntityId | undefined>(() =>
  filter.value.kind === 'folder' ? filter.value.id : undefined,
)

const openCreateBookmark = () => {
  editingBookmark.value = null
  bookmarkDialogOpen.value = true
}

const openEditBookmark = (bookmark: Bookmark) => {
  editingBookmark.value = bookmark
  bookmarkDialogOpen.value = true
}

const onBookmarkSaved = () => {
  bookmarkDialogOpen.value = false
  toast.ok(editingBookmark.value ? '书签已更新' : '书签已添加')
  loadBookmarks()
}

const toggleArchive = async (bookmark: Bookmark) => {
  const archived = bookmark.status === BookmarkStatus.ARCHIVED
  try {
    await updateBookmarkStatus(bookmark.id, archived ? BookmarkStatus.NORMAL : BookmarkStatus.ARCHIVED)
    toast.ok(archived ? '已恢复' : '已归档')
    loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const removeBookmark = async (bookmark: Bookmark) => {
  const ok = await confirm({
    title: '删除书签',
    message: `「${bookmark.title}」及其标签关联会被删除，无法撤销。
只是暂时不看的话，可以改为归档。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteBookmark(bookmark.id)
    toast.ok('已删除')
    // 删掉本页最后一条时回退一页
    if (bookmarks.value.length === 1 && page.value > 1) page.value -= 1
    loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

// ── 目录 / 标签：共用命名弹窗 ──
const nameDialog = ref<{
  open: boolean
  title: string
  label: string
  initial: string
  save: (name: string) => Promise<void>
}>({ open: false, title: '', label: '', initial: '', save: async () => {} })

const closeNameDialog = () => {
  nameDialog.value.open = false
}

const openCreateFolder = (parent: Folder | null) => {
  nameDialog.value = {
    open: true,
    title: parent ? `在「${parent.name}」下新建目录` : '新建目录',
    label: '目录名称',
    initial: '',
    save: async (name) => {
      await createFolder({ parentId: parent?.id ?? 0, name })
      closeNameDialog()
      await space.reload()
    },
  }
}

const openRenameFolder = (folder: Folder) => {
  nameDialog.value = {
    open: true,
    title: '重命名目录',
    label: '目录名称',
    initial: folder.name,
    save: async (name) => {
      await renameFolder(folder.id, name)
      closeNameDialog()
      await space.reload()
    },
  }
}

const removeFolder = async (folder: Folder) => {
  const ok = await confirm({
    title: '删除目录',
    message: `删除「${folder.name}」？
目录里还有子目录或书签时无法删除，请先移走。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteFolder(folder.id)
    await space.reload()
    if (filter.value.kind === 'folder' && String(filter.value.id) === String(folder.id)) {
      selectFilter({ kind: 'all' })
    } else {
      loadBookmarks()
    }
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const openCreateTag = () => {
  nameDialog.value = {
    open: true,
    title: '新建标签',
    label: '标签名称',
    initial: '',
    save: async (name) => {
      const color = TAG_COLORS[space.tags.length % TAG_COLORS.length]
      await createTag({ name, color })
      closeNameDialog()
      await space.reload()
    },
  }
}

const removeTag = async (tag: SpaceTag) => {
  const ok = await confirm({
    title: '删除标签',
    message: `删除「${tag.name}」？书签本身不会被删除，只是去掉这个标签。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteTag(tag.id)
    await space.reload()
    if (filter.value.kind === 'tag' && String(filter.value.id) === String(tag.id)) {
      selectFilter({ kind: 'all' })
    } else {
      loadBookmarks()
    }
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

// ── 导入导出 ──
const fileInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const exporting = ref(false)

const onImportFile = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const task = await importChromeBookmarks(file)
    toast.ok(
      `导入完成：新增 ${task?.successCount ?? 0}，重复 ${task?.duplicateCount ?? 0}，失败 ${task?.failCount ?? 0}`,
    )
    await space.reload()
    loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '导入失败'))
  } finally {
    importing.value = false
  }
}

/** 导出范围跟随侧栏：选中目录/标签就只导出它 */
const onExport = async () => {
  const f = filter.value
  const scope: ExportScope =
    f.kind === 'folder' || f.kind === 'tag' ? { scopeType: f.kind, scopeId: f.id } : { scopeType: 'all' }
  exporting.value = true
  try {
    await exportChromeBookmarks(scope)
  } catch (error) {
    toast.error(errorText(error, '导出失败'))
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  space.reload()
  loadBookmarks()
})

onBeforeUnmount(() => {
  clearTimeout(keywordTimer)
})
</script>

<template>
  <div class="space page">
    <SpaceSidebar
      class="space__side"
      :active="filter"
      @select="selectFilter"
      @create-folder="openCreateFolder"
      @rename-folder="openRenameFolder"
      @remove-folder="removeFolder"
      @create-tag="openCreateTag"
      @remove-tag="removeTag"
    />

    <section class="space__main">
      <header class="toolbar">
        <div class="toolbar__title">
          <h1 class="page-title">{{ heading }}</h1>
          <span class="tag">{{ formatCount(total) }}</span>
        </div>
        <div class="toolbar__actions">
          <label class="search">
            <Icon icon="lucide:search" class="search__icon" />
            <input v-model="keyword" type="search" placeholder="搜索标题、网址、描述" aria-label="搜索书签" />
          </label>
          <button class="btn btn--ghost" type="button" :disabled="importing" title="导入 Chrome 书签 HTML" @click="fileInput?.click()">
            <Icon :icon="importing ? 'lucide:loader-circle' : 'lucide:upload'" :class="{ spin: importing }" />
            <span class="btn__text">导入</span>
          </button>
          <button class="btn btn--ghost" type="button" :disabled="exporting" title="导出为 Chrome 书签 HTML" @click="onExport">
            <Icon :icon="exporting ? 'lucide:loader-circle' : 'lucide:download'" :class="{ spin: exporting }" />
            <span class="btn__text">导出</span>
          </button>
          <button class="btn btn--primary" type="button" @click="openCreateBookmark">
            <Icon icon="lucide:plus" />
            <span class="btn__text">添加书签</span>
          </button>
          <input ref="fileInput" type="file" accept=".html,.htm,text/html" hidden @change="onImportFile" />
        </div>
      </header>

      <StateBlock v-if="loading && !bookmarks.length" state="loading" />
      <StateBlock
        v-else-if="loadError"
        state="error"
        :description="loadError"
        action-label="重试"
        @action="loadBookmarks"
      />
      <StateBlock
        v-else-if="!bookmarks.length"
        state="empty"
        :title="keyword ? '没有匹配的书签' : '这里还没有书签'"
        :description="keyword ? '换个关键词试试' : '添加一个网址，或导入浏览器书签'"
        :action-label="keyword ? '' : '添加书签'"
        @action="openCreateBookmark"
      />
      <template v-else>
        <div class="grid" :class="{ 'grid--loading': loading }">
          <BookmarkCard
            v-for="bookmark in bookmarks"
            :key="bookmark.id"
            :bookmark="bookmark"
            @edit="openEditBookmark(bookmark)"
            @toggle-archive="toggleArchive(bookmark)"
            @remove="removeBookmark(bookmark)"
          />
        </div>
        <nav v-if="totalPages > 1" class="pager" aria-label="分页">
          <button class="btn btn--ghost" type="button" :disabled="page <= 1" @click="goPage(page - 1)">
            <Icon icon="lucide:chevron-left" />上一页
          </button>
          <span class="pager__info">{{ page }} / {{ totalPages }}</span>
          <button class="btn btn--ghost" type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">
            下一页<Icon icon="lucide:chevron-right" />
          </button>
        </nav>
      </template>
    </section>

    <BookmarkDialog
      :open="bookmarkDialogOpen"
      :bookmark="editingBookmark"
      :default-folder-id="defaultFolderId"
      @close="bookmarkDialogOpen = false"
      @saved="onBookmarkSaved"
    />
    <NameDialog
      :open="nameDialog.open"
      :title="nameDialog.title"
      :label="nameDialog.label"
      :initial="nameDialog.initial"
      :save="nameDialog.save"
      @close="closeNameDialog"
    />
  </div>
</template>

<style scoped lang="scss">
.space {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr);
  gap: 2rem;
  align-items: start;
}

.space__side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  max-height: calc(100vh - var(--header-height) - 2.5rem);
  overflow-y: auto;
}

.space__main {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  min-width: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
}

.toolbar__title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.toolbar__title .page-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.toolbar__actions svg {
  width: 1.05rem;
  height: 1.05rem;
}

.search {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 16rem;
  padding: 0 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.search:focus-within {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.search__icon {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}

.search input {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0;
  border: 0;
  outline: none;
  background: none;
}


.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 0.85rem;
  transition: opacity 0.2s ease;
}

.grid--loading {
  opacity: 0.55;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding-top: 0.5rem;
}

.pager__info {
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

@media (max-width: 900px) {
  .space {
    grid-template-columns: 1fr;
    gap: 1.25rem;
  }

  .space__side {
    position: static;
    max-height: none;
  }

  .search {
    width: 100%;
    order: -1;
  }

  .toolbar__actions {
    width: 100%;
  }
}

@media (max-width: 520px) {
  .btn__text {
    display: none;
  }
}
</style>
