/**
 * 本地日期工具。任务、随手记、日历都按「本地日期」工作，统一用 YYYY-MM-DD 字符串，
 * 避免 Date 对象跨时区序列化时差一天。
 */

const pad = (n: number) => String(n).padStart(2, '0')
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

/** Date → YYYY-MM-DD（本地时区） */
export const toYmd = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

/** YYYY-MM-DD → 当天 0 点的 Date */
export const fromYmd = (ymd: string) => {
  const [y, m, d] = ymd.split('-').map(Number)
  return new Date(y!, (m ?? 1) - 1, d ?? 1)
}

export const todayYmd = () => toYmd(new Date())

export const addDays = (ymd: string, days: number) => {
  const date = fromYmd(ymd)
  date.setDate(date.getDate() + days)
  return toYmd(date)
}

/** 两个日期相差几天（b - a） */
export const diffDays = (a: string, b: string) => Math.round((fromYmd(b).getTime() - fromYmd(a).getTime()) / 86_400_000)

export const weekdayOf = (ymd: string) => fromYmd(ymd).getDay()

export const weekdayLabel = (ymd: string) => `周${WEEKDAYS[weekdayOf(ymd)]}`

/** 9月26日 */
export const monthDay = (ymd: string) => {
  const date = fromYmd(ymd)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

/** 今天 / 明天 / 昨天 / 周X（一周内）/ 9月26日（今年）/ 2025年9月26日 */
export const relativeDay = (ymd: string, today = todayYmd()) => {
  const diff = diffDays(today, ymd)
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  if (diff === 2) return '后天'
  if (diff === -1) return '昨天'
  if (diff > 0 && diff < 7) return weekdayLabel(ymd)
  const date = fromYmd(ymd)
  return date.getFullYear() === fromYmd(today).getFullYear()
    ? monthDay(ymd)
    : `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

/** 下一个周 N（1=周一 … 7=周日）；今天正好是周 N 时取下周那天 */
export const nextWeekday = (weekday: number, from = todayYmd()) => {
  const target = weekday % 7
  let diff = (target - weekdayOf(from) + 7) % 7
  if (diff === 0) diff = 7
  return addDays(from, diff)
}

/** 本周一 */
export const startOfWeek = (ymd: string) => addDays(ymd, -((weekdayOf(ymd) + 6) % 7))

/** 当前本地时间 YYYY-MM-DD HH:mm:ss，与后端 LocalDateTime 序列化格式一致 */
export const nowStamp = () => {
  const d = new Date()
  return `${toYmd(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 时间戳串取日期部分 */
export const ymdOf = (stamp: string) => stamp.replace('T', ' ').slice(0, 10)

/** 时间戳串取 HH:mm */
export const hmOf = (stamp: string) => stamp.replace('T', ' ').slice(11, 16)

/** 「刚刚 / 10:31 / 昨天 / 周一 / 9月20日」这类列表里用的简短时间 */
export const shortStamp = (stamp?: string | null) => {
  if (!stamp) return ''
  const ymd = ymdOf(stamp)
  return ymd === todayYmd() ? hmOf(stamp) : relativeDay(ymd)
}
