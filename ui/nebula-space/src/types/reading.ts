/**
 * 稍后读与摘录。后端尚未实现（见 docs/ui设计/个人空间/space-reading.html「后端待补」）：
 * 书签加阅读状态字段，正文抓取清洗后存档；划线落 space_highlight。字段按设计稿拟定。
 */
import type { EntityId } from './space'

export type ReadStatus = 'unread' | 'reading' | 'done'

export interface ReadingItem {
  id: EntityId
  url: string
  title: string
  domain: string
  /** 摘要：列表里显示两行 */
  excerpt: string
  /** 存档的阅读版正文（按段落）；null = 还没抓到，只能打开原文 */
  content: string[] | null
  /** 预计阅读分钟 */
  minutes: number
  status: ReadStatus
  /** 0 ~ 1 */
  progress: number
  /** 读到的滚动位置（占全文高度的比例），下次打开回到这里 */
  position: number
  /** 读完时写的一句读后感 */
  thought: string
  archived: boolean
  /** 对应的书签（从书签加入时） */
  bookmarkId: EntityId | null
  addTime: string
  lastReadTime: string | null
  doneTime: string | null
}

export type HighlightColor = 'yellow' | 'blue'

export const HIGHLIGHT_COLORS: Record<HighlightColor, { label: string; hint: string }> = {
  yellow: { label: '观点', hint: '值得记住的观点' },
  blue: { label: '查证', hint: '要查证 / 待办' },
}

export interface Highlight {
  id: EntityId
  itemId: EntityId
  /** 定位锚点：第几段、段内起止字符 */
  para: number
  start: number
  end: number
  text: string
  color: HighlightColor
  note: string
  /** 转出的随手记 / 任务 */
  noteId: EntityId | null
  taskId: EntityId | null
  taskTitle: string | null
  createTime: string
}

export type ReadingSaveRequest = Partial<Omit<ReadingItem, 'id' | 'addTime'>>
