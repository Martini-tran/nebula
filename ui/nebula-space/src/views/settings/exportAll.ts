/**
 * 「导出全部数据」：把各模块的数据取齐，打成一个 JSON 下载。
 * 后端就绪后应改成异步导出任务（复用书签导出任务表的模式），这里是前端拼的过渡做法。
 * 书签另有 Chrome 兼容 HTML 导出，不放进这个文件。
 */
import { fetchTaskLists, fetchTasks } from '../../api/tasks'
import { fetchNotes } from '../../api/notes'
import { fetchMeetings } from '../../api/meetings'
import { fetchHabitLogs, fetchHabits } from '../../api/habits'
import { fetchFocusSessions } from '../../api/focus'
import { fetchWeeklyReports } from '../../api/reviews'
import { pinia } from '../../stores'
import { useSettingsStore } from '../../stores/settings'
import { nowStamp, toYmd } from '../../utils/date'

/** 返回导出的记录条数 */
export const exportAll = async () => {
  const [tasks, taskLists, notes, archivedNotes, meetings, habits, habitLogs, focusSessions, weeklyReports] = await Promise.all([
    fetchTasks({ view: 'all' }),
    fetchTaskLists(),
    fetchNotes({ view: 'all' }),
    fetchNotes({ view: 'archived' }),
    fetchMeetings(),
    fetchHabits(true),
    fetchHabitLogs(),
    fetchFocusSessions(),
    fetchWeeklyReports(),
  ])
  const payload = {
    app: 'nebula-space',
    version: 1,
    exportedAt: nowStamp(),
    settings: useSettingsStore(pinia).data,
    tasks,
    taskLists,
    notes: [...notes, ...archivedNotes],
    meetings,
    habits,
    habitLogs,
    focusSessions,
    weeklyReports,
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `nebula-space_${toYmd(new Date()).replace(/-/g, '')}.json`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  return [tasks, taskLists, payload.notes, meetings, habits, habitLogs, focusSessions, weeklyReports].reduce((sum, list) => sum + list.length, 0)
}
