/**
 * 从会议正文里抽决议与待办，并生成纪要 Markdown。
 * - 决议：行首「决议：」（Ctrl+D 会自动加上）
 * - 待办：行首「[] 」或「@某人 」；「@我」或没写负责人的「[] 」算我的。行内的日期（周三前、9月30日）识别为截止日
 */
import type { Meeting } from '../types/meetings'
import { parseTaskInput } from './taskParser'
import { monthDay, weekdayLabel } from './date'

export interface MeetingDecision {
  line: number
  text: string
}

export interface MeetingAction {
  line: number
  /** 去掉负责人与日期后的事项 */
  text: string
  owner: string
  mine: boolean
  due: string | null
}

const DECISION_RE = /^\s*(?:[-*] )?决议[:：]\s*(.+)$/
const TODO_RE = /^\s*(?:[-*] )?\[[ xX]?\]\s*(.+)$/
const AT_RE = /^@(\S+?)[\s:：,，]+(.+)$/

/** 行尾多余的标点与「前」字（「周三前」识别掉日期后会剩下一个「前」） */
const tidy = (text: string) => text.replace(/\s*前?[，,。；;、\s]*$/, '').replace(/[，,]\s*$/, '').trim()

export const parseMeetingItems = (content: string, myNames: string[], meetingDate: string) => {
  const decisions: MeetingDecision[] = []
  const actions: MeetingAction[] = []
  const me = new Set(['我', ...myNames.filter(Boolean)])

  content.split('\n').forEach((raw, line) => {
    const decision = DECISION_RE.exec(raw)
    if (decision) {
      decisions.push({ line, text: decision[1]!.trim() })
      return
    }
    const todo = TODO_RE.exec(raw)
    let body = todo ? todo[1]!.trim() : raw.trim()
    if (!todo && !body.startsWith('@')) return
    let owner = '我'
    const at = AT_RE.exec(body)
    if (at) {
      owner = at[1]!
      body = at[2]!
    } else if (!todo) {
      return
    }
    // 截止日期按会议当天为基准解析
    const parsed = parseTaskInput(body, meetingDate)
    const dateSpan = parsed.spans.find((s) => s.kind === 'date')
    const text = tidy(dateSpan ? body.slice(0, dateSpan.start) + body.slice(dateSpan.end) : body)
    if (!text) return
    actions.push({ line, text, owner, mine: me.has(owner), due: dateSpan ? (parsed.dueDate ?? null) : null })
  })

  return { decisions, actions }
}

export const formatDue = (due: string | null) => (due ? `${monthDay(due)} ${weekdayLabel(due)}` : '—')

/** 纪要 Markdown：概况 → 决议 → 待办表 → 原始记录 */
export const meetingMarkdown = (meeting: Meeting, myNames: string[]) => {
  const { decisions, actions } = parseMeetingItems(meeting.content, myNames, meeting.date)
  // 纪要要发给别人看，「我」换成自己的名字
  const self = myNames[0] || '我'
  const who = (name: string) => (name === '我' ? self : name)
  const present = meeting.attendees.filter((a) => !a.absent).map((a) => (a.me ? self : a.name))
  const absent = meeting.attendees.filter((a) => a.absent).map((a) => a.name)
  const time = meeting.startedAt && meeting.endedAt
    ? `${meeting.startedAt.slice(11, 16)}–${meeting.endedAt.slice(11, 16)}`
    : meeting.startTime
  const lines = [
    `# ${meeting.title} · 纪要`,
    '',
    `- 时间：${monthDay(meeting.date)} ${time}`,
    `- 参会：${present.join('、') || '—'}${absent.length ? `（缺席：${absent.join('、')}）` : ''}`,
    '',
    '## 决议',
    ...(decisions.length ? decisions.map((d, i) => `${i + 1}. ${d.text}`) : ['（无）']),
    '',
    '## 待办',
    ...(actions.length
      ? ['| 事项 | 负责人 | 截止 |', '| --- | --- | --- |', ...actions.map((a) => `| ${a.text} | ${who(a.owner)} | ${formatDue(a.due)} |`)]
      : ['（无）']),
    '',
    '## 原始记录',
    '',
    meeting.content.trim(),
  ]
  return lines.join('\n')
}
