<script setup lang="ts">
/**
 * 记账：只记个人日常收支。记一笔不超过 5 秒——一行文字「午饭 32 餐饮」回车即可，月底能回答「钱花哪了、超没超预算」。
 * 流水：顶部三个数（支出与预算进度、收入、结余），输入框识别金额 / 分类 / 日期，流水按天分组，右栏分类占比环图与周期账单。
 * 预算：见 BudgetPanel。?month=YYYY-MM &tab=budget
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import BaseDialog from '../../components/base/BaseDialog.vue'
import EntryDialog from './components/EntryDialog.vue'
import BudgetPanel from './components/BudgetPanel.vue'
import {
  createEntry,
  createRecurring,
  deleteEntry,
  deleteRecurring,
  fetchBudget,
  fetchCategories,
  fetchEntries,
  fetchRecurring,
  saveBudget,
  updateEntry,
  updateRecurring,
} from '../../api/ledger'
import { errorText, toast } from '../../composables/useToast'
import { monthDay, relativeDay, todayYmd, weekdayLabel } from '../../utils/date'
import { formatMoney, parseLedgerInput } from '../../utils/ledgerParser'
import { LEDGER_ICON_DEFAULT, iconOr } from '../../config/icons'
import type { Budget, Direction, LedgerCategory, LedgerEntry, LedgerSaveRequest, Recurring } from '../../types/ledger'

const route = useRoute()
const router = useRouter()
const today = todayYmd()
const thisMonth = today.slice(0, 7)

const month = computed(() => (typeof route.query.month === 'string' && /^\d{4}-\d{2}$/.test(route.query.month) ? route.query.month : thisMonth))
const tab = computed(() => (route.query.tab === 'budget' ? 'budget' : 'flow'))
const shiftMonth = (delta: number) => {
  const [y, m] = month.value.split('-').map(Number) as [number, number]
  const d = new Date(y, m - 1 + delta, 1)
  const next = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  router.replace({ query: { ...route.query, month: next === thisMonth ? undefined : next } })
}
const setTab = (t: 'flow' | 'budget') => router.replace({ query: { ...route.query, tab: t === 'budget' ? 'budget' : undefined } })

const monthStart = computed(() => `${month.value}-01`)
const monthEnd = computed(() => {
  const [y, m] = month.value.split('-').map(Number) as [number, number]
  return `${month.value}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`
})
const daysInMonth = computed(() => Number(monthEnd.value.slice(8)))
const isCurrent = computed(() => month.value === thisMonth)
const pace = computed(() => (month.value < thisMonth ? 1 : isCurrent.value ? Number(today.slice(8)) / daysInMonth.value : 0))
const daysLeft = computed(() => (isCurrent.value ? daysInMonth.value - Number(today.slice(8)) : 0))

// ── 数据：取近 6 个月（预算页的趋势要用），流水只看本月 ──

const categories = ref<LedgerCategory[]>([])
const entries = ref<LedgerEntry[]>([])
const recurring = ref<Recurring[]>([])
const budget = ref<Budget | null>(null)
const loading = ref(true)
const loadError = ref('')

const sixMonthsAgo = computed(() => {
  const [y, m] = month.value.split('-').map(Number) as [number, number]
  const d = new Date(y, m - 6, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
})

const load = async () => {
  loadError.value = ''
  try {
    const [cats, list, rec, b] = await Promise.all([
      fetchCategories(),
      fetchEntries({ from: sixMonthsAgo.value, to: monthEnd.value }),
      fetchRecurring(),
      fetchBudget(month.value),
    ])
    categories.value = cats
    entries.value = list
    recurring.value = rec
    budget.value = b
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}
watch(month, load)

const catOf = (id: LedgerEntry['categoryId']) => categories.value.find((c) => String(c.id) === String(id))
const monthEntries = computed(() => entries.value.filter((e) => e.date >= monthStart.value && e.date <= monthEnd.value))
const sumOf = (list: LedgerEntry[], dir: Direction) => list.filter((e) => e.direction === dir).reduce((s, e) => s + e.amount, 0)

// ── 三个数 ──

const prevMonthEntries = computed(() => {
  const [y, m] = month.value.split('-').map(Number) as [number, number]
  const d = new Date(y, m - 2, 1)
  const prefix = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  return { label: `${d.getMonth() + 1} 月`, list: entries.value.filter((e) => e.date.startsWith(prefix)) }
})
const spent = computed(() => sumOf(monthEntries.value, 'out'))
const income = computed(() => sumOf(monthEntries.value, 'in'))
const balance = computed(() => income.value - spent.value)
const budgetTotal = computed(() => budget.value?.total ?? Object.values(budget.value?.items ?? {}).reduce((s, v) => s + v, 0))
const budgetPct = computed(() => (budgetTotal.value ? Math.round((spent.value / budgetTotal.value) * 100) : 0))
const prevBalance = computed(() => sumOf(prevMonthEntries.value.list, 'in') - sumOf(prevMonthEntries.value.list, 'out'))
const incomeParts = computed(() => {
  const map = new Map<string, number>()
  monthEntries.value.filter((e) => e.direction === 'in').forEach((e) => map.set(String(e.categoryId), (map.get(String(e.categoryId)) ?? 0) + e.amount))
  return [...map.entries()].sort((a, b) => b[1] - a[1]).map(([id, v]) => `${catOf(id)?.name ?? '其他'} ¥ ${formatMoney(v)}`)
})

// ── 记一笔 ──

const draft = ref('')
const saving = ref(false)
const parsed = computed(() => (draft.value.trim() ? parseLedgerInput(draft.value, categories.value, entries.value, today) : null))
const parsedCat = computed(() => (parsed.value ? catOf(parsed.value.categoryId ?? '') : undefined))
const hint = computed(() => {
  const p = parsed.value
  if (!p) return ''
  if (!p.amount) return '还缺金额，例如「午饭 32」「工资 +18000」'
  const by = { explicit: '', keyword: ' · 自动识别', habit: ' · 按上次的习惯', default: '' }[p.categoryBy]
  return `识别为：${monthDay(p.date)} · ${p.direction === 'in' ? '收入' : '支出'} ¥${formatMoney(p.amount, p.amount % 100 !== 0)} · ${parsedCat.value?.name ?? '未分类'}${by}${p.note ? ` · 备注「${p.note}」` : ''}`
})

const add = async () => {
  const p = parsed.value
  if (!p || !p.amount || saving.value || !p.categoryId) return
  saving.value = true
  const catName = parsedCat.value?.name ?? '未分类'
  try {
    const entry = await createEntry({ amount: p.amount, direction: p.direction, categoryId: p.categoryId, date: p.date, note: p.note })
    draft.value = ''
    const monthOf = entry.date.slice(0, 7)
    toast.ok(`${p.direction === 'in' ? '收入' : '支出'} ¥${formatMoney(p.amount, p.amount % 100 !== 0)} · ${catName}${monthOf !== month.value ? `（记在 ${Number(monthOf.slice(5))} 月）` : ''}`, {
      action: {
        label: '撤销',
        run: async () => {
          await deleteEntry(entry.id)
          load()
        },
      },
    })
    load()
  } catch (error) {
    toast.error(errorText(error, '没记上'))
  } finally {
    saving.value = false
  }
}

// ── 按天分组 ──

const days = computed(() => {
  const map = new Map<string, LedgerEntry[]>()
  monthEntries.value.forEach((e) => map.set(e.date, [...(map.get(e.date) ?? []), e]))
  return [...map.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([date, list]) => ({ date, list, out: sumOf(list, 'out'), in: sumOf(list, 'in') }))
})
const SHOW_DAYS = 10
const showAll = ref(false)
watch(month, () => (showAll.value = false))

const dayTitle = (date: string) => {
  const rel = relativeDay(date)
  return rel === '今天' || rel === '昨天' || rel === '前天' ? `${rel} · ${monthDay(date)} ${weekdayLabel(date)}` : `${monthDay(date)} ${weekdayLabel(date)}`
}
const recurringOf = (e: LedgerEntry) => recurring.value.find((r) => String(r.id) === String(e.recurringId))

// ── 编辑 ──

const editing = ref<LedgerEntry | null>(null)
const saveEdit = async (body: Partial<LedgerSaveRequest>) => {
  if (!editing.value) return
  try {
    await updateEntry(editing.value.id, body)
    editing.value = null
    load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}
const removeEdit = async () => {
  const e = editing.value
  if (!e) return
  editing.value = null
  try {
    await deleteEntry(e.id)
    toast.ok('已删除这笔', {
      action: {
        label: '撤销',
        run: async () => {
          await createEntry({ amount: e.amount, direction: e.direction, categoryId: e.categoryId, date: e.date, note: e.note, recurringId: e.recurringId })
          load()
        },
      },
    })
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

// ── 环图 ──

const slices = computed(() => {
  const map = new Map<string, number>()
  monthEntries.value.filter((e) => e.direction === 'out').forEach((e) => map.set(String(e.categoryId), (map.get(String(e.categoryId)) ?? 0) + e.amount))
  const total = spent.value || 1
  let offset = 0
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([id, v]) => {
      const cat = catOf(id)
      const frac = v / total
      const slice = { id, name: cat?.name ?? '其他', icon: cat?.icon ?? '', color: cat?.color ?? '#9ca3af', value: v, pct: Math.round(frac * 100), frac, offset }
      offset += frac
      return slice
    })
})
const R = 52
const C = 2 * Math.PI * R

// ── 周期账单 ──

const recOpen = ref(false)
const recDraft = ref({ note: '', amount: '', categoryId: '', day: 1, direction: 'out' as Direction })
const openRec = () => {
  recDraft.value = { note: '', amount: '', categoryId: String(categories.value.find((c) => c.name === '订阅')?.id ?? categories.value[0]?.id ?? ''), day: Number(today.slice(8)) > 28 ? 28 : Number(today.slice(8)), direction: 'out' }
  recOpen.value = true
}
const recCats = computed(() => categories.value.filter((c) => c.kind === recDraft.value.direction))
const saveRec = async () => {
  const d = recDraft.value
  const cents = Math.round(Number(d.amount) * 100)
  if (!d.note.trim() || !(cents > 0) || !d.categoryId) return
  try {
    await createRecurring({ note: d.note.trim(), amount: cents, direction: d.direction, categoryId: d.categoryId, day: d.day, active: true, startMonth: thisMonth })
    recOpen.value = false
    toast.ok(`每月 ${d.day} 日自动记「${d.note.trim()}」${d.day <= Number(today.slice(8)) ? '，本月这笔已补上' : ''}`)
    load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}
const toggleRec = async (r: Recurring) => {
  try {
    await updateRecurring(r.id, { active: !r.active })
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}
const removeRec = async (r: Recurring) => {
  try {
    await deleteRecurring(r.id)
    toast.ok(`不再自动记「${r.note}」，已记下的保留`)
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}
const recMonthly = computed(() => recurring.value.filter((r) => r.active && r.direction === 'out').reduce((s, r) => s + r.amount, 0))

// ── 预算 ──

const onSaveBudget = async (body: Omit<Budget, 'month'>) => {
  try {
    budget.value = await saveBudget(month.value, body)
    toast.ok(`${Number(month.value.slice(5))} 月起的预算已更新`)
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

// ── 导出 ──

const exportCsv = () => {
  const esc = (s: string) => (/[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s)
  const lines = [
    '日期,收支,金额,分类,备注,周期账单',
    ...[...monthEntries.value]
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((e) => [e.date, e.direction === 'in' ? '收入' : '支出', (e.amount / 100).toFixed(2), catOf(e.categoryId)?.name ?? '', esc(e.note), e.recurringId ? '是' : ''].join(',')),
  ]
  // 带 BOM，Excel 打开不乱码
  const blob = new Blob(['﻿' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `记账_${month.value}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

const monthLabel = computed(() => `${month.value.slice(0, 4)} 年 ${Number(month.value.slice(5))} 月`)

onMounted(load)
</script>

<template>
  <div class="ledger page">
    <header class="head">
      <h1 class="page-title">记账</h1>
      <span class="head__month">{{ monthLabel }}</span>
      <span class="head__nav">
        <button class="btn btn--ghost" type="button" aria-label="上个月" @click="shiftMonth(-1)"><Icon icon="lucide:chevron-left" /></button>
        <button class="btn btn--ghost" type="button" aria-label="下个月" :disabled="isCurrent" @click="shiftMonth(1)"><Icon icon="lucide:chevron-right" /></button>
      </span>
      <button v-if="!isCurrent" class="btn btn--quiet" type="button" @click="router.replace({ query: { tab: route.query.tab } })">回到本月</button>
      <div class="seg" role="tablist" aria-label="视图">
        <button type="button" role="tab" :aria-selected="tab === 'flow'" :class="{ on: tab === 'flow' }" @click="setTab('flow')">流水</button>
        <button type="button" role="tab" :aria-selected="tab === 'budget'" :class="{ on: tab === 'budget' }" @click="setTab('budget')">预算</button>
      </div>
      <button class="btn btn--ghost head__csv" type="button" :disabled="!monthEntries.length" @click="exportCsv"><Icon icon="lucide:download" />导出 CSV</button>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <template v-else>
      <div class="sum3">
        <div class="sm sm--out">
          <span>{{ isCurrent ? '本月' : `${Number(month.slice(5))} 月` }}支出</span>
          <b>¥ {{ formatMoney(spent) }}</b>
          <template v-if="budgetTotal">
            <div class="bud"><i :class="{ warn: budgetPct > pace * 100 + 2, over: budgetPct > 100 }" :style="{ width: `${Math.min(100, budgetPct)}%` }" /><span class="pace" :style="{ left: `${pace * 100}%` }" /></div>
            <small>预算 ¥ {{ formatMoney(budgetTotal) }} · 已用 {{ budgetPct }}%<template v-if="daysLeft"> · 还剩 {{ daysLeft }} 天</template></small>
          </template>
          <small v-else>没设预算 · <button type="button" class="link" @click="setTab('budget')">去设置</button></small>
        </div>
        <div class="sm sm--in">
          <span>{{ isCurrent ? '本月' : `${Number(month.slice(5))} 月` }}收入</span>
          <b>¥ {{ formatMoney(income) }}</b>
          <small>{{ incomeParts.slice(0, 2).join(' · ') || '还没有收入' }}</small>
        </div>
        <div class="sm">
          <span>结余</span>
          <b :class="{ neg: balance < 0 }">{{ balance < 0 ? '-' : '' }}¥ {{ formatMoney(balance) }}</b>
          <small v-if="prevMonthEntries.list.length">比 {{ prevMonthEntries.label }}{{ balance >= prevBalance ? '多' : '少' }} ¥ {{ formatMoney(balance - prevBalance) }}</small>
        </div>
      </div>

      <BudgetPanel
        v-if="tab === 'budget'"
        :month="month"
        :entries="entries"
        :categories="categories"
        :budget="budget"
        :pace="pace"
        :days-left="daysLeft"
        @save="onSaveBudget"
      />

      <div v-else class="lg">
        <section class="flow">
          <form class="quick" @submit.prevent="add">
            <Icon icon="lucide:pen-line" />
            <input v-model="draft" type="text" placeholder="记一笔：「午饭 32」「昨天 打车 46」「工资 +18000」" aria-label="记一笔" autocomplete="off" />
            <kbd>Enter</kbd>
          </form>
          <p class="qhint" :class="{ bad: parsed && !parsed.amount }">{{ hint || '金额前加 + 是收入；分类写名字就用，不写会按关键词和你的习惯自动选。' }}</p>

          <StateBlock v-if="!days.length" state="empty" title="这个月还没有记账" description="在上面输入一行就能记一笔。" />

          <div v-for="d in showAll ? days : days.slice(0, SHOW_DAYS)" :key="d.date" class="day surface">
            <div class="day__h">
              <span>{{ dayTitle(d.date) }}</span>
              <span>
                <template v-if="d.out">支出 ¥ {{ formatMoney(d.out) }}</template><template v-if="d.out && d.in"> · </template><template v-if="d.in">收入 ¥ {{ formatMoney(d.in) }}</template>
              </span>
            </div>
            <button v-for="e in d.list" :key="e.id" type="button" class="tx" @click="editing = e">
              <span class="tx__ico" :style="{ background: `color-mix(in srgb, ${catOf(e.categoryId)?.color ?? '#9ca3af'} 16%, var(--color-bg-surface))` }"><Icon :icon="iconOr(catOf(e.categoryId)?.icon, LEDGER_ICON_DEFAULT)" /></span>
              <span class="tx__main">
                {{ e.note || catOf(e.categoryId)?.name }}
                <small>{{ catOf(e.categoryId)?.name }}<template v-if="recurringOf(e)"> · <span class="recur">每月 {{ recurringOf(e)!.day }} 日自动记</span></template></small>
              </span>
              <span class="tx__amt" :class="{ 'tx__amt--in': e.direction === 'in' }">{{ e.direction === 'in' ? '+' : '-' }}{{ formatMoney(e.amount, true) }}</span>
            </button>
          </div>
          <button v-if="days.length > SHOW_DAYS && !showAll" class="btn btn--ghost more" type="button" @click="showAll = true">再看 {{ days.length - SHOW_DAYS }} 天</button>
        </section>

        <aside class="side">
          <section class="panel surface">
            <div v-if="slices.length" class="donut">
              <svg viewBox="0 0 128 128" role="img" :aria-label="`支出分类占比：${slices.map((s) => `${s.name} ${s.pct}%`).join('，')}`">
                <circle cx="64" cy="64" :r="R" fill="none" stroke="var(--color-bg-soft)" stroke-width="16" />
                <circle
                  v-for="s in slices"
                  :key="s.id"
                  cx="64"
                  cy="64"
                  :r="R"
                  fill="none"
                  :stroke="s.color"
                  stroke-width="16"
                  :stroke-dasharray="`${Math.max(0, s.frac * C - 1)} ${C}`"
                  :stroke-dashoffset="-s.offset * C"
                  transform="rotate(-90 64 64)"
                />
              </svg>
              <div class="donut__c"><b>¥ {{ formatMoney(spent) }}</b><span>支出</span></div>
            </div>
            <p v-else class="empty">这个月还没有支出。</p>
            <div class="cats">
              <span v-for="s in slices" :key="s.id"><i :style="{ background: s.color }" />{{ s.name }}<b>¥ {{ formatMoney(s.value) }}</b><small>{{ s.pct }}%</small></span>
            </div>
          </section>

          <section class="panel surface">
            <header class="panel__head">
              <h2>周期账单</h2>
              <small>每月固定 ¥ {{ formatMoney(recMonthly) }}</small>
              <button class="btn btn--quiet btn--sm" type="button" @click="openRec"><Icon icon="lucide:plus" />添加</button>
            </header>
            <p v-if="!recurring.length" class="empty">房租、订阅这类每月固定的，到日子自动记一笔。</p>
            <div v-for="r in recurring" :key="r.id" class="rec" :class="{ off: !r.active }">
              <span class="rec__ico"><Icon :icon="iconOr(catOf(r.categoryId)?.icon, LEDGER_ICON_DEFAULT)" /></span>
              <span class="rec__main">{{ r.note }}<small>每月 {{ r.day }} 日 · {{ r.direction === 'in' ? '+' : '-' }}¥ {{ formatMoney(r.amount) }}</small></span>
              <button type="button" class="rec__btn" :title="r.active ? '暂停' : '恢复'" :aria-label="r.active ? `暂停「${r.note}」` : `恢复「${r.note}」`" @click="toggleRec(r)">
                <Icon :icon="r.active ? 'lucide:pause' : 'lucide:play'" />
              </button>
              <button type="button" class="rec__btn rec__btn--danger" title="删除" :aria-label="`删除「${r.note}」`" @click="removeRec(r)"><Icon icon="lucide:trash-2" /></button>
            </div>
          </section>
        </aside>
      </div>
    </template>

    <EntryDialog :entry="editing" :categories="categories" @close="editing = null" @save="saveEdit" @remove="removeEdit" />

    <BaseDialog :open="recOpen" title="添加周期账单" width="26rem" @close="recOpen = false">
      <form class="form rec-form" @submit.prevent="saveRec">
        <label class="field">
          <span class="field__label">名称</span>
          <input v-model="recDraft.note" class="field__input" type="text" placeholder="例如：房租、视频会员" maxlength="30" />
        </label>
        <div class="rec-form__row">
          <label class="field">
            <span class="field__label">金额（元）</span>
            <input v-model="recDraft.amount" class="field__input" type="number" min="0.01" step="0.01" />
          </label>
          <label class="field">
            <span class="field__label">每月几号</span>
            <select v-model.number="recDraft.day" class="field__input">
              <option v-for="n in 28" :key="n" :value="n">{{ n }} 日</option>
            </select>
          </label>
        </div>
        <div class="rec-form__row">
          <label class="field">
            <span class="field__label">收支</span>
            <select v-model="recDraft.direction" class="field__input" @change="recDraft.categoryId = String(recCats[0]?.id ?? '')">
              <option value="out">支出</option>
              <option value="in">收入</option>
            </select>
          </label>
          <label class="field">
            <span class="field__label">分类</span>
            <select v-model="recDraft.categoryId" class="field__input">
              <option v-for="c in recCats" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
            </select>
          </label>
        </div>
      </form>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="recOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="!recDraft.note.trim() || !(Number(recDraft.amount) > 0)" @click="saveRec">保存</button>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  margin-bottom: 1.1rem;
}

.head__month {
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.head__nav {
  display: inline-flex;
  gap: 0.25rem;
}

.head__nav .btn {
  padding: 0.35rem;
}

.seg {
  display: inline-flex;
  margin-left: auto;
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
  font-size: 0.86rem;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.head__csv {
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.sum3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.1rem;
}

.sm {
  display: flex;
  flex-direction: column;
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.sm > span {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.sm b {
  margin: 0.15rem 0 0.3rem;
  font-size: 1.55rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.sm--in b {
  color: var(--color-accent-text);
}

.sm b.neg {
  color: var(--color-danger);
}

.sm small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.bud {
  position: relative;
  height: 0.4rem;
  margin-bottom: 0.4rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.bud i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent);
}

.bud i.warn {
  background: #f59e0b;
}

.bud i.over {
  background: var(--color-danger);
}

.pace {
  position: absolute;
  top: -0.2rem;
  bottom: -0.2rem;
  width: 2px;
  background: var(--color-text-primary);
  opacity: 0.5;
}

.link {
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.74rem;
  cursor: pointer;
}

.lg {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20rem;
  gap: 1.1rem;
  align-items: start;
}

.flow {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.quick {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 0.9rem;
  border: 1.5px solid var(--color-brand);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: 0 0 0 4px var(--color-brand-soft);
  color: var(--color-brand);
}

.quick input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 1rem;
}

kbd {
  padding: 0.02rem 0.35rem;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono, monospace);
  font-size: 0.68rem;
  color: var(--color-text-secondary);
}

.qhint {
  margin-top: -0.3rem;
  padding-left: 0.3rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.qhint.bad {
  color: #b45309;
}

.day {
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.day__h {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.tx {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  width: 100%;
  padding: 0.55rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: none;
  color: inherit;
  text-align: left;
  font-size: 0.9rem;
  cursor: pointer;
}

.tx:last-child {
  border-bottom: 0;
}

.tx:hover {
  background: var(--color-bg-soft);
}

.tx__ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-md);
  font-size: 1rem;
}

.tx__ico svg,
.rec__ico svg {
  width: 1rem;
  height: 1rem;
}

.tx__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.tx__main small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.recur {
  color: var(--color-brand);
}

.tx__amt {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.tx__amt--in {
  color: var(--color-accent-text);
}

.more {
  align-self: center;
  padding: 0.35rem 1rem;
  font-size: 0.84rem;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
}

.panel {
  border-radius: var(--radius-lg);
}

.panel__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.panel__head h2 {
  font-size: 0.9rem;
  font-weight: 800;
}

.panel__head small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.panel__head .btn {
  margin-left: auto;
}

.btn--sm {
  padding: 0.25rem 0.55rem;
  font-size: 0.78rem;
}

.donut {
  position: relative;
  width: 11rem;
  height: 11rem;
  margin: 1rem auto 0.4rem;
}

.donut svg {
  width: 100%;
  height: 100%;
}

.donut__c {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut__c b {
  font-size: 1.05rem;
  font-variant-numeric: tabular-nums;
}

.donut__c span {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.cats {
  display: flex;
  flex-direction: column;
  padding: 0.4rem 1rem 0.9rem;
}

.cats span {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.3rem 0;
  font-size: 0.84rem;
}

.cats i {
  flex: none;
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 0.15rem;
}

.cats b {
  margin-left: auto;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.cats small {
  width: 2.4rem;
  font-size: 0.74rem;
  text-align: right;
  color: var(--color-text-secondary);
}

.empty {
  padding: 0.8rem 1rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.rec {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.rec:last-child {
  border-bottom: 0;
}

.rec.off {
  opacity: 0.5;
}

.rec__ico {
  display: grid;
  flex: none;
  place-items: center;
  color: var(--color-text-secondary);
}

.rec__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.rec__main small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.rec__btn {
  display: grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.rec__btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.rec__btn--danger:hover {
  color: var(--color-danger);
}

.rec-form {
  padding: 0;
}

.rec-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.8rem;
}

@media (max-width: 1000px) {
  .lg {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
  }
}

@media (max-width: 640px) {
  .sum3 {
    grid-template-columns: 1fr;
  }

  .seg {
    margin-left: 0;
  }
}
</style>
