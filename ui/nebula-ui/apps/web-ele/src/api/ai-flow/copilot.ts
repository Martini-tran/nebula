import { useAppConfig } from '@nebula/hooks';
import { preferences } from '@nebula/preferences';
import { useAccessStore } from '@nebula/stores';

import { requestClient } from '#/api/request';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

/**
 * 流程设计助手（Copilot）流式 API。
 * 与通用聊天 chatStreamApi 同构：SSE 需消费 ReadableStream，axios 封装拿不到流，故直接用原生 fetch，
 * 手动对齐 requestClient 的鉴权约定（sa-token 原始 token 作 Authorization，无 Bearer 前缀）。
 *
 * 后端事件约定：delta（文本片段 {content}）/ tool_call（工具调用进度）/ flow（流程落库产物）/
 * agent（Agent 派生产物）/ draft_updated（草稿变更）/ confirm_required（待用户确认）/
 * operation_updated（真实试跑状态）/ done（完整响应）/ error（{message}）。
 */
export namespace CopilotApi {
  /** 单条对话消息 */
  export interface ChatMessage {
    role: 'assistant' | 'system' | 'user';
    content: string;
  }

  /** 流式请求 */
  export interface CopilotStreamRequest {
    prompt: string;
    messages?: ChatMessage[];
    conversationId?: string;
    model?: string;
    temperature?: number;
    /** 当前会话正在编辑的草稿；后续轮次在此草稿上增量修改。 */
    activeDraftId?: string;
    /** 前端最后收到的草稿 revision。 */
    activeDraftRevision?: number;
    /** 独立确认接口签发的一次性授权，只走结构化请求字段。 */
    confirmationToken?: string;
    /** 用户确认后恢复的原工具调用，不进入模型消息。 */
    resumeAction?: ResumeAction;
  }

  export interface ResumeAction {
    toolCode: 'real_run_draft';
    arguments: Record<string, any>;
  }

  export interface ConfirmationRequiredEvent {
    confirmationId: string;
    draftId: string;
    revision: number;
    expiresAt: string;
    warning: string;
    resumeArguments: Record<string, any>;
  }

  export interface ConfirmationResult {
    ok: boolean;
    code?: string;
    message?: string;
    confirmationId?: string;
    confirmationToken?: string;
    draftId?: string;
    revision?: number;
    expiresAt?: string;
  }

  export type OperationStatus =
    | 'FAILED'
    | 'PENDING'
    | 'RUNNING'
    | 'SUCCEEDED'
    | 'UNKNOWN';

  export interface OperationEvent {
    operationId: string;
    action: string;
    draftId: string;
    revision: number;
    status: OperationStatus;
    result?: Record<string, any>;
    errorCode?: string;
    errorMessage?: string;
  }

  export interface DraftUpdatedEvent {
    action: string;
    draftId: string;
    revision: number;
  }

  export interface DraftDefinitionResult {
    ok: boolean;
    draftId: string;
    revision: number;
    definition?: Record<string, any>;
    issues?: Array<Record<string, any>>;
  }

  /** flow 事件 payload：流程落库产物 */
  export interface FlowEvent {
    flowCode: string;
    version?: number;
    name?: string;
    nodeCount?: number;
    /** 完整流程定义，前端可直接回显画布 */
    definition?: Record<string, any>;
  }

  /** agent 事件 payload：Agent 派生产物 */
  export interface AgentEvent {
    agentCode: string;
    id?: number;
    name?: string;
  }

  /**
   * tool_call 事件 payload：工具调用进度。
   *
   * 后端只发这些字段（不含工具入参：`arguments` 从未下发，
   * 入参仅在 confirm_required 事件里以 resumeArguments 暴露）。
   * `toolCallId` 是同一次调用 start / done 两帧的关联键——
   * 同一轮可并行调多个工具，仅靠 name 无法配对。
   */
  export interface ToolCallEvent {
    status: 'done' | 'start';
    name: string;
    toolCallId?: string;
    /** status='done' 时才有 */
    success?: boolean;
    /** 工具结果摘要，后端已截断至 120 字符 */
    resultBrief?: string;
    latencyMs?: number;
  }
}

const COPILOT_BASE = '/manager/admin/ai-flow/copilot';

/** 显式确认高风险动作并取得短期一次性授权。 */
export function confirmCopilotActionApi(
  confirmationId: string,
  conversationId?: string,
) {
  return requestClient.post<CopilotApi.ConfirmationResult>(
    `${COPILOT_BASE}/confirmations/${encodeURIComponent(confirmationId)}/confirm`,
    { conversationId },
  );
}

/** 查询真实试跑 operation 的持久化状态。 */
export function getCopilotOperationApi(operationId: string) {
  return requestClient.get<CopilotApi.OperationEvent>(
    `${COPILOT_BASE}/operations/${encodeURIComponent(operationId)}`,
  );
}

/** 获取供编辑器回显的完整 canonical 草稿定义。 */
export function getCopilotDraftDefinitionApi(
  draftId: string,
  conversationId?: string,
) {
  return requestClient.get<CopilotApi.DraftDefinitionResult>(
    `${COPILOT_BASE}/drafts/${encodeURIComponent(draftId)}/definition`,
    { params: { conversationId } },
  );
}

/**
 * 发起流程设计助手对话，返回响应体 ReadableStream 供 useXStream 消费。
 *
 * @param request 请求
 * @param signal 取消信号
 * @returns 响应体流
 */
export async function copilotStreamApi(
  request: CopilotApi.CopilotStreamRequest,
  signal?: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const accessStore = useAccessStore();

  const response = await fetch(
    `${apiURL}/manager/admin/ai-flow/copilot/stream`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: accessStore.accessToken ?? '',
        'Accept-Language': preferences.app.locale,
      },
      body: JSON.stringify(request),
      signal,
    },
  );

  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    try {
      const data = await response.json();
      message = data?.message ?? data?.error ?? message;
    } catch {
      // 响应体非 JSON，沿用状态码提示
    }
    throw new Error(message);
  }
  if (!response.body) {
    throw new Error('响应体为空，无法读取流式内容');
  }
  return response.body;
}
