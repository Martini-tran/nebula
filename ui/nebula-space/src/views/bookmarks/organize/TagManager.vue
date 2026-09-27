<script setup lang="ts">
/**
 * 标签整理：一行一个标签，色块（点开 12 色调色板）、名称与备注（双击就地改）、使用次数。
 * 按用量倒序，用得少的自然沉底；名称相近的给出合并提示。
 * 合并 = 把 B 的书签改挂 A，再删 B——后端没有合并接口，前端逐条完成。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { bindBookmarkTags, countBookmarks, createTag, deleteTag, fetchAllBookmarks, updateTag } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { confirm } from '../../../composables/useConfirm'
import { errorText, toast } from '../../../composables/useToast'
import { TAG_COLORS, nextTagColor } from '../tagColors'
import type { EntityId, SpaceTag } from '../../../types/space'

const space = useSpaceStore()

/** 每个标签挂了多少条书签（任意状态） */
const usage = ref(new Map<string, number>())
const counting = ref(false)

const loadUsage = async () => {
  counting.value = true
  try {
    const entries = await Promise.all(
      space.tags.map(async (tag) => [String(tag.id), await countBookmarks({ tagId: tag.id })] as const),
    )
    usage.value = new Map(entries)
  } catch {
    // 统计失败不影响改名、改色
  } finally {
    counting.value = false
  }
}

watch(() => space.tags.map((t) => t.id).join(','), loadUsage)
onMounted(loadUsage)

const countOf = (tag: SpaceTag) => usage.value.get(String(tag.id))
const maxUsage = computed(() => Math.max(1, ...usage.value.values()))

const rows = computed(() =>
  [...space.tags].sort((a, b) => (countOf(b) ?? 0) - (countOf(a) ?? 0) || a.name.localeCompare(b.name, 'zh')),
)

const unused = computed(() => space.tags.filter((tag) => countOf(tag) === 0))

/**
 * 名称相近的标签对：忽略大小写相同、互相包含，或两字标签同首字（教程 / 教学）。
 * 只取第一对作为提示，合并到用得多的那个。
 */
const similar = computed(() => {
  const tags = rows.value
  for (let i = 0; i < tags.length; i += 1) {
    for (let j = i + 1; j < tags.length; j += 1) {
      const a = tags[i]!.name.toLowerCase()
      const b = tags[j]!.name.toLowerCase()
      const alike =
        a === b ||
        (a.length > 1 && b.includes(a)) ||
        (b.length > 1 && a.includes(b)) ||
        (a.length === 2 && b.length === 2 && a[0] === b[0])
      if (alike) return { keep: tags[i]!, drop: tags[j]! }
    }
  }
  return null
})

// ── 就地编辑 ──

const editing = ref<{ id: string; field: 'name' | 'remark' } | null>(null)
const draft = ref('')
const editInput = ref<HTMLInputElement[] | null>(null)

const startEdit = async (tag: SpaceTag, field: 'name' | 'remark') => {
  editing.value = { id: String(tag.id), field }
  draft.value = (field === 'name' ? tag.name : tag.remark) ?? ''
  await nextTick()
  editInput.value?.[0]?.focus()
  editInput.value?.[0]?.select()
}

const commitEdit = async (tag: SpaceTag) => {
  const current = editing.value
  if (!current || current.id !== String(tag.id)) return
  editing.value = null
  const value = draft.value.trim()
  if (current.field === 'name' && (!value || value === tag.name)) return
  if (current.field === 'remark' && value === (tag.remark ?? '')) return
  try {
    await updateTag(tag.id, { [current.field]: value })
    await space.reload()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const isEditing = (tag: SpaceTag, field: 'name' | 'remark') =>
  editing.value?.id === String(tag.id) && editing.value.field === field

// ── 颜色 ──

const paletteFor = ref<string | null>(null)

/** 点调色板以外的地方收起 */
const onPointerDown = (event: PointerEvent) => {
  if (paletteFor.value && !(event.target as HTMLElement).closest('.palette, .swatch')) paletteFor.value = null
}
onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))

