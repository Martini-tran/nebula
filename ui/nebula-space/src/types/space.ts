/**
 * 个人空间（书签 / 目录 / 标签）相关类型。
 *
 * 字段与 nebula-service-space 的 VO / DTO 一一对应，camelCase。
 */

/** Long 主键：Jackson 可能序列化成字符串，两种都接受 */
export type EntityId = number | string

/** 统一分页返回（对应 com.nebula.common.core.domain.PageResult）。 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 书签状态：0 正常 1 归档 2 失效（后端链接检查标记） */
export const BookmarkStatus = {
  NORMAL: 0,
  ARCHIVED: 1,
  BROKEN: 2,
} as const

export type BookmarkStatusValue = (typeof BookmarkStatus)[keyof typeof BookmarkStatus]

export const BOOKMARK_STATUS_LABEL: Record<BookmarkStatusValue, string> = {
  0: '正常',
  1: '已归档',
  2: '已失效',
}

export interface SpaceTag {
  id: EntityId
  name: string
  color?: string | null
  sortOrder?: number | null
  remark?: string | null
}

export interface Folder {
  id: EntityId
  parentId: EntityId
  name: string
  level?: number | null
  sortOrder?: number | null
  remark?: string | null
  children?: Folder[] | null
  createTime?: string | null
}

export interface Bookmark {
  id: EntityId
  folderId: EntityId
  title: string
  url: string
  domain?: string | null
  description?: string | null
  faviconUrl?: string | null
  source?: string | null
  status: BookmarkStatusValue
  visitCount?: number | null
  lastVisitTime?: string | null
  sortOrder?: number | null
  remark?: string | null
  tags?: SpaceTag[] | null
  createTime?: string | null
  updateTime?: string | null
}

export interface BookmarkPageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  folderId?: EntityId
  tagId?: EntityId
  status?: BookmarkStatusValue
  /** 精确匹配小写主机名 */
  domain?: string
}

export interface BookmarkSaveRequest {
  /** 0 表示未分类 */
  folderId?: EntityId
  title: string
  url: string
  description?: string
  faviconUrl?: string
  remark?: string
  tagIds?: EntityId[]
}

export interface FolderSaveRequest {
  /** 0 表示根目录 */
  parentId?: EntityId
  name: string
}

export interface TagSaveRequest {
  name: string
  color?: string
}

export interface TagUpdateRequest {
  name?: string
  color?: string
  sortOrder?: number
  remark?: string
}

export interface FolderUpdateRequest {
  name?: string
  sortOrder?: number
  remark?: string
}

/** 导入 / 导出任务状态：0 待处理 1 处理中 2 成功 3 失败 */
export const TaskStatus = {
  PENDING: 0,
  PROCESSING: 1,
  SUCCESS: 2,
  FAIL: 3,
} as const

export type TaskStatusValue = (typeof TaskStatus)[keyof typeof TaskStatus]

export const TASK_STATUS_LABEL: Record<TaskStatusValue, string> = {
  0: '待处理',
  1: '处理中',
  2: '成功',
  3: '失败',
}

export interface ImportTask {
  id: EntityId
  status: TaskStatusValue
  source?: string | null
  totalCount?: number | null
  successCount?: number | null
  duplicateCount?: number | null
  failCount?: number | null
  errorMsg?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface ExportTask {
  id: EntityId
  status: TaskStatusValue
  exportType?: string | null
  scopeType?: 'all' | 'folder' | 'tag' | null
  scopeId?: EntityId | null
  totalCount?: number | null
  errorMsg?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface TaskPageQuery {
  pageNum?: number
  pageSize?: number
  status?: TaskStatusValue
}

/** 导出范围 */
export type ExportScope =
  | { scopeType: 'all' }
  | { scopeType: 'folder' | 'tag'; scopeId: EntityId }
