/**
 * 拼访客看到的公开页数据：mock 模式（VITE_REAL_MODULES 不含 profile）下的前端版本。
 * 接通后端后访客页与「以访客身份预览」都由服务端 PublicPageBuilder 拼，规则与这里一致。
 * 只取打开的区块；每一块只带白名单里的字段——划线不带批注，读完的文章不带划线，目标不带金额类。
 */
import { fetchAllBookmarks } from '../../api/space'
import { fetchHighlights, fetchReadingItems } from '../../api/reading'
import { fetchGoals } from '../../api/goals'
import { fetchHabitLogs, fetchHabits } from '../../api/habits'
import { fetchTaskLists, fetchTasks } from '../../api/tasks'
import { computeProgress } from '../goals/goalProgress'
import { todayYmd, ymdOf } from '../../utils/date'
import type { PublicPage, PublicProfile } from '../../types/profile'

const isEmail = (url: string) => /^mailto:/i.test(url) || (/^[^\s/]+@[^\s/]+\.[^\s/]+$/.test(url) && !/^https?:/i.test(url))
const domainOf = (url: string) => url.replace(/^https?:\/\//, '').split('/')[0]!.replace(/^www\./, '')

export const buildPublicPage = async (profile: PublicProfile, nickname: string): Promise<PublicPage> => {
  const on = profile.blocks.filter((b) => b.on).map((b) => b.key)
  const has = (k: (typeof on)[number]) => on.includes(k)
  const settle = <T>(p: Promise<T>, fallback: T) => p.catch(() => fallback)

  const [collections, reading, goals, quotes] = await Promise.all([
    has('collections')
      ? Promise.all(
          profile.collections.map(async (c) => {
            const list = await settle(fetchAllBookmarks({ folderId: c.folderId, status: 0 }), [])
            const updated = list.map((b) => ymdOf(b.updateTime ?? b.createTime ?? '')).filter(Boolean).sort().at(-1) ?? null
            return {
              id: String(c.folderId),
              title: c.title,
              description: c.description,
              updated,
              bookmarks: list.map((b) => ({ title: b.title || b.url, url: b.url, domain: b.domain || domainOf(b.url) })),
            }
          }),
        )
      : Promise.resolve([]),
    has('reading')
      ? settle(
          Promise.all([fetchReadingItems({ status: 'done' }), fetchReadingItems({ status: 'done', archived: true })]).then(([a, b]) =>
            [...a, ...b]
              .filter((r) => !profile.hiddenReading.map(String).includes(String(r.id)))
              .sort((x, y) => String(y.doneTime).localeCompare(String(x.doneTime)))
              .slice(0, 10)
              .map((r) => ({ title: r.title, url: r.url, domain: r.domain, date: r.doneTime ? ymdOf(r.doneTime) : '' })),
          ),
          [],
        )
      : Promise.resolve([]),
    has('goals')
      ? settle(
          (async () => {
            const year = Number(todayYmd().slice(0, 4))
            const [gs, habits, logs, done, lists, tasks] = await Promise.all([
              fetchGoals(year),
              fetchHabits(true),
              fetchHabitLogs({ from: `${year}-01-01`, to: `${year}-12-31` }),
              fetchReadingItems({ status: 'done' }),
              fetchTaskLists(),
              fetchTasks({ view: 'all' }),
            ])
            const data = { habits, logs, reading: done, entries: [], tasks, lists }
            // 金额类目标（记账来源、单位是钱）不公开
            return gs
              .filter((g) => g.source !== 'ledger' && g.unit !== '¥')
              .map((g) => ({ icon: g.icon, title: g.title, pct: Math.min(1, computeProgress(g, data, todayYmd()).pct) }))
          })(),
          [],
        )
      : Promise.resolve([]),
    has('quotes') && profile.quoteIds.length
      ? settle(
          Promise.all([fetchHighlights(), fetchReadingItems(), fetchReadingItems({ archived: true })]).then(([hls, a, b]) =>
            hls
              .filter((h) => profile.quoteIds.map(String).includes(String(h.id)))
              .map((h) => ({ text: h.text, source: [...a, ...b].find((i) => String(i.id) === String(h.itemId))?.title ?? '' })),
          ),
          [],
        )
      : Promise.resolve([]),
  ])

  return {
    handle: profile.handle,
    nickname,
    bio: has('intro') ? profile.bio : '',
    links: has('links') ? profile.links.filter((l) => l.label.trim() && l.url.trim()).map((l) => ({ label: l.label, url: l.url, masked: isEmail(l.url) })) : [],
    now: has('now') ? profile.now : '',
    nowUpdated: has('now') ? profile.nowUpdated : null,
    blocks: on,
    collections,
    reading,
    goals,
    quotes,
  }
}
