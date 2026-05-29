import { requestClient } from '#/api/request';

export namespace AiRelayProviderApi {
  export interface ProviderPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
  }

  export interface ProviderItemRaw {
    id: number | string;
    name: string;
    website_url?: string;
    logo_file_id?: number | string;
    logo_url?: string;
    description?: string;
    recommend_score?: number | string;
    sort_order?: number;
    status: number;
    last_sync_time?: string;
    create_time?: string;
    update_time?: string;
  }

  export interface ProviderItem {
    id: number | string;
    name: string;
    websiteUrl?: string;
    logoFileId?: number | string;
    logoUrl?: string;
    description?: string;
    recommendScore?: number | string;
    sortOrder?: number;
    status: number;
    lastSyncTime?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface ProviderCreateParams {
    name: string;
    website_url?: string;
    logo_file_id?: number | string;
    description?: string;
    recommend_score?: number | string;
    sort_order?: number;
    status?: number;
    last_sync_time?: string;
  }

  export type ProviderUpdateParams = Partial<ProviderCreateParams>;

  export interface ProviderPageResult {
    records: ProviderItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // -------------------- 服务商优势（子资源） --------------------

  export interface AdvantageItemRaw {
    id: number | string;
    provider_id: number | string;
    title: string;
    content?: string;
    advantage_type: number;
    icon_file_id?: number | string;
    icon_url?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
  }

  export interface AdvantageItem {
    id: number | string;
    providerId: number | string;
    title: string;
    content?: string;
    advantageType: number;
    iconFileId?: number | string;
    iconUrl?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface AdvantageParams {
    title: string;
    content?: string;
    advantage_type?: number;
    icon_file_id?: number | string;
    sort_order?: number;
    status?: number;
  }

  // -------------------- 服务商支付方式（子资源） --------------------

  export interface ProviderPaymentMethodItemRaw {
    id: number | string;
    provider_id: number | string;
    payment_method_id: number | string;
    payment_method_code?: string;
    payment_method_name?: string;
    remark?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
  }

  export interface ProviderPaymentMethodItem {
    id: number | string;
    providerId: number | string;
    paymentMethodId: number | string;
    paymentMethodCode?: string;
    paymentMethodName?: string;
    remark?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
  }

  export interface BindPaymentMethodsParams {
    payment_method_ids: Array<number | string>;
  }
}

function normalizeProvider(
  raw: AiRelayProviderApi.ProviderItemRaw,
): AiRelayProviderApi.ProviderItem {
  return {
    id: raw.id,
    name: raw.name,
    websiteUrl: raw.website_url,
    logoFileId: raw.logo_file_id,
    logoUrl: raw.logo_url,
    description: raw.description,
    recommendScore: raw.recommend_score,
    sortOrder: raw.sort_order,
    status: raw.status,
    lastSyncTime: raw.last_sync_time,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeAdvantage(
  raw: AiRelayProviderApi.AdvantageItemRaw,
): AiRelayProviderApi.AdvantageItem {
  return {
    id: raw.id,
    providerId: raw.provider_id,
    title: raw.title,
    content: raw.content,
    advantageType: raw.advantage_type,
    iconFileId: raw.icon_file_id,
    iconUrl: raw.icon_url,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeProviderPaymentMethod(
  raw: AiRelayProviderApi.ProviderPaymentMethodItemRaw,
): AiRelayProviderApi.ProviderPaymentMethodItem {
  return {
    id: raw.id,
    providerId: raw.provider_id,
    paymentMethodId: raw.payment_method_id,
    paymentMethodCode: raw.payment_method_code,
    paymentMethodName: raw.payment_method_name,
    remark: raw.remark,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
  };
}

export async function getAiRelayProviderPageApi(
  params: AiRelayProviderApi.ProviderPageQuery,
) {
  const result = await requestClient.get<{
    records: AiRelayProviderApi.ProviderItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/ai-relay/providers/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeProvider),
  } as AiRelayProviderApi.ProviderPageResult;
}

export async function getAiRelayProviderDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayProviderApi.ProviderItemRaw>(
    `/blog/admin/ai-relay/providers/${id}`,
  );
  return normalizeProvider(raw);
}

export async function createAiRelayProviderApi(
  data: AiRelayProviderApi.ProviderCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/providers',
    data,
  );
}

export async function updateAiRelayProviderApi(
  id: number | string,
  data: AiRelayProviderApi.ProviderUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/ai-relay/providers/${id}`, data);
}

export async function updateAiRelayProviderStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/providers/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayProviderApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/providers/${id}`);
}

// -------------------- 服务商优势 --------------------

export async function getAiRelayProviderAdvantageListApi(providerId: number | string) {
  const raw = await requestClient.get<AiRelayProviderApi.AdvantageItemRaw[]>(
    `/blog/admin/ai-relay/providers/${providerId}/advantages`,
  );
  return (raw ?? []).map(normalizeAdvantage);
}

export async function createAiRelayProviderAdvantageApi(
  providerId: number | string,
  data: AiRelayProviderApi.AdvantageParams,
) {
  return requestClient.post<number | string>(
    `/blog/admin/ai-relay/providers/${providerId}/advantages`,
    data,
  );
}

export async function updateAiRelayProviderAdvantageApi(
  providerId: number | string,
  advantageId: number | string,
  data: AiRelayProviderApi.AdvantageParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/providers/${providerId}/advantages/${advantageId}`,
    data,
  );
}

export async function deleteAiRelayProviderAdvantageApi(
  providerId: number | string,
  advantageId: number | string,
) {
  return requestClient.delete<void>(
    `/blog/admin/ai-relay/providers/${providerId}/advantages/${advantageId}`,
  );
}

// -------------------- 服务商支付方式 --------------------

export async function getAiRelayProviderPaymentMethodListApi(
  providerId: number | string,
) {
  const raw = await requestClient.get<
    AiRelayProviderApi.ProviderPaymentMethodItemRaw[]
  >(`/blog/admin/ai-relay/providers/${providerId}/payment-methods`);
  return (raw ?? []).map(normalizeProviderPaymentMethod);
}

export async function bindAiRelayProviderPaymentMethodsApi(
  providerId: number | string,
  data: AiRelayProviderApi.BindPaymentMethodsParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/providers/${providerId}/payment-methods`,
    data,
  );
}
