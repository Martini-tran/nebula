<script setup lang="ts">
/**
 * 周视图：七天并排的时间轴，看一周的忙闲。
 * 顶部「全天」行放这天还没排时间的任务和习惯完成情况；下面是会议、已排时间的任务、专注记录。
 * 任务可以拖到任意一天的任意时间（写 dueDate + dueTime），拖回「全天」行就是只留日期、不排时间。
 * 点日期头进入那天的日视图。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { updateTask } from '../../../api/tasks'
import { errorText, toast } from '../../../composables/useToast'
import { addDays, fromYmd, monthDay, relativeDay, todayYmd, weekdayLabel, weekdayOf } from '../../../utils/date'
import { holidayOf } from '../../../utils/holidays'
import { dur, layLanes, toHm, toMin } from '../timeLayout'
import type { CalendarData } from '../useCalendarData'
import type { Task } from '../../../types/tasks'

const props = defineProps<{ start: string; data: CalendarData }>()
const emit = defineEmits<{ changed: []; openDay: [date: string] }>()
const router = useRouter()

const START = 7
const END = 23
const HOUR_PX = 44
const SNAP = 15
const DEFAULT_MIN = 30
const ALLDAY_MAX = 3

const today = todayYmd()
const top = (min: number) => ((min - START * 60) / 60) * HOUR_PX
const hours = Array.from({ length: END - START }, (_, i) => START + i)

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => (timer = setInterval(() => (now.value = new Date()), 60_000)))
onBeforeUnmount(() => clearInterval(timer))
const nowMin = computed(() => now.value.getHours() * 60 + now.value.getMinutes())

interface Block {
  key: string
  kind: 'meeting' | 'task' | 'focus'
  start: number
  end: number
  title: string
  time: string
  task?: Task
  meetingId?: string
  done?: boolean
}

const blocksOn = (date: string): Block[] => {
  const list: Block[] = []
  for (const m of props.data.meetingsOn(date)) {
    const s = toMin(m.startTime)
    list.push({ key: `m${m.id}`, kind: 'meeting', start: s, end: s + m.durationMin, title: m.title, time: m.startTime, meetingId: String(m.id) })
  }
  for (const t of props.data.tasksOn(date).filter((x) => x.dueTime)) {
    const s = toMin(t.dueTime!)
    list.push({ key: `t${t.id}`, kind: 'task', start: s, end: s + (t.estimateMin ?? DEFAULT_MIN), title: t.title, time: t.dueTime!, task: t, done: t.done })
  }
  for (const f of props.data.sessionsOn(date).filter((x) => x.status === 'done')) {
    const s = toMin(f.startedAt.slice(11, 16))
    const e = Math.max(s + 10, toMin(f.endedAt.slice(11, 16)))
    list.push({ key: `f${f.id}`, kind: 'focus', start: s, end: e, title: `专注 · ${f.taskTitle}`, time: toHm(s) })
  }
  // 超出显示范围的块夹到边上，不至于看不见
  return list
    .map((b) => ({ ...b, start: Math.max(START * 60, Math.min(b.start, END * 60 - SNAP)), end: Math.min(END * 60, Math.max(b.end, b.start + SNAP)) }))
    .filter((b) => b.end > b.start)
}

const days = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const date = addDays(props.start, i)
    const unscheduled = props.data.tasksOn(date).filter((t) => !t.done && !t.dueTime)
    const habits = date <= today ? props.data.habitsOn(date) : []
    return {
      date,
      day: fromYmd(date).getDate(),
      weekday: weekdayLabel(date),
      weekend: [0, 6].includes(weekdayOf(date)),
      holiday: holidayOf(date),
      mood: props.data.moodOn(date),
      unscheduled,
      habitsDone: habits.filter((h) => h.done).length,
      habitsTotal: habits.length,
      blocks: layLanes(blocksOn(date)),
    }
  }),
)

// ── 本周汇总 ──

const summary = computed(() => {
  let meeting = 0
  let task = 0
  let unscheduled = 0
  for (const d of days.value) {
    for (const b of d.blocks) {
      if (b.kind === 'meeting') meeting += b.end - b.start
      if (b.kind === 'task' && !b.done) task += b.end - b.start
    }
    unscheduled += d.unscheduled.length
  }
  const focus = days.value.reduce((sum, d) => sum + props.data.sessionsOn(d.date).filter((s) => s.status === 'done').reduce((s2, s) => s2 + s.actualMin, 0), 0)
  /** 会议最多的一天 */
  const busiest = days.value
    .map((d) => ({ date: d.date, min: d.blocks.filter((b) => b.kind === 'meeting').reduce((s, b) => s + b.end - b.start, 0) }))
    .sort((a, b) => b.min - a.min)[0]
  return { meeting, task, focus, unscheduled, busiest: busiest && busiest.min >= 180 ? busiest : null }
})

