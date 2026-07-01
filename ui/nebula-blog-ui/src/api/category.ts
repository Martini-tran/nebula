import { get } from '../utils/request'

export interface CategoryNode {
  id: number | string
  name: string
  slug: string
  children: CategoryNode[]
}

export interface FetchCategoryTreeParams {
  parentId?: number | string
}

export const fetchCategoryTree = (params: FetchCategoryTreeParams = {}) =>
  get<CategoryNode[]>('/blog/front/categories', {
    params: params.parentId ? { parentId: params.parentId } : undefined,
  })
