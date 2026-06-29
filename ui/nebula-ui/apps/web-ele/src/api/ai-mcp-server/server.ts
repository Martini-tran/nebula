import { requestClient } from '#/api/request';

/**
 * AI MCP 服务器 API
 * 后端 manager 服务全局 SNAKE_CASE，故出入参原始字段为 snake_case；本层做 raw(snake)↔camel 归一化，
 * 组件侧统一使用 camelCase。authToken 仅在「保存」时上行明文，列表/详情下行仅含掩码（authTokenMasked/hasAuthToken）。
 */
export namespace AiMcpServerApi {
  export interface ServerPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    transport?: string;
    status?: number;
  }

  /** 列表/详情原始行（snake_case，authToken 掩码） */
  export interface ServerItemRaw {
    id: number | string;
    server_code: string;
    name?: string;
    transport?: string;
    command?: string;
    args?: string[];
    env?: Record<string, string>;
    url?: string;
    headers?: Record<string, string>;
    auth_token_masked?: string;
    has_auth_token?: boolean;
    timeout_ms?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
  }

  /** 列表/详情行（camelCase） */
  export interface ServerItem {
    id: number | string;
    serverCode: string;
    name?: string;
    transport?: string;
    command?: string;
    args?: string[];
    env?: Record<string, string>;
    url?: string;
    headers?: Record<string, string>;
    authTokenMasked?: string;
    hasAuthToken?: boolean;
    timeoutMs?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 保存请求（snake_case 上行，authToken 明文；更新时留空表示不修改） */
  export interface ServerSaveParams {
    server_code: string;
    name?: string;
    transport?: string;
    command?: string;
    args?: string[];
    env?: Record<string, string>;
    url?: string;
    headers?: Record<string, string>;
    auth_token?: string;
    timeout_ms?: null | number;
    options?: Record<string, any>;
    status?: number;
    remark?: string;
  }

  export interface ServerPageResult {
    records: ServerItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeServer(
  raw: AiMcpServerApi.ServerItemRaw,
): AiMcpServerApi.ServerItem {
  return {
    id: raw.id,
    serverCode: raw.server_code,
    name: raw.name,
    transport: raw.transport,
    command: raw.command,
    args: raw.args,
    env: raw.env,
    url: raw.url,
    headers: raw.headers,
    authTokenMasked: raw.auth_token_masked,
    hasAuthToken: raw.has_auth_token,
    timeoutMs: raw.timeout_ms,
    options: raw.options,
    status: raw.status,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

const BASE = '/manager/admin/ai-mcp-server/servers';

/** 分页查询 MCP 服务器 */
export async function getAiMcpServerPageApi(
  params: AiMcpServerApi.ServerPageQuery,
) {
  const result = await requestClient.get<{
    records: AiMcpServerApi.ServerItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(`${BASE}/page`, { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeServer),
  } as AiMcpServerApi.ServerPageResult;
}

/** 获取 MCP 服务器详情 */
export async function getAiMcpServerDetailApi(id: number | string) {
  const raw = await requestClient.get<AiMcpServerApi.ServerItemRaw>(
    `${BASE}/${id}`,
  );
  return normalizeServer(raw);
}

/** 创建 MCP 服务器 */
export async function createAiMcpServerApi(
  data: AiMcpServerApi.ServerSaveParams,
) {
  return requestClient.post<number | string>(BASE, data);
}

/** 更新 MCP 服务器 */
export async function updateAiMcpServerApi(
  id: number | string,
  data: AiMcpServerApi.ServerSaveParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 更新启用/停用状态 */
export async function updateAiMcpServerStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/${id}/status`, null, {
    params: { status },
  });
}

/** 删除 MCP 服务器 */
export async function deleteAiMcpServerApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}
