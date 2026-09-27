/**
 * 任务重复规则：算下一次日期、生成人话描述。完成一次重复任务时据此生成下一次。
 */
import type { RepeatRule } from '../types/tasks'
import { addDays, fromYmd, monthDay, toYmd, weekdayLabel, weekdayOf } from './date'

const WEEK_NAMES = ['日', '一', '二', '三', '四', '五', '六']

/** from 之后（不含 from）的下一次日期 */
export const nextOccurrence = (rule: RepeatRule, from: string): string => {
  switch (rule.type) {
    case 'daily':
      return addDays(from, 1)
    case 'weekdays': {
      let next = addDays(from, 1)
      while ([0, 6].includes(weekdayOf(next))) next = addDays(next, 1)
      return next
    }
    case 'weekly': {
      // days 用 0=周日 … 6=周六；没选就按原来那天每周一次
      const days = rule.days.length ? rule.days : [weekdayOf(from)]
      let next = addDays(from, 1)
      for (let i = 0; i < 7 && !days.includes(weekdayOf(next)); i += 1) next = addDays(next, 1)
      return next
    }
    case 'monthly': {
      const date = fromYmd(from)
      const day = date.getDate()
      // 31 号在小月落到月底
      const target = new Date(date.getFullYear(), date.getMonth() + 1, 1)
      const last = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
      target.setDate(Math.min(day, last))
      return toYmd(target)
    }
  }
}

export const describeRepeat = (rule: RepeatRule | null, anchor?: string | null): string => {
  if (!rule) return '不重复'
  switch (rule.type) {
    case 'daily':
      return '每天'
    case 'weekdays':
      return '工作日'
    case 'weekly': {
      const days = rule.days.length ? rule.days : anchor ? [weekdayOf(anchor)] : []
      if (!days.length) return '每周'
      const sorted = [...days].sort((a, b) => ((a + 6) % 7) - ((b + 6) % 7))
      return `每周${sorted.map((d) => WEEK_NAMES[d]).join('、')}`
    }
    case 'monthly':
      return anchor ? `每月 ${fromYmd(anchor).getDate()} 日` : '每月'
  }
}

/** 「下一次：10月3日 周六，之后 10/10、10/17…」 */
export const previewRepeat = (rule: RepeatRule, from: string, count = 3) => {
  const dates: string[] = []
  let current = from
  for (let i = 0; i < count; i += 1) {
    current = nextOccurrence(rule, current)
    dates.push(current)
  }
  const [first, ...rest] = dates
  const short = (ymd: string) => `${fromYmd(ymd).getMonth() + 1}/${fromYmd(ymd).getDate()}`
  return `${monthDay(first!)} ${weekdayLabel(first!)}，之后 ${rest.map(short).join('、')}…`
}
