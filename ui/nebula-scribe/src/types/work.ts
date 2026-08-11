/**
 * 作品 / 卷 / 章节相关类型。
 *
 * 字段命名按 camelCase，与网关后 scribe 服务的 Jackson 默认一致。
 */

/** 统一分页返回（对应 com.nebula.common.core.domain.PageResult）。 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 作品状态。 */
export type WorkStatus = 'draft' | 'serializing' | 'paused' | 'finished'

export const WORK_STATUS_LABEL: Record<WorkStatus, string> = {
  draft: '构思中',
  serializing: '连载中',
  paused: '暂停',
  finished: '已完结',
}

/** 章节状态。 */
export type ChapterStatus = 'outline' | 'drafting' | 'revising' | 'done'

export const CHAPTER_STATUS_LABEL: Record<ChapterStatus, string> = {
  outline: '大纲',
  drafting: '草稿',
  revising: '修订',
  done: '定稿',
}

/** 作品列表项。 */
export interface WorkListItem {
  id: number
  title: string
  /** 一句话简介 */
  summary?: string | null
  coverUrl?: string | null
  /** 题材，如「玄幻」「悬疑」 */
  genre?: string | null
  tags?: string[] | null
  status: WorkStatus
  /** 累计字数 */
  wordCount: number
  chapterCount: number
  /** 目标总字数，用于进度条 */
  targetWordCount?: number | null
  updateTime?: string | null
  createTime?: string | null
}

/** 作品详情。 */
export interface WorkDetail extends WorkListItem {
  /** 长简介，Markdown */
  intro?: string | null
  /** 一句话立意 / 核心冲突 */
  logline?: string | null
  volumes: Volume[]
}

/** 卷。 */
export interface Volume {
  id: number
  workId: number
  title: string
  sortOrder: number
  chapters: ChapterListItem[]
}

/** 章节列表项（不含正文）。 */
export interface ChapterListItem {
  id: number
  volumeId: number
  title: string
  sortOrder: number
  status: ChapterStatus
  wordCount: number
  /** 本章梗概，写作台侧栏与大纲视图展示 */
  synopsis?: string | null
  updateTime?: string | null
}

/** 章节详情（含正文）。 */
export interface ChapterDetail extends ChapterListItem {
  workId: number
  content: string
}

/** 作品查询参数。 */
export interface WorkPageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: WorkStatus
  sort?: string
}

/** 新建作品请求体。 */
export interface WorkCreateRequest {
  title: string
  genre?: string
  logline?: string
  summary?: string
  targetWordCount?: number
}

/** 章节保存请求体。 */
export interface ChapterSaveRequest {
  title?: string
  content?: string
  synopsis?: string
  status?: ChapterStatus
}

/** 排序选项。 */
export interface SortOption {
  value: string
  label: string
  icon: string
}

export const WORK_SORT_OPTIONS: SortOption[] = [
  { value: 'recent', label: '最近编辑', icon: 'lucide:clock' },
  { value: 'created', label: '创建时间', icon: 'lucide:calendar' },
  { value: 'words', label: '字数最多', icon: 'lucide:file-text' },
  { value: 'title', label: '标题', icon: 'lucide:a-arrow-down' },
]
