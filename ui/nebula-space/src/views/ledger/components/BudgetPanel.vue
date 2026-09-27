<script setup lang="ts">
/**
 * 分类预算：每个分类一条进度条，竖线是按天数折算的「应花到这里」——超过竖线说明花得比计划快（黄），超过预算（红）。
 * 右侧近 6 个月各分类趋势，底部一句观察（只陈述事实，不说教）。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { formatMoney } from '../../../utils/ledgerParser'
import type { Budget, LedgerCategory, LedgerEntry } from '../../../types/ledger'

const props = defineProps<{
  month: string
  /** 近 6 个月的流水（含本月） */
  entries: LedgerEntry[]
  categories: LedgerCategory[]
  budget: Budget | null
  /** 本月过了几成（本月按今天，过去的月份为 1） */
  pace: number
  daysLeft: number
}>()
const emit = defineEmits<{ save: [budget: Omit<Budget, 'month'>] }>()

const outCats = computed(() => props.categories.filter((c) => c.kind === 'out'))
const spentOf = (catId: string, month = props.month) =>
  props.entries.filter((e) => e.direction === 'out' && String(e.categoryId) === catId && e.date.startsWith(month)).reduce((s, e) => s + e.amount, 0)

const rows = computed(() =>
  outCats.value
    .map((c) => {
      const id = String(c.id)
      const limit = props.budget?.items[id] ?? 0
      const spent = spentOf(id)
      const ratio = limit ? spent / limit : spent ? Infinity : 0
      const recurringPaid = props.entries.some((e) => e.recurringId && String(e.categoryId) === id && e.date.startsWith(props.month))
      let state: 'ok' | 'warn' | 'over' = 'ok'
      let note = limit ? `正常 · 剩 ¥ ${formatMoney(limit - spent)}` : '没设预算'
      if (limit && spent > limit) {
        state = 'over'
        note = `超出 ¥ ${formatMoney(spent - limit)}`
      } else if (limit && ratio > props.pace + 0.02 && !(recurringPaid && spent === limit)) {
        state = 'warn'
        note = `比进度快 · 剩 ¥ ${formatMoney(limit - spent)}`
      } else if (recurringPaid && limit && spent >= limit) {
        note = '已按月支付'
      }
      return { cat: c, id, limit, spent, pct: limit ? Math.min(100, ratio * 100) : 0, state, note }
    })
    .filter((r) => r.limit || r.spent),
)

const totalLimit = computed(() => props.budget?.total ?? rows.value.reduce((s, r) => s + r.limit, 0))
const totalSpent = computed(() => rows.value.reduce((s, r) => s + r.spent, 0))

// ── 调整预算 ──

const editing = ref(false)
const draft = ref<Record<string, string>>({})
const draftTotal = ref('')
const startEdit = () => {
  draft.value = Object.fromEntries(outCats.value.map((c) => [String(c.id), props.budget?.items[String(c.id)] ? String(props.budget.items[String(c.id)]! / 100) : '']))
  draftTotal.value = props.budget?.total ? String(props.budget.total / 100) : ''
  editing.value = true
}
watch(() => props.month, () => (editing.value = false))
const draftSum = computed(() => Object.values(draft.value).reduce((s, v) => s + (Number(v) || 0), 0))
const save = () => {
  const items: Record<string, number> = {}
  for (const [id, v] of Object.entries(draft.value)) if (Number(v) > 0) items[id] = Math.round(Number(v) * 100)
  emit('save', { total: Number(draftTotal.value) > 0 ? Math.round(Number(draftTotal.value) * 100) : null, items })
  editing.value = false
}

// ── 近 6 个月 ──