// ── 拖动 ──

const dragTask = ref<Task | null>(null)
/** 拖动中的落点预览：某天的某个时间，或某天的「全天」行（min = null） */
const ghost = ref<{ date: string; min: number | null } | null>(null)

const onDragStart = (event: DragEvent, task: Task) => {
  dragTask.value = task
  event.dataTransfer?.setData('text/plain', task.title)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
const onDragEnd = () => {
  dragTask.value = null
  ghost.value = null
}

const minuteAt = (event: DragEvent) => {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const len = dragTask.value?.estimateMin ?? DEFAULT_MIN
  const raw = START * 60 + ((event.clientY - rect.top) / HOUR_PX) * 60
  return Math.min(END * 60 - Math.min(len, 60), Math.max(START * 60, Math.round(raw / SNAP) * SNAP))
}

const onColOver = (event: DragEvent, date: string) => {
  if (!dragTask.value) return
  event.preventDefault()
  ghost.value = { date, min: minuteAt(event) }
}
const onAllDayOver = (event: DragEvent, date: string) => {
  if (!dragTask.value) return
  event.preventDefault()
  ghost.value = { date, min: null }
}

const onDrop = async (event: DragEvent, date: string, timed: boolean) => {
  const task = dragTask.value
  const min = timed ? minuteAt(event) : null
  onDragEnd()
  if (!task) return
  const dueTime = min === null ? null : toHm(min)
  if (task.dueDate === date && task.dueTime === dueTime) return
  try {
    await updateTask(task.id, { dueDate: date, dueTime })
    toast.ok(dueTime ? `「${task.title}」排到${relativeDay(date)} ${dueTime}` : `「${task.title}」改到${relativeDay(date)}，不排时间`)
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '改期失败'))
  }
}

const openBlock = (b: Block) => {
  if (b.meetingId) router.push({ name: 'meeting', params: { id: b.meetingId } })
  else if (b.task) router.push({ path: '/tasks', query: { v: 'all', task: String(b.task.id) } })
}
const openTask = (t: Task) => router.push({ path: '/tasks', query: { v: 'all', task: String(t.id) } })
</script>

