import { requestClient } from '#/api/request';

/**
 * API Key（SK）管理 API
 * 后端实现位于 nebula-service-manager，context-path=/manager
 */
export namespace SystemApiKeyApi {
  /** 分页查询入参，对齐后端 ApiKeyPageQuery（继承 PageQuery） */
  export interface ApiKeyPageQuery {
    pageNum?: number;
    pageSize?: number;
    name?: string;
    status?: number;
  }

  /** 列表行，对齐后端 ApiKeyVO。不含 key 明文，只有掩码 */
  export interface ApiKeyItem {
    id: number | string;
    name: string;
    keyMask: string;
    userId: number | string;
    username?: string;
    scopes: string[];
    /** 为空表示永不过期 */
    expireTime?: null | string;
    expired: boolean;
    status: number;
    remark?: string;
    createTime?: string;
  }

  /** 创建/更新入参，对齐后端 ApiKeySaveRequest。key 由服务端生成，不可传入 */
  export interface ApiKeySaveParams {
    name: string;
    scopes: string[];
    /** yyyy-MM-dd HH:mm:ss；传 null 表示永不过期 */
    expireTime?: null | string;
    remark?: string;
  }

  /** 创建结果，对齐后端 ApiKeyCreatedVO。明文仅此一次返回 */
  export interface ApiKeyCreated {
    id: number | string;
    apiKey: string;
  }

  /** 分页结果，对齐后端 PageResult<ApiKeyVO> */
  export interface ApiKeyPageResult {
    records: ApiKeyItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

const BASE = '/manager/api-key';

/** 分页查询 */
export async function getSystemApiKeyPageApi(
  params: SystemApiKeyApi.ApiKeyPageQuery,
) {
  return requestClient.get<SystemApiKeyApi.ApiKeyPageResult>(`${BASE}/page`, {
    params,
  });
}

/** 创建密钥，返回值里带明文 */
export async function createSystemApiKeyApi(
  data: SystemApiKeyApi.ApiKeySaveParams,
) {
  return requestClient.post<SystemApiKeyApi.ApiKeyCreated>(BASE, data);
}

/** 更新名称/授权范围/过期时间/备注 */
export async function updateSystemApiKeyApi(
  id: number | string,
  data: SystemApiKeyApi.ApiKeySaveParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 启用 / 禁用 */
export async function updateSystemApiKeyStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/${id}/status`, { status });
}

/** 删除密钥（立即失效） */
export async function deleteSystemApiKeyApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}
