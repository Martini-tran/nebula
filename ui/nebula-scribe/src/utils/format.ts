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
 * 字数统计：去掉空白后的字符数——汉字、标点、字母、数字各算 1 个，空格与换行不算。
 * 与后端 WordCounter 同一口径（按码点计，全角空格也算空白），保证编辑器里看到的字数与入库一致。
 */
export const countWords = (text?: string | null): number => {
  if (!text) {
    return 0
  }
  let n = 0
  for (const ch of text) {
    // JS 的 \s 已涵盖全角空格 U+3000 与不换行空格
    if (!/\s/.test(ch)) n += 1
  }
  return n
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
