/**
 * 专注记录：番茄钟挂在任务上，每一段专注都记到某个任务名下。
 * 对应后端 space_focus_session 表；taskTitle 是任务标题快照，任务改名或删除后仍按当时的名字显示。
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
