import { requestClient } from '#/api/request';

/**
 * Forge-插件下载日志 API（只读）
 * forge 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出参均为 camelCase，本层直接透传。
 */
export namespace ForgeDownloadLogApi {
  /** 下载日志条目（camelCase） */
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

/** 分页查询下载日志（只读） */
export async function getForgeDownloadLogPageApi(
  params: ForgeDownloadLogApi.DownloadLogPageQuery,
) {
  return requestClient.get<ForgeDownloadLogApi.DownloadLogPageResult>(
    '/forge/admin/plugin-download-logs/page',
    { params },
  );
}
