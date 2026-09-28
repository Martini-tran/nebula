/**
 * 纪念日的日期计算：下一次在哪天、还有几天、副标题怎么写；以及到期前自动生成任务。
 */
import { createTask } from '../../api/tasks'
import { updateAnniversary } from '../../api/goals'
import { addDays, diffDays, fromYmd, monthDay, todayYmd, weekdayLabel } from '../../utils/date'
import { lunarText, nextLunar } from '../../utils/lunar'
import type { Anniversary } from '../../types/goals'

const isLeap = (y: number) => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0

/** 下一次（含今天）；正数日没有「下一次」 */
export const nextOccurrence = (a: Anniversary, today = todayYmd()): string | null => {
  if (a.type === 'countup') return null
  if (a.type === 'countdown') return a.date
  if (a.calendar === 'lunar' && a.lunarMonth && a.lunarDay) return nextLunar(a.lunarMonth, a.lunarDay, today)
  const y = Number(today.slice(0, 4))
  const md = a.date.slice(5)
  const onYear = (year: number) => (md === '02-29' && !isLeap(year) ? `${year}-02-28` : `${year}-${md}`)
  const thisYear = onYear(y)
  return thisYear >= today ? thisYear : onYear(y + 1)
}

/** 距离下一次还有几天（负数 = 倒数日已经过去）；正数日返回已经过了几天 */
export const daysOf = (a: Anniversary, today = todayYmd()) => {
  if (a.type === 'countup') return diffDays(a.date, today)
  return diffDays(today, nextOccurrence(a, today)!)
}

const fullDate = (ymd: string) => {
  const d = fromYmd(ymd)
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
}

export const subtitleOf = (a: Anniversary, today = todayYmd()) => {
  const next = nextOccurrence(a, today)
  if (a.type === 'countup') return `${fullDate(a.date)}起`
  if (a.type === 'countdown') {
    const days = diffDays(today, a.date)
    return `${fullDate(a.date)}${days >= 0 && days <= 30 ? ` ${weekdayLabel(a.date)}` : ''}${a.note ? ` · ${a.note}` : ''}`
  }
  const nth = Number(next!.slice(0, 4)) - Number(a.date.slice(0, 4))
  if (a.calendar === 'lunar' && a.lunarMonth && a.lunarDay) {
    return `农历${lunarText(a.lunarMonth, a.lunarDay)} · ${next!.slice(0, 4) === today.slice(0, 4) ? '今年' : '明年'} ${monthDay(next!).replace('月', ' 月 ').replace('日', ' 日')}${a.note ? ` · ${a.note}` : ''}`
  }
  const md = `${Number(a.date.slice(5, 7))} 月 ${Number(a.date.slice(8, 10))} 日`
  return `每年 ${md}${a.tag === '生日' ? '' : nth > 0 ? ` · 第 ${nth} 周年` : ''}${a.note ? ` · ${a.note}` : ''}`
}

/** 设了「提醒时生成任务」的：离下一次还有「提醒天数 + 30 天」以内时，生成一条到提醒那天的任务（每次只生成一条） */
export const syncAnniversaryTasks = async (list: Anniversary[], today = todayYmd()) => {
  let created = 0
  for (const a of list) {
    if (!a.createTask || a.remindDays === null || a.type === 'countup') continue
    const next = nextOccurrence(a, today)
    if (!next || next < today || a.taskFor === next) continue
    if (diffDays(today, next) > a.remindDays + 30) continue
    const due = addDays(next, -a.remindDays)
    await createTask({
      title: a.taskTitle.trim() || a.title,
      dueDate: due < today ? today : due,
      note: `${a.title}：${monthDay(next)} ${weekdayLabel(next)}`,
    })
    await updateAnniversary(a.id, { taskFor: next })
    a.taskFor = next
    created += 1
  }
  return created
}
