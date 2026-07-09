import { get } from '../utils/request'

export interface PopularTag {
  id: number | string
  name: string
  slug: string
  postCount: number
}

export const fetchPopularTags = (limit = 20) =>
  get<PopularTag[]>('/blog/front/tags', { params: { limit } })
