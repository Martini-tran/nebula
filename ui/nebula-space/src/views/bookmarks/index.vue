<script setup lang="ts">
/**
 * 书签工作台：左栏决定「看哪些」，右侧决定「怎么看、怎么动」。
 * 筛选同步到地址栏（?folder= / ?tag= / ?view=），刷新或分享链接停在同一处。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import SpaceSidebar from './components/SpaceSidebar.vue'
import BookmarkCard from './components/BookmarkCard.vue'
import BookmarkTable from './components/BookmarkTable.vue'
import BookmarkDialog from './components/BookmarkDialog.vue'
import BookmarkDrawer from './components/BookmarkDrawer.vue'
import BulkBar from './components/BulkBar.vue'
import BulkTagDialog from './components/BulkTagDialog.vue'
import ExportDialog from './components/ExportDialog.vue'
import FolderDeleteDialog from './components/FolderDeleteDialog.vue'
import FolderPickerDialog from './components/FolderPickerDialog.vue'
import ImportDialog from './components/ImportDialog.vue'
import LinkCheckDialog from './components/LinkCheckDialog.vue'
import NameDialog from './components/NameDialog.vue'
import QuickAdd from './components/QuickAdd.vue'
import StateBlock from '../../components/StateBlock.vue'
import {
  batchDeleteBookmarks,
  createFolder,
  createTag,
  deleteBookmark,
  deleteTag,
  fetchAllBookmarks,
  fetchBookmark,
  fetchBookmarks,
  moveBookmarks,
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
  type ImportTask,
  type SpaceTag,
} from '../../types/space'
import { formatCount } from '../../utils/format'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { filterFromQuery, filterToQuery, isSameFilter, type SpaceFilter } from './filter'
import { nextTagColor } from './tagColors'

const PAGE_SIZE = 24
const VIEW_KEY = 'nebula-space:bookmark-view'

const route = useRoute()
const router = useRouter()
const space = useSpaceStore()

const filter = ref<SpaceFilter>(filterFromQuery(route.query))
const keyword = ref('')
const page = ref(1)

const bookmarks = ref<Bookmark[]>([])
const total = ref(0)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

const readView = (): 'grid' | 'list' => {
  try {
    return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}
const viewMode = ref<'grid' | 'list'>(readView())
watch(viewMode, (mode) => {
  try {
    localStorage.setItem(VIEW_KEY, mode)
  } catch {
    // 记不住就算了
  }
})

/** 窄屏下侧栏收起，点「筛选」展开 */
const sideOpen = ref(false)

// ── 列表 ──

/** 已点删除、还在 5 秒撤销期内的书签：先从界面隐藏，到时才真正删除 */
const hiddenIds = ref(new Set<string>())
const visible = computed(() => bookmarks.value.filter((b) => !hiddenIds.value.has(String(b.id))))
const shownTotal = computed(() => Math.max(0, total.value - (bookmarks.value.length - visible.value.length)))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

const currentFolder = computed(() => (filter.value.kind === 'folder' ? space.findFolder(filter.value.id) : undefined))
/** 目录只列它自己这一层的书签，子目录用 chip 列在上方，免得父目录看起来是空的 */
const childFolders = computed(() => currentFolder.value?.children ?? [])

const currentTag = computed(() => (filter.value.kind === 'tag' ? space.findTag(filter.value.id) : undefined))

const heading = computed(() => {
  switch (filter.value.kind) {
    case 'all':
      return '全部书签'
    case 'uncategorized':
      return '未分类'
    case 'archived':
      return '已归档'
    case 'broken':
      return '失效链接'
    case 'folder':
      return currentFolder.value?.name ?? '目录'
    case 'tag':
      return `# ${currentTag.value?.name ?? '标签'}`
  }
  return ''
})

/** 标题上方的小字：目录显示上级路径，特殊视图给一句说明 */
const subheading = computed(() => {
  const f = filter.value
  if (f.kind === 'folder') {
    const path = space.folderPath(f.id).split(' / ')
    return path.length > 1 ? path.slice(0, -1).join(' / ') : ''
  }
  if (f.kind === 'uncategorized') return '导入后或快速收藏时尚未归入目录的书签'
  if (f.kind === 'archived') return '归档的书签不出现在其他视图，也不会被导出'
  if (f.kind === 'broken') return '链接检查发现打不开的书签；站点可能只是临时维护，确认后再处理'
  return ''
})

