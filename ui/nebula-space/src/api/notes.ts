/**
 * 随手记接口。后端还没有（设计见 space-notes.html「后端待补」），路径按设计拟定为 /space/me/notes；
 * 未接通时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 规则（后端实现时照搬）：
 * - 新笔记是临时笔记，expireDate = 今天 + 7；编辑一次重新计时
 * - 置顶 = 长期笔记（expireDate 置空）；加标签、被任务引用也会自动转长期
 * - 过了 expireDate 自动归档（不删除）；从归档恢复会重新变成临时笔记
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd } from '../utils/date'
import { NOTE_TTL_DAYS, type Note, type NoteQuery, type NoteSaveRequest } from '../types/notes'
import type { EntityId } from '../types/space'

export interface NoteStats {
  all: number
  temporary: number
  pinned: number
  archived: number
  /** 明天到期的临时笔记 */
  dueTomorrow: number
  tags: { name: string; count: number }[]
}

const BASE = '/space/me/notes'

const real = {
  fetchNotes: (query: NoteQuery = {}) => get<Note[]>(BASE, { params: query }),
  fetchNote: (id: EntityId) => get<Note>(`${BASE}/${id}`),
  fetchNoteStats: () => get<NoteStats>(`${BASE}/stats`),
  createNote: (body: NoteSaveRequest) => post<Note>(BASE, body),
  updateNote: (id: EntityId, body: NoteSaveRequest) => put<Note>(`${BASE}/${id}`, body),
  deleteNote: (id: EntityId) => del<void>(`${BASE}/${id}`),
}

// ── mock ──

type NoteRow = Note & { id: string }

const seed = (): NoteRow[] => {
  const today = todayYmd()
  // 种子时间相对「现在」往前推，保证新记的笔记总排在最前
  const at = (days: number, hoursAgo = 2) => {
    const d = new Date(Date.now() - days * 86_400_000 - hoursAgo * 3_600_000)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
  }
  const temp = (createdDaysAgo: number) => addDays(today, NOTE_TTL_DAYS - createdDaysAgo)
  const rows: Omit<NoteRow, 'id'>[] = [
    {
      content: '书签前台接口拆分\n现在前端直接调 `/space/admin/**`，普通用户要有 space:* 权限才能用。方案：\n- [x] 列出前台需要的接口\n- [ ] 新增 /space/me/** 只校验登录\n- [ ] admin 接口保留给后台',
      color: 'yellow', pinned: false, expireDate: temp(0), archived: false, tags: [], createTime: at(0, 5), updateTime: at(0, 5),
    },
    {
      content: '评审会想问：重复书签是否按 normalizedUrl 合并标签？还是保留两条？',
      color: 'blue', pinned: true, expireDate: null, archived: false, tags: ['工作'], createTime: at(1), updateTime: at(1),
    },
    {
      content: '快递取件码 6-2-3018，菜鸟驿站东门',
      color: 'plain', pinned: false, expireDate: temp(6), archived: false, tags: [], createTime: at(6, 4), updateTime: at(6, 4),
    },
    {
      content: '## 读《数据密集型应用系统设计》\n第 5 章复制：单主 / 多主 / 无主。无主复制的读修复和反熵过程，和 Redis Cluster 的区别再看一遍。\nhttps://dataintensive.net/',
      color: 'green', pinned: true, expireDate: null, archived: false, tags: ['学习'], createTime: at(6), updateTime: at(6),
    },
    {
      content: '猫粮换成低敏配方，找上次那个链接',
      color: 'pink', pinned: false, expireDate: temp(0), archived: false, tags: [], createTime: at(0, 4), updateTime: at(0, 4),
    },
    {
      content: '给 space 加一个「今天」首页？任务 + 会议 + 随手记放一起。',
      color: 'purple', pinned: false, expireDate: temp(6), archived: false, tags: [], createTime: at(6, 2), updateTime: at(6, 2),
    },
    {
      content: '## 家里网络\n路由器后台 192.168.31.1，光猫桥接已改，端口转发 8443 → NAS。',
      color: 'plain', pinned: true, expireDate: null, archived: false, tags: ['生活'], createTime: at(40), updateTime: at(40),
    },
    {
      content: '## MinIO 扩容准备\n现在 nebula-system 一个桶放了导出文件和头像，生命周期规则没配，导出的书签 HTML 会一直留着。\n\n### 要确认的\n- [x] 当前桶占用：41 GB\n- [ ] 和运维确认扩容到 200 GB 的时间窗口\n- [ ] 导出文件是否 30 天自动清理\n\n参考 https://min.io/docs/minio/linux/administration/object-management/object-lifecycle-management.html',
      color: 'plain', pinned: false, expireDate: temp(0), archived: false, tags: [], createTime: at(0, 1), updateTime: at(0, 1),
    },
    {
      content: '周五聚餐 AA 转给小陈 86 元',
      color: 'yellow', pinned: false, expireDate: null, archived: true, tags: ['生活'], createTime: at(12), updateTime: at(12),
    },
  ]
  return rows.map((row, index) => ({ ...row, id: `n${index + 1}` }))
}

