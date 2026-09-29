/**
 * 周回顾的数据：全部来自已有模块，不需要手填。取这周和上一周（算对比）的任务、会议、专注、习惯、随手记，
 * 再按周算出五个数、完成清单、决议、没做完的、七天状态、留下来的笔记。
 * 一次请求 GET /space/me/review/weekly 取齐（见 api/overview.ts），统计都在这里算。
 */
import { fetchWeekReview } from '../../api/overview'
import { addDays, diffDays, todayYmd, ymdOf } from '../../utils/date'
import { isDone, isScheduled } from '../../utils/habitStats'
import { parseMeetingItems, type MeetingAction } from '../../utils/meetingItems'
import { firstLine } from '../../utils/markdown'
import { MOOD_RE } from '../calendar/useCalendarData'
import { planLines } from './reportDraft'
import type { Task, TaskList } from '../../types/tasks'
import type { Meeting } from '../../types/meetings'
import type { FocusSession } from '../../types/focus'
import type { Habit, HabitLog } from '../../types/habits'
import type { Note } from '../../types/notes'
import type { Report } from '../../types/reviews'

export interface WeekSource {
  start: string
  end: string
  prevStart: string
  today: string
  tasks: Task[]
  lists: TaskList[]
  meetings: Meeting[]
  sessions: FocusSession[]
  habits: Habit[]
  logs: HabitLog[]
  notes: Note[]
  /** 上周保存的周报（本周回顾里对照「上周计划」） */
  lastReport: Report | null
}

export const loadWeek = async (start: string): Promise<WeekSource> => {
  const end = addDays(start, 6)
  const prevStart = addDays(start, -7)
  const data = await fetchWeekReview(start)
  return { start, end, prevStart, today: todayYmd(), ...data }
}

const within = (ymd: string | null | undefined, from: string, to: string) => Boolean(ymd) && ymd! >= from && ymd! <= to
const doneDay = (t: Task) => (t.doneTime ? ymdOf(t.doneTime) : null)

/** 某一周（从 from 开始的 7 天）的原始统计，本周和上周各算一次再比较 */
const measure = (src: WeekSource, from: string) => {
  const to = addDays(from, 6)
  const today = src.today
  const done = src.tasks.filter((t) => t.done && within(doneDay(t), from, to))
  const meetings = src.meetings.filter((m) => within(m.date, from, to))
  const focusMin = src.sessions.filter((s) => within(s.startedAt.slice(0, 10), from, to)).reduce((sum, s) => sum + s.actualMin, 0)

  // 逾期：本周看现在，过去的周看那周结束时
  const cutoff = to >= today ? today : addDays(to, 1)
  const asOf = to >= today ? today : to
  const overdue = src.tasks.filter((t) => t.dueDate && t.dueDate < cutoff && (!t.done || (doneDay(t) ?? '') > asOf))

  // 习惯完成率：安排了的日子里做到了几天；每周 N 次型按 N 次算（本周没过完时按过去的天数折算）
  const last = to < today ? to : today
  const elapsed = diffDays(from, last) + 1
  let hit = 0
  let expected = 0
  const perHabit: { habit: Habit; done: number; expected: number }[] = []
  for (const h of src.habits) {
    if (h.archived || h.createTime.slice(0, 10) > last || elapsed <= 0) continue
    const logs = src.logs.filter((l) => String(l.habitId) === String(h.id) && within(l.date, from, last))
    let d = 0
    let e = 0
    if (h.freq.type === 'weekly_n') {
      e = Math.min(h.freq.n, Math.ceil((h.freq.n * elapsed) / 7))
      d = Math.min(e, logs.filter((l) => isDone(h, l)).length)
    } else {
      for (let day = from; day <= last; day = addDays(day, 1)) {
        if (!isScheduled(h, day) || day < h.createTime.slice(0, 10)) continue
        // 今天还没打卡的不算没做到
        const ok = isDone(h, logs.find((l) => l.date === day))
        if (day === today && !ok) continue
        e += 1
        if (ok) d += 1
      }
    }
    hit += d
    expected += e
    perHabit.push({ habit: h, done: d, expected: e })
  }

  return { done, meetings, focusMin, overdue, habitRate: expected ? hit / expected : null, perHabit }
}

