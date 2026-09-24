/**
 * 本地假数据：作品 / 卷 / 章节。
 *
 * 仅在 VITE_USE_MOCK=true 时被 api 层使用，后端就绪后删除本文件与
 * api/*.ts 中的 mock 分支即可，页面代码无需改动。
 */
import type { ChapterDetail, ChapterListItem, Volume, WorkDetail, WorkListItem } from '../types/work'

/** 卷表落地前，mock 仍按卷组织好写，导出时再摊平成章节列表 */
type MockVolume = Omit<Volume, 'chapters'> & { chapters: Omit<ChapterListItem, 'workId'>[] }

const PARAGRAPHS = [
  '雨是子时下起来的。青石板上先是几点湿痕，而后连成一片，最后整条长街都浸在水声里。守夜人提着灯笼从巷口过，火光被雨丝割得支离破碎。',
  '沈砚站在檐下，没有动。他已经在这里站了半个时辰，衣摆下沿滴着水，像是刚从河里捞上来。对面那扇门始终没开，可他知道里面有人——灯芯爆过三次，每次都在他数到七的时候。',
  '"你要等的人不会来了。"身后的声音很轻，混在雨里几乎听不真切。沈砚没有回头，只是把手按在了腰间那截断刀上。刀是三年前断的，他一直没换。',
  '"我知道。"他说，"我等的是他不来这件事本身。"',
  '雨忽然大了。远处传来更鼓，一声，两声。第三声没有响——这在城里已经十七天没有发生过了。沈砚终于转过身，雨水顺着他的下颌线往下淌，落进领口。',
]

const makeContent = (seed: number, size = 5): string => {
  const out: string[] = []
  for (let i = 0; i < size; i += 1) {
    out.push(PARAGRAPHS[(seed + i) % PARAGRAPHS.length]!)
  }
  return out.join('\n\n')
}

export const mockWorks: WorkListItem[] = [
  {
    id: 1,
    title: '断刀记',
    summary: '一个不肯换刀的捕快，和一座每晚少敲一记更鼓的城。',
    genre: '古代悬疑',
    tags: ['悬疑', '武侠', '慢热'],
    status: 'serializing',
    wordCount: 183_420,
    chapterCount: 62,
    targetWordCount: 600_000,
    updateTime: '2026-08-10 22:14:00',
    createTime: '2025-11-02 09:30:00',
  },
  {
    id: 2,
    title: '归墟纪年',
    summary: '海沟深处沉着一座会呼吸的城，它每醒来一次，人间就少一个姓氏。',
    genre: '东方玄幻',
    tags: ['玄幻', '设定流', '群像'],
    status: 'serializing',
    wordCount: 421_880,
    chapterCount: 145,
    targetWordCount: 1_200_000,
    updateTime: '2026-08-09 01:05:00',
    createTime: '2024-06-18 20:12:00',
  },
  {
    id: 3,
    title: '第七个夏天',
    summary: '她每年夏天都会失去一段记忆，而他每年都要重新认识她一次。',
    genre: '现代言情',
    tags: ['言情', '治愈', '短篇'],
    status: 'finished',
    wordCount: 96_500,
    chapterCount: 34,
    targetWordCount: 100_000,
    updateTime: '2026-05-21 16:40:00',
    createTime: '2025-03-07 11:00:00',
  },
  {
    id: 4,
    title: '锈潮',
    summary: '机械义体开始生锈的那年，整座城市都在寻找最后一批不锈钢。',
    genre: '赛博朋克',
    tags: ['科幻', '反乌托邦'],
    status: 'paused',
    wordCount: 52_300,
    chapterCount: 18,
    targetWordCount: 400_000,
    updateTime: '2026-02-14 23:58:00',
    createTime: '2025-12-01 14:22:00',
  },
  {
    id: 5,
    title: '无名稿',
    summary: '还只有一个念头：如果一个人的名字被所有人同时忘记，他会变成什么？',
    genre: '待定',
    tags: ['构思中'],
    status: 'draft',
    wordCount: 1_240,
    chapterCount: 2,
    targetWordCount: null,
    updateTime: '2026-08-11 08:02:00',
    createTime: '2026-08-11 07:55:00',
  },
]

