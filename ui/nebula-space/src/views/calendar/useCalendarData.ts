/**
 * 日历不是新数据，只是另一种看法：把任务、会议、习惯打卡、日记心情、专注记录按日期范围取来，按天索引。
 * 后端就绪后可以换成一个聚合接口 GET /space/calendar?from=&to=（设计稿「后端待补」）。
 */
import { ref } from 'vue'
import { fetchTasks } from '../../api/tasks'
import { fetchMeetings } from '../../api/meetings'
import { fetchHabitLogs, fetchHabits } from '../../api/habits'
import { fetchNotes } from '../../api/notes'
import { fetchFocusSessions } from '../../api/focus'
import { isDone, isScheduled } from '../../utils/habitStats'
import { ymdOf } from '../../utils/date'
import type { Task } from '../../types/tasks'
import type { Meeting } from '../../types/meetings'
import type { Habit, HabitLog } from '../../types/habits'
import type { Note } from '../../types/notes'
import type { FocusSession } from '../../types/focus'

/** 日记笔记第一行里的心情表情：「## 9月27日 周日 · 🙂 顺」 */
const MOOD_RE = /·\s*(\p{Extended_Pictographic})/u

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
      const [t, m, h, l, n, f] = await Promise.all([
        fetchTasks({ view: 'all' }),
        fetchMeetings({ from, to }),
        fetchHabits(),
        fetchHabitLogs({ from, to }),
        fetchNotes({ view: 'all', tag: '日记' }),
        fetchFocusSessions({ from, to }),
      ])
      tasks.value = t
      meetings.value = m
      habits.value = h
      logs.value = l
      journals.value = n
      sessions.value = f
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
