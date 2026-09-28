/**
 * 随手记。对应后端 space_note 表（标签存 JSON 数组，不走 space_tag）。
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
  /** 手动指定到期日（YYYY-MM-DD，不早于今天），笔记随之变为临时笔记 */
  expireDate?: string
}

/** 新建临时笔记的默认寿命（天） */
export const NOTE_TTL_DAYS = 7
