import request, { del, get, post, put } from '../utils/request'
import { useMockFor } from './mock'
import { mockSpace } from './space.mock'
import type {
  AiAction,
  AiSuggestion,
  Bookmark,
  BookmarkPageQuery,
  BookmarkSaveRequest,
  BookmarkStatusValue,
  EntityId,
  ExportOptions,
  ExportScope,
  FolderDeleteStrategy,
  ExportTask,
  Folder,
  FolderPlan,
  FolderSaveRequest,
  FolderUpdateRequest,
  ImportTask,
  LinkCheckResult,
  PageResult,
  SpaceTag,
  TagSaveRequest,
  TagUpdateRequest,
  TaskPageQuery,
} from '../types/space'

/**
 * 个人空间接口。
 *
 * 网关按 /space/** 路由到空间服务；request.ts 的 baseURL=/api，
 * vite 代理会删掉 /api 前缀。服务目前只提供 /admin/** 端点，
 * 数据已按登录用户隔离，前台直接复用。
 *
 * 没有后端时（VITE_REAL_MODULES 不含 bookmarks）整组换成 space.mock.ts 的内存实现。
 */
const BASE = '/space/admin'

const real = {
  // ── 书签 ──
  fetchBookmarks: (query: BookmarkPageQuery = {}) =>
    get<PageResult<Bookmark>>(`${BASE}/bookmarks/page`, { params: query }),

  fetchBookmark: (id: EntityId) => get<Bookmark>(`${BASE}/bookmarks/${id}`),

  /** 同一个网址（规范化后相同）已收藏的那一条，任意状态；没有为 null */
  fetchDuplicate: (url: string, excludeId?: EntityId) =>
    get<Bookmark | null>(`${BASE}/bookmarks/duplicate`, { params: { url, excludeId } }),

  /** 打开了一次：访问次数 +1，不改更新时间 */
  recordBookmarkVisit: (id: EntityId) => post<void>(`${BASE}/bookmarks/${id}/visit`),

  /** 服务端抓网页补标题与描述（只补自动填的域名标题与空描述），返回补完的书签；打不开时原样返回 */
  fillBookmarkMeta: (id: EntityId) => post<Bookmark>(`${BASE}/bookmarks/${id}/meta`, undefined, { timeout: 30000 }),

  /**
   * 注意：网址与已有书签重复时，后端不报错，而是返回已有书签的 id 并用本次的 tagIds 覆盖它的标签。
   * 新建前先用 findDuplicate 查重，别让快速收藏把已有标签清掉。
   */
  createBookmark: (body: BookmarkSaveRequest) =>
    post<EntityId>(`${BASE}/bookmarks`, { source: 'manual', ...body }),

  updateBookmark: (id: EntityId, body: Partial<BookmarkSaveRequest>) =>
    put<void>(`${BASE}/bookmarks/${id}`, body),

  updateBookmarkStatus: (id: EntityId, status: BookmarkStatusValue) =>
    put<void>(`${BASE}/bookmarks/${id}/status`, { status }),

  /** 全量替换一条书签的标签 */
  bindBookmarkTags: (id: EntityId, tagIds: EntityId[]) =>
    put<void>(`${BASE}/bookmarks/${id}/tags`, { tagIds }),

  /** 批量移动，targetFolderId=0 表示移到未分类；返回实际移动条数 */
  moveBookmarks: (bookmarkIds: EntityId[], targetFolderId: EntityId) =>
    post<number>(`${BASE}/bookmarks/move`, { bookmarkIds, targetFolderId }),

  batchDeleteBookmarks: (bookmarkIds: EntityId[]) =>
    post<number>(`${BASE}/bookmarks/batch-delete`, { bookmarkIds }),

  deleteBookmark: (id: EntityId) => del<void>(`${BASE}/bookmarks/${id}`),

  /**
   * 检查一批书签（最多 20 条）能否打开，后端顺带改状态：打不开 → 失效，失效的又能打开 → 正常。
   * 服务端并发访问，每条最长 15 秒。
   */
  checkBookmarkLinks: (bookmarkIds: EntityId[]) =>
    post<LinkCheckResult[]>(`${BASE}/bookmarks/link-check`, { bookmarkIds }, { timeout: 120000 }),

  /** AI 整理建议（最多 30 条），只出建议不改数据；模型回复慢，超时放宽 */
  suggestBookmarks: (bookmarkIds: EntityId[], actions: AiAction[]) =>
    post<AiSuggestion[]>(`${BASE}/bookmarks/ai-suggest`, { bookmarkIds, actions }, { timeout: 150000 }),

  // ── 目录 ──
  fetchFolderTree: () => get<Folder[]>(`${BASE}/bookmark-folders/tree`),

  createFolder: (body: FolderSaveRequest) =>
    post<EntityId>(`${BASE}/bookmark-folders`, { parentId: 0, source: 'manual', ...body }),

  updateFolder: (id: EntityId, body: FolderUpdateRequest) => put<void>(`${BASE}/bookmark-folders/${id}`, body),

  /** 改父目录（0 = 顶层），可顺带给 sortOrder；后端会拦截移到自身或后代下 */
  moveFolder: (id: EntityId, targetParentId: EntityId, sortOrder?: number) =>
    put<void>(`${BASE}/bookmark-folders/${id}/move`, { targetParentId, sortOrder }),

  /** 目录下还有子目录或书签时后端返回 409 */
  /** 不带 strategy 只能删空目录；带了就在服务端一个事务里连同里面的一起处理，返回移走或删掉的书签数 */
  deleteFolder: (id: EntityId, strategy?: FolderDeleteStrategy) =>
    del<number>(`${BASE}/bookmark-folders/${id}`, { params: strategy ? { strategy } : undefined }),

  /** AI 重排目录方案（只出方案不改数据），hint 是用户的额外要求 */
  planFolders: (hint?: string) =>
    post<FolderPlan>(`${BASE}/bookmark-folders/ai-plan`, { hint: hint || undefined }, { timeout: 150000 }),

  // ── 标签 ──
  fetchTags: () => get<SpaceTag[]>(`${BASE}/space-tags`),

  createTag: (body: TagSaveRequest) => post<EntityId>(`${BASE}/space-tags`, body),

  updateTag: (id: EntityId, body: TagUpdateRequest) => put<void>(`${BASE}/space-tags/${id}`, body),

  deleteTag: (id: EntityId) => del<void>(`${BASE}/space-tags/${id}`),

  /** 把标签 from 合并进 to（服务端一个事务），返回新打上 to 的书签数 */
  mergeTag: (fromId: EntityId, toId: EntityId) => post<number>(`${BASE}/space-tags/${fromId}/merge`, undefined, { params: { into: toId } }),

  // ── 导入导出 ──

  /** 上传 Chrome 导出的书签 HTML：服务端建好任务（处理中，带总条数）马上返回，后台逐条入库，用 fetchImportTask 看进度 */
  importChromeBookmarks: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<ImportTask>(`${BASE}/bookmark-import-tasks/chrome`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // 20 MB 的文件上传可能超过默认 30 秒
      timeout: 120000,
    })
  },

  /** 导入任务详情（轮询进度用） */
  fetchImportTask: (id: EntityId) => get<ImportTask>(`${BASE}/bookmark-import-tasks/${id}`),

  /** 取导出文件；默认只包含「正常」状态的书签，includeArchived 时连同已归档的；目录范围不含子目录 */
  fetchExportBlob: async (scope: ExportScope, options: ExportOptions = {}) => {
    const path = options.format === 'json' ? 'json' : 'chrome'
    const blob = await request.get<unknown, Blob>(`${BASE}/bookmark-export-tasks/${path}`, {
      params: { ...scope, includeArchived: Boolean(options.includeArchived) },
      responseType: 'blob',
    })
    // 业务错误也可能以 HTTP 200 + JSON 返回，此时 blob 里是错误体
    if (blob.type.includes('json')) {
      const body = JSON.parse(await blob.text()) as { message?: string }
      throw new Error(body.message || '导出失败')
    }
    return blob
  },

  /** silent：403 只抛错不跳无权限页（记录属于附属信息，缺这项权限不该让整个空间不可用） */
  fetchImportTasks: (query: TaskPageQuery = {}, silent = false) =>
    get<PageResult<ImportTask>>(`${BASE}/bookmark-import-tasks/page`, {
      params: query,
      silentForbidden: silent,
    }),

  fetchExportTasks: (query: TaskPageQuery = {}, silent = false) =>
    get<PageResult<ExportTask>>(`${BASE}/bookmark-export-tasks/page`, { params: query, silentForbidden: silent }),

  cancelImportTask: (id: EntityId) => post<void>(`${BASE}/bookmark-import-tasks/${id}/cancel`),

  cancelExportTask: (id: EntityId) => post<void>(`${BASE}/bookmark-export-tasks/${id}/cancel`),
}

