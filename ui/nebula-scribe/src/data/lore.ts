/**
 * 本地假数据：设定库条目。
 */
import type { LoreEntry } from '../types/lore'

export const mockLore: LoreEntry[] = [
  {
    id: 1,
    workId: 1,
    kind: 'character',
    name: '沈砚',
    aliases: ['断刀捕快'],
    summary: '城南捕快，三年前断了刀便再没换过，沉默寡言但记性极好。',
    detail:
      '## 外貌\n三十上下，瘦高，左手虎口有一道旧疤。\n\n## 动机\n寻找三年前灭门案的真凶，但真正驱动他的是「不肯承认已经晚了」。\n\n## 弧光\n从执着于旧案，到接受有些事无法挽回，转而守住眼前的人。',
    tags: ['主角', '第一视角'],
    pinned: true,
    updateTime: '2026-08-10 20:00:00',
  },
  {
    id: 2,
    workId: 1,
    kind: 'character',
    name: '柳三娘',
    aliases: ['三娘', '柳掌柜'],
    summary: '西市酒肆掌柜，消息灵通，与沈砚有旧，立场始终暧昧。',
    detail: '## 定位\n信息节点兼道德灰度人物。\n\n## 秘密\n她是当年报官的人，但一直没敢告诉沈砚。',
    tags: ['配角', '伏笔'],
    pinned: true,
    updateTime: '2026-08-08 15:30:00',
  },
  {
    id: 3,
    workId: 1,
    kind: 'location',
    name: '鼓楼',
    summary: '全城报时之处，近来每夜少敲一记更鼓，是全书核心异象的发生地。',
    detail: '共七层，顶层积尘极厚却有新鲜脚印。更鼓由世袭更夫敲击，从不外传。',
    tags: ['核心场景'],
    pinned: true,
    updateTime: '2026-08-09 10:12:00',
  },
  {
    id: 4,
    workId: 1,
    kind: 'faction',
    name: '漏更司',
    summary: '不见于官册的隐秘组织，以「时间被吃掉了」为教义，成员皆无姓名。',
    detail: '第二卷末揭示其存在。成员以袖口墨迹为记，墨中掺有鼓楼顶层的尘土。',
    tags: ['反派势力', '第二卷登场'],
    pinned: false,
    updateTime: '2026-08-07 09:00:00',
  },
  {
    id: 5,
    workId: 1,
    kind: 'item',
    name: '断刀',
    summary: '沈砚随身佩带的断刃，断口平整不似战损，更像被人为截去。',
    detail: '断口处有极细的刻痕，需在第三卷由柳三娘点破：那是漏更司的记名方式。',
    tags: ['关键道具', '伏笔'],
    pinned: false,
    updateTime: '2026-08-05 17:45:00',
  },
  {
    id: 6,
    workId: 1,
    kind: 'rule',
    name: '更漏规则',
    summary: '本书世界观基石：被漏掉的那一记更鼓，会从世上抹去一段等长的时间。',
    detail:
      '## 规则\n1. 每漏一记，城中随机一人失去一个时辰的记忆。\n2. 漏满七记，将有一人被彻底遗忘。\n3. 规则对更夫本人无效——这是他不肯承认的原因。\n\n## 约束\n必须在第五章前完整交代，否则后续推理无法成立。',
    tags: ['世界观', '硬设定'],
    pinned: true,
    updateTime: '2026-08-11 08:30:00',
  },
]
