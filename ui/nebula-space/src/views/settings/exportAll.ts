/**
 * 「导出全部数据」：把各模块的数据取齐，打成一个 JSON 下载（文件柜只含文件信息，人物卡单独一节）。
 * 接通后端时由服务端 GET /space/me/export 一次拼好（任何一块取不到就整体失败，不会导出缺了一块的文件），
 * 本地演示（VITE_REAL_MODULES 不含 export）时在前端逐个模块取了拼。两边结构相同（version 1），服务端版另带 counts / total 与记账预算。
 * 书签另有 Chrome 兼容 HTML 导出，不放进这个文件。
 */
import { fetchTaskLists, fetchTasks } from '../../api/tasks'
import { fetchNotes } from '../../api/notes'
import { fetchMeetings } from '../../api/meetings'
import { fetchHabitLogs, fetchHabits } from '../../api/habits'
import { fetchFocusSessions } from '../../api/focus'
import { fetchReports } from '../../api/reviews'
import { fetchHighlights, fetchReadingItems } from '../../api/reading'
import { fetchCategories, fetchEntries, fetchRecurring } from '../../api/ledger'
import { fetchAnniversaries, fetchGoals } from '../../api/goals'
import { fetchPeople } from '../../api/people'
import { fetchFiles, fetchShares } from '../../api/files'
import { fetchProfile } from '../../api/profile'
import { downloadAllData } from '../../api/settings'
import { saveBlob } from '../../api/files'
import { useMockFor } from '../../api/mock'
import { pinia } from '../../stores'
import { useSettingsStore } from '../../stores/settings'
import { nowStamp, toYmd } from '../../utils/date'

const fileName = () => `nebula-space_${toYmd(new Date()).replace(/-/g, '')}.json`

/** 返回导出的记录条数 */
export const exportAll = async (): Promise<number> => {
  if (!useMockFor('export')) {
    const blob = await downloadAllData()
    saveBlob(blob, fileName())
    try {
      return Number((JSON.parse(await blob.text()) as { total?: number }).total ?? 0)
    } catch {
      return 0
    }
  }
  const soft = <T>(p: Promise<T>, fallback: T) => p.catch(() => fallback)
  const year = new Date().getFullYear()
  const [tasks, taskLists, notes, archivedNotes, meetings, habits, habitLogs, focusSessions, reports] = await Promise.all([
    fetchTasks({ view: 'all' }),
    fetchTaskLists(),
    fetchNotes({ view: 'all' }),
    fetchNotes({ view: 'archived' }),
    fetchMeetings(),
    fetchHabits(true),
    fetchHabitLogs(),
    fetchFocusSessions(),
    fetchReports(),
  ])
  const [reading, readingArchived, highlights, ledgerCategories, ledgerEntries, ledgerRecurring, goalsThis, goalsLast, anniversaries, people, files, shares, profile] =
    await Promise.all([
      soft(fetchReadingItems(), []),
      soft(fetchReadingItems({ archived: true }), []),
      soft(fetchHighlights(), []),
      soft(fetchCategories(), []),
      soft(fetchEntries(), []),
      soft(fetchRecurring(), []),
      soft(fetchGoals(year), []),
      soft(fetchGoals(year - 1), []),
      soft(fetchAnniversaries(), []),
      soft(fetchPeople(), []),
      soft(fetchFiles({ view: 'all' }), []),
      soft(fetchShares(), []),
      soft(fetchProfile(), null),
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
    reports,
    reading: [...reading, ...readingArchived],
    highlights,
    ledger: { categories: ledgerCategories, entries: ledgerEntries, recurring: ledgerRecurring },
    goals: [...goalsLast, ...goalsThis],
    anniversaries,
    /** 文件柜只导出文件信息，文件本身请在文件柜里下载 */
    files,
    shares,
    publicProfile: profile,
    /** 人物卡单独列出，方便自行决定是否保留在导出文件里 */
    privatePeople: people,
  }
  saveBlob(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }), fileName())
  return [tasks, taskLists, payload.notes, meetings, habits, habitLogs, focusSessions, reports, payload.reading, highlights, ledgerEntries, payload.goals, anniversaries, people, files].reduce(
    (sum, list) => sum + list.length,
    0,
  )
}
