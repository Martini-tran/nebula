/**
 * 记账接口。后端还没有（设计见 space-ledger.html「后端待补」），路径按设计拟定为 /space/me/ledger/**；
 * 未接通时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 规则（后端实现时照搬）：
 * - 金额以分存整数
 * - 周期账单由定时任务在每月那天生成一笔（带 recurringId）；这里在取流水时补齐到今天
 * - 某月没设预算时沿用最近一个月的
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Budget, LedgerCategory, LedgerEntry, LedgerSaveRequest, Recurring } from '../types/ledger'

const BASE = '/space/me/ledger'

const real = {
  fetchCategories: () => get<LedgerCategory[]>(`${BASE}/categories`),
  updateCategory: (id: EntityId, body: Partial<Omit<LedgerCategory, 'id'>>) => put<LedgerCategory>(`${BASE}/categories/${id}`, body),
  /** from / to 含两端（YYYY-MM-DD） */
  fetchEntries: (query: { from?: string; to?: string } = {}) => get<LedgerEntry[]>(`${BASE}/entries`, { params: query }),
  createEntry: (body: LedgerSaveRequest) => post<LedgerEntry>(`${BASE}/entries`, body),
  updateEntry: (id: EntityId, body: Partial<LedgerSaveRequest>) => put<LedgerEntry>(`${BASE}/entries/${id}`, body),
  deleteEntry: (id: EntityId) => del<void>(`${BASE}/entries/${id}`),
  fetchRecurring: () => get<Recurring[]>(`${BASE}/recurring`),
  createRecurring: (body: Omit<Recurring, 'id'>) => post<Recurring>(`${BASE}/recurring`, body),
  updateRecurring: (id: EntityId, body: Partial<Omit<Recurring, 'id'>>) => put<Recurring>(`${BASE}/recurring/${id}`, body),
  deleteRecurring: (id: EntityId) => del<void>(`${BASE}/recurring/${id}`),
  fetchBudget: (month: string) => get<Budget>(`${BASE}/budgets/${month}`),
  saveBudget: (month: string, body: Omit<Budget, 'month'>) => put<Budget>(`${BASE}/budgets/${month}`, body),
}

// ── mock ──

type CatRow = LedgerCategory & { id: string }
type EntryRow = LedgerEntry & { id: string }
type RecRow = Recurring & { id: string }
type BudgetRow = Budget & { id: string }

const CATEGORIES: CatRow[] = [
  { id: 'c1', name: '餐饮', icon: '🍜', color: '#0d9488', kind: 'out', keywords: ['午饭', '晚饭', '早餐', '早饭', '外卖', '咖啡', '瑞幸', '星巴克', '奶茶', '聚餐', '食堂', '面包'], sortOrder: 1 },
  { id: 'c2', name: '交通', icon: '🚕', color: '#d97706', kind: 'out', keywords: ['打车', '地铁', '公交', '高铁', '火车', '机票', '加油', '停车', '滴滴', '单车'], sortOrder: 2 },
  { id: 'c3', name: '住房', icon: '🏠', color: '#4f46e5', kind: 'out', keywords: ['房租', '物业', '水费', '电费', '燃气', '宽带', '维修'], sortOrder: 3 },
  { id: 'c4', name: '购物', icon: '🛍️', color: '#7c3aed', kind: 'out', keywords: ['淘宝', '京东', '超市', '衣服', '鞋', '日用'], sortOrder: 4 },
  { id: 'c5', name: '宠物', icon: '🐱', color: '#db2777', kind: 'out', keywords: ['猫粮', '猫砂', '宠物', '疫苗', '驱虫'], sortOrder: 5 },
  { id: 'c6', name: '订阅', icon: '📺', color: '#2563eb', kind: 'out', keywords: ['会员', '订阅', 'icloud', 'netflix', 'spotify', '续费', 'chatgpt', 'claude'], sortOrder: 6 },
  { id: 'c7', name: '医疗', icon: '💊', color: '#dc2626', kind: 'out', keywords: ['药', '医院', '挂号', '体检', '牙'], sortOrder: 7 },
  { id: 'c8', name: '其他', icon: '🧺', color: '#9ca3af', kind: 'out', keywords: [], sortOrder: 99 },
  { id: 'c9', name: '工资', icon: '💰', color: '#16a34a', kind: 'in', keywords: ['工资', '薪水', '奖金', '年终'], sortOrder: 10 },
  { id: 'c10', name: '其他收入', icon: '💵', color: '#65a30d', kind: 'in', keywords: ['卖出', '退款', '红包', '利息', '报销'], sortOrder: 98 },
]

