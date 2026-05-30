import { requestClient } from '#/api/request';

export namespace BlogCategoryApi {
  export interface CategoryItemRaw {
    id: number | string;
    parentId?: number | string | null;
    parent_id?: number | string | null;
    name: string;
    slug: string;
    description?: string;
    sortOrder?: number;
    sort_order?: number;
    createTime?: string;
    createdAt?: string;
    created_at?: string;
    updateTime?: string;
    updatedAt?: string;
    updated_at?: string;
    children?: CategoryItemRaw[];
  }

  export interface CategoryItem {
    id: number | string;
    parentId?: number | string | null;
    name: string;
    slug: string;
    description?: string;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
    createdAt?: string;
    updatedAt?: string;
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

function normalizeCategoryItem(
  item: BlogCategoryApi.CategoryItemRaw,
): BlogCategoryApi.CategoryItem {
  return {
    id: item.id,
    parentId: item.parentId ?? item.parent_id ?? null,
    name: item.name,
    slug: item.slug,
    description: item.description,
    sortOrder: item.sortOrder ?? item.sort_order ?? 0,
    createTime: item.createTime ?? item.createdAt ?? item.created_at,
    updateTime: item.updateTime ?? item.updatedAt ?? item.updated_at,
    createdAt: item.createdAt ?? item.created_at,
    updatedAt: item.updatedAt ?? item.updated_at,
    children: item.children?.map(normalizeCategoryItem) ?? [],
  };
}

function extractCategoryItems(
  data:
    | BlogCategoryApi.CategoryItemRaw[]
    | { data?: BlogCategoryApi.CategoryItemRaw[] | null }
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

function serializeCategoryPayload(
  data: BlogCategoryApi.CategoryCreateParams | BlogCategoryApi.CategoryUpdateParams,
) {
  const payload: Record<string, unknown> = {};

  if ('name' in data) payload.name = data.name;
  if ('slug' in data) payload.slug = data.slug;
  if ('description' in data) payload.description = data.description;
  if ('parentId' in data) payload.parent_id = data.parentId;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;

  return payload;
}

/** 鑾峰彇鍒嗙被鏍戯紙鍚瓙鍒嗙被锛?*/
export async function getBlogCategoryTreeApi() {
  const data = await requestClient.get<
    BlogCategoryApi.CategoryItemRaw[] | { data?: BlogCategoryApi.CategoryItemRaw[] }
  >(
    '/blog/admin/categories',
  );
  return buildCategoryTree(extractCategoryItems(data).map(normalizeCategoryItem));
}

/** 鍒涘缓鍒嗙被锛岃繑鍥炴柊寤?id */
export async function createBlogCategoryApi(data: BlogCategoryApi.CategoryCreateParams) {
  return requestClient.post<number | string>(
    '/blog/admin/categories',
    serializeCategoryPayload(data),
  );
}

/** 鏇存柊鍒嗙被 */
export async function updateBlogCategoryApi(
  id: number | string,
  data: BlogCategoryApi.CategoryUpdateParams,
) {
  return requestClient.put<void>(
    `/blog/admin/categories/${id}`,
    serializeCategoryPayload(data),
  );
}

/** 鍒犻櫎鍒嗙被锛堟湁瀛愬垎绫绘椂鍚庣杩斿洖涓氬姟閿欒锛?*/
export async function deleteBlogCategoryApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/categories/${id}`);
}
