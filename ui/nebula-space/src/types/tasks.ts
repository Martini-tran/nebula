/**
 * 任务与清单。后端尚未实现（见 docs/ui设计/个人空间/space-tasks.html「后端待补」），字段按设计稿拟定。
 */
import type { EntityId } from './space'

/** 3 高 2 中 1 低 0 无 */
export type TaskPriority = 0 | 1 | 2 | 3

export const PRIORITY_LABEL: Record<TaskPriority, string> = { 3: '高', 2: '中', 1: '低', 0: '无' }

export type RepeatRule =
  | { type: 'daily' }
  | { type: 'weekdays' }
  | { type: 'weekly'; days: number[] }
  | { type: 'monthly' }

export interface TaskSource {
  type: 'note' | 'meeting' | 'bookmark'
  id: EntityId
  label: string
}

export interface SubTask {
  id: string
  title: string
  done: boolean
}

export interface Task {
  id: EntityId
  title: string
  listId: EntityId | null
  /** YYYY-MM-DD；null 表示在收件箱 */
  dueDate: string | null
  /** HH:mm；null 表示全天 */
  dueTime: string | null
  priority: TaskPriority
  done: boolean
  doneTime: string | null
  /** 预估分钟数，计划视图的负荷条用 */
  estimateMin: number | null
  /** 提前多少分钟提醒，null 不提醒 */
  remindBefore: number | null
  repeat: RepeatRule | null
  subtasks: SubTask[]
  source: TaskSource | null
  note: string
  createTime: string
}

export interface TaskList {
  id: EntityId
  name: string
  color: string
}

export type TaskSaveRequest = Partial<Omit<Task, 'id' | 'createTime' | 'done' | 'doneTime'>>

export interface TaskQuery {
  view?: 'inbox' | 'today' | 'plan' | 'done' | 'all'
  listId?: EntityId
  sourceType?: TaskSource['type']
  sourceId?: EntityId
}
