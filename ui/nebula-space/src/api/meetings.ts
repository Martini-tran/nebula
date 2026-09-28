/**
 * 会议接口：后端 /space/me/meetings（nebula-service-space，表 space_meeting）。
 * VITE_REAL_MODULES 不含 meetings 时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 决议与待办不单独建表：前端从正文里识别（utils/meetingItems.ts）。
 * 局部保存区分「没传」和「传 null」（如结束会议时 currentAgendaId: null）。
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nextWeekday, nowStamp, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Meeting, MeetingSaveRequest } from '../types/meetings'

export interface MeetingQuery {
  /** 起止日期（含），不传为全部 */
  from?: string
  to?: string
}

const BASE = '/space/me/meetings'

const real = {
  fetchMeetings: (query: MeetingQuery = {}) => get<Meeting[]>(BASE, { params: query }),
  fetchMeeting: (id: EntityId) => get<Meeting>(`${BASE}/${id}`),
  createMeeting: (body: MeetingSaveRequest & { title: string; date: string; startTime: string }) => post<Meeting>(BASE, body),
  updateMeeting: (id: EntityId, body: MeetingSaveRequest) => put<Meeting>(`${BASE}/${id}`, body),
  deleteMeeting: (id: EntityId) => del<void>(`${BASE}/${id}`),
}

// ── mock ──

type MeetingRow = Meeting & { id: string }

const hm = (offsetMin: number) => {
  const d = new Date(Date.now() + offsetMin * 60_000)
  return `${String(d.getHours()).padStart(2, '0')}:${String(Math.floor(d.getMinutes() / 5) * 5).padStart(2, '0')}`
}

const blank = (): Omit<MeetingRow, 'id' | 'title' | 'date' | 'startTime'> => ({
  durationMin: 30,
  template: null,
  attendees: [{ name: '我', me: true }],
  agenda: [],
  currentAgendaId: null,
  content: '',
  status: 'planned',
  startedAt: null,
  endedAt: null,
  syncedTasks: {},
  createTime: nowStamp(),
})

const seed = (): MeetingRow[] => {
  const t = todayYmd()
  const agenda = (items: [string, number, number?][]) =>
    items.map(([title, budgetMin, used], i) => ({ id: `a${i + 1}`, title, budgetMin, usedSec: (used ?? 0) * 60 }))
  const rows: (Partial<MeetingRow> & Pick<MeetingRow, 'title' | 'date' | 'startTime'>)[] = [
    {
      title: '产品周会', date: t, startTime: hm(-150), durationMin: 45, template: 'weekly', status: 'done',
      startedAt: `${t} ${hm(-150)}:00`, endedAt: `${t} ${hm(-105)}:00`,
      attendees: [{ name: '我', me: true }, { name: '张工' }, { name: '李工' }, { name: '王工' }],
      agenda: agenda([['上周回顾', 15, 14], ['本周计划', 20, 22], ['风险', 10, 9]]),
      content: [
        '## 上周回顾',
        'space 前端第一版已提交，书签导入可用。',
        '',
        '## 本周计划',
        '决议：本周先把 /space/me 前台接口拆出来，admin 接口留给后台。',
        '决议：导入方案下午单独评审。',
        '@我 评审会前看完书签导入方案，今天',
        '@王工 给数据库迁移窗口，下周二前',
        '@张工 整理 gateway 路由文档',
        '',
        '## 风险',
        'MinIO 桶容量快满了。',
        '决议：先配生命周期规则，再谈扩容。',
        '@我 和运维确认 MinIO 扩容，后天',
      ].join('\n'),
      syncedTasks: { '评审会前看完书签导入方案': 't2', '和运维确认 MinIO 扩容': 't6' },
    },
    {
      title: '书签导入方案评审', date: t, startTime: hm(180), durationMin: 30, template: 'review',
      attendees: [{ name: '我', me: true }, { name: '陈工' }, { name: '张工' }, { name: '李工' }],
      agenda: agenda([['背景：为什么要导入', 5], ['方案：同步解析 + 同名复用', 10], ['重复与失败怎么处理', 10], ['排期', 5]]),
    },
    {
      title: '产品周会', date: nextWeekday(1), startTime: '10:00', durationMin: 60, template: 'weekly',
      attendees: [{ name: '我', me: true }, { name: '张工' }, { name: '李工' }, { name: '王工' }],
      agenda: agenda([['上周回顾', 15], ['本周计划', 30], ['风险', 15]]),
    },
    {
      title: '1:1 · 张工', date: addDays(t, -3), startTime: '16:00', durationMin: 30, template: 'one-on-one', status: 'done',
      startedAt: `${addDays(t, -3)} 16:00:00`, endedAt: `${addDays(t, -3)} 16:28:00`,
      attendees: [{ name: '我', me: true }, { name: '张工' }],
      agenda: agenda([['近况', 10, 9], ['困难', 10, 12], ['反馈', 10, 7]]),
      content: '## 近况\n张工在做网关限流。\n\n## 困难\n@我 给出前台接口清单，下周一\n@张工 限流规则整理成文档\n\n## 反馈\n决议：每两周一次 1:1。',
    },
  ]
  return rows.map((row, i) => ({ ...blank(), ...row, id: `m${i + 1}` }))
}

const table = createMockTable<MeetingRow>('meetings.v1', seed)

const byTime = (a: Meeting, b: Meeting) => `${a.date} ${a.startTime}`.localeCompare(`${b.date} ${b.startTime}`)

const mock: typeof real = {
  fetchMeetings: (query = {}) =>
    delay(
      structuredClone(
        table
          .all()
          .filter((m) => (!query.from || m.date >= query.from) && (!query.to || m.date <= query.to))
          .sort(byTime),
      ),
      140,
    ),

  fetchMeeting: async (id) => {
    const row = table.find(id)
    if (!row) throw new Error('会议不存在或已删除')
    return delay(structuredClone(row), 100)
  },

  createMeeting: async (body) => delay(structuredClone(table.insert({ ...blank(), ...body, id: nextId() } as MeetingRow)), 140),

  updateMeeting: async (id, body) => {
    const row = table.update(id, body as Partial<MeetingRow>)
    if (!row) throw new Error('会议不存在或已删除')
    return delay(structuredClone(row), 80)
  },

  deleteMeeting: async (id) => {
    table.remove(id)
    return delay(undefined, 100)
  },
}

const api = useMockFor('meetings') ? mock : real

export const { fetchMeetings, fetchMeeting, createMeeting, updateMeeting, deleteMeeting } = api
