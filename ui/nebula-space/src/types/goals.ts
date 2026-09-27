/**
 * 年度目标与纪念日。后端尚未实现（见 docs/ui设计/个人空间/space-goals.html「后端待补」）。
 * 目标进度按来源实时聚合、不落库；纪念日的农历日期每年换算成当年公历。
 */
import type { EntityId } from './space'

/** 进度从哪来：习惯打卡、稍后读读完、记账结余、任务清单完成数；或者手动记 */
export type GoalSource = 'habit' | 'reading' | 'ledger' | 'task_list' | 'manual'

export interface KeyResult {
  id: string
  title: string
  done: boolean
  doneDate: string | null
  /** 挂一个任务清单，显示那个清单的完成情况 */
  listId: EntityId | null
}

export interface Goal {
  id: EntityId
  year: number
  title: string
  icon: string
  /** metric 数值型；milestone 关键结果型 */
  kind: 'metric' | 'milestone'
  target: number
  unit: string
  source: GoalSource
  sourceId: EntityId | null
  /** 每条来源记录折算多少（打卡一次 = 5 公里） */
  factor: number
  /** 开始用 Space 之前已经有的量 */
  baseline: number
  /** 手动型的当前值 */
  manualValue: number
  krs: KeyResult[]
  sortOrder: number
  createTime: string
}

export type GoalSaveRequest = Partial<Omit<Goal, 'id' | 'createTime'>>

export type AnnivType = 'countdown' | 'annual' | 'countup'

export const ANNIV_TYPES: Record<AnnivType, { label: string; hint: string }> = {
  countdown: { label: '倒数日', hint: '假期、考试、证件到期' },
  annual: { label: '纪念日 · 每年', hint: '生日、纪念日，每年重复' },
  countup: { label: '正数日', hint: '在一起第几天、到家第几天' },
}

export interface Anniversary {
  id: EntityId
  title: string
  icon: string
  type: AnnivType
  /** 公历日期；每年重复的只看月日，农历的看 lunarMonth / lunarDay */
  date: string
  calendar: 'solar' | 'lunar'
  lunarMonth: number | null
  lunarDay: number | null
  /** 提前几天提醒；null 不提醒 */
  remindDays: number | null
  /** 提醒时生成任务 */
  createTask: boolean
  taskTitle: string
  /** 已为哪一次（公历日期）生成过任务，避免重复 */
  taskFor: string | null
  /** 关联文件柜里的文件（证件扫描件） */
  fileId: EntityId | null
  note: string
  tag: string
  createTime: string
}

export type AnniversarySaveRequest = Partial<Omit<Anniversary, 'id' | 'createTime'>>
