/**
 * 人物卡接口。后端还没有（设计见 space-people.html「后端待补」），路径按设计拟定为 /space/me/people；
 * 未接通时走下面的 mock，数据存在浏览器 localStorage。
 * 隐私：人物卡不参与任何分享、公开主页、周报；只在「导出全部」里单独列出。
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Person, PersonSaveRequest } from '../types/people'

const BASE = '/space/me/people'

const real = {
  fetchPeople: () => get<Person[]>(BASE),
  createPerson: (body: PersonSaveRequest & { name: string }) => post<Person>(BASE, body),
  updatePerson: (id: EntityId, body: PersonSaveRequest) => put<Person>(`${BASE}/${id}`, body),
  deletePerson: (id: EntityId) => del<void>(`${BASE}/${id}`),
}

type Row = Person & { id: string }

const blank = (): Omit<Row, 'id' | 'name'> => ({
  alias: '',
  extraNames: [],
  group: '同事',
  color: '#0d9488',
  birthday: null,
  contactEvery: null,
  intro: '',
  facts: [],
  memo: '',
  contacts: [],
  promises: [],
  createTime: nowStamp(),
})

const seed = (): Row[] => {
  const t = todayYmd()
  const at = (days: number) => addDays(t, -days)
  return [
    {
      ...blank(),
      id: 'p1',
      name: '张立',
      alias: '张工',
      color: '#0d9488',
      birthday: '11-03',
      contactEvery: 14,
      intro: '后端 / 基础设施 · 认识于 2024 年 3 月',
      facts: [
        { label: '团队', value: '基础设施组' },
        { label: '偏好', value: '喝美式不加糖；上午效率高，约会议放上午' },
        { label: '家庭', value: '女儿 5 岁，周三接孩子早走' },
      ],
      memo: '对新技术谨慎，提方案时先讲风险和回滚；喜欢看数据不喜欢看 PPT。',
      promises: [{ id: 'pr1', who: 'them', text: '确认 MinIO 扩容的运维窗口', due: at(2), done: false, createTime: `${at(5)} 10:00:00` }],
      contacts: [],
    },
    { ...blank(), id: 'p2', name: '陈思远', alias: '陈工', color: '#2563eb', intro: '前端 · 书签导入方案的评审人', promises: [{ id: 'pr2', who: 'them', text: '把 Chrome 书签样例文件发我', due: t, done: false, createTime: `${at(1)} 15:00:00` }] },
    { ...blank(), id: 'p3', name: '王蕾', alias: '王工', extraNames: ['王姐'], color: '#db2777', intro: 'DBA', facts: [{ label: '团队', value: '数据平台' }] },
    { ...blank(), id: 'p4', name: '李航', alias: '李工', color: '#d97706', intro: '测试 / 发布' },
    {
      ...blank(),
      id: 'p5',
      name: '周扬',
      alias: '',
      group: '朋友',
      color: '#7c3aed',
      contactEvery: 60,
      intro: '大学室友 · 现在在深圳',
      contacts: [{ date: at(76), note: '视频聊了一小时，他换了工作' }],
      facts: [{ label: '爱好', value: '骑行、胶片' }],
    },
    {
      ...blank(),
      id: 'p6',
      name: '妈妈',
      alias: '',
      group: '家人',
      color: '#65a30d',
      birthday: '10-09',
      contactEvery: 7,
      intro: '农历生日八月廿九',
      contacts: [{ date: at(12), note: '电话，说国庆回家' }, { date: at(26), note: '视频' }],
      promises: [{ id: 'pr3', who: 'me', text: '国庆带她去体检', due: addDays(t, 6), done: false, createTime: `${at(12)} 20:00:00` }],
    },
  ]
}

const table = createMockTable<Row>('people.v1', seed)

const mock: typeof real = {
  fetchPeople: () => delay(structuredClone(table.all()), 100),
  createPerson: async (body) => {
    const name = body.name.trim()
    if (!name) throw new Error('姓名不能为空')
    if (table.all().some((p) => p.name === name)) throw new Error(`已经有「${name}」了`)
    return delay(structuredClone(table.insert({ ...blank(), ...body, name, id: nextId() } as Row)), 120)
  },
  updatePerson: async (id, body) => {
    const row = table.update(String(id), body as Partial<Row>)
    if (!row) throw new Error('人物不存在或已删除')
    return delay(structuredClone(row), 80)
  },
  deletePerson: async (id) => {
    table.remove(String(id))
    return delay(undefined, 80)
  },
}

const api = useMockFor('people') ? mock : real

export const { fetchPeople, createPerson, updatePerson, deletePerson } = api
