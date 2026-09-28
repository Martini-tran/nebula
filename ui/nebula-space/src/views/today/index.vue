<script setup lang="ts">
/**
 * 今天：登录后的首页，只回答「今天我要做什么、记下了什么」。
 * 左列要做的：今日任务（过期标红）+ 会议时间线；右列记下的：随手记（回车即存）+ 今天收藏。
 * 顶部黄条处理昨天没做完的；下午 6 点后换成晚间回顾（?review=1 可随时打开）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import TaskRow from '../tasks/components/TaskRow.vue'
import TaskQuickAdd from '../tasks/components/TaskQuickAdd.vue'
import MeetingTimeline from './components/MeetingTimeline.vue'
import EveningReview from './components/EveningReview.vue'
import { completeTask, fetchTasks, updateTask } from '../../api/tasks'
import { fetchMeetings } from '../../api/meetings'
import { createNote, fetchNotes } from '../../api/notes'
import { fetchBookmarks } from '../../api/space'
import { useAuthStore } from '../../stores/auth'
import { useTaskStore } from '../../stores/tasks'
import { useFocusStore } from '../../stores/focus'
import { useBadgeStore } from '../../stores/badges'
import { useMyNames } from '../../composables/useMeetingSync'
import { errorText, toast } from '../../composables/useToast'
import { diffDays, hmOf, relativeDay, startOfWeek, todayYmd, weekdayOf, ymdOf } from '../../utils/date'
import { firstLine } from '../../utils/markdown'
import { parseMeetingItems } from '../../utils/meetingItems'
import { lifeLabel } from '../notes/noteLife'
import { type Note } from '../../types/notes'
import { noteTtl, notesLongByDefault, useSettingsStore } from '../../stores/settings'
import { fetchReport } from '../../api/reviews'
import type { Bookmark } from '../../types/space'
import type { Meeting } from '../../types/meetings'
import type { Task } from '../../types/tasks'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const taskStore = useTaskStore()
const focusStore = useFocusStore()
const badges = useBadgeStore()
const myNames = useMyNames()
const settings = useSettingsStore()
const on = settings.isEnabled

const today = todayYmd()
const tasks = ref<Task[]>([])
const meetings = ref<Meeting[]>([])
const notes = ref<Note[]>([])
const bookmarks = ref<Bookmark[] | null>(null)
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    const [allTasks, todayMeetings, allNotes] = await Promise.all([
      fetchTasks({ view: 'all' }),
      fetchMeetings({ from: today, to: today }),
      fetchNotes({ view: 'all' }),
      taskStore.reloadLists(),
    ])
    tasks.value = allTasks
    meetings.value = todayMeetings
    notes.value = allNotes
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
  badges.refresh()
  // 书签是独立的服务，取不到（没权限、没启动）就不显示这一块
  try {
    const page = await fetchBookmarks({ pageNum: 1, pageSize: 20 })
    bookmarks.value = (page?.records ?? []).filter((b) => b.createTime && ymdOf(b.createTime) === today)
  } catch {
    bookmarks.value = null
  }
}

// ── 顶部 ──

const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 5 ? '夜深了' : h < 11 ? '上午好' : h < 13 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})

const dateTitle = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${'日一二三四五六'[weekdayOf(today)]}`
})

/** 今天的任务：日期是今天的全部 + 过期未完成 */
const todayTasks = computed(() => tasks.value.filter((t) => t.dueDate === today || (!t.done && t.dueDate && t.dueDate < today)))
const overdue = computed(() => tasks.value.filter((t) => !t.done && t.dueDate && t.dueDate < today))
const doneToday = computed(() => tasks.value.filter((t) => t.done && ((t.doneTime && ymdOf(t.doneTime) === today) || t.dueDate === today)))
const openToday = computed(() =>
  todayTasks.value
    .filter((t) => !t.done)
    .sort((a, b) => {
      const late = Number(Boolean(b.dueDate! < today)) - Number(Boolean(a.dueDate! < today))
      return late || String(a.dueTime ?? '99').localeCompare(String(b.dueTime ?? '99')) || b.priority - a.priority
    }),
)
const taskTotal = computed(() => openToday.value.length + doneToday.value.length)

const nowHm = () => `${String(new Date().getHours()).padStart(2, '0')}:${String(new Date().getMinutes()).padStart(2, '0')}`
const nextMeeting = computed(() => meetings.value.find((m) => m.status !== 'done' && m.startTime >= nowHm()))
const notesToday = computed(() => notes.value.filter((n) => ymdOf(n.createTime) === today))