<template>
  <div class="wk">
    <p class="wk__sum">
      <span><i class="sw sw--meeting" />会议 <b>{{ dur(summary.meeting) }}</b></span>
      <span><i class="sw sw--task" />已排任务 <b>{{ dur(summary.task) }}</b></span>
      <span><i class="sw sw--focus" />已专注 <b>{{ dur(summary.focus) }}</b></span>
      <span v-if="summary.unscheduled">还有 <b>{{ summary.unscheduled }} 项</b>只定了日期、没排时间</span>
      <span v-if="summary.busiest" class="wk__busy">{{ relativeDay(summary.busiest.date) }}会议 {{ dur(summary.busiest.min) }}，是这周最满的一天</span>
    </p>

    <div class="wk__scroll surface">
      <div class="wk__grid">
        <!-- 日期头 -->
        <span class="corner" />
        <button
          v-for="d in days"
          :key="`h${d.date}`"
          type="button"
          class="head"
          :class="{ today: d.date === today, weekend: d.weekend }"
          :title="`看${monthDay(d.date)}的日视图`"
          @click="emit('openDay', d.date)"
        >
          <small>{{ d.weekday }}</small>
          <b>{{ d.day }}</b>
          <span v-if="d.mood" class="head__mood">{{ d.mood }}</span>
          <span v-if="d.holiday" class="head__holiday">{{ d.holiday }}</span>
        </button>

        <!-- 全天行 -->
        <span class="label">全天</span>
        <div
          v-for="d in days"
          :key="`a${d.date}`"
          class="allday"
          :class="{ weekend: d.weekend, over: ghost?.date === d.date && ghost.min === null }"
          @dragover="onAllDayOver($event, d.date)"
          @dragleave.self="ghost = null"
          @drop.prevent="onDrop($event, d.date, false)"
        >
          <button
            v-for="t in d.unscheduled.slice(0, ALLDAY_MAX)"
            :key="t.id"
            type="button"
            class="chip"
            :class="{ 'chip--drag': dragTask?.id === t.id }"
            draggable="true"
            :title="t.title"
            @dragstart="onDragStart($event, t)"
            @dragend="onDragEnd"
            @click="openTask(t)"
          >
            {{ t.title }}
          </button>
          <button v-if="d.unscheduled.length > ALLDAY_MAX" type="button" class="more" @click="emit('openDay', d.date)">+{{ d.unscheduled.length - ALLDAY_MAX }}</button>
          <span v-if="d.habitsTotal" class="habits" :title="`习惯 ${d.habitsDone} / ${d.habitsTotal}`">
            <i v-for="n in d.habitsTotal" :key="n" :class="{ on: n <= d.habitsDone }" />
          </span>
        </div>

        <!-- 时间轴 -->
        <div class="hours" :style="{ height: `${(END - START) * HOUR_PX}px` }">
          <span v-for="h in hours" :key="h" :style="{ top: `${(h - START) * HOUR_PX}px` }">{{ String(h).padStart(2, '0') }}:00</span>
        </div>
        <div
          v-for="d in days"
          :key="`c${d.date}`"
          class="col"
          :class="{ weekend: d.weekend, today: d.date === today }"
          :style="{ height: `${(END - START) * HOUR_PX}px`, backgroundSize: `100% ${HOUR_PX}px` }"
          @dragover="onColOver($event, d.date)"
          @dragleave.self="ghost = null"
          @drop.prevent="onDrop($event, d.date, true)"
          @dblclick.self="emit('openDay', d.date)"
        >
          <div v-if="d.date === today && nowMin >= START * 60 && nowMin < END * 60" class="now" :style="{ top: `${top(nowMin)}px` }" />
          <div
            v-if="ghost?.date === d.date && ghost.min !== null"
            class="ghost"
            :style="{ top: `${top(ghost.min)}px`, height: `${(((dragTask?.estimateMin ?? DEFAULT_MIN) / 60) * HOUR_PX) - 2}px` }"
          >
            <span>{{ toHm(ghost.min) }}</span>
          </div>
          <div
            v-for="b in d.blocks"
            :key="b.key"
            class="blk"
            :class="[`blk--${b.kind}`, { 'blk--done': b.done, 'blk--drag': dragTask && b.task?.id === dragTask.id, 'blk--short': b.end - b.start < 45 }]"
            :style="{
              top: `${top(b.start)}px`,
              height: `${Math.max(16, ((b.end - b.start) / 60) * HOUR_PX - 2)}px`,
              left: `${(b.lane / b.lanes) * 100}%`,
              width: `calc(${100 / b.lanes}% - 3px)`,
            }"
            :title="`${b.time} ${b.title}`"
            :draggable="b.kind === 'task' && !b.done"
            @dragstart="b.task && onDragStart($event, b.task)"
            @dragend="onDragEnd"
            @click="openBlock(b)"
          >
            <b>{{ b.done ? '✓ ' : '' }}{{ b.title }}</b>
            <small>{{ b.time }}</small>
          </div>
        </div>
      </div>
    </div>
    <p class="tip">把任务拖到某天某个时间就排进去，拖回「全天」行只保留日期。点日期看那天的日视图，双击空白处也行。</p>
  </div>
</template>

