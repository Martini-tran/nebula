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

/** 目标读者（对应网文平台的男频/女频）。 */
export type WorkAudience = 'male' | 'female' | 'general'

export const WORK_AUDIENCE_LABEL: Record<WorkAudience, string> = {
  male: '男频',
  female: '女频',
  general: '不限',
}

/**
 * 后端 Long 型 id 统一序列化成字符串（防 JS 大整数丢精度），mock 数据是数字，
 * 所以 id 两种都可能；比较时先 String() 再比。
 */
export type EntityId = number | string

/** 作品列表项。 */
export interface WorkListItem {
  id: EntityId
  title: string
  /** 一句话简介 */
  summary?: string | null
  coverUrl?: string | null
  audience?: WorkAudience | null
  /** 题材，如「玄幻」「悬疑」 */
  genre?: string | null
  tags?: string[] | null
  /** 主角名 */
  protagonists?: string[] | null
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
  /** 作品简介，纯文本 */
  intro?: string | null
  /** 一句话立意 / 核心冲突 */
  logline?: string | null
}

/**
 * 卷：可选的一层。作品要么没有卷（章节平铺），要么每一章都属于某一卷。
 * 章数、字数不存库，由章节列表按 volumeId 聚合。
 */
export interface Volume {
  id: EntityId
  workId: EntityId
  title: string
  /** 本卷梗概 */
  synopsis?: string | null
  sortOrder: number
  updateTime?: string | null
}

/** 作品目录：删卷、重排等改动结构的操作都返回整棵新目录。 */
export interface Toc {
  volumes: Volume[]
  chapters: ChapterListItem[]
}

/** 整棵目录树重排请求：有卷传 volumes，无卷传 chapterIds。 */
export interface TocSortRequest {
  volumes?: Array<{ id: EntityId; chapterIds: EntityId[] }>
  chapterIds?: EntityId[]
}

/** 章节列表项（不含正文）。 */
export interface ChapterListItem {
  id: EntityId
  workId: EntityId
  /** 所属卷；卷表落地前恒为 null */
  volumeId?: EntityId | null
  title: string
  sortOrder: number
  status: ChapterStatus
  /** 去掉空白后的字符数 */
  wordCount: number
  /** 本章梗概，写作台侧栏与大纲视图展示 */
  synopsis?: string | null
  updateTime?: string | null
}

/** 章节详情（含正文）。 */
export interface ChapterDetail extends ChapterListItem {
  /** 正文，纯文本，段落以换行分隔 */
  content: string
  /** 修订号：保存时原样带回，不一致说明此章已在别处修改（后端返回 409） */
  revision: number | string
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
  summary?: string
  logline?: string
  intro?: string
  audience?: WorkAudience
  genre?: string
  tags?: string[]
  protagonists?: string[]
  targetWordCount?: number
}

/** 修改作品请求体：整表单覆盖，没传的可选字段会被清空。 */
export interface WorkUpdateRequest extends WorkCreateRequest {
  status?: WorkStatus
}

/** 章节保存请求体：局部更新，没传的字段保持不变。 */
export interface ChapterSaveRequest {
  title?: string
  content?: string
  synopsis?: string
  status?: ChapterStatus
  revision: number | string
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
