<script setup lang="ts">
/**
 * 习惯详情：当前连续、最长连续、累计次数、近 30 天完成率；一年热力图（颜色深浅 = 完成度）；
 * 打卡日志（附备注，补打卡标「补」，中断的地方写明）。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { Icon } from '@iconify/vue'
import HabitCell from './HabitCell.vue'
import { addDays, monthDay, relativeDay, startOfWeek, todayYmd, fromYmd, hmOf } from '../../../utils/date'
import { freqText, isDone, isScheduled, logMap, progressOf, rateOf, streakOf, totalDone, valueText } from '../../../utils/habitStats'
import { HABIT_ICON_DEFAULT, iconOr } from '../../../config/icons'
import { HABIT_BACKFILL_DAYS, type Habit, type HabitLog } from '../../../types/habits'

const props = defineProps<{ habit: Habit; logs: HabitLog[] }>()
const emit = defineEmits<{ close: []; edit: []; archive: []; remove: []; set: [date: string, value: number, note?: string] }>()

const today = todayYmd()
const map = computed(() => logMap(props.logs))
const streak = computed(() => streakOf(props.habit, map.value))
const rate = computed(() => Math.round(rateOf(props.habit, map.value) * 100))
const total = computed(() => totalDone(props.habit, props.logs))
const since = computed(() => {
  const first = [...props.logs].sort((a, b) => a.date.localeCompare(b.date))[0]
  return first ? monthDay(first.date) : '—'
})

/** 53 列 × 7 行，最后一列是本周 */
const weeks = computed(() => {
  const end = startOfWeek(today)
  return Array.from({ length: 53 }, (_, w) => {
    const monday = addDays(end, (w - 52) * 7)
    return Array.from({ length: 7 }, (_, d) => {
      const date = addDays(monday, d)
      const log = map.value.get(date)
      return { date, level: date > today ? -1 : log ? Math.ceil(progressOf(props.habit, log) * 4) : 0 }
    })
  })
})

const monthLabels = computed(() =>
  weeks.value.map((week, i) => {
    const d = fromYmd(week[0]!.date)
    const prev = i ? fromYmd(weeks.value[i - 1]![0]!.date) : null
    return !prev || prev.getMonth() !== d.getMonth() ? `${d.getMonth() + 1}月` : ''
  }),
)

/** 最近 30 条日志，外加中断的日子 */
const journal = computed(() => {
  const rows: { date: string; log?: HabitLog; broken?: boolean }[] = []
  for (let i = 0; i < 45 && rows.length < 30; i += 1) {
    const date = addDays(today, -i)
    const log = map.value.get(date)
    if (log) rows.push({ date, log })
    else if (props.habit.freq.type !== 'weekly_n' && isScheduled(props.habit, date) && date < today && date >= props.habit.createTime.slice(0, 10)) {
      rows.push({ date, broken: true })
    }
  }
  return rows
})

/** 热力图默认显示最近几个月（最右边） */
const heatWrap = ref<HTMLElement | null>(null)
onMounted(async () => {
  await nextTick()
  if (heatWrap.value) heatWrap.value.scrollLeft = heatWrap.value.scrollWidth
})

const backfillDays = computed(() => Array.from({ length: HABIT_BACKFILL_DAYS + 1 }, (_, i) => addDays(today, -i)).reverse())
</script>

