<script setup lang="ts">
/**
 * 习惯：与任务的区别是「任务做完就消失，习惯看的是连续与频率」。
 * 一行一个习惯，横向本周七天，今天那一列高亮；连续断了显示「上次 N 天」，不清零到让人泄气。
 * 点习惯名在右侧打开详情（?id=）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import HabitCell from './components/HabitCell.vue'
import HabitDialog from './components/HabitDialog.vue'
import HabitDetail from './components/HabitDetail.vue'
import { deleteHabit, fetchHabitLogs, fetchHabits, setHabitLog, updateHabit } from '../../api/habits'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { addDays, fromYmd, startOfWeek, todayYmd } from '../../utils/date'
import { freqText, isDone, isScheduled, logMap, rateOf, streakOf } from '../../utils/habitStats'
import { HABIT_ICON_DEFAULT, iconOr } from '../../config/icons'
import type { Habit, HabitLog } from '../../types/habits'

const route = useRoute()
const router = useRouter()
const today = todayYmd()

const habits = ref<Habit[]>([])
const logs = ref<HabitLog[]>([])
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    const [list, logList] = await Promise.all([fetchHabits(true), fetchHabitLogs({ from: addDays(today, -400) })])
    habits.value = list
    logs.value = logList
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const showArchived = ref(false)
const visibleHabits = computed(() => habits.value.filter((h) => showArchived.value || !h.archived))
const archivedCount = computed(() => habits.value.filter((h) => h.archived).length)

/** 每个习惯的打卡按日期索引 */
const maps = computed(() => {
  const byHabit = new Map<string, HabitLog[]>()
  for (const l of logs.value) byHabit.set(String(l.habitId), [...(byHabit.get(String(l.habitId)) ?? []), l])
  return new Map([...byHabit.entries()].map(([id, list]) => [id, logMap(list)]))
})
const mapOf = (habit: Habit) => maps.value.get(String(habit.id)) ?? new Map()

// ── 本周 ──

const weekOffset = computed(() => Number(route.query.week ?? 0) || 0)
const monday = computed(() => addDays(startOfWeek(today), weekOffset.value * 7))
const days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(monday.value, i)))
const weekTitle = computed(() => {
  const a = fromYmd(monday.value)
  const b = fromYmd(addDays(monday.value, 6))
  return `${a.getMonth() + 1}/${a.getDate()} – ${b.getMonth() + 1}/${b.getDate()}`
})
const shiftWeek = (delta: number) => {
  const next = weekOffset.value + delta
  router.replace({ query: { ...route.query, week: next ? String(next) : undefined } })
}

/** 本周完成率：到今天为止安排了的格子里完成了多少 */
const weekRate = computed(() => {
  let ok = 0
  let total = 0
  for (const h of visibleHabits.value.filter((x) => !x.archived)) {
    if (h.freq.type === 'weekly_n') {
      const n = days.value.filter((d) => isDone(h, mapOf(h).get(d))).length
      total += h.freq.n
      ok += Math.min(n, h.freq.n)
      continue
    }
    for (const d of days.value) {
      if (d > today || !isScheduled(h, d)) continue
      if (d === today && !isDone(h, mapOf(h).get(d))) continue
      total += 1
      if (isDone(h, mapOf(h).get(d))) ok += 1
    }
  }
  return total ? Math.round((ok / total) * 100) : 0
})

const weekCountText = (h: Habit) => {
  if (h.freq.type !== 'weekly_n') return ''
  return `本周 ${days.value.filter((d) => isDone(h, mapOf(h).get(d))).length} / ${h.freq.n}`
}

/** 「喝水 8 杯」「读书 30 分钟」 */
const titleOf = (h: Habit) => (h.kind === 'check' ? h.name : `${h.name} ${h.target} ${h.kind === 'duration' ? '分钟' : h.unit || '次'}`)

// ── 打卡 ──

const setLog = async (habit: Habit, date: string, value: number, note?: string) => {
  const before = logs.value
  const id = `${habit.id}-${date}`
  const rest = logs.value.filter((l) => !(String(l.habitId) === String(habit.id) && l.date === date))
  logs.value = value > 0 ? [...rest, { id, habitId: habit.id, date, value, note: note ?? '', backfilled: date < today, time: `${today} 00:00:00` }] : rest
  try {
    const saved = await setHabitLog(habit.id, date, value, note)
    logs.value = saved ? [...rest, saved] : rest
    if (value >= habit.target && date === today) {
      const s = streakOf(habit, mapOf(habit))
      if (s.current > 1) toast.ok(`${habit.name} 连续 ${s.current} ${s.unit}`)
    }
  } catch (error) {
    logs.value = before
    toast.error(errorText(error, '打卡失败'))
  }
}

