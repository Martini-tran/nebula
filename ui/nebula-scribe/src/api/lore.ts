import { del, get, post, put } from '../utils/request'
import { USE_MOCK, delay } from './mock'
import { mockLore } from '../data/lore'
import type { LoreEntry, LoreKind, LoreSaveRequest } from '../types/lore'

/** 设定库接口。 */
const BASE = '/scribe'

/** 某作品的设定条目，可按类型过滤。 */
export const fetchLoreEntries = async (
  workId: number | string,
  kind?: LoreKind,
): Promise<LoreEntry[]> => {
  if (USE_MOCK) {
    const records = mockLore.filter(
      (item) => item.workId === Number(workId) && (!kind || item.kind === kind),
    )
    return delay(records)
  }

  return get<LoreEntry[]>(`${BASE}/works/${workId}/lore`, { params: { kind } })
}

/** 单条设定详情。 */
export const fetchLoreEntry = async (
  workId: number | string,
  entryId: number | string,
): Promise<LoreEntry> => {
  if (USE_MOCK) {
    const entry = mockLore.find((item) => item.id === Number(entryId))
    if (!entry) {
      throw new Error('设定条目不存在')
    }
    return delay(entry)
  }

  return get<LoreEntry>(`${BASE}/works/${workId}/lore/${entryId}`)
}

/** 新建设定。 */
export const createLoreEntry = async (
  workId: number | string,
  body: LoreSaveRequest,
): Promise<LoreEntry> => {
  if (USE_MOCK) {
    return delay({
      id: Date.now(),
      workId: Number(workId),
      ...body,
      updateTime: new Date().toISOString(),
    } as LoreEntry)
  }

  return post<LoreEntry>(`${BASE}/works/${workId}/lore`, body)
}

/** 更新设定。 */
export const updateLoreEntry = async (
  workId: number | string,
  entryId: number | string,
  body: LoreSaveRequest,
): Promise<void> => {
  if (USE_MOCK) {
    await delay(null)
    return
  }

  await put(`${BASE}/works/${workId}/lore/${entryId}`, body)
}

/** 删除设定。 */
export const deleteLoreEntry = async (
  workId: number | string,
  entryId: number | string,
): Promise<void> => {
  if (USE_MOCK) {
    await delay(null)
    return
  }

  await del(`${BASE}/works/${workId}/lore/${entryId}`)
}