const months = computed(() => {
  const [y, m] = props.month.split('-').map(Number) as [number, number]
  return Array.from({ length: 6 }, (_, i) => {
    const d = new Date(y, m - 1 - (5 - i), 1)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
})

/** 半年里花得最多的三个分类 */
const topCats = computed(() =>
  outCats.value
    .map((c) => ({ c, sum: months.value.reduce((s, m) => s + spentOf(String(c.id), m), 0) }))
    .filter((x) => x.sum)
    .sort((a, b) => b.sum - a.sum)
    .slice(0, 3)
    .map((x) => x.c),
)

const bars = computed(() => {
  const data = months.value.map((m) => topCats.value.map((c) => ({ c, v: spentOf(String(c.id), m) })))
  const max = Math.max(1, ...data.map((col) => col.reduce((s, x) => s + x.v, 0)))
  return months.value.map((m, i) => ({ month: m, parts: data[i]!.map((x) => ({ ...x, h: (x.v / max) * 100 })) }))
})

/** 一句观察：连续上涨的分类；没有就说本月变化最大的 */
const tip = computed(() => {
  const done = months.value.slice(0, props.pace < 1 ? 5 : 6)
  const label = (m: string) => `${Number(m.slice(5))} 月`
  for (const c of outCats.value) {
    const series = done.map((m) => spentOf(String(c.id), m))
    let run = 1
    for (let i = series.length - 1; i > 0 && series[i]! > series[i - 1]!; i -= 1) run += 1
    if (run >= 4 && series.at(-1)! > 0) {
      const from = done[done.length - run]!
      return `${c.name}已连续 ${run - 1} 个月上涨，${label(done.at(-1)!)}比 ${label(from)}多 ¥ ${formatMoney(series.at(-1)! - series[series.length - run]!)}。`
    }
  }
  const last = done.at(-1)!
  const prev3 = done.slice(-4, -1)
  if (!prev3.length) return ''
  const diffs = outCats.value
    .map((c) => {
      const avg = prev3.reduce((s, m) => s + spentOf(String(c.id), m), 0) / prev3.length
      return { c, diff: spentOf(String(c.id), last) - avg }
    })
    .sort((a, b) => Math.abs(b.diff) - Math.abs(a.diff))
  const top = diffs[0]
  if (!top || Math.abs(top.diff) < 10000) return `${label(last)}各分类和前三个月差不多。`
  return `${label(last)}${top.c.name}比前三个月平均${top.diff > 0 ? '多' : '少'} ¥ ${formatMoney(Math.abs(top.diff))}。`
})
</script>

<template>
  <div class="budget">
    <section class="panel surface">
      <header class="panel__head">
        <h2>{{ Number(month.slice(5)) }} 月预算</h2>
        <span class="muted">¥ {{ formatMoney(totalSpent) }} / {{ formatMoney(totalLimit) }}<template v-if="daysLeft"> · 还剩 {{ daysLeft }} 天</template></span>
        <button v-if="!editing" class="btn btn--quiet btn--sm" type="button" @click="startEdit"><Icon icon="lucide:pencil" />调整预算</button>
      </header>

      <div v-if="editing" class="edit">
        <label v-for="c in outCats" :key="c.id" class="edit__row">
          <span>{{ c.icon }} {{ c.name }}</span>
          <input v-model="draft[String(c.id)]" type="number" min="0" step="50" placeholder="不设" :aria-label="`${c.name}预算（元）`" />
        </label>
        <label class="edit__row edit__row--total">
          <span>总预算</span>
          <input v-model="draftTotal" type="number" min="0" step="100" :placeholder="`各分类之和 ${draftSum}`" aria-label="总预算（元）" />
        </label>
        <p class="edit__hint">从这个月开始生效，之后的月份沿用，直到再次调整。</p>
        <div class="edit__acts">
          <button class="btn btn--ghost btn--sm" type="button" @click="editing = false">取消</button>
          <button class="btn btn--primary btn--sm" type="button" @click="save">保存</button>
        </div>
      </div>

      <div v-else class="rows">
        <div v-for="r in rows" :key="r.id" class="brow" :class="`brow--${r.state}`">
          <span class="brow__name">{{ r.cat.icon }} {{ r.cat.name }}</span>
          <span class="brow__bar">
            <i :style="{ width: `${r.pct}%` }" />
            <span v-if="r.limit" class="pace" :style="{ left: `${pace * 100}%` }" :title="`按天数应花到 ${Math.round(pace * 100)}%`" />
          </span>
          <span class="brow__num">¥ {{ formatMoney(r.spent) }}<template v-if="r.limit"> / {{ formatMoney(r.limit) }}</template><small>{{ r.note }}</small></span>
        </div>
        <p class="legend"><span class="pace pace--legend" />竖线：按过去的天数，这个月应该花到的位置</p>
      </div>
    </section>

    <section class="panel surface">
      <header class="panel__head"><h2>近 6 个月</h2></header>
      <div class="trend" role="img" :aria-label="`近 6 个月${topCats.map((c) => c.name).join('、')}支出`">
        <div v-for="b in bars" :key="b.month" class="trend__col">
          <div class="trend__stack">
            <i v-for="p in b.parts" :key="p.c.id" :style="{ height: `${p.h}%`, background: p.c.color }" :title="`${p.c.name} ¥ ${formatMoney(p.v)}`" />
          </div>
          <span>{{ Number(b.month.slice(5)) }} 月</span>
        </div>
      </div>
      <p class="keys"><span v-for="c in topCats" :key="c.id" :style="{ color: c.color }">● {{ c.name }}</span></p>
      <p v-if="tip" class="tip"><Icon icon="lucide:lightbulb" />{{ tip }}</p>
    </section>
  </div>
</template>

<style scoped>
.budget {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.panel {
  border-radius: var(--radius-xl);
}

.panel__head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.panel__head h2 {
  font-size: 0.95rem;
  font-weight: 800;
}

.muted {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.panel__head .btn {
  margin-left: auto;
}

.btn--sm {
  padding: 0.28rem 0.6rem;
  font-size: 0.8rem;
}

.rows {
  padding: 0.4rem 1rem 0.9rem;
}

.brow {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr) 9.5rem;
  align-items: center;
  gap: 0.8rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.brow__bar {
  position: relative;
  height: 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.brow__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent);
}

.brow--warn .brow__bar i {
  background: #f59e0b;
}

.brow--over .brow__bar i {
  background: var(--color-danger);
}

.pace {
  position: absolute;
  top: -0.25rem;
  bottom: -0.25rem;
  width: 2px;
  background: var(--color-text-primary);
  opacity: 0.55;
}

.pace--legend {
  position: static;
  display: inline-block;
  width: 2px;
  height: 0.8rem;
  margin-right: 0.4rem;
  vertical-align: -0.1rem;
}

.brow__num {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.brow__num small {
  display: block;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.brow--warn small {
  color: #b45309;
}

.brow--over small {
  color: var(--color-danger);
}

.legend {
  margin-top: 0.6rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.edit {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.8rem 1rem 1rem;
}

.edit__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.86rem;
}

.edit__row input {
  width: 8rem;
  padding: 0.3rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  text-align: right;
}

.edit__row--total {
  margin-top: 0.3rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
  font-weight: 700;
}

.edit__hint {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.edit__acts {
  display: flex;
  justify-content: flex-end;
  gap: 0.4rem;
}

.trend {
  display: flex;
  gap: 0.6rem;
  height: 11rem;
  padding: 1rem 1rem 0;
}

.trend__col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.trend__stack {
  display: flex;
  flex: 1;
  flex-direction: column-reverse;
  width: 100%;
  max-width: 2.4rem;
}

.trend__stack i {
  display: block;
}

.trend__stack i:last-child {
  border-radius: 0.3rem 0.3rem 0 0;
}

.keys {
  display: flex;
  gap: 0.9rem;
  padding: 0.6rem 1rem 0;
  font-size: 0.76rem;
}

.tip {
  display: flex;
  gap: 0.45rem;
  margin: 0.8rem 1rem 1rem;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.82rem;
  line-height: 1.6;
}

.tip svg {
  flex: none;
  margin-top: 0.2rem;
  color: #d97706;
}

@media (max-width: 960px) {
  .budget {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .brow {
    grid-template-columns: 4.5rem minmax(0, 1fr);
  }

  .brow__num {
    grid-column: 1 / -1;
    text-align: left;
  }
}
</style>
