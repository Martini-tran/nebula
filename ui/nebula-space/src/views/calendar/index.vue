<script setup lang="ts">
/**
 * 日历：把任务、会议、日记、习惯放到同一条时间轴上（不是新数据，只是另一种看法）。
 * 月视图：每格最多 3 条（会议优先，其次未完成任务），其余折叠成「+N」；底部小圆点是当天习惯完成情况，
 * 表情是晚间回顾写下的心情。点某天在右侧展开明细，双击在那天新建任务，拖动任务到另一天即改期。
 * 日视图：把任务排进时间（见 DayTimeline）。状态同步到地址栏：?view=day&date= / ?month=
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import DayPanel from './components/DayPanel.vue'
import DayTimeline from './components/DayTimeline.vue'
import { useCalendarData } from './useCalendarData'
import { updateTask } from '../../api/tasks'
import { errorText, toast } from '../../composables/useToast'
import { addDays, fromYmd, monthDay, relativeDay, startOfWeek, todayYmd, weekdayLabel, weekdayOf } from '../../utils/date'
import { holidayOf } from '../../utils/holidays'

const route = useRoute()
const router = useRouter()
const data = useCalendarData()
const today = todayYmd()

const view = computed(() => (route.query.view === 'day' ? 'day' : 'month'))
const selected = computed(() => (typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : today))
const month = computed(() => (typeof route.query.month === 'string' && /^\d{4}-\d{2}$/.test(route.query.month) ? route.query.month : selected.value.slice(0, 7)))

const go = (query: Record<string, string | undefined>) => router.replace({ query: { ...route.query, ...query } })

// ── 月视图 ──

const gridStart = computed(() => startOfWeek(`${month.value}-01`))
const cells = computed(() =>
  Array.from({ length: 42 }, (_, i) => {
    const date = addDays(gridStart.value, i)
    return { date, day: fromYmd(date).getDate(), inMonth: date.slice(0, 7) === month.value, weekend: [0, 6].includes(weekdayOf(date)) }
  }),
)
/** 最后一行整行都在下个月时不显示 */
const visibleCells = computed(() => (cells.value[35]!.inMonth ? cells.value : cells.value.slice(0, 35)))

const range = computed(() =>
  view.value === 'day' ? [selected.value, selected.value] : [gridStart.value, addDays(gridStart.value, 41)],
)
const reload = () => data.load(range.value[0]!, range.value[1]!)
watch(range, reload)
onMounted(reload)

const title = computed(() => {
  if (view.value === 'day') return `${monthDay(selected.value)} · ${weekdayLabel(selected.value)}`
  const [y, m] = month.value.split('-')
  return `${y} 年 ${Number(m)} 月`
})

