/**
 * 目录树工具：按卷分组、上下移动（含跨卷）、生成重排请求。
 *
 * 规则见设计文档 4.3：作品要么没有卷（一组、volume 为 null），要么每一章都属于某一卷。
 * 这里只做纯计算，不发请求；调用方拿新分组生成 TocSortRequest 提交，再用返回的目录整体替换。
 */
import type { ChapterListItem, EntityId, Toc, TocSortRequest, Volume } from '../types/work'

export interface TocGroup {
  /** null 表示作品没有分卷，全部章节平铺在这一组 */
  volume: Volume | null
  chapters: ChapterListItem[]
  wordCount: number
}

const bySort = <T extends { sortOrder: number }>(list: T[]) => [...list].sort((a, b) => a.sortOrder - b.sortOrder)

export const sameId = (a?: EntityId | null, b?: EntityId | null) => String(a ?? '') === String(b ?? '')

const sumWords = (chapters: ChapterListItem[]) => chapters.reduce((sum, c) => sum + (c.wordCount ?? 0), 0)

/** 目录 → 分组；无卷时返回唯一一组（volume 为 null）。 */
export const groupToc = (toc: Toc): TocGroup[] => {
  const chapters = bySort(toc.chapters)
  const volumes = bySort(toc.volumes)
  if (volumes.length === 0) {
    return [{ volume: null, chapters, wordCount: sumWords(chapters) }]
  }
  return volumes.map((volume) => {
    const own = chapters.filter((c) => sameId(c.volumeId, volume.id))
    return { volume, chapters: own, wordCount: sumWords(own) }
  })
}

/** 分组 → 重排请求：有卷传整棵树，无卷传章节顺序。 */
export const toSortRequest = (groups: TocGroup[]): TocSortRequest =>
  groups[0]?.volume
    ? { volumes: groups.map((g) => ({ id: g.volume!.id, chapterIds: g.chapters.map((c) => c.id) })) }
    : { chapterIds: groups[0]?.chapters.map((c) => c.id) ?? [] }

const locate = (groups: TocGroup[], chapterId: EntityId) => {
  for (let g = 0; g < groups.length; g += 1) {
    const c = groups[g]!.chapters.findIndex((item) => sameId(item.id, chapterId))
    if (c >= 0) return { g, c }
  }
  return null
}

/** 章节能否再往上/下移：卷首可移到上一卷末尾，卷尾可移到下一卷开头，只有全书首尾到头。 */
export const canMoveChapter = (groups: TocGroup[], chapterId: EntityId, offset: -1 | 1): boolean => {
  const at = locate(groups, chapterId)
  if (!at) return false
  if (offset < 0) return at.g > 0 || at.c > 0
  return at.g < groups.length - 1 || at.c < groups[at.g]!.chapters.length - 1
}

/**
 * 上移/下移一章，返回新分组；到头时返回 null。
 * 不做拖拽，跨卷靠边界：卷首再上移 → 上一卷末尾；卷尾再下移 → 下一卷开头。
 */
export const moveChapter = (groups: TocGroup[], chapterId: EntityId, offset: -1 | 1): TocGroup[] | null => {
  if (!canMoveChapter(groups, chapterId, offset)) return null
  const next = groups.map((g) => ({ ...g, chapters: [...g.chapters] }))
  const { g, c } = locate(next, chapterId)!
  const list = next[g]!.chapters
  const target = c + offset
  if (target >= 0 && target < list.length) {
    ;[list[c], list[target]] = [list[target]!, list[c]!]
  } else {
    const [moving] = list.splice(c, 1)
    if (offset < 0) next[g - 1]!.chapters.push(moving!)
    else next[g + 1]!.chapters.unshift(moving!)
  }
  return next
}

/** 卷上移/下移，返回新分组；到头时返回 null。 */
export const moveVolume = (groups: TocGroup[], volumeId: EntityId, offset: -1 | 1): TocGroup[] | null => {
  const index = groups.findIndex((g) => sameId(g.volume?.id, volumeId))
  const target = index + offset
  if (index < 0 || target < 0 || target >= groups.length) return null
  const next = [...groups]
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  return next
}

/** 删卷前的去向说明，写进确认文案，让作者知道章节不会丢。 */
export const describeVolumeRemoval = (groups: TocGroup[], volumeId: EntityId): string => {
  const index = groups.findIndex((g) => sameId(g.volume?.id, volumeId))
  const group = groups[index]
  if (!group) return ''
  const count = group.chapters.length
  if (count === 0) return '这是空卷，直接删除。'
  if (groups.length === 1) return `删除后，这 ${count} 章回到不分卷的目录。`
  const target = groups[index > 0 ? index - 1 : 1]!
  return `删除后，这 ${count} 章将并入《${target.volume!.title}》${index > 0 ? '末尾' : '开头'}。`
}
