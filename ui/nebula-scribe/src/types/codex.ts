/**
 * 设定库（Codex）类型：人物、地点、势力、道具、世界观规则。
 *
 * 设定条目挂在作品下，写作台侧栏可检索并一键引用到 AI 上下文。
 */

export type CodexKind = 'character' | 'location' | 'faction' | 'item' | 'lore'

export const CODEX_KIND_LABEL: Record<CodexKind, string> = {
  character: '人物',
  location: '地点',
  faction: '势力',
  item: '道具',
  lore: '设定',
}

export const CODEX_KIND_ICON: Record<CodexKind, string> = {
  character: 'lucide:user-round',
  location: 'lucide:map-pin',
  faction: 'lucide:flag',
  item: 'lucide:swords',
  lore: 'lucide:scroll-text',
}

/** 设定条目。 */
export interface CodexEntry {
  id: number
  workId: number
  kind: CodexKind
  name: string
  /** 别名，检索与正文高亮用 */
  aliases?: string[] | null
  /** 一句话概述，列表展示 */
  summary?: string | null
  /** 详细设定，Markdown */
  detail?: string | null
  avatarUrl?: string | null
  tags?: string[] | null
  /** 是否默认注入 AI 上下文 */
  pinned?: boolean
  updateTime?: string | null
}

/** 设定保存请求体。 */
export interface CodexSaveRequest {
  kind: CodexKind
  name: string
  aliases?: string[]
  summary?: string
  detail?: string
  tags?: string[]
  pinned?: boolean
}
