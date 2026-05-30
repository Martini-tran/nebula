import { requestClient } from '#/api/request';

export namespace AiRelayRecommendApi {
  export interface RecommendPageQuery {
    pageNum?: number;
    pageSize?: number;
    providerId?: number | string;
    keyword?: string;
    status?: number;
  }

  export interface RecommendItemRaw {
    id: number | string;
    provider_id: number | string;
    provider_name?: string;
    recommend_reason: string;
    review_content?: string;
    review_score?: number | string;
    pros?: string;
    cons?: string;
    use_scenario?: string;
    first_use_time?: string;
    review_time?: string;
    recommend_time?: string;
    sort_order?: number;
    status: number;
    create_time?: string;
    update_time?: string;
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
    provider_id: number | string;
    recommend_reason: string;
    review_content?: string;
    review_score?: number | string;
    pros?: string;
    cons?: string;
    use_scenario?: string;
    first_use_time?: string;
    review_time?: string;
    recommend_time?: string;
    sort_order?: number;
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

function normalizeRecommend(
  raw: AiRelayRecommendApi.RecommendItemRaw,
): AiRelayRecommendApi.RecommendItem {
  return {
    id: raw.id,
    providerId: raw.provider_id,
    providerName: raw.provider_name,
    recommendReason: raw.recommend_reason,
    reviewContent: raw.review_content,
    reviewScore: raw.review_score,
    pros: raw.pros,
    cons: raw.cons,
    useScenario: raw.use_scenario,
    firstUseTime: raw.first_use_time,
    reviewTime: raw.review_time,
    recommendTime: raw.recommend_time,
    sortOrder: raw.sort_order,
    status: raw.status,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

export async function getAiRelayRecommendPageApi(
  params: AiRelayRecommendApi.RecommendPageQuery,
) {
  const result = await requestClient.get<{
    records: AiRelayRecommendApi.RecommendItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/ai-relay/recommends/page', { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeRecommend),
  } as AiRelayRecommendApi.RecommendPageResult;
}

export async function getAiRelayRecommendDetailApi(id: number | string) {
  const raw = await requestClient.get<AiRelayRecommendApi.RecommendItemRaw>(
    `/blog/admin/ai-relay/recommends/${id}`,
  );
  return normalizeRecommend(raw);
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
