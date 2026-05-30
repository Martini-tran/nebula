import { get } from '../utils/request'
import type { TagSummary } from './post'

/**
 * 系列章节项（一篇已发布文章）
 */
export interface SeriesChapter {
  post_id: number | string
  title: string
  slug: string
  summary?: string | null
  status: string
  catalog_id: number | string
  catalog_title: string
  is_primary: boolean
  order: number
  published_at?: string | null
}

/** 系列目录节点 */
export interface SeriesCatalogNode {
  id: number | string
  parent_id?: number | string | null
  title: string
  /** 0 目录 / 1 文章集合 / 2 链接 */
  node_type: number
  link_url?: string | null
  link_target?: string | null
  sort_order?: number
  posts: SeriesChapter[]
  children: SeriesCatalogNode[]
}

/** 系列列表项 */
export interface SeriesListItem {
  id: number | string
  slug: string
  name: string
  description?: string | null
  cover_url?: string | null
  is_finished: boolean
  article_count: number
  sort_order?: number
  create_time?: string | null
  update_time?: string | null
  tags: TagSummary[]
}

/** 系列列表分页响应 */
export interface SeriesListResponse {
  items: SeriesListItem[]
  next_cursor: string | null
}

/** 系列详情 */
export interface SeriesDetail {
  id: number | string
  slug: string
  name: string
  description?: string | null
  cover_url?: string | null
  is_finished: boolean
  article_count: number
  create_time?: string | null
  update_time?: string | null
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
