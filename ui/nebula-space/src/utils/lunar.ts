/**
 * 农历换算：用浏览器自带的 Intl 中国历（zh-CN-u-ca-chinese），不引第三方库。
 * 农历生日这类「每年农历某月某日」换成当年的公历日期：在那一农历年里逐日找。
 */
import { addDays, fromYmd, toYmd } from './date'

const fmt = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', { year: 'numeric', month: 'long', day: 'numeric' })
const MONTHS = ['正月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '冬月', '腊月']
const MONTHS_ALT = ['正月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']

export interface LunarDate {
  /** 农历年（对应的公历年份，春节所在那年） */
  year: number
  month: number
  leap: boolean
  day: number
}

export const lunarOf = (ymd: string): LunarDate => {
  const parts = fmt.formatToParts(fromYmd(ymd))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  const monthText = get('month')
  const leap = monthText.startsWith('闰')
  const name = monthText.replace('闰', '')
  const index = Math.max(MONTHS.indexOf(name), MONTHS_ALT.indexOf(name))
  return { year: Number(get('relatedYear') || get('year')), month: index + 1, leap, day: Number(get('day')) }
}

const DAY_NAMES = ['初', '十', '廿', '三']
const DIGITS = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
export const lunarDayName = (day: number) => {
  if (day === 10) return '初十'
  if (day === 20) return '二十'
  if (day === 30) return '三十'
  return `${DAY_NAMES[Math.floor(day / 10)]}${DIGITS[day % 10]}`
}
export const lunarMonthName = (month: number, leap = false) => `${leap ? '闰' : ''}${MONTHS[month - 1]}`
export const lunarText = (month: number, day: number) => `${lunarMonthName(month)}${lunarDayName(day)}`

/** 某农历年的某月某日对应的公历日期；那年这个月没有三十就取廿九 */
export const solarOfLunar = (lunarYear: number, month: number, day: number): string | null => {
  // 农历年大致从公历 1 月下旬到次年 2 月中旬
  let best: string | null = null
  for (let d = `${lunarYear}-01-15`; d <= `${lunarYear + 1}-02-25`; d = addDays(d, 1)) {
    const l = lunarOf(d)
    if (l.year !== lunarYear || l.leap || l.month !== month) {
      if (best) break
      continue
    }
    if (l.day === day) return d
    if (l.day < day) best = d
  }
  return best
}

/** 从某天起（含）的下一次农历某月某日 */
export const nextLunar = (month: number, day: number, from: string) => {
  const year = fromYmd(from).getFullYear()
  for (const y of [year - 1, year, year + 1]) {
    const d = solarOfLunar(y, month, day)
    if (d && d >= from) return d
  }
  return toYmd(fromYmd(from))
}
