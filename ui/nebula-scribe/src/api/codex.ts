import { del, get, post, put } from '../utils/request'
import { USE_MOCK, delay } from './mock'
import { mockCodex } from '../data/codex'
import type { CodexEntry, CodexKind, CodexSaveRequest } from '../types/codex'

/** 设定库接口。 */
const BASE = '/scribe'

/** 某作品的设定条目，可按类型过滤。 */
export const fetchCodexEntries = async (
  workId: number | string,
  kind?: CodexKind,
): Promise<CodexEntry[]> => {
  if (USE_MOCK) {
    const records = mockCodex.filter(
      (item) => item.workId === Number(workId) && (!kind || item.kind === kind),
    )
    return delay(records)
  }

  return get<CodexEntry[]>(`${BASE}/works/${workId}/codex`, { params: { kind } })
}

/** 单条设定详情。 */
export const fetchCodexEntry = async (
  workId: number | string,
  entryId: number | string,
): Promise<CodexEntry> => {
  if (USE_MOCK) {
    const entry = mockCodex.find((item) => item.id === Number(entryId))
    if (!entry) {
      throw new Error('设定条目不存在')
    }
    return delay(entry)
  }

  return get<CodexEntry>(`${BASE}/works/${workId}/codex/${entryId}`)
}

/** 新建设定。 */
export const createCodexEntry = async (
  workId: number | string,
  body: CodexSaveRequest,
): Promise<CodexEntry> => {
  if (USE_MOCK) {
    return delay({
      id: Date.now(),
      workId: Number(workId),
      ...body,
      updateTime: new Date().toISOString(),
    } as CodexEntry)
  }

  return post<CodexEntry>(`${BASE}/works/${workId}/codex`, body)
}

/** 更新设定。 */
export const updateCodexEntry = async (
  workId: number | string,
  entryId: number | string,
  body: CodexSaveRequest,
): Promise<void> => {
  if (USE_MOCK) {
    await delay(null)
    return
  }

  await put(`${BASE}/works/${workId}/codex/${entryId}`, body)
}

/** 删除设定。 */
export const deleteCodexEntry = async (
  workId: number | string,
  entryId: number | string,
): Promise<void> => {
  if (USE_MOCK) {
    await delay(null)
    return
  }

  await del(`${BASE}/works/${workId}/codex/${entryId}`)
}
