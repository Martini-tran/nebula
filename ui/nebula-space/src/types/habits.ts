/**
 * 习惯打卡。对应后端 space_habit、space_habit_log 表（频率、提醒存 JSON 列）。
 * 连续天数按频率规则实时计算，不落库（utils/habitStats.ts）。
 */
import type { EntityId } from './space'

export type HabitKind = 'check' | 'count' | 'duration'

export type HabitFreq =
  | { type: 'daily' }
  /** 每周 N 次，哪天做都行 */
  | { type: 'weekly_n'; n: number }
  /** 指定星期几，0=周日 … 6=周六 */
  | { type: 'weekdays'; days: number[] }

export interface Habit {
  id: EntityId
  name: string
  icon: string
  kind: HabitKind
  /** 目标值：勾选型为 1，计数型为次数，时长型为分钟 */
  target: number
  /** 计数型的单位，如「杯」 */
  unit: string
  freq: HabitFreq
  /** 提醒时间 HH:mm */
  reminders: string[]
  /** 时长型：专注记录自动累加进来 */
  fromFocus: boolean
  archived: boolean
  sortOrder: number
  createTime: string
}

export interface HabitLog {
  id: EntityId
  habitId: EntityId
  /** YYYY-MM-DD */
  date: string
  value: number
  note: string
  /** 事后补打卡 */
  backfilled: boolean
  time: string
}

export type HabitSaveRequest = Partial<Omit<Habit, 'id' | 'createTime'>>

/** 补打卡最多允许补前几天（与后端 SpaceHabitServiceImpl.BACKFILL_DAYS 一致） */
export const HABIT_BACKFILL_DAYS = 2
