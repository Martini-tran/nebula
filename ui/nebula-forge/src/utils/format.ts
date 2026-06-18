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

/** 字节数转可读大小。 */
export const formatBytes = (value?: number | null): string => {
  const bytes = typeof value === 'number' && Number.isFinite(value) ? value : 0
  if (bytes <= 0) {
    return '—'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const size = bytes / 1024 ** i
  return `${size.toFixed(i === 0 ? 0 : 1).replace(/\.0$/, '')} ${units[i]}`
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

/** 评分保留一位小数。 */
export const formatRating = (value?: number | null): string => {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : 0
  return n.toFixed(1)
}
