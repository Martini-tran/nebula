<script setup lang="ts">
/**
 * 设定库：设定挂在作品下，页内切换作品（?workId=），选中条目记在 ?entry= 里便于直接打开。
 *
 * 左列表右详情；窄屏时两者择一显示。编辑中有未保存的改动时，切条目/切作品/离开页面都会先确认。
 */
import { computed, ref, watch } from 'vue'
import {
  NavigationFailureType,
  isNavigationFailure,
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
} from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchWorks } from '../../api/work'
import { createLoreEntry, deleteLoreEntry, fetchLoreEntries, fetchLoreEntry, updateLoreEntry } from '../../api/lore'
import { ApiError } from '../../utils/request'
import StateBlock from '../../components/StateBlock.vue'
import LoreForm from './components/LoreForm.vue'
import LoreView from './components/LoreView.vue'
import type { WorkListItem } from '../../types/work'
import { LORE_KIND_ICON, LORE_KIND_LABEL, type LoreEntry, type LoreKind, type LoreSaveRequest } from '../../types/lore'

const route = useRoute()
const router = useRouter()

const LAST_WORK_KEY = 'nebula-scribe:lore-work'

const queryOf = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '')
const workId = computed(() => queryOf('workId'))
const entryId = computed(() => queryOf('entry'))

// ----------------------------------------------------------------- 作品

const works = ref<WorkListItem[]>([])
const worksLoading = ref(true)
const worksFailed = ref(false)

const currentWork = computed(() => works.value.find((w) => String(w.id) === workId.value) ?? null)

const readLastWork = () => {
  try {
    return localStorage.getItem(LAST_WORK_KEY) ?? ''
  } catch {
    return ''
  }
}

const rememberWork = (id: string) => {
  try {
    localStorage.setItem(LAST_WORK_KEY, id)
  } catch {
    // 无痕模式等场景存不了，下次回退到最近编辑的作品即可
  }
}

/** 地址里的作品不可用（没带、已删除、不属于当前账号）时，回退到上次看的，再回退到最近编辑的 */
const ensureWork = () => {
  if (works.value.length === 0 || currentWork.value) {
    return
  }
  const has = (id: string) => works.value.some((w) => String(w.id) === id)
  const last = readLastWork()
  router.replace({ query: { workId: has(last) ? last : String(works.value[0].id) } })
}

const loadWorks = async () => {
  worksLoading.value = true
  worksFailed.value = false
  try {
    const page = await fetchWorks({ pageNum: 1, pageSize: 100, sort: 'recent' })
    works.value = page.records
    ensureWork()
  } catch {
    worksFailed.value = true
  } finally {
    worksLoading.value = false
  }
}

loadWorks()
watch(workId, ensureWork)

const onPickWork = async (event: Event) => {
  const select = event.target as HTMLSelectElement
  const result = await router.replace({ query: { workId: select.value } })
  // 用户取消了放弃修改，下拉框退回当前作品
  if (isNavigationFailure(result, NavigationFailureType.aborted)) {
    select.value = workId.value
  }
}

// ----------------------------------------------------------------- 条目列表

const entries = ref<LoreEntry[]>([])
const entriesLoading = ref(false)
const entriesFailed = ref(false)

const kind = ref<LoreKind | ''>('')
const keyword = ref('')

const loadEntries = async () => {
  const id = workId.value
  entriesLoading.value = true
  entriesFailed.value = false
  try {
    const list = await fetchLoreEntries(id)
    if (id === workId.value) {
      entries.value = list
    }
  } catch {
    if (id === workId.value) {
      entriesFailed.value = true
    }
  } finally {
    if (id === workId.value) {
      entriesLoading.value = false
    }
  }
}

watch(
  () => currentWork.value?.id,
  (id) => {
    entries.value = []
    keyword.value = ''
    if (id != null) {
      rememberWork(String(id))
      loadEntries()
    }
  },
)

const kindFilters = computed(() => {
  const count = (value: LoreKind | '') => entries.value.filter((e) => !value || e.kind === value).length
  return [
    { value: '' as const, label: '全部', icon: 'lucide:layers', count: count('') },
    ...(Object.keys(LORE_KIND_LABEL) as LoreKind[]).map((value) => ({
      value,
      label: LORE_KIND_LABEL[value],
      icon: LORE_KIND_ICON[value],
      count: count(value),
    })),
  ]
})

