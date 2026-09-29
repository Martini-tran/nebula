/**
 * 公开主页接口：
 * - 自己改：GET / PUT /space/me/profile；预览草稿 POST /space/me/profile/preview（和访客看到的同一套服务端拼装）
 * - 访客看：GET /space/public/@{handle}（网关白名单、不需登录，服务端只返回白名单字段）
 * VITE_REAL_MODULES 不含 profile 时走下面的 mock：设置存 localStorage，公开页在前端拼（只在同一个浏览器里能看到）。
 */
import { get, post, put } from '../utils/request'
import { delay, useMockFor } from './mock'
import { buildPublicPage } from '../views/share/publicData'
import { todayYmd } from '../utils/date'
import type { PublicPage, PublicProfile } from '../types/profile'

const real = {
  fetchProfile: () => get<PublicProfile>('/space/me/profile'),
  saveProfile: (body: PublicProfile) => put<PublicProfile>('/space/me/profile', body),
  /** 短名是否可用（唯一、不是保留词） */
  checkHandle: (handle: string) => get<{ available: boolean; reason?: string }>('/space/me/profile/handle', { params: { handle } }),
  /** 以访客身份预览还没保存的草稿 */
  previewProfile: (body: PublicProfile) => post<PublicPage>('/space/me/profile/preview', body),
  fetchPublicPage: (handle: string) => get<PublicPage>(`/space/public/@${handle}`, { silentForbidden: true }),
  /** 访客展开某个合集后计数 */
  countCollectionView: (handle: string, collectionId: string) => post<void>(`/space/public/@${handle}/collections/${collectionId}/viewed`),
  /** 访客导入某个合集后计数 */
  countImport: (handle: string, collectionId: string) => post<void>(`/space/public/@${handle}/collections/${collectionId}/imported`),
}

const KEY = 'nebula-space:mock:profile.v1'
const RESERVED = new Set(['admin', 'api', 'space', 'login', 'logout', 'www', 'root', 'nebula', 'me', 'public', 'help', 'settings', 'system'])

const nickname = () => {
  try {
    const user = JSON.parse(localStorage.getItem('nebula-space:user') ?? '{}')
    return { nick: (user.nickname || user.username || '我') as string, handle: String(user.username || 'me').toLowerCase().replace(/[^a-z0-9_-]/g, '') || 'someone' }
  } catch {
    return { nick: '我', handle: 'someone' }
  }
}

const defaults = (): PublicProfile => ({
  enabled: false,
  handle: nickname().handle,
  bio: '写 Java 也写前端，在做 Nebula——一套自托管的个人工具集。喜欢收集好用的文档和长文。',
  links: [
    { label: 'GitHub', url: 'https://github.com/' },
    { label: '邮箱', url: 'me@example.com' },
  ],
  now: '在给 Space 加个人工作台：随手记、任务、会议记录\n在读《数据密集型应用系统设计》，第 5 章\n每天早上跑 5 公里',
  nowUpdated: todayYmd(),
  blocks: [
    { key: 'intro', on: true },
    { key: 'links', on: true },
    { key: 'now', on: true },
    { key: 'collections', on: true },
    { key: 'reading', on: true },
    { key: 'goals', on: false },
    { key: 'quotes', on: false },
  ],
  collections: [],
  hiddenReading: [],
  quoteIds: [],
  stats: { visits: 126, collectionViews: 38, imports: 5 },
})

const read = (): PublicProfile => {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? { ...defaults(), ...JSON.parse(raw) } : defaults()
  } catch {
    return defaults()
  }
}
const write = (p: PublicProfile) => localStorage.setItem(KEY, JSON.stringify(p))

const mock: typeof real = {
  fetchProfile: () => delay(read(), 100),
  saveProfile: async (body) => {
    const reason = handleProblem(body.handle)
    if (reason) throw new Error(reason)
    const saved = { ...body, stats: read().stats }
    write(saved)
    return delay(saved, 140)
  },
  checkHandle: (handle) => {
    const reason = handleProblem(handle)
    return delay(reason ? { available: false, reason } : { available: true }, 120)
  },
  previewProfile: (body) => buildPublicPage(body, nickname().nick),
  fetchPublicPage: async (handle) => {
    const p = read()
    if (!p.enabled || p.handle !== handle) throw new Error('这个主页不存在，或者主人没有公开')
    write({ ...p, stats: { ...p.stats, visits: p.stats.visits + 1 } })
    return buildPublicPage(p, nickname().nick)
  },
  countCollectionView: async () => {
    const p = read()
    write({ ...p, stats: { ...p.stats, collectionViews: p.stats.collectionViews + 1 } })
    return delay(undefined, 60)
  },
  countImport: async () => {
    const p = read()
    write({ ...p, stats: { ...p.stats, imports: p.stats.imports + 1 } })
    return delay(undefined, 60)
  },
}

/** 短名规则：3-20 位小写字母、数字、- 和 _；不能是保留词 */
export const handleProblem = (handle: string) => {
  if (!/^[a-z0-9_-]{3,20}$/.test(handle)) return '3-20 位小写字母、数字、- 或 _'
  if (RESERVED.has(handle)) return '这个名字是保留的，换一个'
  return ''
}

const api = useMockFor('profile') ? mock : real

export const { fetchProfile, saveProfile, checkHandle, previewProfile, fetchPublicPage, countCollectionView, countImport } = api
