import { requestClient } from '#/api/request';

export namespace SpaceFolderApi {
  export interface FolderItemRaw {
    id: number | string;
    user_id?: number | string;
    parent_id?: number | string | null;
    ancestors?: string;
    name: string;
    level?: number;
    sort_order?: number;
    source?: string;
    source_key?: string;
    remark?: string;
    create_time?: string;
    update_time?: string;
    children?: FolderItemRaw[];
  }

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

function normalizeFolder(
  raw: SpaceFolderApi.FolderItemRaw,
): SpaceFolderApi.FolderItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    parentId: raw.parent_id ?? null,
    ancestors: raw.ancestors,
    name: raw.name,
    level: raw.level,
    sortOrder: raw.sort_order,
    source: raw.source,
    sourceKey: raw.source_key,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
    children: raw.children?.map(normalizeFolder) ?? [],
  };
}

function serializeCreate(data: SpaceFolderApi.FolderCreateParams) {
  return {
    name: data.name,
    parent_id: data.parentId ?? undefined,
    sort_order: data.sortOrder,
    source: data.source,
    source_key: data.sourceKey,
    remark: data.remark,
  };
}

function serializeUpdate(data: SpaceFolderApi.FolderUpdateParams) {
  const payload: Record<string, unknown> = {};
  if ('name' in data) payload.name = data.name;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;
  if ('remark' in data) payload.remark = data.remark;
  return payload;
}

function serializeMove(data: SpaceFolderApi.FolderMoveParams) {
  return {
    target_parent_id: data.targetParentId,
    sort_order: data.sortOrder,
  };
}

/** 获取当前用户的目录树 */
export async function getSpaceFolderTreeApi() {
  const data = await requestClient.get<SpaceFolderApi.FolderItemRaw[]>(
    '/space/admin/bookmark-folders/tree',
  );
  return (data ?? []).map(normalizeFolder);
}

export async function getSpaceFolderDetailApi(id: number | string) {
  const raw = await requestClient.get<SpaceFolderApi.FolderItemRaw>(
    `/space/admin/bookmark-folders/${id}`,
  );
  return normalizeFolder(raw);
}

export async function createSpaceFolderApi(
  data: SpaceFolderApi.FolderCreateParams,
) {
  return requestClient.post<number | string>(
    '/space/admin/bookmark-folders',
    serializeCreate(data),
  );
}

export async function updateSpaceFolderApi(
  id: number | string,
  data: SpaceFolderApi.FolderUpdateParams,
) {
  return requestClient.put<void>(
    `/space/admin/bookmark-folders/${id}`,
    serializeUpdate(data),
  );
}

export async function moveSpaceFolderApi(
  id: number | string,
  data: SpaceFolderApi.FolderMoveParams,
) {
  return requestClient.put<void>(
    `/space/admin/bookmark-folders/${id}/move`,
    serializeMove(data),
  );
}

export async function deleteSpaceFolderApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/bookmark-folders/${id}`);
}
