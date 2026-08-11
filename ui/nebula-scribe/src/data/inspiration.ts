/**
 * 本地假数据：灵感页的提示卡与写作练习。
 */

export interface PromptCard {
  id: number
  category: string
  title: string
  body: string
  tags: string[]
}

export const mockPrompts: PromptCard[] = [
  {
    id: 1,
    category: '开篇',
    title: '从一个反常的日常细节写起',
    body: '选一件主角每天都做、今天却出了偏差的小事（少敲一记鼓、多出一双鞋、水烧不开），用三百字只写这个偏差，不解释原因。',
    tags: ['开篇', '悬念'],
  },
  {
    id: 2,
    category: '人物',
    title: '给配角一个与主线无关的执念',
    body: '你的配角在意某件对剧情毫无推动作用的事。写一段他为此奔波的场景——读者会因此相信他是活的。',
    tags: ['人物', '配角'],
  },
  {
    id: 3,
    category: '冲突',
    title: '让两个都对的人吵起来',
    body: '设计一场争执，双方立场都站得住脚。不要让任何一方说错话，冲突来自处境而非人品。',
    tags: ['冲突', '对话'],
  },
  {
    id: 4,
    category: '节奏',
    title: '连写五个短句',
    body: '在高潮处强制自己连用五个不超过八字的句子，然后接一个长句。读一遍，感受呼吸的变化。',
    tags: ['节奏', '文风'],
  },
  {
    id: 5,
    category: '伏笔',
    title: '埋一件三章后才有用的东西',
    body: '在本章不经意地写一件物品、一句闲话或一个人名，规定自己必须在第三章后让它产生实际作用。',
    tags: ['伏笔', '结构'],
  },
  {
    id: 6,
    category: '收束',
    title: '用开篇的意象收尾',
    body: '回到第一章的那个画面，换一个心境重写一遍。相同的雨，不同的人。',
    tags: ['结尾', '呼应'],
  },
]

export const promptCategories = ['全部', '开篇', '人物', '冲突', '节奏', '伏笔', '收束']
