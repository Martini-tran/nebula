/**
 * 文件柜与分享链接接口：/space/me/files、/space/me/shares，分享页走 /space/public/shares/{code}（不需要登录）。
 * 文件内容存在公共文件组件里（sys_file + MinIO 私有桶），预览、下载都经服务端转发，不给浏览器对象存储地址。
 * mock（VITE_REAL_MODULES 不含 files 时）：文件信息存 localStorage，文件内容存 IndexedDB（api/blobStore.ts），只在本机有效。
 *
 * 规则（mock 与后端一致）：
 * - 删除先进「最近删除」，30 天后彻底删除；删文件夹连同里面的一起进，恢复时一起回来
 * - 分享链接指向 space 自己的下载页，这样才能撤销、计数；下载经服务端校验提取码、有效期、次数，文件夹打成 zip
 */
import request, { blobOrError, del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { deleteBlob, getBlob, putBlob } from './blobStore'
import { addDays, diffDays, nowStamp, todayYmd, ymdOf } from '../utils/date'
import { kindOf } from '../utils/files'
import type { EntityId } from '../types/space'
import type { FileKind, FileSource, FileUsage, Share, ShareCreateRequest, SharePublic, SpaceFile } from '../types/files'

export interface FileQuery {
  /** 浏览某个文件夹；null 为根目录 */
  folderId?: EntityId | null
  view?: 'folder' | 'recent' | 'trash' | 'kind' | 'source' | 'all'
  kind?: FileKind
  source?: Exclude<FileSource, 'upload'>
}

const BASE = '/space/me'

const real = {
  fetchFiles: (query: FileQuery = {}) => get<SpaceFile[]>(`${BASE}/files`, { params: query }),
  fetchFile: (id: EntityId) => get<SpaceFile>(`${BASE}/files/${id}`),
  fetchFileUsage: () => get<FileUsage>(`${BASE}/files/usage`),
  createFileFolder: (body: { name: string; folderId: EntityId | null }) => post<SpaceFile>(`${BASE}/files/folders`, body),
  uploadFile: (file: File, folderId: EntityId | null, onProgress?: (ratio: number) => void, signal?: AbortSignal) => {
    const form = new FormData()
    form.append('file', file)
    if (folderId !== null) form.append('folderId', String(folderId))
    return post<SpaceFile>(`${BASE}/files`, form, {
      signal,
      // 默认的 JSON 类型会让 axios 把表单转成 JSON；大文件传得慢，不设超时
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0,
      onUploadProgress: (e) => e.total && onProgress?.(e.loaded / e.total),
    })
  },
  updateFile: (id: EntityId, body: { name?: string; folderId?: EntityId | null }) => put<SpaceFile>(`${BASE}/files/${id}`, body),
  /** 移进最近删除 */
  trashFile: (id: EntityId) => del<void>(`${BASE}/files/${id}`),
  restoreFile: (id: EntityId) => put<void>(`${BASE}/files/${id}/restore`),
  /** 彻底删除 */
  purgeFile: (id: EntityId) => del<void>(`${BASE}/files/${id}`, { params: { purge: true } }),
  downloadFile: (id: EntityId) => blobOrError(request.get<unknown, Blob>(`${BASE}/files/${id}/content`, { responseType: 'blob', timeout: 0 })),
  fetchShares: () => get<Share[]>(`${BASE}/shares`),
  createShare: (body: ShareCreateRequest) => post<Share>(`${BASE}/shares`, body),
  revokeShare: (id: EntityId) => put<void>(`${BASE}/shares/${id}/revoke`),
  fetchSharePublic: (code: string) => get<SharePublic>(`/space/public/shares/${code}`, { silentForbidden: true }),
  /** 文件夹下载下来是 zip */
  downloadShared: (code: string, password: string | null) =>
    blobOrError(request.post<unknown, Blob>(`/space/public/shares/${code}/download`, { password }, { responseType: 'blob', timeout: 0 })),
}

// ── mock ──

type FileRow = SpaceFile & { id: string }
type ShareRow = Share & { id: string }

const MB = 1024 * 1024
const MOCK_QUOTA = 10 * 1024 * MB
/** mock 把内容存在浏览器里，单个文件限制小一点 */
const MOCK_MAX = 50 * MB
const MIME: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  png: 'image/png',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  zip: 'application/zip',
  html: 'text/html',
  md: 'text/markdown',
}

