/**
 * 记账：只记个人日常收支，不对接银行、不做理财。后端尚未实现（见 docs/ui设计/个人空间/space-ledger.html「后端待补」）。
 * 金额一律以「分」为单位的整数，避免浮点误差。
 */
import type { EntityId } from './space'

export type Direction = 'out' | 'in'

export interface LedgerCategory {
  id: EntityId
  name: string
  /** Iconify 名称，如 lucide:utensils；不用表情 */
  icon: string
  color: string
  kind: Direction
  /** 备注里出现这些词就归到这个分类 */
  keywords: string[]
  sortOrder: number
}

export interface LedgerEntry {
  id: EntityId
  /** 分，正数 */
  amount: number
  direction: Direction
  categoryId: EntityId
  /** YYYY-MM-DD */
  date: string
  note: string
  /** 由周期账单自动生成的 */
  recurringId: EntityId | null
  createTime: string
}

export type LedgerSaveRequest = Omit<LedgerEntry, 'id' | 'createTime' | 'recurringId'> & { recurringId?: EntityId | null }

/** 周期账单：房租、订阅这类每月固定的，到日子自动记一笔 */
export interface Recurring {
  id: EntityId
  note: string
  amount: number
  direction: Direction
  categoryId: EntityId
  /** 每月几号（1-28） */
  day: number
  active: boolean
  /** 从哪个月开始（YYYY-MM） */
  startMonth: string
}

export interface Budget {
  /** YYYY-MM */
  month: string
  /** 总预算（分）；null = 各分类之和 */
  total: number | null
  /** 分类 id → 额度（分） */
  items: Record<string, number>
}
