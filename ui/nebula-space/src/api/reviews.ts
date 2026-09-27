/**
 * 周报接口。后端还没有（设计见 space-review.html「后端待补」），路径按设计拟定为 /space/me/weekly-reports；
 * 未接通时走下面的 mock，数据存在浏览器 localStorage。
 * 周回顾本身是聚合（前端分别取任务、会议、专注、习惯、随手记拼出来），后端可以补一个
 * GET /space/me/review/weekly?week= 聚合接口代替。
 */
import { get, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { nowStamp } from '../utils/date'
import type { WeeklyReport, WeeklyReportSaveRequest } from '../types/reviews'

const BASE = '/space/me/weekly-reports'

const real = {
  /** 全部周报，新的在前（不含正文以外的大字段也行，这里直接要整份） */
  fetchWeeklyReports: () => get<WeeklyReport[]>(BASE),
  /** 没写过返回 null */
  fetchWeeklyReport: (week: string) => get<WeeklyReport | null>(`${BASE}/${week}`),
  /** 按周覆盖保存 */
  saveWeeklyReport: (week: string, body: WeeklyReportSaveRequest) => put<WeeklyReport>(`${BASE}/${week}`, body),
}

type Row = WeeklyReport & { id: string }

const table = createMockTable<Row>('weekly-reports.v1', () => [])

const mock: typeof real = {
  fetchWeeklyReports: () => delay(structuredClone([...table.all()].sort((a, b) => b.week.localeCompare(a.week))), 100),
  fetchWeeklyReport: (week) => delay(structuredClone(table.all().find((r) => r.week === week) ?? null), 100),
  saveWeeklyReport: async (week, body) => {
    const exists = table.all().find((r) => r.week === week)
    const row = exists
      ? table.update(exists.id, { ...body, updateTime: nowStamp() })!
      : table.insert({ ...body, id: nextId(), week, updateTime: nowStamp() })
    return delay(structuredClone(row), 160)
  },
}

const api = useMockFor('reviews') ? mock : real

export const { fetchWeeklyReports, fetchWeeklyReport, saveWeeklyReport } = api
