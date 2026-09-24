import { del, get, post, put } from '../utils/request'
import { delay, paginate, useMockFor } from './mock'
import { buildMockChapters, buildMockVolumes, mockWorkDetails, mockWorks } from '../data/works'
import { countWords } from '../utils/format'
import { ApiError } from '../utils/request'
import type {
  ChapterDetail,
  ChapterListItem,
  ChapterSaveRequest,
  EntityId,
  PageResult,
  Toc,
  TocSortRequest,
  Volume,
  WorkCreateRequest,
  WorkDetail,
  WorkListItem,
  WorkPageQuery,
  WorkUpdateRequest,
} from '../types/work'

/**
 * 作品与章节接口。
 *
 * 网关按 /scribe/** 路由到写作服务；request.ts 的 baseURL=/api，
 * vite 代理会删掉 /api 前缀，因此这里写 /scribe/... 即可。
 */
const BASE = '/scribe'

/**
 * 作品与目录分模块接入后端，各自可单独切 mock（见 .env 的 VITE_REAL_MODULES）。
 * 卷与章节同属「目录」，结构操作互相牵连，共用 chapters 一个开关。
 */
const WORKS_MOCK = useMockFor('works')
const CHAPTERS_MOCK = useMockFor('chapters')

/** 分页查询我的作品。 */
export const fetchWorks = async (
  query: WorkPageQuery = {},
): Promise<PageResult<WorkListItem>> => {
  if (WORKS_MOCK) {
    const keyword = query.keyword?.trim()
    let records = [...mockWorks]

    if (keyword) {
      records = records.filter(
        (item) =>
          item.title.includes(keyword) ||
          (item.summary ?? '').includes(keyword) ||
          (item.tags ?? []).some((tag) => tag.includes(keyword)),
      )
    }
    if (query.status) {
      records = records.filter((item) => item.status === query.status)
    }

    switch (query.sort) {
      case 'created':
        records.sort((a, b) => (b.createTime ?? '').localeCompare(a.createTime ?? ''))
        break
      case 'words':
        records.sort((a, b) => b.wordCount - a.wordCount)
        break
      case 'title':
        records.sort((a, b) => a.title.localeCompare(b.title, 'zh-Hans-CN'))
        break
      default:
        records.sort((a, b) => (b.updateTime ?? '').localeCompare(a.updateTime ?? ''))
    }

    return delay(paginate(records, query.pageNum, query.pageSize))
  }

  return get<PageResult<WorkListItem>>(`${BASE}/works`, { params: query })
}

/** 作品详情（含卷章树）。 */
export const fetchWorkDetail = async (id: EntityId): Promise<WorkDetail> => {
  if (WORKS_MOCK) {
    const detail = mockWorkDetails[Number(id)]
    if (!detail) {
      throw new Error('作品不存在')
    }
    return delay(detail)
  }

  return get<WorkDetail>(`${BASE}/works/${id}`)
}

/** 新建作品。 */
export const createWork = async (body: WorkCreateRequest): Promise<WorkListItem> => {
  if (WORKS_MOCK) {
    const created: WorkListItem = {
      id: Date.now(),
      title: body.title,
      summary: body.summary ?? body.logline ?? '',
      genre: body.genre ?? '待定',
      tags: [],
      status: 'draft',
      wordCount: 0,
      chapterCount: 0,
      targetWordCount: body.targetWordCount ?? null,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }
    return delay(created)
  }

  return post<WorkListItem>(`${BASE}/works`, body)
}

/** 修改作品（整表单覆盖）。 */
export const updateWork = async (id: EntityId, body: WorkUpdateRequest): Promise<WorkDetail> => {
  if (WORKS_MOCK) {
    const detail = mockWorkDetails[Number(id)]
    if (!detail) {
      throw new Error('作品不存在')
    }
    return delay({ ...detail, ...body, updateTime: new Date().toISOString() })
  }

  return put<WorkDetail>(`${BASE}/works/${id}`, body)
}

