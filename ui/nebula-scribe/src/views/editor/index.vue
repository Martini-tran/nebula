<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { createChapter, fetchChapter, fetchChapters, fetchWorkDetail, saveChapter } from '../../api/work'
import StateBlock from '../../components/StateBlock.vue'
import EditorRail from './components/EditorRail.vue'
import { countWords, formatRelative } from '../../utils/format'
import { ApiError } from '../../utils/request'
import {
  CHAPTER_STATUS_LABEL,
  type ChapterDetail,
  type ChapterListItem,
  type ChapterSaveRequest,
  type ChapterStatus,
  type EntityId,
} from '../../types/work'

/**
 * 写作台（纯文本版）：左栏章节目录，中间标题 + 梗概 + 正文。
 *
 * 丢稿等于事故，所以保存链路做了三层：
 * 1. 停笔 1.2 秒自动保存，Ctrl/⌘+S 立即保存，切章/离开前先保存完；
 * 2. 修订号比对，别处改过就停下自动保存，让作者选载入还是覆盖；
 * 3. 每次输入都在本机留一份草稿，服务端没存上也能在下次打开时恢复。
 */

const route = useRoute()
const router = useRouter()

const workId = computed(() => String(route.params.workId))
const chapterId = computed(() => (route.params.chapterId ? String(route.params.chapterId) : null))

const AUTOSAVE_DELAY = 1200
const STATUS_OPTIONS = Object.entries(CHAPTER_STATUS_LABEL) as Array<[ChapterStatus, string]>

type Fields = { title: string; synopsis: string; content: string; status: ChapterStatus }
type FieldKey = keyof Fields
const FIELD_KEYS: FieldKey[] = ['title', 'synopsis', 'content', 'status']

const workTitle = ref('')
const chapters = ref<ChapterListItem[]>([])
const pageLoading = ref(true)
const pageError = ref('')

const chapter = ref<ChapterDetail | null>(null)
const chapterLoading = ref(false)
const chapterError = ref('')

/** 编辑中的内容与最近一次存上服务端的内容；两者之差就是要保存的字段 */
const draft = reactive<Fields>({ title: '', synopsis: '', content: '', status: 'outline' })
const saved = reactive<Fields>({ title: '', synopsis: '', content: '', status: 'outline' })
const revision = ref<number | string>(0)

const saving = ref(false)
const saveError = ref('')
const conflict = ref(false)
const lastSavedAt = ref<string | null>(null)
const creating = ref(false)
const railOpen = ref(false)

/** 本机草稿：进入章节时发现与服务端不同，就提示恢复 */
const localDraft = ref<(Fields & { at: string }) | null>(null)

const titleEl = ref<HTMLTextAreaElement | null>(null)
const synopsisEl = ref<HTMLTextAreaElement | null>(null)
const contentEl = ref<HTMLTextAreaElement | null>(null)

const changedKeys = computed(() => FIELD_KEYS.filter((key) => draft[key] !== saved[key]))
const dirty = computed(() => changedKeys.value.length > 0)
/** 标题清空时先不存标题，其余照存；作者补上标题后下一轮再存 */
const savableKeys = computed(() => changedKeys.value.filter((key) => key !== 'title' || draft.title.trim()))
const words = computed(() => countWords(draft.content))

const saveState = computed(() => {
  if (conflict.value) return { icon: 'lucide:git-compare', text: '别处已修改', tone: 'warn' }
  if (saving.value) return { icon: 'lucide:loader-circle', text: '保存中…', tone: 'muted' }
  if (saveError.value) return { icon: 'lucide:cloud-off', text: '保存失败', tone: 'warn' }
  if (dirty.value) return { icon: 'lucide:pencil', text: '未保存', tone: 'muted' }
  return { icon: 'lucide:check', text: '已保存', tone: 'ok' }
})

// ------------------------------------------------------------------ 本机草稿

const draftKey = (id: EntityId) => `scribe:chapter-draft:${id}`

const writeLocalDraft = () => {
  if (!chapter.value) return
  try {
    localStorage.setItem(draftKey(chapter.value.id), JSON.stringify({ ...draft, at: new Date().toISOString() }))
  } catch {
    // 隐私模式或存储满了：本机草稿只是兜底，写不进去不影响正常保存
  }
}

const clearLocalDraft = (id: EntityId) => {
  try {
    localStorage.removeItem(draftKey(id))
  } catch {
    // 同上
  }
}