/** 类型与关键词都在前端筛：一本书的设定量不大，全量取回还能顺带算出各类型计数 */
const filtered = computed(() => {
  const word = keyword.value.trim()
  return entries.value.filter((e) => {
    if (kind.value && e.kind !== kind.value) {
      return false
    }
    if (!word) {
      return true
    }
    return [e.name, e.summary ?? '', ...(e.aliases ?? []), ...(e.tags ?? [])].some((text) => text.includes(word))
  })
})

// ----------------------------------------------------------------- 右侧面板

type Mode = 'view' | 'edit' | 'create'
const mode = ref<Mode>('view')
const formDirty = ref(false)
const saving = ref(false)
const nameError = ref('')
const formError = ref('')

const detail = ref<LoreEntry | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const deleting = ref(false)
const deleteError = ref('')

const showPanel = computed(() => Boolean(entryId.value) || mode.value === 'create')

const resetFormState = () => {
  formDirty.value = false
  nameError.value = ''
  formError.value = ''
}

const confirmDiscard = () =>
  mode.value === 'view' || !formDirty.value || window.confirm('有未保存的修改，确定放弃吗？')

// 切条目、切作品、浏览器前进后退都会改地址：先确认，确认后回到查看态
onBeforeRouteUpdate((to, from) => {
  if (to.fullPath === from.fullPath) {
    return true
  }
  if (!confirmDiscard()) {
    return false
  }
  mode.value = 'view'
  resetFormState()
  return true
})
onBeforeRouteLeave(() => confirmDiscard())

const loadDetail = async () => {
  const wid = workId.value
  const eid = entryId.value
  detail.value = null
  detailError.value = ''
  deleteError.value = ''
  if (!eid || !currentWork.value) {
    return
  }
  detailLoading.value = true
  try {
    const data = await fetchLoreEntry(wid, eid)
    if (wid === workId.value && eid === entryId.value) {
      detail.value = data
    }
  } catch (error) {
    if (eid === entryId.value) {
      detailError.value = error instanceof Error ? error.message : '加载失败'
    }
  } finally {
    if (eid === entryId.value) {
      detailLoading.value = false
    }
  }
}

watch([() => currentWork.value?.id, entryId], loadDetail, { immediate: true })

const select = (entry: LoreEntry) => {
  router.replace({ query: { workId: workId.value, entry: String(entry.id) } })
}

const startCreate = async () => {
  if (entryId.value) {
    const result = await router.replace({ query: { workId: workId.value } })
    if (isNavigationFailure(result, NavigationFailureType.aborted)) {
      return
    }
  } else if (!confirmDiscard()) {
    return
  }
  resetFormState()
  mode.value = 'create'
}

const startEdit = () => {
  resetFormState()
  mode.value = 'edit'
}

const cancelForm = () => {
  resetFormState()
  mode.value = 'view'
}

/** 窄屏「返回列表」：新建中直接收起，查看/编辑某条则清掉地址里的 entry */
const closePanel = () => {
  if (entryId.value) {
    router.replace({ query: { workId: workId.value } })
    return
  }
  if (confirmDiscard()) {
    cancelForm()
  }
}

/** 名称冲突落在名称框下，其余错误放表单底部 */
const applySaveError = (error: unknown) => {
  const message = error instanceof Error ? error.message : '保存失败，请稍后重试'
  if (error instanceof ApiError && error.code === 409) {
    nameError.value = message
  } else {
    formError.value = message
  }
}

const onCreate = async (body: LoreSaveRequest) => {
  nameError.value = ''
  formError.value = ''
  saving.value = true
  try {
    const created = await createLoreEntry(workId.value, body)
    // 先落回查看态，再改地址，避免路由守卫把刚保存的表单当成未保存
    cancelForm()
    await loadEntries()
    await router.replace({ query: { workId: workId.value, entry: String(created.id) } })
  } catch (error) {
    applySaveError(error)
  } finally {
    saving.value = false
  }
}

