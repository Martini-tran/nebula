<script setup lang="ts">
/**
 * 日视图：把任务排进时间。
 * 左栏是这天还没排时间的任务，拖进时间轴就变成时间块（写入 dueTime；块高 = 预估时长，拖下边缘可调）。
 * 会议是只读块；专注记录以青色块回填，看得出计划和实际的差距。右栏汇总时间分配，排不下时提示。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { updateTask } from '../../../api/tasks'
import { errorText, toast } from '../../../composables/useToast'
import { relativeDay, todayYmd } from '../../../utils/date'
import type { CalendarData } from '../useCalendarData'
import type { Task } from '../../../types/tasks'

const props = defineProps<{ date: string; data: CalendarData }>()
const emit = defineEmits<{ changed: [] }>()
const router = useRouter()

const START = 7
const END = 23
const HOUR_PX = 56
const SNAP = 15
const DEFAULT_MIN = 30
/** 计算空闲时间的窗口 */
const DAY_FROM = 8 * 60
const DAY_TO = 22 * 60

const today = todayYmd()
const toMin = (hm: string) => {
  const [h, m] = hm.split(':').map(Number)
  return (h ?? 0) * 60 + (m ?? 0)
}
const toHm = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
const top = (min: number) => ((min - START * 60) / 60) * HOUR_PX
const dur = (min: number) => {
  const h = Math.floor(min / 60)
  const m = min % 60
  return h ? `${h}h${m ? ` ${m}m` : ''}` : `${m}m`
}

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => (timer = setInterval(() => (now.value = new Date()), 60_000)))
onBeforeUnmount(() => clearInterval(timer))
const nowMin = computed(() => now.value.getHours() * 60 + now.value.getMinutes())

// ── 数据 ──

const dayTasks = computed(() => props.data.tasksOn(props.date))
const unscheduled = computed(() => dayTasks.value.filter((t) => !t.done && !t.dueTime))
const overdue = computed(() => (props.date === today ? props.data.tasks.value.filter((t) => !t.done && t.dueDate && t.dueDate < today) : []))
const meetings = computed(() => props.data.meetingsOn(props.date))
const sessions = computed(() => props.data.sessionsOn(props.date).filter((s) => s.status === 'done'))

interface Block {
  key: string
  kind: 'meeting' | 'task' | 'focus'
  start: number
  end: number
  title: string
  sub: string
  task?: Task
  to?: object
  done?: boolean
}

/** 拖动中的预览 */
const drag = ref<{ key: string; start: number; end: number } | null>(null)

const blocks = computed<Block[]>(() => {
  const list: Block[] = []
  for (const m of meetings.value) {
    const s = toMin(m.startTime)
    list.push({ key: `m${m.id}`, kind: 'meeting', start: s, end: s + m.durationMin, title: m.title, sub: `${m.startTime}–${toHm(s + m.durationMin)} · ${m.attendees.length} 人`, to: { name: 'meeting', params: { id: String(m.id) } } })
  }
  for (const t of dayTasks.value.filter((x) => x.dueTime)) {
    const s = toMin(t.dueTime!)
    const len = t.estimateMin ?? DEFAULT_MIN
    const sub = t.subtasks.length ? ` · 子任务 ${t.subtasks.filter((x) => x.done).length}/${t.subtasks.length}` : ''
    list.push({ key: `t${t.id}`, kind: 'task', start: s, end: s + len, title: t.title, sub: `${t.dueTime}–${toHm(s + len)}${t.estimateMin ? '' : ' · 未预估'}${sub}`, task: t, done: t.done })
  }
  for (const f of sessions.value) {
    const s = toMin(f.startedAt.slice(11, 16))
    const e = Math.max(s + 10, toMin(f.endedAt.slice(11, 16)))
    list.push({ key: `f${f.id}`, kind: 'focus', start: s, end: e, title: `专注 · ${f.taskTitle}`, sub: `${toHm(s)}–${toHm(e)} · 实际 ${f.actualMin}m` })
  }
  // 拖动中的块用预览位置
  return list.map((b) => (drag.value?.key === b.key ? { ...b, start: drag.value.start, end: drag.value.end } : b))
})

