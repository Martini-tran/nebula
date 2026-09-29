/**
 * 全局搜索，查询语法见 utils/searchQuery.ts。
 *
 * 分两步：先召回候选，再在这里按下面各 searchXxx 的规则精确过滤、打分、排序。
 * - 接通后端：GET /space/me/search?q= 把输入原样传过去，服务端解析同一套语法，在库里按「只会多不会少」的条件
 *   筛出各模块的候选（最近的在前截断；稍后读能搜到存档正文，命中的段落放在 content 里）
 * - 本地演示（VITE_REAL_MODULES 不含 search）：取各模块全部数据当候选，书签用最长的词让书签接口先筛
 * 排序：标题命中 > 正文命中 > 最近打开 / 最近更新。「最近打开」只有浏览器知道，所以打分留在前端。
 */
import { get } from '../utils/request'
import { useMockFor } from './mock'
import { fetchTaskLists, fetchTasks } from './tasks'
import { fetchNotes } from './notes'
import { fetchMeetings } from './meetings'
import { fetchReports } from './reviews'
import { fetchBookmarks } from './space'
import { fetchHighlights, fetchReadingItems } from './reading'
import { fetchPeople } from './people'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'
import { useSpaceStore } from '../stores/space'
import { useSettingsStore } from '../stores/settings'
import { diffDays, monthDay, relativeDay, todayYmd, weekdayLabel, weekNumberOf, ymdOf, addDays } from '../utils/date'
import { firstLine, plainText } from '../utils/markdown'
import { parseMeetingItems } from '../utils/meetingItems'
import { recentItems } from '../utils/recent'
import { hasAll, hasAny, inRange, isEmptyQuery, parseSearch, snippetOf, type ParsedQuery, type SearchKind } from '../utils/searchQuery'
import type { Task, TaskList } from '../types/tasks'
import type { Note } from '../types/notes'
import type { Meeting } from '../types/meetings'
import type { Report } from '../types/reviews'
import type { Bookmark } from '../types/space'
import type { Highlight, ReadingItem } from '../types/reading'
import type { Person } from '../types/people'
import { namesOf } from '../views/people/personData'

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

interface Corpus {
  at: number
  tasks: Task[]
  lists: TaskList[]
  notes: Note[]
  meetings: Meeting[]
  reports: Report[]
  reading: ReadingItem[]
  highlights: Highlight[]
  people: Person[]
  bookmarks: Bookmark[]
}

/** 服务端召回的候选，各模块与列表接口同样的结构 */
const fetchCandidates = (q: string) => get<Omit<Corpus, 'at'>>('/space/me/search', { params: { q } })

// ── 本地演示：各模块全部数据当候选 ──

let corpus: Omit<Corpus, 'bookmarks'> | null = null
/** 同一次打开里连续输入不重复拉数据；20 秒后或调用 resetSearchCache 后重新取 */
const loadCorpus = async (): Promise<Omit<Corpus, 'bookmarks'>> => {
  if (corpus && Date.now() - corpus.at < 20_000) return corpus
  const settle = async <T>(p: Promise<T>, fallback: T) => p.catch(() => fallback)
  const [tasks, lists, notes, meetings, reports, reading, readingArchived, highlights, people] = await Promise.all([
    settle(fetchTasks({ view: 'all' }), [] as Task[]),
    settle(fetchTaskLists(), [] as TaskList[]),
    settle(fetchNotes({ view: 'all' }), [] as Note[]),
    settle(fetchMeetings(), [] as Meeting[]),
    settle(fetchReports(), [] as Report[]),
    settle(fetchReadingItems(), [] as ReadingItem[]),
    settle(fetchReadingItems({ archived: true }), [] as ReadingItem[]),
    settle(fetchHighlights(), [] as Highlight[]),
    useSettingsStore(pinia).isEnabled('people') ? settle(fetchPeople(), [] as Person[]) : Promise.resolve([] as Person[]),
  ])
  // 归档的笔记也要能搜到
  const archived = await settle(fetchNotes({ view: 'archived' }), [] as Note[])
  const seen = new Set(notes.map((n) => String(n.id)))
  corpus = {
    at: Date.now(),
    tasks,
    lists,
    notes: [...notes, ...archived.filter((n) => !seen.has(String(n.id)))],
    meetings,
    reports,
    reading: [...reading, ...readingArchived],
    highlights,
    people,
  }
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
    if (!inRange(r.date, q)) return []
    if (!hasAll(r.content, q.terms)) return []
    const title = r.type === 'day' ? `日报 · ${monthDay(r.date)}` : `周报 · 第 ${weekNumberOf(r.date)} 周`
    const body = r.content.replace(/^#+\s*/gm, '').split('\n').join(' ')
    return [
      {
        kind: 'report' as const,
        id: `${r.type}:${r.date}`,
        title,
        sub: r.type === 'day' ? weekdayLabel(r.date) : `${monthDay(r.date)} – ${monthDay(addDays(r.date, 6))}`,
        snippet: q.terms.length ? snippetOf(body, q.terms) : undefined,
        meta: relativeDay(ymdOf(r.updateTime)),
        to: `/review/report?type=${r.type}&date=${r.date}`,
        score: scoreOf(title, q, recent, `report:${r.type}:${r.date}`, r.updateTime),
      },
    ]
  })
}