<style scoped>
.wk {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.wk__sum {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1.2rem;
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.wk__sum span {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.wk__sum b {
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.wk__busy {
  color: #b45309;
}

:root[data-theme='dark'] .wk__busy {
  color: #fbbf24;
}

.sw {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 2px;
}

.sw--meeting {
  background: #3b82f6;
}

.sw--task {
  background: var(--color-brand);
}

.sw--focus {
  background: var(--color-accent);
}

.wk__scroll {
  overflow-x: auto;
  border-radius: var(--radius-lg);
}

.wk__grid {
  display: grid;
  grid-template-columns: 3.2rem repeat(7, minmax(5.5rem, 1fr));
  min-width: 44rem;
}

.corner,
.label {
  border-bottom: 1px solid var(--color-border);
}

.label {
  padding: 0.45rem 0.4rem 0 0;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-align: right;
}

.head {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 0.35rem;
  padding: 0.5rem 0.5rem 0.4rem;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.head:hover {
  background: var(--color-bg-soft);
}

.head small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.head b {
  font-size: 1.15rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.head.today b {
  color: var(--color-brand);
}

.head.today {
  box-shadow: inset 0 -2px 0 var(--color-brand);
}

.head__mood {
  font-size: 0.85rem;
}

.head__holiday {
  flex-basis: 100%;
  overflow: hidden;
  color: #b45309;
  font-size: 0.66rem;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

:root[data-theme='dark'] .head__holiday {
  color: #fbbf24;
}

.weekend {
  background: color-mix(in srgb, var(--color-bg-soft) 45%, transparent);
}

.allday {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-height: 3.2rem;
  padding: 0.3rem 0.3rem 0.9rem;
  border-bottom: 2px solid var(--color-border);
  border-left: 1px solid var(--color-border);
  min-width: 0;
}

.allday.over {
  background: var(--color-brand-soft);
}

.chip {
  overflow: hidden;
  padding: 0.12rem 0.35rem;
  border: 0;
  border-left: 2px solid var(--color-brand);
  border-radius: 3px;
  background: var(--color-brand-soft);
  color: inherit;
  font-size: 0.72rem;
  line-height: 1.4;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: grab;
}

.chip--drag {
  opacity: 0.4;
}

.more {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  font-weight: 700;
  cursor: pointer;
}

.habits {
  position: absolute;
  bottom: 0.3rem;
  left: 0.35rem;
  display: flex;
  gap: 0.15rem;
}

.habits i {
  width: 0.38rem;
  height: 0.38rem;
  border: 1px solid var(--color-accent);
  border-radius: 50%;
}

.habits i.on {
  background: var(--color-accent);
}

.hours {
  position: relative;
}

.hours span {
  position: absolute;
  right: 0.4rem;
  font-size: 0.68rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
  transform: translateY(-0.1rem);
}

.hours span:first-child {
  display: none;
}

.col {
  position: relative;
  border-left: 1px solid var(--color-border);
  background-image: linear-gradient(to bottom, var(--color-border) 1px, transparent 1px);
}

.col.weekend {
  background-color: color-mix(in srgb, var(--color-bg-soft) 45%, transparent);
}

.now,
.ghost {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 3;
  pointer-events: none;
}

.now {
  height: 2px;
  background: var(--color-danger);
}

.now::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
}

.ghost {
  border: 2px dashed var(--color-brand);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--color-brand) 10%, transparent);
}

.ghost span {
  position: absolute;
  top: 0.1rem;
  left: 0.3rem;
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--color-brand);
}

.blk {
  position: absolute;
  display: flex;
  flex-direction: column;
  padding: 0.15rem 0.3rem;
  overflow: hidden;
  border-left: 3px solid;
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
  line-height: 1.35;
  cursor: pointer;
  user-select: none;
}

.blk b {
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blk small {
  font-size: 0.66rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.blk--short {
  flex-direction: row;
  align-items: center;
  gap: 0.3rem;
  padding-block: 0;
}

.blk--short small {
  order: -1;
  flex: none;
}

.blk--meeting {
  border-color: #3b82f6;
  background: color-mix(in srgb, #3b82f6 13%, var(--color-bg-surface));
}

.blk--task {
  z-index: 2;
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  cursor: grab;
}

.blk--focus {
  z-index: 1;
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
  cursor: default;
}

.blk--done {
  opacity: 0.6;
  cursor: pointer;
}

.blk--drag {
  opacity: 0.4;
}

.tip {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}
</style>
