import { del, get, post, put } from '../utils/request'
import { delay, paginate, useMockFor } from './mock'
import { buildMockChapters, mockWorkDetails, mockWorks } from '../data/works'
import { countWords } from '../utils/format'
import { ApiError } from '../utils/request'
import type {
  ChapterDetail,
  ChapterListItem,
  ChapterSaveRequest,
  EntityId,
  PageResult,
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

/** 作品与章节分模块接入后端，各自可单独切 mock（见 .env 的 VITE_REAL_MODULES）。 */
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

// ------------------------------------------------------------------ 章节

/** mock 章节库：按作品懒初始化，行为对齐后端（修订号比对、首次写正文转草稿、排序值 1000 间隔） */
const mockChapterStore = new Map<string, ChapterDetail[]>()

const mockChaptersOf = (workId: EntityId): ChapterDetail[] => {
  const key = String(workId)
  let list = mockChapterStore.get(key)
  if (!list) {
    list = buildMockChapters(Number(workId)).map((c) => ({ ...c, wordCount: countWords(c.content) }))
    mockChapterStore.set(key, list)
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

const toListItem = ({ content: _content, revision: _revision, ...rest }: ChapterDetail): ChapterListItem => rest

/** 章节目录（不含正文），按排序值升序。 */
export const fetchChapters = async (workId: EntityId): Promise<ChapterListItem[]> => {
  if (CHAPTERS_MOCK) {
    return delay(mockChaptersOf(workId).map(toListItem))
  }

  return get<ChapterListItem[]>(`${BASE}/works/${workId}/chapters`)
}

/** 在末尾新建章节；不传标题时后端按「第 N 章」命名。 */
export const createChapter = async (workId: EntityId, title?: string): Promise<ChapterDetail> => {
  if (CHAPTERS_MOCK) {
    const list = mockChaptersOf(workId)
    const created: ChapterDetail = {
      id: Date.now(),
      workId,
      volumeId: null,
      title: title?.trim() || `第${list.length + 1}章`,
      sortOrder: (list.at(-1)?.sortOrder ?? 0) + 1000,
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

  return post<ChapterDetail>(`${BASE}/works/${workId}/chapters`, title ? { title } : {})
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

/** 重排章节：按新顺序提交本作品全部章节 ID。 */
export const sortChapters = async (workId: EntityId, ids: EntityId[]): Promise<ChapterListItem[]> => {
  if (CHAPTERS_MOCK) {
    const list = mockChaptersOf(workId)
    const byId = new Map(list.map((c) => [String(c.id), c]))
    const sorted = ids.map((id) => byId.get(String(id))).filter((c): c is ChapterDetail => !!c)
    if (sorted.length !== list.length) {
      throw new ApiError('章节列表已变化，请刷新后再排序', 409)
    }
    sorted.forEach((c, index) => (c.sortOrder = (index + 1) * 1000))
    list.splice(0, list.length, ...sorted)
    return delay(list.map(toListItem))
  }

  return put<ChapterListItem[]>(`${BASE}/works/${workId}/chapters/order`, { ids })
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