export type WeekMeasure = ReturnType<typeof measure>

export interface DoneGroup {
  key: string
  name: string
  color: string
  tasks: Task[]
}

export interface Decision {
  key: string
  text: string
  meeting: Meeting
}

export interface WaitingAction {
  key: string
  action: MeetingAction
  meeting: Meeting
}

export const summarize = (src: WeekSource, myNames: string[]) => {
  const cur = measure(src, src.start)
  const prev = measure(src, src.prevStart)
  const listOf = (t: Task) => src.lists.find((l) => String(l.id) === String(t.listId))

  // 完成了什么：按清单分组，清单顺序跟任务模块一致，无清单放最后
  const groups: DoneGroup[] = [
    ...src.lists.map((l) => ({ key: String(l.id), name: l.name, color: l.color, tasks: cur.done.filter((t) => String(t.listId) === String(l.id)) })),
    { key: 'none', name: '未分清单', color: '#94a3b8', tasks: cur.done.filter((t) => !listOf(t)) },
  ]
    .map((g) => ({ ...g, tasks: [...g.tasks].sort((a, b) => String(b.doneTime).localeCompare(String(a.doneTime))) }))
    .filter((g) => g.tasks.length)

  const focusByTask = new Map<string, number>()
  src.sessions
    .filter((s) => within(s.startedAt.slice(0, 10), src.start, src.end) && s.taskId !== null)
    .forEach((s) => focusByTask.set(String(s.taskId), (focusByTask.get(String(s.taskId)) ?? 0) + s.actualMin))

  // 决议与会上的待办
  const decisions: Decision[] = []
  const myActions: WaitingAction[] = []
  const waiting: WaitingAction[] = []
  for (const m of [...cur.meetings].sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`))) {
    const items = parseMeetingItems(m.content, myNames, m.date)
    const lines = m.content.split('\n')
    items.decisions.forEach((d) => decisions.push({ key: `dec:${m.id}:${d.line}`, text: d.text, meeting: m }))
    items.actions
      .filter((a) => !/\[[xX]\]/.test(lines[a.line] ?? ''))
      .forEach((a) => (a.mine ? myActions : waiting).push({ key: `act:${m.id}:${a.line}`, action: a, meeting: m }))
  }

  // 没做完的：到这周结束为止该做却没做的
  const leftover = src.tasks
    .filter((t) => !t.done && t.dueDate && t.dueDate <= src.end)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!) || b.priority - a.priority)

  // 七天：心情（来自日记）与专注时长
  const journals = src.notes.filter((n) => n.tags.includes('日记'))
  const days = Array.from({ length: 7 }, (_, i) => {
    const date = addDays(src.start, i)
    const journal = journals.find((n) => ymdOf(n.createTime) === date)
    return {
      date,
      future: date > src.today,
      mood: journal?.content.split('\n')[0]?.match(MOOD_RE)?.[1] ?? '',
      focusMin: src.sessions.filter((s) => s.startedAt.slice(0, 10) === date).reduce((sum, s) => sum + s.actualMin, 0),
    }
  })

  // 笔记：这周记的，留下（长期）了几条、归档了几条
  const weekNotes = src.notes.filter((n) => !n.tags.includes('日记') && within(ymdOf(n.createTime), src.start, src.end))
  const kept = weekNotes.filter((n) => !n.archived && (n.pinned || !n.expireDate))
  const notes = {
    total: weekNotes.length,
    kept: kept.length,
    archived: weekNotes.filter((n) => n.archived).length,
    list: kept.slice(0, 5).map((n) => ({ id: n.id, title: firstLine(n.content) || '（空笔记）', tags: n.tags })),
  }

  // 上周周报「计划」标题下写的条目，这周做到了没有：按任务标题认（「标题（备注）」也算）
  const lastPlan = planLines(src.lastReport?.content ?? '').map((text) => {
    const task = src.tasks.find((t) => text === t.title || text.startsWith(`${t.title}（`))
    return { text, done: task ? task.done : null, taskId: task?.id }
  })

  return { cur, prev, groups, focusByTask, decisions, myActions, waiting, leftover, days, notes, lastPlan, listOf }
}

export type WeekSummary = ReturnType<typeof summarize>
