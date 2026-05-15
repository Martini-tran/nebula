import { get } from '../utils/request'

export interface CategoryNode {
  id: number
  name: string
  slug: string
  children: CategoryNode[]
}

export interface FetchCategoryTreeParams {
  parentId?: number
}

export const fetchCategoryTree = (params: FetchCategoryTreeParams = {}) =>
  get<CategoryNode[]>('/blog/front/categories', {
    params: params.parentId ? { parent_id: params.parentId } : undefined,
  })
