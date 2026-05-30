import { requestClient } from '#/api/request';

export namespace AiRelayPaymentMethodApi {
  export interface PaymentMethodItemRaw {
    id: number | string;
    code: string;
    name: string;
    icon_file_id?: number | string;
    icon_url?: string;
    description?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
  }

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
    icon_file_id?: number | string;
    description?: string;
    sort_order?: number;
    status?: number;
  }
}

function normalizePaymentMethod(
  raw: AiRelayPaymentMethodApi.PaymentMethodItemRaw,
): AiRelayPaymentMethodApi.PaymentMethodItem {
  return {
    id: raw.id,
    code: raw.code,
    name: raw.name,
    iconFileId: raw.icon_file_id,
    iconUrl: raw.icon_url,
    description: raw.description,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getAiRelayPaymentMethodListApi(status?: number) {
  const raw = await requestClient.get<
    AiRelayPaymentMethodApi.PaymentMethodItemRaw[]
  >('/blog/admin/ai-relay/payment-methods', {
    params: status == null ? {} : { status },
  });
  return (raw ?? []).map(normalizePaymentMethod);
}

export async function getAiRelayPaymentMethodDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayPaymentMethodApi.PaymentMethodItemRaw>(
    `/blog/admin/ai-relay/payment-methods/${id}`,
  );
  return normalizePaymentMethod(raw);
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
