import { requestClient } from '#/api/request';

/**
 * 跨实例迭代链（系列递推）API
 * 后端 manager 服务 Jackson 默认 camelCase，出入参字段均为 camelCase，本层直接透传不做命名归一化。
 * 对应 docs/跨实例迭代层设计.md：一条链 = 一个"系列"，由后端 IterationDriver 定时逐轮推进，
 * 上一轮实例产物按 carryOver 映射成下一轮入参。
 */
export namespace AiIterationApi {
  export interface ChainPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    /** ACTIVE | PAUSED | COMPLETED | FAILED */
    status?: string;
  }

  /** 迭代链头（列表/详情共用；详情多带 runs 时间线） */
  export interface Chain {
    id?: number | string;
    chainId?: string;
    name?: string;
    agentCode?: string;
    cron?: string;
    nextRunAt?: string;
    /** 已完成轮次（= 已写到第几篇） */
    seq?: number;
    maxIterations?: number;
    untilExpr?: string;
    /** carry-over 映射：上一轮产物键 → 下一轮 inputs 键 */
    carryOver?: Record<string, string>;
    /** 首轮种子入参 */
    seedInputs?: Record<string, any>;
    lastInstanceId?: string;
    /** ACTIVE | PAUSED | COMPLETED | FAILED */
    status?: string;
    consecutiveFails?: number;
    errorMsg?: string;
    createTime?: string;
    updateTime?: string;
    /** 已产出实例时间线（仅详情） */
    runs?: ChainRun[];
  }

  /** 链上一轮产出实例摘要 */
  export interface ChainRun {
    instanceId?: string;
    status?: string;
    createTime?: string;
  }

  export interface ChainPageResult {
    records: Chain[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  /** 创建/更新请求 */
  export interface ChainSaveRequest {
    name?: string;
    agentCode?: string;
    cron?: string;
    maxIterations?: number;
    untilExpr?: string;
    carryOver?: Record<string, string>;
    seedInputs?: Record<string, any>;
    userId?: string;
    conversationId?: string;
    /** 首轮触发时间（缺省立即到点） */
    firstRunAt?: string;
  }
}

const BASE = '/manager/admin/ai-iteration';

/** 分页查询迭代链 */
export async function getIterationChainPageApi(
  params: AiIterationApi.ChainPageQuery,
) {
  return requestClient.get<AiIterationApi.ChainPageResult>(
    `${BASE}/chains/page`,
    { params },
  );
}

/** 链详情（含已产出实例时间线） */
export async function getIterationChainDetailApi(chainId: string) {
  return requestClient.get<AiIterationApi.Chain>(
    `${BASE}/chains/${encodeURIComponent(chainId)}`,
  );
}

/** 创建迭代链（开一个"系列"），返回 chainId */
export async function createIterationChainApi(
  body: AiIterationApi.ChainSaveRequest,
) {
  return requestClient.post<string>(`${BASE}/chains`, body);
}

/** 更新迭代链配置 */
export async function updateIterationChainApi(
  chainId: string,
  body: AiIterationApi.ChainSaveRequest,
) {
  return requestClient.put<void>(
    `${BASE}/chains/${encodeURIComponent(chainId)}`,
    body,
  );
}

/** 暂停链（ACTIVE → PAUSED） */
export async function pauseIterationChainApi(chainId: string) {
  return requestClient.post<void>(
    `${BASE}/chains/${encodeURIComponent(chainId)}/pause`,
  );
}

/** 恢复链（PAUSED → ACTIVE） */
export async function resumeIterationChainApi(chainId: string) {
  return requestClient.post<void>(
    `${BASE}/chains/${encodeURIComponent(chainId)}/resume`,
  );
}

/** 立即推进一轮（run-now）：把 next_run_at 置当前，下次扫描即跑 */
export async function runNowIterationChainApi(chainId: string) {
  return requestClient.post<void>(
    `${BASE}/chains/${encodeURIComponent(chainId)}/run-now`,
  );
}

/** 删除链（不删已产出实例） */
export async function deleteIterationChainApi(chainId: string) {
  return requestClient.delete<void>(
    `${BASE}/chains/${encodeURIComponent(chainId)}`,
  );
}