/** 计数型的「+」：今天加一 */
const plusOne = (habit: Habit) => setLog(habit, today, (mapOf(habit).get(today)?.value ?? 0) + 1)

// ── 详情 / 编辑 ──

const selected = computed(() => habits.value.find((h) => String(h.id) === route.query.id) ?? null)
const selectedLogs = computed(() => (selected.value ? logs.value.filter((l) => String(l.habitId) === String(selected.value!.id)) : []))
const openDetail = (h: Habit) => router.replace({ query: { ...route.query, id: selected.value?.id === h.id ? undefined : String(h.id) } })
const closeDetail = () => router.replace({ query: { ...route.query, id: undefined } })

const dialog = ref<{ open: boolean; habit: Habit | null }>({ open: false, habit: null })
const onSaved = (habit: Habit) => {
  const creating = !dialog.value.habit
  dialog.value.open = false
  toast.ok(creating ? `已添加「${habit.name}」` : '已保存')
  load()
}

const archive = async (habit: Habit) => {
  try {
    await updateHabit(habit.id, { archived: !habit.archived })
    toast.ok(habit.archived ? '已恢复' : '已归档，打卡记录保留')
    closeDetail()
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const remove = async (habit: Habit) => {
  const ok = await confirm({
    title: `删除习惯「${habit.name}」？`,
    message: '所有打卡记录会一起删除，无法撤销。只是暂时不想做的话，可以归档。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteHabit(habit.id)
    closeDetail()
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <div class="hb page" :class="{ 'hb--detail': selected }">
    <section class="hb__main">
      <header class="hb__head">
        <div>
          <h1 class="page-title">习惯</h1>
          <p class="page-subtitle">
            <button type="button" class="wk" aria-label="上一周" @click="shiftWeek(-1)"><Icon icon="lucide:chevron-left" /></button>
            {{ weekOffset === 0 ? '本周' : weekOffset === -1 ? '上周' : '' }} {{ weekTitle }}
            <button type="button" class="wk" aria-label="下一周" :disabled="weekOffset >= 0" @click="shiftWeek(1)"><Icon icon="lucide:chevron-right" /></button>
            · 完成率 <b>{{ weekRate }}%</b>
          </p>
        </div>
        <button class="btn btn--primary" type="button" @click="dialog = { open: true, habit: null }"><Icon icon="lucide:plus" />新习惯</button>
      </header>

      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
      <StateBlock
        v-else-if="!visibleHabits.length"
        state="empty"
        title="还没有习惯"
        description="每天或每周重复、不需要截止时间的事，比如晨跑、喝水、读书。"
        action-label="新习惯"
        @action="dialog = { open: true, habit: null }"
      />

      <div v-else class="tbl-wrap surface">
        <table class="tbl">
          <thead>
            <tr>
              <th class="c-name">习惯</th>
              <th v-for="d in days" :key="d" class="c-day" :class="{ today: d === today }">
                {{ d === today ? '今天' : '一二三四五六日'[(fromYmd(d).getDay() + 6) % 7] }}
                <small>{{ fromYmd(d).getDate() }}</small>
              </th>
              <th class="c-streak">连续</th>
              <th class="c-rate">近 30 天</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="h in visibleHabits" :key="h.id" :class="{ on: selected?.id === h.id, archived: h.archived }">
              <td class="c-name">
                <button type="button" class="name" @click="openDetail(h)">
                  <span class="name__icon"><Icon :icon="iconOr(h.icon, HABIT_ICON_DEFAULT)" /></span>
                  <span class="name__text">
                    <b>{{ titleOf(h) }}</b>
                    <small>{{ weekCountText(h) || freqText(h) }}<template v-if="h.kind !== 'check'"> · {{ h.kind === 'count' ? '计数' : '时长' }}</template><template v-if="h.reminders.length"> · {{ h.reminders[0] }} 提醒</template><template v-if="h.archived"> · 已归档</template></small>
                  </span>
                </button>
              </td>
              <td v-for="d in days" :key="d" class="c-day" :class="{ today: d === today }">
                <HabitCell :habit="h" :date="d" :log="mapOf(h).get(d)" @set="(v, n) => setLog(h, d, v, n)" />
              </td>
              <td class="c-streak">
                <template v-for="s in [streakOf(h, mapOf(h))]" :key="'s'">
                  <span v-if="s.current" class="streak"><Icon icon="lucide:flame" />{{ s.current }} {{ s.unit }}</span>
                  <span v-else-if="s.last" class="muted">上次 {{ s.last }} {{ s.unit }}</span>
                  <span v-else class="muted">—</span>
                </template>
                <button v-if="h.kind === 'count' && weekOffset === 0" type="button" class="plus" :title="`今天 +1 ${h.unit || '次'}`" @click="plusOne(h)"><Icon icon="lucide:plus" /></button>
              </td>
              <td class="c-rate">
                <span class="rate"><i :style="{ width: `${rateOf(h, mapOf(h)) * 100}%` }" /></span>
                {{ Math.round(rateOf(h, mapOf(h)) * 100) }}%
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <button v-if="archivedCount" type="button" class="archived-toggle" @click="showArchived = !showArchived">
        {{ showArchived ? '隐藏' : '显示' }}已归档的 {{ archivedCount }} 个习惯
      </button>
      <p class="tip">虚线圈 = 那天没安排（或「每周 N 次」不要求每天做）。只能补打前 2 天，补的会标「补」。</p>
    </section>

    <HabitDetail
      v-if="selected"
      :key="selected.id"
      class="hb__detail"
      :habit="selected"
      :logs="selectedLogs"
      @close="closeDetail"
      @edit="dialog = { open: true, habit: selected }"
      @archive="archive(selected)"
      @remove="remove(selected)"
      @set="(d, v, n) => setLog(selected!, d, v, n)"
    />

    <HabitDialog :open="dialog.open" :habit="dialog.habit" @close="dialog.open = false" @saved="onSaved" />
  </div>
</template>

<style scoped>
.hb {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.hb--detail {
  grid-template-columns: minmax(0, 1fr) 26rem;
}

.hb__main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.hb__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem;
}

.hb__head .page-subtitle {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.wk {
  display: inline-grid;
  place-items: center;
  width: 1.6rem;
  height: 1.6rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.wk:hover:not(:disabled) {
  background: var(--color-bg-soft);
}

.wk:disabled {
  opacity: 0.35;
}

.tbl-wrap {
  overflow-x: auto;
  border-radius: var(--radius-lg);
}

.tbl {
  width: 100%;
  border-collapse: collapse;
}

.tbl th {
  padding: 0.6rem 0.4rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.tbl th small {
  display: block;
  font-weight: 400;
}

.tbl td {
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid var(--color-border);
}

.tbl tbody tr:last-child td {
  border-bottom: 0;
}

.tbl tr.on td {
  background: var(--color-brand-soft);
}

.tbl tr.archived {
  opacity: 0.55;
}

.c-name {
  min-width: 13rem;
  padding-left: 1rem !important;
  text-align: left;
}

.c-day {
  width: 3.2rem;
  text-align: center;
}

.c-day.today {
  background: color-mix(in srgb, var(--color-brand) 6%, transparent);
  color: var(--color-brand);
}

.name {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.name:hover b {
  color: var(--color-brand);
}

.name__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 1.1rem;
}

.name__icon svg {
  width: 1.1rem;
  height: 1.1rem;
  color: var(--color-text-secondary);
}

.name__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name__text b {
  font-size: 0.92rem;
  font-weight: 600;
}

.name__text small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.c-streak {
  width: 7rem;
  font-size: 0.84rem;
  white-space: nowrap;
}

.streak {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  font-weight: 700;
}

.streak svg {
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-warn, #d97706);
}

.muted {
  color: var(--color-text-secondary);
}

.plus {
  display: inline-grid;
  place-items: center;
  width: 1.4rem;
  height: 1.4rem;
  margin-left: 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: none;
  color: var(--color-accent-text);
  cursor: pointer;
  vertical-align: middle;
}

.plus:hover {
  border-color: var(--color-accent);
}

.c-rate {
  width: 8rem;
  padding-right: 1rem !important;
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
}

.rate {
  display: inline-block;
  width: 3.5rem;
  height: 0.35rem;
  margin-right: 0.35rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
  vertical-align: middle;
}

.rate i {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.archived-toggle {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.84rem;
  cursor: pointer;
}

.tip {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.hb__detail {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  max-height: calc(100vh - var(--header-height) - 2.5rem);
  overflow-y: auto;
}

@media (max-width: 1180px) {
  .hb--detail {
    grid-template-columns: 1fr;
  }

  .hb__detail {
    position: static;
    max-height: none;
  }
}
</style>
