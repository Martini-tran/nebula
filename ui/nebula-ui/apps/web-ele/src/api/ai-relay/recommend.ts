import { requestClient } from '#/api/request';

/**
 * AI 中转-推荐/测评 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace AiRelayRecommendApi {
  export interface RecommendPageQuery {
    pageNum?: number;
    pageSize?: number;
    providerId?: number | string;
    keyword?: string;
    status?: number;
  }

  export interface RecommendItem {
    id: number | string;
    providerId: number | string;
    providerName?: string;
    recommendReason: string;
    reviewContent?: string;
    reviewScore?: number | string;
    pros?: string;
    cons?: string;
    useScenario?: string;
    firstUseTime?: string;
    reviewTime?: string;
    recommendTime?: string;
    sortOrder?: number;
    status: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface RecommendParams {
    providerId: number | string;
    recommendReason: string;
    reviewContent?: string;
    reviewScore?: number | string;
    pros?: string;
    cons?: string;
    useScenario?: string;
    firstUseTime?: string;
    reviewTime?: string;
    recommendTime?: string;
    sortOrder?: number;
    status?: number;
  }

  export interface RecommendPageResult {
    records: RecommendItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

export async function getAiRelayRecommendPageApi(
  params: AiRelayRecommendApi.RecommendPageQuery,
) {
  return requestClient.get<AiRelayRecommendApi.RecommendPageResult>(
    '/blog/admin/ai-relay/recommends/page',
    { params },
  );
}

export async function getAiRelayRecommendDetailApi(id: number | string) {
  return requestClient.get<AiRelayRecommendApi.RecommendItem>(
    `/blog/admin/ai-relay/recommends/${id}`,
  );
}

export async function createAiRelayRecommendApi(
  data: AiRelayRecommendApi.RecommendParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/ai-relay/recommends',
    data,
  );
}

export async function updateAiRelayRecommendApi(
  id: number | string,
  data: AiRelayRecommendApi.RecommendParams,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/recommends/${id}`,
    data,
  );
}

export async function updateAiRelayRecommendStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/blog/admin/ai-relay/recommends/${id}/status`,
    null,
    { params: { status } },
  );
}

export async function deleteAiRelayRecommendApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/ai-relay/recommends/${id}`);
}
