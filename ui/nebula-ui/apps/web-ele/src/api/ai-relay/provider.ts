import { requestClient } from '#/api/request';

/**
 * AI 中转-服务商 API（含优势/支付方式子资源）
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayProviderApi {
  export interface ProviderPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
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
    websiteUrl?: string;
    logoFileId?: number | string;
    description?: string;
    recommendScore?: number | string;
    sortOrder?: number;
    status?: number;
    lastSyncTime?: string;
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
    advantageType?: number;
    iconFileId?: number | string;
    sortOrder?: number;
    status?: number;
  }

  // -------------------- 服务商支付方式（子资源） --------------------

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
    paymentMethodIds: Array<number | string>;
  }
}

export async function getAiRelayProviderPageApi(
  params: AiRelayProviderApi.ProviderPageQuery,
) {
  return requestClient.get<AiRelayProviderApi.ProviderPageResult>(
    '/blog/admin/ai-relay/providers/page',
    { params },
  );
}

export async function getAiRelayProviderDetailApi(id: number | string) {
  return requestClient.get<AiRelayProviderApi.ProviderItem>(
    `/blog/admin/ai-relay/providers/${id}`,
  );
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
  const raw = await requestClient.get<AiRelayProviderApi.AdvantageItem[]>(
    `/blog/admin/ai-relay/providers/${providerId}/advantages`,
  );
  return raw ?? [];
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
    AiRelayProviderApi.ProviderPaymentMethodItem[]
  >(`/blog/admin/ai-relay/providers/${providerId}/payment-methods`);
  return raw ?? [];
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