const table = createMockTable<NoteRow>('notes.v1', seed)

/** 到期的临时笔记自动归档（后端由定时任务完成） */
const sweep = () => {
  const today = todayYmd()
  let changed = false
  for (const note of table.all()) {
    if (!note.archived && !note.pinned && note.expireDate && note.expireDate < today) {
      note.archived = true
      changed = true
    }
  }
  if (changed) table.save()
}

const byUpdate = (a: Note, b: Note) => b.updateTime.localeCompare(a.updateTime)

const mock: typeof real = {
  fetchNotes: (query = {}) => {
    sweep()
    const view = query.view ?? 'all'
    const kw = query.keyword?.trim().toLowerCase()
    const rows = table
      .all()
      .filter((n) => (view === 'archived' ? n.archived : !n.archived))
      .filter((n) => view !== 'temporary' || !n.pinned)
      .filter((n) => view !== 'pinned' || n.pinned)
      .filter((n) => !query.tag || n.tags.includes(query.tag))
      .filter((n) => !kw || n.content.toLowerCase().includes(kw) || n.tags.some((t) => t.toLowerCase().includes(kw)))
      .sort(byUpdate)
    return delay(structuredClone(rows), 180)
  },

  fetchNote: async (id) => {
    sweep()
    const note = table.find(id)
    if (!note) throw new Error('笔记不存在或已删除')
    return delay(structuredClone(note), 120)
  },

  fetchNoteStats: () => {
    sweep()
    const tomorrow = addDays(todayYmd(), 1)
    const live = table.all().filter((n) => !n.archived)
    const tagCount = new Map<string, number>()
    live.forEach((n) => n.tags.forEach((t) => tagCount.set(t, (tagCount.get(t) ?? 0) + 1)))
    return delay(
      {
        all: live.length,
        temporary: live.filter((n) => !n.pinned).length,
        pinned: live.filter((n) => n.pinned).length,
        archived: table.all().length - live.length,
        dueTomorrow: live.filter((n) => !n.pinned && n.expireDate && n.expireDate <= tomorrow).length,
        tags: [...tagCount.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count),
      },
      120,
    )
  },

  createNote: async (body) => {
    const now = nowStamp()
    const tags = body.tags ?? []
    const pinned = Boolean(body.pinned) || tags.length > 0
    const row = table.insert({
      id: nextId(),
      content: body.content ?? '',
      color: body.color ?? 'plain',
      pinned,
      expireDate: pinned ? null : addDays(todayYmd(), NOTE_TTL_DAYS),
      archived: false,
      tags,
      createTime: now,
      updateTime: now,
    })
    return delay(structuredClone(row), 160)
  },

  updateNote: async (id, body) => {
    const note = table.find(id)
    if (!note) throw new Error('笔记不存在或已删除')
    const patch: Partial<NoteRow> = { ...body, updateTime: nowStamp() }
    if (body.tags?.length && !note.tags.length) patch.pinned = true
    const pinned = patch.pinned ?? note.pinned
    if (body.archived === false && note.archived) {
      // 从归档恢复：重新变成临时笔记
      patch.pinned = false
      patch.expireDate = addDays(todayYmd(), NOTE_TTL_DAYS)
    } else if (pinned) {
      patch.expireDate = null
    } else if (body.pinned === false || body.content !== undefined) {
      // 取消置顶，或编辑了临时笔记：寿命重新计时
      patch.expireDate = addDays(todayYmd(), NOTE_TTL_DAYS)
    }
    return delay(structuredClone(table.update(id, patch)!), 120)
  },

  deleteNote: async (id) => {
    table.remove(id)
    return delay(undefined, 120)
  },
}

const api = useMockFor('notes') ? mock : real

export const { fetchNotes, fetchNote, fetchNoteStats, createNote, updateNote, deleteNote } = api

