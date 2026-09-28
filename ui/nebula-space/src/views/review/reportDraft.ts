/**
 * 日报周报的草稿：正文是自由的 Markdown，不分模板。这里只负责「插入记录」按钮要插的那段文字：
 * 从任务、会议、专注里按固定规则拼出来，不调用模型；插进去之后就是普通文字，随便改。
 * 周报还可以按日报汇总：把这周每天的日报按日期依次拼起来。
 */
import { fetchTasks } from '../../api/tasks'
import { fetchMeetings } from '../../api/meetings'
import { fetchFocusSessions } from '../../api/focus'
import { addDays, diffDays, monthDay, weekdayLabel, weekNumberOf, ymdOf } from '../../utils/date'
import { formatMinutes } from '../../utils/format'
import { parseMeetingItems } from '../../utils/meetingItems'
import type { WeekSource, WeekSummary } from './weekData'
import type { Report, ReportType } from '../../types/reviews'
import type { Task } from '../../types/tasks'
import type { Meeting } from '../../types/meetings'

export const reportTitle = (type: ReportType, date: string) => {
  if (type === 'day') return `日报 · ${monthDay(date)} ${weekdayLabel(date)}`
  const md = (d: string) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`
  return `周报 · 第 ${weekNumberOf(date)} 周（${md(date)} – ${md(addDays(date, 6))}）`
}

/** 有内容的段落拼成 Markdown；空段不出现 */
const toMarkdown = (sections: { title: string; ordered: boolean; lines: string[] }[]) =>
  sections
    .filter((s) => s.lines.length)
    .map((s) => `### ${s.title}\n${s.lines.map((l, i) => `${s.ordered ? `${i + 1}.` : '-'} ${l}`).join('\n')}`)
    .join('\n\n')

/** 会上我领的、还没转成任务的待办（记在 syncedTasks，或者同一场会来源、同名的任务都算已转） */
const unsyncedMine = (m: Meeting, text: string, tasks: Task[]) =>
  m.syncedTasks[text] === undefined && !tasks.some((t) => t.title === text && t.source?.type === 'meeting' && String(t.source.id) === String(m.id))

// ── 日报 ──

export interface DaySource {
  date: string
  tasks: Task[]
  meetings: Meeting[]
  focusByTask: Map<string, number>
  focusMin: number
}

export const loadDay = async (date: string): Promise<DaySource> => {
  const [tasks, meetings, sessions] = await Promise.all([
    fetchTasks({ view: 'all' }),
    fetchMeetings({ from: date, to: date }),
    fetchFocusSessions({ from: date, to: date }),
  ])
  const focusByTask = new Map<string, number>()
  sessions.filter((s) => s.taskId !== null).forEach((s) => focusByTask.set(String(s.taskId), (focusByTask.get(String(s.taskId)) ?? 0) + s.actualMin))
  return { date, tasks, meetings, focusByTask, focusMin: sessions.reduce((sum, s) => sum + s.actualMin, 0) }
}

/** 当天完成的任务与会议决议、下一天到期的任务与会上领的待办、到当天为止没做完的 */
export const dayDraft = (src: DaySource, myNames: string[]) => {
  const next = addDays(src.date, 1)
  const done = src.tasks
    .filter((t) => t.done && t.doneTime && ymdOf(t.doneTime) === src.date)
    .sort((a, b) => String(a.doneTime).localeCompare(String(b.doneTime)))
    .map((t) => {
      const min = src.focusByTask.get(String(t.id))
      return `${t.title}${min ? `（专注 ${formatMinutes(min)}）` : ''}`
    })
  const meetingLines: string[] = []
  const actions: string[] = []
  for (const m of [...src.meetings].sort((a, b) => a.startTime.localeCompare(b.startTime))) {
    const items = parseMeetingItems(m.content, myNames, m.date)
    const lines = m.content.split('\n')
    items.decisions.forEach((d) => meetingLines.push(`${m.title}：${d.text}`))
    items.actions
      .filter((a) => a.mine && !/\[[xX]\]/.test(lines[a.line] ?? '') && unsyncedMine(m, a.text, src.tasks))
      .forEach((a) => actions.push(`${a.text}（${m.title}）`))
  }
  const plan = src.tasks
    .filter((t) => !t.done && t.dueDate === next)
    .sort((a, b) => b.priority - a.priority)
    .map((t) => t.title)
  const left = src.tasks
    .filter((t) => !t.done && t.dueDate && t.dueDate <= src.date)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))
    .map((t) => (t.dueDate! < src.date ? `${t.title}（逾期 ${diffDays(t.dueDate!, src.date)} 天）` : t.title))
  return toMarkdown([
    { title: '完成', ordered: true, lines: [...done, ...(src.focusMin ? [`专注共 ${formatMinutes(src.focusMin)}`] : [])] },
    { title: '会议', ordered: false, lines: meetingLines },
    { title: `${weekdayLabel(next)}计划`, ordered: true, lines: [...plan, ...actions] },
    { title: '没做完', ordered: false, lines: left },
  ])
}

