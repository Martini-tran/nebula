/**
 * orccode 产品元信息与首页内容的单一数据源。
 * 图标名称取自 Iconify（项目已安装 @iconify/vue），统一使用 lucide / tabler 集合。
 */

export interface NavItem {
  label: string
  href: string
  /**
   * route    — SPA 路由跳转（href 以 / 开头）
   * external — 跳转到独立站点（如 VitePress 文档站）
   * soon     — 功能暂未上线，仅占位展示
   */
  kind: 'route' | 'external' | 'soon'
  /** 可选图标（Iconify 名称） */
  icon?: string
}

export interface Feature {
  icon: string
  title: string
  description: string
}

export interface WorkflowStep {
  icon: string
  title: string
  description: string
}

export const product = {
  name: 'orccode',
  /** 简短描述，用于页脚等处 */
  tagline: 'Windows 快速启动器',
  /** 首页主标题 */
  headline: '一个快捷键，找到并打开一切',
  /** 一句话简介 */
  summary:
    '常驻后台的命令面板。按下快捷键，输入几个字母，打开应用、文件或插件。支持拼音匹配，可用插件扩展。',
  eyebrow: 'Windows 快速启动器',
  currentVersion: '1.0.0',
  platform: 'Windows 10 / 11 (x64)',
  license: 'PolyForm Noncommercial License 1.0.0',
  author: 'xiangqainlu',
  github: 'https://github.com/Martini-tran/forge',
  gitee: 'https://gitee.com/forwardable/forge',
  year: 2026,
} as const

/** 代码仓库链接，复用于页头图标与页脚。 */
export const socialLinks = [
  { label: 'GitHub', href: product.github, icon: 'simple-icons:github' },
  { label: 'Gitee', href: product.gitee, icon: 'simple-icons:gitee' },
]

/**
 * 顶部导航：均为页面级跳转，不再使用首页内的锚点滚动。
 * 「下载」与其余导航并列，跳转到版本页（沿用原有的版本方式展示）。
 */
export const navItems: NavItem[] = [
  { label: '首页', href: '/', kind: 'route' },
  { label: '文档', href: '/docs/', kind: 'external' },
  { label: '插件市场', href: '', kind: 'soon' },
  { label: '更新记录', href: '/versions', kind: 'route', icon: 'lucide:history' },
]

export const features: Feature[] = [
  {
    icon: 'lucide:keyboard',
    title: '全局唤起',
    description: '任意界面按下快捷键即可呼出，不必切换窗口。',
  },
  {
    icon: 'lucide:rocket',
    title: '启动应用',
    description: '扫描系统已安装的应用，输入名称即可打开。',
  },
  {
    icon: 'lucide:languages',
    title: '拼音搜索',
    description: '支持全拼与首字母匹配，中文应用用拼音也能搜到。',
  },
  {
    icon: 'lucide:blocks',
    title: '插件扩展',
    description: '插件运行在独立窗口与运行时中，按需安装。',
  },
  {
    icon: 'lucide:file-code-2',
    title: '自定义插件',
    description: '按插件规范编写自己的插件，扩展命令与功能。',
  },
  {
    icon: 'lucide:settings-2',
    title: '托盘与设置',
    description: '常驻系统托盘，统一管理快捷键与插件。',
  },
]

export const workflowSteps: WorkflowStep[] = [
  {
    icon: 'lucide:command',
    title: '唤起',
    description: '按下全局快捷键，命令面板即刻浮现在屏幕中央。',
  },
  {
    icon: 'lucide:search',
    title: '搜索',
    description: '输入关键字或拼音，结果实时过滤、键盘上下选择。',
  },
  {
    icon: 'lucide:corner-down-left',
    title: '启动',
    description: '回车执行——打开应用、运行插件，一气呵成。',
  },
]
