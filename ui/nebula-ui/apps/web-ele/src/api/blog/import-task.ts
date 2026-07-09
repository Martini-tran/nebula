import { requestClient } from '#/api/request';

/**
 * 博客-文章导入任务 API（只读）
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出参均为 camelCase，本层直接透传。
 */
export namespace BlogImportTaskApi {
  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    status?: string;
    userId?: number | string;
  }

  /** 导入任务（camelCase） */
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

/** 分页查询导入任务（不含逐文件明细） */
export async function getBlogImportTaskPageApi(
  params: BlogImportTaskApi.TaskPageQuery,
) {
  return requestClient.get<
    BlogImportTaskApi.PageResult<BlogImportTaskApi.ImportTaskItem>
  >('/blog/admin/articles/import-tasks/page', { params });
}

/** 查询导入任务详情（含逐文件明细） */
export async function getBlogImportTaskDetailApi(id: number | string) {
  return requestClient.get<BlogImportTaskApi.ImportTaskItem>(
    `/blog/admin/articles/import-tasks/${id}`,
  );
}
