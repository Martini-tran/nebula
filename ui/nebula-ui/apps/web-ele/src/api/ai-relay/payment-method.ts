import { requestClient } from '#/api/request';

/**
 * AI 中转-支付方式 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayPaymentMethodApi {
  export interface PaymentMethodItem {
    id: number | string;
    code: string;
    name: string;
    iconFileId?: number | string;
    iconUrl?: string;
    description?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PaymentMethodParams {
    code: string;
    name: string;
    iconFileId?: number | string;
    description?: string;
    sortOrder?: number;
    status?: number;
  }
}

export async function getAiRelayPaymentMethodListApi(status?: number) {
  const raw = await requestClient.get<
    AiRelayPaymentMethodApi.PaymentMethodItem[]
  >('/blog/admin/ai-relay/payment-methods', {
    params: status == null ? {} : { status },
  });
  return raw ?? [];
}

export async function getAiRelayPaymentMethodDetailApi(id: number | string) {
  return requestClient.get<AiRelayPaymentMethodApi.PaymentMethodItem>(
    `/blog/admin/ai-relay/payment-methods/${id}`,
  );
}

export async function createAiRelayPaymentMethodApi(
  data: AiRelayPaymentMethodApi.PaymentMethodParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/payment-methods',
    data,
  );
}

export async function updateAiRelayPaymentMethodApi(
  id: number | string,
  data: AiRelayPaymentMethodApi.PaymentMethodParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/payment-methods/${id}`,
    data,
  );
}

export async function updateAiRelayPaymentMethodStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/payment-methods/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayPaymentMethodApi(id: number | string) {
  return requestClient.delete<void>(
    `/blog/admin/ai-relay/payment-methods/${id}`,
  );
}
