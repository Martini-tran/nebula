/**
 * 全局搜索的查询语法（space-search.html「搜索语法」）：
 *   b: vue        只搜书签（n: 随手记、t: 任务、m: 会议、l: 稍后读与划线、r: 周报）
 *   #工作          按标签或清单过滤
 *   @张工          会议待办里负责人是张工的
 *   is:open       未完成的任务 / 待办（is:done、is:overdue）
 *   after:9-20    该日期之后创建或发生（before: 同理，含当天）
 *   "同名目录"     精确短语
 * 其余的词全部要命中（不分大小写）。
 */
import { todayYmd } from './date'

export type SearchKind = 'task' | 'note' | 'bookmark' | 'meeting' | 'reading' | 'report'

export const SEARCH_KINDS: { key: SearchKind; label: string; prefix: string; icon: string }[] = [
  { key: 'task', label: '任务', prefix: 't', icon: 'lucide:square-check-big' },
  { key: 'note', label: '随手记', prefix: 'n', icon: 'lucide:pencil-line' },
  { key: 'bookmark', label: '书签', prefix: 'b', icon: 'lucide:bookmark' },
  { key: 'meeting', label: '会议', prefix: 'm', icon: 'lucide:users' },
  { key: 'reading', label: '稍后读', prefix: 'l', icon: 'lucide:book-open' },
  { key: 'report', label: '周报', prefix: 'r', icon: 'lucide:file-text' },
]

export type SearchState = 'open' | 'done' | 'overdue'

export interface ParsedQuery {
  kind: SearchKind | null
  /** 小写的词与短语 */
  terms: string[]
  tags: string[]
  people: string[]
  states: SearchState[]
  after: string | null
  before: string | null
}

const TOKEN_RE = /"([^"]*)"?|(\S+)/g

/** 9-20、9/20、2026-9-20 → YYYY-MM-DD；认不出返回 null */
const parseDay = (raw: string, today: string) => {
  const m = /^(?:(\d{4})[-/.])?(\d{1,2})[-/.](\d{1,2})$/.exec(raw)
  if (!m) return null
  const year = m[1] ?? today.slice(0, 4)
  return `${year}-${m[2]!.padStart(2, '0')}-${m[3]!.padStart(2, '0')}`
}

export const parseSearch = (input: string, today = todayYmd()): ParsedQuery => {
  const q: ParsedQuery = { kind: null, terms: [], tags: [], people: [], states: [], after: null, before: null }
  for (const match of input.matchAll(TOKEN_RE)) {
    if (match[1] !== undefined) {
      if (match[1].trim()) q.terms.push(match[1].trim().toLowerCase())
      continue
    }
    const token = match[2]!
    const prefix = /^([bntmlr])[:：](.*)$/i.exec(token)
    if (prefix && !q.kind) {
      q.kind = SEARCH_KINDS.find((k) => k.prefix === prefix[1]!.toLowerCase())!.key
      if (prefix[2]) q.terms.push(prefix[2].toLowerCase())
      continue
    }
    if (token.length > 1 && (token[0] === '#' || token[0] === '＃')) {
      q.tags.push(token.slice(1).toLowerCase())
      continue
    }
    if (token.length > 1 && token[0] === '@') {
      q.people.push(token.slice(1).toLowerCase())
      continue
    }
    const kv = /^(is|after|before)[:：](.+)$/i.exec(token)
    if (kv) {
      const key = kv[1]!.toLowerCase()
      const value = kv[2]!.toLowerCase()
      if (key === 'is' && (value === 'open' || value === 'done' || value === 'overdue')) {
        q.states.push(value)
        continue
      }
      const day = key !== 'is' ? parseDay(value, today) : null
      if (day) {
        if (key === 'after') q.after = day
        else q.before = day
        continue
      }
    }
    q.terms.push(token.toLowerCase())
  }
  return q
}

/** 有没有任何条件（空条件时显示最近打开） */
export const isEmptyQuery = (q: ParsedQuery) =>
  !q.terms.length && !q.tags.length && !q.people.length && !q.states.length && !q.after && !q.before

export const hasAll = (text: string, terms: string[]) => {
  const lower = text.toLowerCase()
  return terms.every((t) => lower.includes(t))
}

export const hasAny = (text: string, terms: string[]) => {
  const lower = text.toLowerCase()
  return terms.some((t) => lower.includes(t))
}

export const inRange = (ymd: string | null | undefined, q: ParsedQuery) =>
  (!q.after || (Boolean(ymd) && ymd! >= q.after)) && (!q.before || (Boolean(ymd) && ymd! <= q.before))

/** 正文命中处前后各取一段，作结果下方的上下文 */
export const snippetOf = (text: string, terms: string[], radius = 26) => {
  const flat = text.replace(/\s+/g, ' ').trim()
  const lower = flat.toLowerCase()
  const at = terms.map((t) => lower.indexOf(t)).filter((i) => i >= 0).sort((a, b) => a - b)[0]
  if (at === undefined) return flat.slice(0, radius * 2)
  const start = Math.max(0, at - radius)
  const end = Math.min(flat.length, at + radius * 2)
  return `${start > 0 ? '…' : ''}${flat.slice(start, end)}${end < flat.length ? '…' : ''}`
}

/** 拆成「普通 / 命中」片段，模板里逐段渲染（不用 v-html） */
export const highlightParts = (text: string, terms: string[]) => {
  const words = terms.filter(Boolean)
  if (!words.length) return [{ text, hit: false }]
  const escaped = words.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).sort((a, b) => b.length - a.length)
  const re = new RegExp(`(${escaped.join('|')})`, 'gi')
  return text
    .split(re)
    .filter(Boolean)
    .map((part) => ({ text: part, hit: words.includes(part.toLowerCase()) }))
}
