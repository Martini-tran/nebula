<script setup lang="ts">
/**
 * 目录整理：整棵树平铺展开，拖拽调整层级与顺序，改动即时保存。
 * 落点：行上/下沿的蓝线 = 插到它前/后（同级排序）；整行高亮 = 放进去成为子目录。
 * 不方便拖拽时（触屏、键盘）用行尾的「上移 / 下移 / 移动到…」。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import FolderPickerDialog from '../components/FolderPickerDialog.vue'
import FolderDeleteDialog from '../components/FolderDeleteDialog.vue'
import { countBookmarks, createFolder, moveFolder, updateFolder } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import { formatDate } from '../../../utils/format'
import type { EntityId, Folder } from '../../../types/space'

const space = useSpaceStore()
const sameId = (a: EntityId | null | undefined, b: EntityId | null | undefined) => String(a ?? '') === String(b ?? '')

const collapsed = ref(new Set<string>())
const selectedId = ref<string | null>(null)
const busy = ref(false)

/** 可见行：收起的目录不展开其子孙 */
const rows = computed(() => {
  const result: { folder: Folder; depth: number; hasChildren: boolean }[] = []
  const walk = (nodes: Folder[], depth: number) => {
    for (const node of nodes) {
      const hasChildren = Boolean(node.children?.length)
      result.push({ folder: node, depth, hasChildren })
      if (hasChildren && !collapsed.value.has(String(node.id))) walk(node.children!, depth + 1)
    }
  }
  walk(space.folders, 0)
  return result
})

const maxDepth = computed(() => space.flat.reduce((max, f) => Math.max(max, f.depth + 1), 0))
const selected = computed(() => (selectedId.value ? space.findFolder(selectedId.value) : undefined))

const toggleCollapse = (id: EntityId) => {
  const next = new Set(collapsed.value)
  if (next.has(String(id))) next.delete(String(id))
  else next.add(String(id))
  collapsed.value = next
}

const siblingsOf = (parentId: EntityId): Folder[] =>
  sameId(parentId, 0) ? space.folders : (space.findFolder(parentId)?.children ?? [])

/**
 * 把 folder 放到 parentId 下第 index 位：必要时先改父目录，再把同级按新顺序重新编号。
 * 只提交 sortOrder 真正变了的那几条。
 */
const place = async (folder: Folder, parentId: EntityId, index: number) => {
  const others = siblingsOf(parentId).filter((f) => !sameId(f.id, folder.id))
  const ordered = [...others.slice(0, index), folder, ...others.slice(index)]
  busy.value = true
  try {
    if (!sameId(folder.parentId, parentId)) await moveFolder(folder.id, parentId, index)
    for (const [i, f] of ordered.entries()) {
      if ((f.sortOrder ?? -1) !== i || sameId(f.id, folder.id)) await updateFolder(f.id, { sortOrder: i })
    }
    await space.reload()
    if (!sameId(parentId, 0)) {
      const next = new Set(collapsed.value)
      next.delete(String(parentId))
      collapsed.value = next
    }
  } catch (error) {
    toast.error(errorText(error, '移动失败'))
    await space.reload()
  } finally {
    busy.value = false
  }
}

// ── 拖拽 ──

type Zone = 'before' | 'inside' | 'after'
const dragId = ref<string | null>(null)
const dropTarget = ref<{ id: string; zone: Zone } | null>(null)

const blocked = computed(() => new Set(dragId.value ? space.subtree(dragId.value).map((f) => String(f.id)) : []))