// ── 周报 ──

/** 这周完成的任务与会议决议、下周要做的、逾期与在等别人的 */
export const weekDraft = (src: WeekSource, sum: WeekSummary) => {
  const nextStart = addDays(src.end, 1)
  const nextEnd = addDays(nextStart, 6)
  const upcomingFrom = src.today > src.end ? nextStart : src.today
  const done = [...sum.cur.done]
    .sort((a, b) => String(a.doneTime).localeCompare(String(b.doneTime)))
    .map((t) => {
      const min = sum.focusByTask.get(String(t.id))
      return `${t.title}${min ? `（专注 ${formatMinutes(min)}）` : ''}`
    })
  const plan = src.tasks
    .filter((t) => !t.done && t.dueDate && t.dueDate >= upcomingFrom && t.dueDate <= nextEnd)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!) || b.priority - a.priority)
    .map((t) => t.title)
  const actions = sum.myActions.filter((a) => unsyncedMine(a.meeting, a.action.text, src.tasks)).map((a) => `${a.action.text}（${a.meeting.title}）`)
  const cutoff = src.today < nextStart ? src.today : nextStart
  const risk = [
    ...src.tasks
      .filter((t) => !t.done && t.dueDate && t.dueDate < cutoff)
      .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))
      .map((t) => `${t.title}（逾期 ${diffDays(t.dueDate!, src.today)} 天）`),
    ...sum.waiting.map((w) => `等 ${w.action.owner}：${w.action.text}${w.action.due ? `（${monthDay(w.action.due)}前）` : ''}`),
  ]
  return toMarkdown([
    { title: '本周完成', ordered: true, lines: [...done, ...(sum.cur.focusMin ? [`本周专注共 ${formatMinutes(sum.cur.focusMin)}`] : [])] },
    { title: '会议决议', ordered: false, lines: sum.decisions.map((d) => `${d.meeting.title}：${d.text}`) },
    { title: '下周计划', ordered: true, lines: [...plan, ...actions] },
    { title: '风险与需要的支持', ordered: false, lines: risk },
  ])
}

/** 标题降三级（# → ####，最多到 ######），嵌进周报的日期小标题下面 */
const demote = (md: string) => md.replace(/^(#{1,6})(\s)/gm, (_, h: string, sp: string) => `${'#'.repeat(Math.min(6, h.length + 3))}${sp}`)

/** 周报按日报汇总：这周写过的日报按日期依次拼，每天一个小标题 */
export const summarizeDailies = (dailies: Report[]) =>
  [...dailies]
    .filter((r) => r.content.trim())
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((r) => `### ${weekdayLabel(r.date)} ${monthDay(r.date)}\n\n${demote(r.content.trim())}`)
    .join('\n\n')

/** 从周报正文里找「计划」标题下的列表项，下周回顾拿来对照做到了没有 */
export const planLines = (md: string) => {
  const out: string[] = []
  let inPlan = false
  for (const line of md.split('\n')) {
    const heading = /^#{1,6}\s+(.*)$/.exec(line)
    if (heading) {
      inPlan = heading[1]!.includes('计划')
      continue
    }
    const item = inPlan ? /^\s*(?:[-*+]|\d+[.)])\s+(?:\[[ xX]\]\s+)?(.+)$/.exec(line) : null
    if (item) out.push(item[1]!.trim())
  }
  return out
}