/** 重叠的块分列并排 */
const laid = computed(() => {
  const sorted = [...blocks.value].sort((a, b) => a.start - b.start || b.end - a.end)
  const out: (Block & { lane: number; lanes: number })[] = []
  let cluster: (Block & { lane: number; lanes: number })[] = []
  let clusterEnd = -1
  const flush = () => {
    const lanes = Math.max(1, ...cluster.map((b) => b.lane + 1))
    cluster.forEach((b) => (b.lanes = lanes))
    out.push(...cluster)
    cluster = []
  }
  for (const b of sorted) {
    if (b.start >= clusterEnd && cluster.length) flush()
    const used = new Set(cluster.filter((c) => c.end > b.start).map((c) => c.lane))
    let lane = 0
    while (used.has(lane)) lane += 1
    cluster.push({ ...b, lane, lanes: 1 })
    clusterEnd = Math.max(clusterEnd, b.end)
  }
  if (cluster.length) flush()
  return out
})

// ── 汇总 ──

const sumOf = (list: Block[]) => list.reduce((sum, b) => sum + (b.end - b.start), 0)
const meetingMin = computed(() => sumOf(blocks.value.filter((b) => b.kind === 'meeting')))
const taskMin = computed(() => sumOf(blocks.value.filter((b) => b.kind === 'task')))
const focusMin = computed(() => sessions.value.reduce((sum, s) => sum + s.actualMin, 0))

/** 剩余空闲：08:00–22:00 窗口里（今天从现在算起）没被会议和已排任务占用的时间 */
const freeMin = computed(() => {
  if (props.date < today) return 0
  const from = props.date === today ? Math.max(DAY_FROM, nowMin.value) : DAY_FROM
  if (from >= DAY_TO) return 0
  const busy = blocks.value
    .filter((b) => b.kind !== 'focus' && !b.done)
    .map((b) => [Math.max(b.start, from), Math.min(b.end, DAY_TO)] as const)
    .filter(([s, e]) => e > s)
    .sort((a, b) => a[0] - b[0])
  let taken = 0
  let cursor = from
  for (const [s, e] of busy) {
    const start = Math.max(s, cursor)
    if (e > start) {
      taken += e - start
      cursor = e
    }
  }
  return DAY_TO - from - taken
})

const pendingMin = computed(() => [...unscheduled.value, ...overdue.value.filter((t) => !t.dueTime)].reduce((sum, t) => sum + (t.estimateMin ?? DEFAULT_MIN), 0))
const pendingCount = computed(() => unscheduled.value.length + overdue.value.filter((t) => !t.dueTime).length)

/** 计划 vs 实际：有专注记录的任务、开过的会 */
const planVsActual = computed(() => {
  const rows: { title: string; plan: number; actual: number }[] = []
  const byTask = new Map<string, { title: string; actual: number; taskId: string | null }>()
  for (const s of sessions.value) {
    const key = s.taskId !== null ? String(s.taskId) : s.taskTitle
    const cur = byTask.get(key) ?? { title: s.taskTitle, actual: 0, taskId: s.taskId !== null ? String(s.taskId) : null }
    cur.actual += s.actualMin
    byTask.set(key, cur)
  }
  for (const v of byTask.values()) {
    const task = v.taskId ? props.data.tasks.value.find((t) => String(t.id) === v.taskId) : undefined
    rows.push({ title: v.title, plan: task?.estimateMin ?? 0, actual: v.actual })
  }
  for (const m of meetings.value.filter((x) => x.startedAt && x.endedAt)) {
    const actual = Math.round((new Date(m.endedAt!.replace(' ', 'T')).getTime() - new Date(m.startedAt!.replace(' ', 'T')).getTime()) / 60000)
    rows.push({ title: m.title, plan: m.durationMin, actual })
  }
  return rows
})

// ── 拖动 ──

const axis = ref<HTMLElement | null>(null)
const ghost = ref<number | null>(null)

const minuteAt = (clientY: number) => {
  const rect = axis.value!.getBoundingClientRect()
  const raw = START * 60 + ((clientY - rect.top) / HOUR_PX) * 60
  return Math.min(END * 60 - SNAP, Math.max(START * 60, Math.round(raw / SNAP) * SNAP))
}

