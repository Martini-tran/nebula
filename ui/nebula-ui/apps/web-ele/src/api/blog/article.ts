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

  export interface ArticleCreateParams {
    title: string;
    slug: string;
    summary?: string;
    content: string;
    contentFileId?: number | string;
    coverFileId?: number | string;
    status?: string;
    visibility?: string;
    sourceType?: string;
    isOriginal?: boolean;
    publishedAt?: string;
    categoryIds?: Array<number | string>;
    tagIds?: Array<number | string>;
  }

  export type ArticleUpdateParams = Partial<ArticleCreateParams>;

  export interface ArticleStatusUpdateParams {
    status: string;
    publishedAt?: string;
  }

  export interface ArticlePageResult {
    records: ArticleListItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

export async function getBlogArticlePageApi(
  params: BlogArticleApi.ArticlePageQuery,
) {
  return requestClient.get<BlogArticleApi.ArticlePageResult>(
    '/blog/admin/articles/page',
    { params },
  );
}

export async function getBlogArticleDetailApi(id: number | string) {
  return requestClient.get<BlogArticleApi.ArticleDetail>(
    `/blog/admin/articles/${id}`,
  );
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