const readLocalDraft = (id: EntityId): (Fields & { at: string }) | null => {
  try {
    const raw = localStorage.getItem(draftKey(id))
    return raw ? (JSON.parse(raw) as Fields & { at: string }) : null
  } catch {
    return null
  }
}

// ------------------------------------------------------------------ 加载

const autosize = (el: HTMLTextAreaElement | null) => {
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}

const autosizeAll = () => nextTick(() => [titleEl, synopsisEl, contentEl].forEach((el) => autosize(el.value)))

let focusTitleOnEnter = false

/**
 * 切章是 out-in 过渡，新稿纸要等旧的淡出后才挂载，nextTick 时 ref 还指着旧元素；
 * 所以尺寸自适应与聚焦标题放在新稿纸进场这一刻做。
 */
const onSheetEnter = () => {
  autosizeAll()
  if (focusTitleOnEnter) {
    focusTitleOnEnter = false
    titleEl.value?.focus()
    titleEl.value?.select()
  }
}

const applyServer = (detail: ChapterDetail) => {
  chapter.value = detail
  const fields: Fields = {
    title: detail.title,
    synopsis: detail.synopsis ?? '',
    content: detail.content ?? '',
    status: detail.status,
  }
  Object.assign(saved, fields)
  Object.assign(draft, fields)
  revision.value = detail.revision
  saveError.value = ''
  conflict.value = false
  syncListItem(detail)
  autosizeAll()
}

/** 目录里的标题/状态/字数跟着保存结果走，不必重新拉目录 */
const syncListItem = (detail: ChapterDetail) => {
  const item = chapters.value.find((c) => String(c.id) === String(detail.id))
  if (item) {
    item.title = detail.title
    item.status = detail.status
    item.wordCount = detail.wordCount
    item.updateTime = detail.updateTime
  }
}

const loadChapter = async (id: string) => {
  chapterLoading.value = true
  chapterError.value = ''
  localDraft.value = null
  try {
    const detail = await fetchChapter(workId.value, id)
    applyServer(detail)
    lastSavedAt.value = detail.updateTime ?? null
    const local = readLocalDraft(detail.id)
    // 只差一个空标题的草稿不算数：空标题本来就不会被保存
    const differs = (key: FieldKey) => !(key === 'title' && !local?.title.trim()) && local?.[key] !== saved[key]
    if (local && FIELD_KEYS.some(differs)) {
      localDraft.value = local
    } else if (local) {
      clearLocalDraft(detail.id)
    }
    // 空章节（多半刚新建）先让作者起标题：选中默认的「第 N 章」，直接打字即可替换
    focusTitleOnEnter = !detail.content && !localDraft.value
  } catch (error) {
    chapter.value = null
    chapterError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    chapterLoading.value = false
  }
}

/** 没指定章节时打开最近写过的那一章 */
const pickDefault = () =>
  [...chapters.value].sort((a, b) => (b.updateTime ?? '').localeCompare(a.updateTime ?? ''))[0]

