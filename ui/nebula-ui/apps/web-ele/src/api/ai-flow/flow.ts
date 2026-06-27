import { requestClient } from '#/api/request';

/**
 * AI 流程编排 API
 * 后端 blog 服务全局 SNAKE_CASE，故所有出入参字段均为 snake_case，
 * 与 SDK 的 FlowDefinition / FlowNodeDefinition / FlowEdgeDefinition 序列化对齐。
 */
export namespace AiFlowApi {
  /** 流程节点（与后端 FlowNodeDefinition 对齐，snake_case） */
  export interface FlowNodeRaw {
    node_code: string;
    name?: string;
    node_type?: string;
    system_prompt?: string;
    prompt_template?: string;
    profile_code?: string;
    provider?: string;
    model?: string;
    base_url?: string;
    api_key?: string;
    temperature?: null | number;
    max_tokens?: null | number;
    top_p?: null | number;
    timeout_ms?: null | number;
    stop?: string[];
    options?: Record<string, any>;
    input_mapping?: Record<string, string>;
    output_key?: string;
    output_mode?: string;
    node_config?: Record<string, any>;
    remember_trace?: boolean;
    sort_no?: number;
  }

  /** 流程边（与后端 FlowEdgeDefinition 对齐，snake_case） */
  export interface FlowEdgeRaw {
    from_node: string;
    to_node: string;
    condition_expr?: string;
    sort_no?: number;
  }

  /** 流程完整定义（与后端 FlowDefinition 对齐，snake_case） */
  export interface FlowDefinitionRaw {
    flow_code: string;
    name?: string;
    description?: string;
    version?: number;
    default_profile_code?: string;
    nodes: FlowNodeRaw[];
    edges: FlowEdgeRaw[];
  }

  /** 流程列表行 */
  export interface FlowSummaryRaw {
    flow_code: string;
    name?: string;
    description?: string;
    version?: number;
    default_profile_code?: string;
    status?: number;
    node_count?: number;
    create_time?: string;
    update_time?: string;
  }

  export interface FlowPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
  }

  export interface FlowPageResult {
    records: FlowSummaryRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  /** 运行请求 */
  export interface FlowRunRequest {
    input?: Record<string, any>;
    conversation_id?: string;
  }

  /** 运行结果 */
  export interface FlowRunResultRaw {
    flow_code: string;
    attributes: Record<string, any>;
    node_results: Record<string, any>;
  }

  /** 节点类型元数据 */
  export interface NodeTypeMeta {
    type: string;
    name: string;
  }
}

/** 分页查询流程 */
export async function getFlowPageApi(params: AiFlowApi.FlowPageQuery) {
  return requestClient.get<AiFlowApi.FlowPageResult>(
    '/blog/admin/ai-flow/flows/page',
    { params },
  );
}

/** 获取流程完整定义（供画布回显） */
export async function getFlowDetailApi(flowCode: string) {
  return requestClient.get<AiFlowApi.FlowDefinitionRaw>(
    `/blog/admin/ai-flow/flows/${encodeURIComponent(flowCode)}`,
  );
}

/** 保存整图（前端导出的 FlowDefinition 直接落库） */
export async function saveFlowApi(definition: AiFlowApi.FlowDefinitionRaw) {
  return requestClient.post<string>('/blog/admin/ai-flow/flows', definition);
}

/** 删除流程 */
export async function deleteFlowApi(flowCode: string) {
  return requestClient.delete<void>(
    `/blog/admin/ai-flow/flows/${encodeURIComponent(flowCode)}`,
  );
}

/** 一键运行流程 */
export async function runFlowApi(
  flowCode: string,
  body: AiFlowApi.FlowRunRequest,
) {
  return requestClient.post<AiFlowApi.FlowRunResultRaw>(
    `/blog/admin/ai-flow/flows/${encodeURIComponent(flowCode)}/run`,
    body,
  );
}

/** 节点类型元数据（驱动左侧节点面板） */
export async function getFlowNodeTypesApi() {
  return requestClient.get<AiFlowApi.NodeTypeMeta[]>(
    '/blog/admin/ai-flow/flows/node-types',
  );
}
