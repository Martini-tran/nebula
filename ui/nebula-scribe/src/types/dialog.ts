/**
 * 创作对话类型，对应《写作Agent设计.md》7.6.5 / 7.6.6。
 * 对话可选绑定作品：workId 为空表示通用写作问答。
 */
import type { EntityId } from './work'

/** 消息状态：只有 DONE 的轮次会作为后续上下文。 */
export type DialogMessageStatus = 'DONE' | 'STOPPED' | 'FAILED'

/** 会话列表项。 */
export interface DialogListItem {
  id: EntityId
  title: string
  workId?: EntityId | null
  /** 关联作品的标题，列表里显示在会话标题下方 */
  workTitle?: string | null
  /** 最后活动时间，列表按它分组与排序 */
  updateTime: string
}

/** 会话详情。 */
export interface DialogDetail extends DialogListItem {
  /** 已有消息数；大于 0 时关联作品锁定 */
  messageCount: number
}

/** 一条消息。 */
export interface DialogMessage {
  id: EntityId
  role: 'user' | 'assistant'
  content: string
  status: DialogMessageStatus
  createTime: string
}

/** 新建会话：发出首条消息时才调用（惰性创建）。 */
export interface DialogCreateRequest {
  workId?: EntityId | null
  title?: string
}

/** 修改会话：workId 只有在会话还没有消息时才允许改。 */
export interface DialogUpdateRequest {
  title: string
  workId?: EntityId | null
}

/** SSE 第一帧：服务端落库后的真实消息 id。 */
export interface DialogStreamMeta {
  userMessageId?: EntityId
  assistantMessageId: EntityId
}

/** 流式回调：对应 SSE 的 meta / delta 事件；done / error 通过 Promise 结果表达。 */
export interface DialogStreamHandlers {
  onMeta?: (meta: DialogStreamMeta) => void
  onDelta: (text: string) => void
}

/** 一轮生成的最终结果。 */
export interface DialogStreamResult {
  status: DialogMessageStatus
  /** FAILED 时的原因 */
  error?: string
}
