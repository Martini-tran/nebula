import { get } from '../utils/request'

export interface CategorySummary {
  id: number | string
  name: string
  slug: string
}

export interface TagSummary {
  id: number | string
  name: string
  slug: string
}

export interface PostListItem {
  id: number | string
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
  categoryId?: number | string | null
  tagId?: number | string | null
  keyword?: string
  cursor?: string | null
  limit?: number
}

const omitEmpty = <T extends Record<string, unknown>>(obj: T) =>
  Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  )

export const fetchArticles = (params: FetchArticlesParams = {}) =>
  get<PostListResponse>('/blog/front/articles', {
    params: omitEmpty({
      categoryId: params.categoryId,
      tagId: params.tagId,
      keyword: params.keyword,
      cursor: params.cursor,
      limit: params.limit,
    }),
  })

export const fetchHotArticles = (limit = 5) =>
  get<PostListItem[]>('/blog/front/articles/hot', { params: { limit } })

export const fetchArticleDetail = (slug: string) =>
  get<PostDetail>(`/blog/front/articles/${encodeURIComponent(slug)}`)

export const fetchArticleContent = (slug: string) =>
  get<PostContent>(`/blog/front/articles/${encodeURIComponent(slug)}/content`)