const onDragStart = (event: DragEvent, folder: Folder) => {
  dragId.value = String(folder.id)
  event.dataTransfer?.setData('text/plain', folder.name)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const onDragOver = (event: DragEvent, folder: Folder) => {
  if (!dragId.value || blocked.value.has(String(folder.id))) {
    dropTarget.value = null
    return
  }
  event.preventDefault()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = (event.clientY - rect.top) / rect.height
  const zone: Zone = ratio < 0.28 ? 'before' : ratio > 0.72 ? 'after' : 'inside'
  dropTarget.value = { id: String(folder.id), zone }
}

const onDrop = async (folder: Folder) => {
  const target = dropTarget.value
  const dragged = dragId.value ? space.findFolder(dragId.value) : undefined
  onDragEnd()
  if (!target || !dragged) return
  if (target.zone === 'inside') {
    await place(dragged, folder.id, folder.children?.filter((f) => !sameId(f.id, dragged.id)).length ?? 0)
    return
  }
  const siblings = siblingsOf(folder.parentId).filter((f) => !sameId(f.id, dragged.id))
  const index = siblings.findIndex((f) => sameId(f.id, folder.id)) + (target.zone === 'after' ? 1 : 0)
  await place(dragged, folder.parentId, index)
}

const onDragEnd = () => {
  dragId.value = null
  dropTarget.value = null
}

/** 拖到列表底部空白处 = 放到顶层末尾 */
const onDropRoot = async () => {
  const dragged = dragId.value ? space.findFolder(dragId.value) : undefined
  onDragEnd()
  if (dragged) await place(dragged, 0, space.folders.filter((f) => !sameId(f.id, dragged.id)).length)
}

// ── 上移 / 下移 / 移动到 ──

const shift = (folder: Folder, delta: -1 | 1) => {
  const siblings = siblingsOf(folder.parentId)
  const index = siblings.findIndex((f) => sameId(f.id, folder.id))
  const target = index + delta
  if (target < 0 || target >= siblings.length) return
  place(folder, folder.parentId, target)
}

const movingFolder = ref<Folder | null>(null)
const onPickParent = async (parentId: EntityId) => {
  const folder = movingFolder.value
  movingFolder.value = null
  if (folder) await place(folder, parentId, siblingsOf(parentId).length)
}

// ── 新建 / 改名 / 删除 ──

const editingId = ref<string | null>(null)
const editingName = ref('')
const editInput = ref<HTMLInputElement[] | null>(null)

const startRename = async (folder: Folder) => {
  editingId.value = String(folder.id)
  editingName.value = folder.name
  await nextTick()
  editInput.value?.[0]?.focus()
  editInput.value?.[0]?.select()
}

const commitRename = async (folder: Folder) => {
  if (editingId.value !== String(folder.id)) return
  const name = editingName.value.trim()
  editingId.value = null
  if (!name || name === folder.name) return
  try {
    await updateFolder(folder.id, { name })
    await space.reload()
  } catch (error) {
    toast.error(errorText(error, '重命名失败'))
  }
}

const addFolder = async (parent: Folder | null) => {
  const siblings = parent ? (parent.children ?? []) : space.folders
  let name = '新目录'
  for (let i = 2; siblings.some((f) => f.name === name); i += 1) name = `新目录 ${i}`
  try {
    const id = await createFolder({ parentId: parent?.id ?? 0, name })
    await space.reload()
    if (parent) {
      const next = new Set(collapsed.value)
      next.delete(String(parent.id))
      collapsed.value = next
    }
    selectedId.value = String(id)
    const created = space.findFolder(id)
    if (created) startRename(created)
  } catch (error) {
    toast.error(errorText(error, '新建目录失败'))
  }
}

const deleting = ref<Folder | null>(null)
const onDeleted = async (folder: Folder) => {
  deleting.value = null
  if (sameId(selectedId.value, folder.id)) selectedId.value = null
  toast.ok(`目录「${folder.name}」已删除`)
  await space.reload()
}

const onRowKeydown = (event: KeyboardEvent, folder: Folder) => {
  if (editingId.value) return
  if (event.key === 'F2') {
    event.preventDefault()
    startRename(folder)
  } else if (event.key === 'Delete') {
    event.preventDefault()
    deleting.value = folder
  } else if (event.altKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
    event.preventDefault()
    shift(folder, event.key === 'ArrowUp' ? -1 : 1)
  }
}

// ── 右侧详情 ──

const stats = ref<{ direct: number; nested: number } | null>(null)
watch(
  selected,
  async (folder) => {
    stats.value = null
    if (!folder) return
    const tree = space.subtree(folder.id)
    try {
      const counts = await Promise.all(tree.map((f) => countBookmarks({ folderId: f.id })))
      if (sameId(selected.value?.id, folder.id)) {
        stats.value = { direct: counts[0] ?? 0, nested: counts.slice(1).reduce((a, b) => a + b, 0) }
      }
    } catch {
      stats.value = null
    }
  },
)
</script>

<template>
  <div class="fm">
    <section class="fm__tree surface">
      <header class="fm__head">
        <span>目录结构 · {{ maxDepth }} 层 · {{ space.flat.length }} 个</span>
        <span v-if="busy" class="fm__saving"><Icon icon="lucide:loader-circle" class="spin" />保存中</span>
        <button class="btn btn--ghost" type="button" @click="addFolder(null)"><Icon icon="lucide:folder-plus" />新建目录</button>
      </header>

      <p v-if="!space.flat.length" class="fm__empty">还没有目录。新建一个，或者导入浏览器书签——文件夹结构会一起带过来。</p>

      <ul v-else class="fm__list" role="tree" aria-label="目录结构">
        <li
          v-for="row in rows"
          :key="row.folder.id"
          class="row"
          role="treeitem"
          :aria-expanded="row.hasChildren ? !collapsed.has(String(row.folder.id)) : undefined"
          :aria-selected="selectedId === String(row.folder.id)"
          tabindex="0"
          :class="{
            'row--on': selectedId === String(row.folder.id),
            'row--dragging': dragId === String(row.folder.id),
            'row--blocked': dragId && blocked.has(String(row.folder.id)),
            [`row--${dropTarget?.zone}`]: dropTarget?.id === String(row.folder.id),
          }"
          :style="{ '--depth': row.depth }"
          :draggable="editingId !== String(row.folder.id) && !busy"
          @click="selectedId = String(row.folder.id)"
          @keydown="onRowKeydown($event, row.folder)"
          @dragstart="onDragStart($event, row.folder)"
          @dragover="onDragOver($event, row.folder)"
          @dragleave="dropTarget = null"
          @drop.prevent="onDrop(row.folder)"
          @dragend="onDragEnd"
        >
          <Icon icon="lucide:grip-vertical" class="row__grip" aria-hidden="true" />
          <button
            class="row__toggle"
            type="button"
            :style="{ visibility: row.hasChildren ? 'visible' : 'hidden' }"
            :aria-label="collapsed.has(String(row.folder.id)) ? '展开' : '收起'"
            @click.stop="toggleCollapse(row.folder.id)"
          >
            <Icon :icon="collapsed.has(String(row.folder.id)) ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
          </button>
          <Icon :icon="row.hasChildren && !collapsed.has(String(row.folder.id)) ? 'lucide:folder-open' : 'lucide:folder'" class="row__icon" />
          <input
            v-if="editingId === String(row.folder.id)"
            ref="editInput"
            v-model="editingName"
            class="row__edit"
            maxlength="100"
            aria-label="目录名称"
            @click.stop
            @keydown.enter.prevent="commitRename(row.folder)"
            @keydown.esc.stop.prevent="editingId = null"
            @blur="commitRename(row.folder)"
          />
          <span v-else class="row__name" @dblclick="startRename(row.folder)">{{ row.folder.name }}</span>
          <span v-if="editingId === String(row.folder.id)" class="row__hint">回车保存 · Esc 取消</span>
          <span v-if="dropTarget?.id === String(row.folder.id) && dropTarget.zone === 'inside'" class="row__hint row__hint--drop">放入此目录</span>

          <span class="row__acts">
            <button type="button" title="上移（Alt ↑）" :disabled="busy" @click.stop="shift(row.folder, -1)"><Icon icon="lucide:arrow-up" /></button>
            <button type="button" title="下移（Alt ↓）" :disabled="busy" @click.stop="shift(row.folder, 1)"><Icon icon="lucide:arrow-down" /></button>
            <button type="button" title="新建子目录" @click.stop="addFolder(row.folder)"><Icon icon="lucide:folder-plus" /></button>
            <button type="button" title="重命名（F2）" @click.stop="startRename(row.folder)"><Icon icon="lucide:pencil" /></button>
            <button type="button" title="移动到…" @click.stop="movingFolder = row.folder"><Icon icon="lucide:folder-input" /></button>
            <button type="button" title="删除（Del）" class="danger" @click.stop="deleting = row.folder"><Icon icon="lucide:trash-2" /></button>
          </span>
        </li>
        <li
          v-if="dragId"
          class="row row--root"
          :class="{ 'row--inside': dropTarget?.id === '__root__' }"
          @dragover.prevent="dropTarget = { id: '__root__', zone: 'inside' }"
          @dragleave="dropTarget = null"
          @drop.prevent="onDropRoot"
        >
          <Icon icon="lucide:corner-left-up" />拖到这里成为顶层目录
        </li>
      </ul>
      <p class="fm__tip">拖动整行调整结构：行的上下沿插入到前后，行中间放进去成为子目录。双击名称或 F2 改名。</p>
    </section>

    <aside class="fm__detail surface">
      <template v-if="selected">
        <h3>{{ selected.name }}</h3>
        <p class="fm__path">{{ space.folderPath(selected.id) }}</p>
        <div class="fm__stats">
          <span><b>{{ stats?.direct ?? '…' }}</b>条书签</span>
          <span><b>{{ (selected.children ?? []).length }}</b>个子目录</span>
          <span><b>{{ stats?.nested ?? '…' }}</b>子目录内书签</span>
        </div>
        <p v-if="selected.createTime" class="fm__meta">创建于 {{ formatDate(selected.createTime) }}</p>
        <div class="fm__acts">
          <router-link class="btn btn--primary" :to="{ path: '/bookmarks', query: { folder: String(selected.id) } }">
            <Icon icon="lucide:bookmark" />查看书签
          </router-link>
          <button class="btn btn--ghost" type="button" @click="addFolder(selected)"><Icon icon="lucide:folder-plus" />新建子目录</button>
          <button class="btn btn--ghost" type="button" @click="startRename(selected)"><Icon icon="lucide:pencil" />重命名</button>
          <button class="btn btn--ghost" type="button" @click="movingFolder = selected"><Icon icon="lucide:folder-input" />移动到…</button>
          <button class="btn btn--ghost fm__danger" type="button" @click="deleting = selected"><Icon icon="lucide:trash-2" />删除</button>
        </div>
      </template>
      <p v-else class="fm__placeholder"><Icon icon="lucide:mouse-pointer-click" />选中一个目录查看统计与操作</p>
    </aside>

    <FolderPickerDialog
      :open="Boolean(movingFolder)"
      :title="`移动目录「${movingFolder?.name ?? ''}」到…`"
      :current-id="movingFolder?.parentId ?? null"
      :disabled-ids="movingFolder ? space.subtree(movingFolder.id).map((f) => f.id) : []"
      root-label="顶层"
      :allow-create="false"
      @close="movingFolder = null"
      @pick="onPickParent"
    />
    <FolderDeleteDialog :folder="deleting" @close="deleting = null" @deleted="onDeleted" />
  </div>
