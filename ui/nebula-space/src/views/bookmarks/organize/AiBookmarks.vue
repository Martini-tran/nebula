<script setup lang="ts">
/**
 * AI 整理书签：选范围和要做的事 → 分批交给 AI 出建议（边出边列）→ 逐项勾选 → 应用。
 * AI 只出建议不改数据；应用时先补建新目录、新标签，再每条书签一次 PUT（目录、标题、描述、标签一起改）。
 * 标签只加不删。不是事务：中途失败的会留在列表里，可以再点一次。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import FaviconMark from '../components/FaviconMark.vue'
import {
  countBookmarks,
  createFolder,
  createTag,
  fetchAllBookmarks,
  fetchBookmark,
  suggestBookmarks,
  updateBookmark,
} from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import { ApiError } from '../../../utils/request'
import { nextTagColor } from '../tagColors'
import {
  BookmarkStatus,
  type AiAction,
  type AiSuggestion,
  type Bookmark,
  type BookmarkSaveRequest,
  type EntityId,
  type Folder,
} from '../../../types/space'

/** 一次交给 AI 的书签数（后端上限 30） */
const BATCH = 25
const WORKERS = 2

type ScopeKind = 'uncategorized' | 'folder' | 'all' | 'ids'
type Field = 'folder' | 'title' | 'description'

interface Row {
  bookmark: Bookmark
  s: AiSuggestion
  on: Record<Field, boolean>
  /** 勾选的标签名 */
  tagsOn: Set<string>
}

const route = useRoute()
const space = useSpaceStore()

// ── 范围 ──

const queryIds = computed(() =>
  typeof route.query.ids === 'string' ? route.query.ids.split(',').filter(Boolean) : [],
)

const initialScope = (): ScopeKind => {
  if (queryIds.value.length) return 'ids'
  if (typeof route.query.folder === 'string') return 'folder'
  return 'uncategorized'
}

const scope = ref<ScopeKind>(initialScope())
const folderId = ref<string>(typeof route.query.folder === 'string' ? route.query.folder : '')
const scopeCount = ref<number | null>(null)

let countSeq = 0
const recount = async () => {
  const seq = ++countSeq
  scopeCount.value = null
  try {
    let n: number
    if (scope.value === 'ids') n = queryIds.value.length
    else if (scope.value === 'uncategorized') n = await countBookmarks({ folderId: 0, status: BookmarkStatus.NORMAL })
    else if (scope.value === 'folder') n = folderId.value ? await countBookmarks({ folderId: folderId.value, status: BookmarkStatus.NORMAL }) : 0
    else n = await countBookmarks({ status: BookmarkStatus.NORMAL })
    if (seq === countSeq) scopeCount.value = n
  } catch {
    if (seq === countSeq) scopeCount.value = 0
  }
}
watch([scope, folderId], recount, { immediate: true })

// 选了「某个目录」但还没挑，默认第一个
watch(scope, (value) => {
  if (value === 'folder' && !folderId.value && space.flat.length) folderId.value = String(space.flat[0]!.id)
})

