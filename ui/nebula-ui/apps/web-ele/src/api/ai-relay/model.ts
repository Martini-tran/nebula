import { requestClient } from '#/api/request';

/**
 * AI 中转-模型 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayModelApi {
  export interface ModelPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    modelVendor?: string;
    modelType?: number;
    status?: number;
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
    modelVendor?: string;
    modelType: number;
    description?: string;
    sortOrder?: number;
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

export async function getAiRelayModelPageApi(
  params: AiRelayModelApi.ModelPageQuery,
) {
  return requestClient.get<AiRelayModelApi.ModelPageResult>(
    '/blog/admin/ai-relay/models/page',
    { params },
  );
}

/** 获取所有可用模型（启用状态），供套餐绑定模型时下拉用。无后端 list 接口，使用 page 拉大页 */
export async function getAiRelayModelOptionsApi() {
  const result = await requestClient.get<{
    records: AiRelayModelApi.ModelItem[];
  }>('/blog/admin/ai-relay/models/page', {
    params: { pageNum: 1, pageSize: 500, status: 1 },
  });
  return result.records ?? [];
}

export async function getAiRelayModelDetailApi(id: number | string) {
  return requestClient.get<AiRelayModelApi.ModelItem>(
    `/blog/admin/ai-relay/models/${id}`,
  );
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
