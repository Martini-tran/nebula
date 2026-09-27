/**
 * 最近打开：全局搜索空输入时显示的 5 条，也给搜索结果排序加一点权重。只存本机。
 */
import type { SearchKind } from './searchQuery'

export interface RecentItem {
  kind: SearchKind
  id: string
  title: string
  sub: string
  /** 站内路径 */
  to: string
  /** 书签的原网址 */
  url?: string
  time: number
}

const KEY = 'nebula-space:recent-open'
const MAX = 30

export const recentItems = (): RecentItem[] => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

export const recordRecent = (item: Omit<RecentItem, 'time'>) => {
  try {
    const rest = recentItems().filter((r) => !(r.kind === item.kind && r.id === item.id))
    localStorage.setItem(KEY, JSON.stringify([{ ...item, time: Date.now() }, ...rest].slice(0, MAX)))
  } catch {
    // 记不住不影响使用
  }
}

export const forgetRecent = (kind: SearchKind, id: string) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(recentItems().filter((r) => !(r.kind === kind && r.id === id))))
  } catch {
    // 同上
  }
}