/** 周期账单从日常流水开始的那个月算起（种子流水覆盖过去半年） */
const seedRecurring = (): RecRow[] => {
  const startMonth = addDays(todayYmd(), -180).slice(0, 7)
  return [
    { id: 'rc1', note: '房租', amount: 350000, direction: 'out', categoryId: 'c3', day: 20, active: true, startMonth },
    { id: 'rc2', note: 'iCloud 200G', amount: 2100, direction: 'out', categoryId: 'c6', day: 3, active: true, startMonth },
    { id: 'rc3', note: '视频会员', amount: 2500, direction: 'out', categoryId: 'c6', day: 12, active: true, startMonth },
    { id: 'rc4', note: '工资', amount: 1800000, direction: 'in', categoryId: 'c9', day: 10, active: true, startMonth },
  ]
}

/** 过去半年的日常流水：可复现的伪随机，保证每次种子一样 */
const seedEntries = (): EntryRow[] => {
  const today = todayYmd()
  const rows: EntryRow[] = []
  let seed = 20260926
  const rand = () => ((seed = (seed * 1103515245 + 12345) % 2 ** 31) / 2 ** 31)
  const add = (date: string, amount: number, categoryId: string, note: string, direction: 'out' | 'in' = 'out') =>
    rows.push({ id: `e${rows.length + 1}`, amount, direction, categoryId, date, note, recurringId: null, createTime: `${date} ${String(8 + (rows.length % 12)).padStart(2, '0')}:${String(rows.length % 60).padStart(2, '0')}:00` })
  // 餐饮逐月略涨，趋势图里能看出来
  for (let d = 180; d >= 1; d -= 1) {
    const date = addDays(today, -d)
    const drift = 1 + (180 - d) / 400
    if (rand() > 0.25) add(date, Math.round((1800 + rand() * 1200) * drift), 'c1', rand() > 0.5 ? '午饭' : '外卖')
    if (rand() > 0.7) add(date, Math.round(1500 + rand() * 1000), 'c1', rand() > 0.5 ? '瑞幸' : '奶茶')
    if (rand() > 0.96) add(date, Math.round((15000 + rand() * 12000) * drift), 'c1', '和同事聚餐')
    if (rand() > 0.6) add(date, rand() > 0.7 ? Math.round(2600 + rand() * 3000) : 400, 'c2', rand() > 0.7 ? '打车' : '地铁')
    if (rand() > 0.93) add(date, Math.round(5000 + rand() * 25000), 'c4', rand() > 0.5 ? '超市' : '京东 日用')
    if (rand() > 0.96) add(date, Math.round(4000 + rand() * 8000), 'c5', rand() > 0.5 ? '猫粮' : '猫砂')
    if (rand() > 0.985) add(date, Math.round(3000 + rand() * 20000), 'c7', '药')
    if (rand() > 0.97) add(date, Math.round(1000 + rand() * 6000), 'c8', '快递')
  }
  // 设计稿里的几笔
  add(today, 3200, 'c1', '午饭')
  add(today, 1600, 'c1', '瑞幸')
  add(today, 4600, 'c5', '低敏猫粮 2kg')
  add(addDays(today, -1), 28600, 'c1', '和同事聚餐')
  add(addDays(today, -1), 4600, 'c2', '打车回家')
  add(addDays(today, -6), 60000, 'c10', '二手显示器卖出', 'in')
  return rows
}

const categories = createMockTable<CatRow>('ledger-categories.v1', () => structuredClone(CATEGORIES))
const entries = createMockTable<EntryRow>('ledger-entries.v3', seedEntries)
const recurring = createMockTable<RecRow>('ledger-recurring.v2', seedRecurring)
const budgets = createMockTable<BudgetRow>('ledger-budgets.v1', () => [
  { id: 'b1', month: '2026-01', total: 800000, items: { c1: 150000, c2: 80000, c3: 350000, c4: 80000, c5: 30000, c6: 40000, c7: 30000, c8: 40000 } },
])

