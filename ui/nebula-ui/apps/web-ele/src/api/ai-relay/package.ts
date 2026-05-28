import { requestClient } from '#/api/request';

export namespace AiRelayPackageApi {
  export interface PackagePageQuery {
    pageNum?: number;
    pageSize?: number;
    providerId?: number | string;
    packageTypeId?: number | string;
    keyword?: string;
    status?: number;
  }

  export interface PackageItemRaw {
    id: number | string;
    provider_id: number | string;
    provider_name?: string;
    package_type_id: number | string;
    package_type_code?: string;
    package_type_name?: string;
    name: string;
    price: number | string;
    original_price?: number | string;
    currency?: string;
    is_recommended?: number;
    recommend_score?: number | string;
    description?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
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
    provider_id: number | string;
    package_type_id: number | string;
    name: string;
    price: number | string;
    original_price?: number | string;
    currency?: string;
    is_recommended?: number;
    recommend_score?: number | string;
    description?: string;
    sort_order?: number;
    status?: number;
  }

  export type PackageUpdateParams = Partial<Omit<PackageCreateParams, 'provider_id'>>;

  export interface PackagePageResult {
    records: PackageItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // -------------------- 套餐限制（子资源） --------------------

  export interface PackageLimitItemRaw {
    id: number | string;
    package_id: number | string;
    limit_type: number;
    quota_amount: number | string;
    quota_unit: string;
    reset_cycle?: number;
    over_limit_strategy?: number;
    description?: string;
    status: number;
    create_time?: string;
    update_time?: string;
  }

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
    limit_type: number;
    quota_amount: number | string;
    quota_unit: string;
    reset_cycle?: number;
    over_limit_strategy?: number;
    description?: string;
    status?: number;
  }

  // -------------------- 套餐模型（子资源） --------------------

  export interface PackageModelItemRaw {
    id: number | string;
    package_id: number | string;
    model_id: number | string;
    model_code?: string;
    model_name?: string;
    model_vendor?: string;
    provider_model_code?: string;
    consume_multiplier?: number | string;
    min_charge_amount?: number | string;
    max_context_tokens?: number;
    is_default?: number;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
  }

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
    isDefault?: number;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PackageModelParams {
    model_id: number | string;
    provider_model_code?: string;
    consume_multiplier?: number | string;
    min_charge_amount?: number | string;
    max_context_tokens?: number;
    is_default?: number;
    sort_order?: number;
    status?: number;
  }
}

function normalizePackage(
  raw: AiRelayPackageApi.PackageItemRaw,
): AiRelayPackageApi.PackageItem {
  return {
    id: raw.id,
    providerId: raw.provider_id,
    providerName: raw.provider_name,
    packageTypeId: raw.package_type_id,
    packageTypeCode: raw.package_type_code,
    packageTypeName: raw.package_type_name,
    name: raw.name,
    price: raw.price,
    originalPrice: raw.original_price,
    currency: raw.currency,
    isRecommended: raw.is_recommended,
    recommendScore: raw.recommend_score,
    description: raw.description,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizePackageLimit(
  raw: AiRelayPackageApi.PackageLimitItemRaw,
): AiRelayPackageApi.PackageLimitItem {
  return {
    id: raw.id,
    packageId: raw.package_id,
    limitType: raw.limit_type,
    quotaAmount: raw.quota_amount,
    quotaUnit: raw.quota_unit,
    resetCycle: raw.reset_cycle,
    overLimitStrategy: raw.over_limit_strategy,
    description: raw.description,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizePackageModel(
  raw: AiRelayPackageApi.PackageModelItemRaw,
): AiRelayPackageApi.PackageModelItem {
  return {
    id: raw.id,
    packageId: raw.package_id,
    modelId: raw.model_id,
    modelCode: raw.model_code,
    modelName: raw.model_name,
    modelVendor: raw.model_vendor,
    providerModelCode: raw.provider_model_code,
    consumeMultiplier: raw.consume_multiplier,
    minChargeAmount: raw.min_charge_amount,
    maxContextTokens: raw.max_context_tokens,
    isDefault: raw.is_default,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getAiRelayPackagePageApi(
  params: AiRelayPackageApi.PackagePageQuery,
) {
  const result = await requestClient.get<{
    records: AiRelayPackageApi.PackageItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/ai-relay/packages/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizePackage),
  } as AiRelayPackageApi.PackagePageResult;
}

export async function getAiRelayPackageDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayPackageApi.PackageItemRaw>(
    `/blog/admin/ai-relay/packages/${id}`,
  );
  return normalizePackage(raw);
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
  const raw = await requestClient.get<AiRelayPackageApi.PackageLimitItemRaw[]>(
    `/blog/admin/ai-relay/packages/${packageId}/limits`,
  );
  return (raw ?? []).map(normalizePackageLimit);
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
  const raw = await requestClient.get<AiRelayPackageApi.PackageModelItemRaw[]>(
    `/blog/admin/ai-relay/packages/${packageId}/models`,
  );
  return (raw ?? []).map(normalizePackageModel);
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
