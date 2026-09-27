/**
 * 通用展示格式化工具。
 */

/** 数量缩写：≥1万→x.x万，≥1千→x.xk，否则原样。 */
export const formatCount = (value?: number | null): string => {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : 0
  if (n >= 10000) {
    return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万`
  }
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`
  }
  return String(n)
}

/** ISO 时间转 YYYY-MM-DD（无值返回空串）。 */
export const formatDate = (value?: string | null): string => {
  if (!value) {
    return ''
  }
  // 兼容 "2026-06-12T10:00:00" 与 "2026-06-12 10:00:00"
  const datePart = value.replace('T', ' ').split(' ')[0]
  return datePart ?? ''
}

/** 相对时间：刚刚 / x分钟前 / x小时前 / x天前 / 具体日期。 */
export const formatRelative = (value?: string | null, now = new Date()): string => {
  if (!value) {
    return ''
  }
  const time = new Date(value.replace(' ', 'T')).getTime()
  if (!Number.isFinite(time)) {
    return formatDate(value)
  }

  const diff = now.getTime() - time
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(value)
}

/** 分钟 → 「2h 10m」「45m」 */
export const formatMinutes = (min: number): string => {
  const h = Math.floor(Math.abs(min) / 60)
  const m = Math.round(Math.abs(min) % 60)
  return h ? `${h}h${m ? ` ${m}m` : ''}` : `${m}m`
}
