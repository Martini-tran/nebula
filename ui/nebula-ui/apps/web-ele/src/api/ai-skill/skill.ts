import { requestClient } from '#/api/request';

/**
 * AI 技能 API
 * 后端 manager 服务 Jackson 默认 camelCase（未配置 SNAKE_CASE），出入参字段均为 camelCase，本层直接透传不做命名归一化。
 * 技能 = 元数据 + Markdown 指令正文（instructions）+ 绑定工具（toolCodes）；
 * 命中后指令以 system 消息注入模型上下文，绑定工具并入该次调用的工具白名单。
 */
export namespace AiSkillApi {
  export interface SkillPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    triggerType?: string;
    status?: number;
  }

  /** 列表/详情原始行（manager 服务默认驼峰序列化，与 SkillItem 同构） */
  export interface SkillItemRaw {
    id: number | string;
    skillCode: string;
    name?: string;
    description?: string;
    instructions?: string;
    triggerType?: string;
    toolCodes?: string[];
    mcpServerCodes?: string[];
    sortNo?: number;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 列表/详情行（camelCase） */
  export interface SkillItem {
    id: number | string;
    skillCode: string;
    name?: string;
    description?: string;
    instructions?: string;
    triggerType?: string;
    toolCodes?: string[];
    mcpServerCodes?: string[];
    sortNo?: number;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 保存请求（camelCase 上行） */
  export interface SkillSaveParams {
    skillCode: string;
    name?: string;
    description?: string;
    instructions?: string;
    triggerType?: string;
    toolCodes?: string[];
    mcpServerCodes?: string[];
    sortNo?: number;
    status?: number;
    remark?: string;
  }

  export interface SkillPageResult {
    records: SkillItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

function normalizeSkill(raw: AiSkillApi.SkillItemRaw): AiSkillApi.SkillItem {
  // manager 服务 Jackson 默认驼峰序列化，下行字段已是 camelCase，直接透传
  return { ...raw };
}

const BASE = '/manager/admin/ai-skill/skills';

/** 分页查询技能 */
export async function getAiSkillPageApi(params: AiSkillApi.SkillPageQuery) {
  const result = await requestClient.get<{
    records: AiSkillApi.SkillItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(`${BASE}/page`, { params });

  return {
    ...result,
    records: (result.records ?? []).map(normalizeSkill),
  } as AiSkillApi.SkillPageResult;
}

/** 全部启用技能（编码+名称），供 Agent/节点配置面板下拉选择 */
export async function getAiSkillOptionsApi() {
  const raws = await requestClient.get<AiSkillApi.SkillItemRaw[]>(
    `${BASE}/options`,
  );
  return (raws ?? []).map(normalizeSkill);
}

/** 获取技能详情 */
export async function getAiSkillDetailApi(id: number | string) {
  const raw = await requestClient.get<AiSkillApi.SkillItemRaw>(`${BASE}/${id}`);
  return normalizeSkill(raw);
}

/** 创建技能 */
export async function createAiSkillApi(data: AiSkillApi.SkillSaveParams) {
  return requestClient.post<number | string>(BASE, data);
}

/** 更新技能 */
export async function updateAiSkillApi(
  id: number | string,
  data: AiSkillApi.SkillSaveParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 更新启用/停用状态 */
export async function updateAiSkillStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/${id}/status`, null, {
    params: { status },
  });
}

/** 删除技能 */
export async function deleteAiSkillApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}