const loadPage = async () => {
  pageLoading.value = true
  pageError.value = ''
  try {
    const [work, list] = await Promise.all([fetchWorkDetail(workId.value), fetchChapters(workId.value)])
    workTitle.value = work.title
    chapters.value = list
    if (chapterId.value) {
      await loadChapter(chapterId.value)
    } else {
      const first = pickDefault()
      if (first) await router.replace({ name: 'editor', params: { workId: workId.value, chapterId: String(first.id) } })
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    pageLoading.value = false
  }
}

onMounted(loadPage)

// ------------------------------------------------------------------ 保存

let saveTimer: ReturnType<typeof setTimeout> | undefined
let inflight: Promise<boolean> | null = null

const doSave = async (): Promise<boolean> => {
  const current = chapter.value
  if (!current) return true
  const keys = savableKeys.value
  if (keys.length === 0) return true

  const snapshot: Fields = { ...draft }
  const body: ChapterSaveRequest = { revision: revision.value }
  for (const key of keys) {
    ;(body as unknown as Record<string, string>)[key] = snapshot[key]
  }

  saving.value = true
  try {
    const result = await saveChapter(workId.value, current.id, body)
    if (chapter.value?.id !== current.id) return true
    revision.value = result.revision
    for (const key of keys) {
      ;(saved as Record<FieldKey, string>)[key] = snapshot[key]
    }
    // 后端可能顺手改了状态（大纲首次写正文转草稿）；作者这期间没动状态才跟着改
    if (!keys.includes('status') && draft.status === saved.status && result.status !== saved.status) {
      saved.status = result.status
      draft.status = result.status
    }
    saveError.value = ''
    lastSavedAt.value = result.updateTime ?? new Date().toISOString()
    syncListItem(result)
    if (!dirty.value) clearLocalDraft(current.id)
    return true
  } catch (error) {
    if (error instanceof ApiError && error.code === 409) {
      conflict.value = true
    } else {
      saveError.value = error instanceof Error ? error.message : '保存失败'
    }
    return false
  } finally {
    saving.value = false
  }
}

/** 立即保存；已有保存在路上就等它完，再把期间新改的一并存掉 */
const flush = async (): Promise<boolean> => {
  clearTimeout(saveTimer)
  if (inflight) await inflight
  if (conflict.value) return !dirty.value
  if (savableKeys.value.length === 0) return true
  inflight = doSave()
  const ok = await inflight
  inflight = null
  // 保存途中又有新改动就接着存；只剩空标题这种存不了的差异时停下，避免空转
  return ok && savableKeys.value.length > 0 ? flush() : ok
}

watch(
  draft,
  () => {
    if (!chapter.value || !dirty.value) return
    writeLocalDraft()
    if (savableKeys.value.length === 0) return
    if (conflict.value) return
    clearTimeout(saveTimer)
    saveTimer = setTimeout(flush, AUTOSAVE_DELAY)
  },
  { deep: true },
)

/** 冲突时：丢掉本地修改，载入服务端最新版 */
const reloadLatest = async () => {
  if (!chapter.value) return
  if (dirty.value && !window.confirm('载入最新版本会丢掉你在这里的修改，确定吗？')) return
  clearLocalDraft(chapter.value.id)
  await loadChapter(String(chapter.value.id))
}

/** 冲突时：以当前编辑内容为准覆盖服务端 */
const overwrite = async () => {
  if (!chapter.value) return
  try {
    const latest = await fetchChapter(workId.value, chapter.value.id)
    revision.value = latest.revision
    // 以最新版为基准重算差异，保证这次覆盖把四个字段都对齐到本地内容
    Object.assign(saved, {
      title: latest.title,
      synopsis: latest.synopsis ?? '',
      content: latest.content ?? '',
      status: latest.status,
    })
    conflict.value = false
    await flush()
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '覆盖失败'
  }
}

const restoreLocal = () => {
  if (!localDraft.value) return
  const { at: _at, ...fields } = localDraft.value
  Object.assign(draft, fields)
  localDraft.value = null
  autosizeAll()
}

const discardLocal = () => {
  if (chapter.value) clearLocalDraft(chapter.value.id)
  localDraft.value = null
}

// ------------------------------------------------------------------ 导航

/** 离开当前章节前先存完；存不上就问一句，别让作者稀里糊涂丢稿 */
const confirmLeave = async () => {
  const ok = await flush()
  if (ok || !dirty.value) return true
  return window.confirm('还有修改没保存成功（本机已留草稿）。确定离开吗？')
}

onBeforeRouteUpdate(async (to, from) => {
  if (to.params.chapterId === from.params.chapterId) return true
  if (!(await confirmLeave())) return false
  railOpen.value = false
  if (to.params.chapterId) await loadChapter(String(to.params.chapterId))
  return true
})

onBeforeRouteLeave(confirmLeave)

const selectChapter = (id: EntityId) => {
  if (String(id) === chapterId.value) {
    railOpen.value = false
    return
  }
  router.push({ name: 'editor', params: { workId: workId.value, chapterId: String(id) } })
}

const newChapter = async () => {
  if (creating.value || !(await confirmLeave())) return
  creating.value = true
  try {
    const created = await createChapter(workId.value)
    chapters.value.push({ ...created })
    await router.push({ name: 'editor', params: { workId: workId.value, chapterId: String(created.id) } })
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '新建章节失败'
  } finally {
    creating.value = false
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void flush()
  }
}

const onBeforeUnload = (event: BeforeUnloadEvent) => {
  if (dirty.value || saving.value) {
    event.preventDefault()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('beforeunload', onBeforeUnload)
})
</script>

<template>
  <div :class="['desk', { 'desk--rail-open': railOpen }]">
    <StateBlock v-if="pageLoading" state="loading" class="desk__full" />
    <StateBlock
      v-else-if="pageError"
      state="error"
      class="desk__full"
      :description="pageError"
      action-label="回到我的作品"
      @action="router.push('/works')"
    />

    <template v-else>
      <EditorRail
        class="desk__rail"
        :work-title="workTitle"
        :chapters="chapters"
        :active-id="chapterId"
        :busy="creating"
        @select="selectChapter"
        @create="newChapter"
        @back="router.push({ name: 'work-detail', params: { id: workId } })"
      />
      <Transition name="scrim">
        <div v-if="railOpen" class="desk__scrim" aria-hidden="true" @click="railOpen = false" />
      </Transition>

      <main class="stage">
        <header class="stage__bar">
          <button class="icon-btn stage__toc" type="button" title="章节目录" aria-label="章节目录" @click="railOpen = true">
            <Icon icon="lucide:list" />
          </button>
          <span class="stage__crumb">
            {{ workTitle }}<template v-if="chapter"> / <b>{{ draft.title || '未命名章节' }}</b></template>
          </span>
          <span class="stage__spacer" />
          <template v-if="chapter">
            <label class="status">
              <span class="sr-only">章节状态</span>
              <select v-model="draft.status" class="status__select">
                <option v-for="[value, label] in STATUS_OPTIONS" :key="value" :value="value">{{ label }}</option>
              </select>
            </label>
            <span class="stage__words">{{ words.toLocaleString() }} 字</span>
            <span
              :class="['save-state', `save-state--${saveState.tone}`]"
              role="status"
              :title="lastSavedAt ? `上次保存：${formatRelative(lastSavedAt)}` : undefined"
            >
              <Icon :icon="saveState.icon" :class="{ spin: saving }" />
              {{ saveState.text }}
            </span>
          </template>
        </header>

        <div v-if="chapter && (conflict || saveError || localDraft)" class="notice-wrap">
          <div v-if="conflict" class="notice notice--warn" role="alert">
            <Icon icon="lucide:git-compare" />
            <span class="notice__text">这一章已在别处修改（可能是另一个标签页）。自动保存已暂停。</span>
            <button class="btn btn--ghost btn--sm" type="button" @click="reloadLatest">载入最新版本</button>
            <button class="btn btn--primary btn--sm" type="button" @click="overwrite">用我的版本覆盖</button>
          </div>
          <div v-else-if="saveError" class="notice notice--warn" role="alert">
            <Icon icon="lucide:cloud-off" />
            <span class="notice__text">{{ saveError }}。修改已在本机留存，不会丢失。</span>
            <button class="btn btn--ghost btn--sm" type="button" :disabled="saving" @click="flush">重试</button>
          </div>
          <div v-if="localDraft" class="notice" role="status">
            <Icon icon="lucide:history" />
            <span class="notice__text">发现本机上一份没保存上的草稿（{{ formatRelative(localDraft.at) }}，{{ countWords(localDraft.content).toLocaleString() }} 字）。</span>
            <button class="btn btn--ghost btn--sm" type="button" @click="discardLocal">丢弃</button>
            <button class="btn btn--primary btn--sm" type="button" @click="restoreLocal">恢复</button>
          </div>
        </div>

        <div class="stage__scroll">
          <StateBlock
            v-if="chapters.length === 0"
            state="empty"
            title="还没有章节"
            description="从第一章开始写吧。"
            action-label="新建第一章"
            @action="newChapter"
          />
          <StateBlock v-else-if="chapterError" state="error" :description="chapterError" />
          <Transition v-else name="sheet" mode="out-in" appear @enter="onSheetEnter">
            <article v-if="chapter" :key="String(chapter.id)" :class="['sheet', { 'sheet--loading': chapterLoading }]">
              <label class="sr-only" for="chapter-title">章节标题</label>
              <textarea
                id="chapter-title"
                ref="titleEl"
                v-model="draft.title"
                class="sheet__title"
                rows="1"
                maxlength="100"
                placeholder="章节标题"
                @input="autosize(titleEl)"
                @keydown.enter.prevent="contentEl?.focus()"
              />
              <label class="sr-only" for="chapter-synopsis">本章梗概</label>
              <textarea
                id="chapter-synopsis"
                ref="synopsisEl"
                v-model="draft.synopsis"
                class="sheet__synopsis"
                rows="1"
                maxlength="1000"
                placeholder="本章梗概（可选）：这一章要发生什么"
                @input="autosize(synopsisEl)"
              />
              <label class="sr-only" for="chapter-content">正文</label>
              <textarea
                id="chapter-content"
                ref="contentEl"
                v-model="draft.content"
                class="sheet__content"
                placeholder="从这里开始写…"
                spellcheck="false"
                @input="autosize(contentEl)"
              />
            </article>
          </Transition>
        </div>
      </main>
    </template>
  </div>
</template>

<style scoped lang="scss">
.desk {
  display: flex;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg-canvas);
}

