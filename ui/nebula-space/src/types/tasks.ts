/**
 * 任务与清单。对应后端 space_task、space_task_list 表（子任务、重复规则存 JSON 列）。
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
  type: 'note' | 'meeting' | 'bookmark' | 'reading' | 'person'
  id: EntityId
  label: string
}

/** 任务来源的名称、图标、跳回去的地址 */
export const SOURCE_META: Record<TaskSource['type'], { name: string; icon: string; path: (id: EntityId) => string }> = {
  note: { name: '笔记', icon: 'lucide:sticky-note', path: (id) => `/notes/${id}` },
  meeting: { name: '会议', icon: 'lucide:users', path: (id) => `/meetings/${id}` },
  bookmark: { name: '书签', icon: 'lucide:bookmark', path: (id) => `/bookmarks?open=${id}` },
  reading: { name: '稍后读', icon: 'lucide:book-open', path: (id) => `/reading/${id}` },
  person: { name: '人物卡', icon: 'lucide:contact-round', path: (id) => `/people?id=${id}` },
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
