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

export interface ShowcaseItem {
  /** 截图基名，对应 public/screenshots/<image>.png（支持 <image>-dark / -light 主题变体）。 */
  image: string
  /** 小标签（区块徽标）。 */
  badge: string
  icon: string
  title: string
  description: string
  /** 要点列表，逐条展示在文案下方。 */
  bullets: string[]
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
  { label: '插件市场', href: '/market', kind: 'route', icon: 'lucide:store' },
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

/**
 * 首页「图文交替」展示区的内容。每项配一张产品截图（public/screenshots/<image>.png）。
 * 截图缺失时由 ScreenshotFrame 自动显示占位块，不影响布局。
 */
export const showcaseItems: ShowcaseItem[] = [
  {
    image: 'pinyin',
    badge: '中文友好',
    icon: 'lucide:languages',
    title: '拼音搜索，中文应用也能秒搜',
    description:
      '不用切换输入法，直接敲拼音。无论是全拼、首字母还是混合输入，都能命中目标应用与文件。',
    bullets: ['全拼匹配，如「weixin」找到微信', '首字母匹配，如「wx」同样命中', '模糊容错，少敲几个字母也能搜到'],
  },
  {
    image: 'plugins',
    badge: '插件系统',
    icon: 'lucide:blocks',
    title: '插件扩展，能力随需生长',
    description:
      '内置插件市场（即将上线），按需安装。每个插件运行在独立窗口与运行时中，互不干扰、安全隔离。',
    bullets: ['独立窗口与运行时，稳定隔离', '按需安装，保持核心轻量', '首发内置「剪贴板历史」插件'],
  },
  {
    image: 'custom-plugin',
    badge: '可编程',
    icon: 'lucide:file-code-2',
    title: '编写属于你自己的插件',
    description:
      '按照插件规范，用熟悉的方式扩展命令与功能，把你的高频操作沉淀成一条命令。',
    bullets: ['遵循统一插件规范', '自定义命令与交互', '复用现有运行时能力'],
  },
]
