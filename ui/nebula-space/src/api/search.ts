/**
 * 全局搜索。后端拟定为 GET /space/me/search?q=（见 space-search.html「后端待补」：
 * 第一版 MySQL ngram 全文索引跨表查询后合并排序），查询语法与 utils/searchQuery.ts 一致，原样把输入传过去。
 *
 * 未接通时在前端做：分别取各模块数据（书签走书签接口的关键词查询），按同样的规则过滤、打分。
 * 排序：标题命中 > 正文命中 > 最近打开 / 最近更新。
 */
import { get } from '../utils/request'
import { useMockFor } from './mock'
import { fetchTaskLists, fetchTasks } from './tasks'
import { fetchNotes } from './notes'
import { fetchMeetings } from './meetings'
import { fetchWeeklyReports } from './reviews'
import { fetchBookmarks } from './space'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'
import { useSpaceStore } from '../stores/space'
import { diffDays, monthDay, relativeDay, todayYmd, weekNumberOf, ymdOf, addDays } from '../utils/date'
import { firstLine, plainText } from '../utils/markdown'
import { parseMeetingItems } from '../utils/meetingItems'
import { recentItems } from '../utils/recent'
import { hasAll, hasAny, inRange, isEmptyQuery, parseSearch, snippetOf, type ParsedQuery, type SearchKind } from '../utils/searchQuery'
import type { Task, TaskList } from '../types/tasks'
import type { Note } from '../types/notes'
import type { Meeting } from '../types/meetings'
import type { WeeklyReport } from '../types/reviews'
import type { Bookmark } from '../types/space'

export interface SearchHit {
  kind: SearchKind
  id: string
  title: string
  /** 第二行：所属、时间、来源 */
  sub: string
  /** 正文命中时的上下文 */
  snippet?: string
  /** 右侧的短时间 */
  meta: string
  /** 站内路径 */
  to: string
  /** 书签的原网址 */
  url?: string
  /** 任务：清单色；书签：首字母色块 */
  color?: string
  /** 任务状态，决定图标 */
  state?: 'open' | 'done' | 'overdue'
  score: number
}

const real = {
  search: (q: string) => get<SearchHit[]>('/space/me/search', { params: { q } }),
}

// ── mock：前端合并 ──

interface Corpus {
  at: number
  tasks: Task[]
  lists: TaskList[]
  notes: Note[]
  meetings: Meeting[]
  reports: WeeklyReport[]
}

let corpus: Corpus | null = null
/** 同一次打开里连续输入不重复拉数据；20 秒后或调用 resetSearchCache 后重新取 */
const loadCorpus = async (): Promise<Corpus> => {
  if (corpus && Date.now() - corpus.at < 20_000) return corpus
  const settle = async <T>(p: Promise<T>, fallback: T) => p.catch(() => fallback)
  const [tasks, lists, notes, meetings, reports] = await Promise.all([
    settle(fetchTasks({ view: 'all' }), [] as Task[]),
    settle(fetchTaskLists(), [] as TaskList[]),
    settle(fetchNotes({ view: 'all' }), [] as Note[]),
    settle(fetchMeetings(), [] as Meeting[]),
    settle(fetchWeeklyReports(), [] as WeeklyReport[]),
  ])
  // 归档的笔记也要能搜到
  const archived = await settle(fetchNotes({ view: 'archived' }), [] as Note[])
  const seen = new Set(notes.map((n) => String(n.id)))
  corpus = { at: Date.now(), tasks, lists, notes: [...notes, ...archived.filter((n) => !seen.has(String(n.id)))], meetings, reports }
  return corpus
}

export const resetSearchCache = () => {
  corpus = null
}

/** 标题命中每词 3 分，正文 1 分；最近打开过 +2，30 天内更新过最多 +1 */
const scoreOf = (title: string, q: ParsedQuery, recent: Set<string>, key: string, time?: string | null) => {
  const lowerTitle = title.toLowerCase()
  let score = q.terms.reduce((sum, t) => sum + (lowerTitle.includes(t) ? 3 : 1), 0)
  if (recent.has(key)) score += 2
  if (time) score += Math.max(0, 1 - diffDays(ymdOf(time), todayYmd()) / 30)
  return score
}