const READ_STATE = { unread: '未读', reading: '在读', done: '读完' } as const

/** 稍后读：文章按标题、摘要、正文；划线按原文与批注，打开时定位到那条划线 */
const searchReading = (c: Corpus, q: ParsedQuery, recent: Set<string>): SearchHit[] => {
  if (q.tags.length || q.people.length) return []
  const stateOk = (item: ReadingItem) =>
    !q.states.length || q.states.some((s) => (s === 'done' ? item.status === 'done' : s === 'open' ? item.status !== 'done' : false))
  const articles = c.reading.flatMap((item) => {
    if (!stateOk(item) || !inRange(ymdOf(item.addTime), q)) return []
    const body = [item.excerpt, ...(item.content ?? [])].join(' ')
    if (!hasAll(`${item.title} ${item.url} ${body}`, q.terms)) return []
    return [
      {
        kind: 'reading' as const,
        id: String(item.id),
        title: item.title,
        sub: [item.domain, item.archived ? '已归档' : READ_STATE[item.status], item.minutes ? `${item.minutes} 分钟` : ''].filter(Boolean).join(' · '),
        snippet: q.terms.length && !hasAll(item.title, q.terms) && hasAny(body, q.terms) ? snippetOf(body, q.terms) : undefined,
        meta: relativeDay(ymdOf(item.addTime)),
        to: `/reading/${item.id}`,
        url: item.url,
        color: favColor(item.domain),
        score: scoreOf(item.title, q, recent, `reading:${item.id}`, item.lastReadTime ?? item.addTime),
      },
    ]
  })
  if (q.states.length || !q.terms.length) return articles
  const marks = c.highlights.flatMap((h) => {
    if (!inRange(ymdOf(h.createTime), q) || !hasAll(`${h.text} ${h.note}`, q.terms)) return []
    const item = c.reading.find((i) => String(i.id) === String(h.itemId))
    return [
      {
        kind: 'reading' as const,
        id: `hl:${h.id}`,
        title: `“${h.text.length > 40 ? `${h.text.slice(0, 40)}…` : h.text}”`,
        sub: `划线 · ${item?.title ?? '已删除的文章'}`,
        snippet: h.note ? snippetOf(h.note, q.terms) : undefined,
        meta: relativeDay(ymdOf(h.createTime)),
        to: `/reading/${h.itemId}?hl=${h.id}`,
        score: scoreOf(h.text, q, recent, `reading:hl:${h.id}`, h.createTime) - 0.5,
      },
    ]
  })
  return [...articles, ...marks]
}

