import { requestClient } from '#/api/request';

/**
 * AI 提示词 API
 * 后端 manager 服务 Jackson 默认 camelCase（未配置 SNAKE_CASE），出入参字段均为 camelCase，本层直接透传不做命名归一化。
 * content 内可写 {{变量名}} 占位符，占位符清单由 variables 声明。
 */
export namespace AiPromptApi {
  export interface PromptPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    role?: string;
  }

  /** 变量声明 */
  export interface PromptVariable {
    name: string;
    type?: string;
    required?: boolean;
    description?: string;
    [key: string]: any;
  }

  /** 列表/详情原始行（manager 服务默认驼峰序列化，与 PromptItem 同构） */
  export interface PromptItemRaw {
    id: number | string;
    promptCode: string;
    name?: string;
    role?: string;
    content?: string;
    variables?: PromptVariable[];
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 列表/详情行（camelCase） */
  export interface PromptItem {
    id: number | string;
    promptCode: string;
    name?: string;
    role?: string;
    content?: string;
    variables?: PromptVariable[];
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 保存请求（camelCase 上行） */
  export interface PromptSaveParams {
    promptCode: string;
    name?: string;
    role?: string;
    content: string;
    variables?: PromptVariable[];
    remark?: string;
  }

  export interface PromptPageResult {
    records: PromptItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizePrompt(
  raw: AiPromptApi.PromptItemRaw,
): AiPromptApi.PromptItem {
  // manager 服务 Jackson 默认驼峰序列化，下行字段已是 camelCase，直接透传
  return { ...raw };
}

const BASE = '/manager/admin/ai-prompt/prompts';

/** 分页查询提示词 */
export async function getAiPromptPageApi(params: AiPromptApi.PromptPageQuery) {
  const result = await requestClient.get<{
    records: AiPromptApi.PromptItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(`${BASE}/page`, { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizePrompt),
  } as AiPromptApi.PromptPageResult;
}

/** 获取提示词详情 */
export async function getAiPromptDetailApi(id: number | string) {
  const raw = await requestClient.get<AiPromptApi.PromptItemRaw>(
    `${BASE}/${id}`,
  );
  return normalizePrompt(raw);
}

/** 创建提示词 */
export async function createAiPromptApi(data: AiPromptApi.PromptSaveParams) {
  return requestClient.post<number | string>(BASE, data);
}

/** 更新提示词 */
export async function updateAiPromptApi(
  id: number | string,
  data: AiPromptApi.PromptSaveParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 删除提示词 */
export async function deleteAiPromptApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}