/** 删除作品（移入回收站）。 */
export const deleteWork = async (id: EntityId): Promise<void> => {
  if (WORKS_MOCK) {
    await delay(null)
    return
  }

  await del(`${BASE}/works/${id}`)
}

// ------------------------------------------------------------------ 目录（卷 + 章节）

/**
 * mock 目录：按作品懒初始化，规则对齐后端（设计文档 4.3）——
 * 首卷收编散章、删卷并入相邻卷、修订号比对、首次写正文转草稿、排序值 1000 间隔。
 */
const mockChapterStore = new Map<string, ChapterDetail[]>()
const mockVolumeStore = new Map<string, Volume[]>()

const mockChaptersOf = (workId: EntityId): ChapterDetail[] => {
  const key = String(workId)
  let list = mockChapterStore.get(key)
  if (!list) {
    list = buildMockChapters(Number(workId)).map((c) => ({ ...c, wordCount: countWords(c.content) }))
    mockChapterStore.set(key, list)
  }
  return list
}

const mockVolumesOf = (workId: EntityId): Volume[] => {
  const key = String(workId)
  let list = mockVolumeStore.get(key)
  if (!list) {
    list = buildMockVolumes(Number(workId))
    mockVolumeStore.set(key, list)
  }
  return list
}

const findMockChapter = (workId: EntityId, chapterId: EntityId): ChapterDetail => {
  const chapter = mockChaptersOf(workId).find((c) => String(c.id) === String(chapterId))
  if (!chapter) {
    throw new ApiError('章节不存在', 404)
  }
  return chapter
}

const findMockVolume = (workId: EntityId, volumeId: EntityId): Volume => {
  const volume = mockVolumesOf(workId).find((v) => String(v.id) === String(volumeId))
  if (!volume) {
    throw new ApiError('卷不存在', 404)
  }
  return volume
}

const toListItem = ({ content: _content, revision: _revision, ...rest }: ChapterDetail): ChapterListItem => rest

const bySort = <T extends { sortOrder: number }>(list: T[]) => [...list].sort((a, b) => a.sortOrder - b.sortOrder)

const sameVolume = (a?: EntityId | null, b?: EntityId | null) => String(a ?? '') === String(b ?? '')

const mockToc = (workId: EntityId): Toc => ({
  volumes: bySort(mockVolumesOf(workId)).map((v) => ({ ...v })),
  chapters: bySort(mockChaptersOf(workId)).map(toListItem),
})

const sameMembers = (ids: EntityId[], expected: EntityId[]) => {
  const got = new Set(ids.map(String))
  return got.size === ids.length && got.size === expected.length && expected.every((id) => got.has(String(id)))
}

/** 章节目录（不含正文）。 */
export const fetchChapters = async (workId: EntityId): Promise<ChapterListItem[]> => {
  if (CHAPTERS_MOCK) {
    return delay(mockToc(workId).chapters)
  }

  return get<ChapterListItem[]>(`${BASE}/works/${workId}/chapters`)
}

/** 作品的卷，按排序值升序。 */
export const fetchVolumes = async (workId: EntityId): Promise<Volume[]> => {
  if (CHAPTERS_MOCK) {
    return delay(mockToc(workId).volumes)
  }

  return get<Volume[]>(`${BASE}/works/${workId}/volumes`)
}

/** 整个目录（卷 + 章节）。 */
export const fetchToc = async (workId: EntityId): Promise<Toc> => {
  const [volumes, chapters] = await Promise.all([fetchVolumes(workId), fetchChapters(workId)])
  return { volumes, chapters }
}

/**
 * 新建章节：有卷的作品不指定卷则放进最后一卷；不传标题时后端按全书章数命名「第 N 章」。
 */
