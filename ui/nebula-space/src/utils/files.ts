/** 文件柜的小工具：大小格式化、按扩展名 / MIME 分类 */
import type { FileKind } from '../types/files'

export const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${Math.round(bytes / 1024)} KB`
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(bytes < 10 * 1024 ** 2 ? 1 : 0)} MB`
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`
}

export const extOf = (name: string) => (name.includes('.') ? name.split('.').pop()!.toLowerCase() : '')

export const kindOf = (name: string, mime = ''): FileKind => {
  const ext = extOf(name)
  if (ext === 'pdf' || mime === 'application/pdf') return 'pdf'
  if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'heic'].includes(ext)) return 'image'
  if (['xls', 'xlsx', 'csv', 'numbers'].includes(ext)) return 'sheet'
  if (['doc', 'docx', 'txt', 'md', 'ppt', 'pptx', 'pages', 'rtf', 'html'].includes(ext) || mime.startsWith('text/')) return 'doc'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'zip'
  return 'other'
}

/** 缩略图配色与容量条颜色 */
export const KIND_META: Record<FileKind, { label: string; color: string }> = {
  pdf: { label: 'PDF', color: '#ef4444' },
  image: { label: '图片', color: '#8b5cf6' },
  doc: { label: '文档', color: '#3b82f6' },
  sheet: { label: '表格', color: '#16a34a' },
  zip: { label: '压缩包', color: '#d97706' },
  other: { label: '其他', color: '#9ca3af' },
}

/** 身份证、护照这类敏感文件：分享时额外提醒 */
export const isSensitive = (name: string) => /身份证|护照|户口|银行卡|社保|驾驶证|结婚证/.test(name)

/** 能在浏览器里直接预览的 */
export const canPreview = (name: string, mime: string) => {
  const kind = kindOf(name, mime)
  return kind === 'image' || kind === 'pdf' || mime.startsWith('text/') || ['txt', 'md', 'csv', 'json'].includes(extOf(name))
}
