import { useAppConfig } from '@nebula/hooks';
import { preferences } from '@nebula/preferences';
import { useAccessStore } from '@nebula/stores';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

/**
 * AI 聊天（流式对话）API
 * SSE 需消费 ReadableStream，axios 封装的 requestClient 拿不到流，故这里直接用原生 fetch，
 * 并手动对齐 requestClient 的鉴权约定：sa-token 以原始 token 作为 Authorization 值，无 Bearer 前缀。
 */
export namespace AiChatApi {
  /** 单条对话消息 */
  export interface ChatMessage {
    role: 'assistant' | 'system' | 'user';
    content: string;
  }

  /** 流式对话请求 */
  export interface ChatStreamRequest {
    prompt: string;
    messages?: ChatMessage[];
    conversationId?: string;
    model?: string;
    temperature?: number;
  }
}

/**
 * 发起流式对话，返回响应体的 ReadableStream 供 useXStream 消费。
 * 后端事件约定：delta（文本片段）/ done（完整响应）/ error（异常原因）。
 *
 * @param request 聊天请求
 * @param signal 取消信号
 * @returns 响应体流
 */
export async function chatStreamApi(
  request: AiChatApi.ChatStreamRequest,
  signal?: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const accessStore = useAccessStore();

  const response = await fetch(`${apiURL}/admin/ai-chat/stream`, {
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
    // 鉴权失败等错误在响应头阶段就返回，此时响应体是 R 包装的 JSON 而非事件流
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
