import { requestClient } from '#/api/request';

/**
 * 博客-文章 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
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
    postType?: string;
    authorId?: number | string;
    categoryId?: number | string;
    tagId?: number | string;
  }

  /** 文章条目（camelCase），用于列表和详情 */
  export interface ArticleListItem {
    id: number | string;
    authorId?: number | string;
    title: string;
    postType?: string;
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

  /** 创建/更新文章请求体（camelCase 上行） */
  export interface ArticleCreateParams {
    title: string;
    postType?: string;
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

  export interface ArticleUpdateParams {
    title?: string;
    postType?: string;
    slug?: string;
    summary?: string;
    content?: string;
    contentFileId?: number | string;
    coverFileId?: number | string;
    /**
     * 显式清除封面图片。
     * JSON 无法区分字段缺失与显式 null，前端移除封面时传 true，
     * 后端收到后将 coverFileId 置为 null。与 coverFileId 互斥。
     */
    clearCoverFileId?: boolean;
    status?: string;
    visibility?: string;
    sourceType?: string;
    isOriginal?: boolean;
    publishedAt?: string;
    categoryIds?: Array<number | string>;
    tagIds?: Array<number | string>;
    changeNote?: string;
  }

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

  /** 批量导入选项（统一应用到本批所有文件） */
  export interface ArticleImportOptions {
    status?: string;
    visibility?: string;
    postType?: string;
    categoryIds?: Array<number | string>;
    /** 是否下载正文外链图片并转存到公开桶后替换 URL，默认 true */
    rehostImages?: boolean;
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
  return requestClient.get<BlogArticleApi.ArticleListItem>(
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

/**
 * 异步批量导入 Markdown 文件，每个文件创建一篇文章。
 * 多文件以同名 `files` 字段提交，绑定到后端 MultipartFile[]。
 * 后端立即返回导入任务 ID，逐文件处理在后台进行，进度与逐文件明细在「文章导入任务」页查看。
 *
 * @param files   要导入的 .md / .markdown 文件
 * @param options 统一应用的状态 / 可见性 / 类型 / 分类
 * @returns 导入任务 ID
 */
export async function importBlogArticlesApi(
  files: File[],
  options: BlogArticleApi.ArticleImportOptions = {},
): Promise<number | string> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  if (options.status) formData.append('status', options.status);
  if (options.visibility) formData.append('visibility', options.visibility);
  if (options.postType) formData.append('postType', options.postType);
  (options.categoryIds ?? []).forEach((id) => {
    formData.append('categoryIds', String(id));
  });
  if (options.rehostImages !== undefined) {
    formData.append('rehostImages', String(options.rehostImages));
  }

  return requestClient.post<number | string>(
    '/blog/admin/articles/import',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}
