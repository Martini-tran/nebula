import { requestClient } from '#/api/request';

export namespace AiRelayModelApi {
  export interface ModelPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    modelVendor?: string;
    modelType?: number;
    status?: number;
  }

  export interface ModelItemRaw {
    id: number | string;
    code: string;
    name: string;
    model_vendor?: string;
    model_type: number;
    description?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
  }

  export interface ModelItem {
    id: number | string;
    code: string;
    name: string;
    modelVendor?: string;
    modelType: number;
    description?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface ModelParams {
    code: string;
    name: string;
    model_vendor?: string;
    model_type: number;
    description?: string;
    sort_order?: number;
    status?: number;
  }

  export interface ModelPageResult {
    records: ModelItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeModel(
  raw: AiRelayModelApi.ModelItemRaw,
): AiRelayModelApi.ModelItem {
  return {
    id: raw.id,
    code: raw.code,
    name: raw.name,
    modelVendor: raw.model_vendor,
    modelType: raw.model_type,
    description: raw.description,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getAiRelayModelPageApi(
  params: AiRelayModelApi.ModelPageQuery,
) {
  const result = await requestClient.get<{
    records: AiRelayModelApi.ModelItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/ai-relay/models/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeModel),
  } as AiRelayModelApi.ModelPageResult;
}

/** 获取所有可用模型（启用状态），供套餐绑定模型时下拉用。无后端 list 接口，使用 page 拉大页 */
export async function getAiRelayModelOptionsApi() {
  const result = await requestClient.get<{
    records: AiRelayModelApi.ModelItemRaw[];
  }>('/blog/admin/ai-relay/models/page', {
    params: { pageNum: 1, pageSize: 500, status: 1 },
  });
  return (result.records ?? []).map(normalizeModel);
}

export async function getAiRelayModelDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayModelApi.ModelItemRaw>(
    `/blog/admin/ai-relay/models/${id}`,
  );
  return normalizeModel(raw);
}

export async function createAiRelayModelApi(data: AiRelayModelApi.ModelParams) {
  return requestClient.post<number | string>('/blog/admin/ai-relay/models', data);
}

export async function updateAiRelayModelApi(
  id: number | string,
  data: AiRelayModelApi.ModelParams,
) {
  return requestClient.put<void>(`/blog/admin/ai-relay/models/${id}`, data);
}

export async function updateAiRelayModelStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/models/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayModelApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/models/${id}`);
}
