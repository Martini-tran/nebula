/**
 * 稍后读与划线接口：后端 /space/me/reading、/space/me/highlights（nebula-service-space）。
 * VITE_REAL_MODULES 不含 reading 时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 规则：
 * - 加入时后端抓取网页、提取正文存档，原网址失效后阅读版仍可看；抓不到（要登录、不是文章页、内网地址）时只能打开原文，
 *   阅读页可以「再抓一次」。mock 抓不了正文
 * - 列表不带正文，看 saved；打开单篇才带。划线按段落和段内位置定位，所以存档后不再重抓
 * - 同一网址再加一次返回原来那条（归档的放回队列）
 * - 打开阅读版且有进度后状态从「未读」变「在读」；点「读完了」变「读完」
 * - 删除文章时一并删除它的划线；转出的随手记、任务保留
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Highlight, ReadingItem, ReadingSaveRequest, ReadStatus } from '../types/reading'

export interface ReadingQuery {
  status?: ReadStatus
  /** 默认不含归档 */
  archived?: boolean
}

const BASE = '/space/me'

const real = {
  fetchReadingItems: (query: ReadingQuery = {}) => get<ReadingItem[]>(`${BASE}/reading`, { params: query }),
  fetchReadingItem: (id: EntityId) => get<ReadingItem>(`${BASE}/reading/${id}`),
  /** 后端抓取正文（最多等 20 秒）；已在队列里的同一网址直接返回原来那条 */
  createReadingItem: (body: { url: string; title?: string; bookmarkId?: EntityId | null }) => post<ReadingItem>(`${BASE}/reading`, body),
  /** 之前没抓到正文的再抓一次；已经存档的不重抓 */
  refetchReadingItem: (id: EntityId) => post<ReadingItem>(`${BASE}/reading/${id}/fetch`),
  updateReadingItem: (id: EntityId, body: ReadingSaveRequest) => put<ReadingItem>(`${BASE}/reading/${id}`, body),
  deleteReadingItem: (id: EntityId) => del<void>(`${BASE}/reading/${id}`),
  fetchHighlights: (query: { itemId?: EntityId } = {}) => get<Highlight[]>(`${BASE}/highlights`, { params: query }),
  createHighlight: (body: Omit<Highlight, 'id' | 'createTime' | 'noteId' | 'taskId' | 'taskTitle'>) => post<Highlight>(`${BASE}/highlights`, body),
  updateHighlight: (id: EntityId, body: Partial<Omit<Highlight, 'id' | 'itemId'>>) => put<Highlight>(`${BASE}/highlights/${id}`, body),
  deleteHighlight: (id: EntityId) => del<void>(`${BASE}/highlights/${id}`),
}

// ── mock ──

type ItemRow = ReadingItem & { id: string }
type HlRow = Highlight & { id: string }

