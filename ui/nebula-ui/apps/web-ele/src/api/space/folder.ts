import { requestClient } from '#/api/request';

/**
 * 空间-目录 API
 * space 服务 Jackson 已回归默认 camelCase（移除了 SNAKE_CASE 配置），出入参均为 camelCase，本层直接透传。
 */
export namespace SpaceFolderApi {
  export interface FolderItem {
    id: number | string;
    userId?: number | string;
    parentId?: number | string | null;
    ancestors?: string;
    name: string;
    level?: number;
    sortOrder?: number;
    source?: string;
    sourceKey?: string;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    children?: FolderItem[];
  }

  export interface FolderCreateParams {
    name: string;
    parentId?: number | string | null;
    sortOrder?: number;
    source?: string;
    sourceKey?: string;
    remark?: string;
  }

  export type FolderUpdateParams = Partial<{
    name: string;
    sortOrder: number;
    remark: string;
  }>;

  export interface FolderMoveParams {
    targetParentId: number | string;
    sortOrder?: number;
  }
}

/** 获取当前用户的目录树 */
export async function getSpaceFolderTreeApi() {
  const data = await requestClient.get<SpaceFolderApi.FolderItem[]>(
    '/space/admin/bookmark-folders/tree',
  );
  return data ?? [];
}

export async function getSpaceFolderDetailApi(id: number | string) {
  return requestClient.get<SpaceFolderApi.FolderItem>(
    `/space/admin/bookmark-folders/${id}`,
  );
}

export async function createSpaceFolderApi(
  data: SpaceFolderApi.FolderCreateParams,
) {
  return requestClient.post<number | string>(
    '/space/admin/bookmark-folders',
    data,
  );
}

export async function updateSpaceFolderApi(
  id: number | string,
  data: SpaceFolderApi.FolderUpdateParams,
) {
  return requestClient.put<void>(`/space/admin/bookmark-folders/${id}`, data);
}

export async function moveSpaceFolderApi(
  id: number | string,
  data: SpaceFolderApi.FolderMoveParams,
) {
  return requestClient.put<void>(
    `/space/admin/bookmark-folders/${id}/move`,
    data,
  );
}

export async function deleteSpaceFolderApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/bookmark-folders/${id}`);
}
