import { requestClient } from '#/api/request';

/**
 * 博客-分类 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace BlogCategoryApi {
  export interface CategoryItem {
    id: number | string;
    parentId?: number | string | null;
    name: string;
    slug: string;
    description?: string;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
    children?: CategoryItem[];
  }

  export interface CategoryCreateParams {
    name: string;
    slug: string;
    description?: string;
    parentId?: number | string | null;
    sortOrder?: number;
  }

  export type CategoryUpdateParams = Partial<CategoryCreateParams>;
}

function extractCategoryItems(
  data:
    | BlogCategoryApi.CategoryItem[]
    | { data?: BlogCategoryApi.CategoryItem[] | null }
    | null
    | undefined,
) {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.data)) return data.data;
  return [];
}

function buildCategoryTree(items: BlogCategoryApi.CategoryItem[]) {
  const hasNestedChildren = items.some((item) => (item.children?.length ?? 0) > 0);
  if (hasNestedChildren) return items;

  const nodeMap = new Map<string, BlogCategoryApi.CategoryItem>();
  const roots: BlogCategoryApi.CategoryItem[] = [];

  for (const item of items) {
    nodeMap.set(String(item.id), { ...item, children: [] });
  }

  for (const item of nodeMap.values()) {
    const parentId = item.parentId;
    if (parentId == null || parentId === '' || String(parentId) === '0') {
      roots.push(item);
      continue;
    }

    const parent = nodeMap.get(String(parentId));
    if (parent) {
      parent.children = [...(parent.children ?? []), item];
    } else {
      roots.push(item);
    }
  }

  return roots;
}

/** 获取分类树（含子分类） */
export async function getBlogCategoryTreeApi() {
  const data = await requestClient.get<
    BlogCategoryApi.CategoryItem[] | { data?: BlogCategoryApi.CategoryItem[] }
  >('/blog/admin/categories');
  return buildCategoryTree(extractCategoryItems(data));
}

/** 创建分类，返回新建 id */
export async function createBlogCategoryApi(
  data: BlogCategoryApi.CategoryCreateParams,
) {
  return requestClient.post<number | string>('/blog/admin/categories', data);
}

/** 更新分类 */
export async function updateBlogCategoryApi(
  id: number | string,
  data: BlogCategoryApi.CategoryUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/categories/${id}`, data);
}

/** 删除分类（有子分类时后端返回业务错误） */
export async function deleteBlogCategoryApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/categories/${id}`);
}
