import { requestClient } from '#/api/request';

/**
 * 博客-系列 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace BlogSeriesApi {
  export interface SeriesPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: string;
    visibility?: string;
    isFinished?: boolean;
    createBy?: number | string;
  }

  export interface SeriesItem {
    id: number | string;
    name: string;
    slug: string;
    description?: string;
    coverFileId?: number | string;
    coverUrl?: string;
    status: string;
    visibility: string;
    isFinished?: boolean;
    sortOrder?: number;
    createBy?: number | string;
    createTime?: string;
    updateTime?: string;
  }

  export type SeriesDetail = SeriesItem;

  export interface SeriesCreateParams {
    name: string;
    slug: string;
    description?: string;
    coverFileId?: number | string;
    status?: string;
    visibility?: string;
    isFinished?: boolean;
    sortOrder?: number;
  }

  export interface SeriesUpdateParams {
    name?: string;
    slug?: string;
    description?: string;
    coverFileId?: number | string;
    /**
     * 显式清除封面。前端移除封面时传 true，
     * 后端将 coverFileId 置为 null。
     */
    clearCoverFileId?: boolean;
    status?: string;
    visibility?: string;
    isFinished?: boolean;
    sortOrder?: number;
  }

  export interface SeriesPageResult {
    records: SeriesItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // -------------------- 目录节点 --------------------

  export interface CatalogNode {
    id: number | string;
    seriesId: number | string;
    parentId?: number | string | null;
    title: string;
    /** 0 目录 / 1 文章集合 / 2 链接 */
    nodeType: number;
    linkUrl?: string;
    linkTarget?: string;
    path?: string;
    level?: number;
    sortOrder?: number;
    childrenCount?: number;
    createTime?: string;
    updateTime?: string;
    children?: CatalogNode[];
  }

  export interface CatalogCreateParams {
    seriesId: number | string;
    parentId?: number | string | null;
    title: string;
    nodeType?: number;
    linkUrl?: string;
    linkTarget?: string;
    sortOrder?: number;
  }

  export interface CatalogUpdateParams {
    parentId?: number | string | null;
    title?: string;
    nodeType?: number;
    linkUrl?: string;
    linkTarget?: string;
    sortOrder?: number;
  }

  export interface CatalogPost {
    id: number | string;
    catalogId: number | string;
    postId: number | string;
    postTitle?: string;
    postSlug?: string;
    postStatus?: string;
    sortOrder?: number;
    isPrimary?: boolean;
    createTime?: string;
  }

  export interface CatalogPostBindParams {
    postIds: Array<number | string>;
    primaryPostId?: number | string | null;
  }
}

// -------------------- Series CRUD --------------------

export async function getBlogSeriesPageApi(
  params: BlogSeriesApi.SeriesPageQuery,
) {
  return requestClient.get<BlogSeriesApi.SeriesPageResult>(
    '/blog/admin/series/page',
    { params },
  );
}

export async function getBlogSeriesDetailApi(id: number | string) {
  return requestClient.get<BlogSeriesApi.SeriesItem>(`/blog/admin/series/${id}`);
}

export async function createBlogSeriesApi(
  data: BlogSeriesApi.SeriesCreateParams,
) {
  return requestClient.post<number | string>('/blog/admin/series', data);
}

export async function updateBlogSeriesApi(
  id: number | string,
  data: BlogSeriesApi.SeriesUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/series/${id}`, data);
}

export async function deleteBlogSeriesApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/series/${id}`);
}

// -------------------- Catalog --------------------

export async function getBlogSeriesCatalogTreeApi(seriesId: number | string) {
  const data = await requestClient.get<BlogSeriesApi.CatalogNode[]>(
    '/blog/admin/series/catalogs/tree',
    { params: { seriesId } },
  );
  return data ?? [];
}

export async function createBlogSeriesCatalogApi(
  data: BlogSeriesApi.CatalogCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/series/catalogs',
    data,
  );
}

export async function updateBlogSeriesCatalogApi(
  id: number | string,
  data: BlogSeriesApi.CatalogUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/series/catalogs/${id}`, data);
}

export async function deleteBlogSeriesCatalogApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/series/catalogs/${id}`);
}

export async function getBlogSeriesCatalogPostsApi(catalogId: number | string) {
  const data = await requestClient.get<BlogSeriesApi.CatalogPost[]>(
    `/blog/admin/series/catalogs/${catalogId}/posts`,
  );
  return data ?? [];
}

export async function bindBlogSeriesCatalogPostsApi(
  catalogId: number | string,
  data: BlogSeriesApi.CatalogPostBindParams,
) {
  return requestClient.put<void>(
    `/blog/admin/series/catalogs/${catalogId}/posts`,
    data,
  );
}
