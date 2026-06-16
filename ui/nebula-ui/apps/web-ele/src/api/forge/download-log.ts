import { requestClient } from '#/api/request';

export namespace ForgeDownloadLogApi {
  /** 后端 SNAKE_CASE 序列化的原始下载日志结构 */
  export interface DownloadLogItemRaw {
    id: number | string;
    user_id?: number | string | null;
    plugin_id: number | string;
    version_id?: number | string | null;
    client_version?: string;
    client_os?: string;
    ip?: string;
    user_agent?: string;
    result?: number;
    error_msg?: string;
    create_time?: string;
  }

  /** 规范化后的下载日志条目（camelCase） */
  export interface DownloadLogItem {
    id: number | string;
    userId?: number | string | null;
    pluginId: number | string;
    versionId?: number | string | null;
    clientVersion?: string;
    clientOs?: string;
    ip?: string;
    userAgent?: string;
    result?: number;
    errorMsg?: string;
    createTime?: string;
  }

  export interface DownloadLogPageQuery {
    pageNum?: number;
    pageSize?: number;
    pluginId?: number | string;
    versionId?: number | string;
    userId?: number | string;
    result?: number;
    startTime?: string;
    endTime?: string;
  }

  export interface DownloadLogPageResult {
    records: DownloadLogItem[];
    total: number;
    current: number;
    size: number;
  }
}

function normalizeDownloadLog(
  raw: ForgeDownloadLogApi.DownloadLogItemRaw,
): ForgeDownloadLogApi.DownloadLogItem {
  return {
    id: raw.id,
    userId: raw.user_id ?? null,
    pluginId: raw.plugin_id,
    versionId: raw.version_id ?? null,
    clientVersion: raw.client_version,
    clientOs: raw.client_os,
    ip: raw.ip,
    userAgent: raw.user_agent,
    result: raw.result,
    errorMsg: raw.error_msg,
    createTime: raw.create_time,
  };
}

/** 分页查询下载日志（只读） */
export async function getForgeDownloadLogPageApi(
  params: ForgeDownloadLogApi.DownloadLogPageQuery,
) {
  const result = await requestClient.get<{
    records: ForgeDownloadLogApi.DownloadLogItemRaw[];
    total: number;
    current: number;
    size: number;
  }>('/forge/admin/plugin-download-logs/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeDownloadLog),
  } as ForgeDownloadLogApi.DownloadLogPageResult;
}
