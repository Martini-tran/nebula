/**
 * mock 开关与工具。
 *
 * 所有 api/*.ts 的函数都遵循同一形态：
 *   if (USE_MOCK) return delay(假数据)
 *   return get/post(真实路径)
 * 后端就绪后把 VITE_USE_MOCK 置为 false 即可整体切换，页面代码不动；
 * 也可以把已接通的模块写进 VITE_REAL_MODULES，单独切到真实接口。
 */

export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

/** 已接通后端的模块，即使全局开着 mock 也走真实接口（逗号分隔，如 works,lore）。 */
const REAL_MODULES = new Set(
  (import.meta.env.VITE_REAL_MODULES ?? '')
    .split(',')
    .map((item: string) => item.trim())
    .filter(Boolean),
)

/** 按模块判断是否使用 mock：后端逐个模块接入时，前端也逐个模块切换。 */
export const useMockFor = (module: string) => USE_MOCK && !REAL_MODULES.has(module)

/** 模拟网络时延，让加载态在开发期也能被看到。 */
export const delay = <T>(data: T, ms = 260): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(data), ms))

/** 内存分页，供 mock 列表接口复用。 */
export const paginate = <T>(records: T[], pageNum = 1, pageSize = 12) => {
  const current = Math.max(1, pageNum)
  const size = Math.max(1, pageSize)
  const start = (current - 1) * size
  return {
    records: records.slice(start, start + size),
    total: records.length,
    current,
    size,
    pages: Math.max(1, Math.ceil(records.length / size)),
  }
}
