import { requestClient } from '#/api/request';

/**
 * AI 工具 API（只读）
 * 工具定义于后端代码、由启动同步器维护，管理端仅提供查询。manager 服务 Jackson 默认 camelCase
 * （未配置 SNAKE_CASE），出参字段已是 camelCase，本层直接透传不做命名归一化。
 */
export namespace AiToolApi {
  export interface ToolPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    category?: string;
    enabled?: number;
  }

  /** 列表/详情原始行（manager 服务默认驼峰序列化，与 ToolItem 同构） */
  export interface ToolItemRaw {
    id: number | string;
    toolCode: string;
    name?: string;
    description?: string;
    category?: string;
    paramsSchema?: Record<string, any>;
    resultSchema?: Record<string, any>;
    enabled?: number;
    builtin?: number;
    sortNo?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 列表/详情行（camelCase） */
  export interface ToolItem {
    id: number | string;
    toolCode: string;
    name?: string;
    description?: string;
    category?: string;
    paramsSchema?: Record<string, any>;
    resultSchema?: Record<string, any>;
    enabled?: number;
    builtin?: number;
    sortNo?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface ToolPageResult {
    records: ToolItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeTool(raw: AiToolApi.ToolItemRaw): AiToolApi.ToolItem {
  // manager 服务默认驼峰序列化，下行字段已是 camelCase，直接透传
  return { ...raw };
}

const BASE = '/manager/admin/ai-tool/tools';

/** 分页查询工具 */
export async function getAiToolPageApi(params: AiToolApi.ToolPageQuery) {
  const result = await requestClient.get<{
    records: AiToolApi.ToolItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(`${BASE}/page`, { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeTool),
  } as AiToolApi.ToolPageResult;
}

/** 获取工具详情 */
export async function getAiToolDetailApi(id: number | string) {
  const raw = await requestClient.get<AiToolApi.ToolItemRaw>(`${BASE}/${id}`);
  return normalizeTool(raw);
}
