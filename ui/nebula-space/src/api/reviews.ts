/**
 * 日报周报接口：后端 /space/me/reports（nebula-service-space，表 space_report）。
 * VITE_REAL_MODULES 不含 reviews 时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 正文是 Markdown，整份覆盖保存；保存空正文等于删除这一份。
 * 草稿生成、周报按日报汇总都在前端做（views/review/reportDraft.ts）。
 * 周回顾本身是聚合（前端分别取任务、会议、专注、习惯、随手记拼出来），后端可以补一个
 * GET /space/me/review/weekly?week= 聚合接口代替。
 */
import { get, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { nowStamp } from '../utils/date'
import type { Report, ReportType } from '../types/reviews'

export interface ReportQuery {
  type?: ReportType
  /** 按日期筛选（两端都含） */
  from?: string
  to?: string
}

const BASE = '/space/me/reports'

const real = {
  /** 新的在前 */
  fetchReports: (query: ReportQuery = {}) => get<Report[]>(BASE, { params: query }),
  /** 没写过返回 null */
  fetchReport: (type: ReportType, date: string) => get<Report | null>(`${BASE}/${type}/${date}`),
  /** 整份覆盖；content 为空时删除并返回 null */
  saveReport: (type: ReportType, date: string, content: string) => put<Report | null>(`${BASE}/${type}/${date}`, { content }),
}

type Row = Report & { id: string }

const table = createMockTable<Row>('reports.v1', () => [])

const mock: typeof real = {
  fetchReports: (query = {}) =>
    delay(
      structuredClone(
        table
          .all()
          .filter((r) => (!query.type || r.type === query.type) && (!query.from || r.date >= query.from) && (!query.to || r.date <= query.to))
          .sort((a, b) => b.date.localeCompare(a.date) || a.type.localeCompare(b.type)),
      ),
      100,
    ),
  fetchReport: (type, date) => delay(structuredClone(table.all().find((r) => r.type === type && r.date === date) ?? null), 100),
  saveReport: async (type, date, content) => {
    const exists = table.all().find((r) => r.type === type && r.date === date)
    if (!content.trim()) {
      if (exists) table.remove(exists.id)
      return delay(null, 120)
    }
    const row = exists
      ? table.update(exists.id, { content, updateTime: nowStamp() })!
      : table.insert({ id: nextId(), type, date, content, updateTime: nowStamp() })
    return delay(structuredClone(row), 160)
  },
}

const api = useMockFor('reviews') ? mock : real

export const { fetchReports, fetchReport, saveReport } = api