/** 人物卡：按姓名、称呼、其他叫法、分组和手记的信息；@张工 也会找到张工这张卡 */
const searchPeople = (c: Corpus, q: ParsedQuery, recent: Set<string>): SearchHit[] => {
  if (q.states.length || q.after || q.before) return []
  return c.people.flatMap((p) => {
    const names = namesOf(p).map((n) => n.toLowerCase())
    if (q.people.length && !q.people.every((who) => names.some((n) => n.includes(who)))) return []
    if (q.tags.length && !q.tags.every((tag) => p.group.toLowerCase().includes(tag))) return []
    const head = `${p.name} ${p.alias} ${p.extraNames.join(' ')} ${p.group}`
    const body = [p.intro, p.memo, ...p.facts.map((f) => `${f.label}：${f.value}`)].join(' ')
    if (!hasAll(`${head} ${body}`, q.terms)) return []
    const title = p.alias && p.alias !== p.name ? `${p.alias} · ${p.name}` : p.name
    return [
      {
        kind: 'person' as const,
        id: String(p.id),
        title,
        sub: [p.group, p.intro].filter(Boolean).join(' · '),
        snippet: q.terms.length && !hasAll(head, q.terms) && hasAny(body, q.terms) ? snippetOf(body, q.terms) : undefined,
        meta: '',
        to: `/people?id=${p.id}`,
        color: p.color,
        score: scoreOf(head, q, recent, `person:${p.id}`) + (q.people.length ? 3 : 0),
      },
    ]
  })
}

const FAV_COLORS = ['#4f46e5', '#0d9488', '#d97706', '#db2777', '#2563eb', '#65a30d', '#c71a36', '#7c3aed']
export const favColor = (text: string) => FAV_COLORS[[...text].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % FAV_COLORS.length]!

/** 本地演示时书签不全量取：用最长的一个词让书签接口先筛，其余条件在 searchBookmarks 里补 */
const localBookmarks = async (q: ParsedQuery): Promise<Bookmark[]> => {
  if (q.states.length || q.people.length) return []
  const keyword = [...q.terms].sort((a, b) => b.length - a.length)[0]
  try {
    const page = await fetchBookmarks({ pageNum: 1, pageSize: 40, keyword, status: 0 })
    return page?.records ?? []
  } catch {
    return []
  }
}

const searchBookmarks = (c: Corpus, q: ParsedQuery, recent: Set<string>): SearchHit[] => {
  if (q.states.length || q.people.length) return []
  const space = useSpaceStore(pinia)
  return c.bookmarks.flatMap((b) => {
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

const loadCandidates = async (input: string, q: ParsedQuery, withBookmarks: boolean): Promise<Corpus> => {
  if (!useMockFor('search')) return { at: Date.now(), ...(await fetchCandidates(input)) }
  const [c, bookmarks] = await Promise.all([loadCorpus(), withBookmarks ? localBookmarks(q) : Promise.resolve([])])
  return { ...c, bookmarks }
}

export const searchAll = async (input: string): Promise<SearchHit[]> => {
  const q = parseSearch(input)
  if (isEmptyQuery(q)) return []
  const today = todayYmd()
  const recent = new Set(recentItems().map((r) => `${r.kind}:${r.id}`))
  const want = (kind: SearchKind) => !q.kind || q.kind === kind
  const c = await loadCandidates(input, q, want('bookmark'))
  // @张工：人物卡里登记了张工的其他叫法（张立、立哥）时，任务和会议里写成那些名字的也算
  const namesFor = (who: string) => {
    const person = c.people.find((p) => namesOf(p).some((n) => n.toLowerCase() === who))
    return person ? [who, ...namesOf(person).map((n) => n.toLowerCase())] : [who]
  }
  const aliasQuery = { ...q, people: [...new Set(q.people.flatMap(namesFor))] }
  return [
    ...(want('task') ? searchTasks(c, aliasQuery, recent, today) : []),
    ...(want('note') ? searchNotes(c, q, recent) : []),
    ...(want('bookmark') ? searchBookmarks(c, q, recent) : []),
    ...(want('meeting') ? searchMeetings(c, aliasQuery, recent, today) : []),
    ...(want('reading') ? searchReading(c, q, recent) : []),
    ...(want('report') ? searchReports(c, q, recent) : []),
    ...(want('person') ? searchPeople(c, q, recent) : []),
  ].sort((a, b) => b.score - a.score)
}
