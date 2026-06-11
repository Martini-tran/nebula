import { requestClient } from '#/api/request';

export namespace BlogImportTaskApi {
  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    status?: string;
    userId?: number | string;
  }

  /**
   * 后端以 SNAKE_CASE 序列化 + Long→String 返回的原始结构。
   * 仅供内部规范化使用，外部请使用 ImportTaskItem。
   */
  export interface ImportTaskRaw {
    id: number | string;
    user_id?: number | string;
    status?: string;
    total_count?: number;
    processed_count?: number;
    success_count?: number;
    fail_count?: number;
    post_status?: string;
    visibility?: string;
    post_type?: string;
    rehost_images?: boolean;
    error_message?: string;
    started_at?: string;
    finished_at?: string;
    create_time?: string;
    items?: ImportItemRaw[];
  }

  /** 规范化后的导入任务（camelCase） */
  export interface ImportTaskItem {
    id: number | string;
    userId?: number | string;
    status?: string;
    totalCount?: number;
    processedCount?: number;
    successCount?: number;
    failCount?: number;
    postStatus?: string;
    visibility?: string;
    postType?: string;
    rehostImages?: boolean;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
    createTime?: string;
    items?: ImportItem[];
  }

  export interface ImportItemRaw {
    id: number | string;
    filename: string;
    success?: boolean;
    article_id?: number | string;
    title?: string;
    slug?: string;
    error?: string;
    create_time?: string;
  }

  export interface ImportItem {
    id: number | string;
    filename: string;
    success?: boolean;
    articleId?: number | string;
    title?: string;
    slug?: string;
    error?: string;
    createTime?: string;
  }

  export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeItem(
  raw: BlogImportTaskApi.ImportItemRaw,
): BlogImportTaskApi.ImportItem {
  return {
    id: raw.id,
    filename: raw.filename,
    success: raw.success,
    articleId: raw.article_id,
    title: raw.title,
    slug: raw.slug,
    error: raw.error,
    createTime: raw.create_time,
  };
}

function normalizeTask(
  raw: BlogImportTaskApi.ImportTaskRaw,
): BlogImportTaskApi.ImportTaskItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    status: raw.status,
    totalCount: raw.total_count,
    processedCount: raw.processed_count,
    successCount: raw.success_count,
    failCount: raw.fail_count,
    postStatus: raw.post_status,
    visibility: raw.visibility,
    postType: raw.post_type,
    rehostImages: raw.rehost_images,
    errorMessage: raw.error_message,
    startedAt: raw.started_at,
    finishedAt: raw.finished_at,
    createTime: raw.create_time,
    items: (raw.items ?? []).map(normalizeItem),
  };
}

/** 分页查询导入任务（不含逐文件明细） */
export async function getBlogImportTaskPageApi(
  params: BlogImportTaskApi.TaskPageQuery,
) {
  const result = await requestClient.get<
    BlogImportTaskApi.PageResult<BlogImportTaskApi.ImportTaskRaw>
  >('/blog/admin/articles/import-tasks/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeTask),
  } as BlogImportTaskApi.PageResult<BlogImportTaskApi.ImportTaskItem>;
}

/** 查询导入任务详情（含逐文件明细） */
export async function getBlogImportTaskDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogImportTaskApi.ImportTaskRaw>(
    `/blog/admin/articles/import-tasks/${id}`,
  );
  return normalizeTask(raw);
}