// ── 昨天没做完的 ──

const moveAllToToday = async () => {
  try {
    await Promise.all(overdue.value.map((t) => updateTask(t.id, { dueDate: today })))
    toast.ok(`${overdue.value.length} 项已移到今天`)
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

// ── 任务 ──

const toggle = async (task: Task) => {
  const done = !task.done
  const index = tasks.value.findIndex((t) => t.id === task.id)
  if (index >= 0) tasks.value[index] = { ...task, done, doneTime: done ? `${today} ${nowHm()}:00` : null }
  try {
    const result = await completeTask(task.id, done)
    if (done) toast.ok(result.next ? `已完成，下一次：${relativeDay(result.next.dueDate!)}` : `已完成「${task.title}」`, { action: { label: '撤销', run: () => toggle(result.task) } })
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
    load()
  }
}

const openTask = (task: Task) => router.push({ path: '/tasks', query: { v: 'all', task: String(task.id) } })

// ── 随手记 ──

const draft = ref('')
const savingNote = ref(false)
const saveNote = async () => {
  const content = draft.value.trim()
  if (!content || savingNote.value) return
  savingNote.value = true
  try {
    await createNote({ content })
    draft.value = ''
    load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    savingNote.value = false
  }
}
const onNoteKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    saveNote()
  }
}
const recentNotes = computed(() => notes.value.slice(0, 5))

// ── 晚间回顾 ──

const REVIEW_KEY = `nebula-space:review:${today}`
const reviewDismissed = ref((() => {
  try {
    return localStorage.getItem(REVIEW_KEY) === '1'
  } catch {
    return false
  }
})())
const showReview = computed(() => {
  if (route.query.review === '1') return true
  const { enabled, time } = settings.data.eveningReview
  return enabled && nowHm() >= time && !reviewDismissed.value
})

const closeReview = (saved: boolean) => {
  reviewDismissed.value = true
  try {
    localStorage.setItem(REVIEW_KEY, '1')
  } catch {
    // 记不住就明天再问
  }
  if (route.query.review) router.replace({ query: {} })
  if (saved) load()
}

const reviewStats = computed(() => ({
  meetings: meetings.value.length,
  actions: meetings.value.reduce((sum, m) => sum + parseMeetingItems(m.content, myNames, m.date).actions.length, 0),
  notes: notesToday.value.length,
  bookmarks: bookmarks.value ? bookmarks.value.length : null,
}))

const tempNotesToday = computed(() => notesToday.value.filter((n) => !n.pinned && !n.archived && !n.tags.includes('日记')))

// ── 周回顾提示：设置里的那天那个点之后，到这周结束前都显示；写过周报或点过关闭就不再出现 ──

const weekStart = startOfWeek(today)
const WEEK_KEY = `nebula-space:review:week:${weekStart}`
const weekDismissed = ref((() => {
  try {
    return localStorage.getItem(WEEK_KEY) === '1'
  } catch {
    return false
  }
})())
const reportSaved = ref(true)
const weekCardDue = computed(() => {
  const { enabled, weekday, time } = settings.data.weeklyReview
  if (!enabled || weekDismissed.value) return false
  // 在这周里的第几天（周从哪天开始跟设置走）
  const dayIndex = diffDays(weekStart, today)
  const targetIndex = (weekday - weekdayOf(weekStart) + 7) % 7
  return dayIndex > targetIndex || (dayIndex === targetIndex && nowHm() >= time)
})
const showWeekCard = computed(() => weekCardDue.value && !reportSaved.value && !showReview.value)
const doneThisWeek = computed(() => tasks.value.filter((t) => t.done && t.doneTime && ymdOf(t.doneTime) >= weekStart).length)
const dismissWeekCard = () => {
  weekDismissed.value = true
  try {
    localStorage.setItem(WEEK_KEY, '1')
  } catch {
    // 记不住就下次再提醒
  }
}

onMounted(async () => {
  load()
  if (weekCardDue.value) reportSaved.value = Boolean(await fetchReport('week', weekStart).catch(() => true))
})
</script>

