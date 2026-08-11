import { post } from '../utils/request'
import { USE_MOCK, delay } from './mock'
import type { AiGenerateRequest, AiGenerateResult } from '../types/ai'

/**
 * AI 写作辅助接口。
 *
 * 后端由 nebula-sdk-ai 的流程编排承接，前端只传意图与上下文。
 */
const BASE = '/scribe/ai'

const MOCK_TEXT: Record<string, string> = {
  continue:
    '他推开门。屋里没有人，桌上的茶还温着，杯沿留着一道极浅的口脂痕。窗是从里面闩上的，可窗台的灰上印着半只脚印——朝外。\n\n沈砚在桌边坐下，把断刀横放在膝上。雨声忽然停了，静得能听见自己的心跳。他数到七，灯芯"啪"地爆了一下。',
  polish:
    '雨自子时落下。青石板先洇出几点湿痕，继而连成一片，最后整条长街都浸在水声里。守夜人提灯过巷，火光被雨丝割得零碎。',
  expand:
    '雨是子时下起来的。起初只是几点，落在青石板上洇开铜钱大的湿痕，一点，两点，彼此还够不着。约莫一炷香后，湿痕连成了片，再分不出哪一滴是哪一滴。到最后，整条长街都浸在水声里——不是雷雨那种劈头盖脸的响，是细密而耐心的、仿佛要下到天亮的声音。守夜人提着灯笼从巷口过，油纸罩子被雨水打得发亮，火光透出来，被雨丝割得支离破碎，在墙上晃出一片碎金。',
  condense: '子时落雨，长街尽湿。守夜人提灯过巷，火光被雨割碎。',
  rewrite:
    '若从守夜人的眼里看：他提灯拐进长街时，雨才刚起。檐下站着个人，一动不动，像块被雨浇透的石头。他绕开走了，没敢多看——这半个月里，这条街上多的是不该多看的东西。',
  brainstorm: '',
  outline: '',
  critique:
    '三处需要留意：\n\n1. 第三章交代「灯芯爆过三次」，但第一章设定的更漏规则是「七」，两个数字的象征系统尚未打通，读者会误以为三也有含义。\n\n2. 柳三娘在第二章说自己「那晚不在城里」，与设定库中「她是当年报官的人」冲突——需要明确这是她的谎言，并在文中留下可被察觉的破绽。\n\n3. 断刀的刻痕是第三卷的关键，但前两卷从未有人近距离看过这把刀，缺少铺垫。建议在第四章让更夫瞥见一眼并露出异色。',
}

const MOCK_OPTIONS: Record<string, string[]> = {
  brainstorm: [
    '屋里的人其实一直都在，只是沈砚看不见他——漏更司的成员会被「遗忘」，包括从视野里消失。',
    '桌上的信是沈砚三年前自己写的，他忘了这件事，而这正是他被漏掉的那个时辰。',
    '柳三娘先一步到过这里，她带走了本该留下的第二封信。',
    '更夫今晚没有漏敲，是沈砚自己漏听了——异象第一次落到主角身上。',
  ],
  outline: [
    '第五章 · 守鼓楼：沈砚夜宿鼓楼顶层，记录每一记更鼓，亲历漏敲的瞬间。',
    '第六章 · 少了的那个时辰：醒来后发现自己丢失了子时到丑时的记忆，桌上多了一封自己笔迹的信。',
    '第七章 · 三娘的破绽：对质柳三娘，她的说辞与信中细节对不上，沈砚第一次怀疑她。',
    '第八章 · 袖口的墨：更夫死于家中，袖口墨迹与信墨同源，漏更司浮出水面。',
  ],
}

/** 触发一次 AI 生成。 */
export const generate = async (body: AiGenerateRequest): Promise<AiGenerateResult> => {
  if (USE_MOCK) {
    return delay(
      {
        id: `mock-${Date.now()}`,
        action: body.action,
        content: MOCK_TEXT[body.action] ?? '（模型未返回内容）',
        options: MOCK_OPTIONS[body.action] ?? null,
        tokensUsed: 320,
        createTime: new Date().toISOString(),
      },
      900,
    )
  }

  return post<AiGenerateResult>(`${BASE}/generate`, body)
}

/** 全书一致性检查（长任务，后端异步执行）。 */
export const checkConsistency = async (workId: number | string): Promise<AiGenerateResult> => {
  if (USE_MOCK) {
    return delay(
      {
        id: `mock-check-${Date.now()}`,
        action: 'critique',
        content: MOCK_TEXT.critique!,
        options: null,
        tokensUsed: 1_280,
        createTime: new Date().toISOString(),
      },
      1_400,
    )
  }

  return post<AiGenerateResult>(`${BASE}/works/${workId}/consistency-check`)
}