const buildVolumes = (workId: number): MockVolume[] => [
  {
    id: workId * 100 + 1,
    workId,
    title: '第一卷 · 雨夜',
    sortOrder: 1,
    chapters: [
      {
        id: workId * 1000 + 1,
        volumeId: workId * 100 + 1,
        title: '第一章 少了一记的更鼓',
        sortOrder: 1,
        status: 'done',
        wordCount: 3_120,
        synopsis: '沈砚雨夜蹲守，发现更鼓少敲一记，引出全书核心异象。',
        updateTime: '2026-07-30 21:00:00',
      },
      {
        id: workId * 1000 + 2,
        volumeId: workId * 100 + 1,
        title: '第二章 断刀',
        sortOrder: 2,
        status: 'done',
        wordCount: 3_460,
        synopsis: '回溯三年前断刀之夜，交代沈砚与旧案的牵连。',
        updateTime: '2026-08-01 19:20:00',
      },
      {
        id: workId * 1000 + 3,
        volumeId: workId * 100 + 1,
        title: '第三章 空屋里的灯',
        sortOrder: 3,
        status: 'revising',
        wordCount: 2_880,
        synopsis: '推门进屋，屋内无人却有余温，桌上留一封没有落款的信。',
        updateTime: '2026-08-10 22:14:00',
      },
    ],
  },
  {
    id: workId * 100 + 2,
    workId,
    title: '第二卷 · 更漏',
    sortOrder: 2,
    chapters: [
      {
        id: workId * 1000 + 4,
        volumeId: workId * 100 + 2,
        title: '第四章 更夫说他不识字',
        sortOrder: 1,
        status: 'drafting',
        wordCount: 1_540,
        synopsis: '走访更夫，对方矢口否认漏敲，却在袖口露出与信同样的墨迹。',
        updateTime: '2026-08-11 09:12:00',
      },
      {
        id: workId * 1000 + 5,
        volumeId: workId * 100 + 2,
        title: '第五章 （待定）',
        sortOrder: 2,
        status: 'outline',
        wordCount: 0,
        synopsis: '沈砚决定夜里守在鼓楼，直面漏敲的真相。此章需与第一章形成呼应。',
        updateTime: '2026-08-11 09:15:00',
      },
    ],
  },
]

export const mockWorkDetails: Record<number, WorkDetail> = Object.fromEntries(
  mockWorks.map((work) => [
    work.id,
    {
      ...work,
      logline: work.summary ?? '',
      intro: `${work.summary ?? ''}\n\n这是一部${work.genre ?? ''}题材的长篇作品，目前${
        work.status === 'finished' ? '已完结' : '仍在连载'
      }。`,
      // 卷表落地前与后端一致返回空卷，章节走独立的章节接口
      volumes: [],
    } satisfies WorkDetail,
  ]),
)

/** 某作品的章节（含正文），按卷顺序摊平；排序值按 1000 间隔，与后端一致。 */
export const buildMockChapters = (workId: number): ChapterDetail[] =>
  buildVolumes(workId)
    .flatMap((volume) => volume.chapters)
    .map((chapter, index) => ({
      ...chapter,
      workId,
      volumeId: null,
      sortOrder: (index + 1) * 1000,
      revision: 0,
      content: chapter.status === 'outline' ? '' : makeContent(index),
    }))

/** 首页/作品页展示的写作节奏统计。 */
export const mockWritingStats = {
  /** 最近 14 天每日字数 */
  daily: [1200, 0, 2400, 3100, 1800, 0, 0, 2600, 3300, 2900, 1500, 0, 2200, 3400],
  streakDays: 4,
  monthWords: 42_600,
  monthTarget: 60_000,
}