<template>
  <div class="today page">
    <header class="hero">
      <div>
        <p class="hero__date">{{ dateTitle }}</p>
        <h1 class="page-title">{{ greeting }}，{{ auth.displayName || '你好' }}</h1>
      </div>
      <div class="hero__stats">
        <router-link v-if="on('tasks')" to="/tasks" class="stat"><b>{{ doneToday.length }} / {{ taskTotal }}</b><span>任务完成</span></router-link>
        <router-link v-if="on('meetings')" to="/meetings" class="stat">
          <b>{{ meetings.length }} 个会议</b><span>{{ nextMeeting ? `下一个 ${nextMeeting.startTime}` : '今天没有更多会议' }}</span>
        </router-link>
        <router-link v-if="on('notes')" to="/notes" class="stat"><b>{{ notesToday.length }} 条随手记</b><span>今天记下</span></router-link>
      </div>
    </header>

    <div v-if="showWeekCard" class="weekcard">
      <span class="weekcard__ico"><Icon icon="lucide:calendar-check" /></span>
      <span class="weekcard__text">
        <b>这周快过完了，花两分钟回顾一下？</b>
        <small>本周完成 {{ doneThisWeek }} 项任务。回顾给自己看，周报可以直接复制给别人。</small>
      </span>
      <router-link class="btn btn--primary" to="/review">看周回顾</router-link>
      <router-link class="btn btn--ghost" :to="{ path: '/review/report', query: { type: 'week' } }">写周报</router-link>
      <button class="btn btn--quiet weekcard__x" type="button" aria-label="这周不再提醒" title="这周不再提醒" @click="dismissWeekCard"><Icon icon="lucide:x" /></button>
    </div>

    <EveningReview
      v-if="showReview && !loading"
      :done="doneToday.length"
      :total="taskTotal"
      :remaining="openToday"
      :temp-notes="tempNotesToday"
      :stats="reviewStats"
      @changed="load"
      @close="closeReview"
    />

    <div v-else-if="overdue.length && on('tasks')" class="leftover">
      <Icon icon="lucide:history" />
      <span>
        之前还有 <b>{{ overdue.length }} 项</b>没做完：{{ overdue.slice(0, 2).map((t) => `「${t.title}」`).join('') }}<template v-if="overdue.length > 2"> 等</template>
      </span>
      <button class="btn btn--primary" type="button" @click="moveAllToToday">全部移到今天</button>
      <router-link class="btn btn--ghost" :to="{ path: '/tasks' }">逐条处理</router-link>
    </div>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <div v-else class="cols">
      <div class="col">
        <section v-if="on('tasks')" class="card surface">
          <header class="card__head">
            <h2><Icon icon="lucide:square-check-big" />今日任务</h2>
            <span class="card__meta">{{ doneToday.length }} / {{ taskTotal }}</span>
          </header>
          <p v-if="!taskTotal" class="empty">今天还没有任务。在下面加一条，或者去<router-link to="/tasks?v=inbox">收件箱</router-link>挑几件。</p>
          <ul class="rows">
            <TaskRow v-for="t in openToday" :key="t.id" :task="t" :show-date="t.dueDate !== today" @open="openTask(t)" @toggle="toggle(t)"
              @focus="focusStore.openSetup(t.id, t.title)" />
            <TaskRow v-for="t in doneToday" :key="t.id" :task="t" :show-date="false" @open="openTask(t)" @toggle="toggle(t)"
              @focus="focusStore.openSetup(t.id, t.title)" />
          </ul>
          <TaskQuickAdd :default-date="today" :default-list-id="null" placeholder="添加任务，例如「下午3点 回电话 !2」" @added="load" />
        </section>

        <section v-if="on('meetings')" class="card surface">
          <header class="card__head">
            <h2><Icon icon="lucide:users" />今天的会议</h2>
            <router-link class="card__link" :to="{ path: '/meetings', query: { new: '1' } }"><Icon icon="lucide:plus" />新会议</router-link>
          </header>
          <p v-if="!meetings.length" class="empty">今天没有会议。</p>
          <MeetingTimeline v-else :meetings="meetings" />
        </section>
      </div>

      <div class="col">
        <section v-if="on('notes')" class="card surface">
          <header class="card__head">
            <h2><Icon icon="lucide:pencil-line" />随手记</h2>
            <router-link class="card__link" to="/notes">全部笔记 →</router-link>
          </header>
          <div class="compose">
            <textarea v-model="draft" rows="2" placeholder="记点什么…回车即存" aria-label="随手记" @keydown="onNoteKeydown" />
            <div class="compose__foot">
              <span><Icon icon="lucide:hourglass" />{{ notesLongByDefault() ? '长期' : `临时 · ${noteTtl()} 天` }}</span>
              <button class="btn btn--primary" type="button" :disabled="!draft.trim() || savingNote" @click="saveNote">记下</button>
            </div>
          </div>
          <ul class="notes">
            <li v-for="n in recentNotes" :key="n.id">
              <router-link :to="{ name: 'note-editor', params: { id: String(n.id) } }" :class="`note--${n.color}`">
                <span class="notes__text">{{ firstLine(n.content) || '（空笔记）' }}</span>
                <small>
                  {{ ymdOf(n.updateTime) === today ? hmOf(n.updateTime) : relativeDay(ymdOf(n.updateTime)) }} ·
                  <em :class="`life--${lifeLabel(n).tone}`">{{ lifeLabel(n).text }}</em>
                </small>
              </router-link>
            </li>
          </ul>
        </section>

        <section v-if="bookmarks && on('bookmarks')" class="card surface">
          <header class="card__head">
            <h2><Icon icon="lucide:bookmark" />今天收藏</h2>
            <span class="card__meta">{{ bookmarks.length }}</span>
          </header>
          <p v-if="!bookmarks.length" class="empty">今天还没有收藏。随时按 <kbd>Ctrl</kbd> <kbd>Shift</kbd> <kbd>Space</kbd> 粘贴网址就能存。</p>
          <ul class="bms">
            <li v-for="b in bookmarks" :key="b.id">
              <a :href="b.url" target="_blank" rel="noopener noreferrer">
                <span class="bms__ico">{{ (b.title || b.domain || '?').charAt(0).toUpperCase() }}</span>
                <span class="bms__text">{{ b.title }}</span>
                <small>{{ b.createTime ? hmOf(b.createTime) : '' }}</small>
              </a>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.today {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
}

