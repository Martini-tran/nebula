<script setup lang="ts">
/**
 * 随手记：先记下来，再决定要不要留。
 * 顶部输入框回车即存为临时笔记（7 天后自动归档），瀑布流便签墙，快到期的在顶部提示整理。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import SideNav, { type SideNavGroup } from '../../components/layout/SideNav.vue'
import StateBlock from '../../components/StateBlock.vue'
import NoteCard from './components/NoteCard.vue'
import ExpiryReview from './components/ExpiryReview.vue'
import { createNote, deleteNote, fetchNoteStats, fetchNotes, updateNote, type NoteStats } from '../../api/notes'
import { noteToTask } from '../../composables/useNoteTask'
import { saveLinkAsBookmark } from '../../composables/useSaveLink'
import { useDeferredDelete } from '../../composables/useDeferredDelete'
import { errorText, toast } from '../../composables/useToast'
import { toggleTodoLine } from '../../utils/markdown'
import { type Note, type NoteQuery } from '../../types/notes'
import { noteTtl } from '../../stores/settings'

const route = useRoute()
const router = useRouter()

const notes = ref<Note[]>([])
const stats = ref<NoteStats | null>(null)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')
const keyword = ref('')
const sideOpen = ref(false)

/** 当前视图：all / temporary / pinned / archived / tag:<名称>，同步到 ?v= */
const active = computed(() => (typeof route.query.v === 'string' && route.query.v ? route.query.v : 'all'))

const query = computed<NoteQuery>(() =>
  active.value.startsWith('tag:')
    ? { view: 'all', tag: active.value.slice(4), keyword: keyword.value.trim() || undefined }
    : { view: active.value as NoteQuery['view'], keyword: keyword.value.trim() || undefined },
)

const heading = computed(() => {
  if (active.value.startsWith('tag:')) return `#${active.value.slice(4)}`
  return { all: '随手记', temporary: '临时笔记', pinned: '置顶 · 长期', archived: '已归档' }[active.value] ?? '随手记'
})

const groups = computed<SideNavGroup[]>(() => [
  {
    key: 'views',
    items: [
      { key: 'all', label: '全部', icon: 'lucide:layout-grid', count: stats.value?.all },
      { key: 'temporary', label: '临时', icon: 'lucide:hourglass', count: stats.value?.temporary },
      { key: 'pinned', label: '置顶 · 长期', icon: 'lucide:pin', count: stats.value?.pinned },
      { key: 'archived', label: '已归档', icon: 'lucide:archive', count: stats.value?.archived },
    ],
  },
  {
    key: 'tags',
    title: '标签',
    empty: '在笔记里加标签后出现在这里',
    items: (stats.value?.tags ?? []).map((t) => ({ key: `tag:${t.name}`, label: t.name, icon: 'lucide:hash', count: t.count })),
  },
])

const select = (key: string) => {
  sideOpen.value = false
  router.push({ query: key === 'all' ? {} : { v: key } })
}

let seq = 0
const load = async () => {
  const current = ++seq
  loading.value = true
  loadError.value = ''
  try {
    const [list, s] = await Promise.all([fetchNotes(query.value), fetchNoteStats()])
    if (current !== seq) return
    notes.value = list
    stats.value = s
  } catch (error) {
    if (current === seq) loadError.value = errorText(error, '加载失败')
  } finally {
    if (current === seq) {
      loading.value = false
      loaded.value = true
    }
  }
}

watch(active, load)
let keywordTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(load, 250)
})

// ── 快速记录 ──

const draft = ref('')
const saving = ref(false)
const draftInput = ref<HTMLTextAreaElement | null>(null)

