/**
 * 周报草稿：按模板从这周的数据拼出来，不调用模型，规则固定可预测。
 * 每条带来源（任务 / 会议 / 专注），key 固定，重新生成时据此保留手改内容、不再加回删掉的条目。
 */
import { addDays, diffDays, monthDay, weekdayLabel, weekNumberOf } from '../../utils/date'
import { formatMinutes } from '../../utils/format'
import type { WeekSource, WeekSummary } from './weekData'
import type { ReportItem, ReportSection, ReportSources, ReportTemplate } from '../../types/reviews'
import type { Task, TaskList } from '../../types/tasks'
import { computeProgress, formatValue, goalAsOf, goalDataAsOf, STATE_LABEL } from '../goals/goalProgress'

export const TEMPLATES: { key: ReportTemplate; label: string; desc: string }[] = [
  { key: 'standard', label: '完成 / 计划 / 风险', desc: '最常用的三段式' },
  { key: 'byList', label: '按清单分组', desc: '每个清单一段，适合多条线并行' },
  { key: 'okr', label: '按年度目标', desc: '每个目标的进度与本周推进' },
  { key: 'done', label: '只列完成项', desc: '给只关心结果的人' },
]

/** 默认只纳入「工作」清单；没有叫「工作」的清单就全纳入 */
export const defaultSources = (lists: TaskList[]): ReportSources => {
  const work = lists.find((l) => l.name === '工作')
  return {
    lists: work ? [String(work.id)] : [...lists.map((l) => String(l.id)), 'none'],
    decisions: true,
    myActions: true,
    waiting: true,
    focus: false,
  }
}

const item = (key: string, text: string, ref: ReportItem['ref']): ReportItem => ({ key, text, auto: text, ref })

