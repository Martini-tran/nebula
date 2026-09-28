/**
 * 各模块可选的图标：一律用 Iconify 的 lucide 集合，值存「集合:名字」（如 lucide:target），渲染用 <Icon :icon>。
 * 项目里不用表情当图标；要新图标就从 lucide 里挑名字加进对应列表。
 */

export interface IconOption {
  icon: string
  label: string
}

/** 不是「集合:名字」的（早先存的表情）换成默认图标 */
export const iconOr = (icon: string | null | undefined, fallback: string) => (icon && icon.includes(':') ? icon : fallback)

// ── 年度目标与纪念日 ──

export const GOAL_ICON_DEFAULT = 'lucide:target'
export const GOAL_ICONS: IconOption[] = [
  { icon: 'lucide:target', label: '目标' },
  { icon: 'lucide:footprints', label: '跑步' },
  { icon: 'lucide:book-open', label: '阅读' },
  { icon: 'lucide:piggy-bank', label: '存钱' },
  { icon: 'lucide:rocket', label: '发布' },
  { icon: 'lucide:heart-pulse', label: '健康' },
  { icon: 'lucide:camera', label: '摄影' },
  { icon: 'lucide:plane', label: '旅行' },
  { icon: 'lucide:guitar', label: '音乐' },
  { icon: 'lucide:dumbbell', label: '健身' },
  { icon: 'lucide:sprout', label: '成长' },
  { icon: 'lucide:pen-line', label: '写作' },
]

export const ANNIV_ICON_DEFAULT = 'lucide:calendar'
export const ANNIV_ICONS: IconOption[] = [
  { icon: 'lucide:cake', label: '生日' },
  { icon: 'lucide:gem', label: '纪念日' },
  { icon: 'lucide:party-popper', label: '节日' },
  { icon: 'lucide:id-card', label: '证件' },
  { icon: 'lucide:house', label: '住房' },
  { icon: 'lucide:cat', label: '宠物' },
  { icon: 'lucide:calendar', label: '日子' },
  { icon: 'lucide:plane', label: '旅行' },
  { icon: 'lucide:graduation-cap', label: '考试' },
  { icon: 'lucide:stethoscope', label: '体检' },
  { icon: 'lucide:car', label: '车' },
  { icon: 'lucide:briefcase', label: '工作' },
]