<template>
  <aside class="hd surface" :aria-label="`${habit.name} 详情`">
    <header class="hd__head">
      <span class="hd__icon"><Icon :icon="iconOr(habit.icon, HABIT_ICON_DEFAULT)" /></span>
      <div>
        <h2>{{ habit.name }}</h2>
        <p>{{ freqText(habit) }}<template v-if="habit.kind !== 'check'"> · 目标 {{ valueText(habit, habit.target) }}</template><template v-if="habit.reminders.length"> · {{ habit.reminders.join('、') }} 提醒</template></p>
      </div>
      <button class="btn btn--ghost" type="button" @click="emit('edit')"><Icon icon="lucide:pencil" />编辑</button>
      <button class="btn btn--quiet hd__close" type="button" aria-label="关闭" @click="emit('close')"><Icon icon="lucide:x" /></button>
    </header>

    <div class="stats">
      <div><b class="flame"><Icon icon="lucide:flame" />{{ streak.current }} {{ streak.unit }}</b><span>当前连续</span></div>
      <div><b>{{ streak.longest }} {{ streak.unit }}</b><span>最长连续</span></div>
      <div><b>{{ total }} 次</b><span>累计 · 始于 {{ since }}</span></div>
      <div><b>{{ rate }}%</b><span>近 30 天</span></div>
    </div>

    <div ref="heatWrap" class="heat-wrap">
      <div class="heat" role="img" :aria-label="`${habit.name}近一年打卡热力图`">
        <div class="heat__months">
          <span v-for="(m, i) in monthLabels" :key="i">{{ m }}</span>
        </div>
        <div class="heat__grid">
          <div v-for="(week, i) in weeks" :key="i" class="heat__col">
            <i v-for="d in week" :key="d.date" :class="`l${d.level}`" :title="`${d.date} ${map.get(d.date) ? valueText(habit, map.get(d.date)!.value) : ''}`" />
          </div>
        </div>
      </div>
    </div>
    <p class="legend">少 <i class="l1" /><i class="l2" /><i class="l3" /><i class="l4" /> 多</p>

    <section class="back">
      <h3>补打卡 <small>最多补前 {{ HABIT_BACKFILL_DAYS }} 天</small></h3>
      <div class="back__days">
        <span v-for="d in backfillDays" :key="d" class="back__day">
          <HabitCell :habit="habit" :date="d" :log="map.get(d)" @set="(v, n) => emit('set', d, v, n)" />
          <small>{{ relativeDay(d) }}</small>
        </span>
      </div>
    </section>

    <section class="log">
      <h3>打卡日志</h3>
      <ul>
        <li v-for="row in journal" :key="row.date" :class="{ broken: row.broken }">
          <span class="log__day">{{ relativeDay(row.date) }}</span>
          <template v-if="row.log">
            <span class="log__time">{{ hmOf(row.log.time) }}</span>
            <b :class="{ ok: isDone(habit, row.log) }">{{ isDone(habit, row.log) ? '完成' : valueText(habit, row.log.value) }}</b>
            <em v-if="row.log.backfilled" class="tag">补</em>
            <span class="log__note">{{ row.log.note }}</span>
          </template>
          <span v-else class="log__note">未完成</span>
        </li>
      </ul>
    </section>

    <footer class="hd__foot">
      <button class="btn btn--quiet" type="button" @click="emit('archive')"><Icon icon="lucide:archive" />{{ habit.archived ? '恢复' : '归档' }}</button>
      <button class="btn btn--quiet danger" type="button" @click="emit('remove')"><Icon icon="lucide:trash-2" />删除</button>
    </footer>
  </aside>
</template>

<style scoped>
.hd {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.1rem 1.2rem;
  border-radius: var(--radius-lg);
}

.hd__head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.hd__head > div {
  flex: 1;
  min-width: 0;
}

.hd__head h2 {
  font-size: 1.1rem;
  font-weight: 800;
}

.hd__head p {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.hd__head .btn {
  padding: 0.35rem 0.7rem;
  font-size: 0.84rem;
}

.hd__close {
  padding: 0.35rem !important;
}

.hd__icon {
  display: grid;
  place-items: center;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 1.4rem;
}

.hd__icon svg {
  width: 1.3rem;
  height: 1.3rem;
  color: var(--color-text-secondary);
}

.flame {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.flame svg {
  width: 1em;
  height: 1em;
  color: var(--color-warn, #d97706);
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.5rem;
}

.stats div {
  display: flex;
  flex-direction: column;
  padding: 0.6rem 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.stats b {
  font-size: 1.05rem;
}

.stats span {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.heat-wrap {
  overflow-x: auto;
}

.heat {
  display: inline-flex;
  flex-direction: column;
  gap: 0.2rem;
}

.heat__months {
  display: grid;
  grid-template-columns: repeat(53, 0.72rem);
  gap: 0.15rem;
  font-size: 0.62rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.heat__grid {
  display: flex;
  gap: 0.15rem;
}

.heat__col {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.heat i,
.legend i {
  display: block;
  width: 0.72rem;
  height: 0.72rem;
  border-radius: 2px;
  background: var(--color-bg-soft);
}

.heat i.l-1 {
  background: transparent;
}

.l1 {
  background: color-mix(in srgb, var(--color-accent) 28%, var(--color-bg-soft)) !important;
}

.l2 {
  background: color-mix(in srgb, var(--color-accent) 50%, var(--color-bg-soft)) !important;
}

.l3 {
  background: color-mix(in srgb, var(--color-accent) 75%, var(--color-bg-soft)) !important;
}

.l4 {
  background: var(--color-accent) !important;
}

.legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.2rem;
  margin-top: -0.7rem;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.back h3,
.log h3 {
  margin-bottom: 0.5rem;
  font-size: 0.82rem;
  font-weight: 700;
}

.back h3 small {
  margin-left: 0.3rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.back__days {
  display: flex;
  gap: 1.25rem;
}

.back__day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.log ul {
  max-height: 16rem;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.log li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.84rem;
}

.log li.broken {
  color: var(--color-text-secondary);
}

.log__day {
  width: 4rem;
  flex: none;
  color: var(--color-text-secondary);
}

.log__time {
  width: 2.8rem;
  flex: none;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.log b {
  flex: none;
  font-weight: 600;
}

.log b.ok {
  color: var(--color-accent-text);
}

.log em {
  font-style: normal;
}

.log__note {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-secondary);
}

.hd__foot {
  display: flex;
  gap: 0.3rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
}

.hd__foot .danger:hover {
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
