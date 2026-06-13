/**
 * orccode 产品元信息与首页内容的单一数据源。
 * 图标名称取自 Iconify（项目已安装 @iconify/vue），统一使用 lucide / tabler 集合。
 */

export interface NavItem {
  label: string
  /** 锚点（首页内滚动）或路由路径（以 / 开头） */
  href: string
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
  /** 中文副标题 */
  tagline: '为效率而生的 Windows 快速启动器',
  /** 一句话简介 */
  summary:
    '一个全局热键唤起的命令面板：秒搜应用、文件与插件，回车即启动。支持拼音搜索与可扩展插件系统，让每一次操作都更快一步。',
  eyebrow: 'Windows 快速启动器',
  currentVersion: '1.0.0',
  platform: 'Windows 10 / 11 (x64)',
  license: 'PolyForm Noncommercial License 1.0.0',
  author: 'xiangqainlu',
  repoUrl: 'https://github.com',
  year: 2026,
} as const

export const navItems: NavItem[] = [
  { label: '首页', href: '#top' },
  { label: '功能', href: '#features' },
  { label: '工作流', href: '#workflow' },
  { label: '插件', href: '#plugins' },
  { label: '版本', href: '/versions' },
  { label: '下载', href: '#download' },
]

export const features: Feature[] = [
  {
    icon: 'lucide:keyboard',
    title: '全局热键唤起',
    description: '任意界面一键呼出命令面板，无需切换窗口，所想即所搜。',
  },
  {
    icon: 'lucide:rocket',
    title: '应用扫描启动',
    description: '自动扫描系统已安装应用，输入名称即可秒级定位并启动。',
  },
  {
    icon: 'lucide:languages',
    title: '拼音智能搜索',
    description: '支持全拼与首字母模糊匹配，中文应用也能用拼音飞速命中。',
  },
  {
    icon: 'lucide:blocks',
    title: '可扩展插件系统',
    description: '插件独立窗口与运行时隔离，按需安装，功能无限延展。',
  },
  {
    icon: 'lucide:clipboard-list',
    title: '剪贴板历史',
    description: '内置剪贴板插件，随时回溯历史记录，复制粘贴不再丢失。',
  },
  {
    icon: 'lucide:settings-2',
    title: '托盘与设置中心',
    description: '常驻系统托盘，集中管理快捷键、插件与个性化设置。',
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
