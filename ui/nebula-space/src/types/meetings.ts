/**
 * 会议记录：一个人的会议笔记本，不做日历同步与多人协同。
 * 对应后端 space_meeting 表（参会人、议程、已同步任务存 JSON 列）。
 */
import type { EntityId } from './space'

export interface AgendaItem {
  id: string
  title: string
  /** 时间预算（分钟） */
  budgetMin: number
  /** 实际用时（秒），记录中按当前议题累加 */
  usedSec: number
}

export interface Attendee {
  name: string
  me?: boolean
  absent?: boolean
}

export type MeetingStatus = 'planned' | 'live' | 'done'

export interface Meeting {
  id: EntityId
  title: string
  /** YYYY-MM-DD */
  date: string
  /** HH:mm */
  startTime: string
  durationMin: number
  template: string | null
  attendees: Attendee[]
  agenda: AgendaItem[]
  /** 当前议题（记录中） */
  currentAgendaId: string | null
  /** 正文 Markdown：决议行以「决议：」开头，待办行以「[] 」或「@某人」开头 */
  content: string
  status: MeetingStatus
  startedAt: string | null
  endedAt: string | null
  /** 已同步进任务的「我的待办」：待办文本 → 任务 id */
  syncedTasks: Record<string, EntityId>
  createTime: string
}

export type MeetingSaveRequest = Partial<Omit<Meeting, 'id' | 'createTime'>>

export interface MeetingTemplate {
  key: string
  name: string
  durationMin: number
  agenda: { title: string; budgetMin: number }[]
}

export const MEETING_TEMPLATES: MeetingTemplate[] = [
  { key: 'weekly', name: '周会', durationMin: 45, agenda: [{ title: '上周回顾', budgetMin: 15 }, { title: '本周计划', budgetMin: 20 }, { title: '风险', budgetMin: 10 }] },
  { key: 'review', name: '方案评审', durationMin: 30, agenda: [{ title: '背景', budgetMin: 5 }, { title: '方案', budgetMin: 10 }, { title: '问题', budgetMin: 10 }, { title: '结论', budgetMin: 5 }] },
  { key: 'one-on-one', name: '1:1', durationMin: 30, agenda: [{ title: '近况', budgetMin: 10 }, { title: '困难', budgetMin: 10 }, { title: '反馈', budgetMin: 10 }] },
]
