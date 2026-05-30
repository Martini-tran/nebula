import { requestClient } from '#/api/request';

export namespace SpaceTaskApi {
  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    userId?: number | string;
    status?: number;
  }

  export interface ImportTaskRaw {
    id: number | string;
    user_id?: number | string;
    file_id?: number | string;
    source?: string;
    status?: number;
    total_count?: number;
    success_count?: number;
    duplicate_count?: number;
    fail_count?: number;
    error_msg?: string;
    create_time?: string;
    update_time?: string;
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

  export interface ExportTaskRaw {
    id: number | string;
    user_id?: number | string;
    file_id?: number | string;
    export_type?: string;
    scope_type?: string;
    scope_id?: number | string;
    status?: number;
    total_count?: number;
    error_msg?: string;
    create_time?: string;
    update_time?: string;
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

function normalizeImport(
  raw: SpaceTaskApi.ImportTaskRaw,
): SpaceTaskApi.ImportTaskItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    fileId: raw.file_id,
    source: raw.source,
    status: raw.status,
    totalCount: raw.total_count,
    successCount: raw.success_count,
    duplicateCount: raw.duplicate_count,
    failCount: raw.fail_count,
    errorMsg: raw.error_msg,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeExport(
  raw: SpaceTaskApi.ExportTaskRaw,
): SpaceTaskApi.ExportTaskItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    fileId: raw.file_id,
    exportType: raw.export_type,
    scopeType: raw.scope_type,
    scopeId: raw.scope_id,
    status: raw.status,
    totalCount: raw.total_count,
    errorMsg: raw.error_msg,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getSpaceImportTaskPageApi(
  params: SpaceTaskApi.TaskPageQuery,
) {
  const result = await requestClient.get<
    SpaceTaskApi.PageResult<SpaceTaskApi.ImportTaskRaw>
  >('/space/admin/bookmark-import-tasks/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeImport),
  } as SpaceTaskApi.PageResult<SpaceTaskApi.ImportTaskItem>;
}

export async function cancelSpaceImportTaskApi(id: number | string) {
  return requestClient.post<void>(
    `/space/admin/bookmark-import-tasks/${id}/cancel`,
  );
}

export async function getSpaceExportTaskPageApi(
  params: SpaceTaskApi.TaskPageQuery,
) {
  const result = await requestClient.get<
    SpaceTaskApi.PageResult<SpaceTaskApi.ExportTaskRaw>
  >('/space/admin/bookmark-export-tasks/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeExport),
  } as SpaceTaskApi.PageResult<SpaceTaskApi.ExportTaskItem>;
}

export async function cancelSpaceExportTaskApi(id: number | string) {
  return requestClient.post<void>(
    `/space/admin/bookmark-export-tasks/${id}/cancel`,
  );
}
