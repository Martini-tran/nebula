import { diffDays, todayYmd } from '../../utils/date'
import { parseMeetingItems } from '../../utils/meetingItems'
import type { Meeting } from '../../types/meetings'

const minutesOf = (hm: string) => {
  const [h, m] = hm.split(':').map(Number)
  return (h ?? 0) * 60 + (m ?? 0)
}

/** 结束时间 HH:mm */
export const endTime = (meeting: Meeting) => {
  const total = minutesOf(meeting.startTime) + meeting.durationMin
  return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

/** 状态角标：已记录 / 记录中 / 3 小时后 / 该开始了 / 未开始 / 没有记录 */
export const statusOf = (meeting: Meeting, now = new Date()) => {
  if (meeting.status === 'done') return { text: '已记录', tone: 'done' as const }
  if (meeting.status === 'live') return { text: '记录中', tone: 'live' as const }
  const days = diffDays(todayYmd(), meeting.date)
  if (days < 0) return { text: '没有记录', tone: 'muted' as const }
  if (days > 0) return { text: '未开始', tone: 'muted' as const }
  const diff = minutesOf(meeting.startTime) - (now.getHours() * 60 + now.getMinutes())
  if (diff <= 0) return { text: diff > -meeting.durationMin ? '该开始了' : '没有记录', tone: diff > -meeting.durationMin ? ('soon' as const) : ('muted' as const) }
  if (diff < 60) return { text: `${diff} 分钟后`, tone: 'soon' as const }
  return { text: `${Math.round(diff / 60)} 小时后`, tone: 'muted' as const }
}

/** 一行摘要：3 条决议 · 5 项待办 · 我的 2 项 / 议程 4 项已备好 */
export const summaryOf = (meeting: Meeting, myNames: string[]) => {
  if (meeting.status === 'planned') {
    return meeting.agenda.length ? `议程 ${meeting.agenda.length} 项已备好` : '还没准备议程'
  }
  const { decisions, actions } = parseMeetingItems(meeting.content, myNames, meeting.date)
  const mine = actions.filter((a) => a.mine).length
  const parts = [decisions.length && `${decisions.length} 条决议`, actions.length && `${actions.length} 项待办`, mine && `我的 ${mine} 项`]
  return parts.filter(Boolean).join(' · ') || '没有决议与待办'
}
