import { requestClient } from '#/api/request';

/**
 * 空间-标签 API
 * space 服务 Jackson 已回归默认 camelCase（移除了 SNAKE_CASE 配置），出入参均为 camelCase，本层直接透传。
 */
export namespace SpaceTagApi {
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

export async function getSpaceTagListApi() {
  const data = await requestClient.get<SpaceTagApi.TagItem[]>(
    '/space/admin/space-tags',
  );
  return data ?? [];
}

export async function getSpaceTagDetailApi(id: number | string) {
  return requestClient.get<SpaceTagApi.TagItem>(`/space/admin/space-tags/${id}`);
}

export async function createSpaceTagApi(data: SpaceTagApi.TagCreateParams) {
  return requestClient.post<number | string>('/space/admin/space-tags', data);
}

export async function updateSpaceTagApi(
  id: number | string,
  data: SpaceTagApi.TagUpdateParams,
) {
  return requestClient.put<void>(`/space/admin/space-tags/${id}`, data);
}

export async function deleteSpaceTagApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/space-tags/${id}`);
}