const api: typeof real = useMockFor('bookmarks') ? mockSpace : real

export const {
  fetchBookmarks,
  fetchBookmark,
  fillBookmarkMeta,
  createBookmark,
  updateBookmark,
  updateBookmarkStatus,
  bindBookmarkTags,
  moveBookmarks,
  batchDeleteBookmarks,
  deleteBookmark,
  checkBookmarkLinks,
  suggestBookmarks,
  fetchFolderTree,
  createFolder,
  updateFolder,
  moveFolder,
  deleteFolder,
  planFolders,
  fetchTags,
  createTag,
  updateTag,
  deleteTag,
  mergeTag,
  importChromeBookmarks,
  fetchImportTask,
  fetchImportTasks,
  fetchExportTasks,
  cancelImportTask,
  cancelExportTask,
} = api

export const renameFolder = (id: EntityId, name: string) => updateFolder(id, { name })

/**
 * 导出书签：Chrome 兼容的 HTML（默认），或带标签与备注的 JSON。
 * 接口要带 Authorization 头，不能用 <a href> 直接下载，只能取 Blob 后本地触发保存。
 */
export const exportBookmarks = async (scope: ExportScope = { scopeType: 'all' }, options: ExportOptions = {}) => {
  const blob = await api.fetchExportBlob(scope, options)
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `bookmarks_${stamp}.${options.format === 'json' ? 'json' : 'html'}`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

// ── 组合操作：后端没有直接对应的接口，用现有接口拼出来 ──

/**
 * 与后端 normalizeUrl 一致：协议与主机小写，去掉 #锚点，保留端口、路径与查询串。
 * 不用 URL 对象——它会给裸域名补上 "/"、吞掉默认端口，与后端结果对不上。
 */
export const normalizeUrl = (raw: string) => {
  const url = raw.trim()
  const match = /^([a-z][\w+.-]*):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?/i.exec(url)
  if (!match) return url
  const [, scheme, authority, path, query = ''] = match
  const at = authority!.lastIndexOf('@')
  const hostPort = at >= 0 ? authority!.slice(at + 1) : authority!
  return `${scheme!.toLowerCase()}://${hostPort.toLowerCase()}${path}${query}`
}

export const hostOf = (raw: string) => {
  const match = /^[a-z][\w+.-]*:\/\/(?:[^@/?#]*@)?([^:/?#]+)/i.exec(raw.trim())
  return match ? match[1]!.toLowerCase() : ''
}

/** 按规范化网址找已收藏的同一条书签（任意状态）；没有返回 undefined */
export const findDuplicate = async (url: string, excludeId?: EntityId): Promise<Bookmark | undefined> =>
  (await api.fetchDuplicate(url, excludeId)) ?? undefined

/** 点开书签时记一次访问；失败不打扰 */
export const noteBookmarkVisit = (id: EntityId) => {
  api.recordBookmarkVisit(id).catch(() => undefined)
}

/** 翻完所有页，取出满足条件的全部书签（批量整理用，单页上限 500） */
export const fetchAllBookmarks = async (query: Omit<BookmarkPageQuery, 'pageNum' | 'pageSize'>) => {
  const all: Bookmark[] = []
  for (let pageNum = 1; ; pageNum += 1) {
    const page = await fetchBookmarks({ ...query, pageNum, pageSize: 500 })
    const records = page?.records ?? []
    all.push(...records)
    if (!records.length || all.length >= Number(page?.total ?? 0)) return all
  }
}

/** 统计某目录下的书签数（任意状态） */
export const countBookmarks = async (query: Omit<BookmarkPageQuery, 'pageNum' | 'pageSize'>) => {
  const page = await fetchBookmarks({ ...query, pageNum: 1, pageSize: 1 })
  return Number(page?.total ?? 0)
}