const seedFiles = (): FileRow[] => {
  const t = todayYmd()
  const rows: FileRow[] = []
  const add = (name: string, folderId: string | null, size: number, daysAgo: number, extra: Partial<FileRow> = {}) => {
    const stamp = `${addDays(t, -daysAgo)} 10:${String(rows.length % 60).padStart(2, '0')}:00`
    const ext = name.split('.').pop()!
    rows.push({
      id: extra.id ?? `fl${rows.length + 1}`,
      folderId,
      isFolder: false,
      name,
      size,
      mime: MIME[ext] ?? 'application/octet-stream',
      source: 'upload',
      deleteTime: null,
      createTime: stamp,
      updateTime: stamp,
      ...extra,
    })
  }
  const folder = (id: string, name: string, daysAgo: number) => add(name, null, 0, daysAgo, { id, isFolder: true, mime: '' })
  folder('d1', '证件与合同', 200)
  folder('d2', '发票 2026', 6)
  folder('d3', '体检报告', 106)
  add('劳动合同_2025.pdf', 'd1', 2.3 * MB, 200)
  add('身份证正面.jpg', 'd1', 1.1 * MB, 208)
  add('身份证反面.jpg', 'd1', 1.0 * MB, 208)
  add('房屋租赁合同_2026.pdf', 'd1', 4.8 * MB, 3)
  add('学位证书扫描.zip', 'd1', 12 * MB, 400)
  for (let m = 1; m <= 9; m += 1) add(`2026-0${m} 电费发票.pdf`, 'd2', 0.2 * MB + m * 9000, 270 - m * 30)
  add('2026-09 宽带发票.pdf', 'd2', 0.3 * MB, 6)
  add('2026 年度体检报告.pdf', 'd3', 8.6 * MB, 106)
  add('血常规.jpg', 'd3', 2.2 * MB, 106)
  add('个人简历_2026.docx', null, 86 * 1024, 27)
  add('家庭年度开支.xlsx', null, 42 * 1024, 25)
  add('护照首页.jpg', null, 1.4 * MB, 90)
  add('装修报价单.pdf', null, 3.2 * MB, 12)
  // 其他模块的附件
  add('猫粮包装.jpg', null, 0.9 * MB, 1, { source: 'notes' })
  add('白板照片_周会.jpg', null, 2.6 * MB, 0, { source: 'notes' })
  add('手写草图.png', null, 1.2 * MB, 8, { source: 'notes' })
  add('书签导入方案.md', null, 12 * 1024, 0, { source: 'meetings' })
  add('迁移窗口排期.xlsx', null, 18 * 1024, 3, { source: 'meetings' })
  add('prompt-caching.html', null, 240 * 1024, 3, { source: 'reading' })
  add('ddia-ch5-notes.html', null, 310 * 1024, 12, { source: 'reading' })
  add('bookmarks_20260921_101500.html', null, 420 * 1024, 6, { source: 'bookmarks' })
  add('bookmarks_20260801_220301.html', null, 380 * 1024, 57, { source: 'bookmarks' })
  // 最近删除
  add('旧版简历_2024.docx', null, 74 * 1024, 300, { deleteTime: `${addDays(t, -4)} 21:00:00` })
  add('截图 2026-09-10.png', null, 640 * 1024, 17, { deleteTime: `${addDays(t, -12)} 09:30:00` })
  return rows
}

