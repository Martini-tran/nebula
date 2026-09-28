/**
 * 个人空间偏好。后端存在 space_user_setting（每人一行，整份 JSON），新加字段只改这里和 DEFAULT_SETTINGS。
 * 主题只存本机（跟着设备走），不在这里。
 */
import type { ModuleKey } from '../config/modules'
import type { CaptureMode } from '../composables/useQuickCapture'
import type { NoteColor } from './notes'

export interface SpaceSettings {
  /** 关掉的模块：从导航与「今天」里消失，数据保留 */
  disabledModules: ModuleKey[]
  /** 登录后首页 */
  home: ModuleKey
  /** 一周从哪天开始：1 周一 / 0 周日 */
  weekStart: 0 | 1
  /** 晚间回顾：到点后「今天」顶部换成回顾卡 */
  eveningReview: { enabled: boolean; time: string }
  /** 周回顾提醒：到点后「今天」显示生成周报的提示卡；weekday 0-6（0 周日） */
  weeklyReview: { enabled: boolean; weekday: number; time: string }
  /** 新随手记默认保留几天；0 = 长期 */
  noteTtlDays: number
  noteColor: NoteColor
  /** Ctrl+Shift+Space 打开快速记录时默认的类型 */
  captureMode: CaptureMode
  /** 页面开着时的提醒（浏览器通知，未授权时退回页内提示） */
  remind: { tasks: boolean; habits: boolean }
  /** 稍后读：每月读完几篇 */
  readingGoal: number
  /** 摘录库顶部「今天回顾一条」 */
  dailyQuote: boolean
}

export const DEFAULT_SETTINGS: SpaceSettings = {
  disabledModules: [],
  home: 'today',
  weekStart: 1,
  eveningReview: { enabled: true, time: '18:00' },
  weeklyReview: { enabled: true, weekday: 5, time: '17:00' },
  noteTtlDays: 7,
  noteColor: 'yellow',
  captureMode: 'note',
  remind: { tasks: true, habits: true },
  readingGoal: 10,
  dailyQuote: true,
}
