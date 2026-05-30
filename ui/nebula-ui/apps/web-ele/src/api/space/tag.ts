import { requestClient } from '#/api/request';

export namespace SpaceTagApi {
  export interface TagItemRaw {
    id: number | string;
    user_id?: number | string;
    name: string;
    color?: string;
    sort_order?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
  }

  export interface TagItem {
    id: number | string;
    userId?: number | string;
    name: string;
    color?: string;
    sortOrder?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface TagCreateParams {
    name: string;
    color?: string;
    sortOrder?: number;
    remark?: string;
  }

  export type TagUpdateParams = Partial<TagCreateParams>;
}

function normalize(raw: SpaceTagApi.TagItemRaw): SpaceTagApi.TagItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    name: raw.name,
    color: raw.color,
    sortOrder: raw.sort_order,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function serialize(
  data: SpaceTagApi.TagCreateParams | SpaceTagApi.TagUpdateParams,
) {
  const payload: Record<string, unknown> = {};
  if ('name' in data) payload.name = data.name;
  if ('color' in data) payload.color = data.color;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;
  if ('remark' in data) payload.remark = data.remark;
  return payload;
}

export async function getSpaceTagListApi() {
  const data = await requestClient.get<SpaceTagApi.TagItemRaw[]>(
    '/space/admin/space-tags',
  );
  return (data ?? []).map(normalize);
}

export async function getSpaceTagDetailApi(id: number | string) {
  const raw = await requestClient.get<SpaceTagApi.TagItemRaw>(
    `/space/admin/space-tags/${id}`,
  );
  return normalize(raw);
}

export async function createSpaceTagApi(data: SpaceTagApi.TagCreateParams) {
  return requestClient.post<number | string>(
    '/space/admin/space-tags',
    serialize(data),
  );
}

export async function updateSpaceTagApi(
  id: number | string,
  data: SpaceTagApi.TagUpdateParams,
) {
  return requestClient.put<void>(
    `/space/admin/space-tags/${id}`,
    serialize(data),
  );
}

export async function deleteSpaceTagApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/space-tags/${id}`);
}