.desk__full {
  flex: 1;
  align-self: center;
}

.desk__rail {
  flex-shrink: 0;
}

.stage {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.stage__bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 3.25rem;
  padding: 0.5rem 1.25rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.stage__toc {
  display: none;
}

.stage__crumb {
  min-width: 0;
  overflow: hidden;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage__crumb b {
  color: var(--color-text-primary);
}

.stage__spacer {
  flex: 1;
}

.stage__words {
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.status__select {
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  font-size: 0.85rem;
  cursor: pointer;
}

.status__select:focus {
  outline: none;
  border-color: var(--color-brand);
}

.save-state {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  min-width: 5.5rem;
  font-size: 0.85rem;
  white-space: nowrap;
  transition: color var(--duration-leave) var(--ease-soft);
}

.save-state svg {
  width: 0.95rem;
  height: 0.95rem;
}

.save-state--muted {
  color: var(--color-text-secondary);
}

.save-state--ok {
  color: var(--color-accent-text);
}

.save-state--warn {
  color: #d97706;
}

.notice-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem 0;
}

.notice {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  max-width: calc(var(--measure-prose) + 4rem);
  margin: 0 auto;
  width: 100%;
  padding: 0.6rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  font-size: 0.88rem;
}

.notice > svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-text-secondary);
}

