/**
 * 公开主页：个人空间唯一的对外出口，默认关闭，逐块决定公开什么。
 * 随手记、任务、会议、周报、记账、人物卡、习惯明细永远不会出现在公开页（见 space-share.html）。
 */
import type { EntityId } from './space'

export type BlockKey = 'intro' | 'links' | 'now' | 'collections' | 'reading' | 'goals' | 'quotes'

export const BLOCKS: Record<BlockKey, { label: string; hint: string }> = {
  intro: { label: '头像与介绍', hint: '昵称取自账号，介绍在这里写' },
  links: { label: '外部链接', hint: 'GitHub、博客、邮箱（邮箱默认点击才显示，防爬）' },
  now: { label: 'Now', hint: '手写的一段近况' },
  collections: { label: '书签合集', hint: '选几个书签目录公开' },
  reading: { label: '最近读完', hint: '只显示标题与原网址，最近 10 篇，可逐篇隐藏' },
  goals: { label: '年度目标', hint: '只显示目标名与进度条，不显示金额类目标' },
  quotes: { label: '摘录精选', hint: '手动挑选的划线，不含批注' },
}

export interface ProfileLink {
  label: string
  url: string
}

export interface PublicCollection {
  /** 对应的书签目录 */
  folderId: EntityId
  title: string
  description: string
}

export interface PublicProfile {
  enabled: boolean
  /** 地址里的短名：/@handle */
  handle: string
  bio: string
  links: ProfileLink[]
  now: string
  nowUpdated: string | null
  blocks: { key: BlockKey; on: boolean }[]
  collections: PublicCollection[]
  /** 不想公开的已读文章 */
  hiddenReading: EntityId[]
  /** 摘录精选：挑出来的划线 */
  quoteIds: EntityId[]
  stats: { visits: number; collectionViews: number; imports: number }
}

/** 访客看到的数据：服务端按白名单拼好，只有这些字段 */
export interface PublicPage {
  handle: string
  nickname: string
  bio: string
  links: { label: string; url: string; masked: boolean }[]
  now: string
  nowUpdated: string | null
  blocks: BlockKey[]
  collections: {
    id: string
    title: string
    description: string
    updated: string | null
    bookmarks: { title: string; url: string; domain: string }[]
  }[]
  reading: { title: string; url: string; domain: string; date: string }[]
  goals: { icon: string; title: string; pct: number }[]
  quotes: { text: string; source: string }[]
}