export const createChapter = async (
  workId: EntityId,
  options: { title?: string; volumeId?: EntityId | null } = {},
): Promise<ChapterDetail> => {
  if (CHAPTERS_MOCK) {
    const list = mockChaptersOf(workId)
    const volumeId = options.volumeId ?? bySort(mockVolumesOf(workId)).at(-1)?.id ?? null
    const siblings = bySort(list.filter((c) => sameVolume(c.volumeId, volumeId)))
    const created: ChapterDetail = {
      id: Date.now(),
      workId,
      volumeId,
      title: options.title?.trim() || `第${list.length + 1}章`,
      sortOrder: (siblings.at(-1)?.sortOrder ?? 0) + 1000,
      status: 'outline',
      wordCount: 0,
      synopsis: null,
      content: '',
      revision: 0,
      updateTime: new Date().toISOString(),
    }
    list.push(created)
    return delay({ ...created })
  }

  const body: Record<string, unknown> = {}
  if (options.title) body.title = options.title
  if (options.volumeId != null) body.volumeId = options.volumeId
  return post<ChapterDetail>(`${BASE}/works/${workId}/chapters`, body)
}

/** 章节详情（含正文）。 */
export const fetchChapter = async (workId: EntityId, chapterId: EntityId): Promise<ChapterDetail> => {
  if (CHAPTERS_MOCK) {
    return delay({ ...findMockChapter(workId, chapterId) })
  }

  return get<ChapterDetail>(`${BASE}/works/${workId}/chapters/${chapterId}`)
}

/** 保存章节（局部更新）；修订号不一致时抛出 code=409 的 ApiError。 */
export const saveChapter = async (
  workId: EntityId,
  chapterId: EntityId,
  body: ChapterSaveRequest,
): Promise<ChapterDetail> => {
  if (CHAPTERS_MOCK) {
    await delay(null, 420)
    const chapter = findMockChapter(workId, chapterId)
    if (String(chapter.revision) !== String(body.revision)) {
      throw new ApiError('此章已在别处修改，请刷新后再保存', 409)
    }
    if (body.title !== undefined) chapter.title = body.title.trim()
    if (body.synopsis !== undefined) chapter.synopsis = body.synopsis.trim() || null
    if (body.status !== undefined) chapter.status = body.status
    if (body.content !== undefined) {
      chapter.content = body.content
      chapter.wordCount = countWords(body.content)
      if (body.status === undefined && chapter.status === 'outline' && chapter.wordCount > 0) {
        chapter.status = 'drafting'
      }
    }
    chapter.revision = Number(chapter.revision) + 1
    chapter.updateTime = new Date().toISOString()
    return { ...chapter }
  }

  return put<ChapterDetail>(`${BASE}/works/${workId}/chapters/${chapterId}`, body)
}

/** 删除章节（移入回收站）。 */
export const deleteChapter = async (workId: EntityId, chapterId: EntityId): Promise<void> => {
  if (CHAPTERS_MOCK) {
    const list = mockChaptersOf(workId)
    const index = list.findIndex((c) => String(c.id) === String(chapterId))
    if (index >= 0) list.splice(index, 1)
    await delay(null)
    return
  }

  await del(`${BASE}/works/${workId}/chapters/${chapterId}`)
}

/** 新建卷：追加到最后；这是第一卷时，现有章节全部划入。标题为空按「第 N 卷」命名。 */
export const createVolume = async (
  workId: EntityId,
  body: { title?: string; synopsis?: string } = {},
): Promise<Volume> => {
  if (CHAPTERS_MOCK) {
    const volumes = mockVolumesOf(workId)
    const created: Volume = {
      id: Date.now(),
      workId,
      title: body.title?.trim() || `第${volumes.length + 1}卷`,
      synopsis: body.synopsis?.trim() || null,
      sortOrder: (bySort(volumes).at(-1)?.sortOrder ?? 0) + 1000,
      updateTime: new Date().toISOString(),
    }
    if (volumes.length === 0) {
      mockChaptersOf(workId).forEach((c) => (c.volumeId = created.id))
    }
    volumes.push(created)
    return delay({ ...created })
  }

  return post<Volume>(`${BASE}/works/${workId}/volumes`, body)
}