export const generate = (src: WeekSource, sum: WeekSummary, sources: ReportSources, template: ReportTemplate): ReportSection[] => {
  const inList = (t: Task) => sources.lists.includes(t.listId === null || t.listId === undefined ? 'none' : String(t.listId))
  const byDone = (a: Task, b: Task) => String(a.doneTime).localeCompare(String(b.doneTime))
  const nextStart = addDays(src.end, 1)
  const nextEnd = addDays(nextStart, 6)
  const upcomingFrom = src.today > src.end ? nextStart : src.today

  const doneItem = (t: Task) => {
    const focus = sources.focus ? sum.focusByTask.get(String(t.id)) : undefined
    return item(`task:${t.id}`, `${t.title}${focus ? `（专注 ${formatMinutes(focus)}）` : ''}`, { type: 'task', id: t.id, label: '任务' })
  }
  const decisionItems = sources.decisions
    ? sum.decisions.map((d) => item(d.key, `${d.meeting.title}：${d.text}`, { type: 'meeting', id: d.meeting.id, label: `会议 · ${weekdayLabel(d.meeting.date)}` }))
    : []
  const focusItems =
    sources.focus && sum.cur.focusMin
      ? [item('focus:total', `本周专注共 ${formatMinutes(sum.cur.focusMin)}`, { type: 'focus', label: '专注' })]
      : []

  // 下周计划：这周还没到期的 + 下周到期的任务，外加会上我领的、还没转成任务的待办
  const planTasks = src.tasks
    .filter((t) => !t.done && inList(t) && t.dueDate && t.dueDate >= upcomingFrom && t.dueDate <= nextEnd)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!) || b.priority - a.priority)
    .map((t) => item(`plan:${t.id}`, t.title, { type: 'task', id: t.id, label: `任务 · ${weekdayLabel(t.dueDate!)}` }))
  const planActions = sources.myActions
    ? sum.myActions
        // 已经转成任务的（记在 syncedTasks，或者同一场会来源、同名的任务）不重复列
        .filter(
          (a) =>
            a.meeting.syncedTasks[a.action.text] === undefined &&
            !src.tasks.some((t) => t.title === a.action.text && t.source?.type === 'meeting' && String(t.source.id) === String(a.meeting.id)),
        )
        .map((a) =>
          item(a.key, a.action.text, { type: 'meeting', id: a.meeting.id, label: `${a.meeting.title}待办${a.action.due ? ` · ${weekdayLabel(a.action.due)}` : ''}` }),
        )
    : []

  // 风险：逾期的任务；别人名下、我在等的待办
  const cutoff = src.today < nextStart ? src.today : nextStart
  const riskTasks = src.tasks
    .filter((t) => !t.done && inList(t) && t.dueDate && t.dueDate < cutoff)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))
    .map((t) => item(`risk:${t.id}`, t.title, { type: 'task', id: t.id, label: `逾期 ${diffDays(t.dueDate!, src.today)} 天` }))
  const riskWaiting = sources.waiting
    ? sum.waiting.map((w) =>
        item(w.key, `等 ${w.action.owner}：${w.action.text}${w.action.due ? `（${monthDay(w.action.due)}前）` : ''}`, {
          type: 'meeting',
          id: w.meeting.id,
          label: w.meeting.title,
        }),
      )
    : []

  const done = sum.cur.done.filter(inList).sort(byDone)
  const plan: ReportSection = { key: 'plan', title: '下周计划', ordered: true, items: [...planTasks, ...planActions] }
  const risk: ReportSection = { key: 'risk', title: '风险与需要的支持', ordered: false, items: [...riskTasks, ...riskWaiting] }

  if (template === 'okr') {
    const { sections, used } = okrSections(src, done)
    const others = [...done.filter((t) => !used.has(String(t.id))).map(doneItem), ...decisionItems, ...focusItems]
    return [...sections, { key: 'done', title: sections.length ? '其他完成' : '本周完成', ordered: true, items: others }, plan, risk]
  }
  if (template === 'done') {
    return [{ key: 'done', title: '本周完成', ordered: true, items: [...done.map(doneItem), ...decisionItems, ...focusItems] }]
  }
  if (template === 'byList') {
    const listSections: ReportSection[] = [
      ...src.lists.map((l) => ({ key: `list:${l.id}`, name: l.name, tasks: done.filter((t) => String(t.listId) === String(l.id)) })),
      { key: 'list:none', name: '其他', tasks: done.filter((t) => t.listId === null || t.listId === undefined) },
    ]
      .filter((s) => s.tasks.length)
      .map((s) => ({ key: s.key, title: s.name, ordered: true, items: s.tasks.map(doneItem) }))
    const extra: ReportSection[] = decisionItems.length || focusItems.length ? [{ key: 'decisions', title: '会议决议与其他', ordered: false, items: [...decisionItems, ...focusItems] }] : []
    return [...listSections, ...extra, plan, risk]
  }
  return [{ key: 'done', title: '本周完成', ordered: true, items: [...done.map(doneItem), ...decisionItems, ...focusItems] }, plan, risk]
}

/**
 * 「按年度目标」：每个目标一段，第一条是截至这周结束的进度和状态，第二条是这周推进了多少（和上周末比），
 * 再列这周完成的关键结果、挂在目标或关键结果上的清单里完成的任务。算进目标的任务不再出现在「其他完成」。
 * 来源是记账结余的目标不列。
 */
