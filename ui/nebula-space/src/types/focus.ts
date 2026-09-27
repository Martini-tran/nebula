/**
 * 专注记录：番茄钟挂在任务上，每一段专注都记到某个任务名下。
 * 后端尚未实现（见 docs/ui设计/个人空间/space-focus.html「后端待补」），字段按设计稿拟定。
 */
import type { EntityId } from './space'

export interface FocusSession {
  id: EntityId
  taskId: EntityId | null
  taskTitle: string
  /** YYYY-MM-DD HH:mm:ss */
  startedAt: string
  endedAt: string
  plannedMin: number
  actualMin: number
  status: 'done' | 'abandoned'
  interruptions: number
}

export interface FocusSettings {
  minutes: number
  autoBreak: boolean
  breakMinutes: number
  titleCountdown: boolean
  notify: boolean
}