</template>

<style scoped>
.fm {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 18rem;
  gap: 1rem;
  align-items: start;
}

.fm__tree {
  padding: 0.5rem 0.5rem 0.75rem;
  border-radius: var(--radius-lg);
}

.fm__head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.4rem 0.5rem 0.6rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.fm__head .btn {
  margin-left: auto;
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.fm__saving {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-weight: 400;
}

.fm__empty,
.fm__tip {
  padding: 0.5rem 0.75rem;
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.fm__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.3rem;
  min-height: 2.3rem;
  padding: 0 0.4rem 0 calc(0.3rem + var(--depth) * 1.35rem);
  border-radius: var(--radius-md);
  font-size: 0.92rem;
  cursor: grab;
  outline: none;
}

.row:hover {
  background: var(--color-bg-soft);
}

.row:focus-visible {
  box-shadow: inset 0 0 0 2px var(--color-brand-soft);
}

.row--on,
.row--on:hover {
  background: var(--color-brand-soft);
}

.row--dragging {
  opacity: 0.4;
}

.row--blocked {
  cursor: not-allowed;
  opacity: 0.45;
}

.row--inside {
  background: var(--color-brand-soft);
  box-shadow: inset 0 0 0 2px var(--color-brand);
}

.row--before::before,
.row--after::after {
  content: '';
  position: absolute;
  left: calc(0.3rem + var(--depth, 0) * 1.35rem);
  right: 0.4rem;
  height: 2px;
  border-radius: 2px;
  background: var(--color-brand);
}

.row--before::before {
  top: -1px;
}

.row--after::after {
  bottom: -1px;
}

.row--root {
  justify-content: center;
  gap: 0.4rem;
  margin-top: 0.4rem;
  border: 1px dashed var(--color-border);
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  --depth: 0;
}

.row__grip {
  flex: none;
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-text-secondary);
  opacity: 0.45;
}

