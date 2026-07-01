import { requestClient } from '#/api/request';

/**
 * AI 中转-充值记录 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayRechargeApi {
  export interface RechargePageQuery {
    pageNum?: number;
    pageSize?: number;
    providerId?: number | string;
    packageId?: number | string;
    status?: number;
    startTime?: string;
    endTime?: string;
  }

  export interface RechargeItem {
    id: number | string;
    providerId: number | string;
    providerName?: string;
    packageId?: number | string;
    packageName?: string;
    amount: number | string;
    currency: string;
    exchangeRate?: number | string;
    cnyAmount?: number | string;
    paymentMethodId?: number | string;
    paymentMethodName?: string;
    rechargeTime: string;
    orderNo?: string;
    voucherFileId?: number | string;
    voucherUrl?: string;
    remark?: string;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface RechargeParams {
    providerId: number | string;
    packageId?: number | string;
    amount: number | string;
    currency?: string;
    exchangeRate?: number | string;
    cnyAmount?: number | string;
    paymentMethodId?: number | string;
    rechargeTime: string;
    orderNo?: string;
    voucherFileId?: number | string;
    remark?: string;
    status?: number;
  }

  export interface RechargePageResult {
    records: RechargeItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  export interface RechargeStats {
    providerId: number | string;
    providerName?: string;
    rechargeCount: number;
    totalCnyAmount: number | string;
    lastRechargeTime?: string;
  }
}

export async function getAiRelayRechargePageApi(
  params: AiRelayRechargeApi.RechargePageQuery,
) {
  return requestClient.get<AiRelayRechargeApi.RechargePageResult>(
    '/blog/admin/ai-relay/recharges/page',
    { params },
  );
}

export async function getAiRelayRechargeDetailApi(id: number | string) {
  return requestClient.get<AiRelayRechargeApi.RechargeItem>(
    `/blog/admin/ai-relay/recharges/${id}`,
  );
}

export async function createAiRelayRechargeApi(
  data: AiRelayRechargeApi.RechargeParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/recharges',
    data,
  );
}

export async function updateAiRelayRechargeApi(
  id: number | string,
  data: AiRelayRechargeApi.RechargeParams,
) {
  return requestClient.put<void>(`/blog/admin/ai-relay/recharges/${id}`, data);
}

export async function deleteAiRelayRechargeApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/recharges/${id}`);
}

export async function getAiRelayRechargeStatsApi(providerId?: number | string) {
  const raw = await requestClient.get<AiRelayRechargeApi.RechargeStats[]>(
    '/blog/admin/ai-relay/recharges/stats/by-provider',
    { params: providerId == null ? {} : { providerId } },
  );
  return raw ?? [];
}
