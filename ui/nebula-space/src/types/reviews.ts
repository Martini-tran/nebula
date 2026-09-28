/**
 * 日报与周报：正文就是 Markdown，不分模板。对应后端 space_report 表，同一类型同一天一份。
 * 日报用当天（YYYY-MM-DD）当编号；周报用这周第一天，周从哪天开始跟设置走。
 * 周回顾是纯聚合、不存数据。
 */
import type { EntityId } from './space'

export type ReportType = 'day' | 'week'

export interface Report {
  id: EntityId
  type: ReportType
  /** 日报为当天，周报为这周第一天 */
  date: string
  content: string
  updateTime: string
}