const okrSections = (src: WeekSource, done: Task[]) => {
  const used = new Set<string>()
  if (!src.okr) return { sections: [] as ReportSection[], used }
  const asOf = src.end < src.today ? src.end : src.today
  const before = addDays(src.start, -1)
  const now = goalDataAsOf(src.okr.data, asOf)
  const prev = goalDataAsOf(src.okr.data, before)
  const inWeek = (ymd: string | null) => Boolean(ymd) && ymd! >= src.start && ymd! <= src.end
  const goalRef = (label = '目标'): ReportItem['ref'] => ({ type: 'goal', label })
  const tasksIn = (listId: unknown) => {
    const list = done.filter((t) => String(t.listId) === String(listId))
    list.forEach((t) => used.add(String(t.id)))
    return list
  }
  /** 关键结果挂的清单：一行汇总，不逐条展开 */
  const krLine = (goalId: unknown, k: { id: string; title: string; listId: unknown }) => {
    const list = tasksIn(k.listId)
    if (!list.length) return []
    const names = list.slice(0, 3).map((t) => `「${t.title}」`).join('')
    return [item(`kr:${goalId}:${k.id}:week`, `${k.title}：本周完成 ${list.length} 项，${names}${list.length > 3 ? ' 等' : ''}`, goalRef('关键结果'))]
  }

  // 周报是给别人看的：记账结余这类金额目标不写进去（公开主页也不公开）
  const sections = src.okr.goals
    .filter((g) => !(g.kind === 'metric' && g.source === 'ledger'))
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((g): ReportSection => {
      const p = computeProgress(goalAsOf(g, asOf), now, asOf)
      const items: ReportItem[] = []
      if (g.kind === 'milestone') {
        const doneKrs = goalAsOf(g, asOf).krs.filter((k) => k.done).length
        items.push(item(`goal:${g.id}:progress`, `关键结果完成 ${doneKrs} / ${g.krs.length}`, goalRef()))
        for (const k of g.krs) {
          if (k.done && inWeek(k.doneDate)) items.push(item(`kr:${g.id}:${k.id}`, `达成：${k.title}`, goalRef('关键结果')))
          else if (k.listId !== null) items.push(...krLine(g.id, k))
        }
      } else {
        const pct = Math.round(p.pct * 100)
        const target = `${formatValue(p.value, g.unit)} / ${formatValue(g.target, g.unit)}`
        items.push(item(`goal:${g.id}:progress`, `进度 ${pct}%（${target}）· ${STATE_LABEL[p.state]}${p.projection ? `，${p.projection}` : ''}`, goalRef()))
        if (g.source !== 'manual') {
          const delta = p.value - computeProgress(goalAsOf(g, before), prev, before).value
          const text = Math.abs(delta) < 0.05 ? '本周没有新进展' : `本周 ${delta > 0 ? '+' : '-'}${formatValue(Math.abs(delta), g.unit)}`
          items.push(item(`goal:${g.id}:week`, text, goalRef()))
        }
        if (g.source === 'task_list')
          items.push(...tasksIn(g.sourceId).map((t) => item(`task:${t.id}`, t.title, { type: 'task', id: t.id, label: '任务' })))
      }
      return { key: `goal:${g.id}`, title: `${g.icon} ${g.title}`, ordered: false, items }
    })
  return { sections, used }
}

/** 重新生成：新规则结果为底，手改过的沿用手改文字，手动加的条目放回原段落末尾，删掉的不再出现 */
export const mergeDraft = (fresh: ReportSection[], prev: ReportSection[], removed: string[]): ReportSection[] => {
  const gone = new Set(removed)
  const old = new Map(prev.flatMap((s) => s.items.map((i) => [i.key, i] as const)))
  const sections = fresh.map((s) => ({
    ...s,
    items: s.items
      .filter((i) => !gone.has(i.key))
      .map((i) => {
        const was = old.get(i.key)
        return was && was.auto !== null && was.text !== was.auto ? { ...i, text: was.text } : i
      }),
  }))
  for (const s of prev) {
    const manual = s.items.filter((i) => i.auto === null)
    if (!manual.length) continue
    const target = sections.find((x) => x.key === s.key) ?? sections[0]
    target?.items.push(...manual)
  }
  return sections
}

export const isEdited = (i: ReportItem) => i.auto === null || i.text !== i.auto

export const reportTitle = (week: string) => {
  const end = addDays(week, 6)
  const md = (d: string) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`
  return `周报 · 第 ${weekNumberOf(week)} 周（${md(week)} – ${md(end)}）`
}

export const toMarkdown = (week: string, sections: ReportSection[]) =>
  [
    `## ${reportTitle(week)}`,
    ...sections
      .filter((s) => s.items.some((i) => i.text.trim()))
      .map((s) => {
        const lines = s.items.filter((i) => i.text.trim()).map((i, n) => `${s.ordered ? `${n + 1}.` : '-'} ${i.text.trim()}`)
        return `\n### ${s.title}\n${lines.join('\n')}`
      }),
  ].join('\n')
