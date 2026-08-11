/**
 * 通用展示格式化工具。
 */

/** 字数缩写：≥1万→x.x万，≥1千→x.xk，否则原样。 */
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

/**
 * 中文稿件字数统计：汉字逐字计，连续的西文单词按 1 个词计，
 * 空白与常见标点不计入（与主流写作软件的口径一致）。
 */
export const countWords = (text?: string | null): number => {
  if (!text) {
    return 0
  }
  const cjk = text.match(/[一-龥぀-ヿ]/g)?.length ?? 0
  const words = text.match(/[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*/g)?.length ?? 0
  return cjk + words
}

/** 按平均写作速度估算完成时长（分钟）。 */
export const estimateMinutes = (words: number, wordsPerMinute = 300): number => {
  if (words <= 0) return 0
  return Math.max(1, Math.round(words / wordsPerMinute))
}

/** 百分比展示，输入 0~1。 */
export const formatPercent = (value?: number | null): string => {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : 0
  return `${Math.round(n * 100)}%`
}
