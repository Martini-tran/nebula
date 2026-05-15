import { get } from '../utils/request'

export interface PopularTag {
  id: number
  name: string
  slug: string
  post_count: number
}

export const fetchPopularTags = (limit = 20) =>
  get<PopularTag[]>('/front/tags', { params: { limit } })