/** 改卷名与梗概（整表单覆盖）。 */
export const updateVolume = async (
  workId: EntityId,
  volumeId: EntityId,
  body: { title: string; synopsis?: string | null },
): Promise<Volume> => {
  if (CHAPTERS_MOCK) {
    const volume = findMockVolume(workId, volumeId)
    if (!body.title.trim()) {
      throw new ApiError('卷名不能为空', 400)
    }
    volume.title = body.title.trim()
    volume.synopsis = body.synopsis?.trim() || null
    volume.updateTime = new Date().toISOString()
    return delay({ ...volume })
  }

  return put<Volume>(`${BASE}/works/${workId}/volumes/${volumeId}`, body)
}

/** 删除卷：只删卷不删章节，章节并入相邻卷（删唯一一卷则回到无卷）。返回新目录。 */
export const deleteVolume = async (workId: EntityId, volumeId: EntityId): Promise<Toc> => {
  if (CHAPTERS_MOCK) {
    const volumes = mockVolumesOf(workId)
    const ordered = bySort(volumes)
    const index = ordered.findIndex((v) => String(v.id) === String(volumeId))
    if (index < 0) {
      throw new ApiError('卷不存在', 404)
    }
    const chapters = mockChaptersOf(workId)
    const inVolume = (id: EntityId) => bySort(chapters.filter((c) => sameVolume(c.volumeId, id)))
    const moving = inVolume(volumeId)
    if (ordered.length === 1) {
      moving.forEach((c) => (c.volumeId = null))
    } else {
      const target = ordered[index > 0 ? index - 1 : 1]!
      const merged = index > 0 ? [...inVolume(target.id), ...moving] : [...moving, ...inVolume(target.id)]
      merged.forEach((c, i) => {
        c.volumeId = target.id
        c.sortOrder = (i + 1) * 1000
      })
    }
    volumes.splice(volumes.indexOf(ordered[index]!), 1)
    return delay(mockToc(workId))
  }

  return del<Toc>(`${BASE}/works/${workId}/volumes/${volumeId}`)
}

/** 整棵目录树重排：卷顺序、卷内顺序、跨卷移动一次提交。返回新目录。 */
export const saveToc = async (workId: EntityId, body: TocSortRequest): Promise<Toc> => {
  if (CHAPTERS_MOCK) {
    const volumes = mockVolumesOf(workId)
    const chapters = mockChaptersOf(workId)
    const changed = () => new ApiError('目录已变化，请刷新后再调整', 409)
    const byId = new Map(chapters.map((c) => [String(c.id), c]))
    if (volumes.length === 0) {
      const ids = body.chapterIds ?? []
      if (body.volumes?.length || !sameMembers(ids, chapters.map((c) => c.id))) throw changed()
      ids.forEach((id, i) => (byId.get(String(id))!.sortOrder = (i + 1) * 1000))
    } else {
      const nodes = body.volumes ?? []
      const allIds = nodes.flatMap((n) => n.chapterIds)
      if (
        body.chapterIds?.length ||
        !sameMembers(nodes.map((n) => n.id), volumes.map((v) => v.id)) ||
        !sameMembers(allIds, chapters.map((c) => c.id))
      ) {
        throw changed()
      }
      nodes.forEach((node, v) => {
        findMockVolume(workId, node.id).sortOrder = (v + 1) * 1000
        node.chapterIds.forEach((id, i) => {
          const c = byId.get(String(id))!
          c.volumeId = node.id
          c.sortOrder = (i + 1) * 1000
        })
      })
    }
    return delay(mockToc(workId))
  }

  return put<Toc>(`${BASE}/works/${workId}/toc`, body)
}