/** 侧栏选择 → 查询条件；「全部 / 目录 / 标签 / 未分类」只看正常状态的书签 */
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
  if (f.kind === 'broken') query.status = BookmarkStatus.BROKEN
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
    // 删完本页最后几条后，退回有数据的那一页
    if (!bookmarks.value.length && page.value > 1 && total.value > 0) {
      page.value = Math.ceil(total.value / PAGE_SIZE)
      return loadBookmarks()
    }
  } catch (error) {
    if (seq !== requestSeq) return
    loadError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      loaded.value = true
    }
  }
}

const selectFilter = (next: SpaceFilter) => {
  sideOpen.value = false
  if (isSameFilter(filter.value, next)) return
  router.push({ query: filterToQuery(next) })
}

// 地址栏是筛选的唯一来源：侧栏点击、抽屉里点目录、浏览器前进后退都走这里
watch(
  () => route.query,
  (query) => {
    const next = filterFromQuery(query)
    if (isSameFilter(filter.value, next)) return
    filter.value = next
    page.value = 1
    clearSelection()
    loadBookmarks()
  },
)

let keywordTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    page.value = 1
    clearSelection()
    loadBookmarks()
  }, 300)
})

const goPage = (next: number) => {
  page.value = Math.min(Math.max(1, next), totalPages.value)
  clearSelection()
  loadBookmarks()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** 局部刷新：列表 + 抽屉里那条 */
const refresh = async () => {
  await loadBookmarks()
  if (drawerBookmark.value) {
    try {
      drawerBookmark.value = await fetchBookmark(drawerBookmark.value.id)
    } catch {
      drawerBookmark.value = null
    }
  }
}

// ── 选择 ──

const selectedIds = ref(new Set<string>())
const lastSelectedIndex = ref(-1)
const selectedBookmarks = computed(() => visible.value.filter((b) => selectedIds.value.has(String(b.id))))

const toggleSelect = (bookmark: Bookmark, range: boolean) => {
  const index = visible.value.findIndex((b) => String(b.id) === String(bookmark.id))
  const next = new Set(selectedIds.value)
  if (range && lastSelectedIndex.value >= 0) {
    // Shift 连选：把上次点的和这次点的之间全部选上
    const [from, to] = [lastSelectedIndex.value, index].sort((a, b) => a - b)
    visible.value.slice(from, to + 1).forEach((b) => next.add(String(b.id)))
  } else if (next.has(String(bookmark.id))) {
    next.delete(String(bookmark.id))
  } else {
    next.add(String(bookmark.id))
  }
  selectedIds.value = next
  lastSelectedIndex.value = index
}

const selectPage = () => {
  selectedIds.value = new Set(visible.value.map((b) => String(b.id)))
}

const toggleAll = () => {
  if (selectedBookmarks.value.length === visible.value.length) clearSelection()
  else selectPage()
}

function clearSelection() {
  selectedIds.value = new Set()
  lastSelectedIndex.value = -1
}

// ── 抽屉 ──

const drawerBookmark = ref<Bookmark | null>(null)
const openDrawer = (bookmark: Bookmark) => (drawerBookmark.value = bookmark)

// ── 新建 / 编辑 ──

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

const onBookmarkSaved = (message: string) => {
  bookmarkDialogOpen.value = false
  toast.ok(message)
  refresh()
}

const onOpenExisting = (bookmark: Bookmark) => {
  bookmarkDialogOpen.value = false
  openDrawer(bookmark)
}

const quickFolderId = computed<EntityId>(() => (filter.value.kind === 'folder' ? filter.value.id : 0))
const quickFolderLabel = computed(() => {
  if (filter.value.kind === 'folder') return currentFolder.value?.name ?? '目录'
  if (filter.value.kind === 'tag') return `未分类 · #${currentTag.value?.name ?? ''}`
  return '未分类'
})
const showQuickAdd = computed(() => filter.value.kind !== 'archived' && filter.value.kind !== 'broken')

const onQuickAdded = () => {
  toast.ok('已收藏，正在取网页标题…')
  page.value = 1
  loadBookmarks()
}

/** 服务端抓完网页：取到标题就刷新列表；取不到提示去补 */
const onQuickFilled = (bookmark: Bookmark, got: boolean) => {
  if (got) {
    loadBookmarks()
    return
  }
  toast.info('没取到网页标题，标题先用了域名', {
    action: {
      label: '补充信息',
      run: async () => {
        try {
          openEditBookmark(await fetchBookmark(bookmark.id))
        } catch (error) {
          toast.error(errorText(error, '读取书签失败'))
        }
      },
    },
  })
}

// ── 移动 ──

const moveState = ref<{ ids: EntityId[]; currentId: EntityId | null; title: string } | null>(null)
const moving = ref(false)

const openMove = (items: Bookmark[]) => {
  if (!items.length) return
  const folders = new Set(items.map((b) => String(b.folderId)))
  moveState.value = {
    ids: items.map((b) => b.id),
    // 全都在同一个目录里时，把它标成「当前位置」
    currentId: folders.size === 1 ? items[0]!.folderId : null,
    title: items.length === 1 ? `移动「${items[0]!.title}」到…` : `移动 ${items.length} 条书签到…`,
  }
}

const onPickFolder = async (targetId: EntityId, label: string) => {
  if (!moveState.value) return
  moving.value = true
  try {
    const moved = await moveBookmarks(moveState.value.ids, targetId)
    if (String(targetId) !== '0') space.rememberFolder(targetId)
    toast.ok(`已移动 ${moved ?? moveState.value.ids.length} 条到「${label.split(' / ').pop()}」`)
    moveState.value = null
    clearSelection()
    refresh()
  } catch (error) {
    toast.error(errorText(error, '移动失败'))
  } finally {
    moving.value = false
  }
}

// ── 归档 ──

const setStatus = async (items: Bookmark[], status: 0 | 1) => {
  let failed = 0
  // 后端没有批量改状态接口，逐条提交，每次 5 条并发
  for (let i = 0; i < items.length; i += 5) {
    const results = await Promise.allSettled(items.slice(i, i + 5).map((b) => updateBookmarkStatus(b.id, status)))
    failed += results.filter((r) => r.status === 'rejected').length
  }
  return failed
}

const toggleArchive = async (bookmark: Bookmark) => {
  const archived = bookmark.status === BookmarkStatus.ARCHIVED
  const next = archived ? BookmarkStatus.NORMAL : BookmarkStatus.ARCHIVED
  const previous = bookmark.status
  try {
    await updateBookmarkStatus(bookmark.id, next)
    toast.ok(archived ? '已恢复' : '已归档', {
      action: {
        label: '撤销',
        run: async () => {
          await updateBookmarkStatus(bookmark.id, previous)
          refresh()
        },
      },
    })
    refresh()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const bulkBusy = ref(false)

const bulkArchive = async () => {
  const items = selectedBookmarks.value
  const restoring = filter.value.kind === 'archived'
  bulkBusy.value = true
  const failed = await setStatus(items, restoring ? BookmarkStatus.NORMAL : BookmarkStatus.ARCHIVED)
  bulkBusy.value = false
  const done = items.length - failed
  if (failed) toast.error(`${done} 条已${restoring ? '恢复' : '归档'}，${failed} 条失败`)
  else toast.ok(`已${restoring ? '恢复' : '归档'} ${done} 条`)
  clearSelection()
  refresh()
}

// ── 打标签 ──

const bulkTagOpen = ref(false)
const onBulkTagged = (changed: number, failed: number) => {
  bulkTagOpen.value = false
  if (failed) toast.error(`${changed} 条已更新，${failed} 条失败`)
  else toast.ok(`已更新 ${changed} 条书签的标签`)
  refresh()
}

// ── 删除 ──

const pendingDeletes = new Map<string, ReturnType<typeof setTimeout>>()

const commitDelete = async (id: string) => {
  pendingDeletes.delete(id)
  try {
    await deleteBookmark(id)
    await loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  } finally {
    const next = new Set(hiddenIds.value)
    next.delete(id)
    hiddenIds.value = next
  }
}

/** 单条删除不弹确认：先隐藏，5 秒内可撤销，之后才真正删除 */
const removeBookmark = (bookmark: Bookmark) => {
  const id = String(bookmark.id)
  if (pendingDeletes.has(id)) return
  hiddenIds.value = new Set(hiddenIds.value).add(id)
  if (drawerBookmark.value && String(drawerBookmark.value.id) === id) drawerBookmark.value = null
  if (selectedIds.value.has(id)) {
    const next = new Set(selectedIds.value)
    next.delete(id)
    selectedIds.value = next
  }
  pendingDeletes.set(
    id,
    setTimeout(() => commitDelete(id), 5000),
  )
  toast.ok(`已删除「${bookmark.title}」`, {
    duration: 5000,
    action: {
      label: '撤销',
      run: () => {
        clearTimeout(pendingDeletes.get(id))
        pendingDeletes.delete(id)
        const next = new Set(hiddenIds.value)
        next.delete(id)
        hiddenIds.value = next
      },
    },
  })
}

/** 离开页面时把撤销期内的删除立即提交 */
const flushDeletes = () => {
  for (const [id, timer] of pendingDeletes) {
    clearTimeout(timer)
    deleteBookmark(id).catch(() => undefined)
  }
  pendingDeletes.clear()
}

const bulkRemove = async () => {
  const items = selectedBookmarks.value
  const names = items.slice(0, 3).map((b) => `「${b.title}」`).join('、')
  const ok = await confirm({
    title: `删除 ${items.length} 条书签？`,
    message: `${names}${items.length > 3 ? ` 等 ${items.length} 条` : ''}书签及其标签关联会被删除，无法撤销。
只是暂时不看的话，可以改为归档，随时能恢复。`,
    confirmText: `删除 ${items.length} 条`,
    danger: true,
  })
  if (!ok) return
  bulkBusy.value = true
  try {
    const removed = await batchDeleteBookmarks(items.map((b) => b.id))
    toast.ok(`已删除 ${removed ?? items.length} 条`)
    clearSelection()
    await loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  } finally {
    bulkBusy.value = false
  }
}

// ── 失效链接 ──

const linkCheckOpen = ref(false)

const onLinksChanged = () => {
  space.reload()
  loadBookmarks()
}

const viewBroken = () => {
  linkCheckOpen.value = false
  selectFilter({ kind: 'broken' })
}

/** 失效的其实能打开：改回正常，可撤销 */
const restoreBroken = async (bookmark: Bookmark) => {
  try {
    await updateBookmarkStatus(bookmark.id, BookmarkStatus.NORMAL)
    toast.ok('已恢复正常', {
      action: {
        label: '撤销',
        run: async () => {
          await updateBookmarkStatus(bookmark.id, BookmarkStatus.BROKEN)
          refresh()
        },
      },
    })
    refresh()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

/** 删除全部失效链接（不受搜索词影响），批量接口每次 200 条 */
const removeAllBroken = async () => {
  bulkBusy.value = true
  try {
    const items = await fetchAllBookmarks({ status: BookmarkStatus.BROKEN })
    if (!items.length) return
    const names = items.slice(0, 3).map((b) => `「${b.title}」`).join('、')
    const ok = await confirm({
      title: `删除全部 ${items.length} 条失效链接？`,
      message: `${names}${items.length > 3 ? ` 等 ${items.length} 条` : ''}书签及其标签关联会被删除，无法撤销。
站点也可能只是临时打不开，拿不准的可以先打开看看，或改为归档。`,
      confirmText: `删除 ${items.length} 条`,
      danger: true,
    })
    if (!ok) return
    let removed = 0
    for (let i = 0; i < items.length; i += 200) {
      removed += (await batchDeleteBookmarks(items.slice(i, i + 200).map((b) => b.id))) ?? 0
    }
    toast.ok(`已删除 ${removed} 条失效链接`)
    clearSelection()
    await loadBookmarks()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
    loadBookmarks()
  } finally {
    bulkBusy.value = false
  }
}

// ── AI 整理：到整理页做，带上范围 ──

const openAiOrganize = (ids?: EntityId[]) => {
  const query: Record<string, string> = { tab: 'ai' }
  if (ids?.length) query.ids = ids.map(String).join(',')
  else if (filter.value.kind === 'folder') query.folder = String(filter.value.id)
  else if (filter.value.kind === 'uncategorized') query.scope = 'uncategorized'
  router.push({ path: '/bookmarks/organize', query })
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
      const id = await createFolder({ parentId: parent?.id ?? 0, name })
      closeNameDialog()
      await space.reload()
      selectFilter({ kind: 'folder', id })
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

const deletingFolder = ref<Folder | null>(null)

const onFolderDeleted = async (folder: Folder) => {
  deletingFolder.value = null
  toast.ok(`目录「${folder.name}」已删除`)
  await space.reload()
  if (filter.value.kind === 'folder' && !space.findFolder(filter.value.id)) selectFilter({ kind: 'all' })
  else loadBookmarks()
}

const openCreateTag = () => {
  nameDialog.value = {
    open: true,
    title: '新建标签',
    label: '标签名称',
    initial: '',
    save: async (name) => {
      await createTag({ name, color: nextTagColor(space.tags.length) })
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

const importOpen = ref(false)
const exportOpen = ref(false)
const sidebar = ref<InstanceType<typeof SpaceSidebar> | null>(null)

const onImported = async (_task: ImportTask) => {
  await space.reload()
  sidebar.value?.reloadLastImport()
  loadBookmarks()
}

/** 导出范围默认跟随侧栏：选中目录/标签就只导出它 */
const exportScope = computed<ExportScope>(() => {
  const f = filter.value
  return f.kind === 'folder' || f.kind === 'tag' ? { scopeType: f.kind, scopeId: f.id } : { scopeType: 'all' }
})

const onExported = (count: number) => {
  exportOpen.value = false
  toast.ok(`已导出 ${count} 条书签`)
}

// ── 空状态 ──

const emptyState = computed(() => {
  const kw = keyword.value.trim()
  const f = filter.value
  if (kw) {
    return {
      title: f.kind === 'all' ? `没有匹配「${kw}」的书签` : `「${heading.value}」里没有「${kw}」`,
      description: f.kind === 'all' ? '换个关键词试试；搜索范围是标题、网址和描述。' : '书签可能在别的目录里。',
    }
  }
  switch (f.kind) {
    case 'folder':
      return childFolders.value.length
        ? { title: `「${heading.value}」这一层没有书签`, description: '书签都在上面的子目录里；也可以直接往这一层添加。' }
        : { title: `「${heading.value}」还是空的`, description: '添加一个网址，或从未分类中挑几条移进来。' }
    case 'tag':
      return { title: `还没有书签打上${heading.value}`, description: '在书签的编辑弹窗或批量操作里打标签。' }
    case 'uncategorized':
      return { title: '未分类是空的', description: '所有书签都已经归入目录了。' }
    case 'archived':
      return { title: '没有归档的书签', description: '暂时不看的书签可以归档，它们不会出现在其他视图里。' }
    case 'broken':
      return { title: '没有失效链接', description: '检查一遍书签，打不开的会出现在这里，可以一键删除。' }
    default:
      return { title: '这里还没有书签', description: '在上面粘贴一个网址，或导入浏览器书签。' }
  }
})

const searchAll = () => selectFilter({ kind: 'all' })

// ── 快捷键 ──

const searchInput = ref<HTMLInputElement | null>(null)

const onKeydown = (event: KeyboardEvent) => {
  if (document.querySelector('.dialog-mask, .drawer-mask')) return
  const target = event.target as HTMLElement | null
  const typing = target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)
  if (event.key === 'Escape' && selectedIds.value.size) {
    clearSelection()
  } else if (event.key === '/' && !typing) {
    event.preventDefault()
    searchInput.value?.focus()
  }
}

/** 全局搜索打开某条书签：/bookmarks?open=<id>，打开抽屉后把参数去掉 */
const openFromQuery = async () => {
  const id = route.query.open
  if (typeof id !== 'string' || !id) return
  const { open: _open, ...rest } = route.query
  router.replace({ query: rest })
  try {
    openDrawer(await fetchBookmark(id))
  } catch (error) {
    toast.error(errorText(error, '书签不存在或已删除'))
  }
}
watch(() => route.query.open, openFromQuery)

onMounted(() => {
  space.reload()
  loadBookmarks()
  openFromQuery()
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('pagehide', flushDeletes)
})

onBeforeUnmount(() => {
  clearTimeout(keywordTimer)
  flushDeletes()
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pagehide', flushDeletes)
})
</script>

<template>
  <div class="space page">
    <button class="side-toggle btn btn--ghost" type="button" :aria-expanded="sideOpen" @click="sideOpen = !sideOpen">
      <Icon icon="lucide:panel-left" />
      <span>{{ heading }}</span>
      <Icon :icon="sideOpen ? 'lucide:chevron-up' : 'lucide:chevron-down'" />
    </button>

    <SpaceSidebar
      ref="sidebar"
      class="space__side scrollbar-slim"
      :class="{ 'space__side--open': sideOpen }"
      :active="filter"
      @select="selectFilter"
      @create-folder="openCreateFolder"
      @rename-folder="openRenameFolder"
      @remove-folder="deletingFolder = $event"
      @create-tag="openCreateTag"
      @remove-tag="removeTag"
    />

    <section class="space__main">
      <header class="toolbar">
        <div class="toolbar__title">
          <p v-if="subheading" class="toolbar__sub">{{ subheading }}</p>
          <div class="toolbar__row">
            <h1 class="page-title">{{ heading }}</h1>
            <span class="tag">{{ formatCount(shownTotal) }}</span>
          </div>
        </div>
        <div class="toolbar__actions">
          <label class="search">
            <Icon icon="lucide:search" class="search__icon" />
            <input
              ref="searchInput"
              v-model="keyword"
              type="search"
              :placeholder="filter.kind === 'all' ? '搜索标题、网址、描述' : `在「${heading}」中搜索`"
              aria-label="搜索书签"
            />
            <kbd v-if="!keyword">/</kbd>
          </label>
          <div class="seg" role="radiogroup" aria-label="视图">
            <button type="button" role="radio" :aria-checked="viewMode === 'grid'" :class="{ on: viewMode === 'grid' }" title="网格" @click="viewMode = 'grid'">
              <Icon icon="lucide:layout-grid" />
            </button>
            <button type="button" role="radio" :aria-checked="viewMode === 'list'" :class="{ on: viewMode === 'list' }" title="列表" @click="viewMode = 'list'">
              <Icon icon="lucide:list" />
            </button>
          </div>
          <template v-if="filter.kind === 'broken'">
            <button class="btn btn--ghost" type="button" title="由服务器逐个访问书签网址" @click="linkCheckOpen = true">
              <Icon icon="lucide:radar" />
              <span class="btn__text">检查链接</span>
            </button>
            <button v-if="total" class="btn btn--ghost btn--warn" type="button" :disabled="bulkBusy" @click="removeAllBroken">
              <Icon icon="lucide:trash-2" />
              <span class="btn__text">删除全部</span>
            </button>
          </template>
          <button
            v-else-if="filter.kind === 'uncategorized' || filter.kind === 'folder'"
            class="btn btn--ghost"
            type="button"
            title="让 AI 给这里的书签归目录、打标签"
            @click="openAiOrganize()"
          >
            <Icon icon="lucide:sparkles" />
            <span class="btn__text">AI 整理</span>
          </button>
          <button class="btn btn--ghost" type="button" title="导入浏览器书签" @click="importOpen = true">
            <Icon icon="lucide:upload" />
            <span class="btn__text">导入</span>
          </button>
          <button class="btn btn--ghost" type="button" title="导出为 Chrome 书签 HTML" @click="exportOpen = true">
            <Icon icon="lucide:download" />
            <span class="btn__text">导出</span>
          </button>
          <button class="btn btn--primary" type="button" @click="openCreateBookmark">
            <Icon icon="lucide:plus" />
            <span class="btn__text">添加书签</span>
          </button>
        </div>
      </header>

      <div v-if="childFolders.length" class="subs" aria-label="子目录">
        <span>子目录</span>
        <button
          v-for="child in childFolders"
          :key="child.id"
          type="button"
          class="subs__item"
          @click="selectFilter({ kind: 'folder', id: child.id })"
        >
          <Icon icon="lucide:folder" />{{ child.name }}
          <small v-if="child.children?.length">+{{ child.children.length }}</small>
        </button>
      </div>

      <QuickAdd
        v-if="showQuickAdd"
        :folder-id="quickFolderId"
        :folder-label="quickFolderLabel"
        :tag-id="filter.kind === 'tag' ? filter.id : undefined"
        @added="onQuickAdded"
        @filled="onQuickFilled"
        @duplicate="openDrawer"
      />

      <BulkBar
        v-if="selectedBookmarks.length"
        :count="selectedBookmarks.length"
        :page-count="visible.length"
        :restoring="filter.kind === 'archived'"
        :busy="bulkBusy"
        @move="openMove(selectedBookmarks)"
        @tag="bulkTagOpen = true"
        @ai="openAiOrganize(selectedBookmarks.map((b) => b.id))"
        @archive="bulkArchive"
        @remove="bulkRemove"
        @select-page="selectPage"
        @clear="clearSelection"
      />

      <!-- 首次加载用骨架占位，不闪白 -->
      <div v-if="loading && !loaded" class="grid" aria-busy="true" aria-label="加载中">
        <div v-for="n in 9" :key="n" class="skeleton surface">
          <span class="skeleton__icon" />
          <span class="skeleton__line" />
          <span class="skeleton__line skeleton__line--short" />
        </div>
      </div>
      <StateBlock
        v-else-if="loadError"
        state="error"
        :description="loadError"
        action-label="重试"
        @action="loadBookmarks"
      />
      <div v-else-if="!visible.length" class="empty">
        <StateBlock state="empty" :title="emptyState.title" :description="emptyState.description" />
        <div class="empty__acts">
          <template v-if="keyword.trim()">
            <button v-if="filter.kind !== 'all'" class="btn btn--primary" type="button" @click="searchAll">
              <Icon icon="lucide:search" />在全部书签中搜索
            </button>
            <button class="btn btn--ghost" type="button" @click="keyword = ''">清空关键词</button>
          </template>
          <template v-else-if="filter.kind === 'folder'">
            <button class="btn btn--primary" type="button" @click="openCreateBookmark"><Icon icon="lucide:plus" />添加书签</button>
            <button class="btn btn--ghost" type="button" @click="selectFilter({ kind: 'uncategorized' })">
              <Icon icon="lucide:inbox" />从未分类移入
            </button>
          </template>
          <template v-else-if="filter.kind === 'all'">
            <button class="btn btn--ghost" type="button" @click="importOpen = true"><Icon icon="lucide:upload" />导入浏览器书签</button>
          </template>
          <template v-else-if="filter.kind === 'broken'">
            <button class="btn btn--primary" type="button" @click="linkCheckOpen = true"><Icon icon="lucide:radar" />检查全部链接</button>
          </template>
        </div>
      </div>
      <template v-else>
        <div v-if="viewMode === 'grid'" class="grid" :class="{ 'grid--loading': loading }">
          <BookmarkCard
            v-for="bookmark in visible"
            :key="bookmark.id"
            :bookmark="bookmark"
            :selected="selectedIds.has(String(bookmark.id))"
            :selecting="selectedIds.size > 0"
            @open="openDrawer(bookmark)"
            @select="toggleSelect(bookmark, $event)"
            @edit="openEditBookmark(bookmark)"
            @move="openMove([bookmark])"
            @toggle-archive="toggleArchive(bookmark)"
            @remove="removeBookmark(bookmark)"
          />
        </div>
        <BookmarkTable
          v-else
          :class="{ 'grid--loading': loading }"
          :bookmarks="visible"
          :selected-ids="selectedIds"
          :show-folder="filter.kind !== 'folder'"
          @open="openDrawer"
          @select="toggleSelect"
          @toggle-all="toggleAll"
          @edit="openEditBookmark"
          @move="openMove([$event])"
          @toggle-archive="toggleArchive"
          @remove="removeBookmark"
        />
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

    <BookmarkDrawer
      :bookmark="drawerBookmark"
      @close="drawerBookmark = null"
      @edit="openEditBookmark"
      @move="openMove([$event])"
      @toggle-archive="toggleArchive"
      @restore="restoreBroken"
      @remove="removeBookmark"
      @open-folder="(id) => { drawerBookmark = null; selectFilter({ kind: 'folder', id }) }"
      @open-tag="(id) => { drawerBookmark = null; selectFilter({ kind: 'tag', id }) }"
    />
    <BookmarkDialog
      :open="bookmarkDialogOpen"
      :bookmark="editingBookmark"
      :default-folder-id="defaultFolderId"
      @close="bookmarkDialogOpen = false"
      @saved="onBookmarkSaved"
      @open-existing="onOpenExisting"
    />
    <FolderPickerDialog
      :open="Boolean(moveState)"
      :title="moveState?.title ?? ''"
      :current-id="moveState?.currentId ?? null"
      :busy="moving"
      @close="moveState = null"
      @pick="onPickFolder"
    />
    <BulkTagDialog
      :open="bulkTagOpen"
      :bookmarks="selectedBookmarks"
      @close="bulkTagOpen = false"
      @done="onBulkTagged"
    />
    <FolderDeleteDialog :folder="deletingFolder" @close="deletingFolder = null" @deleted="onFolderDeleted" />
    <ImportDialog
      :open="importOpen"
      @close="importOpen = false"
      @imported="onImported"
      @view-records="router.push({ path: '/bookmarks/organize', query: { tab: 'records' } })"
    />
    <LinkCheckDialog
      :open="linkCheckOpen"
      :initial-scope="filter.kind === 'broken' && total ? 'broken' : 'all'"
      @close="linkCheckOpen = false"
      @changed="onLinksChanged"
      @view-broken="viewBroken"
    />
    <ExportDialog :open="exportOpen" :initial-scope="exportScope" @close="exportOpen = false" @exported="onExported" />
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

.side-toggle {
  display: none;
}

/* 右侧向主区间隙借出一条滚动条宽的内边距，目录树的滚动条落在这里，不挤占目录行 */
.space__side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  max-height: calc(100vh - var(--header-height) - 2.5rem);
  margin-right: calc(-1 * var(--scrollbar-size));
  padding-right: var(--scrollbar-size);
  overflow-y: auto;
}

.space__main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem 1rem;
}

.toolbar__title {
  min-width: 0;
}

.toolbar__sub {
  margin-bottom: 0.15rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.toolbar__row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.toolbar__row .page-title {
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

.btn--warn:not(:disabled):hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
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

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}

.seg button {
  display: inline-grid;
  place-items: center;
  width: 2rem;
  height: 1.85rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.seg button.on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.subs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.subs > span {
  margin-right: 0.2rem;
}

.subs__item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
}

.subs__item:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.subs__item svg {
  width: 0.9rem;
  height: 0.9rem;
  color: var(--color-brand);
}

.subs__item small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
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

.skeleton {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  height: 8.2rem;
  padding: 0.9rem 1rem;
}

.skeleton span {
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--color-bg-soft) 25%, var(--color-bg-canvas) 50%, var(--color-bg-soft) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease infinite;
}

.skeleton__icon {
  width: 2.25rem;
  height: 2.25rem;
}

.skeleton__line {
  height: 0.75rem;
}

.skeleton__line--short {
  width: 60%;
}

@keyframes shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -100% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton span {
    animation: none;
  }
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-top: -2rem;
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
    gap: 1rem;
  }

  .side-toggle {
    display: inline-flex;
    justify-self: start;
    max-width: 100%;
  }

  .side-toggle span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .space__side {
    display: none;
    position: static;
    max-height: none;
    margin-right: 0;
    padding: 1rem;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    background: var(--color-bg-surface);
  }

  .space__side--open {
    display: flex;
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
