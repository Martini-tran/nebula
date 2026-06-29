import { requestClient } from '#/api/request';

/**
 * AI 工具 API（只读）
 * 工具定义于后端代码、由启动同步器维护，管理端仅提供查询。后端 manager 服务全局 SNAKE_CASE，
 * 故出参原始字段为 snake_case；本层做 raw(snake)↔camel 归一化，组件侧统一使用 camelCase。
 */
export namespace AiToolApi {
  export interface ToolPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    category?: string;
    enabled?: number;
  }

  /** 列表/详情原始行（snake_case） */
  export interface ToolItemRaw {
    id: number | string;
    tool_code: string;
    name?: string;
    description?: string;
    category?: string;
    params_schema?: Record<string, any>;
    result_schema?: Record<string, any>;
    enabled?: number;
    builtin?: number;
    sort_no?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
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
  return {
    id: raw.id,
    toolCode: raw.tool_code,
    name: raw.name,
    description: raw.description,
    category: raw.category,
    paramsSchema: raw.params_schema,
    resultSchema: raw.result_schema,
    enabled: raw.enabled,
    builtin: raw.builtin,
    sortNo: raw.sort_no,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
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
