import { requestClient } from '#/api/request';

export namespace ForgeCategoryApi {
  /** 后端 SNAKE_CASE 序列化的原始结构，仅内部规范化使用 */
  export interface CategoryItemRaw {
    id: number | string;
    code: string;
    name: string;
    description?: string;
    icon_file_id?: number | string | null;
    sort_order?: number;
    status?: number;
    create_time?: string;
    update_time?: string;
  }

  /** 规范化后的分类条目（camelCase） */
  export interface CategoryItem {
    id: number | string;
    code: string;
    name: string;
    description?: string;
    iconFileId?: number | string | null;
    sortOrder?: number;
    status?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface CategoryPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
  }

  export interface CategoryCreateParams {
    code: string;
    name: string;
    description?: string;
    iconFileId?: number | string | null;
    sortOrder?: number;
    status?: number;
  }

  export type CategoryUpdateParams = Partial<CategoryCreateParams>;

  export interface CategoryPageResult {
    records: CategoryItem[];
    total: number;
    current: number;
    size: number;
  }
}

function normalizeCategory(
  raw: ForgeCategoryApi.CategoryItemRaw,
): ForgeCategoryApi.CategoryItem {
  return {
    id: raw.id,
    code: raw.code,
    name: raw.name,
    description: raw.description,
    iconFileId: raw.icon_file_id ?? null,
    sortOrder: raw.sort_order ?? 0,
    status: raw.status ?? 1,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function serializeCategory(
  data: ForgeCategoryApi.CategoryCreateParams | ForgeCategoryApi.CategoryUpdateParams,
) {
  const payload: Record<string, unknown> = {};
  if ('code' in data) payload.code = data.code;
  if ('name' in data) payload.name = data.name;
  if ('description' in data) payload.description = data.description;
  if ('iconFileId' in data) payload.icon_file_id = data.iconFileId;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;
  if ('status' in data) payload.status = data.status;
  return payload;
}

/** 分页查询插件分类 */
export async function getForgeCategoryPageApi(
  params: ForgeCategoryApi.CategoryPageQuery,
) {
  const result = await requestClient.get<{
    records: ForgeCategoryApi.CategoryItemRaw[];
    total: number;
    current: number;
    size: number;
  }>('/forge/admin/plugin-categories/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeCategory),
  } as ForgeCategoryApi.CategoryPageResult;
}

/** 拉取全部分类（供插件绑定下拉使用） */
export async function getAllForgeCategoriesApi() {
  const result = await getForgeCategoryPageApi({ pageNum: 1, pageSize: 500 });
  return result.records;
}

/** 分类详情 */
export async function getForgeCategoryDetailApi(id: number | string) {
  const raw = await requestClient.get<ForgeCategoryApi.CategoryItemRaw>(
    `/forge/admin/plugin-categories/${id}`,
  );
  return normalizeCategory(raw);
}

/** 创建分类，返回新建 id */
export async function createForgeCategoryApi(
  data: ForgeCategoryApi.CategoryCreateParams,
) {
  return requestClient.post<number | string>(
    '/forge/admin/plugin-categories',
    serializeCategory(data),
  );
}

/** 更新分类 */
export async function updateForgeCategoryApi(
  id: number | string,
  data: ForgeCategoryApi.CategoryUpdateParams,
) {
  return requestClient.put<void>(
    `/forge/admin/plugin-categories/${id}`,
    serializeCategory(data),
  );
}

/** 更新分类状态（0 禁用 / 1 启用） */
export async function updateForgeCategoryStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `/forge/admin/plugin-categories/${id}/status`,
    undefined,
    { params: { status } },
  );
}

/** 删除分类（存在插件引用时后端拒绝） */
export async function deleteForgeCategoryApi(id: number | string) {
  return requestClient.delete<void>(`/forge/admin/plugin-categories/${id}`);
}