const ARTICLES: {
  id: string
  url: string
  title: string
  minutes: number
  content: string[] | null
}[] = [
  {
    id: 'r1',
    url: 'https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching',
    title: 'Prompt caching：缓存断点、TTL 与计费',
    minutes: 12,
    content: [
      '对于反复发送相同长前缀的应用——系统提示、工具定义、大段参考文档——每次请求都重新处理这部分内容既慢又贵。缓存让模型在后续请求中直接复用已处理的前缀。',
      '缓存按前缀匹配：断点之前的内容必须逐字节一致，任何变化都会使该断点及其之后的缓存失效。因此应把最稳定的内容放在最前面，把每次都会变的用户输入放在最后。',
      '缓存有存活时间。默认 TTL 较短，在期间内每次命中都会刷新计时；如果请求间隔可能超过默认时长，可以选择更长的 TTL，写入成本略高，但能避免频繁重建缓存。',
      '计费上，缓存写入比普通输入略贵，缓存读取则便宜得多——命中率越高越划算。可以通过响应里的用量字段确认每次请求读写了多少缓存。',
      '一个请求里可以设置多个断点。常见做法是：工具定义之后一个、系统提示之后一个、长对话历史的末尾一个。这样即使最后一段变化，前面几段依然能命中。',
      '排查命中率低的问题时，先看是不是前缀里混进了会变的东西：时间戳、随机 ID、按字典序不稳定的 JSON 键顺序，都是常见原因。',
      '最后，缓存是按组织隔离的，不同账号之间不会共享；同一组织内，只要前缀完全一致，不同用户的请求也能命中同一份缓存。',
    ],
  },
  {
    id: 'r2',
    url: 'https://sspai.com/post/desktop-workflow-2026',
    title: '我的桌面工作流：2026 版',
    minutes: 8,
    content: [
      '每年换一次工作流不是为了折腾，而是为了看清哪些习惯已经过时。今年最大的变化是：我删掉了一半的自动化。',
      '启动器仍然是核心。所有应用、文件、剪贴板历史、常用网址都从同一个入口出发，手不用离开键盘。',
      '剪贴板管理器是被低估的工具。它把「复制」从一次性的动作变成可以回溯的记录，写文档时尤其省事。',
      '自动化的价值不在于省下的那几秒，而在于不用再记住那件事。那些需要我记住「什么时候该触发」的自动化，最后都被我删了。',
      '窗口管理我回到了最朴素的方案：左右分屏加一个全屏快捷键，其余的布局从来没有真正用上过。',
    ],
  },
  {
    id: 'r3',
    url: 'https://developer.mozilla.org/en-US/docs/Web/API/View_Transitions_API/Using',
    title: 'Using the View Transitions API',
    minutes: 20,
    content: [
      '视图过渡 API 让你在单页应用中为 DOM 状态切换添加平滑过渡，无需手写动画状态机。',
      '基本用法是把更新 DOM 的函数传给 document.startViewTransition()。浏览器会先截取旧状态，执行更新，再截取新状态，然后在两者之间做交叉淡化。',
      '给元素设置 view-transition-name 后，它会被单独拎出来做过渡：位置和大小的变化会被自动补间，看起来像是同一个元素移动到了新位置。',
      '过渡的每一部分都暴露为伪元素，可以用普通的 CSS 动画改写默认效果，例如把淡入淡出换成滑动。',
      '需要注意的是，过渡期间页面不响应交互；过渡本身也应该尊重 prefers-reduced-motion。',
    ],
  },
  {
    id: 'r4',
    url: 'https://dataintensive.net/ch5-notes',
    title: '《数据密集型应用系统设计》第 5 章：复制',
    minutes: 25,
    content: [
      '复制意味着在通过网络连接的多台机器上保留相同数据的副本。目的有三：让数据在地理上离用户更近、在部分节点故障时系统继续工作、以及扩展可以处理读请求的机器数量。',
      '单主复制最常见：所有写入都发往主库，从库按同样的顺序应用变更日志。难点在于复制延迟——从库读到的可能是旧数据。',
      '读己之写一致性要求用户总能看到自己刚提交的修改；单调读保证用户不会看到时间倒流。',
      '无主复制中，读修复与反熵过程共同保证最终一致；前者只修复被读到的数据，后者在后台慢慢补齐。',
      '法定人数读写（w + r > n）并不能保证线性一致，边界情况下仍可能读到旧值。',
    ],
  },
  {
    id: 'r5',
    url: 'https://blog.vuejs.org/posts/vue-3-5',
    title: 'Vue 3.5 发布：响应式系统重构与内存优化',
    minutes: 9,
    content: [
      'Vue 3.5 对响应式系统做了一次重构，内存占用明显下降，大型数组上的操作也快了不少。',
      '响应式 props 解构现在默认稳定可用，解构出来的变量依然保持响应式，并且可以直接写默认值。',
      'useTemplateRef 让模板引用不再依赖同名变量，动态 ref 也更好写了。',
      '服务端渲染方面新增了惰性激活，可以按需激活异步组件，首屏交互更快。',
    ],
  },
  {
    id: 'r6',
    url: 'https://example.com/why-weekly-report',
    title: '周报到底是写给谁看的',
    minutes: 6,
    content: [
      '周报常被当成交差的形式，但它真正的读者至少有两个：你的上级，和三个月后的你自己。',
      '写给上级的部分应该短：做成了什么、下周做什么、需要谁帮忙。其余细节放链接即可。',
      '写给自己的部分可以长一点：哪些事拖了、为什么拖。回头看时，这些才是最有价值的记录。',
    ],
  },
  {
    id: 'r7',
    url: 'https://dev.mysql.com/doc/refman/8.0/en/fulltext-search-ngram.html',
    title: 'MySQL ngram Full-Text Parser',
    minutes: 15,
    content: null,
  },
  {
    id: 'r8',
    url: 'https://www.sqlite.org/appfileformat.html',
    title: 'SQLite As An Application File Format',
    minutes: 14,
    content: [
      '与其自己设计一种文件格式，不如直接用一个 SQLite 数据库文件作为应用的存档格式。',
      '这样可以免费得到原子写入、增量更新、并发读取，以及一个人人都能打开检查的格式。',
      '很多人担心性能，但对绝大多数桌面应用来说，SQLite 比手写的序列化代码更快、更可靠。',
    ],
  },
]

