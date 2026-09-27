/**
 * 周报。周回顾是纯聚合、不存数据；周报草稿保存后落 space_weekly_report（见 space-review.html「后端待补」）。
 * 一周用它的第一天（YYYY-MM-DD）当编号，周从哪天开始跟设置走。
 */
import type { EntityId } from './space'

/** 草稿每一条后面的来源小标签，点了跳回原记录 */
export interface ReportRef {
  type: 'task' | 'meeting' | 'focus' | 'goal'
  id?: EntityId
  label: string
}

export interface ReportItem {
  /** 同一来源每次生成的 key 相同，重新生成时据此保留手改内容 */
  key: string
  text: string
  /** 按规则生成的原文；null 表示手动加的一条 */
  auto: string | null
  ref?: ReportRef | null
}

export interface ReportSection {
  key: string
  title: string
  /** 有序列表（完成、计划）还是无序（风险） */
  ordered: boolean
  items: ReportItem[]
}

/** okr：按年度目标分段（需要开着「目标与纪念日」） */
export type ReportTemplate = 'standard' | 'done' | 'byList' | 'okr'

export interface ReportSources {
  /** 纳入哪些清单的任务；'none' 代表不在任何清单里的任务 */
  lists: string[]
  decisions: boolean
  myActions: boolean
  /** 别人名下、影响我的待办，放进「风险与需要的支持」 */
  waiting: boolean
  focus: boolean
}

export interface WeeklyReport {
  id: EntityId
  /** 这周第一天 */
  week: string
  template: ReportTemplate
  sources: ReportSources
  sections: ReportSection[]
  /** 删掉的自动条目，重新生成时不再出现 */
  removed: string[]
  /** 导出的 Markdown，搜索与复制用 */
  content: string
  updateTime: string
}

export type WeeklyReportSaveRequest = Omit<WeeklyReport, 'id' | 'week' | 'updateTime'>
