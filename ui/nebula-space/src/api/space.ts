import request, { del, get, post, put } from '../utils/request'
import type {
  Bookmark,
  BookmarkPageQuery,
  BookmarkSaveRequest,
  BookmarkStatusValue,
  EntityId,
  ExportScope,
  Folder,
  FolderSaveRequest,
  ImportTask,
  PageResult,
  SpaceTag,
  TagSaveRequest,
} from '../types/space'

/**
 * 个人空间接口。
 *
 * 网关按 /space/** 路由到空间服务；request.ts 的 baseURL=/api，
 * vite 代理会删掉 /api 前缀。服务目前只提供 /admin/** 端点，
 * 数据已按登录用户隔离，前台直接复用。
 */
const BASE = '/space/admin'

// ── 书签 ──

export const fetchBookmarks = (query: BookmarkPageQuery = {}) =>
  get<PageResult<Bookmark>>(`${BASE}/bookmarks/page`, { params: query })

export const createBookmark = (body: BookmarkSaveRequest) =>
  post<EntityId>(`${BASE}/bookmarks`, { source: 'manual', ...body })

export const updateBookmark = (id: EntityId, body: Partial<BookmarkSaveRequest>) =>
  put<void>(`${BASE}/bookmarks/${id}`, body)

export const updateBookmarkStatus = (id: EntityId, status: BookmarkStatusValue) =>
  put<void>(`${BASE}/bookmarks/${id}/status`, { status })

export const deleteBookmark = (id: EntityId) => del<void>(`${BASE}/bookmarks/${id}`)

// ── 目录 ──

export const fetchFolderTree = () => get<Folder[]>(`${BASE}/bookmark-folders/tree`)

export const createFolder = (body: FolderSaveRequest) =>
  post<EntityId>(`${BASE}/bookmark-folders`, { parentId: 0, source: 'manual', ...body })

export const renameFolder = (id: EntityId, name: string) =>
  put<void>(`${BASE}/bookmark-folders/${id}`, { name })

export const deleteFolder = (id: EntityId) => del<void>(`${BASE}/bookmark-folders/${id}`)

// ── 标签 ──

export const fetchTags = () => get<SpaceTag[]>(`${BASE}/space-tags`)

export const createTag = (body: TagSaveRequest) => post<EntityId>(`${BASE}/space-tags`, body)

export const deleteTag = (id: EntityId) => del<void>(`${BASE}/space-tags/${id}`)

// ── 导入导出 ──

/** 上传 Chrome 导出的书签 HTML，服务端同步解析入库 */
export const importChromeBookmarks = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return post<ImportTask>(`${BASE}/bookmark-import-tasks/chrome`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 导出为 Chrome 兼容的书签 HTML。
 * 接口要带 Authorization 头，不能用 <a href> 直接下载，只能取 Blob 后本地触发保存。
 */
export const exportChromeBookmarks = async (scope: ExportScope = { scopeType: 'all' }) => {
  const blob = await request.get<unknown, Blob>(`${BASE}/bookmark-export-tasks/chrome`, {
    params: scope,
    responseType: 'blob',
  })
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `bookmarks_${stamp}.html`
  link.click()
  URL.revokeObjectURL(link.href)
}
