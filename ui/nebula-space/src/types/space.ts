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
  /** 最近一次链接检查时间 */
  checkTime?: string | null
  /** 最近一次链接检查的结论：打不开或无法确定时的原因 */
  checkResult?: string | null
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

/** 一条没导进来的书签 */
export interface ImportFailure {
  title?: string | null
  url?: string | null
  reason: string
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
  /** 逐条失败原因，最多 100 条（failCount 可能更多） */
  failures?: ImportFailure[] | null
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

/** 删除非空目录时里面的东西怎么办：子目录与书签上移一层 / 书签放进未分类 / 连同书签一起删 */
export type FolderDeleteStrategy = 'moveUp' | 'uncategorize' | 'cascade'

/** html：Chrome 兼容，可导回浏览器；json：带目录路径、标签、描述、备注，完整备份 */
export type ExportFormat = 'html' | 'json'

export interface ExportOptions {
  format?: ExportFormat
  /** 连同「已归档」的一起导出（失效的始终不导出） */
  includeArchived?: boolean
}

/** 导出记录上的 exportType 对应哪种格式 */
export const exportFormatOf = (exportType?: string | null): ExportFormat => (exportType === 'json' ? 'json' : 'html')

// ── 链接检查 ──

/** alive 能打开 / dead 打不开 / unknown 无法确定（超时、内网地址等，状态不变） / skipped 已归档未检查 */
export type LinkVerdict = 'alive' | 'dead' | 'unknown' | 'skipped'

export interface LinkCheckResult {
  id: EntityId
  verdict: LinkVerdict
  reason?: string | null
  /** 检查后的状态 */
  status: BookmarkStatusValue
  /** 状态是否因这次检查改变（正常→失效，或失效→正常） */
  changed: boolean
}

// ── AI 整理 ──

/** folder 归目录 / tags 打标签 / title 改标题 / description 补描述 */
export type AiAction = 'folder' | 'tags' | 'title' | 'description'

/** 一条书签的整理建议，只列要改的项 */
export interface AiSuggestion {
  bookmarkId: EntityId
  /** id 为空表示要新建，path 是完整路径「前端 / 工程化」 */
  folder?: { id?: EntityId | null; path: string } | null
  /** 要加上的标签（不含已有的）；id 为空表示要新建 */
  tags?: { id?: EntityId | null; name: string; color?: string | null }[] | null
  title?: string | null
  description?: string | null
}

/**
 * 目录重排的一个操作。folder / parent / into 取值：已有目录为 ID，
 * 本方案新建的目录为 N1、N2… 编号，顶层为 "0"。
 */
export interface FolderPlanOp {
  op: 'create' | 'rename' | 'move' | 'merge'
  key?: string | null
  folder?: string | null
  parent?: string | null
  into?: string | null
  name?: string | null
  reason?: string | null
  before?: string | null
  after?: string | null
  bookmarkCount?: number | null
}

export interface FolderPlan {
  summary?: string | null
  ops: FolderPlanOp[]
  /** AI 给出但不合规、已丢弃的操作数 */
  dropped?: number | null
}