const domainOf = (url: string) => url.replace(/^https?:\/\//, '').split('/')[0]!.replace(/^www\./, '')

const seedItems = (): ItemRow[] => {
  const t = todayYmd()
  const at = (days: number, hm = '21:30') => `${addDays(t, -days)} ${hm}:00`
  const state: Record<string, Partial<ItemRow>> = {
    r1: { status: 'reading', progress: 0.42, position: 0.38, addTime: at(3, '10:12'), lastReadTime: at(1, '22:05') },
    r2: { status: 'reading', progress: 0.15, position: 0.12, addTime: at(18, '12:40'), lastReadTime: at(16) },
    r3: { status: 'reading', progress: 0.06, position: 0.05, addTime: at(40, '15:00'), lastReadTime: at(34) },
    r4: { status: 'done', progress: 1, position: 1, addTime: at(12), lastReadTime: at(7), doneTime: at(7), thought: '读修复和反熵的区别终于想清楚了，scribe 的同步可以参考。' },
    r5: { status: 'unread', addTime: at(2, '09:20') },
    r6: { status: 'unread', addTime: at(36, '08:30') },
    r7: { status: 'unread', addTime: at(0, '10:05') },
    r8: { status: 'done', progress: 1, position: 1, addTime: at(20), lastReadTime: at(5), doneTime: at(5), thought: '' },
  }
  return ARTICLES.map((a) => ({
    id: a.id,
    url: a.url,
    title: a.title,
    domain: domainOf(a.url),
    excerpt: a.content ? a.content.slice(0, 2).join('').slice(0, 90) : '',
    content: a.content,
    saved: a.content !== null,
    minutes: a.minutes,
    status: 'unread' as const,
    progress: 0,
    position: 0,
    thought: '',
    archived: false,
    bookmarkId: null,
    addTime: nowStamp(),
    lastReadTime: null,
    doneTime: null,
    ...state[a.id],
  }))
}

const seedHighlights = (): HlRow[] => {
  const t = todayYmd()
  const rows: HlRow[] = []
  const add = (itemId: string, para: number, text: string, color: HlRow['color'], daysAgo: number, extra: Partial<HlRow> = {}) => {
    const source = ARTICLES.find((a) => a.id === itemId)!.content![para]!
    const start = source.indexOf(text)
    rows.push({
      id: `h${rows.length + 1}`,
      itemId,
      para,
      start,
      end: start + text.length,
      text,
      color,
      note: '',
      noteId: null,
      taskId: null,
      taskTitle: null,
      createTime: `${addDays(t, -daysAgo)} 22:00:00`,
      ...extra,
    })
  }
  add('r1', 1, '缓存按前缀匹配：断点之前的内容必须逐字节一致，任何变化都会使该断点及其之后的缓存失效。', 'yellow', 1, {
    note: 'scribe 的系统提示里有时间戳，要挪到最后，不然每次都失效。',
  })
  add('r1', 2, '如果请求间隔可能超过默认时长，可以选择更长的 TTL', 'yellow', 1)
  add('r1', 3, '计费上，缓存写入比普通输入略贵，缓存读取则便宜得多', 'blue', 1, { taskTitle: '查 scribe 的缓存命中率' })
  add('r2', 3, '自动化的价值不在于省下的那几秒，而在于不用再记住那件事。', 'yellow', 16)
  add('r4', 3, '无主复制中，读修复与反熵过程共同保证最终一致；前者只修复被读到的数据，后者在后台慢慢补齐。', 'yellow', 7)
  add('r4', 4, '法定人数读写（w + r > n）并不能保证线性一致', 'blue', 7, { note: '找一下具体的反例。' })
  add('r4', 2, '读己之写一致性要求用户总能看到自己刚提交的修改', 'yellow', 8)
  add('r8', 1, '免费得到原子写入、增量更新、并发读取', 'yellow', 5)
  return rows
}

const items = createMockTable<ItemRow>('reading.v1', seedItems)
const highlights = createMockTable<HlRow>('highlights.v1', seedHighlights)

const byAdd = (a: ReadingItem, b: ReadingItem) => b.addTime.localeCompare(a.addTime)
/** 和后端一致：saved 按有没有正文算，列表和写操作的返回不带正文 */
const view = (i: ItemRow): ReadingItem => structuredClone({ ...i, saved: Boolean(i.content) })
const brief = (i: ItemRow): ReadingItem => ({ ...view(i), content: null })

const mock: typeof real = {
  fetchReadingItems: (query = {}) =>
    delay(
      items
        .all()
        .filter((i) => Boolean(i.archived) === Boolean(query.archived))
        .filter((i) => !query.status || i.status === query.status)
        .sort(byAdd)
        .map(brief),
      140,
    ),

  fetchReadingItem: async (id) => {
    const item = items.find(String(id))
    if (!item) throw new Error('文章不存在或已删除')
    return delay(view(item), 100)
  },

  createReadingItem: async (body) => {
    const url = body.url.trim()
    const exists = items.all().find((i) => i.url === url)
    if (exists) {
      if (exists.archived) items.update(exists.id, { archived: false })
      return delay(brief(items.find(exists.id)!), 120)
    }
    // mock 抓不了正文：只记下网址，阅读版要等后端
    const row = items.insert({
      id: nextId(),
      url,
      title: body.title?.trim() || domainOf(url),
      domain: domainOf(url),
      excerpt: '',
      content: null,
      saved: false,
      minutes: 0,
      status: 'unread',
      progress: 0,
      position: 0,
      thought: '',
      archived: false,
      bookmarkId: body.bookmarkId ?? null,
      addTime: nowStamp(),
      lastReadTime: null,
      doneTime: null,
    })
    return delay(brief(row), 160)
  },

  refetchReadingItem: async (id) => {
    const item = items.find(String(id))
    if (!item) throw new Error('文章不存在或已删除')
    // mock 抓不了正文，原样返回
    return delay(view(item), 300)
  },

  updateReadingItem: async (id, body) => {
    const row = items.update(String(id), body as Partial<ItemRow>)
    if (!row) throw new Error('文章不存在或已删除')
    return delay(brief(row), 100)
  },

  deleteReadingItem: async (id) => {
    items.remove(String(id))
    highlights
      .all()
      .filter((h) => String(h.itemId) === String(id))
      .forEach((h) => highlights.remove(h.id))
    return delay(undefined, 100)
  },

  fetchHighlights: (query = {}) =>
    delay(
      structuredClone(
        highlights
          .all()
          .filter((h) => query.itemId === undefined || String(h.itemId) === String(query.itemId))
          .sort((a, b) => b.createTime.localeCompare(a.createTime)),
      ),
      100,
    ),

  createHighlight: async (body) => {
    const row = highlights.insert({ ...body, id: nextId(), noteId: null, taskId: null, taskTitle: null, createTime: nowStamp() } as HlRow)
    return delay(structuredClone(row), 100)
  },

  updateHighlight: async (id, body) => {
    const row = highlights.update(String(id), body as Partial<HlRow>)
    if (!row) throw new Error('划线不存在或已删除')
    return delay(structuredClone(row), 80)
  },

  deleteHighlight: async (id) => {
    highlights.remove(String(id))
    return delay(undefined, 80)
  },
}

const api = useMockFor('reading') ? mock : real

export const {
  fetchReadingItems,
  fetchReadingItem,
  createReadingItem,
  refetchReadingItem,
  updateReadingItem,
  deleteReadingItem,
  fetchHighlights,
  createHighlight,
  updateHighlight,
  deleteHighlight,
} = api
