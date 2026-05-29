export type ReviewsNavGroup = 'relay' | 'guides'

export type ReviewsNavItem = {
  key: string
  label: string
  description: string
  to: string
  group: ReviewsNavGroup
}

export const reviewsNavItems: ReviewsNavItem[] = [
  {
    key: 'directory',
    label: '收录中转站',
    description: '整理可用中转站与基础信息',
    to: '/reviews/directory',
    group: 'relay',
  },
  {
    key: 'recommend',
    label: '中转站推荐',
    description: '编辑精选与推荐分排序',
    to: '/reviews/recommend',
    group: 'relay',
  },
  {
    key: 'guides',
    label: '所有套餐',
    description: '所有中转站套餐与每百万 token 单价',
    to: '/reviews/guides',
    group: 'guides',
  },
]
