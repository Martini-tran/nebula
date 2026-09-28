/**
 * 专注记录接口：后端 /space/me/focus-sessions（nebula-service-space，表 space_focus_session）。
 * VITE_REAL_MODULES 不含 focus 时走下面的 mock，数据存在浏览器 localStorage。
 * 计时以前端为准：一轮结束（完成或放弃）时上报一次；记录只增不改。
 */
import { get, post } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { FocusSession } from '../types/focus'

export interface FocusQuery {
  /** 按开始日期筛选（含） */
  from?: string
  to?: string
  taskId?: EntityId
}

const BASE = '/space/me/focus-sessions'

const real = {
  fetchFocusSessions: (query: FocusQuery = {}) => get<FocusSession[]>(BASE, { params: query }),
  createFocusSession: (body: Omit<FocusSession, 'id'>) => post<FocusSession>(BASE, body),
}

type Row = FocusSession & { id: string }

const table = createMockTable<Row>('focus.v1', () => {
  const today = todayYmd()
  const rows: Row[] = []
  const add = (days: number, start: string, min: number, taskId: string | null, taskTitle: string, interruptions = 0, status: Row['status'] = 'done') => {
    const date = addDays(today, -days)
    const [h, m] = start.split(':').map(Number)
    const end = new Date(2000, 0, 1, h!, m! + min)
    const hm = `${String(end.getHours()).padStart(2, '0')}:${String(end.getMinutes()).padStart(2, '0')}`
    rows.push({ id: `f${rows.length + 1}`, taskId, taskTitle, startedAt: `${date} ${start}:00`, endedAt: `${date} ${hm}:00`, plannedMin: 25, actualMin: min, status, interruptions })
  }
  // 本周与上周各若干轮，统计图才有东西可看
  for (let d = 13; d >= 1; d -= 1) {
    if (d % 7 === 0) continue
    add(d, '09:30', 25, null, 'space 书签 CRUD', d % 3 === 0 ? 1 : 0)
    if (d % 2 === 0) add(d, '14:00', 25, null, 'space 书签 CRUD', 1)
    if (d % 3 === 1) add(d, '20:30', 30, 't4', '读 Prompt caching 文档')
  }
  add(0, '09:00', 25, 't2', '评审会前看完书签导入方案', 1)
  add(0, '09:30', 25, 't2', '评审会前看完书签导入方案')
  return rows
})

const mock: typeof real = {
  fetchFocusSessions: (query = {}) =>
    delay(
      structuredClone(
        table
          .all()
          .filter((s) => (!query.from || s.startedAt.slice(0, 10) >= query.from) && (!query.to || s.startedAt.slice(0, 10) <= query.to))
          .filter((s) => query.taskId === undefined || String(s.taskId) === String(query.taskId))
          .sort((a, b) => a.startedAt.localeCompare(b.startedAt)),
      ),
      100,
    ),
  createFocusSession: async (body) => delay(structuredClone(table.insert({ ...body, id: nextId() } as Row)), 100),
}

const api = useMockFor('focus') ? mock : real

export const { fetchFocusSessions, createFocusSession } = api
