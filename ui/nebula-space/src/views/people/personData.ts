/**
 * 人物卡的汇总：按名字（姓名、称呼、其他叫法）把会议、随手记、任务里的提及收拢到一个人名下。
 * - 往来时间线：参加过的会、提到他的随手记、提到他的已完成任务、手记的联系
 * - 他答应我的：会议待办里负责人是他、还没勾掉的；加上手记的
 * - 我答应他的：只有我俩的会（1:1）里我领的待办、或者待办里写了他的名字；加上手记的
 */
import { parseMeetingItems } from '../../utils/meetingItems'
import { diffDays, ymdOf } from '../../utils/date'
import { firstLine } from '../../utils/markdown'
import type { Meeting } from '../../types/meetings'
import type { Note } from '../../types/notes'
import type { Task } from '../../types/tasks'
import type { Person } from '../../types/people'

export interface PeopleSource {
  meetings: Meeting[]
  notes: Note[]
  tasks: Task[]
  myNames: string[]
}

export interface TimelineEvent {
  date: string
  kind: 'meeting' | 'note' | 'task' | 'contact' | 'upcoming'
  title: string
  detail: string
  to?: string
}

export interface Commitment {
  key: string
  text: string
  due: string | null
  /** 来自哪：会议名或「手记」 */
  from: string
  to?: string
  /** 手记的承诺 id，可以勾掉 */
  promiseId?: string
  taskId?: string
}

/** 这个人会被叫的名字；单字太容易误配，不算 */
export const namesOf = (p: Person) => [...new Set([p.name, p.alias, ...p.extraNames].map((n) => n.trim()).filter((n) => n.length >= 2))]

const attended = (m: Meeting, names: string[]) => m.attendees.some((a) => !a.me && names.includes(a.name.trim()))
const mentions = (text: string, names: string[]) => names.some((n) => text.includes(n))
const DONE_RE = /\[[xX]\]/

export const timelineOf = (p: Person, src: PeopleSource, today: string): TimelineEvent[] => {
  const names = namesOf(p)
  const out: TimelineEvent[] = []
  for (const m of src.meetings) {
    if (!attended(m, names)) continue
    if (m.date > today) {
      out.push({ date: m.date, kind: 'upcoming', title: m.title, detail: `${m.startTime} · 即将开`, to: `/meetings/${m.id}` })
      continue
    }
    const items = parseMeetingItems(m.content, src.myNames, m.date)
    const his = items.actions.filter((a) => names.includes(a.owner))
    const decisions = items.decisions
    const detail = his.length
      ? `他领了：${his.map((a) => a.text).join('；')}`
      : decisions.length
        ? `决议：${decisions[0]!.text}${decisions.length > 1 ? ` 等 ${decisions.length} 条` : ''}`
        : m.status === 'done'
          ? '参会'
          : '已安排'
    out.push({ date: m.date, kind: 'meeting', title: m.title, detail, to: `/meetings/${m.id}` })
  }
  for (const n of src.notes) {
    if (!mentions(n.content, names)) continue
    out.push({ date: ymdOf(n.createTime), kind: 'note', title: '随手记', detail: `「${firstLine(n.content).slice(0, 60)}」`, to: `/notes/${n.id}` })
  }
  for (const t of src.tasks) {
    if (!t.done || !t.doneTime) continue
    if (!(mentions(`${t.title} ${t.note}`, names) || (t.source?.type === 'person' && String(t.source.id) === String(p.id)))) continue
    out.push({ date: ymdOf(t.doneTime), kind: 'task', title: `任务完成：${t.title}`, detail: '', to: `/tasks?v=all&task=${t.id}` })
  }
  for (const c of p.contacts) out.push({ date: c.date, kind: 'contact', title: '联系', detail: c.note })
  return out.sort((a, b) => b.date.localeCompare(a.date) || (a.kind === 'upcoming' ? -1 : 1))
}

/** 最近一次往来（不算还没开的会） */
export const lastInteraction = (p: Person, src: PeopleSource, today: string) =>
  timelineOf(p, src, today).find((e) => e.kind !== 'upcoming' && e.date <= today)?.date ?? null

export const commitmentsOf = (p: Person, src: PeopleSource) => {
  const names = namesOf(p)
  const theirs: Commitment[] = []
  const mine: Commitment[] = []
  for (const m of src.meetings) {
    if (!attended(m, names)) continue
    const lines = m.content.split('\n')
    const others = m.attendees.filter((a) => !a.me)
    const oneOnOne = others.length === 1
    for (const a of parseMeetingItems(m.content, src.myNames, m.date).actions) {
      if (DONE_RE.test(lines[a.line] ?? '')) continue
      if (names.includes(a.owner)) {
        theirs.push({ key: `m${m.id}:${a.line}`, text: a.text, due: a.due, from: m.title, to: `/meetings/${m.id}` })
      } else if (a.mine && (oneOnOne || mentions(a.text, names))) {
        // 已经同步成任务并且做完了的不算
        const taskId = m.syncedTasks[a.text]
        const task = taskId !== undefined ? src.tasks.find((t) => String(t.id) === String(taskId)) : src.tasks.find((t) => t.title === a.text && t.source?.type === 'meeting' && String(t.source.id) === String(m.id))
        if (task?.done) continue
        mine.push({ key: `m${m.id}:${a.line}`, text: a.text, due: task?.dueDate ?? a.due, from: m.title, to: task ? `/tasks?v=all&task=${task.id}` : `/meetings/${m.id}`, taskId: task ? String(task.id) : undefined })
      }
    }
  }
  for (const pr of p.promises.filter((x) => !x.done)) {
    ;(pr.who === 'them' ? theirs : mine).push({ key: pr.id, text: pr.text, due: pr.due, from: '手记', promiseId: pr.id })
  }
  const byDue = (a: Commitment, b: Commitment) => String(a.due ?? '9999').localeCompare(String(b.due ?? '9999'))
  return { theirs: theirs.sort(byDue), mine: mine.sort(byDue) }
}

/** 离今天的天数说法：已过 2 天 / 今天 / 3 天后 */
export const dueText = (due: string | null, today: string) => {
  if (!due) return ''
  const d = diffDays(today, due)
  if (d < 0) return `已过 ${-d} 天`
  if (d === 0) return '今天到期'
  return `${d} 天后`
}

/** 下一次生日还有几天 */
export const birthdayIn = (p: Person, today: string) => {
  if (!p.birthday) return null
  const y = Number(today.slice(0, 4))
  let next = `${y}-${p.birthday}`
  if (next < today) next = `${y + 1}-${p.birthday}`
  return diffDays(today, next)
}
