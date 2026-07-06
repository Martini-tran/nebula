import { requestClient } from '#/api/request';

/**
 * AI 智能体（Agent 定义）API
 * 后端 manager 服务 Jackson 默认 camelCase，出入参字段均为 camelCase，本层直接透传不做命名归一化。
 * 本模块当前仅提供 AGENT 节点选择器所需的列表接口；完整 Agent 管理页 CRUD 是独立批次。
 */
export namespace AiAgentApi {
  export interface AgentPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
  }

  /** Agent 定义列表行（camelCase） */
  export interface AgentSummary {
    id: number | string;
    agentCode: string;
    name?: string;
    description?: string;
    flowCode?: string;
    flowVersion?: number;
    defaultProfileCode?: string;
    version?: number;
    status?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface AgentPageResult {
    records: AgentSummary[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  /* ---- 实例运行 / 唤醒 / 续跑 ---- */

  /** 运行请求：按 agentCode 建实例并执行 */
  export interface AgentRunRequest {
    inputs?: Record<string, any>;
    conversationId?: string;
  }

  /** 唤醒请求：event 需命中实例 awaitingEvents，payload 供出边 guard 裁决 */
  export interface AgentSignalRequest {
    event?: string;
    payload?: Record<string, any>;
  }

  /** 运行/唤醒/续跑结果（run/signal/resume 共用） */
  export interface AgentRunResult {
    instanceId?: string;
    agentCode?: string;
    /** RUNNING | SUSPENDED | SUCCESS | FAILED */
    status?: string;
    currentState?: string;
    awaitingEvents?: string[];
    attributes?: Record<string, any>;
  }

  /* ---- 实例回放 ---- */

  export interface AgentInstancePageQuery {
    pageNum?: number;
    pageSize?: number;
    agentCode?: string;
    status?: string;
    userId?: string;
  }

  /** 一条转移记录（回放时间线） */
  export interface AgentTransition {
    seq?: number;
    fromState?: string;
    toState?: string;
    eventName?: string;
    attempt?: number;
    /** SUCCESS | RETRY | FAILED | COMPENSATED */
    outcome?: string;
    nodeResult?: Record<string, any>;
  }

  /** 实例（列表项只含头；详情含 transitions + contextSnapshot） */
  export interface AgentInstance {
    instanceId?: string;
    agentCode?: string;
    flowCode?: string;
    status?: string;
    currentState?: string;
    awaitingEvents?: string[];
    transitionCount?: number;
    contextSnapshot?: Record<string, any>;
    transitions?: AgentTransition[];
  }

  export interface AgentInstancePageResult {
    records: AgentInstance[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

/** 分页查询 Agent 定义（供 AGENT 节点选择器拉取可调用的子 Agent） */
export async function getAgentPageApi(params: AiAgentApi.AgentPageQuery) {
  return requestClient.get<AiAgentApi.AgentPageResult>(
    '/manager/admin/ai-agent/agents/page',
    { params },
  );
}

/** 按 agentCode 创建实例并执行 */
export async function runAgentApi(
  agentCode: string,
  body: AiAgentApi.AgentRunRequest,
) {
  return requestClient.post<AiAgentApi.AgentRunResult>(
    `/manager/admin/ai-agent/agents/${encodeURIComponent(agentCode)}/run`,
    body,
  );
}

/** 唤醒一个挂起（SUSPENDED）实例 */
export async function signalAgentInstanceApi(
  instanceId: string,
  body: AiAgentApi.AgentSignalRequest,
) {
  return requestClient.post<AiAgentApi.AgentRunResult>(
    `/manager/admin/ai-agent/instances/${encodeURIComponent(instanceId)}/signal`,
    body,
  );
}

/** 崩溃恢复：从落库状态恢复实例并续跑（RUNNING 断点） */
export async function resumeAgentInstanceApi(instanceId: string) {
  return requestClient.post<AiAgentApi.AgentRunResult>(
    `/manager/admin/ai-agent/instances/${encodeURIComponent(instanceId)}/resume`,
  );
}

/** 分页查询实例 */
export async function getAgentInstancePageApi(
  params: AiAgentApi.AgentInstancePageQuery,
) {
  return requestClient.get<AiAgentApi.AgentInstancePageResult>(
    '/manager/admin/ai-agent/instances',
    { params },
  );
}

/** 实例详情（含转移历史时间线，用于回放） */
export async function getAgentInstanceDetailApi(instanceId: string) {
  return requestClient.get<AiAgentApi.AgentInstance>(
    `/manager/admin/ai-agent/instances/${encodeURIComponent(instanceId)}`,
  );
}
