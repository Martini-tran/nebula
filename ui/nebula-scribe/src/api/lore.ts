import { del, get, post, put, ApiError } from '../utils/request'
import { delay, useMockFor } from './mock'
import { mockLore } from '../data/lore'
import type { EntityId } from '../types/work'
import type { LoreEntry, LoreKind, LoreSaveRequest } from '../types/lore'

/** 设定库接口，挂在作品下：/scribe/works/{workId}/lore。 */
const BASE = '/scribe'

const LORE_MOCK = useMockFor('lore')

/**
 * mock 设定库：按作品懒初始化，规则对齐后端——
 * 列表不带 detail、固定的在前再按更新时间倒序、同一作品内名称不重复（不分类型）。
 */
const mockStore = new Map<string, LoreEntry[]>()

const mockEntriesOf = (workId: EntityId): LoreEntry[] => {
  const key = String(workId)
  let list = mockStore.get(key)
  if (!list) {
    list = mockLore.filter((item) => item.workId === Number(workId)).map((item) => ({ ...item }))
    mockStore.set(key, list)
  }
  return list
}

const findMockEntry = (workId: EntityId, entryId: EntityId): LoreEntry => {
  const entry = mockEntriesOf(workId).find((item) => String(item.id) === String(entryId))
  if (!entry) {
    throw new ApiError('设定条目不存在', 404)
  }
  return entry
}

const cleanList = (values?: string[]) => [...new Set((values ?? []).map((v) => v.trim()).filter(Boolean))]

/** 与后端同口径地整理请求：去空白、别名去掉与名称相同的、pinned 缺省为否 */
const applyMockSave = (workId: EntityId, body: LoreSaveRequest, selfId?: number): Omit<LoreEntry, 'id'> => {
  const name = body.name.trim()
  const taken = mockEntriesOf(workId).some((item) => item.name === name && item.id !== selfId)
  if (taken) {
    throw new ApiError(`本作品已有名为「${name}」的设定`, 409)
  }
  return {
    workId: Number(workId),
    kind: body.kind,
    name,
    aliases: cleanList(body.aliases).filter((alias) => alias !== name),
    summary: body.summary?.trim() || null,
    detail: body.detail?.trim() || null,
    tags: cleanList(body.tags),
    pinned: Boolean(body.pinned),
    updateTime: new Date().toISOString(),
  }
}

/** 某作品的设定条目，可按类型过滤；不带 detail。 */
export const fetchLoreEntries = async (workId: EntityId, kind?: LoreKind): Promise<LoreEntry[]> => {
  if (LORE_MOCK) {
    const records = mockEntriesOf(workId)
      .filter((item) => !kind || item.kind === kind)
      .sort(
        (a, b) =>
          Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)) ||
          (b.updateTime ?? '').localeCompare(a.updateTime ?? ''),
      )
      .map((item) => ({ ...item, detail: null }))
    return delay(records)
  }

  return get<LoreEntry[]>(`${BASE}/works/${workId}/lore`, { params: { kind } })
}

/** 单条设定详情。 */
export const fetchLoreEntry = async (workId: EntityId, entryId: EntityId): Promise<LoreEntry> => {
  if (LORE_MOCK) {
    return delay({ ...findMockEntry(workId, entryId) })
  }

  return get<LoreEntry>(`${BASE}/works/${workId}/lore/${entryId}`)
}

/** 新建设定；同名时抛出 code=409 的 ApiError。 */
export const createLoreEntry = async (workId: EntityId, body: LoreSaveRequest): Promise<LoreEntry> => {
  if (LORE_MOCK) {
    const created: LoreEntry = { id: Date.now(), ...applyMockSave(workId, body) }
    mockEntriesOf(workId).push(created)
    return delay({ ...created })
  }

  return post<LoreEntry>(`${BASE}/works/${workId}/lore`, body)
}

/** 修改设定（整表单覆盖）；改成已有的名字时抛出 code=409 的 ApiError。 */
export const updateLoreEntry = async (
  workId: EntityId,
  entryId: EntityId,
  body: LoreSaveRequest,
): Promise<LoreEntry> => {
  if (LORE_MOCK) {
    const entry = findMockEntry(workId, entryId)
    Object.assign(entry, applyMockSave(workId, body, entry.id))
    return delay({ ...entry })
  }

  return put<LoreEntry>(`${BASE}/works/${workId}/lore/${entryId}`, body)
}

/** 删除设定（移入回收站）。 */
export const deleteLoreEntry = async (workId: EntityId, entryId: EntityId): Promise<void> => {
  if (LORE_MOCK) {
    const list = mockEntriesOf(workId)
    list.splice(list.indexOf(findMockEntry(workId, entryId)), 1)
    await delay(null)
    return
  }

  await del(`${BASE}/works/${workId}/lore/${entryId}`)
}
