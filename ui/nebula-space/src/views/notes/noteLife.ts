import { diffDays, monthDay, todayYmd } from '../../utils/date'
import type { Note, NoteColor } from '../../types/notes'

export const NOTE_COLORS: { key: NoteColor; label: string }[] = [
  { key: 'plain', label: '白' },
  { key: 'yellow', label: '黄' },
  { key: 'green', label: '绿' },
  { key: 'blue', label: '蓝' },
  { key: 'pink', label: '粉' },
  { key: 'purple', label: '紫' },
]

/** 便签底部的寿命说明：置顶 / 7 天 / 明天到期（标红）/ 已归档 */
export const lifeLabel = (note: Note): { text: string; icon: string; tone: 'pinned' | 'normal' | 'danger' } => {
  if (note.archived) return { text: '已归档', icon: 'lucide:archive', tone: 'normal' }
  if (note.pinned || !note.expireDate) return { text: '置顶', icon: 'lucide:pin', tone: 'pinned' }
  const days = diffDays(todayYmd(), note.expireDate)
  if (days <= 0) return { text: '今天到期', icon: 'lucide:hourglass', tone: 'danger' }
  if (days === 1) return { text: '明天到期', icon: 'lucide:hourglass', tone: 'danger' }
  return { text: `${days} 天`, icon: 'lucide:hourglass', tone: 'normal' }
}

/** 编辑页右栏「保留」里的说明 */
export const expireText = (note: Note) =>
  note.pinned || !note.expireDate ? '长期保留，不会过期' : `临时笔记，${monthDay(note.expireDate)}自动归档`