.notice--warn {
  border-color: color-mix(in srgb, #d97706 45%, var(--color-border));
  background: color-mix(in srgb, #d97706 8%, var(--color-bg-surface));
}

.notice--warn > svg {
  color: #d97706;
}

.notice__text {
  flex: 1;
  min-width: 12rem;
}

.btn--sm {
  padding: 0.3rem 0.7rem;
  font-size: 0.82rem;
}

.stage__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.sheet {
  display: flex;
  flex-direction: column;
  max-width: calc(var(--measure-prose) + 4rem);
  margin: 0 auto;
  padding: 2.5rem 2rem 40vh;
  transition: opacity var(--duration-leave) var(--ease-soft);
}

.sheet--loading {
  opacity: 0.45;
}

.sheet textarea {
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  resize: none;
  overflow: hidden;
}

.sheet textarea:focus {
  outline: none;
}

.sheet textarea::placeholder {
  color: var(--color-text-secondary);
  opacity: 0.6;
}

.sheet__title {
  font-family: var(--font-serif);
  font-size: 1.6rem;
  font-weight: 700;
  line-height: 1.4;
}

.sheet__synopsis {
  margin-top: 0.75rem;
  padding: 0.6rem 0.8rem !important;
  border-left: 3px solid var(--color-border) !important;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.sheet__content {
  min-height: 50vh;
  margin-top: 1.75rem;
  font-family: var(--font-serif);
  font-size: 1.08rem;
  line-height: 2;
}

.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.icon-btn svg {
  width: 1.1rem;
  height: 1.1rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* 切章：旧稿淡出、新稿轻微上浮淡入，与全站过渡同一套曲线 */
.sheet-enter-active {
  transition:
    opacity var(--duration-enter) var(--ease-soft),
    transform var(--duration-enter) var(--ease-soft);
}

.sheet-leave-active {
  transition: opacity var(--duration-leave) var(--ease-soft);
}

.sheet-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.sheet-leave-to {
  opacity: 0;
}

.scrim-enter-active,
.scrim-leave-active {
  transition: opacity var(--duration-panel) var(--ease-soft);
}

.scrim-enter-from,
.scrim-leave-to {
  opacity: 0;
}

.desk__scrim {
  display: none;
}

@media (max-width: 900px) {
  .stage__toc {
    display: inline-grid;
  }

  .desk__rail {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    box-shadow: var(--shadow-lg);
    transform: translateX(-100%);
    transition: transform var(--duration-panel) var(--ease-soft);
  }

  .desk--rail-open .desk__rail {
    transform: none;
  }

  .desk__scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(0, 0, 0, 0.32);
  }

  .stage__bar {
    gap: 0.5rem;
    padding-inline: 0.75rem;
  }

  .stage__words {
    display: none;
  }

  .sheet {
    padding: 1.75rem 1rem 40vh;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet-enter-active,
  .sheet-leave-active,
  .scrim-enter-active,
  .scrim-leave-active,
  .desk__rail {
    transition: none;
  }
}
</style>
