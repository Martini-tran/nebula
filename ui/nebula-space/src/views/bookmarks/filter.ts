import type { EntityId } from '../../types/space'

/** 侧栏当前选中的范围，决定书签列表的查询条件 */
export type SpaceFilter =
  | { kind: 'all' }
  | { kind: 'uncategorized' }
  | { kind: 'archived' }
  | { kind: 'broken' }
  | { kind: 'folder'; id: EntityId }
  | { kind: 'tag'; id: EntityId }

export const isSameFilter = (a: SpaceFilter, b: SpaceFilter): boolean => {
  if (a.kind !== b.kind) return false
  if ((a.kind === 'folder' || a.kind === 'tag') && (b.kind === 'folder' || b.kind === 'tag')) {
    return String(a.id) === String(b.id)
  }
  return true
}

/** 筛选 ↔ 地址栏 query，刷新或分享链接后停在同一处 */
export const filterFromQuery = (query: Record<string, unknown>): SpaceFilter => {
  const folder = query.folder
  const tag = query.tag
  const view = query.view
  if (typeof folder === 'string' && folder) return { kind: 'folder', id: folder }
  if (typeof tag === 'string' && tag) return { kind: 'tag', id: tag }
  if (view === 'uncategorized' || view === 'archived' || view === 'broken') return { kind: view }
  return { kind: 'all' }
}

export const filterToQuery = (filter: SpaceFilter): Record<string, string> => {
  if (filter.kind === 'folder') return { folder: String(filter.id) }
  if (filter.kind === 'tag') return { tag: String(filter.id) }
  if (filter.kind === 'all') return {}
  return { view: filter.kind }
}
