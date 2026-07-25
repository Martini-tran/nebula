import { requestClient } from '#/api/request';

/**
 * AI 知识库 API
 * 后端 manager 服务 Jackson 默认 camelCase（未配置 SNAKE_CASE），出入参字段均为 camelCase，本层直接透传不做命名归一化。
 * 两层资源：知识库（bases）+ 库下文档（documents）+ 检索预览（search）。
 * 导入/检索依赖 embedding + 向量库就绪，未就绪时后端返回明确错误，由请求拦截统一提示。
 */
export namespace AiKnowledgeApi {
  /** 分页入参基础字段 */
  export interface PageQuery {
    pageNum?: number;
    pageSize?: number;
  }

  /** 统一分页返回（对齐后端 PageResult：records/total/current/size/pages） */
  export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  // —— 知识库 ——

  export interface KbPageQuery extends PageQuery {
    keyword?: string;
    status?: number;
  }

  /** 知识库行（manager 服务默认驼峰序列化） */
  export interface KbItem {
    id: number | string;
    kbCode: string;
    name?: string;
    description?: string;
    embeddingProvider?: string;
    embeddingModel?: string;
    dimension?: null | number;
    metric?: string;
    status?: number;
    createTime?: string;
    updateTime?: string;
  }

  /** 保存请求（创建时必填 kbCode，更新忽略；维度/模型/度量留空由服务端按 embedding 配置补齐） */
  export interface KbSaveParams {
    kbCode?: string;
    name?: string;
    description?: string;
    embeddingProvider?: string;
    embeddingModel?: string;
    dimension?: null | number;
    metric?: string;
    status?: number;
  }

  // —— 文档 ——

  /** 文档行 */
  export interface DocItem {
    id: number | string;
    kbCode: string;
    docId: string;
    title?: string;
    sourceType?: string;
    sourceUri?: string;
    charCount?: null | number;
    chunkCount?: null | number;
    /** 0=索引中 1=完成 2=失败 */
    status?: number;
    createTime?: string;
    updateTime?: string;
  }

  /** 文档导入请求（以正文文本导入：切块 → embed → 入库；同 docId 覆盖旧切片） */
  export interface DocImportParams {
    docId?: string;
    title?: string;
    sourceType?: string;
    sourceUri?: string;
    content: string;
  }

  // —— 检索预览 ——

  /** 检索命中行 */
  export interface SearchHit {
    content?: string;
    /** 归一化相似度得分（[0,1]，降序） */
    score?: null | number;
    metadata?: Record<string, any>;
  }
}

const BASE = '/manager/admin/ai-knowledge';

// —— 知识库 CRUD ——

/** 分页查询知识库 */
export async function getAiKnowledgePageApi(
  params: AiKnowledgeApi.KbPageQuery,
) {
  return requestClient.get<AiKnowledgeApi.PageResult<AiKnowledgeApi.KbItem>>(
    `${BASE}/bases/page`,
    { params },
  );
}

/** 获取知识库详情 */
export async function getAiKnowledgeDetailApi(id: number | string) {
  return requestClient.get<AiKnowledgeApi.KbItem>(`${BASE}/bases/${id}`);
}

/** 创建知识库 */
export async function createAiKnowledgeApi(data: AiKnowledgeApi.KbSaveParams) {
  return requestClient.post<number | string>(`${BASE}/bases`, data);
}

/** 更新知识库 */
export async function updateAiKnowledgeApi(
  id: number | string,
  data: AiKnowledgeApi.KbSaveParams,
) {
  return requestClient.put<void>(`${BASE}/bases/${id}`, data);
}

/** 更新启用/停用状态 */
export async function updateAiKnowledgeStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/bases/${id}/status`, null, {
    params: { status },
  });
}

/** 删除知识库（含文档/切片/向量） */
export async function deleteAiKnowledgeApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/bases/${id}`);
}

// —— 文档导入/列表/删除 ——

/** 分页查询知识库下文档 */
export async function getAiKnowledgeDocumentPageApi(
  kbCode: string,
  params: AiKnowledgeApi.PageQuery,
) {
  return requestClient.get<AiKnowledgeApi.PageResult<AiKnowledgeApi.DocItem>>(
    `${BASE}/bases/${kbCode}/documents`,
    { params },
  );
}

/** 导入一篇文档（切块 → embed → 入库） */
export async function importAiKnowledgeDocumentApi(
  kbCode: string,
  data: AiKnowledgeApi.DocImportParams,
) {
  return requestClient.post<number>(
    `${BASE}/bases/${kbCode}/documents`,
    data,
  );
}

/** 删除一篇文档 */
export async function deleteAiKnowledgeDocumentApi(
  kbCode: string,
  docId: string,
) {
  return requestClient.delete<void>(
    `${BASE}/bases/${kbCode}/documents/${docId}`,
  );
}

// —— 检索预览 ——

/** 检索预览（验证召回效果） */
export async function searchAiKnowledgeApi(
  kbCode: string,
  query: string,
  topK?: number,
) {
  return requestClient.get<AiKnowledgeApi.SearchHit[]>(
    `${BASE}/bases/${kbCode}/search`,
    { params: { query, topK } },
  );
}