const onUpdate = async (body: LoreSaveRequest) => {
  if (!detail.value) {
    return
  }
  nameError.value = ''
  formError.value = ''
  saving.value = true
  try {
    detail.value = await updateLoreEntry(workId.value, detail.value.id, body)
    cancelForm()
    // 固定与更新时间决定列表顺序，整表重取最省心
    await loadEntries()
  } catch (error) {
    applySaveError(error)
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!detail.value) {
    return
  }
  const id = detail.value.id
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteLoreEntry(workId.value, id)
    entries.value = entries.value.filter((e) => e.id !== id)
    await router.replace({ query: { workId: workId.value } })
  } catch (error) {
    deleteError.value = error instanceof Error ? error.message : '删除失败，请稍后重试'
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1 class="page-title">设定库</h1>
        <p class="page-subtitle">人物、地点、势力、道具与世界观规则，按作品分开整理。</p>
      </div>
      <div v-if="works.length" class="head__actions">
        <label class="picker">
          <span class="sr-only">当前作品</span>
          <Icon icon="lucide:book-open" class="picker__icon" />
          <select class="picker__select" :value="workId" @change="onPickWork">
            <option v-for="item in works" :key="item.id" :value="String(item.id)">{{ item.title }}</option>
          </select>
          <Icon icon="lucide:chevron-down" class="picker__caret" />
        </label>
        <button class="btn btn--primary" type="button" :disabled="!currentWork" @click="startCreate">
          <Icon icon="lucide:plus" />
          新建设定
        </button>
      </div>
    </header>

    <StateBlock v-if="worksLoading" state="loading" />
    <StateBlock
      v-else-if="worksFailed"
      state="error"
      description="稍后重试，或检查后端服务是否已启动。"
      action-label="重试"
      @action="loadWorks"
    />
    <StateBlock
      v-else-if="works.length === 0"
      state="empty"
      title="还没有作品"
      description="设定挂在作品下，先建一部作品，再来整理人物和世界观。"
      action-label="去新建作品"
      @action="router.push('/works')"
    />

    <template v-else-if="currentWork">
      <div class="toolbar">
        <div class="search">
          <Icon icon="lucide:search" class="search__icon" />
          <input
            v-model="keyword"
            class="search__input"
            type="search"
            placeholder="搜索名称、别名、概述或标签"
            aria-label="搜索设定"
          />
        </div>
        <div class="filters" role="group" aria-label="按类型筛选">
          <button
            v-for="item in kindFilters"
            :key="item.value"
            type="button"
            class="chip"
            :class="{ 'chip--active': kind === item.value }"
            :aria-pressed="kind === item.value"
            @click="kind = item.value"
          >
            <Icon :icon="item.icon" />
            {{ item.label }}
            <span class="chip__count">{{ item.count }}</span>
          </button>
        </div>
      </div>

      <div class="lore-layout" :class="{ 'lore-layout--panel': showPanel }">
        <section class="list-col" aria-label="设定列表">
          <StateBlock v-if="entriesLoading && entries.length === 0" state="loading" />
          <StateBlock
            v-else-if="entriesFailed"
            state="error"
            description="稍后重试，或检查后端服务是否已启动。"
            action-label="重试"
            @action="loadEntries"
          />
          <StateBlock
            v-else-if="entries.length === 0"
            state="empty"
            title="这部作品还没有设定"
            description="从主角开始，把人物、地点和世界观规则记下来。"
            action-label="新建设定"
            @action="startCreate"
          />
          <StateBlock
            v-else-if="filtered.length === 0"
            state="empty"
            title="没有匹配的设定"
            description="换个关键词或类型试试。"
          />
          <ul v-else class="list">
            <li v-for="item in filtered" :key="item.id">
              <button
                type="button"
                class="item"
                :class="{ 'item--active': String(item.id) === entryId }"
                :aria-current="String(item.id) === entryId ? 'true' : undefined"
                @click="select(item)"
              >
                <span class="item__icon" :title="LORE_KIND_LABEL[item.kind]">
                  <Icon :icon="LORE_KIND_ICON[item.kind]" />
                </span>
                <span class="item__body">
                  <span class="item__head">
                    <span class="item__name">{{ item.name }}</span>
                    <Icon v-if="item.pinned" icon="lucide:pin" class="item__pin" aria-label="固定" />
                  </span>
                  <span v-if="item.aliases?.length" class="item__aliases">{{ item.aliases.join('、') }}</span>
                  <span v-if="item.summary" class="item__summary">{{ item.summary }}</span>
                </span>
              </button>
            </li>
          </ul>
        </section>

        <section class="panel-col surface" aria-label="设定详情">
          <button class="btn btn--quiet panel-back" type="button" @click="closePanel">
            <Icon icon="lucide:arrow-left" />
            返回列表
          </button>

          <template v-if="mode === 'create'">
            <h2 class="panel-title">新建设定</h2>
            <LoreForm
              :default-kind="kind || 'character'"
              :saving="saving"
              :name-error="nameError"
              :form-error="formError"
              @submit="onCreate"
              @cancel="cancelForm"
              @dirty="formDirty = $event"
            />
          </template>

          <template v-else-if="entryId">
            <StateBlock v-if="detailLoading" state="loading" />
            <StateBlock
              v-else-if="!detail"
              state="error"
              :title="detailError || '加载失败'"
              description="这条设定可能已被删除。"
            />
            <template v-else-if="mode === 'edit'">
              <h2 class="panel-title">编辑设定</h2>
              <LoreForm
                :entry="detail"
                :saving="saving"
                :name-error="nameError"
                :form-error="formError"
                @submit="onUpdate"
                @cancel="cancelForm"
                @dirty="formDirty = $event"
              />
            </template>
            <LoreView
              v-else
              :entry="detail"
              :deleting="deleting"
              :delete-error="deleteError"
              @edit="startEdit"
              @delete="onDelete"
            />
          </template>

          <div v-else class="placeholder">
            <Icon icon="lucide:book-marked" class="placeholder__icon" />
            <p>选一条设定查看，或新建一条。</p>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.head__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.head__actions svg {
  width: 1rem;
  height: 1rem;
}

.picker {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.picker__icon,
.picker__caret {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-secondary);
  pointer-events: none;
}

.picker__icon {
  left: 0.7rem;
}

.picker__caret {
  right: 0.6rem;
}

.picker__select {
  appearance: none;
  max-width: 16rem;
  min-height: 2.5rem;
  padding: 0.5rem 2rem 0.5rem 2.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.picker__select:hover {
  border-color: var(--color-brand);
}

.picker__select:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.search {
  position: relative;
  flex: 1 1 16rem;
  min-width: 12rem;
}

.search__icon {
  position: absolute;
  left: 0.7rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1rem;
  height: 1rem;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.search__input {
  width: 100%;
  padding: 0.5rem 0.75rem 0.5rem 2.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.search__input:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.chip svg {
  width: 0.9rem;
  height: 0.9rem;
}

.chip:hover {
  color: var(--color-text-primary);
  border-color: var(--color-brand);
}

.chip--active {
  color: var(--color-brand);
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.chip__count {
  min-width: 1.1rem;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
  opacity: 0.75;
}

.lore-layout {
  display: grid;
  grid-template-columns: 22rem minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.item {
  display: flex;
  gap: 0.75rem;
  width: 100%;
  padding: 0.85rem 0.95rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.item:hover {
  border-color: var(--color-brand);
}

.item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.item--active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.item__icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.item__icon svg {
  width: 1rem;
  height: 1rem;
}

.item__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.item__head {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.item__name {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__pin {
  flex-shrink: 0;
  width: 0.85rem;
  height: 0.85rem;
  color: var(--color-accent-text);
}

.item__aliases {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__summary {
  display: -webkit-box;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.panel-col {
  position: sticky;
  top: 5rem;
  max-height: calc(100vh - 6rem);
  overflow-y: auto;
}

.panel-back {
  display: none;
  margin: 1rem 1rem 0;
  padding-inline: 0.4rem;
}

.panel-back svg {
  width: 1rem;
  height: 1rem;
}

.panel-title {
  padding: 1.35rem 1.35rem 0;
  font-size: 1.05rem;
  font-weight: 700;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 4rem 1.5rem;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.placeholder__icon {
  width: 2rem;
  height: 2rem;
  opacity: 0.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* 窄屏：列表与详情择一显示 */
@media (max-width: 900px) {
  .lore-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .lore-layout--panel .list-col,
  .lore-layout:not(.lore-layout--panel) .panel-col {
    display: none;
  }

  .panel-col {
    position: static;
    max-height: none;
  }

  .panel-back {
    display: inline-flex;
  }

  .picker__select {
    max-width: 11rem;
  }
}
</style>
