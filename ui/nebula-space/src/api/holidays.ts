/**
 * 法定节假日：后端 GET /space/me/holidays?year=（nebula-service-space，按年的静态 JSON，出自国务院公告）。
 * 逐日给出放假的日子（off: true）与调休上班的日子（off: false）；当年还没公布时 days 为空。
 * VITE_REAL_MODULES 不含 holidays 时走 mock：没有安排，日历只按周末与公历节日标注。
 */
import { get } from '../utils/request'
import { delay, useMockFor } from './mock'

export interface HolidayDay {
  /** YYYY-MM-DD */
  date: string
  /** 所属节日；调休上班的日子也标上是为哪个节调的 */
  name: string
  /** true 放假，false 调休上班 */
  off: boolean
}

export interface HolidayYear {
  year: number
  /** 出处：哪一份通知 */
  source: string
  days: HolidayDay[]
}

const real = {
  fetchHolidays: (year: number) => get<HolidayYear>('/space/me/holidays', { params: { year } }),
}

const mock: typeof real = {
  fetchHolidays: (year) => delay({ year, source: '', days: [] }, 60),
}

export const { fetchHolidays } = useMockFor('holidays') ? mock : real
