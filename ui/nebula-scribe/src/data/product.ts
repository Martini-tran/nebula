/**
 * 站点级静态文案与导航配置。
 */

export interface NavItem {
  label: string
  href: string
  icon?: string
  /** route=站内路由，external=外链，soon=占位未上线 */
  kind: 'route' | 'external' | 'soon'
}

export const product = {
  name: 'Scribe',
  tagline: '为长篇连载而生的 AI 写作台：设定不散、伏笔不丢、灵感不断。',
  description:
    'Scribe 把「卷章管理 + 沉浸式写作 + AI 协作 + 设定库」放在同一张桌子上，让你在写第 300 章时，依然记得第 3 章埋下的那把刀。',
  author: 'Nebula',
  year: new Date().getFullYear(),
  license: 'Apache-2.0',
  github: 'https://github.com/nebula',
  gitee: 'https://gitee.com/nebula',
} as const

export const navItems: NavItem[] = [
  { label: '首页', href: '/', icon: 'lucide:home', kind: 'route' },
  { label: '我的作品', href: '/works', icon: 'lucide:library', kind: 'route' },
  { label: '设定库', href: '/lore', icon: 'lucide:box', kind: 'route' },
  { label: '灵感', href: '/discover', icon: 'lucide:lightbulb', kind: 'route' },
]

export const socialLinks = [
  { label: 'GitHub', href: product.github, icon: 'lucide:github' },
  { label: 'Gitee', href: product.gitee, icon: 'lucide:git-branch' },
]

/** 首页功能卡片。 */
export const features = [
  {
    icon: 'lucide:list-tree',
    title: '卷章树与大纲',
    desc: '拖拽调整卷章顺序，章节梗概与正文并列，写到哪儿都知道自己在整本书的什么位置。',
  },
  {
    icon: 'lucide:pen-line',
    title: '沉浸式写作台',
    desc: '专注模式隐去一切干扰，只留纸与字；自动保存与字数目标常驻，状态一眼可见。',
  },
  {
    icon: 'lucide:sparkles',
    title: 'AI 八种协作方式',
    desc: '续写、润色、扩写、缩写、改写、找灵感、拆大纲、挑毛病——选中文字即可唤起。',
  },
  {
    icon: 'lucide:box',
    title: '设定库常驻上下文',
    desc: '人物、地点、势力、道具、世界观分类沉淀，标记为常驻后自动进入 AI 上下文。',
  },
  {
    icon: 'lucide:search-check',
    title: '一致性检查',
    desc: '让 AI 通读全书，指出人设崩塌、时间线矛盾与被遗忘的伏笔。',
  },
  {
    icon: 'lucide:activity',
    title: '写作节奏统计',
    desc: '每日字数、连续天数与目标进度，把「今天写没写」变成可量化的习惯。',
  },
]

/** 首页三步工作流。 */
export const workflow = [
  {
    step: '01',
    title: '立骨架',
    desc: '写下一句话立意，让 AI 拆成卷与章的大纲，再按自己的想法调整。',
  },
  {
    step: '02',
    title: '填血肉',
    desc: '在写作台逐章推进，卡住时选中段落交给 AI 续写或扩写，采纳与否你说了算。',
  },
  {
    step: '03',
    title: '守一致',
    desc: '人物与世界观沉淀进设定库，定期让 AI 通读挑错，长线连载不走形。',
  },
]
