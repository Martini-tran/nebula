/**
 * AI 辅助写作相关类型。
 *
 * 前端只描述「意图 + 上下文」，具体提示词与模型编排在后端（nebula-sdk-ai）完成。
 */

/** AI 能力类型。 */
export type AiAction =
  | 'continue'
  | 'polish'
  | 'expand'
  | 'condense'
  | 'rewrite'
  | 'brainstorm'
  | 'outline'
  | 'critique'

export interface AiActionMeta {
  value: AiAction
  label: string
  icon: string
  /** 面板中的一句话说明 */
  hint: string
  /** 是否需要用户先选中正文片段 */
  needSelection: boolean
}

export const AI_ACTIONS: AiActionMeta[] = [
  {
    value: 'continue',
    label: '续写',
    icon: 'lucide:pen-line',
    hint: '沿着当前情节与文风往下写一段',
    needSelection: false,
  },
  {
    value: 'polish',
    label: '润色',
    icon: 'lucide:sparkles',
    hint: '保持原意，优化措辞与节奏',
    needSelection: true,
  },
  {
    value: 'expand',
    label: '扩写',
    icon: 'lucide:maximize-2',
    hint: '补充细节描写，把选中段落写厚',
    needSelection: true,
  },
  {
    value: 'condense',
    label: '缩写',
    icon: 'lucide:minimize-2',
    hint: '压缩冗余，保留关键信息',
    needSelection: true,
  },
  {
    value: 'rewrite',
    label: '改写',
    icon: 'lucide:repeat',
    hint: '换一种表达或视角重写',
    needSelection: true,
  },
  {
    value: 'brainstorm',
    label: '找灵感',
    icon: 'lucide:lightbulb',
    hint: '围绕当前情境给出若干走向建议',
    needSelection: false,
  },
  {
    value: 'outline',
    label: '拆大纲',
    icon: 'lucide:list-tree',
    hint: '把梗概拆成可写的章节大纲',
    needSelection: false,
  },
  {
    value: 'critique',
    label: '挑毛病',
    icon: 'lucide:search-check',
    hint: '指出逻辑漏洞、人设崩塌与节奏问题',
    needSelection: false,
  },
]

/** 写作风格预设。 */
export interface StylePreset {
  value: string
  label: string
  description: string
}

export const STYLE_PRESETS: StylePreset[] = [
  { value: 'auto', label: '跟随原文', description: '自动分析已有正文的语感' },
  { value: 'plain', label: '简洁白描', description: '短句为主，少修饰' },
  { value: 'lyric', label: '细腻抒情', description: '重感官与心理描写' },
  { value: 'tense', label: '紧张快节奏', description: '高密度冲突，推进为先' },
  { value: 'classical', label: '古典雅致', description: '偏文言语汇与意象' },
]

/** AI 生成请求。 */
export interface AiGenerateRequest {
  action: AiAction
  workId: number
  chapterId?: number
  /** 选中的正文片段（润色/扩写等需要） */
  selection?: string
  /** 光标前的上文，用于续写 */
  precedingText?: string
  /** 用户附加要求 */
  instruction?: string
  style?: string
  /** 期望字数 */
  targetWords?: number
  /** 参与本次生成的设定条目 id */
  codexIds?: number[]
}

/** AI 生成结果。 */
export interface AiGenerateResult {
  id: string
  action: AiAction
  /** 生成的正文 */
  content: string
  /** brainstorm / outline 场景下的候选列表 */
  options?: string[] | null
  tokensUsed?: number | null
  createTime?: string | null
}

/** 写作台 AI 面板中的一条历史记录。 */
export interface AiHistoryItem extends AiGenerateResult {
  /** 触发时的输入摘要，便于回溯 */
  promptDigest?: string | null
  adopted?: boolean
}
