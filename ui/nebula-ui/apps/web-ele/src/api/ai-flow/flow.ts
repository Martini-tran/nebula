import { requestClient } from '#/api/request';

/**
 * AI 流程编排 API
 * 后端 manager 服务全局 SNAKE_CASE，故所有出入参字段均为 snake_case，
 * 与 SDK 的 FlowDefinition / FlowNodeDefinition / FlowEdgeDefinition 序列化对齐。
 */
export namespace AiFlowApi {
  /** 流程节点（与后端 FlowNodeDefinition 对齐，snake_case） */
  export interface FlowNodeRaw {
    nodeCode: string;
    name?: string;
    nodeType?: string;
    systemPrompt?: string;
    promptTemplate?: string;
    profileCode?: string;
    provider?: string;
    model?: string;
    baseUrl?: string;
    apiKey?: string;
    temperature?: null | number;
    maxTokens?: null | number;
    topP?: null | number;
    timeoutMs?: null | number;
    stop?: string[];
    options?: Record<string, any>;
    inputMapping?: Record<string, string>;
    outputKey?: string;
    outputMode?: string;
    nodeConfig?: Record<string, any>;
    rememberTrace?: boolean;
    sortNo?: number;
  }

  /** 流程边（与后端 FlowEdgeDefinition 对齐，snake_case） */
  export interface FlowEdgeRaw {
    fromNode: string;
    toNode: string;
    conditionExpr?: string;
    sortNo?: number;
  }

  /** 流程完整定义（与后端 FlowDefinition 对齐，snake_case） */
  export interface FlowDefinitionRaw {
    flowCode: string;
    name?: string;
    description?: string;
    version?: number;
    defaultProfileCode?: string;
    nodes: FlowNodeRaw[];
    edges: FlowEdgeRaw[];
  }

  /** 流程列表行 */
  export interface FlowSummaryRaw {
    flowCode: string;
    name?: string;
    description?: string;
    version?: number;
    defaultProfileCode?: string;
    status?: number;
    nodeCount?: number;
    createTime?: string;
    updateTime?: string;
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
    conversationId?: string;
  }

  /** 运行结果 */
  export interface FlowRunResultRaw {
    flowCode: string;
    /** 执行实例标识（启用状态持久化时返回，用于断点续跑） */
    runId?: string;
    attributes: Record<string, any>;
    nodeResults: Record<string, any>;
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
    '/manager/admin/ai-flow/flows/page',
    { params },
  );
}

/** 获取流程完整定义（供画布回显） */
export async function getFlowDetailApi(flowCode: string) {
  return requestClient.get<AiFlowApi.FlowDefinitionRaw>(
    `/manager/admin/ai-flow/flows/${encodeURIComponent(flowCode)}`,
  );
}

/** 保存整图（前端导出的 FlowDefinition 直接落库） */
export async function saveFlowApi(definition: AiFlowApi.FlowDefinitionRaw) {
  return requestClient.post<string>('/manager/admin/ai-flow/flows', definition);
}

/** 删除流程 */
export async function deleteFlowApi(flowCode: string) {
  return requestClient.delete<void>(
    `/manager/admin/ai-flow/flows/${encodeURIComponent(flowCode)}`,
  );
}

/** 一键运行流程 */
export async function runFlowApi(
  flowCode: string,
  body: AiFlowApi.FlowRunRequest,
) {
  return requestClient.post<AiFlowApi.FlowRunResultRaw>(
    `/manager/admin/ai-flow/flows/${encodeURIComponent(flowCode)}/run`,
    body,
  );
}

/** 节点类型元数据（驱动左侧节点面板） */
export async function getFlowNodeTypesApi() {
  return requestClient.get<AiFlowApi.NodeTypeMeta[]>(
    '/manager/admin/ai-flow/flows/node-types',
  );
}

/** 从断点续跑一个失败/中断的执行实例（跳过已完成节点，需启用状态持久化） */
export async function resumeFlowApi(runId: string) {
  return requestClient.post<AiFlowApi.FlowRunResultRaw>(
    `/manager/admin/ai-flow/flows/runs/${encodeURIComponent(runId)}/resume`,
  );
}
