/**
 * 日历不是新数据，只是另一种看法：把任务、会议、习惯打卡、日记心情、专注记录按日期范围取来，按天索引。
 * 一次请求 GET /space/me/calendar 取齐（见 api/overview.ts）。
 */
import { ref } from 'vue'
import { fetchCalendar } from '../../api/overview'
import { isDone, isScheduled } from '../../utils/habitStats'
import { todayYmd, ymdOf } from '../../utils/date'
import type { Task } from '../../types/tasks'
import type { Meeting } from '../../types/meetings'
import type { Habit, HabitLog } from '../../types/habits'
import type { Note } from '../../types/notes'
import type { FocusSession } from '../../types/focus'

/** 日记笔记第一行里的心情：「## 9月27日 周日 · 顺」；早先写的「· 🙂 顺」也认，取出的是文字 */
export const MOOD_RE = /·\s*(?:\p{Extended_Pictographic}\uFE0F?\s*)?(累|平|顺|爽)/u

export const useCalendarData = () => {
  const tasks = ref<Task[]>([])
  const meetings = ref<Meeting[]>([])
  const habits = ref<Habit[]>([])
  const logs = ref<HabitLog[]>([])
  const journals = ref<Note[]>([])
  const sessions = ref<FocusSession[]>([])
  const loading = ref(false)
  const error = ref('')

  const load = async (from: string, to: string) => {
    loading.value = true
    error.value = ''
    try {
      const data = await fetchCalendar(from, to, todayYmd())
      tasks.value = data.tasks
      meetings.value = data.meetings
      habits.value = data.habits
      logs.value = data.logs
      journals.value = data.journals
      sessions.value = data.sessions
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  const tasksOn = (date: string) => tasks.value.filter((t) => t.dueDate === date)
  const meetingsOn = (date: string) => meetings.value.filter((m) => m.date === date).sort((a, b) => a.startTime.localeCompare(b.startTime))
  const sessionsOn = (date: string) => sessions.value.filter((s) => s.startedAt.slice(0, 10) === date)
  const journalOn = (date: string) => journals.value.find((n) => ymdOf(n.createTime) === date)
  const moodOn = (date: string) => journalOn(date)?.content.split('\n')[0]?.match(MOOD_RE)?.[1] ?? ''

  /** 当天每个安排了的习惯是否完成（每周 N 次型只在做了的那天出现） */
  const habitsOn = (date: string) =>
    habits.value
      .filter((h) => h.createTime.slice(0, 10) <= date && isScheduled(h, date))
      .map((h) => {
        const log = logs.value.find((l) => String(l.habitId) === String(h.id) && l.date === date)
        return { habit: h, log, done: isDone(h, log) }
      })
      .filter((x) => x.habit.freq.type !== 'weekly_n' || x.log)

  return { tasks, meetings, habits, logs, sessions, loading, error, load, tasksOn, meetingsOn, sessionsOn, journalOn, moodOn, habitsOn }
}

export type CalendarData = ReturnType<typeof useCalendarData>