.hero__date {
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.stat {
  display: flex;
  flex-direction: column;
  min-width: 8.5rem;
  padding: 0.6rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.stat:hover {
  border-color: var(--color-brand);
}

.stat b {
  font-size: 1.05rem;
}

.stat span {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.weekcard {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  padding: 0.75rem 1rem;
  border: 1px solid color-mix(in srgb, var(--color-brand) 35%, var(--color-border));
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
}

.weekcard__ico {
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.weekcard__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 14rem;
}

.weekcard__text small {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.weekcard .btn {
  padding: 0.35rem 0.8rem;
  font-size: 0.84rem;
}

.weekcard__x {
  padding: 0.35rem !important;
}

.leftover {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  padding: 0.7rem 1rem;
  border: 1px solid color-mix(in srgb, #f59e0b 45%, var(--color-border));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, #f59e0b 10%, var(--color-bg-surface));
  font-size: 0.9rem;
}

.leftover > svg {
  color: #d97706;
}

.leftover > span {
  flex: 1;
  min-width: 14rem;
}

.leftover .btn {
  padding: 0.35rem 0.8rem;
  font-size: 0.84rem;
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__head h2 {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.98rem;
  font-weight: 800;
}

.card__head h2 svg {
  color: var(--color-brand);
}

.card__meta {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.card__link {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-brand);
}

.empty {
  font-size: 0.86rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.empty a {
  color: var(--color-brand);
}

.rows {
  margin: 0 -0.35rem;
  padding: 0;
  list-style: none;
}

.compose {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.6rem 0.75rem;
  border: 1px dashed color-mix(in srgb, var(--color-brand) 40%, var(--color-border));
  border-radius: var(--radius-md);
}

.compose:focus-within {
  border-style: solid;
  border-color: var(--color-brand);
}

.compose textarea {
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.92rem;
  line-height: 1.6;
}

.compose__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.compose__foot span {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.compose__foot .btn {
  padding: 0.25rem 0.75rem;
  font-size: 0.82rem;
}

.notes,
.bms {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.notes a {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-md);
  background: var(--note-plain);
  border: 1px solid var(--color-border);
  font-size: 0.88rem;
}

.notes a:hover {
  border-color: var(--color-brand);
}

.note--yellow {
  background: var(--note-yellow) !important;
}

.note--green {
  background: var(--note-green) !important;
}

.note--blue {
  background: var(--note-blue) !important;
}

.note--pink {
  background: var(--note-pink) !important;
}

.note--purple {
  background: var(--note-purple) !important;
}

.notes__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notes small {
  flex: none;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.notes em {
  font-style: normal;
}

.life--danger {
  color: var(--color-danger);
}

.life--pinned {
  color: var(--color-brand);
}

.bms a {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.35rem 0.25rem;
  font-size: 0.88rem;
}

.bms a:hover .bms__text {
  color: var(--color-brand);
}

.bms__ico {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.74rem;
  font-weight: 800;
}

.bms__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bms small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

@media (max-width: 900px) {
  .cols {
    grid-template-columns: 1fr;
  }
}
</style>
