import { requestClient } from '#/api/request';

/**
 * AI 中转-套餐 API（含限制/模型子资源）
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayPackageApi {
  export interface PackagePageQuery {
    pageNum?: number;
    pageSize?: number;
    providerId?: number | string;
    packageTypeId?: number | string;
    keyword?: string;
    status?: number;
  }

  export interface PackageItem {
    id: number | string;
    providerId: number | string;
    providerName?: string;
    packageTypeId: number | string;
    packageTypeCode?: string;
    packageTypeName?: string;
    name: string;
    price: number | string;
    originalPrice?: number | string;
    currency?: string;
    isRecommended?: number;
    recommendScore?: number | string;
    description?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PackageCreateParams {
    providerId: number | string;
    packageTypeId: number | string;
    name: string;
    price: number | string;
    originalPrice?: number | string;
    currency?: string;
    isRecommended?: number;
    recommendScore?: number | string;
    description?: string;
    sortOrder?: number;
    status?: number;
  }

  export type PackageUpdateParams = Partial<Omit<PackageCreateParams, 'providerId'>>;

  export interface PackagePageResult {
    records: PackageItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // -------------------- 套餐限制（子资源） --------------------

  export interface PackageLimitItem {
    id: number | string;
    packageId: number | string;
    limitType: number;
    quotaAmount: number | string;
    quotaUnit: string;
    resetCycle?: number;
    overLimitStrategy?: number;
    description?: string;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PackageLimitParams {
    limitType: number;
    quotaAmount: number | string;
    quotaUnit: string;
    resetCycle?: number;
    overLimitStrategy?: number;
    description?: string;
    status?: number;
  }

  // -------------------- 套餐模型（子资源） --------------------

  export interface PackageModelItem {
    id: number | string;
    packageId: number | string;
    modelId: number | string;
    modelCode?: string;
    modelName?: string;
    modelVendor?: string;
    providerModelCode?: string;
    consumeMultiplier?: number | string;
    minChargeAmount?: number | string;
    maxContextTokens?: number;
    inputPricePerMillionTokens?: number | string;
    outputPricePerMillionTokens?: number | string;
    isDefault?: number;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PackageModelParams {
    modelId: number | string;
    providerModelCode?: string;
    consumeMultiplier?: number | string;
    minChargeAmount?: number | string;
    maxContextTokens?: number;
    inputPricePerMillionTokens?: number | string;
    outputPricePerMillionTokens?: number | string;
    isDefault?: number;
    sortOrder?: number;
    status?: number;
  }
}

export async function getAiRelayPackagePageApi(
  params: AiRelayPackageApi.PackagePageQuery,
) {
  return requestClient.get<AiRelayPackageApi.PackagePageResult>(
    '/blog/admin/ai-relay/packages/page',
    { params },
  );
}

export async function getAiRelayPackageDetailApi(id: number | string) {
  return requestClient.get<AiRelayPackageApi.PackageItem>(
    `/blog/admin/ai-relay/packages/${id}`,
  );
}

export async function createAiRelayPackageApi(
  data: AiRelayPackageApi.PackageCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/packages',
    data,
  );
}

export async function updateAiRelayPackageApi(
  id: number | string,
  data: AiRelayPackageApi.PackageUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/ai-relay/packages/${id}`, data);
}

export async function updateAiRelayPackageStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/packages/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayPackageApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/packages/${id}`);
}

// -------------------- 套餐限制 --------------------

export async function getAiRelayPackageLimitListApi(packageId: number | string) {
  const raw = await requestClient.get<AiRelayPackageApi.PackageLimitItem[]>(
    `/blog/admin/ai-relay/packages/${packageId}/limits`,
  );
  return raw ?? [];
}

export async function createAiRelayPackageLimitApi(
  packageId: number | string,
  data: AiRelayPackageApi.PackageLimitParams,
) {
  return requestClient.post<number | string>(
    `/blog/admin/ai-relay/packages/${packageId}/limits`,
    data,
  );
}

export async function updateAiRelayPackageLimitApi(
  packageId: number | string,
  limitId: number | string,
  data: AiRelayPackageApi.PackageLimitParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/packages/${packageId}/limits/${limitId}`,
    data,
  );
}

export async function deleteAiRelayPackageLimitApi(
  packageId: number | string,
  limitId: number | string,
) {
  return requestClient.delete<void>(
    `/blog/admin/ai-relay/packages/${packageId}/limits/${limitId}`,
  );
}

// -------------------- 套餐模型 --------------------

export async function getAiRelayPackageModelListApi(packageId: number | string) {
  const raw = await requestClient.get<AiRelayPackageApi.PackageModelItem[]>(
    `/blog/admin/ai-relay/packages/${packageId}/models`,
  );
  return raw ?? [];
}

export async function createAiRelayPackageModelApi(
  packageId: number | string,
  data: AiRelayPackageApi.PackageModelParams,
) {
  return requestClient.post<number | string>(
    `/blog/admin/ai-relay/packages/${packageId}/models`,
    data,
  );
}

export async function updateAiRelayPackageModelApi(
  packageId: number | string,
  packageModelId: number | string,
  data: AiRelayPackageApi.PackageModelParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/packages/${packageId}/models/${packageModelId}`,
    data,
  );
}

export async function deleteAiRelayPackageModelApi(
  packageId: number | string,
  packageModelId: number | string,
) {
  return requestClient.delete<void>(
    `/blog/admin/ai-relay/packages/${packageId}/models/${packageModelId}`,
  );
}
