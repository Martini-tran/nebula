import { createTask, updateTask } from '../api/tasks'
import { updateMeeting } from '../api/meetings'
import { useAuthStore } from '../stores/auth'
import { parseMeetingItems } from '../utils/meetingItems'
import type { Meeting } from '../types/meetings'

/** 「@我」之外，当前账号的昵称、用户名也算「我」 */
export const useMyNames = () => {
  const auth = useAuthStore()
  return [auth.user?.nickname ?? '', auth.user?.username ?? ''].filter(Boolean)
}

/**
 * 把会议里「我的待办」同步进任务：新出现的建任务（来源记为这场会），已同步过的更新截止日期。
 * 返回新建了几条。同步关系按待办文本记在 meeting.syncedTasks 里。
 */
export const syncMyActions = async (meeting: Meeting, myNames: string[]) => {
  const { actions } = parseMeetingItems(meeting.content, myNames, meeting.date)
  const synced = { ...meeting.syncedTasks }
  let created = 0
  for (const action of actions.filter((a) => a.mine)) {
    const taskId = synced[action.text]
    if (taskId !== undefined) {
      if (action.due) await updateTask(taskId, { dueDate: action.due }).catch(() => undefined)
      continue
    }
    const task = await createTask({
      title: action.text,
      dueDate: action.due,
      source: { type: 'meeting', id: meeting.id, label: meeting.title },
    })
    synced[action.text] = task.id
    created += 1
  }
  const saved = await updateMeeting(meeting.id, { syncedTasks: synced })
  return { meeting: saved, created }
}
