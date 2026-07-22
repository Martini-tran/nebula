import { useAppConfig } from '@nebula/hooks';
import { preferences } from '@nebula/preferences';
import { useAccessStore } from '@nebula/stores';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

/**
 * 流程设计助手（Copilot）流式 API。
 * 与通用聊天 chatStreamApi 同构：SSE 需消费 ReadableStream，axios 封装拿不到流，故直接用原生 fetch，
 * 手动对齐 requestClient 的鉴权约定（sa-token 原始 token 作 Authorization，无 Bearer 前缀）。
 *
 * 后端事件约定：delta（文本片段 {content}）/ tool_call（工具调用进度）/ flow（流程落库产物）/
 * agent（Agent 派生产物）/ done（完整响应）/ error（{message}）。
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

  /** tool_call 事件 payload：工具调用进度 */
  export interface ToolCallEvent {
    status: 'done' | 'start';
    name: string;
    arguments?: Record<string, any>;
    success?: boolean;
    resultBrief?: string;
  }
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

  const response = await fetch(`${apiURL}/admin/ai-flow/copilot/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: accessStore.accessToken ?? '',
      'Accept-Language': preferences.app.locale,
    },
    body: JSON.stringify(request),
    signal,
  });

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