const searchTasks = (c: Corpus, q: ParsedQuery, recent: Set<string>, today: string): SearchHit[] => {
  const listOf = (t: Task) => c.lists.find((l) => String(l.id) === String(t.listId))
  return c.tasks.flatMap((t) => {
    const list = listOf(t)
    const overdue = !t.done && Boolean(t.dueDate) && t.dueDate! < today
    if (q.states.length && !q.states.some((s) => (s === 'done' ? t.done : s === 'open' ? !t.done : overdue))) return []
    if (q.tags.length && !q.tags.every((tag) => list?.name.toLowerCase().includes(tag))) return []
    if (q.people.length && !hasAny(t.source?.label ?? '', q.people)) return []
    if (!inRange(t.dueDate ?? ymdOf(t.createTime), q)) return []
    const body = [t.note, ...t.subtasks.map((s) => s.title)].join(' ')
    if (!hasAll(`${t.title} ${body}`, q.terms)) return []
    const when = t.dueDate ? `${relativeDay(t.dueDate)}${t.dueTime ? ` ${t.dueTime}` : ''}` : '收件箱'
    const sub = [
      list?.name,
      t.done ? '已完成' : overdue ? `逾期 ${diffDays(t.dueDate!, today)} 天` : when,
      t.source ? `来自${t.source.label}` : '',
    ]
      .filter(Boolean)
      .join(' · ')
    return [
      {
        kind: 'task' as const,
        id: String(t.id),
        title: t.title,
        sub,
        snippet: q.terms.length && !hasAll(t.title, q.terms) && hasAny(body, q.terms) ? snippetOf(body, q.terms) : undefined,
        meta: t.dueDate ? (t.dueDate.slice(0, 4) === today.slice(0, 4) ? t.dueDate.slice(5).replace('-', '/') : t.dueDate) : '',
        to: `/tasks?v=all&task=${t.id}`,
        color: list?.color,
        state: t.done ? ('done' as const) : overdue ? ('overdue' as const) : ('open' as const),
        score: scoreOf(t.title, q, recent, `task:${t.id}`, t.createTime) + (t.done ? -0.5 : 0),
      },
    ]
  })
}

const searchNotes = (c: Corpus, q: ParsedQuery, recent: Set<string>): SearchHit[] => {
  if (q.states.length || q.people.length) return []
  return c.notes.flatMap((n) => {
    if (q.tags.length && !q.tags.every((tag) => n.tags.some((x) => x.toLowerCase().includes(tag)))) return []
    if (!inRange(ymdOf(n.createTime), q)) return []
    const title = firstLine(n.content) || '（空笔记）'
    const text = plainText(n.content)
    if (!hasAll(text, q.terms)) return []
    const body = text.slice(title.length)
    const life = n.archived ? '已归档' : n.pinned || !n.expireDate ? '长期' : '临时'
    return [
      {
        kind: 'note' as const,
        id: String(n.id),
        title,
        sub: [life, ...n.tags.map((t) => `#${t}`)].join(' · '),
        snippet: q.terms.length && !hasAll(title, q.terms) ? snippetOf(body, q.terms) : body ? snippetOf(body, [], 30) : undefined,
        meta: relativeDay(ymdOf(n.updateTime)),
        to: `/notes/${n.id}`,
        score: scoreOf(title, q, recent, `note:${n.id}`, n.updateTime) - (n.archived ? 0.5 : 0),
      },
    ]
  })
}

const DONE_RE = /\[[xX]\]/

const searchMeetings = (c: Corpus, q: ParsedQuery, recent: Set<string>, today: string): SearchHit[] => {
  if (q.tags.length) return []
  const myNames = (() => {
    const auth = useAuthStore(pinia)
    return [auth.user?.nickname ?? '', auth.user?.username ?? ''].filter(Boolean)
  })()
  return c.meetings.flatMap((m) => {
    if (!inRange(m.date, q)) return []
    const lines = m.content.split('\n')
    const title = `${m.title} · ${monthDay(m.date)}`
    let snippet: string | undefined
    // 按人或状态搜时，看的是会里的待办
    if (q.people.length || q.states.length) {
      const { actions } = parseMeetingItems(m.content, myNames, m.date)
      const match = actions.find((a) => {
        const done = DONE_RE.test(lines[a.line] ?? '')
        const overdue = !done && Boolean(a.due) && a.due! < today
        const owner = a.owner.toLowerCase()
        return (
          (!q.people.length || q.people.some((p) => owner.includes(p) || (p === '我' && a.mine))) &&
          (!q.states.length || q.states.some((s) => (s === 'done' ? done : s === 'open' ? !done : overdue))) &&
          hasAll(`${a.owner} ${a.text}`, q.terms)
        )
      })
      if (!match) return []
      snippet = `待办：@${match.owner} ${match.text}${match.due ? `，${relativeDay(match.due)}前` : ''}`
    } else {
      if (!hasAll(`${m.title} ${m.content}`, q.terms)) return []
      if (q.terms.length && !hasAll(m.title, q.terms)) {
        const line = lines.find((l) => hasAny(l, q.terms)) ?? ''
        snippet = snippetOf(line.replace(/^\s*[-*]\s*/, ''), q.terms)
      }
    }
    return [
      {
        kind: 'meeting' as const,
        id: String(m.id),
        title,
        sub: `${m.startTime} · ${m.durationMin} 分钟${m.attendees.length ? ` · ${m.attendees.map((a) => a.name).slice(0, 4).join('、')}` : ''}`,
        snippet,
        meta: relativeDay(m.date),
        to: `/meetings/${m.id}`,
        score: scoreOf(m.title, q, recent, `meeting:${m.id}`, `${m.date} ${m.startTime}`),
      },
    ]
  })
}