/** 补齐周期账单：每个启用的周期账单，从开始月到今天，每月那天有一笔 */
const fillRecurring = () => {
  const today = todayYmd()
  let changed = false
  for (const r of recurring.all().filter((x) => x.active)) {
    let [y, m] = r.startMonth.split('-').map(Number) as [number, number]
    for (;;) {
      const month = `${y}-${String(m).padStart(2, '0')}`
      const date = `${month}-${String(r.day).padStart(2, '0')}`
      if (date > today) break
      if (!entries.all().some((e) => String(e.recurringId) === r.id && e.date.startsWith(month))) {
        entries.all().push({ id: nextId(), amount: r.amount, direction: r.direction, categoryId: r.categoryId, date, note: r.note, recurringId: r.id, createTime: `${date} 09:00:00` })
        changed = true
      }
      m += 1
      if (m > 12) {
        m = 1
        y += 1
      }
    }
  }
  if (changed) entries.save()
}

const mock: typeof real = {
  fetchCategories: () => delay(structuredClone([...categories.all()].sort((a, b) => a.sortOrder - b.sortOrder)), 80),

  updateCategory: async (id, body) => {
    const row = categories.update(String(id), body as Partial<CatRow>)
    if (!row) throw new Error('分类不存在')
    return delay(structuredClone(row), 80)
  },

  fetchEntries: (query = {}) => {
    fillRecurring()
    return delay(
      structuredClone(
        entries
          .all()
          .filter((e) => (!query.from || e.date >= query.from) && (!query.to || e.date <= query.to))
          .sort((a, b) => b.date.localeCompare(a.date) || b.createTime.localeCompare(a.createTime)),
      ),
      140,
    )
  },

  createEntry: async (body) => {
    if (!body.amount || body.amount <= 0) throw new Error('金额要大于 0')
    const row = entries.insert({ ...body, id: nextId(), recurringId: body.recurringId ?? null, createTime: nowStamp() })
    return delay(structuredClone(row), 100)
  },

  updateEntry: async (id, body) => {
    const row = entries.update(String(id), body as Partial<EntryRow>)
    if (!row) throw new Error('这笔账不存在或已删除')
    return delay(structuredClone(row), 80)
  },

  deleteEntry: async (id) => {
    entries.remove(String(id))
    return delay(undefined, 80)
  },

  fetchRecurring: () => delay(structuredClone(recurring.all()), 80),

  createRecurring: async (body) => {
    const row = recurring.insert({ ...body, id: nextId() })
    fillRecurring()
    return delay(structuredClone(row), 100)
  },

  updateRecurring: async (id, body) => {
    const row = recurring.update(String(id), body as Partial<RecRow>)
    if (!row) throw new Error('周期账单不存在')
    return delay(structuredClone(row), 80)
  },

  deleteRecurring: async (id) => {
    recurring.remove(String(id))
    // 已经记下的流水保留，只是不再自动生成
    return delay(undefined, 80)
  },

  fetchBudget: (month) => {
    const exact = budgets.all().find((b) => b.month === month)
    const fallback = [...budgets.all()].filter((b) => b.month <= month).sort((a, b) => b.month.localeCompare(a.month))[0]
    const src = exact ?? fallback
    return delay({ month, total: src?.total ?? null, items: { ...(src?.items ?? {}) } }, 80)
  },

  saveBudget: async (month, body) => {
    const exists = budgets.all().find((b) => b.month === month)
    const row = exists ? budgets.update(exists.id, body)! : budgets.insert({ ...body, id: nextId(), month })
    const { id: _id, ...rest } = structuredClone(row)
    return delay(rest, 100)
  },
}

const api = useMockFor('ledger') ? mock : real

export const {
  fetchCategories,
  updateCategory,
  fetchEntries,
  createEntry,
  updateEntry,
  deleteEntry,
  fetchRecurring,
  createRecurring,
  updateRecurring,
  deleteRecurring,
  fetchBudget,
  saveBudget,
} = api