const seedShares = (): ShareRow[] => {
  const t = todayYmd()
  return [
    { id: 's1', code: 'Xb3mP9', fileId: 'fl4', fileName: '劳动合同_2025.pdf', isFolder: false, password: 'k7Q2', expireAt: `${addDays(t, 6)} 23:59:59`, maxDownloads: 3, downloads: 1, revoked: false, createTime: `${addDays(t, -1)} 15:00:00` },
    { id: 's2', code: 'Tn82Lq', fileId: 'd3', fileName: '体检报告', isFolder: true, password: 'p4Wd', expireAt: `${addDays(t, 3)} 23:59:59`, maxDownloads: null, downloads: 0, revoked: false, createTime: `${addDays(t, -4)} 11:00:00` },
    { id: 's3', code: 'Qa7Rz1', fileId: 'fl5', fileName: '身份证正面.jpg', isFolder: false, password: 'h2Ne', expireAt: `${addDays(t, -2)} 23:59:59`, maxDownloads: 1, downloads: 1, revoked: false, createTime: `${addDays(t, -9)} 10:00:00` },
  ]
}

const files = createMockTable<FileRow>('files.v1', seedFiles)
const shares = createMockTable<ShareRow>('shares.v2', seedShares)

const alive = (f: FileRow) => !f.deleteTime
const childrenOf = (id: string): FileRow[] => files.all().filter((f) => String(f.folderId) === id)
const descendants = (id: string): FileRow[] => childrenOf(id).flatMap((c) => [c, ...(c.isFolder ? descendants(c.id) : [])])

/** 最近删除超过 30 天的彻底删掉 */
const sweep = () => {
  const today = todayYmd()
  files
    .all()
    .filter((f) => f.deleteTime && diffDays(ymdOf(f.deleteTime), today) > 30)
    .forEach((f) => {
      files.remove(f.id)
      deleteBlob(f.id).catch(() => undefined)
    })
}

const usage = (): FileUsage => {
  const byKind: Record<FileKind, number> = { pdf: 0, image: 0, doc: 0, sheet: 0, zip: 0, other: 0 }
  files
    .all()
    .filter((f) => !f.isFolder)
    .forEach((f) => (byKind[kindOf(f.name, f.mime)] += f.size))
  return { used: Object.values(byKind).reduce((a, b) => a + b, 0), total: MOCK_QUOTA, maxFileSize: MOCK_MAX, byKind }
}

