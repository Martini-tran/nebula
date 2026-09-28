<script setup lang="ts">
/**
 * 专注统计：只跟自己比——本周每天专注时长、按任务分布、平均每轮被打断次数、比上周多了多少。
 * 不做排行榜。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { fetchFocusSessions } from '../../api/focus'
import { errorText } from '../../composables/useToast'
import { addDays, fromYmd, hmOf, relativeDay, startOfWeek, todayYmd, weekdayLabel } from '../../utils/date'
import type { FocusSession } from '../../types/focus'

const route = useRoute()
const router = useRouter()
const today = todayYmd()

const offset = computed(() => Number(route.query.week ?? 0) || 0)
const monday = computed(() => addDays(startOfWeek(today), offset.value * 7))
const sessions = ref<FocusSession[]>([])
const lastWeek = ref<FocusSession[]>([])
const loading = ref(true)
const error = ref('')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [cur, prev] = await Promise.all([
      fetchFocusSessions({ from: monday.value, to: addDays(monday.value, 6) }),
      fetchFocusSessions({ from: addDays(monday.value, -7), to: addDays(monday.value, -1) }),
    ])
    sessions.value = cur
    lastWeek.value = prev
  } catch (err) {
    error.value = errorText(err, '加载失败')
  } finally {
    loading.value = false
  }
}

const shift = async (delta: number) => {
  const next = offset.value + delta
  await router.replace({ query: next ? { week: String(next) } : {} })
  load()
}

const done = computed(() => sessions.value.filter((s) => s.status === 'done'))
const minutes = (list: FocusSession[]) => list.filter((s) => s.status === 'done').reduce((sum, s) => sum + s.actualMin, 0)
const total = computed(() => minutes(sessions.value))
const diff = computed(() => total.value - minutes(lastWeek.value))
const avgInterrupt = computed(() => (done.value.length ? done.value.reduce((sum, s) => sum + s.interruptions, 0) / done.value.length : 0))

const dur = (min: number) => {
  const h = Math.floor(Math.abs(min) / 60)
  const m = Math.abs(min) % 60
  return h ? `${h}h${m ? ` ${m}m` : ''}` : `${m}m`
}

const days = computed(() => {
  const list = Array.from({ length: 7 }, (_, i) => {
    const date = addDays(monday.value, i)
    return { date, label: weekdayLabel(date).slice(1), min: minutes(sessions.value.filter((s) => s.startedAt.slice(0, 10) === date)) }
  })
  return list
})
const maxDay = computed(() => Math.max(60, ...days.value.map((d) => d.min)))
const axis = computed(() => {
  const top = Math.ceil(maxDay.value / 60)
  return Array.from({ length: top + 1 }, (_, i) => top - i)
})

const byTask = computed(() => {
  const map = new Map<string, number>()
  for (const s of done.value) map.set(s.taskTitle, (map.get(s.taskTitle) ?? 0) + s.actualMin)
  const sorted = [...map.entries()].sort((a, b) => b[1] - a[1])
  const top = sorted.slice(0, 4).map(([title, min]) => ({ title, min }))
  const rest = sorted.slice(4)
  if (rest.length) top.push({ title: `其余 ${rest.length} 项任务`, min: rest.reduce((sum, [, m]) => sum + m, 0) })
  return top
})

const recent = computed(() => [...sessions.value].reverse().slice(0, 12))
const rangeText = computed(() => {
  const a = fromYmd(monday.value)
  const b = fromYmd(addDays(monday.value, 6))
  return `${a.getMonth() + 1}/${a.getDate()} – ${b.getMonth() + 1}/${b.getDate()}`
})

onMounted(load)
</script>

<template>
  <div class="fs page">
    <header class="fs__head">
      <div>
        <router-link to="/tasks" class="back"><Icon icon="lucide:arrow-left" />任务</router-link>
        <h1 class="page-title">专注</h1>
        <p class="page-subtitle">
          <button type="button" class="wk" aria-label="上一周" @click="shift(-1)"><Icon icon="lucide:chevron-left" /></button>
          {{ offset === 0 ? '本周' : offset === -1 ? '上周' : '' }} {{ rangeText }}
          <button type="button" class="wk" aria-label="下一周" :disabled="offset >= 0" @click="shift(1)"><Icon icon="lucide:chevron-right" /></button>
          · 在任务上点「专注」开始
        </p>
      </div>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="error" state="error" :description="error" action-label="重试" @action="load" />
    <template v-else>
      <div class="kpis">
        <div class="surface"><b>{{ dur(total) }}</b><span>本周专注</span></div>
        <div class="surface"><b>{{ done.length }}</b><span>完成轮数</span></div>
        <div class="surface"><b>{{ avgInterrupt.toFixed(1) }}</b><span>平均每轮被打断</span></div>
        <div class="surface"><b :class="{ up: diff > 0, down: diff < 0 }">{{ diff >= 0 ? '+' : '−' }}{{ dur(diff) }}</b><span>比上周</span></div>
      </div>

      <div class="cols">
        <section class="card surface">
          <h2>每天专注</h2>
          <div class="chart" role="img" :aria-label="`本周每天专注时长：${days.map((d) => `周${d.label} ${dur(d.min)}`).join('，')}`">
            <div class="chart__axis">
              <span v-for="h in axis" :key="h">{{ h }}h</span>
            </div>
            <div class="chart__bars">
              <div v-for="d in days" :key="d.date" class="bar" :class="{ today: d.date === today }">
                <span class="bar__val">{{ d.min ? dur(d.min) : '' }}</span>
                <i :style="{ height: `${(d.min / (axis[0]! * 60)) * 100}%` }" />
                <span class="bar__label">{{ d.label }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="card surface">
          <h2>按任务</h2>
          <p v-if="!byTask.length" class="muted">这周还没有专注记录。</p>
          <ul class="tasks">
            <li v-for="t in byTask" :key="t.title">
              <span class="tasks__title">{{ t.title }}</span>
              <span class="tasks__bar"><i :style="{ width: `${(t.min / (byTask[0]?.min || 1)) * 100}%` }" /></span>
              <b>{{ dur(t.min) }}</b>
            </li>
          </ul>
        </section>
      </div>

      <section class="card surface">
        <h2>最近几轮</h2>
        <p v-if="!recent.length" class="muted">还没有记录。</p>
        <ul class="log">
          <li v-for="s in recent" :key="s.id">
            <span class="log__when">{{ relativeDay(s.startedAt.slice(0, 10)) }} {{ hmOf(s.startedAt) }}–{{ hmOf(s.endedAt) }}</span>
            <span class="log__title">{{ s.taskTitle }}</span>
            <span class="log__meta">
              {{ s.actualMin }} 分钟<template v-if="s.interruptions"> · 打断 {{ s.interruptions }} 次</template>
              <em v-if="s.status === 'abandoned'" class="tag">放弃</em>
            </span>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.fs {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  max-width: 64rem;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: 0.3rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.fs__head .page-subtitle {
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

.wk:disabled {
  opacity: 0.35;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

.kpis div {
  display: flex;
  flex-direction: column;
  padding: 0.9rem 1rem;
  border-radius: var(--radius-lg);
}

.kpis b {
  font-size: 1.5rem;
  font-weight: 800;
}

.kpis b.up {
  color: var(--color-accent-text);
}

.kpis b.down {
  color: var(--color-danger);
}

.kpis span {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.cols {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 1rem;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
}

.card h2 {
  font-size: 0.92rem;
  font-weight: 800;
}

.muted {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.chart {
  display: flex;
  gap: 0.5rem;
  height: 12rem;
}

.chart__axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-bottom: 1.3rem;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-align: right;
}

.chart__bars {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.5rem;
  border-bottom: 1px solid var(--color-border);
}

.bar {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  padding-bottom: 0;
}

.bar i {
  width: 70%;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  background: color-mix(in srgb, var(--color-brand) 55%, var(--color-bg-soft));
}

.bar.today i {
  background: var(--color-brand);
}

.bar__val {
  font-size: 0.68rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.bar__label {
  position: absolute;
  bottom: -1.3rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.bar.today .bar__label {
  color: var(--color-brand);
  font-weight: 700;
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.tasks li {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) 1fr auto;
  gap: 0.6rem;
  align-items: center;
  font-size: 0.86rem;
}

.tasks__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tasks__bar {
  height: 0.45rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.tasks__bar i {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.tasks b {
  font-size: 0.82rem;
  font-variant-numeric: tabular-nums;
}

.log {
  margin: 0;
  padding: 0;
  list-style: none;
}

.log li {
  display: grid;
  grid-template-columns: 9.5rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.log__when,
.log__meta {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.log__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log em {
  margin-left: 0.3rem;
  font-style: normal;
}

@media (max-width: 760px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }

  .cols {
    grid-template-columns: 1fr;
  }

  .log li {
    grid-template-columns: 1fr;
    gap: 0.1rem;
  }
}
</style>
