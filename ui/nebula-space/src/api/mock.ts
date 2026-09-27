/**
 * mock 开关与工具。
 *
 * 个人空间多数模块还没有后端（见 docs/ui设计/个人空间 各页末尾的「后端待补」），
 * 这些模块的 api/*.ts 统一写成：
 *   if (useMockFor('notes')) return delay(假数据)
 *   return get/post(真实路径)
 * 后端某个模块就绪后，把它加进 VITE_REAL_MODULES 即可单独切到真实接口，页面代码不动。
 */

export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

/** 已接通后端的模块，即使全局开着 mock 也走真实接口（逗号分隔，如 bookmarks,notes）。 */
const REAL_MODULES = new Set(
  (import.meta.env.VITE_REAL_MODULES ?? '')
    .split(',')
    .map((item: string) => item.trim())
    .filter(Boolean),
)

/** 按模块判断是否使用 mock。 */
export const useMockFor = (module: string) => USE_MOCK && !REAL_MODULES.has(module)

/** 模拟网络时延，让加载态在开发期也能被看到。 */
export const delay = <T>(data: T, ms = 260): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(data), ms))

/** 内存分页，供 mock 列表接口复用。 */
export const paginate = <T>(records: T[], pageNum = 1, pageSize = 20) => {
  const current = Math.max(1, pageNum)
  const size = Math.max(1, pageSize)
  const start = (current - 1) * size
  return {
    records: records.slice(start, start + size),
    total: records.length,
    current,
    size,
  }
}

/** mock 数据的自增 ID。 */
let seq = Date.now()
export const nextId = () => String(++seq)

/**
 * 存进 localStorage 的 mock 表：随手记、任务这类「没有后端也想先用起来」的模块用它，刷新不丢。
 * 读不到或解析失败就用种子数据；数据结构升级时改 key 的版本号即可。
 */
export const createMockTable = <T extends { id: string }>(key: string, seed: () => T[]) => {
  const storageKey = `nebula-space:mock:${key}`
  let rows: T[] | null = null

  const load = (): T[] => {
    if (rows) return rows
    try {
      const raw = localStorage.getItem(storageKey)
      rows = raw ? (JSON.parse(raw) as T[]) : seed()
    } catch {
      rows = seed()
    }
    return rows
  }

  const save = () => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(rows ?? []))
    } catch {
      // 存不下就只留在内存里
    }
  }

  return {
    all: () => load(),
    find: (id: string | number) => load().find((row) => row.id === String(id)),
    insert: (row: T) => {
      load().unshift(row)
      save()
      return row
    },
    update: (id: string | number, patch: Partial<T>) => {
      const row = load().find((item) => item.id === String(id))
      if (row) Object.assign(row, patch)
      save()
      return row
    },
    remove: (id: string | number) => {
      const list = load()
      const index = list.findIndex((item) => item.id === String(id))
      if (index >= 0) list.splice(index, 1)
      save()
      return index >= 0
    },
    save,
  }
}
