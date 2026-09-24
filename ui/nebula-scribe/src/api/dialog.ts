import { del, get, post, put } from '../utils/request'
import { delay, useMockFor } from './mock'
import type { EntityId, PageResult } from '../types/work'
import type {
  DialogCreateRequest,
  DialogDetail,
  DialogListItem,
  DialogMessage,
  DialogStreamHandlers,
  DialogStreamResult,
  DialogUpdateRequest,
} from '../types/dialog'

/**
 * 创作对话接口（《写作Agent设计.md》7.6.5）。
 *
 * 批次 C1 只有前端：会话与消息存在下面的内存 mock 里，刷新即重置。
 * 会话 REST 已按契约写好真实分支；发消息的流式接口到 C2（非流式）/ C3（SSE）再接。
 */
const BASE = '/scribe/dialogs'
const DIALOG_MOCK = useMockFor('dialogs')

// ─────────────────────────────── mock 数据 ───────────────────────────────

interface MockDialog extends DialogDetail {
  messages: DialogMessage[]
}

const HOUR = 3600_000
const DAY = 24 * HOUR
const iso = (offset: number) => new Date(Date.now() - offset).toISOString()
let mockSeq = 1000

const msg = (role: DialogMessage['role'], content: string, offset: number): DialogMessage => ({
  id: ++mockSeq,
  role,
  content,
  status: 'DONE',
  createTime: iso(offset),
})

const mockDialogs: MockDialog[] = [
  {
    id: 1,
    title: '开头怎么写才抓人',
    workId: null,
    workTitle: null,
    updateTime: iso(2 * HOUR),
    messageCount: 2,
    messages: [
      msg('user', '开头怎么写才抓人？', 2 * HOUR),
      msg(
        'assistant',
        '开篇最要紧的是**一个具体的异常**，而不是世界观介绍。\n\n1. 第一段就让主角遇到一件不对劲的小事。\n2. 用动作代替解释。\n3. 章末留一个只有主角注意到的细节。',
        2 * HOUR,
      ),
    ],
  },
  {
    id: 2,
    title: '卡文了怎么办',
    workId: null,
    workTitle: null,
    updateTime: iso(3 * DAY),
    messageCount: 2,
    messages: [
      msg('user', '卡文了，写不下去怎么办', 3 * DAY),
      msg('assistant', '卡文通常不是没想法，而是**下一场戏的目标不清楚**。试着回答：这一章结束时，主角比开头多知道了什么？', 3 * DAY),
    ],
  },
]

const MOCK_REPLIES: Array<{ keywords: string[]; reply: string }> = [
  {
    keywords: ['开头', '第一章', '开篇'],
    reply: `开篇最要紧的是「一个具体的异常」，而不是世界观介绍。

可以试试这三步：

1. **第一段就让主角遇到一件不对劲的小事**——茶还温着，屋里却没人。
2. **用动作代替解释**：让读者先看到他怎么做，再慢慢知道他为什么这么做。
3. **章末留一个钩子**：一个只有主角注意到、读者却已经看见的细节。`,
  },
  {
    keywords: ['人物', '角色', '主角'],
    reply: `立人物，与其写「性格：冷淡」，不如给他一个**可被看见的习惯**和一个**不肯说出口的欲望**。

- 习惯：说话总用数字回应，「三刻」「七步」。
- 欲望：想证明自己没有被世界遗忘。

两者一旦在同一场戏里冲突，人物就立住了。`,
  },
  {
    keywords: ['伏笔', '悬念', '反转'],
    reply: `好的伏笔要满足两个条件：**出现时不显眼，回收时不意外**。

一个实用做法：埋设时把它放进一段读者正在关注别的事的场景里；回收前两三章，再让它以另一种形式轻轻出现一次。`,
  },
  {
    keywords: ['卡文', '写不下去', '没灵感'],
    reply: `卡文通常不是没想法，而是**下一场戏的目标不清楚**。

试着只回答一个问题：这一章结束时，主角比开头多知道了什么、或者失去了什么？答案一旦明确，场景自然会长出来。`,
  },
]

const mockReply = (question: string, workTitle?: string | null) => {
  if (workTitle) {
    return `结合《${workTitle}》目前的设定，我的看法是：

1. **先确认动机**——人物的每个选择都要能回溯到设定里的「核心欲望」。
2. **再看代价**——这件事会让他失去什么？代价越具体，戏越好看。
3. **最后留口子**——给后面的章节留一个可回收的细节。

（演示回复：接入后端后会真正读取这本书的设定与章节。）`
  }
  return (
    MOCK_REPLIES.find((item) => item.keywords.some((word) => question.includes(word)))?.reply ??
    `关于「${question}」，可以从三个角度拆：

- **情节**：它推动了什么？
- **人物**：它暴露了谁的什么？
- **节奏**：它放在这里，读者会不会累？

（演示回复：对话后端尚未接入。）`
  )
}

const findMock = (id: EntityId) => {
  const dialog = mockDialogs.find((item) => String(item.id) === String(id))
  if (!dialog) {
    throw new Error('对话不存在')
  }
  return dialog
}

const toListItem = ({ messages: _messages, ...rest }: MockDialog): DialogDetail => ({ ...rest })

const sleep = (ms: number, signal?: AbortSignal) =>
  new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      clearTimeout(timer)
      resolve()
    })
  })

/**
 * 模拟 SSE：先等一会儿（对应模型首 token 延迟），再每 30ms 推 3 个字。
 * 问题里带「失败」二字时模拟服务端 error 事件，便于验证失败态。
 */
