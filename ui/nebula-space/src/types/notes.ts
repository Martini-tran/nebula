/**
 * 随手记。后端尚未实现（见 docs/ui设计/个人空间/space-notes.html「后端待补」），字段按设计稿拟定。
 */
import type { EntityId } from './space'

/** 便签底色：只存色名，具体色值由主题决定（深色模式下另有一套） */
export type NoteColor = 'plain' | 'yellow' | 'green' | 'blue' | 'pink' | 'purple'

export interface Note {
  id: EntityId
  /** Markdown 正文，第一行作标题 */
  content: string
  color: NoteColor
  /** 置顶 = 长期笔记，不会过期 */
  pinned: boolean
  /** 临时笔记的到期日（YYYY-MM-DD），长期笔记为 null */
  expireDate: string | null
  archived: boolean
  tags: string[]
  createTime: string
  updateTime: string
}

export interface NoteQuery {
  view?: 'all' | 'temporary' | 'pinned' | 'archived'
  tag?: string
  keyword?: string
}

export interface NoteSaveRequest {
  content?: string
  color?: NoteColor
  pinned?: boolean
  tags?: string[]
  archived?: boolean
}

/** 新建临时笔记的默认寿命（天） */
export const NOTE_TTL_DAYS = 7
