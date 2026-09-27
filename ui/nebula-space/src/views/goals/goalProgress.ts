/**
 * 年度目标的进度不用手动更新：订阅其他模块的数据实时算。
 * - habit：习惯打卡（勾选型按次数，计数 / 时长型按数值）× 折算
 * - reading：稍后读里当年读完的篇数
 * - ledger：记账当年的结余（收入 - 支出）
 * - task_list：某个任务清单当年完成的任务数
 * - manual：手动记
 * 进度条上的竖线是年度时间进度，落后于竖线 5% 以上标「落后」。
 */
import { diffDays, monthDay, relativeDay, ymdOf } from '../../utils/date'
import { isDone } from '../../utils/habitStats'
import type { Goal } from '../../types/goals'
import type { Habit, HabitLog } from '../../types/habits'
import type { ReadingItem } from '../../types/reading'
import type { LedgerEntry } from '../../types/ledger'
import type { Task, TaskList } from '../../types/tasks'

export interface GoalData {
  habits: Habit[]
  logs: HabitLog[]
  reading: ReadingItem[]
  entries: LedgerEntry[]
  tasks: Task[]
  lists: TaskList[]
}

export type GoalState = 'ahead' | 'ok' | 'behind' | 'done' | 'active'

export const STATE_LABEL: Record<GoalState, string> = { ahead: '领先', ok: '正常', behind: '落后', done: '已完成', active: '进行中' }

export interface GoalProgress {
  value: number
  pct: number
  state: GoalState
  /** 右侧一句：按现在的节奏年底约… / 后 N 个月需每月… */
  projection: string
  /** 底部一行：数据从哪来、最近一次是什么 */
  feed: string
  feedDelta: string
}

export const formatValue = (value: number, unit: string) => {
  const n = Math.abs(value) >= 100 ? Math.round(value) : Math.round(value * 10) / 10
  return unit === '¥' ? `¥ ${Math.round(n).toLocaleString('zh-CN')}` : `${n.toLocaleString('zh-CN')}${unit ? ` ${unit}` : ''}`
}

/** 年度时间进度（0~1）：过去的年份为 1，未来的为 0 */
export const yearPace = (year: number, today: string) => {
  const y = Number(today.slice(0, 4))
  if (year < y) return 1
  if (year > y) return 0
  return (diffDays(`${year}-01-01`, today) + 1) / (diffDays(`${year}-01-01`, `${year}-12-31`) + 1)
}

const inYear = (ymd: string | null | undefined, year: number) => Boolean(ymd) && ymd!.startsWith(String(year))

