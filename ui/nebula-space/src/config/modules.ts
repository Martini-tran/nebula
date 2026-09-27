/**
 * 模块注册表：顶部导航、「更多」菜单、占位页、设置页的模块开关都从这里读。
 *
 * status 表示前端是否已实现：planned 的模块路由指向占位页，
 * 实现后改为 ready 并在 router 里挂上真实视图即可。
 */
import { pinia } from '../stores'
import { useSettingsStore } from '../stores/settings'

export type ModuleKey =
  | 'today'
  | 'bookmarks'
  | 'notes'
  | 'tasks'
  | 'meetings'
  | 'calendar'
  | 'habits'
  | 'reading'
  | 'files'
  | 'ledger'
  | 'goals'
  | 'people'
  | 'share'

export interface SpaceModule {
  key: ModuleKey
  label: string
  icon: string
  path: string
  /** main 显示在顶部导航，more 收进「更多」菜单 */
  group: 'main' | 'more'
  status: 'ready' | 'planned'
  /** 一句话说明，用于「更多」菜单与占位页 */
  description: string
  /** 计划在第几批实现，占位页展示用 */
  batch: number
}

export const MODULES: SpaceModule[] = [
  {
    key: 'today',
    label: '今天',
    icon: 'lucide:sun',
    path: '/today',
    group: 'main',
    status: 'ready',
    description: '今日任务、会议时间线、随手记与今天收藏，一页看完今天。',
    batch: 4,
  },
  {
    key: 'bookmarks',
    label: '书签',
    icon: 'lucide:bookmark',
    path: '/bookmarks',
    group: 'main',
    status: 'ready',
    description: '收藏网址，按目录与标签整理，导入导出浏览器书签。',
    batch: 2,
  },
  {
    key: 'notes',
    label: '随手记',
    icon: 'lucide:pencil-line',
    path: '/notes',
    group: 'main',
    status: 'ready',
    description: '先记下来再决定要不要留：临时笔记 7 天后自动归档。',
    batch: 3,
  },
  {
    key: 'tasks',
    label: '任务',
    icon: 'lucide:square-check-big',
    path: '/tasks',
    group: 'main',
    status: 'ready',
    description: '收件箱、今天、未来 7 天，按清单归类的个人待办。',
    batch: 3,
  },
  {
    key: 'meetings',
    label: '会议',
    icon: 'lucide:users',
    path: '/meetings',
    group: 'main',
    status: 'ready',
    description: '会前议程、会中记录决议与待办、会后一键纪要。',
    batch: 4,
  },
  {
    key: 'calendar',
    label: '日历',
    icon: 'lucide:calendar',
    path: '/calendar',
    group: 'main',
    status: 'ready',
    description: '任务、会议、习惯放到同一条时间轴，把任务排进时间块。',
    batch: 5,
  },
  {
    key: 'habits',
    label: '习惯',
    icon: 'lucide:activity',
    path: '/habits',
    group: 'main',
    status: 'ready',
    description: '勾选、计数、时长三种打卡，看连续与频率。',
    batch: 5,
  },
  {
    key: 'reading',
    label: '稍后读',
    icon: 'lucide:book-open',
    path: '/reading',
    group: 'more',
    status: 'planned',
    description: '阅读队列、干净的阅读模式、划线与摘录库。',
    batch: 7,
  },
  {
    key: 'files',
    label: '文件柜',
    icon: 'lucide:folder-closed',
    path: '/files',
    group: 'more',
    status: 'planned',
    description: '证件、合同、发票放一处，可生成限时分享链接。',
    batch: 7,
  },
  {
    key: 'ledger',
    label: '记账',
    icon: 'lucide:wallet',
    path: '/ledger',
    group: 'more',
    status: 'planned',
    description: '一行文字记一笔，看本月花在哪、超没超预算。',
    batch: 7,
  },
  {
    key: 'goals',
    label: '目标与纪念日',
    icon: 'lucide:target',
    path: '/goals',
    group: 'more',
    status: 'planned',
    description: '年度目标自动取进度；生日、纪念日、证件到期提醒。',
    batch: 7,
  },
  {
    key: 'people',
    label: '人物卡',
    icon: 'lucide:contact-round',
    path: '/people',
    group: 'more',
    status: 'planned',
    description: '和某人的往来、互相的承诺、下次可以聊什么。',
    batch: 7,
  },
  {
    key: 'share',
    label: '公开主页',
    icon: 'lucide:globe',
    path: '/share',
    group: 'more',
    status: 'planned',
    description: '可选开启的公开主页与书签合集分享，逐块决定公开什么。',
    batch: 7,
  },
]

export const MAIN_MODULES = MODULES.filter((m) => m.group === 'main')
export const MORE_MODULES = MODULES.filter((m) => m.group === 'more')

export const findModule = (key: ModuleKey) => MODULES.find((m) => m.key === key)!

/** 登录后默认进入的页面：设置里选的首页；它被关掉或还没实现时退回「今天」 */
export const defaultHomePath = () => {
  const settings = useSettingsStore(pinia)
  const home = MODULES.find((m) => m.key === settings.data.home && m.status === 'ready' && settings.isEnabled(m.key))
  return (home ?? findModule('today')).path
}