const save = async (task: Task, body: Parameters<typeof updateTask>[1], message: string) => {
  try {
    await updateTask(task.id, body)
    toast.ok(message)
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

/** 从左栏拖进来 */
const dragTaskId = ref<string | null>(null)
const onListDragStart = (event: DragEvent, task: Task) => {
  dragTaskId.value = String(task.id)
  event.dataTransfer?.setData('text/plain', task.title)
}
const onAxisDragOver = (event: DragEvent) => {
  if (!dragTaskId.value) return
  event.preventDefault()
  ghost.value = minuteAt(event.clientY)
}
const onAxisDrop = (event: DragEvent) => {
  const task = [...unscheduled.value, ...overdue.value].find((t) => String(t.id) === dragTaskId.value)
  const at = minuteAt(event.clientY)
  dragTaskId.value = null
  ghost.value = null
  if (task) save(task, { dueDate: props.date, dueTime: toHm(at) }, `「${task.title}」排到 ${toHm(at)}`)
}

/** 时间轴里的任务块：拖动改时间、拖下边缘改时长 */
let pointer: { key: string; task: Task; mode: 'move' | 'resize'; startY: number; start: number; end: number; moved: boolean } | null = null

const onBlockPointerDown = (event: PointerEvent, block: Block, mode: 'move' | 'resize') => {
  if (block.kind !== 'task' || !block.task || block.done) return
  event.preventDefault()
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  pointer = { key: block.key, task: block.task, mode, startY: event.clientY, start: block.start, end: block.end, moved: false }
}

const onBlockPointerMove = (event: PointerEvent) => {
  if (!pointer) return
  const delta = Math.round((((event.clientY - pointer.startY) / HOUR_PX) * 60) / SNAP) * SNAP
  if (delta) pointer.moved = true
  if (pointer.mode === 'move') {
    const len = pointer.end - pointer.start
    const start = Math.min(END * 60 - len, Math.max(START * 60, pointer.start + delta))
    drag.value = { key: pointer.key, start, end: start + len }
  } else {
    drag.value = { key: pointer.key, start: pointer.start, end: Math.max(pointer.start + SNAP, pointer.end + delta) }
  }
}

const onBlockPointerUp = () => {
  const p = pointer
  const d = drag.value
  pointer = null
  if (!p) return
  if (!p.moved || !d) {
    drag.value = null
    if (p.mode === 'move') router.push({ path: '/tasks', query: { v: 'all', task: String(p.task.id) } })
    return
  }
  if (p.mode === 'move') save(p.task, { dueTime: toHm(d.start) }, `改到 ${toHm(d.start)}`).finally(() => (drag.value = null))
  else save(p.task, { estimateMin: d.end - d.start }, `预估改为 ${dur(d.end - d.start)}`).finally(() => (drag.value = null))
}

const unschedule = (task: Task) => save(task, { dueTime: null }, `「${task.title}」移回待安排`)

const hours = Array.from({ length: END - START }, (_, i) => START + i)
</script>

<template>
  <div class="dt">
    <aside class="dt__todo surface">
      <h3>待安排 · {{ unscheduled.length }}</h3>
      <p class="muted">拖到右侧时间轴排进{{ relativeDay(date) }}。</p>
      <div
        v-for="t in unscheduled"
        :key="t.id"
        class="chip"
        :class="{ 'chip--drag': dragTaskId === String(t.id) }"
        draggable="true"
        @dragstart="onListDragStart($event, t)"
        @dragend="dragTaskId = null; ghost = null"
      >
        <Icon icon="lucide:grip-vertical" />
        <span><b>{{ t.title }}</b><small>{{ t.estimateMin ? `预计 ${dur(t.estimateMin)}` : '未预估' }}</small></span>
      </div>
      <p v-if="!unscheduled.length" class="muted">都排好了。</p>
      <template v-if="overdue.length">
        <h3 class="late">已过期 · {{ overdue.length }}</h3>
        <div
          v-for="t in overdue"
          :key="t.id"
          class="chip chip--late"
          draggable="true"
          @dragstart="onListDragStart($event, t)"
          @dragend="dragTaskId = null; ghost = null"
        >
          <Icon icon="lucide:grip-vertical" />
          <span><b>{{ t.title }}</b><small>{{ relativeDay(t.dueDate!) }}{{ t.dueTime ? ` ${t.dueTime}` : '' }}</small></span>
        </div>
      </template>
    </aside>

    <section class="dt__axis-wrap surface">
      <div
        ref="axis"
        class="dt__axis"
        :style="{ height: `${(END - START) * HOUR_PX}px` }"
        @dragover="onAxisDragOver"
        @dragleave="ghost = null"
        @drop.prevent="onAxisDrop"
      >
        <div v-for="h in hours" :key="h" class="hour" :style="{ top: `${(h - START) * HOUR_PX}px` }">
          <span>{{ String(h).padStart(2, '0') }}:00</span>
        </div>
        <div v-if="date === today && nowMin >= START * 60 && nowMin < END * 60" class="now" :style="{ top: `${top(nowMin)}px` }">
          <span>{{ toHm(nowMin) }}</span>
        </div>
        <div v-if="ghost !== null" class="ghost" :style="{ top: `${top(ghost)}px` }"><span>{{ toHm(ghost) }}</span></div>

        <div class="lanes">
          <component
            :is="b.to ? 'router-link' : 'div'"
            v-for="b in laid"
            :key="b.key"
            :to="b.to"
            class="blk"
            :class="[`blk--${b.kind}`, { 'blk--done': b.done, 'blk--drag': drag?.key === b.key, 'blk--short': b.end - b.start <= 30 }]"
            :style="{
              top: `${top(b.start)}px`,
              height: `${Math.max(18, ((b.end - b.start) / 60) * HOUR_PX - 2)}px`,
              left: `${(b.lane / b.lanes) * 100}%`,
              width: `calc(${100 / b.lanes}% - 4px)`,
            }"
            @pointerdown="b.kind === 'task' && onBlockPointerDown($event, b, 'move')"
            @pointermove="onBlockPointerMove"
            @pointerup="onBlockPointerUp"
          >
            <b>{{ b.done ? '✓ ' : '' }}{{ b.title }}</b>
            <small>{{ b.sub }}</small>
            <button v-if="b.kind === 'task' && !b.done" type="button" class="blk__x" title="移回待安排" @pointerdown.stop @click.stop="unschedule(b.task!)">
              <Icon icon="lucide:x" />
            </button>
            <span
              v-if="b.kind === 'task' && !b.done"
              class="blk__resize"
              title="拖动调整时长"
              @pointerdown.stop="onBlockPointerDown($event, b, 'resize')"
              @pointermove="onBlockPointerMove"
              @pointerup="onBlockPointerUp"
            />
          </component>
        </div>
      </div>
    </section>

    <aside class="dt__sum surface">
      <h3>{{ relativeDay(date) }}的时间</h3>
      <dl>
        <dt><i class="sw sw--meeting" />会议</dt><dd>{{ dur(meetingMin) }}</dd>
        <dt><i class="sw sw--task" />已排任务</dt><dd>{{ dur(taskMin) }}</dd>
        <dt><i class="sw sw--focus" />已专注</dt><dd>{{ dur(focusMin) }}</dd>
        <dt><i class="sw" />剩余空闲</dt><dd>{{ date < today ? '—' : dur(freeMin) }}</dd>
      </dl>
      <p v-if="pendingCount && date >= today" class="fit" :class="{ 'fit--no': pendingMin > freeMin }">
        还有 <b>{{ pendingCount }} 项</b>任务没排时间，预计共 {{ dur(pendingMin) }}，{{ pendingMin > freeMin ? '排不下了，挪几项到别天吧。' : '放得下。' }}
      </p>
      <template v-if="planVsActual.length">
        <h3>计划 vs 实际</h3>
        <ul class="pva">
          <li v-for="r in planVsActual" :key="r.title">
            <span>{{ r.title }}</span>
            <small>计划 {{ r.plan ? dur(r.plan) : '—' }} · 实际 <b :class="{ over: r.plan && r.actual > r.plan }">{{ dur(r.actual) }}</b></small>
          </li>
        </ul>
      </template>
      <p class="muted">未预估的任务按 30 分钟算；空闲按 08:00–22:00 计。</p>
    </aside>
  </div>
</template>

<style scoped>
.dt {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr) 16rem;
  gap: 1rem;
  align-items: start;
}

