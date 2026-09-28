/**
 * 书签模块的内存 mock：没有后端时让书签页、整理页可以完整走通。
 * 行为照着 nebula-service-space 写（重复网址、非空目录 409、目录不能移到自身后代下等），
 * 刷新页面数据复原。
 */
import { ApiError } from '../utils/request'
import { delay, nextId, paginate } from './mock'
import type {
  AiAction,
  AiSuggestion,
  Bookmark,
  BookmarkPageQuery,
  BookmarkSaveRequest,
  BookmarkStatusValue,
  EntityId,
  ExportScope,
  ExportTask,
  Folder,
  FolderPlan,
  FolderPlanOp,
  FolderSaveRequest,
  FolderUpdateRequest,
  ImportTask,
  LinkCheckResult,
  SpaceTag,
  TagSaveRequest,
  TagUpdateRequest,
  TaskPageQuery,
} from '../types/space'

type FolderRow = Omit<Folder, 'children'>
type BookmarkRow = Omit<Bookmark, 'tags'> & { tagIds: string[] }

const same = (a: EntityId | null | undefined, b: EntityId | null | undefined) => String(a ?? '') === String(b ?? '')
const daysAgo = (days: number, hour = 10) => {
  const date = new Date(Date.now() - days * 86_400_000)
  date.setHours(hour, 12, 0, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

// ── 种子数据 ──

const tags: SpaceTag[] = [
  { id: 't1', name: '文档', color: '#4f46e5', sortOrder: 0, remark: '官方文档、API 参考' },
  { id: 't2', name: '常用', color: '#0d9488', sortOrder: 1 },
  { id: 't3', name: '开源', color: '#2563eb', sortOrder: 2, remark: 'GitHub 仓库' },
  { id: 't4', name: '教程', color: '#d97706', sortOrder: 3 },
  { id: 't5', name: '待读', color: '#db2777', sortOrder: 4, remark: '读完就摘掉' },
  { id: 't6', name: '灵感', color: '#7c3aed', sortOrder: 5 },
]

const folders: FolderRow[] = [
  { id: 'f1', parentId: 0, name: '前端', level: 1, sortOrder: 0 },
  { id: 'f11', parentId: 'f1', name: 'Vue 生态', level: 2, sortOrder: 0 },
  { id: 'f12', parentId: 'f1', name: 'CSS 与设计', level: 2, sortOrder: 1 },
  { id: 'f13', parentId: 'f1', name: '构建工具', level: 2, sortOrder: 2 },
  { id: 'f2', parentId: 0, name: '后端与架构', level: 1, sortOrder: 1 },
  { id: 'f21', parentId: 'f2', name: '微服务', level: 2, sortOrder: 0 },
  { id: 'f211', parentId: 'f21', name: '网关', level: 3, sortOrder: 0 },
  { id: 'f22', parentId: 'f2', name: '数据库', level: 2, sortOrder: 1 },
  { id: 'f3', parentId: 0, name: 'AI 与模型', level: 1, sortOrder: 2 },
  { id: 'f4', parentId: 0, name: '稍后阅读', level: 1, sortOrder: 3 },
]

const seed: [title: string, url: string, folderId: EntityId, tagIds: string[], desc: string, extra?: Partial<BookmarkRow>][] = [
  ['Vue.js 官方文档', 'https://cn.vuejs.org/guide/introduction.html', 'f11', ['t1', 't2'], '渐进式 JavaScript 框架。组合式 API、响应式原理与单文件组件的权威参考。', { visitCount: 56, source: 'chrome' }],
  ['Vite · 下一代前端工具链', 'https://vite.dev/guide/', 'f13', ['t1'], '基于原生 ESM 的开发服务器与 Rollup 打包，配置、插件 API 与迁移指南。', { visitCount: 31, source: 'chrome', remark: 'server.proxy 的 rewrite 写法以这里为准；nebula 各前端的 /api 代理都照这个配。' }],
  ['Pinia', 'https://pinia.vuejs.org/zh/', 'f11', ['t1'], '符合直觉的 Vue.js 状态管理库。'],
  ['Vue Router', 'https://router.vuejs.org/zh/', 'f11', ['t1'], 'Vue.js 的官方路由。'],
  ['CSS color-mix() - MDN', 'https://developer.mozilla.org/zh-CN/docs/Web/CSS/color_value/color-mix', 'f12', ['t1', 't4'], '在指定色彩空间中按比例混合两种颜色，用于派生半透明与悬停色。', { visitCount: 12 }],
  ['Tailwind CSS v4.0', 'https://tailwindcss.com/blog/tailwindcss-v4', 'f12', ['t1'], 'CSS-first 配置、@theme 指令与 Vite 插件。', { visitCount: 31 }],
  ['Figma Community · Dashboard UI Kit', 'https://www.figma.com/community/file/dashboard-ui-kit', 'f12', ['t6'], '后台布局参考：侧栏密度、表格行高、空状态插画的尺度。'],
  ['Refactoring UI', 'https://www.refactoringui.com/', 'f12', ['t6', 't5'], '开发者视角的界面设计手册。'],
  ['Rollup 配置参考', 'https://rollupjs.org/configuration-options/', 'f13', ['t1'], ''],
  ['spring-cloud/spring-cloud-gateway', 'https://github.com/spring-cloud/spring-cloud-gateway', 'f211', ['t3'], '基于 WebFlux 的 API 网关，路由断言、过滤器与限流配置示例。'],
  ['Spring Cloud Gateway 参考文档', 'https://docs.spring.io/spring-cloud-gateway/reference/', 'f211', ['t1'], '路由、断言工厂、过滤器、限流与熔断配置。'],
  ['Sa-Token 多端登录与权限实践', 'https://juejin.cn/post/sa-token-practice', 'f21', ['t4'], '网关统一鉴权、微服务内 @SaCheckPermission 与 StpInterface 的配合方式。'],
  ['alibaba/nacos · 动态服务发现与配置管理', 'https://github.com/alibaba/nacos', 'f21', ['t3'], ''],
  ['baomidou/mybatis-plus', 'https://github.com/baomidou/mybatis-plus', 'f22', [], ''],
  ['MySQL 8.0 参考手册', 'https://dev.mysql.com/doc/refman/8.0/en/', 'f22', ['t1'], ''],
  ['Redis 设计与实现', 'https://redisbook.com/', 'f22', ['t5'], '从数据结构到集群。'],
  ['Prompt caching · Claude API', 'https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching', 'f3', ['t1', 't5'], '缓存断点、TTL 与计费方式，长上下文场景下的成本优化。'],
  ['Hugging Face', 'https://huggingface.co/', 'f3', ['t2'], ''],
  ['Hacker News', 'https://news.ycombinator.com/', 0, ['t2'], '', { visitCount: 208 }],
  ['我的桌面工作流：2026 版', 'https://sspai.com/post/desk-workflow-2026', 'f4', ['t6'], ''],
  ['MinIO Object Storage for Kubernetes', 'https://min.io/', 0, [], '', { source: 'chrome' }],
  ['dromara/Sa-Token · 轻量级权限认证框架', 'https://github.com/dromara/Sa-Token', 0, [], '', { source: 'chrome' }],
  ['Webpack 4 配置详解', 'https://blog.example-dev.cn/webpack4', 'f13', [], '', { status: 2 }],
  ['Google Code · closure-library', 'https://code.google.com/p/closure-library', 'f1', [], '', { status: 2 }],
  ['jQuery API', 'https://api.jquery.com/', 'f1', ['t1'], '', { status: 1 }],
  ['Bootstrap 3 文档', 'https://getbootstrap.com/docs/3.4/', 'f12', [], '', { status: 1 }],
]

const bookmarks: BookmarkRow[] = seed.map(([title, url, folderId, tagIds, description, extra], index) => ({
  id: `b${index + 1}`,
  folderId,
  title,
  url,
  domain: new URL(url).hostname.toLowerCase(),
  description,
  faviconUrl: null,
  source: 'manual',
  status: 0,
  visitCount: 0,
  lastVisitTime: extra?.visitCount ? daysAgo(index % 5) : null,
  sortOrder: 0,
  remark: null,
  createTime: daysAgo(index * 3 + 1, 9 + (index % 10)),
  updateTime: daysAgo(index),
  tagIds,
  ...extra,
}))

const importTasks: ImportTask[] = [
  { id: 'i3', status: 2, source: 'chrome', totalCount: 342, successCount: 318, duplicateCount: 21, failCount: 3, createTime: daysAgo(6, 21) },
  { id: 'i2', status: 3, source: 'chrome', errorMsg: '文件不是 Netscape Bookmark 格式（缺少 <!DOCTYPE NETSCAPE-Bookmark-file-1>）', createTime: daysAgo(6, 20) },
  { id: 'i1', status: 2, source: 'chrome', totalCount: 96, successCount: 94, duplicateCount: 2, failCount: 0, createTime: daysAgo(147, 10) },
]

const exportTasks: ExportTask[] = [
  { id: 'e2', status: 2, exportType: 'chrome', scopeType: 'folder', scopeId: 'f1', totalCount: 4, createTime: daysAgo(3, 18) },
  { id: 'e1', status: 2, exportType: 'chrome', scopeType: 'all', totalCount: 412, createTime: daysAgo(40, 9) },
]

// ── 工具 ──

const fail = (message: string, code: number): never => {
  throw new ApiError(message, code)
}

const requireBookmark = (id: EntityId) => bookmarks.find((b) => same(b.id, id)) ?? fail('书签不存在', 404)
const requireFolder = (id: EntityId) => folders.find((f) => same(f.id, id)) ?? fail('目录不存在', 404)
const requireTag = (id: EntityId) => tags.find((t) => same(t.id, id)) ?? fail('标签不存在', 404)

const toVO = (row: BookmarkRow): Bookmark => {
  const { tagIds, ...rest } = row
  return { ...rest, tags: tagIds.map((id) => tags.find((t) => same(t.id, id))).filter((t): t is SpaceTag => Boolean(t)) }
}

const normalize = (raw: string) => {
  const match = /^([a-z][\w+.-]*):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?/i.exec(raw.trim())
  return match ? `${match[1]!.toLowerCase()}://${match[2]!.toLowerCase()}${match[3]}${match[4] ?? ''}` : raw.trim()
}

const now = () => daysAgo(0, new Date().getHours())

const descendants = (id: EntityId): FolderRow[] => {
  const children = folders.filter((f) => same(f.parentId, id))
  return children.flatMap((child) => [child, ...descendants(child.id)])
}

const buildTree = (parentId: EntityId): Folder[] =>
  folders
    .filter((f) => same(f.parentId, parentId))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || String(a.id).localeCompare(String(b.id)))
    .map((f) => ({ ...f, children: buildTree(f.id) }))

const relevel = (id: EntityId, level: number) => {
  const folder = requireFolder(id)
  folder.level = level
  folders.filter((f) => same(f.parentId, id)).forEach((child) => relevel(child.id, level + 1))
}

const checkUniqueName = (parentId: EntityId, name: string, selfId?: EntityId) => {
  if (folders.some((f) => same(f.parentId, parentId) && f.name === name && !same(f.id, selfId))) {
    fail('同一父目录下已存在同名目录', 409)
  }
}

const escapeHtml = (text: string) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const folderPathOf = (id: EntityId): string => {
  const names: string[] = []
  for (let cur = folders.find((f) => same(f.id, id)); cur; cur = folders.find((f) => same(f.id, cur!.parentId))) {
    names.unshift(cur.name)
  }
  return names.join(' / ')
}

/** AI 归目录的 mock：按网址和标题里的关键词猜 */
const FOLDER_GUESSES: [RegExp, string][] = [
  [/github\.com/i, '开源项目'],
  [/vue|vite|pinia|css|tailwind|webpack|rollup|figma/i, '前端'],
  [/spring|nacos|sa-token|gateway|mybatis|minio|mysql|redis/i, '后端与架构'],
  [/hugging|claude|anthropic|prompt/i, 'AI 与模型'],
  [/news|hacker/i, '资讯'],
]

// ── 接口实现（签名与 space.ts 的 real 一致） ──

export const mockSpace = {
  fetchBookmarks: (query: BookmarkPageQuery = {}) => {
    const keyword = query.keyword?.trim().toLowerCase()
    const rows = bookmarks
      .filter((b) => query.folderId === undefined || same(b.folderId, query.folderId))
      .filter((b) => query.status === undefined || b.status === Number(query.status))
      .filter((b) => query.tagId === undefined || b.tagIds.some((id) => same(id, query.tagId)))
      .filter((b) => !query.domain || b.domain === query.domain)
      .filter(
        (b) =>
          !keyword ||
          [b.title, b.url, b.description ?? ''].some((field) => field.toLowerCase().includes(keyword)),
      )
      .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))
      .map(toVO)
    return delay(paginate(rows, Number(query.pageNum ?? 1), Number(query.pageSize ?? 10)))
  },

  fetchBookmark: (id: EntityId) => delay(toVO(requireBookmark(id)), 160),

  createBookmark: async (body: BookmarkSaveRequest) => {
    await delay(null, 220)
    const existing = bookmarks.find((b) => normalize(b.url) === normalize(body.url))
    if (existing) {
      existing.tagIds = (body.tagIds ?? []).map(String)
      return existing.id
    }
    const folderId = body.folderId ?? 0
    if (!same(folderId, 0)) requireFolder(folderId)
    const row: BookmarkRow = {
      id: nextId(),
      folderId,
      title: body.title.trim(),
      url: body.url.trim(),
      domain: new URL(body.url.trim()).hostname.toLowerCase(),
      description: body.description ?? null,
      faviconUrl: body.faviconUrl ?? null,
      source: 'manual',
      status: 0,
      visitCount: 0,
      remark: body.remark ?? null,
      createTime: now(),
      updateTime: now(),
      tagIds: (body.tagIds ?? []).map(String),
    }
    bookmarks.push(row)
    return row.id
  },

  updateBookmark: async (id: EntityId, body: Partial<BookmarkSaveRequest>) => {
    await delay(null, 220)
    const row = requireBookmark(id)
    if (body.url && body.url !== row.url) {
      if (bookmarks.some((b) => !same(b.id, id) && normalize(b.url) === normalize(body.url!))) {
        fail('该URL已存在书签', 409)
      }
      row.url = body.url
      row.domain = new URL(body.url).hostname.toLowerCase()
    }
    if (body.folderId !== undefined) {
      if (!same(body.folderId, 0)) requireFolder(body.folderId)
      row.folderId = body.folderId
    }
    if (body.title?.trim()) row.title = body.title.trim()
    if (body.description !== undefined) row.description = body.description
    if (body.remark !== undefined) row.remark = body.remark
    if (body.tagIds) row.tagIds = body.tagIds.map(String)
    row.updateTime = now()
  },

  updateBookmarkStatus: async (id: EntityId, status: BookmarkStatusValue) => {
    await delay(null, 160)
    requireBookmark(id).status = status
  },

  bindBookmarkTags: async (id: EntityId, tagIds: EntityId[]) => {
    await delay(null, 120)
    tagIds.forEach(requireTag)
    requireBookmark(id).tagIds = tagIds.map(String)
  },

  moveBookmarks: async (bookmarkIds: EntityId[], targetFolderId: EntityId) => {
    await delay(null, 220)
    if (!same(targetFolderId, 0)) requireFolder(targetFolderId)
    const rows = bookmarks.filter((b) => bookmarkIds.some((id) => same(id, b.id)))
    rows.forEach((b) => (b.folderId = targetFolderId))
    return rows.length
  },

  batchDeleteBookmarks: async (bookmarkIds: EntityId[]) => {
    await delay(null, 220)
    let removed = 0
    for (let i = bookmarks.length - 1; i >= 0; i -= 1) {
      if (bookmarkIds.some((id) => same(id, bookmarks[i]!.id))) {
        bookmarks.splice(i, 1)
        removed += 1
      }
    }
    return removed
  },

  deleteBookmark: async (id: EntityId) => {
    await delay(null, 160)
    const index = bookmarks.findIndex((b) => same(b.id, id))
    if (index < 0) fail('书签不存在', 404)
    bookmarks.splice(index, 1)
  },

  /** 按网址粗略模拟：种子里的旧站点打不开，其余都能打开 */
  checkBookmarkLinks: async (bookmarkIds: EntityId[]): Promise<LinkCheckResult[]> => {
    await delay(null, 700)
    const checkedAt = now()
    return bookmarkIds
      .map((id) => bookmarks.find((b) => same(b.id, id)))
      .filter((b): b is BookmarkRow => Boolean(b))
      .map((b): LinkCheckResult => {
        if (b.status === 1) return { id: b.id, verdict: 'skipped', reason: '已归档，不检查', status: 1, changed: false }
        const dead = /example-dev|code\.google\.com/.test(b.url)
        const next = dead ? 2 : 0
        const changed = next !== b.status
        b.status = next
        b.checkTime = checkedAt
        b.checkResult = dead ? (b.url.includes('google') ? '页面不存在（404）' : '域名无法解析') : null
        return { id: b.id, verdict: dead ? 'dead' : 'alive', reason: b.checkResult, status: next, changed }
      })
  },

  /** 关键词猜目录、按域名打标签、截掉标题里「 - 站点名」的尾巴 */
  suggestBookmarks: async (bookmarkIds: EntityId[], actions: AiAction[]): Promise<AiSuggestion[]> => {
    await delay(null, 1400)
    const on = new Set(actions)
    const result: AiSuggestion[] = []
    for (const id of bookmarkIds) {
      const b = bookmarks.find((row) => same(row.id, id))
      if (!b) continue
      const s: AiSuggestion = { bookmarkId: b.id }
      if (on.has('folder')) {
        const guess = FOLDER_GUESSES.find(([pattern]) => pattern.test(`${b.url} ${b.title}`))?.[1]
        const existing = guess ? folders.find((f) => folderPathOf(f.id) === guess) : undefined
        const currentPath = same(b.folderId, 0) ? '' : folderPathOf(b.folderId)
        if (guess && !currentPath.startsWith(guess)) s.folder = { id: existing?.id ?? null, path: guess }
      }
      if (on.has('tags')) {
        const names = [/github\.com/.test(b.url) ? '开源' : '', /docs?[./]|\/guide|\/reference/.test(b.url) ? '文档' : '']
          .filter((name) => name && !b.tagIds.some((tagId) => tags.find((t) => same(t.id, tagId))?.name === name))
        if (names.length) {
          s.tags = names.map((name) => {
            const tag = tags.find((t) => t.name === name)
            return { id: tag?.id ?? null, name, color: tag?.color ?? null }
          })
        }
      }
      if (on.has('title')) {
        const short = b.title.split(/\s+[-|·–—]\s+/)[0]!.trim()
        if (short && short !== b.title) s.title = short
      }
      if (on.has('description') && !b.description) s.description = `${b.domain ?? '这个站点'}上的页面`
      if (s.folder || s.tags || s.title || s.description) result.push(s)
    }
    return result
  },

  fetchFolderTree: () => delay(buildTree(0)),

  createFolder: async (body: FolderSaveRequest) => {
    await delay(null, 180)
    const parentId = body.parentId ?? 0
    const parent = same(parentId, 0) ? null : requireFolder(parentId)
    const name = body.name.trim()
    checkUniqueName(parentId, name)
    const siblings = folders.filter((f) => same(f.parentId, parentId))
    const row: FolderRow = {
      id: nextId(),
      parentId,
      name,
      level: parent ? (parent.level ?? 1) + 1 : 1,
      sortOrder: siblings.length,
      createTime: now(),
    }
    folders.push(row)
    return row.id
  },

  updateFolder: async (id: EntityId, body: FolderUpdateRequest) => {
    await delay(null, 160)
    const folder = requireFolder(id)
    if (body.name?.trim() && body.name.trim() !== folder.name) {
      checkUniqueName(folder.parentId, body.name.trim(), id)
      folder.name = body.name.trim()
    }
    if (body.sortOrder !== undefined) folder.sortOrder = body.sortOrder
    if (body.remark !== undefined) folder.remark = body.remark
  },

  moveFolder: async (id: EntityId, targetParentId: EntityId, sortOrder?: number) => {
    await delay(null, 180)
    const folder = requireFolder(id)
    if (same(id, targetParentId)) fail('不能将目录移动到自身', 400)
    const parent = same(targetParentId, 0) ? null : requireFolder(targetParentId)
    if (parent && descendants(id).some((d) => same(d.id, parent.id))) fail('不能将目录移动到其后代目录下', 400)
    checkUniqueName(targetParentId, folder.name, id)
    folder.parentId = targetParentId
    if (sortOrder !== undefined) folder.sortOrder = sortOrder
    relevel(id, parent ? (parent.level ?? 1) + 1 : 1)
  },

  deleteFolder: async (id: EntityId) => {
    await delay(null, 180)
    requireFolder(id)
    if (folders.some((f) => same(f.parentId, id))) fail('目录下存在子目录，无法删除', 409)
    if (bookmarks.some((b) => same(b.folderId, id))) fail('目录下存在书签，请先迁移或清空', 409)
    folders.splice(folders.findIndex((f) => same(f.id, id)), 1)
  },

  /** 固定的几条示范：新建「工程化」收纳构建工具、「稍后阅读」改名、「网关」并入「微服务」 */
  planFolders: async (_hint?: string): Promise<FolderPlan> => {
    await delay(null, 1600)
    const named = (name: string, parentId?: EntityId) =>
      folders.find((f) => f.name === name && (parentId === undefined || same(f.parentId, parentId)))
    const ops: FolderPlanOp[] = []
    const front = named('前端')
    const build = front && named('构建工具', front.id)
    if (front && build && !named('工程化', front.id)) {
      ops.push({ op: 'create', key: 'N1', parent: String(front.id), name: '工程化', after: '前端 / 工程化', reason: '构建、规范类工具集中放' })
      ops.push({ op: 'move', folder: String(build.id), parent: 'N1', before: folderPathOf(build.id), after: '前端 / 工程化 / 构建工具', reason: '构建工具属于工程化' })
    }
    const later = named('稍后阅读', 0)
    if (later && !named('待读', 0)) {
      ops.push({ op: 'rename', folder: String(later.id), name: '待读', before: '稍后阅读', after: '待读', reason: '与「待读」标签统一叫法' })
    }
    const micro = named('微服务')
    const gateway = micro && named('网关', micro.id)
    if (micro && gateway) {
      ops.push({
        op: 'merge',
        folder: String(gateway.id),
        into: String(micro.id),
        before: folderPathOf(gateway.id),
        after: folderPathOf(micro.id),
        bookmarkCount: bookmarks.filter((b) => same(b.folderId, gateway.id)).length,
        reason: '网关里书签很少，并入微服务',
      })
    }
    return {
      summary: ops.length ? '把构建工具收进「工程化」，统一待读的叫法，合并书签很少的「网关」。' : '目录结构已经比较清晰，不需要调整。',
      ops,
      dropped: 0,
    }
  },

  fetchTags: () => delay([...tags].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))),

  createTag: async (body: TagSaveRequest) => {
    await delay(null, 160)
    if (tags.some((t) => t.name === body.name.trim())) fail('标签名称已存在', 409)
    const row: SpaceTag = { id: nextId(), name: body.name.trim(), color: body.color ?? null, sortOrder: tags.length }
    tags.push(row)
    return row.id
  },

  updateTag: async (id: EntityId, body: TagUpdateRequest) => {
    await delay(null, 160)
    const tag = requireTag(id)
    if (body.name?.trim() && body.name.trim() !== tag.name) {
      if (tags.some((t) => t.name === body.name!.trim())) fail('标签名称已存在', 409)
      tag.name = body.name.trim()
    }
    if (body.color !== undefined) tag.color = body.color
    if (body.sortOrder !== undefined) tag.sortOrder = body.sortOrder
    if (body.remark !== undefined) tag.remark = body.remark
  },

  deleteTag: async (id: EntityId) => {
    await delay(null, 160)
    requireTag(id)
    tags.splice(tags.findIndex((t) => same(t.id, id)), 1)
    bookmarks.forEach((b) => (b.tagIds = b.tagIds.filter((tagId) => !same(tagId, id))))
  },

  /** 真解析上传的 HTML：保留文件夹结构，顶层同名复用，按规范化网址去重 */
  importChromeBookmarks: async (file: File): Promise<ImportTask> => {
    const html = await file.text()
    await delay(null, 1400)
    const task: ImportTask = { id: nextId(), status: 2, source: 'chrome', createTime: now() }
    if (!/NETSCAPE-Bookmark-file-1/i.test(html)) {
      Object.assign(task, { status: 3, errorMsg: '文件不是 Netscape Bookmark 格式（缺少 <!DOCTYPE NETSCAPE-Bookmark-file-1>）' })
      importTasks.unshift(task)
      fail(task.errorMsg!, 400)
    }
    const doc = new DOMParser().parseFromString(html, 'text/html')
    let total = 0
    let success = 0
    let duplicate = 0
    let failed = 0
    const walk = (dl: Element, parentId: EntityId, level: number) => {
      for (const dt of Array.from(dl.children).filter((el) => el.tagName === 'DT')) {
        const heading = dt.querySelector(':scope > h3')
        const link = dt.querySelector(':scope > a')
        if (heading) {
          const name = heading.textContent?.trim() || '未命名'
          let folder = folders.find((f) => same(f.parentId, parentId) && f.name === name)
          if (!folder) {
            folder = { id: nextId(), parentId, name, level, sortOrder: folders.length, createTime: now() }
            folders.push(folder)
          }
          const inner = dt.querySelector(':scope > dl')
          if (inner) walk(inner, folder.id, level + 1)
        } else if (link) {
          total += 1
          const url = link.getAttribute('href') ?? ''
          if (!/^https?:\/\//i.test(url)) {
            failed += 1
          } else if (bookmarks.some((b) => normalize(b.url) === normalize(url))) {
            duplicate += 1
          } else {
            success += 1
            bookmarks.push({
              id: nextId(),
              folderId: parentId,
              title: link.textContent?.trim() || url,
              url,
              domain: new URL(url).hostname.toLowerCase(),
              source: 'chrome',
              status: 0,
              visitCount: 0,
              createTime: now(),
              updateTime: now(),
              tagIds: [],
            })
          }
        }
      }
    }
    const root = doc.querySelector('dl')
    if (root) walk(root, 0, 1)
    Object.assign(task, { totalCount: total, successCount: success, duplicateCount: duplicate, failCount: failed })
    importTasks.unshift(task)
    return task
  },

  fetchExportBlob: async (scope: ExportScope) => {
    await delay(null, 500)
    const rows = bookmarks.filter(
      (b) =>
        b.status === 0 &&
        (scope.scopeType === 'all' ||
          (scope.scopeType === 'folder' && same(b.folderId, scope.scopeId)) ||
          (scope.scopeType === 'tag' && b.tagIds.some((id) => same(id, scope.scopeId)))),
    )
    exportTasks.unshift({
      id: nextId(),
      status: 2,
      exportType: 'chrome',
      scopeType: scope.scopeType,
      scopeId: scope.scopeType === 'all' ? null : scope.scopeId,
      totalCount: rows.length,
      createTime: now(),
    })
    const items = rows.map((b) => `    <DT><A HREF="${escapeHtml(b.url)}">${escapeHtml(b.title)}</A>`).join('\n')
    const html = `<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n${items}\n</DL><p>\n`
    return new Blob([html], { type: 'text/html' })
  },

  fetchImportTasks: (query: TaskPageQuery = {}, _silent = false) =>
    delay(paginate(importTasks, Number(query.pageNum ?? 1), Number(query.pageSize ?? 10))),

  fetchExportTasks: (query: TaskPageQuery = {}, _silent = false) =>
    delay(paginate(exportTasks, Number(query.pageNum ?? 1), Number(query.pageSize ?? 10))),

  cancelImportTask: async (id: EntityId) => {
    await delay(null, 160)
    const task = importTasks.find((t) => same(t.id, id)) ?? fail('任务不存在', 404)
    task.status = 3
    task.errorMsg = '已取消'
  },

  cancelExportTask: async (id: EntityId) => {
    await delay(null, 160)
    const task = exportTasks.find((t) => same(t.id, id)) ?? fail('任务不存在', 404)
    task.status = 3
    task.errorMsg = '已取消'
  },
}
