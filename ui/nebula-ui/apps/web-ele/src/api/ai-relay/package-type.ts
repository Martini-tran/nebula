import { requestClient } from '#/api/request';

export namespace AiRelayPackageTypeApi {
  export interface PackageTypeItemRaw {
    id: number | string;
    code: string;
    name: string;
    billing_mode: number;
    duration_value?: number;
    duration_unit?: number;
    description?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
  }

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
    billing_mode: number;
    duration_value?: number;
    duration_unit?: number;
    description?: string;
    sort_order?: number;
    status?: number;
  }
}

function normalizePackageType(
  raw: AiRelayPackageTypeApi.PackageTypeItemRaw,
): AiRelayPackageTypeApi.PackageTypeItem {
  return {
    id: raw.id,
    code: raw.code,
    name: raw.name,
    billingMode: raw.billing_mode,
    durationValue: raw.duration_value,
    durationUnit: raw.duration_unit,
    description: raw.description,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getAiRelayPackageTypeListApi(status?: number) {
  const raw = await requestClient.get<AiRelayPackageTypeApi.PackageTypeItemRaw[]>(
    '/blog/admin/ai-relay/package-types',
    { params: status == null ? {} : { status } },
  );
  return (raw ?? []).map(normalizePackageType);
}

export async function getAiRelayPackageTypeDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayPackageTypeApi.PackageTypeItemRaw>(
    `/blog/admin/ai-relay/package-types/${id}`,
  );
  return normalizePackageType(raw);
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
