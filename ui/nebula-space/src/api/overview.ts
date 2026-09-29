/**
 * 按天看的聚合接口：「今天」页、日历、周回顾各一次请求取齐几个模块的数据。
 * - GET /space/me/today?date=&weekStart=：当天任务（含过期没做完、本周完成的）、当天会议、当天写的与最近改过的笔记、当天收藏
 * - GET /space/me/calendar?from=&to=&today=：范围内的任务（加上过期没做完的）、会议、习惯与打卡、日记、专注，最多 100 天
 * - GET /space/me/review/weekly?start=：周回顾，这周与上周的会议、专注、打卡，所有没做完的与上周以来完成的任务，这周的笔记，上周的周报
 * 服务端只保证不漏，按天分组、排序仍在页面里做。「今天」由浏览器传过去，和页面分组的口径一致。
 * VITE_REAL_MODULES 不含 today / calendar / reviews 时走下面的 mock：分别调各模块接口拼出同样的结构。
 */
import { get } from '../utils/request'
import { useMockFor } from './mock'
import { fetchTaskLists, fetchTasks } from './tasks'
import { fetchMeetings } from './meetings'
import { fetchNotes } from './notes'
import { fetchHabitLogs, fetchHabits } from './habits'
import { fetchFocusSessions } from './focus'
import { fetchBookmarks } from './space'
import { fetchReport } from './reviews'
import { addDays, ymdOf } from '../utils/date'
import type { Task, TaskList } from '../types/tasks'
import type { Report } from '../types/reviews'
import type { Meeting } from '../types/meetings'
import type { Note } from '../types/notes'
import type { Habit, HabitLog } from '../types/habits'
import type { FocusSession } from '../types/focus'
import type { Bookmark } from '../types/space'

export interface TodayData {
  tasks: Task[]
  meetings: Meeting[]
  /** 最近改过的在前 */
  notes: Note[]
  /** 当天收藏；书签服务取不到（没权限）时为 null，页面不显示这一块 */
  bookmarks: Bookmark[] | null
}

export interface CalendarRange {
  tasks: Task[]
  meetings: Meeting[]
  habits: Habit[]
  logs: HabitLog[]
  /** 带「日记」标签的笔记 */
  journals: Note[]
  sessions: FocusSession[]
}

export interface WeekReviewData {
  tasks: Task[]
  lists: TaskList[]
  meetings: Meeting[]
  sessions: FocusSession[]
  habits: Habit[]
  logs: HabitLog[]
  /** 这一周写的笔记，含归档的 */
  notes: Note[]
  lastReport: Report | null
}

const real = {
  fetchToday: (date: string, weekStart: string) => get<TodayData>('/space/me/today', { params: { date, weekStart } }),
  fetchCalendar: (from: string, to: string, today: string) =>
    get<CalendarRange>('/space/me/calendar', { params: { from, to, today } }),
  fetchWeekReview: (start: string) => get<WeekReviewData>('/space/me/review/weekly', { params: { start } }),
}

const mock: typeof real = {
  fetchToday: async (date) => {
    const [tasks, meetings, notes] = await Promise.all([
      fetchTasks({ view: 'all' }),
      fetchMeetings({ from: date, to: date }),
      fetchNotes({ view: 'all' }),
    ])
    let bookmarks: Bookmark[] | null
    try {
      const page = await fetchBookmarks({ pageNum: 1, pageSize: 20 })
      bookmarks = (page?.records ?? []).filter((b) => b.createTime && ymdOf(b.createTime) === date)
    } catch {
      bookmarks = null
    }
    return { tasks, meetings, notes, bookmarks }
  },
  fetchCalendar: async (from, to) => {
    const [tasks, meetings, habits, logs, journals, sessions] = await Promise.all([
      fetchTasks({ view: 'all' }),
      fetchMeetings({ from, to }),
      fetchHabits(),
      fetchHabitLogs({ from, to }),
      fetchNotes({ view: 'all', tag: '日记' }),
      fetchFocusSessions({ from, to }),
    ])
    return { tasks, meetings, habits, logs, journals, sessions }
  },
  fetchWeekReview: async (start) => {
    const end = addDays(start, 6)
    const prevStart = addDays(start, -7)
    const [tasks, lists, meetings, sessions, habits, logs, notes, archived, lastReport] = await Promise.all([
      fetchTasks({ view: 'all' }),
      fetchTaskLists(),
      fetchMeetings({ from: prevStart, to: end }),
      fetchFocusSessions({ from: prevStart, to: end }),
      fetchHabits(true),
      fetchHabitLogs({ from: prevStart, to: end }),
      fetchNotes({ view: 'all' }),
      fetchNotes({ view: 'archived' }),
      fetchReport('week', prevStart).catch(() => null),
    ])
    return { tasks, lists, meetings, sessions, habits, logs, notes: [...notes, ...archived], lastReport }
  },
}

export const fetchToday = useMockFor('today') ? mock.fetchToday : real.fetchToday
export const fetchCalendar = useMockFor('calendar') ? mock.fetchCalendar : real.fetchCalendar
export const fetchWeekReview = useMockFor('reviews') ? mock.fetchWeekReview : real.fetchWeekReview
