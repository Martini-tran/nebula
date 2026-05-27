export type ReviewsNavItem = {
  key: string
  label: string
  description: string
  to: string
}

export const reviewsNavItems: ReviewsNavItem[] = [
  {
    key: 'directory',
    label: '收录中转站',
    description: '整理可用中转站与基础信息',
    to: '/reviews/directory',
  },
  {
    key: 'reviews',
    label: '中转站测评',
    description: '体验、速度、稳定性与服务记录',
    to: '/reviews',
  },
  {
    key: 'compare',
    label: '对比中转',
    description: '横向比较节点、价格和可用场景',
    to: '/reviews/compare',
  },
  {
    key: 'guides',
    label: '选购指南',
    description: '按使用场景筛选合适方案',
    to: '/reviews/guides',
  },
]