const setColor = async (tag: SpaceTag, color: string) => {
  paletteFor.value = null
  if (color === tag.color) return
  try {
    await updateTag(tag.id, { color })
    await space.reload()
  } catch (error) {
    toast.error(errorText(error, '改色失败'))
  }
}

// ── 新建 ──

const newName = ref('')
const addTag = async () => {
  const name = newName.value.trim()
  if (!name) return
  try {
    await createTag({ name, color: nextTagColor(space.tags.length) })
    newName.value = ''
    await space.reload()
  } catch (error) {
    toast.error(errorText(error, '新建失败'))
  }
}

// ── 删除 ──

const removeTag = async (tag: SpaceTag) => {
  const n = countOf(tag) ?? 0
  const ok = await confirm({
    title: `删除标签「${tag.name}」？`,
    message: n ? `${n} 条书签会去掉这个标签，书签本身不会被删除。` : '这个标签没有被使用。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteTag(tag.id)
    await space.reload()
    toast.ok('标签已删除')
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const removeUnused = async () => {
  const list = unused.value
  const ok = await confirm({
    title: `删除 ${list.length} 个未使用的标签？`,
    message: list.map((t) => `「${t.name}」`).join('、'),
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  let failed = 0
  for (const tag of list) {
    try {
      await deleteTag(tag.id)
    } catch {
      failed += 1
    }
  }
  await space.reload()
  if (failed) toast.error(`${list.length - failed} 个已删除，${failed} 个失败`)
  else toast.ok(`已删除 ${list.length} 个标签`)
}

// ── 合并 ──

const merge = ref<{ from: SpaceTag; toId: string } | null>(null)
const merging = ref(false)
const mergeProgress = ref({ done: 0, total: 0 })

const openMerge = (from: SpaceTag, to?: SpaceTag) => {
  const fallback = rows.value.find((t) => String(t.id) !== String(from.id))
  merge.value = { from, toId: String(to?.id ?? fallback?.id ?? '') }
}

const mergeTarget = computed(() => (merge.value ? space.findTag(merge.value.toId) : undefined))

const runMerge = async () => {
  const state = merge.value
  const target = mergeTarget.value
  if (!state || !target || merging.value) return
  merging.value = true
  try {
    const list = await fetchAllBookmarks({ tagId: state.from.id })
    mergeProgress.value = { done: 0, total: list.length }
    for (const b of list) {
      const ids = new Set<EntityId>((b.tags ?? []).map((t) => String(t.id)).filter((id) => id !== String(state.from.id)))
      ids.add(String(target.id))
      await bindBookmarkTags(b.id, [...ids])
      mergeProgress.value.done += 1
    }
    await deleteTag(state.from.id)
    toast.ok(`已把「${state.from.name}」合并到「${target.name}」`)
    merge.value = null
  } catch (error) {
    toast.error(`${errorText(error, '合并失败')}（已处理 ${mergeProgress.value.done} 条）`)
  } finally {
    merging.value = false
    await space.reload()
    loadUsage()
  }
}
</script>

<template>
  <div class="tm">
    <div v-if="similar || unused.length" class="tm__hint">
      <Icon icon="lucide:sparkles" />
      <span>
        <template v-if="similar">
          「{{ similar.drop.name }}」（{{ countOf(similar.drop) ?? '…' }}）与「{{ similar.keep.name }}」（{{ countOf(similar.keep) ?? '…' }}）很像，要合并吗？
        </template>
        <template v-if="unused.length">
          另有 {{ unused.length }} 个从未使用的标签{{ unused.length <= 3 ? `：${unused.map((t) => `「${t.name}」`).join('')}` : '' }}。
        </template>
      </span>
      <button v-if="similar" class="btn btn--ghost" type="button" @click="openMerge(similar.drop, similar.keep)">
        合并到「{{ similar.keep.name }}」
      </button>
      <button v-if="unused.length" class="btn btn--ghost" type="button" @click="removeUnused">删除未使用</button>
    </div>

    <div class="tm__wrap surface">
      <table class="tm__table">
        <thead>
          <tr>
            <th class="c-color">颜色</th>
            <th>名称</th>
            <th class="c-usage">使用</th>
            <th class="c-remark">备注</th>
            <th class="c-acts"><span class="sr-only">操作</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tag in rows" :key="tag.id">
            <td class="c-color">
              <button
                class="swatch"
                type="button"
                :style="{ background: tag.color || 'var(--color-text-secondary)' }"
                :aria-label="`修改「${tag.name}」的颜色`"
                :aria-expanded="paletteFor === String(tag.id)"
                @click="paletteFor = paletteFor === String(tag.id) ? null : String(tag.id)"
              />
              <div v-if="paletteFor === String(tag.id)" class="palette surface" role="listbox" aria-label="标签颜色">
                <button
                  v-for="color in TAG_COLORS"
                  :key="color"
                  type="button"
                  role="option"
                  :aria-selected="color === tag.color"
                  :class="{ on: color === tag.color }"
                  :style="{ background: color }"
                  :aria-label="color"
                  @click="setColor(tag, color)"
                />
              </div>
            </td>
            <td>
              <input
                v-if="isEditing(tag, 'name')"
                ref="editInput"
                v-model="draft"
                class="cell-edit"
                maxlength="100"
                aria-label="标签名称"
                @keydown.enter.prevent="commitEdit(tag)"
                @keydown.esc.stop.prevent="editing = null"
                @blur="commitEdit(tag)"
              />
              <button v-else type="button" class="cell-text cell-name" title="双击改名" @dblclick="startEdit(tag, 'name')">
                {{ tag.name }}
              </button>
            </td>
            <td class="c-usage">
              <span class="usage">
                <i :style="{ width: `${((countOf(tag) ?? 0) / maxUsage) * 100}%` }" />
              </span>
              <b>{{ countOf(tag) ?? (counting ? '…' : '—') }}</b>
            </td>
            <td class="c-remark">
              <input
                v-if="isEditing(tag, 'remark')"
                ref="editInput"
                v-model="draft"
                class="cell-edit"
                maxlength="500"
                aria-label="备注"
                @keydown.enter.prevent="commitEdit(tag)"
                @keydown.esc.stop.prevent="editing = null"
                @blur="commitEdit(tag)"
              />
              <button v-else type="button" class="cell-text cell-remark" title="双击编辑备注" @dblclick="startEdit(tag, 'remark')">
                {{ tag.remark || '—' }}
              </button>
            </td>
            <td class="c-acts">
              <span class="acts">
                <router-link :to="{ path: '/bookmarks', query: { tag: String(tag.id) } }" title="查看书签"><Icon icon="lucide:bookmark" /></router-link>
                <button type="button" title="改名" @click="startEdit(tag, 'name')"><Icon icon="lucide:pencil" /></button>
                <button type="button" title="合并到…" :disabled="space.tags.length < 2" @click="openMerge(tag)"><Icon icon="lucide:merge" /></button>
                <button type="button" title="删除" class="danger" @click="removeTag(tag)"><Icon icon="lucide:trash-2" /></button>
              </span>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="5" class="tm__empty">还没有标签</td>
          </tr>
        </tbody>
      </table>
      <form class="tm__new" @submit.prevent="addTag">
        <Icon icon="lucide:plus" />
        <input v-model="newName" maxlength="100" placeholder="新标签名称，回车创建" aria-label="新标签名称" />
      </form>
    </div>

    <BaseDialog :open="Boolean(merge)" :title="`合并标签「${merge?.from.name ?? ''}」`" width="28rem" :locked="merging" @close="merge = null">
      <div v-if="merge" class="merge">
        <label class="field">
          <span class="field__label">合并到</span>
          <select v-model="merge.toId" class="field__input" :disabled="merging">
            <option v-for="t in rows.filter((r) => String(r.id) !== String(merge!.from.id))" :key="t.id" :value="String(t.id)">
              {{ t.name }}（{{ countOf(t) ?? '…' }}）
            </option>
          </select>
        </label>
        <p class="merge__desc">
          「{{ merge.from.name }}」的 {{ countOf(merge.from) ?? '…' }} 条书签会改挂「{{ mergeTarget?.name }}」，然后删除「{{ merge.from.name }}」。
          已经同时有这两个标签的书签只保留一个。
        </p>
        <p v-if="merging" class="merge__progress">
          <Icon icon="lucide:loader-circle" class="spin" />处理中 {{ mergeProgress.done }}/{{ mergeProgress.total }}
        </p>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" :disabled="merging" @click="merge = null">取消</button>
        <button class="btn btn--primary" type="button" :disabled="!mergeTarget || merging" @click="runMerge">合并</button>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.tm {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.tm__hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 0.9rem;
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  font-size: 0.88rem;
}

.tm__hint > svg {
  flex: none;
  color: var(--color-brand);
}

.tm__hint > span {
  flex: 1;
  min-width: 14rem;
}

.tm__hint .btn {
  padding: 0.35rem 0.75rem;
  font-size: 0.84rem;
}

.tm__wrap {
  overflow-x: auto;
  border-radius: var(--radius-lg);
}

.tm__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.tm__table th {
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-align: left;
  white-space: nowrap;
}

.tm__table td {
  padding: 0.45rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.tm__table tbody tr:hover {
  background: var(--color-bg-soft);
}

.c-color {
  position: relative;
  width: 3.5rem;
}

.swatch {
  display: block;
  width: 1.3rem;
  height: 1.3rem;
  border: 2px solid var(--color-bg-surface);
  border-radius: var(--radius-sm);
  box-shadow: 0 0 0 1px var(--color-border);
  cursor: pointer;
}

.palette {
  position: absolute;
  top: 2.3rem;
  left: 0.5rem;
  z-index: 10;
  display: grid;
  grid-template-columns: repeat(6, 1.5rem);
  gap: 0.35rem;
  padding: 0.6rem;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.palette button {
  width: 1.5rem;
  height: 1.5rem;
  border: 2px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.palette button.on {
  border-color: var(--color-bg-surface);
  box-shadow: 0 0 0 2px var(--color-text-primary);
}

.cell-text {
  display: block;
  max-width: 100%;
  padding: 0.2rem 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
}

.cell-name {
  font-weight: 600;
}

.cell-remark {
  max-width: 18rem;
  color: var(--color-text-secondary);
}

.cell-edit {
  width: 100%;
  max-width: 16rem;
  padding: 0.2rem 0.45rem;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
  outline: none;
}

.c-usage {
  width: 11rem;
  white-space: nowrap;
}

.usage {
  display: inline-block;
  width: 6rem;
  height: 0.4rem;
  margin-right: 0.5rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
  vertical-align: middle;
}

.usage i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
}

.c-acts {
  width: 8.5rem;
  text-align: right;
}

.acts {
  display: inline-flex;
  opacity: 0;
}

tr:hover .acts,
tr:focus-within .acts {
  opacity: 1;
}

@media (hover: none) {
  .acts {
    opacity: 1;
  }
}

.acts a,
.acts button {
  display: inline-grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.acts a:hover,
.acts button:hover:not(:disabled) {
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.acts button:disabled {
  opacity: 0.4;
}

.acts .danger:hover {
  color: var(--color-danger) !important;
}

.tm__empty {
  padding: 2rem !important;
  text-align: center;
  color: var(--color-text-secondary);
}

.tm__new {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.3rem 0.75rem;
  color: var(--color-text-secondary);
}

.tm__new input {
  flex: 1;
  padding: 0.45rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.merge {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1.1rem 1.35rem 0.25rem;
}

.merge__desc {
  font-size: 0.86rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.merge__progress {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.86rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

@media (max-width: 640px) {
  .c-remark,
  .usage {
    display: none;
  }

  .c-usage {
    width: auto;
  }
}
</style>
