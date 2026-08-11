import { del, get, post, put } from '../utils/request'
import { USE_MOCK, delay, paginate } from './mock'
import { mockChapterContent, mockWorkDetails, mockWorks } from '../data/works'
import type {
  ChapterDetail,
  ChapterSaveRequest,
  PageResult,
  WorkCreateRequest,
  WorkDetail,
  WorkListItem,
  WorkPageQuery,
} from '../types/work'

/**
 * 作品与章节接口。
 *
 * 网关按 /scribe/** 路由到写作服务；request.ts 的 baseURL=/api，
 * vite 代理会删掉 /api 前缀，因此这里写 /scribe/... 即可。
 */
const BASE = '/scribe'

/** 分页查询我的作品。 */
export const fetchWorks = async (
  query: WorkPageQuery = {},
): Promise<PageResult<WorkListItem>> => {
  if (USE_MOCK) {
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
export const fetchWorkDetail = async (id: number | string): Promise<WorkDetail> => {
  if (USE_MOCK) {
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
  if (USE_MOCK) {
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

/** 删除作品。 */
export const deleteWork = async (id: number | string): Promise<void> => {
  if (USE_MOCK) {
    await delay(null)
    return
  }

  await del(`${BASE}/works/${id}`)
}

/** 章节正文。 */
export const fetchChapter = async (
  workId: number | string,
  chapterId: number | string,
): Promise<ChapterDetail> => {
  if (USE_MOCK) {
    const chapter = mockChapterContent(Number(workId), Number(chapterId))
    if (!chapter) {
      throw new Error('章节不存在')
    }
    return delay(chapter)
  }

  return get<ChapterDetail>(`${BASE}/works/${workId}/chapters/${chapterId}`)
}

/** 保存章节。 */
export const saveChapter = async (
  workId: number | string,
  chapterId: number | string,
  body: ChapterSaveRequest,
): Promise<void> => {
  if (USE_MOCK) {
    await delay(null, 420)
    return
  }

  await put(`${BASE}/works/${workId}/chapters/${chapterId}`, body)
}

/** 新增章节。 */
export const createChapter = async (
  workId: number | string,
  volumeId: number | string,
  title: string,
): Promise<ChapterDetail> => {
  if (USE_MOCK) {
    return delay({
      id: Date.now(),
      volumeId: Number(volumeId),
      workId: Number(workId),
      title,
      sortOrder: 99,
      status: 'outline',
      wordCount: 0,
      synopsis: '',
      content: '',
      updateTime: new Date().toISOString(),
    })
  }

  return post<ChapterDetail>(`${BASE}/works/${workId}/volumes/${volumeId}/chapters`, {
    title,
  })
}