const ACTIONS: { key: AiAction; label: string; hint: string; icon: string }[] = [
  { key: 'folder', label: '归目录', hint: '放进最合适的目录，必要时建议新目录', icon: 'lucide:folder-input' },
  { key: 'tags', label: '打标签', hint: '优先用已有标签，只加不删', icon: 'lucide:tags' },
  { key: 'title', label: '改标题', hint: '去掉「首页」、站点口号这类噪音', icon: 'lucide:type' },
  { key: 'description', label: '补描述', hint: '只给没有描述的书签写一句话', icon: 'lucide:text' },
]
const actions = ref(new Set<AiAction>(ACTIONS.map((a) => a.key)))
const toggleAction = (key: AiAction) => {
  const next = new Set(actions.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  actions.value = next
}

// ── 分析 ──

const phase = ref<'idle' | 'running' | 'done'>('idle')
const rows = ref<Row[]>([])
const total = ref(0)
const analyzed = ref(0)
const failed = ref(0)
const lastError = ref('')
const stopped = ref(false)
let stopRequested = false

const loadScope = async (): Promise<Bookmark[]> => {
  switch (scope.value) {
    case 'uncategorized':
      return fetchAllBookmarks({ folderId: 0, status: BookmarkStatus.NORMAL })
    case 'folder':
      return fetchAllBookmarks({ folderId: folderId.value, status: BookmarkStatus.NORMAL })
    case 'all':
      return fetchAllBookmarks({ status: BookmarkStatus.NORMAL })
    case 'ids': {
      const list = await Promise.all(queryIds.value.map((id) => fetchBookmark(id).catch(() => null)))
      return list.filter((b): b is Bookmark => Boolean(b))
    }
  }
}

const toRow = (bookmark: Bookmark, s: AiSuggestion): Row => ({
  bookmark,
  s,
  on: { folder: Boolean(s.folder), title: Boolean(s.title), description: Boolean(s.description) },
  tagsOn: new Set((s.tags ?? []).map((t) => t.name)),
})

const analyze = async () => {
  phase.value = 'running'
  rows.value = []
  appliedCount.value = 0
  analyzed.value = 0
  failed.value = 0
  lastError.value = ''
  stopped.value = false
  stopRequested = false
  let list: Bookmark[]
  try {
    list = await loadScope()
  } catch (error) {
    lastError.value = errorText(error, '读取书签失败')
    phase.value = 'idle'
    return
  }
  total.value = list.length
  const order = new Map(list.map((b, i) => [String(b.id), i]))
  const byId = new Map(list.map((b) => [String(b.id), b]))
  const queue: Bookmark[][] = []
  for (let i = 0; i < list.length; i += BATCH) queue.push(list.slice(i, i + BATCH))
  const chosen = ACTIONS.map((a) => a.key).filter((key) => actions.value.has(key))

  const worker = async () => {
    while (!stopRequested && queue.length) {
      const batch = queue.shift()!
      try {
        const result = await suggestBookmarks(batch.map((b) => b.id), chosen)
        const fresh = result
          .filter((s) => byId.has(String(s.bookmarkId)))
          .map((s) => toRow(byId.get(String(s.bookmarkId))!, s))
        rows.value = [...rows.value, ...fresh].sort(
          (a, b) => (order.get(String(a.bookmark.id)) ?? 0) - (order.get(String(b.bookmark.id)) ?? 0),
        )
      } catch (error) {
        failed.value += batch.length
        lastError.value = errorText(error, 'AI 分析失败')
        // 没开 AI、超了次数、没配密钥：后面的批次也一样会失败，直接停
        const code = error instanceof ApiError ? error.code : undefined
        if (code === 503 || code === 429 || lastError.value.includes('密钥')) stopRequested = true
      }
      analyzed.value += batch.length
    }
  }
  await Promise.all(Array.from({ length: WORKERS }, worker))
  stopped.value = stopRequested && analyzed.value < total.value
  phase.value = 'done'
}

const stop = () => {
  stopRequested = true
}
onBeforeUnmount(stop)

const reset = () => {
  phase.value = 'idle'
  rows.value = []
}

const percent = computed(() => (total.value ? Math.round((analyzed.value / total.value) * 100) : 0))

// ── 勾选 ──

const has = (row: Row, field: Field | 'tags') => (field === 'tags' ? Boolean(row.s.tags?.length) : Boolean(row.s[field]))

const hasSelection = (row: Row) =>
  (['folder', 'title', 'description'] as Field[]).some((f) => has(row, f) && row.on[f]) || row.tagsOn.size > 0

const rowState = (row: Row): 'all' | 'some' | 'none' => {
  const fields = (['folder', 'title', 'description'] as Field[]).filter((f) => has(row, f))
  const onCount = fields.filter((f) => row.on[f]).length + row.tagsOn.size
  const allCount = fields.length + (row.s.tags?.length ?? 0)
  return onCount === 0 ? 'none' : onCount === allCount ? 'all' : 'some'
}

const setRow = (row: Row, value: boolean) => {
  row.on = { folder: value && has(row, 'folder'), title: value && has(row, 'title'), description: value && has(row, 'description') }
  row.tagsOn = new Set(value ? (row.s.tags ?? []).map((t) => t.name) : [])
}

const toggleTag = (row: Row, name: string) => {
  const next = new Set(row.tagsOn)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  row.tagsOn = next
}

/** 按类别统计：有几条建议、勾了几条，用来一键全选 / 全不选某一类 */
const kinds = computed(() =>
  (
    [
      { key: 'folder', label: '目录' },
      { key: 'tags', label: '标签' },
      { key: 'title', label: '标题' },
      { key: 'description', label: '描述' },
    ] as const
  )
    .map((k) => {
      const withIt = rows.value.filter((r) => has(r, k.key))
      const on = withIt.filter((r) => (k.key === 'tags' ? r.tagsOn.size > 0 : r.on[k.key])).length
      return { ...k, count: withIt.length, on }
    })
    .filter((k) => k.count > 0),
)

const toggleKind = (key: Field | 'tags', value: boolean) => {
  for (const row of rows.value) {
    if (!has(row, key)) continue
    if (key === 'tags') row.tagsOn = new Set(value ? (row.s.tags ?? []).map((t) => t.name) : [])
    else row.on = { ...row.on, [key]: value }
  }
}

const selectedRows = computed(() => rows.value.filter(hasSelection))

// ── 应用 ──

const applying = ref(false)
/** 本轮分析后已应用的条数 */
const appliedCount = ref(0)
const applyDone = ref(0)
const applyTotal = ref(0)
const applyStep = ref('')

/** 按「前端 / 工程化」逐级找目录，没有就建；名字比较不分大小写（与后端一致） */
const ensureFolderPath = async (path: string): Promise<EntityId> => {
  let parentId: EntityId = 0
  for (const name of path.split('/').map((s) => s.trim()).filter(Boolean)) {
    const siblings: Folder[] = String(parentId) === '0' ? space.folders : (space.findFolder(parentId)?.children ?? [])
    const found = siblings.find((f) => f.name.toLowerCase() === name.toLowerCase())
    if (found) {
      parentId = found.id
      continue
    }
    applyStep.value = `新建目录「${name}」`
    parentId = await createFolder({ parentId, name })
    await space.reload()
  }
  return parentId
}

const ensureTag = async (name: string): Promise<EntityId> => {
  const existing = space.tags.find((t) => t.name.toLowerCase() === name.toLowerCase())
  if (existing) return existing.id
  applyStep.value = `新建标签「${name}」`
  const id = await createTag({ name, color: nextTagColor(space.tags.length) })
  await space.reload()
  return id
}

const apply = async () => {
  const picked = selectedRows.value
  if (!picked.length || applying.value) return
  applying.value = true
  applyDone.value = 0
  applyTotal.value = picked.length
  try {
    const folderIds = new Map<string, EntityId>()
    for (const row of picked) {
      const target = row.on.folder ? row.s.folder : null
      if (target && (target.id === null || target.id === undefined) && !folderIds.has(target.path)) {
        folderIds.set(target.path, await ensureFolderPath(target.path))
      }
    }
    const tagIds = new Map<string, EntityId>()
    for (const row of picked) {
      for (const tag of row.s.tags ?? []) {
        if (!row.tagsOn.has(tag.name)) continue
        const key = tag.name.toLowerCase()
        if (!tagIds.has(key)) tagIds.set(key, tag.id ?? (await ensureTag(tag.name)))
      }
    }

    applyStep.value = '更新书签'
    const applied = new Set<string>()
    let failedCount = 0
    let firstError = ''
    for (let i = 0; i < picked.length; i += 4) {
      const chunk = picked.slice(i, i + 4)
      const results = await Promise.allSettled(
        chunk.map(async (row) => {
          const body: Partial<BookmarkSaveRequest> = {}
          if (row.on.folder && row.s.folder) body.folderId = row.s.folder.id ?? folderIds.get(row.s.folder.path)
          if (row.on.title && row.s.title) body.title = row.s.title
          if (row.on.description && row.s.description) body.description = row.s.description
          if (row.tagsOn.size) {
            const ids = new Set((row.bookmark.tags ?? []).map((t) => String(t.id)))
            for (const name of row.tagsOn) {
              const id = tagIds.get(name.toLowerCase())
              if (id !== undefined) ids.add(String(id))
            }
            body.tagIds = [...ids]
          }
          await updateBookmark(row.bookmark.id, body)
          applied.add(String(row.bookmark.id))
        }),
      )
      for (const r of results) {
        if (r.status === 'rejected') {
          failedCount += 1
          firstError ||= errorText(r.reason, '更新失败')
        }
      }
      applyDone.value += chunk.length
    }
    rows.value = rows.value.filter((r) => !applied.has(String(r.bookmark.id)))
    appliedCount.value += applied.size
    if (failedCount) toast.error(`${applied.size} 条已整理，${failedCount} 条失败：${firstError}`)
    else toast.ok(`已整理 ${applied.size} 条书签`)
  } catch (error) {
    toast.error(`在「${applyStep.value}」这一步失败：${errorText(error, '应用失败')}`)
  } finally {
    applying.value = false
    applyStep.value = ''
    await space.reload()
  }
}

const folderLabel = (id: EntityId) => space.folderPath(id)
</script>

<template>
  <div class="ab">
    <section v-if="phase === 'idle'" class="ab__setup surface">
      <div class="ab__group">
        <h3 class="ab__label">整理哪些书签</h3>
        <div class="ab__scopes" role="radiogroup" aria-label="整理范围">
          <label v-if="queryIds.length" class="chip" :class="{ on: scope === 'ids' }">
            <input v-model="scope" type="radio" value="ids" />选中的 {{ queryIds.length }} 条
          </label>
          <label class="chip" :class="{ on: scope === 'uncategorized' }">
            <input v-model="scope" type="radio" value="uncategorized" />未分类
          </label>
          <label class="chip" :class="{ on: scope === 'folder' }">
            <input v-model="scope" type="radio" value="folder" :disabled="!space.flat.length" />某个目录
          </label>
          <label class="chip" :class="{ on: scope === 'all' }">
            <input v-model="scope" type="radio" value="all" />全部书签
          </label>
          <select v-if="scope === 'folder'" v-model="folderId" class="field__input ab__folder" aria-label="目录">
            <option v-for="f in space.flat" :key="f.id" :value="String(f.id)">{{ f.path }}</option>
          </select>
        </div>
        <p class="ab__hint">
          <template v-if="scopeCount === null"><Icon icon="lucide:loader-circle" class="spin" />统计中…</template>
          <template v-else>共 {{ scopeCount }} 条正常状态的书签（已归档、已失效的不整理）</template>
        </p>
      </div>

      <div class="ab__group">
        <h3 class="ab__label">要做的事</h3>
        <div class="ab__actions">
          <button
            v-for="a in ACTIONS"
            :key="a.key"
            type="button"
            class="act"
            :class="{ on: actions.has(a.key) }"
            :aria-pressed="actions.has(a.key)"
            @click="toggleAction(a.key)"
          >
            <Icon :icon="actions.has(a.key) ? 'lucide:square-check' : 'lucide:square'" class="act__check" />
            <span>
              <b><Icon :icon="a.icon" />{{ a.label }}</b>
              <small>{{ a.hint }}</small>
            </span>
          </button>
        </div>
      </div>

      <div class="ab__foot">
        <p v-if="lastError" class="form__error">{{ lastError }}</p>
        <button class="btn btn--primary" type="button" :disabled="!scopeCount || !actions.size" @click="analyze">
          <Icon icon="lucide:sparkles" />开始分析
        </button>
      </div>
    </section>

    <template v-else>
      <section class="ab__status surface">
        <div class="ab__bar" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
          <i :style="{ width: `${percent}%` }" />
        </div>
        <div class="ab__status-row">
          <p>
            <Icon v-if="phase === 'running'" icon="lucide:loader-circle" class="spin" />
            <template v-if="phase === 'running'">AI 正在分析 {{ analyzed }} / {{ total }} 条，建议会陆续出现</template>
            <template v-else-if="stopped">已停止：分析了 {{ analyzed }} / {{ total }} 条，{{ rows.length + appliedCount }} 条有建议</template>
            <template v-else>分析完成：{{ total }} 条里 {{ rows.length + appliedCount }} 条有建议</template>
            <template v-if="appliedCount">，已应用 {{ appliedCount }} 条</template>
          </p>
          <button v-if="phase === 'running'" class="btn btn--ghost" type="button" @click="stop">
            <Icon icon="lucide:square" />停止
          </button>
          <button v-else class="btn btn--ghost" type="button" :disabled="applying" @click="reset">
            <Icon icon="lucide:rotate-ccw" />重新选择
          </button>
        </div>
        <p v-if="failed" class="form__error">{{ failed }} 条没分析成功：{{ lastError }}</p>
      </section>

      <section v-if="rows.length" class="ab__review">
        <header class="ab__review-head">
          <div class="ab__kinds" aria-label="按类别勾选">
            <button
              v-for="k in kinds"
              :key="k.key"
              type="button"
              class="kind"
              :class="{ on: k.on === k.count, some: k.on > 0 && k.on < k.count }"
              :title="k.on === k.count ? `取消全部${k.label}建议` : `勾选全部${k.label}建议`"
              @click="toggleKind(k.key, k.on !== k.count)"
            >
              <Icon :icon="k.on === k.count ? 'lucide:square-check' : k.on ? 'lucide:square-minus' : 'lucide:square'" />
              {{ k.label }} {{ k.on }}/{{ k.count }}
            </button>
          </div>
          <button class="btn btn--primary" type="button" :disabled="!selectedRows.length || applying" @click="apply">
            <Icon :icon="applying ? 'lucide:loader-circle' : 'lucide:check'" :class="{ spin: applying }" />
            <template v-if="applying">{{ applyStep }} {{ applyDone }}/{{ applyTotal }}</template>
            <template v-else>应用选中的 {{ selectedRows.length }} 条</template>
          </button>
        </header>

        <ul class="ab__rows">
          <li v-for="row in rows" :key="row.bookmark.id" class="row surface" :class="{ 'row--off': !hasSelection(row) }">
            <button
              type="button"
              class="row__check"
              :aria-label="rowState(row) === 'all' ? '不采纳这条的建议' : '采纳这条的全部建议'"
              @click="setRow(row, rowState(row) !== 'all')"
            >
              <Icon :icon="rowState(row) === 'all' ? 'lucide:square-check' : rowState(row) === 'some' ? 'lucide:square-minus' : 'lucide:square'" />
            </button>
            <FaviconMark :bookmark="row.bookmark" size="1.9rem" />
            <div class="row__body">
              <a class="row__title" :href="row.bookmark.url" target="_blank" rel="noopener noreferrer">{{ row.bookmark.title }}</a>
              <span class="row__url">{{ row.bookmark.domain || row.bookmark.url }}</span>

              <dl class="row__changes">
                <template v-if="row.s.folder">
                  <dt>目录</dt>
                  <dd>
                    <label class="change" :class="{ off: !row.on.folder }">
                      <input v-model="row.on.folder" type="checkbox" />
                      <span class="old">{{ folderLabel(row.bookmark.folderId) }}</span>
                      <Icon icon="lucide:arrow-right" class="arrow" />
                      <span>{{ row.s.folder.path }}</span>
                      <em v-if="row.s.folder.id === null || row.s.folder.id === undefined" class="new">新建</em>
                    </label>
                  </dd>
                </template>
                <template v-if="row.s.tags?.length">
                  <dt>标签</dt>
                  <dd class="row__tags">
                    <button
                      v-for="tag in row.s.tags"
                      :key="tag.name"
                      type="button"
                      class="tagchip"
                      :class="{ off: !row.tagsOn.has(tag.name) }"
                      :aria-pressed="row.tagsOn.has(tag.name)"
                      @click="toggleTag(row, tag.name)"
                    >
                      <span class="dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />+{{ tag.name }}
                      <em v-if="tag.id === null || tag.id === undefined" class="new">新</em>
                    </button>
                  </dd>
                </template>
                <template v-if="row.s.title">
                  <dt>标题</dt>
                  <dd>
                    <label class="change" :class="{ off: !row.on.title }">
                      <input v-model="row.on.title" type="checkbox" />
                      <span class="old old--strike">{{ row.bookmark.title }}</span>
                      <Icon icon="lucide:arrow-right" class="arrow" />
                      <span>{{ row.s.title }}</span>
                    </label>
                  </dd>
                </template>
                <template v-if="row.s.description">
                  <dt>描述</dt>
                  <dd>
                    <label class="change" :class="{ off: !row.on.description }">
                      <input v-model="row.on.description" type="checkbox" />
                      <span>{{ row.s.description }}</span>
                    </label>
                  </dd>
                </template>
              </dl>
            </div>
          </li>
        </ul>
      </section>

      <p v-else-if="phase === 'done' && appliedCount" class="ab__empty">
        <Icon icon="lucide:circle-check" />建议都已应用。
      </p>
      <p v-else-if="phase === 'done' && !failed" class="ab__empty">
        <Icon icon="lucide:circle-check" />这些书签已经整理得不错，AI 没有要改的。
      </p>
    </template>
  </div>
</template>

<style scoped>
.ab {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ab__setup {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1.35rem;
  border-radius: var(--radius-lg);
}

.ab__group {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.ab__label {
  font-size: 0.88rem;
  font-weight: 700;
}

.ab__scopes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 0.4rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.88rem;
  cursor: pointer;
}

.chip input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.chip:has(input:focus-visible) {
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.chip:has(input:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}

.chip.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.ab__folder {
  width: auto;
  min-width: 12rem;
  max-width: 100%;
  padding: 0.4rem 0.6rem;
}

.ab__hint {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.ab__actions {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(13rem, 1fr));
  gap: 0.5rem;
}

.act {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.act.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.act__check {
  flex: none;
  margin-top: 0.15rem;
  color: var(--color-text-secondary);
}

.act.on .act__check {
  color: var(--color-brand);
}

.act span {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.act b {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.act small {
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.ab__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}

.ab__foot .form__error {
  flex: 1;
}

.ab__status {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem 1.2rem;
  border-radius: var(--radius-lg);
}

.ab__bar {
  height: 0.4rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.ab__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 0.3s ease;
}

.ab__status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
}

.ab__status-row p {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.9rem;
}

.ab__status-row .btn {
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.ab__review {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ab__review-head {
  position: sticky;
  top: calc(var(--header-height) + 0.5rem);
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0.6rem 0.55rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.ab__kinds {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.kind {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.kind.on,
.kind.some {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.kind.on {
  background: var(--color-brand-soft);
}

.ab__rows {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.8rem 1rem;
  border-radius: var(--radius-lg);
  transition: opacity 0.2s ease;
}

.row--off {
  opacity: 0.6;
}

.row__check {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 1.6rem;
  height: 1.9rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  cursor: pointer;
}

.row__check svg {
  width: 1.15rem;
  height: 1.15rem;
}

.row__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.row__title {
  overflow: hidden;
  font-weight: 600;
  color: var(--color-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__title:hover {
  color: var(--color-brand);
}

.row__url {
  overflow: hidden;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__changes {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr);
  gap: 0.4rem 0.6rem;
  margin: 0.55rem 0 0;
  font-size: 0.86rem;
}

.row__changes dt {
  padding-top: 0.15rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.row__changes dd {
  min-width: 0;
  margin: 0;
}

.change {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  cursor: pointer;
}

.change input {
  accent-color: var(--color-brand);
}

.change.off > span,
.change.off > .arrow,
.change.off > .new {
  opacity: 0.45;
}

.old {
  color: var(--color-text-secondary);
}

.old--strike {
  text-decoration: line-through;
}

.arrow {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--color-text-secondary);
}

.new {
  padding: 0 0.35rem;
  border-radius: var(--radius-sm);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 600;
}

.row__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.tagchip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.12rem 0.55rem;
  border: 1px solid var(--color-brand);
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-text-primary);
  font-size: 0.8rem;
  cursor: pointer;
}

.tagchip.off {
  border-color: var(--color-border);
  background: none;
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.ab__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 2rem;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .ab__review-head .btn {
    width: 100%;
  }
}
</style>