const autoGrow = () => {
  const el = draftInput.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 240)}px`
}

const saveDraft = async () => {
  const content = draft.value.trim()
  if (!content || saving.value) return
  saving.value = true
  try {
    const tag = active.value.startsWith('tag:') ? active.value.slice(4) : undefined
    // 在「置顶」视图里记的直接是长期笔记
    await createNote({ content, tags: tag ? [tag] : undefined, pinned: active.value === 'pinned' || undefined })
    draft.value = ''
    await nextTick()
    autoGrow()
    if (active.value === 'archived') select('all')
    else load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

const onDraftKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    saveDraft()
  }
}

// ── 卡片动作 ──

const replace = (note: Note) => {
  const index = notes.value.findIndex((n) => n.id === note.id)
  if (index >= 0) notes.value[index] = note
}

const togglePin = async (note: Note) => {
  try {
    await updateNote(note.id, { pinned: !note.pinned })
    toast.ok(note.pinned ? `已取消置顶，${noteTtl()} 天后自动归档` : '已置顶，不会过期')
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const restore = async (note: Note) => {
  try {
    await updateNote(note.id, { archived: false })
    toast.ok(`已恢复为临时笔记，${noteTtl()} 天后再次归档`)
    load()
  } catch (error) {
    toast.error(errorText(error, '恢复失败'))
  }
}

const toTask = async (note: Note) => {
  try {
    const task = await noteToTask(note)
    toast.ok(`已加入任务收件箱：「${task.title}」`, {
      action: { label: '去看看', run: () => router.push({ path: '/tasks', query: { v: 'inbox', task: String(task.id) } }) },
    })
    load()
  } catch (error) {
    toast.error(errorText(error, '转任务失败'))
  }
}

const copy = async (note: Note) => {
  try {
    await navigator.clipboard.writeText(note.content)
    toast.ok('已复制')
  } catch {
    toast.error('复制失败')
  }
}

const toggleTodo = async (note: Note, line: number) => {
  const content = toggleTodoLine(note.content, line)
  replace({ ...note, content })
  try {
    replace(await updateNote(note.id, { content }))
  } catch (error) {
    replace(note)
    toast.error(errorText(error, '保存失败'))
  }
}

const deleter = useDeferredDelete({ remove: deleteNote, onCommitted: load })
const visible = computed(() => notes.value.filter((n) => !deleter.isHidden(n.id)))

const open = (note: Note) => router.push({ name: 'note-editor', params: { id: String(note.id) } })

// ── 到期整理 ──

const reviewOpen = ref(false)
const reviewing = computed(() => route.query.review === '1')
watch(reviewing, (value) => (reviewOpen.value = value), { immediate: true })
const closeReview = () => {
  reviewOpen.value = false
  if (reviewing.value) router.replace({ query: { ...route.query, review: undefined } })
}

const emptyText = computed(() => {
  if (keyword.value.trim()) return { title: `没有包含「${keyword.value.trim()}」的笔记`, desc: '搜索范围是正文和标签。' }
  if (active.value === 'archived') return { title: '没有归档的笔记', desc: `临时笔记 ${noteTtl()} 天后自动归档到这里，归档后还能搜到。` }
  if (active.value === 'pinned') return { title: '还没有置顶的笔记', desc: '置顶的笔记会长期保留，不会过期。' }
  return { title: '记点什么吧', desc: '电话号码、会议里闪过的念头、待确认的事……先记下来，再决定要不要留。' }
})

onMounted(load)
</script>

<template>
  <div class="notes page">
    <button class="side-toggle btn btn--ghost" type="button" :aria-expanded="sideOpen" @click="sideOpen = !sideOpen">
      <Icon icon="lucide:panel-left" /><span>{{ heading }}</span>
      <Icon :icon="sideOpen ? 'lucide:chevron-up' : 'lucide:chevron-down'" />
    </button>

    <aside class="notes__side" :class="{ 'notes__side--open': sideOpen }">
      <SideNav :groups="groups" :active="active" @select="select" />
      <div class="rules">
        <b>临时笔记规则</b>
        <p>新笔记默认临时，{{ noteTtl() }} 天后自动归档；编辑一次重新计时。置顶、加标签、转成任务会自动转为长期。</p>
      </div>
    </aside>

    <section class="notes__main">
      <button v-if="stats?.dueTomorrow" class="due" type="button" @click="reviewOpen = true">
        <Icon icon="lucide:hourglass" />
        <span><b>{{ stats.dueTomorrow }} 条</b>临时笔记明天到期。</span>
        <span class="due__go">现在整理 →</span>
      </button>

      <header class="toolbar">
        <h1 class="page-title">{{ heading }}</h1>
        <label class="search">
          <Icon icon="lucide:search" />
          <input v-model="keyword" type="search" placeholder="搜索笔记" aria-label="搜索笔记" />
        </label>
      </header>

      <div v-if="active !== 'archived'" class="compose surface">
        <textarea
          ref="draftInput"
          v-model="draft"
          rows="2"
          :placeholder="active === 'pinned' ? '记点什么…回车保存为长期笔记，Shift+Enter 换行' : '记点什么…回车保存为临时笔记，Shift+Enter 换行'"
          aria-label="快速记录"
          @input="autoGrow"
          @keydown="onDraftKeydown"
        />
        <div class="compose__foot">
          <span class="compose__life">
            <Icon :icon="active === 'pinned' ? 'lucide:pin' : 'lucide:hourglass'" />
            {{ active === 'pinned' ? '长期' : `${noteTtl()} 天` }}
          </span>
          <button class="btn btn--primary" type="button" :disabled="!draft.trim() || saving" @click="saveDraft">
            <Icon :icon="saving ? 'lucide:loader-circle' : 'lucide:corner-down-left'" :class="{ spin: saving }" />记下
          </button>
        </div>
      </div>

      <StateBlock v-if="loading && !loaded" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
      <StateBlock v-else-if="!visible.length" state="empty" :title="emptyText.title" :description="emptyText.desc" />
      <div v-else class="wall" :class="{ 'wall--loading': loading }">
        <NoteCard
          v-for="note in visible"
          :key="note.id"
          :note="note"
          @open="open(note)"
          @toggle-pin="togglePin(note)"
          @to-task="toTask(note)"
          @copy="copy(note)"
          @remove="deleter.schedule(note.id, '笔记已删除')"
          @restore="restore(note)"
          @toggle-todo="toggleTodo(note, $event)"
          @save-link="saveLinkAsBookmark"
        />
      </div>
    </section>

    <ExpiryReview :open="reviewOpen" @close="closeReview" @changed="load" />
  </div>
</template>

<style scoped>
.notes {
  display: grid;
  grid-template-columns: 14rem minmax(0, 1fr);
  gap: 2rem;
  align-items: start;
}

.side-toggle {
  display: none;
}

.notes__side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.rules {
  padding: 0.75rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.76rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.rules b {
  display: block;
  margin-bottom: 0.2rem;
  color: var(--color-text-primary);
}

.notes__main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.due {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.65rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--color-danger) 30%, var(--color-border));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-danger) 7%, var(--color-bg-surface));
  color: var(--color-text-primary);
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;
}

.due > svg {
  color: var(--color-danger);
}

.due__go {
  margin-left: auto;
  color: var(--color-brand);
  font-weight: 700;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
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
  color: var(--color-text-secondary);
}

.search:focus-within {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.search input {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.compose {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.8rem 0.9rem 0.6rem;
  border-radius: var(--radius-lg);
}

.compose:focus-within {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.compose textarea {
  width: 100%;
  min-height: 2.8rem;
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.95rem;
  line-height: 1.6;
}

.compose__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.compose__life {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.compose__foot .btn {
  padding: 0.35rem 0.85rem;
  font-size: 0.86rem;
}

.wall {
  columns: 16rem;
  column-gap: 0.85rem;
  transition: opacity 0.2s ease;
}

.wall--loading {
  opacity: 0.6;
}

@media (max-width: 900px) {
  .notes {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .side-toggle {
    display: inline-flex;
    justify-self: start;
  }

  .notes__side {
    display: none;
    position: static;
    padding: 1rem;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    background: var(--color-bg-surface);
  }

  .notes__side--open {
    display: flex;
  }

  .search {
    width: 100%;
  }
}
</style>
