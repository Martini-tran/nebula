import { requestClient } from '#/api/request';

/**
 * AI 模型档案 API
 * 后端 manager 服务全局 SNAKE_CASE，故出入参原始字段为 snake_case；本层做 raw(snake)↔camel 归一化，
 * 组件侧统一使用 camelCase。apiKey 仅在「保存」时上行明文，列表/详情下行仅含掩码（apiKeyMasked/hasApiKey）。
 */
export namespace AiModelProfileApi {
  export interface ProfilePageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    provider?: string;
    status?: number;
  }

  /** 列表/详情原始行（snake_case，apiKey 掩码） */
  export interface ProfileItemRaw {
    id: number | string;
    profile_code: string;
    name?: string;
    provider?: string;
    base_url?: string;
    api_key_masked?: string;
    has_api_key?: boolean;
    model?: string;
    temperature?: null | number;
    max_tokens?: null | number;
    top_p?: null | number;
    timeout_ms?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
  }

  /** 列表/详情行（camelCase） */
  export interface ProfileItem {
    id: number | string;
    profileCode: string;
    name?: string;
    provider?: string;
    baseUrl?: string;
    apiKeyMasked?: string;
    hasApiKey?: boolean;
    model?: string;
    temperature?: null | number;
    maxTokens?: null | number;
    topP?: null | number;
    timeoutMs?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 保存请求（snake_case 上行，apiKey 明文；更新时留空表示不修改） */
  export interface ProfileSaveParams {
    profile_code: string;
    name?: string;
    provider?: string;
    base_url?: string;
    api_key?: string;
    model?: string;
    temperature?: null | number;
    max_tokens?: null | number;
    top_p?: null | number;
    timeout_ms?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
  }

  export interface ProfilePageResult {
    records: ProfileItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeProfile(
  raw: AiModelProfileApi.ProfileItemRaw,
): AiModelProfileApi.ProfileItem {
  return {
    id: raw.id,
    profileCode: raw.profile_code,
    name: raw.name,
    provider: raw.provider,
    baseUrl: raw.base_url,
    apiKeyMasked: raw.api_key_masked,
    hasApiKey: raw.has_api_key,
    model: raw.model,
    temperature: raw.temperature,
    maxTokens: raw.max_tokens,
    topP: raw.top_p,
    timeoutMs: raw.timeout_ms,
    options: raw.options,
    status: raw.status,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

const BASE = '/manager/admin/ai-model-profile/profiles';

/** 分页查询模型档案 */
export async function getAiModelProfilePageApi(
  params: AiModelProfileApi.ProfilePageQuery,
) {
  const result = await requestClient.get<{
    records: AiModelProfileApi.ProfileItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(`${BASE}/page`, { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeProfile),
  } as AiModelProfileApi.ProfilePageResult;
}

/** 获取模型档案详情 */
export async function getAiModelProfileDetailApi(id: number | string) {
  const raw = await requestClient.get<AiModelProfileApi.ProfileItemRaw>(
    `${BASE}/${id}`,
  );
  return normalizeProfile(raw);
}

/** 创建模型档案 */
export async function createAiModelProfileApi(
  data: AiModelProfileApi.ProfileSaveParams,
) {
  return requestClient.post<number | string>(BASE, data);
}

/** 更新模型档案 */
export async function updateAiModelProfileApi(
  id: number | string,
  data: AiModelProfileApi.ProfileSaveParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 更新启用/停用状态 */
export async function updateAiModelProfileStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/${id}/status`, null, {
    params: { status },
  });
}

/** 删除模型档案 */
export async function deleteAiModelProfileApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}
