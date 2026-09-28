/**
 * 稍后读与摘录。对应后端 space_reading（文章，加入时抓取正文存档）、space_reading_highlight（划线）两张表。
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
  /** 存档的阅读版正文（按段落）。只有打开单篇时带，列表里为 null，看 saved */
  content: string[] | null
  /** 有没有存档的阅读版；没有就只能打开原文 */
  saved: boolean
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

export type ReadingSaveRequest = Partial<Pick<ReadingItem, 'title' | 'status' | 'progress' | 'position' | 'thought' | 'archived' | 'lastReadTime' | 'doneTime'>>
