/**
 * 文件柜与分享链接。后端尚未实现（见 docs/ui设计/个人空间/space-files.html「后端待补」）：
 * space_file（对象存 MinIO，按 space/{userId}/ 前缀隔离）、space_share。字段按设计稿拟定。
 */
import type { EntityId } from './space'

/** 文件从哪来：自己上传的，或者其他模块的附件 */
export type FileSource = 'upload' | 'notes' | 'meetings' | 'reading' | 'bookmarks'

export const FILE_SOURCES: Record<Exclude<FileSource, 'upload'>, { label: string; icon: string }> = {
  notes: { label: '随手记图片', icon: 'lucide:image' },
  meetings: { label: '会议附件', icon: 'lucide:paperclip' },
  reading: { label: '稍后读存档', icon: 'lucide:book-open' },
  bookmarks: { label: '书签导出', icon: 'lucide:bookmark' },
}

export interface SpaceFile {
  id: EntityId
  /** 所在文件夹；null = 根目录「我的文件」 */
  folderId: EntityId | null
  isFolder: boolean
  name: string
  /** 字节；文件夹为 0 */
  size: number
  mime: string
  source: FileSource
  /** 移进最近删除的时间；30 天后彻底删除 */
  deleteTime: string | null
  createTime: string
  updateTime: string
}

/** 文件分成几类：侧栏的类型筛选与容量条都按这个分 */
export type FileKind = 'pdf' | 'image' | 'doc' | 'sheet' | 'zip' | 'other'

export interface FileUsage {
  used: number
  total: number
  byKind: Record<FileKind, number>
}

export interface Share {
  id: EntityId
  /** 短码：链接 /s/{code} */
  code: string
  fileId: EntityId
  fileName: string
  isFolder: boolean
  /** 提取码；null = 不需要（后端只存哈希，这里 mock 存明文） */
  password: string | null
  /** 过期时间；null = 永久 */
  expireAt: string | null
  maxDownloads: number | null
  downloads: number
  revoked: boolean
  createTime: string
}

export interface ShareCreateRequest {
  fileId: EntityId
  /** 有效天数；null = 永久 */
  days: number | null
  withPassword: boolean
  maxDownloads: number | null
}

/** 分享页（公开，无需登录）看到的信息 */
export interface SharePublic {
  code: string
  fileName: string
  isFolder: boolean
  size: number
  needPassword: boolean
  expireAt: string | null
  /** 不可用的原因：过期、撤销、次数用完 */
  unavailable: string | null
  owner: string
}