const shift = (delta: number) => {
  if (view.value === 'day') {
    const date = addDays(selected.value, delta)
    go({ date, month: undefined })
    return
  }
  const d = fromYmd(`${month.value}-01`)
  d.setMonth(d.getMonth() + delta)
  go({ month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` })
}

const goToday = () => go({ date: today, month: undefined })

/** 每格的条目：会议优先，其次未完成任务 */
const itemsOf = (date: string) => {
  const meetings = data.meetingsOn(date).map((m) => ({ key: `m${m.id}`, kind: 'meeting' as const, text: `${m.startTime} ${m.title}`, taskId: null }))
  const tasks = data
    .tasksOn(date)
    .filter((t) => !t.done)
    .sort((a, b) => String(a.dueTime ?? '99').localeCompare(String(b.dueTime ?? '99')))
    .map((t) => ({ key: `t${t.id}`, kind: 'task' as const, text: t.title, taskId: String(t.id) }))
  const all = [...meetings, ...tasks]
  return { shown: all.slice(0, 3), more: Math.max(0, all.length - 3) }
}

const selectDay = (date: string) => go({ date })
const focusAdd = ref(0)
const addOn = (date: string) => {
  go({ date })
  focusAdd.value += 1
}

// 拖动任务到另一天
const dragId = ref<string | null>(null)
const overDate = ref<string | null>(null)
const onDrop = async (date: string) => {
  const id = dragId.value
  dragId.value = null
  overDate.value = null
  const task = data.tasks.value.find((t) => String(t.id) === id)
  if (!task || task.dueDate === date) return
  try {
    await updateTask(task.id, { dueDate: date })
    toast.ok(`「${task.title}」改到${relativeDay(date)}`)
    reload()
  } catch (error) {
    toast.error(errorText(error, '改期失败'))
  }
}
</script>

<template>
  <div class="cal page">
    <header class="cal__head">
      <div class="cal__nav">
        <button class="btn btn--ghost" type="button" aria-label="上一个" @click="shift(-1)"><Icon icon="lucide:chevron-left" /></button>
        <h1 class="page-title">{{ title }}</h1>
        <button class="btn btn--ghost" type="button" aria-label="下一个" @click="shift(1)"><Icon icon="lucide:chevron-right" /></button>
        <button class="btn btn--ghost" type="button" @click="goToday">今天</button>
      </div>
      <div class="seg" role="tablist" aria-label="视图">
        <button type="button" role="tab" :aria-selected="view === 'month'" :class="{ on: view === 'month' }" @click="go({ view: undefined })">月</button>
        <button type="button" role="tab" :aria-selected="view === 'day'" :class="{ on: view === 'day' }" @click="go({ view: 'day' })">日</button>
      </div>
      <p v-if="view === 'month'" class="legend">
        <span><i class="lg lg--meeting" />会议</span>
        <span><i class="lg lg--task" />任务</span>
        <span><i class="lg lg--habit" />习惯完成</span>
      </p>
    </header>

    <StateBlock v-if="data.error.value" state="error" :description="data.error.value" action-label="重试" @action="reload" />

    <div v-else-if="view === 'month'" class="month">
      <div class="grid surface" :class="{ loading: data.loading.value }">
        <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="grid__w">{{ w }}</span>
        <div
          v-for="c in visibleCells"
          :key="c.date"
          class="cell"
          :class="{ out: !c.inMonth, weekend: c.weekend, today: c.date === today, on: c.date === selected, over: overDate === c.date }"
          @click="selectDay(c.date)"
          @dblclick.self="addOn(c.date)"
          @dragover.prevent="overDate = c.date"
          @dragleave.self="overDate = null"
          @drop.prevent="onDrop(c.date)"
        >
          <div class="cell__top" @dblclick="addOn(c.date)">
            <span class="cell__day">{{ c.day === 1 ? `${fromYmd(c.date).getMonth() + 1}/1` : c.day }}</span>
            <span v-if="data.moodOn(c.date)" class="cell__mood">{{ data.moodOn(c.date) }}</span>
            <span v-if="holidayOf(c.date)" class="cell__holiday">{{ holidayOf(c.date) }}</span>
          </div>
          <div
            v-for="item in itemsOf(c.date).shown"
            :key="item.key"
            class="item"
            :class="`item--${item.kind}`"
            :draggable="item.kind === 'task'"
            @dragstart="dragId = item.taskId"
            @dragend="dragId = null; overDate = null"
          >
            {{ item.text }}
          </div>
          <span v-if="itemsOf(c.date).more" class="more">+{{ itemsOf(c.date).more }}</span>
          <span v-if="c.date <= today && data.habitsOn(c.date).length" class="dots" :title="`习惯 ${data.habitsOn(c.date).filter((h) => h.done).length} / ${data.habitsOn(c.date).length}`">
            <i v-for="h in data.habitsOn(c.date)" :key="h.habit.id" :class="{ on: h.done }" />
          </span>
        </div>
      </div>
      <DayPanel class="month__panel" :date="selected" :data="data" :focus-add="focusAdd" @changed="reload" @open-day="go({ view: 'day' })" />
    </div>

    <DayTimeline v-else :date="selected" :data="data" @changed="reload" />
    <p v-if="view === 'month'" class="tip">点某天看明细，双击空白处在那天新建任务，拖动任务到另一天即改期。节日只标公历固定日期，放假安排以官方公告为准。</p>
  </div>
</template>

<style scoped>
.cal {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.cal__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
}

.cal__nav {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.cal__nav .btn {
  padding: 0.4rem 0.6rem;
}

.cal__nav .page-title {
  min-width: 9rem;
  text-align: center;
  font-size: 1.4rem;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 0.9rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.legend {
  display: flex;
  gap: 0.9rem;
  margin-left: auto;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.lg {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 2px;
}

.lg--meeting {
  background: #3b82f6;
}

.lg--task {
  background: var(--color-brand);
}

.lg--habit {
  border-radius: 50%;
  background: var(--color-accent);
}

.month {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20rem;
  gap: 1rem;
  align-items: start;
}

.month__panel {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  overflow: hidden;
  border-radius: var(--radius-lg);
  transition: opacity 0.2s ease;
}

.grid.loading {
  opacity: 0.6;
}

.grid__w {
  padding: 0.45rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-align: center;
}

.cell {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-height: 7.2rem;
  padding: 0.35rem 0.35rem 1rem;
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
  min-width: 0;
}

.cell:nth-child(7n + 7) {
  border-right: 0;
}

.cell.weekend {
  background: color-mix(in srgb, var(--color-bg-soft) 45%, transparent);
}

.cell.out {
  opacity: 0.5;
}

.cell:hover {
  background: var(--color-bg-soft);
}

.cell.on {
  box-shadow: inset 0 0 0 2px var(--color-brand);
}

.cell.over {
  background: var(--color-brand-soft);
}

.cell__top {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 0;
}

.cell__day {
  display: grid;
  place-items: center;
  min-width: 1.5rem;
  height: 1.5rem;
  padding: 0 0.2rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 700;
}

.cell.today .cell__day {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.cell__mood {
  font-size: 0.85rem;
}

.cell__holiday {
  margin-left: auto;
  padding: 0 0.3rem;
  overflow: hidden;
  border-radius: 3px;
  background: color-mix(in srgb, #f59e0b 20%, transparent);
  color: #b45309;
  font-size: 0.66rem;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

:root[data-theme='dark'] .cell__holiday {
  color: #fbbf24;
}

.item {
  overflow: hidden;
  padding: 0.1rem 0.35rem;
  border-left: 2px solid;
  border-radius: 3px;
  font-size: 0.72rem;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item--meeting {
  border-color: #3b82f6;
  background: color-mix(in srgb, #3b82f6 12%, transparent);
}

.item--task {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  cursor: grab;
}

.more {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.dots {
  position: absolute;
  bottom: 0.3rem;
  left: 0.45rem;
  display: flex;
  gap: 0.15rem;
}

.dots i {
  width: 0.38rem;
  height: 0.38rem;
  border: 1px solid var(--color-accent);
  border-radius: 50%;
}

.dots i.on {
  background: var(--color-accent);
}

.tip {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

@media (max-width: 1100px) {
  .month {
    grid-template-columns: 1fr;
  }

  .month__panel {
    position: static;
  }
}

@media (max-width: 640px) {
  .cell {
    min-height: 4.2rem;
  }

  .item,
  .cell__holiday {
    display: none;
  }

  .legend {
    display: none;
  }
}
</style>