.dt__todo,
.dt__sum {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border-radius: var(--radius-lg);
}

h3 {
  font-size: 0.82rem;
  font-weight: 700;
}

h3.late {
  margin-top: 0.5rem;
  color: var(--color-danger);
}

.muted {
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.chip {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-brand);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  cursor: grab;
}

.chip--late {
  border-left-color: var(--color-danger);
}

.chip--drag {
  opacity: 0.4;
}

.chip > svg {
  flex: none;
  color: var(--color-text-secondary);
}

.chip span {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chip b {
  font-size: 0.86rem;
  font-weight: 600;
}

.chip small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.dt__axis-wrap {
  padding: 0.75rem 0.75rem 0.75rem 0;
  border-radius: var(--radius-lg);
}

.dt__axis {
  position: relative;
  margin-left: 3.5rem;
}

.hour {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px solid var(--color-border);
}

.hour span {
  position: absolute;
  top: -0.55rem;
  left: -3.3rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.now,
.ghost {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 3;
  height: 2px;
  background: var(--color-danger);
  pointer-events: none;
}

.now span,
.ghost span {
  position: absolute;
  top: -0.55rem;
  left: -3.3rem;
  padding: 0 0.2rem;
  border-radius: 3px;
  background: var(--color-danger);
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
}

.ghost {
  background: var(--color-brand);
}

.ghost span {
  background: var(--color-brand);
}

.lanes {
  position: absolute;
  inset: 0 0 0 0.25rem;
}

.blk {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 0.05rem;
  padding: 0.25rem 0.45rem;
  overflow: hidden;
  border-left: 3px solid;
  border-radius: var(--radius-sm);
  font-size: 0.78rem;
  touch-action: none;
  user-select: none;
}

.blk b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.blk small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.blk--short {
  flex-direction: row;
  align-items: center;
  gap: 0.4rem;
  padding-block: 0;
}

.blk--short b {
  flex: 0 1 auto;
}

.blk--short small {
  flex: 1 1 0;
}

.blk--meeting {
  border-color: #3b82f6;
  background: color-mix(in srgb, #3b82f6 13%, var(--color-bg-surface));
}

.blk--task {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  cursor: grab;
}

.blk--focus {
  z-index: 1;
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.blk--done {
  cursor: default;
  opacity: 0.6;
}

.blk--drag {
  z-index: 5;
  box-shadow: var(--shadow-md);
  cursor: grabbing;
}

.blk__x {
  position: absolute;
  top: 0.15rem;
  right: 0.15rem;
  display: none;
  place-items: center;
  width: 1.2rem;
  height: 1.2rem;
  border: 0;
  border-radius: 50%;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
}

.blk:hover .blk__x {
  display: grid;
}

.blk__resize {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 6px;
  cursor: ns-resize;
}

.blk__resize::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 1px;
  width: 1.4rem;
  height: 3px;
  border-radius: 2px;
  background: color-mix(in srgb, var(--color-brand) 50%, transparent);
  transform: translateX(-50%);
  opacity: 0;
}

.blk:hover .blk__resize::after {
  opacity: 1;
}

.dt__sum dl {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.86rem;
}

.dt__sum dt {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-text-secondary);
}

.dt__sum dd {
  margin: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sw {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 2px;
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
}

.sw--meeting {
  background: #3b82f6;
  border: 0;
}

.sw--task {
  background: var(--color-brand);
  border: 0;
}

.sw--focus {
  background: var(--color-accent);
  border: 0;
}

.fit {
  padding: 0.55rem 0.65rem;
  border-radius: var(--radius-md);
  background: var(--color-accent-soft);
  font-size: 0.8rem;
  line-height: 1.6;
}

.fit--no {
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
  color: var(--color-danger);
}

.pva {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.82rem;
}

.pva li {
  display: flex;
  flex-direction: column;
}

.pva span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pva small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.pva b.over {
  color: var(--color-danger);
}

@media (max-width: 1180px) {
  .dt {
    grid-template-columns: 14rem minmax(0, 1fr);
  }

  .dt__sum {
    grid-column: 1 / -1;
    position: static;
  }
}

@media (max-width: 760px) {
  .dt {
    grid-template-columns: 1fr;
  }

  .dt__todo {
    position: static;
  }
}
</style>
