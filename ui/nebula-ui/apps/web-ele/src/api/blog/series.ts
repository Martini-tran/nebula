import { requestClient } from '#/api/request';

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

  /** 后端 SNAKE_CASE 原始结构 */
  export interface SeriesItemRaw {
    id: number | string;
    name: string;
    slug: string;
    description?: string;
    cover_file_id?: number | string;
    cover_url?: string;
    status: string;
    visibility: string;
    is_finished?: boolean;
    sort_order?: number;
    create_by?: number | string;
    create_time?: string;
    update_time?: string;
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
    cover_file_id?: number | string;
    status?: string;
    visibility?: string;
    is_finished?: boolean;
    sort_order?: number;
  }

  export interface SeriesUpdateParams {
    name?: string;
    slug?: string;
    description?: string;
    cover_file_id?: number | string;
    /**
     * 显式清除封面。前端移除封面时传 true，
     * 后端将 coverFileId 置为 null。
     */
    clear_cover_file_id?: boolean;
    status?: string;
    visibility?: string;
    is_finished?: boolean;
    sort_order?: number;
  }

  export interface SeriesPageResult {
    records: SeriesItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // -------------------- 目录节点 --------------------

  export interface CatalogNodeRaw {
    id: number | string;
    series_id: number | string;
    parent_id?: number | string | null;
    title: string;
    node_type: number;
    link_url?: string;
    link_target?: string;
    path?: string;
    level?: number;
    sort_order?: number;
    children_count?: number;
    create_time?: string;
    update_time?: string;
    children?: CatalogNodeRaw[];
  }

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
    series_id: number | string;
    parent_id?: number | string | null;
    title: string;
    node_type?: number;
    link_url?: string;
    link_target?: string;
    sort_order?: number;
  }

  export interface CatalogUpdateParams {
    parent_id?: number | string | null;
    title?: string;
    node_type?: number;
    link_url?: string;
    link_target?: string;
    sort_order?: number;
  }

  export interface CatalogPostRaw {
    id: number | string;
    catalog_id: number | string;
    post_id: number | string;
    post_title?: string;
    post_slug?: string;
    post_status?: string;
    sort_order?: number;
    is_primary?: boolean;
    create_time?: string;
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
    post_ids: Array<number | string>;
    primary_post_id?: number | string | null;
  }
}

function normalizeSeriesItem(
  raw: BlogSeriesApi.SeriesItemRaw,
): BlogSeriesApi.SeriesItem {
  return {
    id: raw.id,
    name: raw.name,
    slug: raw.slug,
    description: raw.description,
    coverFileId: raw.cover_file_id,
    coverUrl: raw.cover_url,
    status: raw.status,
    visibility: raw.visibility,
    isFinished: raw.is_finished,
    sortOrder: raw.sort_order,
    createBy: raw.create_by,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeCatalogNode(
  raw: BlogSeriesApi.CatalogNodeRaw,
): BlogSeriesApi.CatalogNode {
  return {
    id: raw.id,
    seriesId: raw.series_id,
    parentId: raw.parent_id ?? null,
    title: raw.title,
    nodeType: raw.node_type,
    linkUrl: raw.link_url,
    linkTarget: raw.link_target,
    path: raw.path,
    level: raw.level,
    sortOrder: raw.sort_order,
    childrenCount: raw.children_count,
    createTime: raw.create_time,
    updateTime: raw.update_time,
    children: raw.children?.map(normalizeCatalogNode) ?? [],
  };
}

function normalizeCatalogPost(
  raw: BlogSeriesApi.CatalogPostRaw,
): BlogSeriesApi.CatalogPost {
  return {
    id: raw.id,
    catalogId: raw.catalog_id,
    postId: raw.post_id,
    postTitle: raw.post_title,
    postSlug: raw.post_slug,
    postStatus: raw.post_status,
    sortOrder: raw.sort_order,
    isPrimary: raw.is_primary,
    createTime: raw.create_time,
  };
}

// -------------------- Series CRUD --------------------

export async function getBlogSeriesPageApi(
  params: BlogSeriesApi.SeriesPageQuery,
) {
  const result = await requestClient.get<{
    records: BlogSeriesApi.SeriesItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/series/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeSeriesItem),
  } as BlogSeriesApi.SeriesPageResult;
}

export async function getBlogSeriesDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogSeriesApi.SeriesItemRaw>(
    `/blog/admin/series/${id}`,
  );
  return normalizeSeriesItem(raw);
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
  const data = await requestClient.get<BlogSeriesApi.CatalogNodeRaw[]>(
    '/blog/admin/series/catalogs/tree',
    { params: { seriesId } },
  );
  return (data ?? []).map(normalizeCatalogNode);
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

export async function getBlogSeriesCatalogPostsApi(
  catalogId: number | string,
) {
  const data = await requestClient.get<BlogSeriesApi.CatalogPostRaw[]>(
    `/blog/admin/series/catalogs/${catalogId}/posts`,
  );
  return (data ?? []).map(normalizeCatalogPost);
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
