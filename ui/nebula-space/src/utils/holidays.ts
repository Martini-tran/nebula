/**
 * 日历上的节日名与休息日。
 * - 法定假日与调休上班按国务院公告（后端按年下发，见 stores/holidays.ts），页面显示前要先 ensure 那几年
 * - 其余只收公历固定日期的节日；没有公告数据的年份，休息日就按周末算
 */
import { pinia } from '../stores'
import { useHolidayStore } from '../stores/holidays'
import { weekdayOf } from './date'

const FIXED: Record<string, string> = {
  '01-01': '元旦',
  '03-08': '妇女节',
  '03-12': '植树节',
  '05-01': '劳动节',
  '05-04': '青年节',
  '06-01': '儿童节',
  '07-01': '建党节',
  '08-01': '建军节',
  '09-10': '教师节',
  '10-01': '国庆节',
  '12-25': '圣诞节',
}

/** 公告里的这一天：放假或调休上班；不在公告里为 undefined */
export const officialDay = (ymd: string) => useHolidayStore(pinia).days[ymd]

/** 节日名：法定假期里的日子用公告的节日名（春节、中秋节……），其余看公历固定节日 */
export const holidayOf = (ymd: string) => {
  const day = officialDay(ymd)
  return day?.off ? day.name : (FIXED[ymd.slice(5)] ?? '')
}

/** 休息日：法定放假算，调休上班不算，其余按周末 */
export const isRestDay = (ymd: string) => {
  const day = officialDay(ymd)
  return day ? day.off : [0, 6].includes(weekdayOf(ymd))
}

/** 日历角上的小字：「休」法定放假，「班」调休上班 */
export const dayMark = (ymd: string): '休' | '班' | '' => {
  const day = officialDay(ymd)
  return day ? (day.off ? '休' : '班') : ''
}

/** 一段日期跨了哪几年，给 ensure 用 */
export const yearsOf = (from: string, to: string) => {
  const a = Number(from.slice(0, 4))
  const b = Number(to.slice(0, 4))
  return a === b ? [a] : [a, b]
}
