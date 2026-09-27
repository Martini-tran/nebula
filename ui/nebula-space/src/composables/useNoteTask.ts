import { createTask } from '../api/tasks'
import { updateNote } from '../api/notes'
import { firstLine } from '../utils/markdown'
import type { Note } from '../types/notes'
import type { Task } from '../types/tasks'

/**
 * 笔记（或笔记里选中的一段）转成任务：任务进收件箱并记下来源；
 * 被任务引用的笔记自动转长期，免得任务还在、来源笔记先过期了。
 */
export const noteToTask = async (note: Note, text?: string): Promise<Task> => {
  const label = firstLine(note.content).slice(0, 60) || '笔记'
  const title = (text ?? label).replace(/\s+/g, ' ').trim().slice(0, 200)
  const task = await createTask({ title, source: { type: 'note', id: note.id, label } })
  if (!note.pinned) await updateNote(note.id, { pinned: true })
  return task
}
