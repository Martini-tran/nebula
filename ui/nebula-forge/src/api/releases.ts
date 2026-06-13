import type { Release } from '../types/release'
import { releases } from '../data/releases'

/**
 * 版本数据访问层。
 *
 * 当前阶段直接返回本地静态数据（src/data/releases.ts）。
 * 后端 `/releases` 接口就绪后，把下面两个函数的实现替换为真实请求即可，
 * 页面无需改动：
 *
 *   import { get } from '../utils/request'
 *   export const fetchReleases = () => get<Release[]>('/releases')
 *   export const fetchLatestRelease = () => get<Release>('/releases/latest')
 */

const sortByDateDesc = (list: Release[]): Release[] =>
  [...list].sort((a, b) => b.date.localeCompare(a.date))

/** 获取全部发布版本，按发布日期倒序。 */
export const fetchReleases = (): Promise<Release[]> =>
  Promise.resolve(sortByDateDesc(releases))

/** 获取最新稳定版（优先取 isLatest，回退到日期最新）。 */
export const fetchLatestRelease = (): Promise<Release | null> => {
  const sorted = sortByDateDesc(releases)
  const latest = sorted.find((item) => item.isLatest) ?? sorted[0] ?? null
  return Promise.resolve(latest)
}
