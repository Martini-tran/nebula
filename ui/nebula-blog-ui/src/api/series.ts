import { get } from '../utils/request'
import type { TagSummary } from './post'

/**
 * 系列章节项（一篇已发布文章）
 */
export interface SeriesChapter {
  postId: number | string
  title: string
  slug: string
  summary?: string | null
  status: string
  catalogId: number | string
  catalogTitle: string
  isPrimary: boolean
  order: number
  publishedAt?: string | null
}

/** 系列目录节点 */
export interface SeriesCatalogNode {
  id: number | string
  parentId?: number | string | null
  title: string
  /** 0 目录 / 1 文章集合 / 2 链接 */
  nodeType: number
  linkUrl?: string | null
  linkTarget?: string | null
  sortOrder?: number
  posts: SeriesChapter[]
  children: SeriesCatalogNode[]
}

/** 系列列表项 */
export interface SeriesListItem {
  id: number | string
  slug: string
  name: string
  description?: string | null
  coverUrl?: string | null
  isFinished: boolean
  articleCount: number
  sortOrder?: number
  createTime?: string | null
  updateTime?: string | null
  tags: TagSummary[]
}

/** 系列列表分页响应 */
export interface SeriesListResponse {
  items: SeriesListItem[]
  nextCursor: string | null
}

/** 系列详情 */
export interface SeriesDetail {
  id: number | string
  slug: string
  name: string
  description?: string | null
  coverUrl?: string | null
  isFinished: boolean
  articleCount: number
  createTime?: string | null
  updateTime?: string | null
  tags: TagSummary[]
  chapters: SeriesChapter[]
  catalog: SeriesCatalogNode[]
}

export interface FetchSeriesParams {
  keyword?: string
  isFinished?: boolean
  cursor?: string | null
  limit?: number
}

const omitEmpty = <T extends Record<string, unknown>>(obj: T) =>
  Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  )

export const fetchSeriesList = (params: FetchSeriesParams = {}) =>
  get<SeriesListResponse>('/blog/front/series', {
    params: omitEmpty({
      keyword: params.keyword,
      isFinished: params.isFinished,
      cursor: params.cursor,
      limit: params.limit,
    }),
  })

export const fetchSeriesDetail = (slug: string) =>
  get<SeriesDetail>(`/blog/front/series/${encodeURIComponent(slug)}`)