export const computeProgress = (goal: Goal, data: GoalData, today: string): GoalProgress => {
  const pace = yearPace(goal.year, today)
  const y = goal.year

  if (goal.kind === 'milestone') {
    const total = goal.krs.length
    const done = goal.krs.filter((k) => k.done).length
    return {
      value: done,
      pct: total ? done / total : 0,
      state: total && done === total ? 'done' : 'active',
      projection: '',
      feed: '',
      feedDelta: '',
    }
  }

  let value = goal.baseline
  let feed = ''
  let feedDelta = ''
  if (goal.source === 'habit') {
    const habit = data.habits.find((h) => String(h.id) === String(goal.sourceId))
    const logs = data.logs.filter((l) => String(l.habitId) === String(goal.sourceId) && inYear(l.date, y))
    const amountOf = (l: HabitLog) => (habit?.kind === 'check' ? (isDone(habit, l) ? 1 : 0) : l.value) * goal.factor
    value += logs.reduce((s, l) => s + amountOf(l), 0)
    const last = [...logs].sort((a, b) => b.date.localeCompare(a.date)).find((l) => amountOf(l) > 0)
    feed = `来自习惯「${habit?.name ?? '已删除的习惯'}」`
    if (last) feedDelta = `${relativeDay(last.date, today)} +${formatValue(amountOf(last), goal.unit)}`
  } else if (goal.source === 'reading') {
    const done = data.reading.filter((r) => r.status === 'done' && inYear(r.doneTime ? ymdOf(r.doneTime) : null, y))
    value += done.length * goal.factor
    const last = [...done].sort((a, b) => String(b.doneTime).localeCompare(String(a.doneTime)))[0]
    feed = '来自稍后读「读完」'
    if (last) feedDelta = `${monthDay(ymdOf(last.doneTime!))} +${goal.factor}`
  } else if (goal.source === 'ledger') {
    const list = data.entries.filter((e) => inYear(e.date, y))
    const net = (l: LedgerEntry[]) => l.reduce((s, e) => s + (e.direction === 'in' ? e.amount : -e.amount), 0) / 100
    value += net(list) * goal.factor
    const months = [...new Set(list.map((e) => Number(e.date.slice(5, 7))))].sort((a, b) => a - b)
    const lastMonth = months.at(-1)
    feed = months.length ? `来自记账 ${months[0]}–${lastMonth} 月结余累计` : '来自记账结余'
    if (lastMonth) feedDelta = `${lastMonth} 月 ${net(list.filter((e) => Number(e.date.slice(5, 7)) === lastMonth)) >= 0 ? '+' : '-'}${formatValue(Math.abs(net(list.filter((e) => Number(e.date.slice(5, 7)) === lastMonth))), '¥')}`
  } else if (goal.source === 'task_list') {
    const list = data.lists.find((l) => String(l.id) === String(goal.sourceId))
    const done = data.tasks.filter((t) => t.done && String(t.listId) === String(goal.sourceId) && inYear(t.doneTime ? ymdOf(t.doneTime) : null, y))
    value += done.length * goal.factor
    const last = [...done].sort((a, b) => String(b.doneTime).localeCompare(String(a.doneTime)))[0]
    feed = `来自任务清单「${list?.name ?? '已删除的清单'}」完成数`
    if (last) feedDelta = `${relativeDay(ymdOf(last.doneTime!), today)}「${last.title}」`
  } else {
    value = goal.manualValue
    feed = '手动记录'
  }

  const pct = goal.target ? Math.max(0, value / goal.target) : 0
  let state: GoalState = 'ok'
  if (pct >= 1) state = 'done'
  else if (pct >= pace + 0.05) state = 'ahead'
  else if (pct < pace - 0.05) state = 'behind'

  let projection = ''
  if (state !== 'done' && pace > 0 && pace < 1) {
    if (state === 'behind') {
      const monthsLeft = (1 - pace) * 12
      const need = (goal.target - value) / Math.max(0.5, monthsLeft)
      projection = `后 ${Math.max(1, Math.round(monthsLeft))} 个月需每月 ${formatValue(need, goal.unit)}`
    } else if (value > 0) {
      projection = `按现在的节奏年底约 ${formatValue(value / pace, goal.unit)}`
    }
  } else if (state === 'done') {
    projection = '已达成 🎉'
  }
  return { value, pct, state, projection, feed, feedDelta }
}

/** 截至某天的数据：周报按那周结束时算进度，不把之后的打卡、读完、记账算进去 */
export const goalDataAsOf = (data: GoalData, asOf: string): GoalData => ({
  ...data,
  logs: data.logs.filter((l) => l.date <= asOf),
  reading: data.reading.map((r) => (r.doneTime && ymdOf(r.doneTime) > asOf ? { ...r, status: 'reading', doneTime: null } : r)),
  entries: data.entries.filter((e) => e.date <= asOf),
  tasks: data.tasks.map((t) => (t.done && t.doneTime && ymdOf(t.doneTime) > asOf ? { ...t, done: false, doneTime: null } : t)),
})

export const goalAsOf = (goal: Goal, asOf: string): Goal => ({
  ...goal,
  krs: goal.krs.map((k) => (k.done && k.doneDate && k.doneDate > asOf ? { ...k, done: false, doneDate: null } : k)),
})