const randomCode = (n: number) => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  return Array.from({ length: n }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

/** 示例文件没有真实内容：给一个说明文字，下载下来不至于是空的 */
const placeholder = (f: FileRow) =>
  new Blob([`这是示例文件「${f.name}」，没有实际内容。\n自己上传的文件会保存在这个浏览器里，可以预览和下载。\n`], { type: 'text/plain;charset=utf-8' })

const shareState = (s: ShareRow): string | null => {
  if (s.revoked) return '分享已被取消'
  if (s.expireAt && s.expireAt < nowStamp()) return '分享已过期'
  if (s.maxDownloads !== null && s.downloads >= s.maxDownloads) return '已达下载次数上限'
  return null
}

const mock: typeof real = {
  fetchFiles: (query = {}) => {
    sweep()
    const view = query.view ?? 'folder'
    let rows = files.all()
    if (view === 'trash') {
      // 最近删除只列「被删的那一层」，文件夹里的跟着文件夹走
      rows = rows.filter((f) => f.deleteTime && !files.all().some((p) => p.id === String(f.folderId) && p.deleteTime === f.deleteTime))
      return delay(structuredClone(rows.sort((a, b) => String(b.deleteTime).localeCompare(String(a.deleteTime)))), 120)
    }
    rows = rows.filter(alive)
    if (view === 'folder') rows = rows.filter((f) => f.source === 'upload' && String(f.folderId ?? '') === String(query.folderId ?? ''))
    if (view === 'recent') rows = rows.filter((f) => !f.isFolder).sort((a, b) => b.updateTime.localeCompare(a.updateTime)).slice(0, 30)
    if (view === 'kind') rows = rows.filter((f) => !f.isFolder && kindOf(f.name, f.mime) === query.kind)
    if (view === 'source') rows = rows.filter((f) => f.source === query.source)
    if (view !== 'recent') rows = [...rows].sort((a, b) => Number(b.isFolder) - Number(a.isFolder) || b.updateTime.localeCompare(a.updateTime))
    return delay(structuredClone(rows), 140)
  },

  fetchFile: async (id) => {
    const f = files.find(String(id))
    if (!f) throw new Error('文件不存在或已删除')
    return delay(structuredClone(f), 80)
  },

  fetchFileUsage: () => delay(usage(), 80),

  createFileFolder: async ({ name, folderId }) => {
    const clean = name.trim()
    if (!clean) throw new Error('文件夹名不能为空')
    if (files.all().some((f) => alive(f) && f.isFolder && f.name === clean && String(f.folderId ?? '') === String(folderId ?? ''))) {
      throw new Error('这里已经有同名文件夹')
    }
    const now = nowStamp()
    return delay(
      structuredClone(
        files.insert({ id: nextId(), folderId: folderId ?? null, isFolder: true, name: clean, size: 0, mime: '', source: 'upload', deleteTime: null, createTime: now, updateTime: now }),
      ),
      120,
    )
  },

  uploadFile: async (file, folderId, onProgress, signal) => {
    if (file.size > MOCK_MAX) throw new Error(`「${file.name}」超过 50 MB（演示模式把文件存在浏览器里，限制小一些）`)
    const u = usage()
    if (u.used + file.size > u.total) throw new Error('空间不够了，先清理一些文件')
    // 模拟上传进度：大文件慢一点
    const steps = 12
    const total = Math.min(2400, 500 + file.size / 20_000)
    for (let i = 1; i <= steps; i += 1) {
      if (signal?.aborted) throw new DOMException('已取消', 'AbortError')
      await new Promise((r) => setTimeout(r, total / steps))
      onProgress?.(i / steps)
    }
    // 同名文件自动改名：合同.pdf → 合同 (1).pdf
    let name = file.name
    const taken = (n: string) => files.all().some((f) => alive(f) && !f.isFolder && f.name === n && String(f.folderId ?? '') === String(folderId ?? ''))
    for (let i = 1; taken(name); i += 1) name = file.name.replace(/(\.[^.]*)?$/, ` (${i})$1`)
    const now = nowStamp()
    const row = files.insert({ id: nextId(), folderId: folderId ?? null, isFolder: false, name, size: file.size, mime: file.type || 'application/octet-stream', source: 'upload', deleteTime: null, createTime: now, updateTime: now })
    await putBlob(row.id, file)
    return structuredClone(row)
  },

  updateFile: async (id, body) => {
    const f = files.find(String(id))
    if (!f) throw new Error('文件不存在或已删除')
    if (body.folderId !== undefined && body.folderId !== null && f.isFolder) {
      if (String(body.folderId) === f.id || descendants(f.id).some((d) => d.id === String(body.folderId))) throw new Error('不能移到它自己里面')
    }
    return delay(structuredClone(files.update(f.id, { ...body, name: body.name?.trim() || f.name, updateTime: nowStamp() })!), 100)
  },

  trashFile: async (id) => {
    const f = files.find(String(id))
    if (!f) throw new Error('文件不存在或已删除')
    const stamp = nowStamp()
    ;[f, ...(f.isFolder ? descendants(f.id) : [])].filter(alive).forEach((x) => files.update(x.id, { deleteTime: stamp }))
    return delay(undefined, 100)
  },

  restoreFile: async (id) => {
    const f = files.find(String(id))
    if (!f?.deleteTime) throw new Error('文件不在最近删除里')
    const stamp = f.deleteTime
    // 原来的文件夹也被删了（或不在了）就放回根目录
    const parent = f.folderId ? files.find(String(f.folderId)) : null
    files.update(f.id, { deleteTime: null, folderId: parent && alive(parent) ? f.folderId : null })
    if (f.isFolder) descendants(f.id).filter((d) => d.deleteTime === stamp).forEach((d) => files.update(d.id, { deleteTime: null }))
    return delay(undefined, 100)
  },

  purgeFile: async (id) => {
    const f = files.find(String(id))
    if (!f) return delay(undefined, 60)
    ;[f, ...(f.isFolder ? descendants(f.id) : [])].forEach((x) => {
      files.remove(x.id)
      deleteBlob(x.id).catch(() => undefined)
    })
    return delay(undefined, 100)
  },

  downloadFile: async (id) => {
    const f = files.find(String(id))
    if (!f) throw new Error('文件不存在或已删除')
    return (await getBlob(f.id)) ?? placeholder(f)
  },

  fetchShares: () =>
    delay(structuredClone([...shares.all()].sort((a, b) => Number(Boolean(shareState(a))) - Number(Boolean(shareState(b))) || b.createTime.localeCompare(a.createTime))), 100),

  createShare: async (body) => {
    const f = files.find(String(body.fileId))
    if (!f) throw new Error('文件不存在或已删除')
    const row = shares.insert({
      id: nextId(),
      code: randomCode(6),
      fileId: f.id,
      fileName: f.name,
      isFolder: f.isFolder,
      password: body.withPassword ? randomCode(4) : null,
      expireAt: body.days === null ? null : `${addDays(todayYmd(), body.days)} 23:59:59`,
      maxDownloads: body.maxDownloads,
      downloads: 0,
      revoked: false,
      createTime: nowStamp(),
    })
    return delay(structuredClone(row), 140)
  },

  revokeShare: async (id) => {
    shares.update(String(id), { revoked: true })
    return delay(undefined, 80)
  },

  fetchSharePublic: async (code) => {
    const s = shares.all().find((x) => x.code === code)
    if (!s) throw new Error('链接不存在，可能输错了')
    const f = files.find(String(s.fileId))
    const size = f ? (f.isFolder ? descendants(f.id).reduce((sum, d) => sum + d.size, 0) : f.size) : 0
    let owner = '一位朋友'
    try {
      const user = JSON.parse(localStorage.getItem('nebula-space:user') ?? '{}')
      owner = user.nickname || user.username || owner
    } catch {
      // 取不到分享人就用默认称呼
    }
    return delay(
      { code, fileName: s.fileName, isFolder: s.isFolder, size, needPassword: Boolean(s.password), expireAt: s.expireAt, unavailable: shareState(s) ?? (f && alive(f) ? null : '文件已被删除'), owner },
      160,
    )
  },

  downloadShared: async (code, password) => {
    const s = shares.all().find((x) => x.code === code)
    if (!s) throw new Error('链接不存在')
    const state = shareState(s)
    if (state) throw new Error(state)
    if (s.password && s.password.toLowerCase() !== (password ?? '').trim().toLowerCase()) throw new Error('提取码不对')
    const f = files.find(String(s.fileId))
    if (!f || !alive(f)) throw new Error('文件已被删除')
    if (f.isFolder) throw new Error('文件夹要由服务端打包成压缩包下载，演示模式下做不到')
    shares.update(s.id, { downloads: s.downloads + 1 })
    return (await getBlob(f.id)) ?? placeholder(f)
  },
}

const api = useMockFor('files') ? mock : real

export const {
  fetchFiles,
  fetchFile,
  fetchFileUsage,
  createFileFolder,
  uploadFile,
  updateFile,
  trashFile,
  restoreFile,
  purgeFile,
  downloadFile,
  fetchShares,
  createShare,
  revokeShare,
  fetchSharePublic,
  downloadShared,
} = api

/** 把 Blob 存成本地文件 */
export const saveBlob = (blob: Blob, name: string) => {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

/** 分享链接的完整地址 */
export const shareUrl = (code: string) => `${location.origin}/s/${code}`
