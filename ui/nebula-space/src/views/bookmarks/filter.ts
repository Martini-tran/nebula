import type { EntityId } from '../../types/space'

/** 侧栏当前选中的范围，决定书签列表的查询条件 */
export type SpaceFilter =
  | { kind: 'all' }
  | { kind: 'uncategorized' }
  | { kind: 'archived' }
  | { kind: 'folder'; id: EntityId }
  | { kind: 'tag'; id: EntityId }

export const isSameFilter = (a: SpaceFilter, b: SpaceFilter): boolean => {
  if (a.kind !== b.kind) return false
  if ((a.kind === 'folder' || a.kind === 'tag') && (b.kind === 'folder' || b.kind === 'tag')) {
    return String(a.id) === String(b.id)
  }
  return true
}
