import { requestClient } from '#/api/request';

export namespace BlogCategoryApi {
  export interface CategoryItem {
    id: number | string;
    parentId?: number | string | null;
    name: string;
    slug: string;
    description?: string;
    sortOrder?: number;
    created_at?: string;
    updated_at?: string;
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

/** 获取分类树（含子分类） */
export async function getBlogCategoryTreeApi() {
  return requestClient.get<BlogCategoryApi.CategoryItem[]>('/blog/admin/categories');
}

/** 创建分类，返回新建 id */
export async function createBlogCategoryApi(data: BlogCategoryApi.CategoryCreateParams) {
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
