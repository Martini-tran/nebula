import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchHolidays, type HolidayDay } from '../api/holidays'

/**
 * 法定节假日与调休，按年取一次后缓存在内存里。
 * 页面在显示某段日期前调 ensure(那几年)；取不到就当没有安排，日历退回只按周末判断。
 */
export const useHolidayStore = defineStore('holidays', () => {
  const days = ref<Record<string, HolidayDay>>({})
  const requested = new Set<number>()

  const ensure = async (...years: number[]) => {
    const missing = [...new Set(years)].filter((y) => !requested.has(y))
    if (!missing.length) return
    missing.forEach((y) => requested.add(y))
    const results = await Promise.allSettled(missing.map((y) => fetchHolidays(y)))
    const next = { ...days.value }
    results.forEach((r, i) => {
      if (r.status === 'fulfilled') r.value.days.forEach((d) => (next[d.date] = d))
      // 失败的年份允许下次再取
      else requested.delete(missing[i]!)
    })
    days.value = next
  }

  return { days, ensure }
})
