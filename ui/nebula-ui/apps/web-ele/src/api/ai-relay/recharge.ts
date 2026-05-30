import { requestClient } from '#/api/request';

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

  export interface RechargeItemRaw {
    id: number | string;
    provider_id: number | string;
    provider_name?: string;
    package_id?: number | string;
    package_name?: string;
    amount: number | string;
    currency: string;
    exchange_rate?: number | string;
    cny_amount?: number | string;
    payment_method_id?: number | string;
    payment_method_name?: string;
    recharge_time: string;
    order_no?: string;
    voucher_file_id?: number | string;
    voucher_url?: string;
    remark?: string;
    status: number;
    create_time?: string;
    update_time?: string;
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
    provider_id: number | string;
    package_id?: number | string;
    amount: number | string;
    currency?: string;
    exchange_rate?: number | string;
    cny_amount?: number | string;
    payment_method_id?: number | string;
    recharge_time: string;
    order_no?: string;
    voucher_file_id?: number | string;
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

  export interface RechargeStatsRaw {
    provider_id: number | string;
    provider_name?: string;
    recharge_count: number;
    total_cny_amount: number | string;
    last_recharge_time?: string;
  }

  export interface RechargeStats {
    providerId: number | string;
    providerName?: string;
    rechargeCount: number;
    totalCnyAmount: number | string;
    lastRechargeTime?: string;
  }
}

function normalizeRecharge(
  raw: AiRelayRechargeApi.RechargeItemRaw,
): AiRelayRechargeApi.RechargeItem {
  return {
    id: raw.id,
    providerId: raw.provider_id,
    providerName: raw.provider_name,
    packageId: raw.package_id,
    packageName: raw.package_name,
    amount: raw.amount,
    currency: raw.currency,
    exchangeRate: raw.exchange_rate,
    cnyAmount: raw.cny_amount,
    paymentMethodId: raw.payment_method_id,
    paymentMethodName: raw.payment_method_name,
    rechargeTime: raw.recharge_time,
    orderNo: raw.order_no,
    voucherFileId: raw.voucher_file_id,
    voucherUrl: raw.voucher_url,
    remark: raw.remark,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeStats(
  raw: AiRelayRechargeApi.RechargeStatsRaw,
): AiRelayRechargeApi.RechargeStats {
  return {
    providerId: raw.provider_id,
    providerName: raw.provider_name,
    rechargeCount: raw.recharge_count,
    totalCnyAmount: raw.total_cny_amount,
    lastRechargeTime: raw.last_recharge_time,
  };
}

export async function getAiRelayRechargePageApi(
  params: AiRelayRechargeApi.RechargePageQuery,
) {
  const result = await requestClient.get<{
    records: AiRelayRechargeApi.RechargeItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/ai-relay/recharges/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeRecharge),
  } as AiRelayRechargeApi.RechargePageResult;
}

export async function getAiRelayRechargeDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayRechargeApi.RechargeItemRaw>(
    `/blog/admin/ai-relay/recharges/${id}`,
  );
  return normalizeRecharge(raw);
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
  const raw = await requestClient.get<AiRelayRechargeApi.RechargeStatsRaw[]>(
    '/blog/admin/ai-relay/recharges/stats/by-provider',
    { params: providerId == null ? {} : { providerId } },
  );
  return (raw ?? []).map(normalizeStats);
}
