import { requestClient } from '#/api/request';

/**
 * AI 中转-套餐类型 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayPackageTypeApi {
  export interface PackageTypeItem {
    id: number | string;
    code: string;
    name: string;
    billingMode: number;
    durationValue?: number;
    durationUnit?: number;
    description?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PackageTypeParams {
    code: string;
    name: string;
    billingMode: number;
    durationValue?: number;
    durationUnit?: number;
    description?: string;
    sortOrder?: number;
    status?: number;
  }
}

export async function getAiRelayPackageTypeListApi(status?: number) {
  const raw = await requestClient.get<AiRelayPackageTypeApi.PackageTypeItem[]>(
    '/blog/admin/ai-relay/package-types',
    { params: status == null ? {} : { status } },
  );
  return raw ?? [];
}

export async function getAiRelayPackageTypeDetailApi(id: number | string) {
  return requestClient.get<AiRelayPackageTypeApi.PackageTypeItem>(
    `/blog/admin/ai-relay/package-types/${id}`,
  );
}

export async function createAiRelayPackageTypeApi(
  data: AiRelayPackageTypeApi.PackageTypeParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/package-types',
    data,
  );
}

export async function updateAiRelayPackageTypeApi(
  id: number | string,
  data: AiRelayPackageTypeApi.PackageTypeParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/package-types/${id}`,
    data,
  );
}

export async function updateAiRelayPackageTypeStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/package-types/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayPackageTypeApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/package-types/${id}`);
}
