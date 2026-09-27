/**
 * 任务快速输入的中文自然语言解析：「明天下午3点 回电话 !2 #工作」
 * → 日期 明天、时间 15:00、优先级 中、清单 工作、标题「回电话」。
 * 只在前端做，后端只收结构化字段。识别出的片段返回位置，输入框下方据此高亮。
 */
import { addDays, fromYmd, nextWeekday, todayYmd, toYmd } from './date'

export type ParsedKind = 'date' | 'time' | 'priority' | 'list'

export interface ParsedSpan {
  kind: ParsedKind
  start: number
  end: number
  text: string
}

export interface ParsedTask {
  title: string
  dueDate?: string
  dueTime?: string
  /** 3 高 2 中 1 低 0 无 */
  priority: number
  listName?: string
  spans: ParsedSpan[]
}

const WEEK: Record<string, number> = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 7, 天: 7 }
const CN_NUM: Record<string, number> = { 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10, 十一: 11, 十二: 12 }

const toNumber = (text: string) => (/^\d+$/.test(text) ? Number(text) : (CN_NUM[text] ?? NaN))
const pad = (n: number) => String(n).padStart(2, '0')

interface Rule {
  kind: ParsedKind
  re: RegExp
  apply: (m: RegExpExecArray, out: ParsedTask, today: string) => boolean
}

const RULES: Rule[] = [
  {
    kind: 'priority',
    re: /(^|\s)!([123])(?=\s|$)/g,
    apply: (m, out) => {
      out.priority = 4 - Number(m[2])
      return true
    },
  },
  {
    kind: 'list',
    re: /(^|\s)#([^\s#!]+)/g,
    apply: (m, out) => {
      out.listName = m[2]
      return true
    },
  },
  {
    kind: 'date',
    re: /(大后天|后天|明天|今天|今晚|明早|明晚)/g,
    apply: (m, out, today) => {
      const offset: Record<string, number> = { 今天: 0, 今晚: 0, 明天: 1, 明早: 1, 明晚: 1, 后天: 2, 大后天: 3 }
      out.dueDate = addDays(today, offset[m[1]!]!)
      // 「今晚 / 明晚」没写几点时默认晚上 8 点
      if ((m[1] === '今晚' || m[1] === '明晚') && !out.dueTime) out.dueTime = '20:00'
      return true
    },
  },
  {
    kind: 'date',
    re: /(下下|下)?(?:周|星期|礼拜)([一二三四五六日天])/g,
    apply: (m, out, today) => {
      const weekday = WEEK[m[2]!]!
      let date = nextWeekday(weekday, today)
      // 「下周三」：下个自然周的周三
      if (m[1]) {
        const thisMonday = addDays(today, -((fromYmd(today).getDay() + 6) % 7))
        date = addDays(thisMonday, (m[1] === '下下' ? 14 : 7) + weekday - 1)
      }
      out.dueDate = date
      return true
    },
  },
  {
    kind: 'date',
    re: /(\d{1,2})月(\d{1,2})[日号]?/g,
    apply: (m, out, today) => {
      const [month, day] = [Number(m[1]), Number(m[2])]
      if (month < 1 || month > 12 || day < 1 || day > 31) return false
      const year = fromYmd(today).getFullYear()
      let date = toYmd(new Date(year, month - 1, day))
      // 已经过去的日期理解为明年
      if (date < today) date = toYmd(new Date(year + 1, month - 1, day))
      out.dueDate = date
      return true
    },
  },
  {
    kind: 'date',
    re: /(\d+|[一两三四五六七八九十])天后/g,
    apply: (m, out, today) => {
      const n = toNumber(m[1]!)
      if (!Number.isFinite(n)) return false
      out.dueDate = addDays(today, n)
      return true
    },
  },
  {
    kind: 'time',
    re: /(早上|上午|中午|下午|傍晚|晚上)?(\d{1,2}|十[一二]?|[一两三四五六七八九十])(?:点|时)(半|(\d{1,2})分?)?/g,
    apply: (m, out) => {
      let hour = toNumber(m[2]!)
      if (!Number.isFinite(hour) || hour > 23) return false
      const period = m[1]
      // 「有一点事」里的「一点」不是时间
      if (!period && m[2] === '一' && !m[3]) return false
      if ((period === '下午' || period === '傍晚' || period === '晚上') && hour < 12) hour += 12
      if (period === '中午' && hour < 6) hour += 12
      const minute = m[3] === '半' ? 30 : m[4] ? Number(m[4]) : 0
      if (minute > 59) return false
      out.dueTime = `${pad(hour)}:${pad(minute)}`
      return true
    },
  },
  {
    kind: 'time',
    re: /(^|\s)([01]?\d|2[0-3])[:：]([0-5]\d)(?=\s|$)/g,
    apply: (m, out) => {
      out.dueTime = `${pad(Number(m[2]))}:${m[3]}`
      return true
    },
  },
]

export const parseTaskInput = (input: string, today = todayYmd()): ParsedTask => {
  const out: ParsedTask = { title: '', priority: 0, spans: [] }
  const taken: [number, number][] = []
  const free = (start: number, end: number) => taken.every(([s, e]) => end <= s || start >= e)

  for (const rule of RULES) {
    rule.re.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = rule.re.exec(input))) {
      // 以 (^|\s) 开头的规则，前导空白不算在识别片段里
      const lead = rule.re.source.startsWith('(^|\\s)') ? (m[1]?.length ?? 0) : 0
      const start = m.index + lead
      const end = m.index + m[0].length
      if (!free(start, end)) continue
      // 同一类只认第一个，后面的留在标题里
      if (out.spans.some((span) => span.kind === rule.kind)) continue
      if (!rule.apply(m, out, today)) continue
      taken.push([start, end])
      out.spans.push({ kind: rule.kind, start, end, text: input.slice(start, end) })
    }
  }

  // 只写了时间没写日期：默认今天（已经过了的时间理解为明天）
  if (out.dueTime && !out.dueDate) {
    const now = new Date()
    const current = `${pad(now.getHours())}:${pad(now.getMinutes())}`
    out.dueDate = today === todayYmd() && out.dueTime <= current ? addDays(today, 1) : today
  }

  out.spans.sort((a, b) => a.start - b.start)
  let title = ''
  let cursor = 0
  for (const span of out.spans) {
    title += `${input.slice(cursor, span.start)} `
    cursor = span.end
  }
  title += input.slice(cursor)
  out.title = title.replace(/\s+/g, ' ').trim()
  return out
}