.row__toggle {
  display: inline-grid;
  place-items: center;
  width: 1.4rem;
  height: 1.4rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.row__icon {
  flex: none;
  width: 1rem;
  height: 1rem;
  color: var(--color-brand);
}

.row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.row__edit {
  min-width: 0;
  width: 14rem;
  padding: 0.2rem 0.45rem;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
  outline: none;
}

.row__hint {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.row__hint--drop {
  color: var(--color-brand);
  font-weight: 600;
}

.row__acts {
  display: inline-flex;
  margin-left: auto;
  opacity: 0;
}

.row:hover .row__acts,
.row:focus-within .row__acts,
.row--on .row__acts {
  opacity: 1;
}

@media (hover: none) {
  .row__acts {
    opacity: 1;
  }
}

.row__acts button {
  display: inline-grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.row__acts button:hover:not(:disabled) {
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.row__acts button:disabled {
  opacity: 0.4;
}

.row__acts .danger:hover {
  color: var(--color-danger) !important;
}

.fm__detail {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.1rem;
  border-radius: var(--radius-lg);
}

.fm__detail h3 {
  font-size: 1.05rem;
  font-weight: 800;
}

.fm__path,
.fm__meta {
  margin-top: -0.5rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.fm__meta {
  margin-top: 0;
}

.fm__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.4rem;
}

.fm__stats span {
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0.55rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.fm__stats b {
  font-size: 1.15rem;
  color: var(--color-text-primary);
}

.fm__acts {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.fm__acts .btn {
  justify-content: flex-start;
  padding: 0.45rem 0.8rem;
  font-size: 0.88rem;
}

.fm__danger:not(:disabled):hover {
  border-color: var(--color-danger) !important;
  color: var(--color-danger) !important;
}

.fm__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0.5rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
  text-align: center;
}

.fm__placeholder svg {
  width: 1.6rem;
  height: 1.6rem;
}

@media (max-width: 900px) {
  .fm {
    grid-template-columns: 1fr;
  }

  .fm__detail {
    position: static;
    order: -1;
  }
}
</style>
