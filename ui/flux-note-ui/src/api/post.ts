import { get } from '../utils/request'

export interface CategorySummary {
  id: number
  name: string
  slug: string
}

export interface TagSummary {
  id: number
  name: string
  slug: string
}

export interface PostListItem {
  id: number
  slug: string
  title: string
  summary?: string | null
  cover_url?: string | null
  categories: CategorySummary[]
  tags: TagSummary[]
  view_count: number
  like_count: number
  published_at: string
}

export type PostDetail = PostListItem

export interface PostContent {
  content: string
}

export interface PostListResponse {
  items: PostListItem[]
  next_cursor: string | null
}

export interface FetchArticlesParams {
  categoryId?: number | null
  tagId?: number | null
  keyword?: string
  cursor?: string | null
  limit?: number
}

const omitEmpty = <T extends Record<string, unknown>>(obj: T) =>
  Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  )

export const fetchArticles = (params: FetchArticlesParams = {}) =>
  get<PostListResponse>('/front/articles', {
    params: omitEmpty({
      category_id: params.categoryId,
      tag_id: params.tagId,
      keyword: params.keyword,
      cursor: params.cursor,
      limit: params.limit,
    }),
  })

export const fetchHotArticles = (limit = 5) =>
  get<PostListItem[]>('/front/articles/hot', { params: { limit } })

export const fetchArticleDetail = (slug: string) =>
  get<PostDetail>(`/front/articles/${encodeURIComponent(slug)}`)

export const fetchArticleContent = (slug: string) =>
  get<PostContent>(`/front/articles/${encodeURIComponent(slug)}/content`)
