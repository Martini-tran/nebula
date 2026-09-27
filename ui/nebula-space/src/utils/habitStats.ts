/**
 * 习惯统计：是否安排在某天、某天是否达标、当前 / 最长连续、近 30 天完成率。
 * 连续按频率规则算——每天型数天，每周 N 次型数周，指定星期型只数安排了的日子；
 * 今天还没打卡不算中断（一天还没过完）。
 */
import type { Habit, HabitLog } from '../types/habits'
import { addDays, diffDays, startOfWeek, todayYmd, weekdayOf } from './date'

export type LogMap = Map<string, HabitLog>

export const logMap = (logs: HabitLog[]) => new Map(logs.map((l) => [l.date, l]))

export const isScheduled = (habit: Habit, date: string) =>
  habit.freq.type !== 'weekdays' || habit.freq.days.includes(weekdayOf(date))

export const progressOf = (habit: Habit, log?: HabitLog) => Math.min(1, (log?.value ?? 0) / Math.max(1, habit.target))

export const isDone = (habit: Habit, log?: HabitLog) => (log?.value ?? 0) >= habit.target

/** 某周达标次数（每周 N 次型） */
const weekCount = (habit: Habit, logs: LogMap, monday: string) => {
  let n = 0
  for (let i = 0; i < 7; i += 1) if (isDone(habit, logs.get(addDays(monday, i)))) n += 1
  return n
}

export interface Streak {
  /** 当前连续；0 表示已中断 */
  current: number
  /** 中断前的上一段连续（用于「上次 12 天」） */
  last: number
  longest: number
  unit: '天' | '周'
}

export const streakOf = (habit: Habit, logs: LogMap, today = todayYmd()): Streak => {
  const start = habit.createTime.slice(0, 10)
  if (habit.freq.type === 'weekly_n') {
    const n = habit.freq.n
    const runs: number[] = []
    let run = 0
    const thisMonday = startOfWeek(today)
    for (let monday = startOfWeek(start); monday <= thisMonday; monday = addDays(monday, 7)) {
      const ok = weekCount(habit, logs, monday) >= n
      if (ok) run += 1
      // 本周还没过完，没达标不算断
      else if (monday !== thisMonday) {
        if (run) runs.push(run)
        run = 0
      }
    }
    const current = run
    return { current, last: runs.at(-1) ?? 0, longest: Math.max(current, ...runs, 0), unit: '周' }
  }
  const runs: number[] = []
  let run = 0
  for (let d = start; d <= today; d = addDays(d, 1)) {
    if (!isScheduled(habit, d)) continue
    if (isDone(habit, logs.get(d))) run += 1
    else if (d !== today) {
      if (run) runs.push(run)
      run = 0
    }
  }
  return { current: run, last: runs.at(-1) ?? 0, longest: Math.max(run, ...runs, 0), unit: '天' }
}

/** 近 N 天完成率：每天型 / 指定星期型按安排的日子算；每周 N 次型按周算 */
export const rateOf = (habit: Habit, logs: LogMap, days = 30, today = todayYmd()) => {
  const from = addDays(today, -(days - 1))
  if (habit.freq.type === 'weekly_n') {
    const n = habit.freq.n
    let ok = 0
    let total = 0
    for (let monday = startOfWeek(from); monday <= today; monday = addDays(monday, 7)) {
      if (monday === startOfWeek(today) && weekCount(habit, logs, monday) < n) continue
      total += 1
      if (weekCount(habit, logs, monday) >= n) ok += 1
    }
    return total ? ok / total : 0
  }
  let ok = 0
  let total = 0
  for (let d = from; d <= today; d = addDays(d, 1)) {
    if (!isScheduled(habit, d) || d < habit.createTime.slice(0, 10)) continue
    // 今天没打卡不计入分母
    if (d === today && !isDone(habit, logs.get(d))) continue
    total += 1
    if (isDone(habit, logs.get(d))) ok += 1
  }
  return total ? ok / total : 0
}

export const totalDone = (habit: Habit, logs: HabitLog[]) => logs.filter((l) => isDone(habit, l)).length

export const freqText = (habit: Habit) => {
  const f = habit.freq
  if (f.type === 'daily') return '每天'
  if (f.type === 'weekly_n') return `每周 ${f.n} 次`
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return `每周${[...f.days].sort((a, b) => ((a + 6) % 7) - ((b + 6) % 7)).map((d) => names[d]).join('、')}`
}

export const valueText = (habit: Habit, value: number) =>
  habit.kind === 'check' ? (value ? '完成' : '未完成') : `${value}${habit.kind === 'duration' ? ' 分钟' : ` ${habit.unit || '次'}`}`

/** 用一句话复述设置：每天喝 8 杯水，10:00 和 15:00 提醒你 */
export const describeHabit = (habit: Pick<Habit, 'name' | 'kind' | 'target' | 'unit' | 'freq' | 'reminders'>) => {
  const f = habit.freq
  const when = f.type === 'daily' ? '每天' : f.type === 'weekly_n' ? `每周 ${f.n} 次` : freqText(habit as Habit)
  const what =
    habit.kind === 'count'
      ? `${habit.name} ${habit.target} ${habit.unit || '次'}`
      : habit.kind === 'duration'
        ? `${habit.name} ${habit.target} 分钟`
        : habit.name
  const remind = habit.reminders.length ? `，${habit.reminders.join(' 和 ')} 提醒你` : ''
  // 「每周 3 次」后面接动作要断开：每周 3 次，冥想 10 分钟
  return `${when}${f.type === 'daily' ? '' : '，'}${what}${remind}。`
}

export const daysSince = (date: string) => diffDays(date, todayYmd())