const searchReports = (c: Corpus, q: ParsedQuery, recent: Set<string>): SearchHit[] => {
  if (q.tags.length || q.people.length || q.states.length) return []
  return c.reports.flatMap((r) => {
    if (!inRange(r.week, q)) return []
    if (!hasAll(r.content, q.terms)) return []
    const title = `周报 · 第 ${weekNumberOf(r.week)} 周`
    const body = r.content.split('\n').slice(1).join(' ')
    return [
      {
        kind: 'report' as const,
        id: r.week,
        title,
        sub: `${monthDay(r.week)} – ${monthDay(addDays(r.week, 6))}`,
        snippet: q.terms.length ? snippetOf(body.replace(/^#+\s*/gm, ''), q.terms) : undefined,
        meta: relativeDay(ymdOf(r.updateTime)),
        to: `/review/report?week=${r.week}`,
        score: scoreOf(title, q, recent, `report:${r.week}`, r.updateTime),
      },
    ]
  })
}

const FAV_COLORS = ['#4f46e5', '#0d9488', '#d97706', '#db2777', '#2563eb', '#65a30d', '#c71a36', '#7c3aed']
export const favColor = (text: string) => FAV_COLORS[[...text].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % FAV_COLORS.length]!

const searchBookmarks = async (q: ParsedQuery, recent: Set<string>): Promise<SearchHit[]> => {
  if (q.states.length || q.people.length) return []
  // 书签在独立服务里：用最长的一个词让后端先筛，其余条件在这里补
  const keyword = [...q.terms].sort((a, b) => b.length - a.length)[0]
  let records: Bookmark[] = []
  try {
    const page = await fetchBookmarks({ pageNum: 1, pageSize: 40, keyword, status: 0 })
    records = page?.records ?? []
  } catch {
    return []
  }
  const space = useSpaceStore(pinia)
  return records.flatMap((b) => {
    const tags = (b.tags ?? []).map((t) => t.name.toLowerCase())
    if (q.tags.length && !q.tags.every((tag) => tags.some((x) => x.includes(tag)))) return []
    if (!inRange(b.createTime ? ymdOf(b.createTime) : null, q)) return []
    if (!hasAll(`${b.title} ${b.url} ${b.description ?? ''} ${tags.join(' ')}`, q.terms)) return []
    const domain = b.domain || b.url.replace(/^https?:\/\//, '').split('/')[0]!
    const where = space.flat.length ? space.folderPath(b.folderId) : ''
    return [
      {
        kind: 'bookmark' as const,
        id: String(b.id),
        title: b.title || b.url,
        sub: [domain, where].filter(Boolean).join(' · '),
        snippet: q.terms.length && !hasAll(b.title, q.terms) && b.description && hasAny(b.description, q.terms) ? snippetOf(b.description, q.terms) : undefined,
        meta: '',
        to: `/bookmarks?open=${b.id}`,
        url: b.url,
        color: favColor(domain),
        score: scoreOf(b.title, q, recent, `bookmark:${b.id}`, b.updateTime ?? b.createTime),
      },
    ]
  })
}

const mock: typeof real = {
  search: async (input) => {
    const q = parseSearch(input)
    if (isEmptyQuery(q)) return []
    const today = todayYmd()
    const recent = new Set(recentItems().map((r) => `${r.kind}:${r.id}`))
    const want = (kind: SearchKind) => !q.kind || q.kind === kind
    const [c, bookmarks] = await Promise.all([loadCorpus(), want('bookmark') ? searchBookmarks(q, recent) : Promise.resolve([])])
    return [
      ...(want('task') ? searchTasks(c, q, recent, today) : []),
      ...(want('note') ? searchNotes(c, q, recent) : []),
      ...bookmarks,
      ...(want('meeting') ? searchMeetings(c, q, recent, today) : []),
      ...(want('report') ? searchReports(c, q, recent) : []),
    ].sort((a, b) => b.score - a.score)
  },
}

export const searchAll = useMockFor('search') ? mock.search : real.search
