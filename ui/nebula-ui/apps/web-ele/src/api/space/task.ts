import { requestClient } from '#/api/request';

/**
 * 空间-导入/导出任务 API
 * space 服务 Jackson 已回归默认 camelCase（移除了 SNAKE_CASE 配置），出入参均为 camelCase，本层直接透传。
 */
export namespace SpaceTaskApi {
  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    userId?: number | string;
    status?: number;
  }

  export interface ImportTaskItem {
    id: number | string;
    userId?: number | string;
    fileId?: number | string;
    source?: string;
    status?: number;
    totalCount?: number;
    successCount?: number;
    duplicateCount?: number;
    failCount?: number;
    errorMsg?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface ExportTaskItem {
    id: number | string;
    userId?: number | string;
    fileId?: number | string;
    exportType?: string;
    scopeType?: string;
    scopeId?: number | string;
    status?: number;
    totalCount?: number;
    errorMsg?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

export async function getSpaceImportTaskPageApi(
  params: SpaceTaskApi.TaskPageQuery,
) {
  return requestClient.get<SpaceTaskApi.PageResult<SpaceTaskApi.ImportTaskItem>>(
    '/space/admin/bookmark-import-tasks/page',
    { params },
  );
}

/**
 * 上传 Chrome 书签 HTML 文件并同步导入
 * 后端会解析 Netscape Bookmark 格式，按目录创建条目，返回写入的导入任务统计
 */
export async function importChromeBookmarksApi(file: File) {
  return requestClient.upload<SpaceTaskApi.ImportTaskItem>(
    '/space/admin/bookmark-import-tasks/chrome',
    { file },
  );
}

export async function cancelSpaceImportTaskApi(id: number | string) {
  return requestClient.post<void>(
    `/space/admin/bookmark-import-tasks/${id}/cancel`,
  );
}

export async function getSpaceExportTaskPageApi(
  params: SpaceTaskApi.TaskPageQuery,
) {
  return requestClient.get<SpaceTaskApi.PageResult<SpaceTaskApi.ExportTaskItem>>(
    '/space/admin/bookmark-export-tasks/page',
    { params },
  );
}

/**
 * 导出 Chrome 兼容的 Netscape Bookmark HTML，返回 Blob 用于浏览器下载
 *
 * @param scopeType 范围：all / folder / tag
 * @param scopeId   scopeType 为 folder/tag 时必填
 */
export async function exportChromeBookmarksApi(
  scopeType: 'all' | 'folder' | 'tag' = 'all',
  scopeId?: number | string,
): Promise<Blob> {
  return requestClient.download<Blob>(
    '/space/admin/bookmark-export-tasks/chrome',
    {
      method: 'GET',
      params: { scopeType, scopeId },
    },
  );
}

export async function cancelSpaceExportTaskApi(id: number | string) {
  return requestClient.post<void>(
    `/space/admin/bookmark-export-tasks/${id}/cancel`,
  );
}
