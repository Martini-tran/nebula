/**
 * 习惯接口：后端 /space/me/habits、/space/me/habit-logs（nebula-service-space，表 space_habit、space_habit_log）。
 * VITE_REAL_MODULES 不含 habits 时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 打卡是「某习惯某天的值」：同一天只有一条，写 0 等于取消打卡；只能打今天和补前 HABIT_BACKFILL_DAYS 天（后端同样校验）。
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd, weekdayOf } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Habit, HabitLog, HabitSaveRequest } from '../types/habits'

export interface HabitLogQuery {
  habitId?: EntityId
  from?: string
  to?: string
}

const BASE = '/space/me'

const real = {
  fetchHabits: (includeArchived = false) => get<Habit[]>(`${BASE}/habits`, { params: { includeArchived } }),
  createHabit: (body: HabitSaveRequest & { name: string }) => post<Habit>(`${BASE}/habits`, body),
  updateHabit: (id: EntityId, body: HabitSaveRequest) => put<Habit>(`${BASE}/habits/${id}`, body),
  deleteHabit: (id: EntityId) => del<void>(`${BASE}/habits/${id}`),
  fetchHabitLogs: (query: HabitLogQuery = {}) => get<HabitLog[]>(`${BASE}/habit-logs`, { params: query }),
  /** 写入某天的值（覆盖）；value=0 删除那天的打卡 */
  setHabitLog: (habitId: EntityId, date: string, value: number, note?: string) =>
    put<HabitLog | null>(`${BASE}/habits/${habitId}/logs/${date}`, { value, note }),
}

// ── mock ──

type HabitRow = Habit & { id: string }
type LogRow = HabitLog & { id: string }

const habits = createMockTable<HabitRow>('habits.v1', () => {
  const base = { reminders: [], fromFocus: false, archived: false, unit: '', createTime: `${addDays(todayYmd(), -260)} 09:00:00` }
  return [
    { ...base, id: 'h1', name: '晨跑 5 公里', icon: '🏃', kind: 'check', target: 1, freq: { type: 'daily' }, reminders: ['07:00'], sortOrder: 0 },
    { ...base, id: 'h2', name: '喝水', icon: '💧', kind: 'count', target: 8, unit: '杯', freq: { type: 'daily' }, reminders: ['10:00', '15:00'], sortOrder: 1 },
    { ...base, id: 'h3', name: '读书', icon: '📖', kind: 'duration', target: 30, unit: '分钟', freq: { type: 'daily' }, fromFocus: true, sortOrder: 2 },
    { ...base, id: 'h4', name: '力量训练', icon: '🏋️', kind: 'check', target: 1, freq: { type: 'weekly_n', n: 3 }, sortOrder: 3 },
    { ...base, id: 'h5', name: '睡前不看手机', icon: '🧘', kind: 'check', target: 1, freq: { type: 'daily' }, reminders: ['22:30'], sortOrder: 4 },
  ]
})

/** 种子打卡：确定性的伪随机，让热力图有深有浅、连续天数有断有续 */
const logs = createMockTable<LogRow>('habit-logs.v1', () => {
  let s = 20260926
  const rand = () => ((s = (s * 1103515245 + 12345) % 2147483648) / 2147483648)
  const rows: LogRow[] = []
  const today = todayYmd()
  const push = (habitId: string, date: string, value: number, note = '') =>
    rows.push({ id: `${habitId}-${date}`, habitId, date, value, note, backfilled: false, time: `${date} 07:1${Math.floor(rand() * 10)}:00` })
  for (let i = 250; i >= 1; i -= 1) {
    const date = addDays(today, -i)
    // 晨跑：最近 12 天连续，之前偶有中断
    if (i <= 12 || (i !== 13 && rand() < 0.8)) push('h1', date, 1, i === 1 ? '配速 5\'40"，膝盖没问题' : '')
    if (rand() < 0.9) push('h2', date, Math.min(8, 3 + Math.floor(rand() * 7)))
    if (i <= 2 || (i > 3 && rand() < 0.65)) push('h3', date, 15 + Math.floor(rand() * 30))
    if ([1, 3, 5].includes(weekdayOf(date)) && rand() < 0.9) push('h4', date, 1)
    if (i > 4 && rand() < 0.4) push('h5', date, 1)
  }
  push('h2', today, 5)
  return rows.reverse()
})

const mock: typeof real = {
  fetchHabits: (includeArchived = false) =>
    delay(structuredClone(habits.all().filter((h) => includeArchived || !h.archived).sort((a, b) => a.sortOrder - b.sortOrder)), 120),

  createHabit: async (body) =>
    delay(
      structuredClone(
        habits.insert({
          reminders: [],
          fromFocus: false,
          archived: false,
          unit: '',
          icon: '✅',
          kind: 'check',
          target: 1,
          freq: { type: 'daily' },
          sortOrder: habits.all().length,
          ...body,
          id: nextId(),
          createTime: nowStamp(),
        } as HabitRow),
      ),
      140,
    ),

  updateHabit: async (id, body) => {
    const row = habits.update(id, body as Partial<HabitRow>)
    if (!row) throw new Error('习惯不存在或已删除')
    return delay(structuredClone(row), 100)
  },

  deleteHabit: async (id) => {
    habits.remove(id)
    logs.all().filter((l) => l.habitId === String(id)).forEach((l) => logs.remove(l.id))
    return delay(undefined, 100)
  },

  fetchHabitLogs: (query = {}) =>
    delay(
      structuredClone(
        logs
          .all()
          .filter((l) => query.habitId === undefined || l.habitId === String(query.habitId))
          .filter((l) => (!query.from || l.date >= query.from) && (!query.to || l.date <= query.to)),
      ),
      120,
    ),

  setHabitLog: async (habitId, date, value, note) => {
    const id = `${habitId}-${date}`
    const existing = logs.find(id)
    if (value <= 0) {
      if (existing) logs.remove(id)
      return delay(null, 80)
    }
    if (existing) return delay(structuredClone(logs.update(id, { value, note: note ?? existing.note, time: nowStamp() })!), 80)
    const row = logs.insert({ id, habitId: String(habitId), date, value, note: note ?? '', backfilled: date < todayYmd(), time: nowStamp() })
    return delay(structuredClone(row), 80)
  },
}

const api = useMockFor('habits') ? mock : real

export const { fetchHabits, createHabit, updateHabit, deleteHabit, fetchHabitLogs, setHabitLog } = api
