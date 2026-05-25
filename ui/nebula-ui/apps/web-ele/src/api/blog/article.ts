import { requestClient } from '#/api/request';

export namespace BlogArticleApi {
  export interface CategorySummary {
    id: number | string;
    name: string;
    slug?: string;
  }

  export interface TagSummary {
    id: number | string;
    name: string;
    slug?: string;
  }

  export interface ArticlePageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: string;
    visibility?: string;
    sourceType?: string;
    authorId?: number | string;
    categoryId?: number | string;
    tagId?: number | string;
  }

  /**
   * 后端以 SNAKE_CASE 序列化 + Long→String 返回的原始结构。
   * 仅供内部规范化使用，外部请使用 ArticleListItem。
   */
  export interface ArticleItemRaw {
    id: number | string;
    author_id?: number | string;
    title: string;
    slug: string;
    summary?: string;
    content?: string;
    content_file_id?: number | string;
    content_url?: string;
    cover_file_id?: number | string;
    cover_url?: string;
    status: string;
    visibility: string;
    source_type?: string;
    is_original?: boolean;
    view_count?: number;
    like_count?: number;
    published_at?: string;
    create_time?: string;
    update_time?: string;
    categories?: CategorySummary[];
    tags?: TagSummary[];
  }

  /** 规范化后的文章条目（camelCase），用于列表和详情 */
  export interface ArticleListItem {
    id: number | string;
    authorId?: number | string;
    title: string;
    slug: string;
    summary?: string;
    content?: string;
    contentFileId?: number | string;
    contentUrl?: string;
    coverFileId?: number | string;
    coverUrl?: string;
    status: string;
    visibility: string;
    sourceType?: string;
    isOriginal?: boolean;
    viewCount?: number;
    likeCount?: number;
    publishedAt?: string;
    createTime?: string;
    updateTime?: string;
    categories?: CategorySummary[];
    tags?: TagSummary[];
  }

  export type ArticleDetail = ArticleListItem;

  /**
   * 创建/更新文章请求体。
   * 后端 SNAKE_CASE 策略在反序列化时要求 JSON key 也是 snake_case，
   * 因此 id 类字段和多词字段均使用 snake_case。
   */
  export interface ArticleCreateParams {
    title: string;
    slug: string;
    summary?: string;
    content: string;
    content_file_id?: number | string;
    cover_file_id?: number | string;
    status?: string;
    visibility?: string;
    source_type?: string;
    is_original?: boolean;
    published_at?: string;
    category_ids?: Array<number | string>;
    tag_ids?: Array<number | string>;
  }

  export interface ArticleUpdateParams {
    title?: string;
    slug?: string;
    summary?: string;
    content?: string;
    content_file_id?: number | string;
    cover_file_id?: number | string;
    /**
     * 显式清除封面图片。
     * JSON 无法区分字段缺失与显式 null，前端移除封面时传 true，
     * 后端收到后将 coverFileId 置为 null。与 cover_file_id 互斥。
     */
    clear_cover_file_id?: boolean;
    status?: string;
    visibility?: string;
    source_type?: string;
    is_original?: boolean;
    published_at?: string;
    category_ids?: Array<number | string>;
    tag_ids?: Array<number | string>;
    change_note?: string;
  }

  export interface ArticleStatusUpdateParams {
    status: string;
    published_at?: string;
  }

  export interface ArticlePageResult {
    records: ArticleListItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

/** 将后端原始 snake_case 对象规范化为前端 camelCase 对象 */
function normalizeArticleItem(
  raw: BlogArticleApi.ArticleItemRaw,
): BlogArticleApi.ArticleListItem {
  return {
    id: raw.id,
    authorId: raw.author_id,
    title: raw.title,
    slug: raw.slug,
    summary: raw.summary,
    content: raw.content,
    contentFileId: raw.content_file_id,
    contentUrl: raw.content_url,
    coverFileId: raw.cover_file_id,
    coverUrl: raw.cover_url,
    status: raw.status,
    visibility: raw.visibility,
    sourceType: raw.source_type,
    isOriginal: raw.is_original,
    viewCount: raw.view_count,
    likeCount: raw.like_count,
    publishedAt: raw.published_at,
    createTime: raw.create_time,
    updateTime: raw.update_time,
    categories: raw.categories,
    tags: raw.tags,
  };
}

export async function getBlogArticlePageApi(
  params: BlogArticleApi.ArticlePageQuery,
) {
  const result = await requestClient.get<{
    records: BlogArticleApi.ArticleItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/articles/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeArticleItem),
  } as BlogArticleApi.ArticlePageResult;
}

export async function getBlogArticleDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogArticleApi.ArticleItemRaw>(
    `/blog/admin/articles/${id}`,
  );
  return normalizeArticleItem(raw);
}

export async function createBlogArticleApi(
  data: BlogArticleApi.ArticleCreateParams,
) {
  return requestClient.post<number | string>('/blog/admin/articles', data);
}

export async function updateBlogArticleApi(
  id: number | string,
  data: BlogArticleApi.ArticleUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/articles/${id}`, data);
}

export async function updateBlogArticleStatusApi(
  id: number | string,
  data: BlogArticleApi.ArticleStatusUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/articles/${id}/status`, data);
}

export async function deleteBlogArticleApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/articles/${id}`);
}
