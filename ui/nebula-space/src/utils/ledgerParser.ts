/**
 * 记账输入解析：一行文字记一笔——「午饭 32 餐饮」「昨天 打车 46」「工资 +18000」「9月20日 房租 3500」。
 * 识别日期、金额（前面带 + 是收入）、分类（写了分类名就用；否则按关键词；再否则沿用同备注上一次的分类），
 * 剩下的是备注。只在前端做；识别出的片段返回位置，输入框下方据此显示。
 */
import { addDays, fromYmd, todayYmd, weekdayOf } from './date'
import type { Direction, LedgerCategory, LedgerEntry } from '../types/ledger'

export type LedgerSpanKind = 'date' | 'amount' | 'category'

export interface LedgerSpan {
  kind: LedgerSpanKind
  start: number
  end: number
  text: string
}

export interface ParsedEntry {
  /** 分；没识别出金额为 0 */
  amount: number
  direction: Direction
  categoryId: string | null
  /** 分类是怎么来的：写明的 / 关键词 / 按习惯 / 默认 */
  categoryBy: 'explicit' | 'keyword' | 'habit' | 'default'
  date: string
  note: string
  spans: LedgerSpan[]
}

const WEEK: Record<string, number> = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 0, 天: 0 }

/** 过去的日期：今天 / 昨天 / 前天 / 周五（最近的那个，不含未来）/ 上周三 / 9月20日 / 9-20 */
const DATE_RULES: { re: RegExp; to: (m: RegExpExecArray, today: string) => string | null }[] = [
  {
    re: /(大前天|前天|昨天|今天)/,
    to: (m, today) => addDays(today, -{ 今天: 0, 昨天: 1, 前天: 2, 大前天: 3 }[m[1] as '今天']),
  },
  {
    re: /上(?:周|星期|礼拜)([一二三四五六日天])/,
    to: (m, today) => {
      const monday = addDays(today, -((weekdayOf(today) + 6) % 7))
      return addDays(monday, -7 + ((WEEK[m[1]!]! + 6) % 7))
    },
  },
  {
    re: /(?:周|星期|礼拜)([一二三四五六日天])/,
    to: (m, today) => addDays(today, -((weekdayOf(today) - WEEK[m[1]!]! + 7) % 7)),
  },
  {
    re: /(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?|(?<![\d.])(\d{1,2})[-/](\d{1,2})(?![\d.])/,
    to: (m, today) => {
      const month = Number(m[1] ?? m[3])
      const day = Number(m[2] ?? m[4])
      if (month < 1 || month > 12 || day < 1 || day > 31) return null
      let year = Number(today.slice(0, 4))
      const ymd = (y: number) => `${y}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
      // 记的是过去的账：比今天晚就当去年
      if (ymd(year) > today) year -= 1
      const date = fromYmd(ymd(year))
      return date.getDate() === day ? ymd(year) : null
    },
  },
]

const AMOUNT_RE = /([+＋-]?)\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)\s*(?:元|块钱?)?/g

export const parseLedgerInput = (
  input: string,
  categories: LedgerCategory[],
  history: LedgerEntry[] = [],
  today = todayYmd(),
): ParsedEntry => {
  const spans: LedgerSpan[] = []
  const taken = (start: number, end: number) => spans.some((s) => start < s.end && s.start < end)
  let date = today

  for (const rule of DATE_RULES) {
    const m = rule.re.exec(input)
    if (!m || taken(m.index, m.index + m[0].length)) continue
    const value = rule.to(m, today)
    if (!value) continue
    date = value
    spans.push({ kind: 'date', start: m.index, end: m.index + m[0].length, text: m[0] })
    break
  }

  // 金额：取最后一个不在日期里的数字（「3 号线 地铁 4」取 4）
  let amount = 0
  let sign = ''
  let last: RegExpExecArray | null = null
  for (const m of input.matchAll(AMOUNT_RE)) {
    const start = m.index! + (m[0].length - m[0].trimStart().length)
    if (taken(start, m.index! + m[0].length)) continue
    last = m as RegExpExecArray
  }
  if (last) {
    const text = last[0].trim()
    const start = last.index + last[0].indexOf(text)
    amount = Math.round(Number(last[2]) * 100)
    sign = last[1] ?? ''
    spans.push({ kind: 'amount', start, end: start + text.length, text })
  }

  // 写明的分类名：整词出现
  let categoryId: string | null = null
  let categoryBy: ParsedEntry['categoryBy'] = 'default'
  const byLength = [...categories].sort((a, b) => b.name.length - a.name.length)
  for (const c of byLength) {
    const at = input.indexOf(c.name)
    if (at < 0 || taken(at, at + c.name.length)) continue
    const before = input[at - 1]
    const after = input[at + c.name.length]
    if ((before && !/\s/.test(before)) || (after && !/\s/.test(after))) continue
    categoryId = String(c.id)
    categoryBy = 'explicit'
    spans.push({ kind: 'category', start: at, end: at + c.name.length, text: c.name })
    break
  }

  spans.sort((a, b) => a.start - b.start)
  let note = ''
  let at = 0
  for (const s of spans) {
    note += input.slice(at, s.start)
    at = s.end
  }
  note = (note + input.slice(at)).replace(/\s+/g, ' ').trim()

  if (!categoryId && note) {
    // 同样的备注以前记过，就沿用那次的分类
    const same = [...history].sort((a, b) => b.createTime.localeCompare(a.createTime)).find((e) => e.note === note)
    if (same) {
      categoryId = String(same.categoryId)
      categoryBy = 'habit'
    } else {
      const hit = categories.find((c) => c.keywords.some((k) => k && note.toLowerCase().includes(k.toLowerCase())))
      if (hit) {
        categoryId = String(hit.id)
        categoryBy = 'keyword'
      }
    }
  }

  const cat = categories.find((c) => String(c.id) === categoryId)
  let direction: Direction = sign === '+' || sign === '＋' ? 'in' : cat?.kind ?? 'out'
  if (sign === '-') direction = 'out'
  // 方向和分类对不上（写了 +，分类却是支出类）时，换成那个方向的默认分类
  if (cat && cat.kind !== direction) {
    categoryId = null
    categoryBy = 'default'
  }
  if (!categoryId) {
    const fallback = categories.filter((c) => c.kind === direction).sort((a, b) => b.sortOrder - a.sortOrder)[0]
    categoryId = fallback ? String(fallback.id) : null
  }

  return { amount, direction, categoryId, categoryBy, date, note, spans }
}

/** 分 → 汇总里取整到元「6,482」，逐笔显示到分「32.00」 */
export const formatMoney = (cents: number, withDecimals = false) => {
  const abs = Math.abs(cents) / 100
  return withDecimals
    ? abs.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : Math.round(abs).toLocaleString('zh-CN')
}
