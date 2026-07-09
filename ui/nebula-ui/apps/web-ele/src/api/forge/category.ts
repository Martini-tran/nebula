import { requestClient } from '#/api/request';

/**
 * Forge-插件分类 API
 * forge 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace ForgeCategoryApi {
  /** 分类条目（camelCase） */
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

/** 分页查询插件分类 */
export async function getForgeCategoryPageApi(
  params: ForgeCategoryApi.CategoryPageQuery,
) {
  return requestClient.get<ForgeCategoryApi.CategoryPageResult>(
    '/forge/admin/plugin-categories/page',
    { params },
  );
}

/** 拉取全部分类（供插件绑定下拉使用） */
export async function getAllForgeCategoriesApi() {
  const result = await getForgeCategoryPageApi({ pageNum: 1, pageSize: 500 });
  return result.records;
}

/** 分类详情 */
export async function getForgeCategoryDetailApi(id: number | string) {
  return requestClient.get<ForgeCategoryApi.CategoryItem>(
    `/forge/admin/plugin-categories/${id}`,
  );
}

/** 创建分类，返回新建 id */
export async function createForgeCategoryApi(
  data: ForgeCategoryApi.CategoryCreateParams,
) {
  return requestClient.post<number | string>(
    '/forge/admin/plugin-categories',
    data,
  );
}

/** 更新分类 */
export async function updateForgeCategoryApi(
  id: number | string,
  data: ForgeCategoryApi.CategoryUpdateParams,
) {
  return requestClient.put<void>(`/forge/admin/plugin-categories/${id}`, data);
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