const mockStream = async (
  dialog: MockDialog,
  question: string,
  handlers: DialogStreamHandlers,
  signal?: AbortSignal,
): Promise<DialogStreamResult> => {
  const reply: DialogMessage = { id: ++mockSeq, role: 'assistant', content: '', status: 'DONE', createTime: iso(0) }
  dialog.messages.push(reply)
  dialog.messageCount = dialog.messages.length
  dialog.updateTime = iso(0)
  handlers.onMeta?.({ assistantMessageId: reply.id })

  await sleep(650, signal)
  if (signal?.aborted) {
    reply.status = 'STOPPED'
    return { status: 'STOPPED' }
  }
  if (question.includes('失败')) {
    reply.status = 'FAILED'
    return { status: 'FAILED', error: '模型服务暂时不可用（演示）' }
  }

  const full = mockReply(question, dialog.workTitle)
  for (let i = 0; i < full.length; i += 3) {
    if (signal?.aborted) {
      reply.status = 'STOPPED'
      return { status: 'STOPPED' }
    }
    const piece = full.slice(i, i + 3)
    reply.content += piece
    handlers.onDelta(piece)
    await sleep(30, signal)
  }
  return { status: 'DONE' }
}

// ─────────────────────────────── 接口 ───────────────────────────────

/** 我的会话列表（按最后活动时间倒序）。 */
export const fetchDialogs = async (keyword?: string): Promise<DialogListItem[]> => {
  if (DIALOG_MOCK) {
    const kw = keyword?.trim()
    const rows = mockDialogs
      .filter((item) => !kw || item.title.includes(kw))
      .sort((a, b) => b.updateTime.localeCompare(a.updateTime))
      .map(toListItem)
    return delay(rows, 200)
  }

  const page = await get<PageResult<DialogListItem>>(BASE, {
    params: { keyword: keyword?.trim() || undefined, pageNum: 1, pageSize: 100 },
  })
  return page.records
}

/** 新建会话（发出首条消息时才调用）。 */
export const createDialog = async (body: DialogCreateRequest): Promise<DialogDetail> => {
  if (DIALOG_MOCK) {
    const dialog: MockDialog = {
      id: ++mockSeq,
      title: body.title?.trim() || '新对话',
      workId: body.workId ?? null,
      workTitle: null,
      updateTime: iso(0),
      messageCount: 0,
      messages: [],
    }
    mockDialogs.push(dialog)
    return delay(toListItem(dialog), 150)
  }

  return post<DialogDetail>(BASE, body)
}

/** mock 模式下由页面告知作品标题（真实接口由后端联表返回）。 */
export const setMockWorkTitle = (id: EntityId, workTitle: string | null) => {
  if (DIALOG_MOCK) {
    findMock(id).workTitle = workTitle
  }
}

/** 重命名会话。 */
export const updateDialog = async (id: EntityId, body: DialogUpdateRequest): Promise<DialogDetail> => {
  if (DIALOG_MOCK) {
    const dialog = findMock(id)
    dialog.title = body.title.trim()
    return delay(toListItem(dialog), 150)
  }

  return put<DialogDetail>(`${BASE}/${id}`, body)
}

/** 删除会话（硬删，含全部消息）。 */
export const deleteDialog = async (id: EntityId): Promise<void> => {
  if (DIALOG_MOCK) {
    const index = mockDialogs.findIndex((item) => String(item.id) === String(id))
    if (index >= 0) mockDialogs.splice(index, 1)
    await delay(null, 150)
    return
  }

  await del(`${BASE}/${id}`)
}

/** 会话详情（含消息数，用于判断关联作品是否已锁定）。 */
export const fetchDialog = async (id: EntityId): Promise<DialogDetail> => {
  if (DIALOG_MOCK) {
    return delay(toListItem(findMock(id)), 150)
  }

  return get<DialogDetail>(`${BASE}/${id}`)
}

/** 历史消息，按时间正序返回（向上翻页加载更早消息留到 C2）。 */
export const fetchMessages = async (id: EntityId): Promise<DialogMessage[]> => {
  if (DIALOG_MOCK) {
    return delay(findMock(id).messages.map((item) => ({ ...item })), 200)
  }

  const page = await get<PageResult<DialogMessage>>(`${BASE}/${id}/messages`, { params: { size: 30 } })
  // 接口按 id 倒序分页，界面按时间正序展示
  return [...page.records].reverse()
}

/** 发一轮：流式返回 AI 回复；signal 中止即「停止生成」。 */
export const sendMessage = async (
  id: EntityId,
  content: string,
  handlers: DialogStreamHandlers,
  signal?: AbortSignal,
): Promise<DialogStreamResult> => {
  if (DIALOG_MOCK) {
    const dialog = findMock(id)
    dialog.messages.push({ id: ++mockSeq, role: 'user', content, status: 'DONE', createTime: iso(0) })
    return mockStream(dialog, content, handlers, signal)
  }

  throw new Error('对话后端尚未接入（批次 C2/C3）')
}

/** 重新生成最后一条 AI 回复。 */
export const regenerateReply = async (
  id: EntityId,
  handlers: DialogStreamHandlers,
  signal?: AbortSignal,
): Promise<DialogStreamResult> => {
  if (DIALOG_MOCK) {
    const dialog = findMock(id)
    if (dialog.messages.at(-1)?.role === 'assistant') {
      dialog.messages.pop()
    }
    const question = [...dialog.messages].reverse().find((item) => item.role === 'user')?.content ?? ''
    // 失败演示只触发一次，重试/重新生成时让它成功
    return mockStream(dialog, question.replace('失败', ''), handlers, signal)
  }

  throw new Error('对话后端尚未接入（批次 C2/C3）')
}
