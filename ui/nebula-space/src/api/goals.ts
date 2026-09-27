/**
 * 年度目标与纪念日接口。后端还没有（设计见 space-goals.html「后端待补」），路径按设计拟定为
 * /space/me/goals、/space/me/anniversaries；未接通时走下面的 mock，数据存在浏览器 localStorage。
 * 目标只存定义，进度由前端（后端就绪后由服务端）按来源实时算，见 views/goals/goalProgress.ts。
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nowStamp, todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { Anniversary, AnniversarySaveRequest, Goal, GoalSaveRequest } from '../types/goals'

const BASE = '/space/me'

const real = {
  fetchGoals: (year: number) => get<Goal[]>(`${BASE}/goals`, { params: { year } }),
  createGoal: (body: GoalSaveRequest & { title: string; year: number }) => post<Goal>(`${BASE}/goals`, body),
  updateGoal: (id: EntityId, body: GoalSaveRequest) => put<Goal>(`${BASE}/goals/${id}`, body),
  deleteGoal: (id: EntityId) => del<void>(`${BASE}/goals/${id}`),
  fetchAnniversaries: () => get<Anniversary[]>(`${BASE}/anniversaries`),
  createAnniversary: (body: AnniversarySaveRequest & { title: string; date: string }) => post<Anniversary>(`${BASE}/anniversaries`, body),
  updateAnniversary: (id: EntityId, body: AnniversarySaveRequest) => put<Anniversary>(`${BASE}/anniversaries/${id}`, body),
  deleteAnniversary: (id: EntityId) => del<void>(`${BASE}/anniversaries/${id}`),
}

// ── mock ──

type GoalRow = Goal & { id: string }
type AnnivRow = Anniversary & { id: string }

const goalBase = (): Omit<GoalRow, 'id' | 'title' | 'icon' | 'kind'> => ({
  year: Number(todayYmd().slice(0, 4)),
  target: 0,
  unit: '',
  source: 'manual',
  sourceId: null,
  factor: 1,
  baseline: 0,
  manualValue: 0,
  krs: [],
  sortOrder: 0,
  createTime: nowStamp(),
})

const seedGoals = (): GoalRow[] => {
  const y = todayYmd().slice(0, 4)
  return [
    { ...goalBase(), id: 'g1', title: '跑满 1200 公里', icon: '🏃', kind: 'metric', target: 1200, unit: 'km', source: 'habit', sourceId: 'h1', factor: 5, sortOrder: 1 },
    { ...goalBase(), id: 'g2', title: '读完 24 本书 / 长文', icon: '📚', kind: 'metric', target: 24, unit: '篇', source: 'reading', baseline: 10, sortOrder: 2 },
    {
      ...goalBase(),
      id: 'g3',
      title: 'Nebula 发布 1.0',
      icon: '🚀',
      kind: 'milestone',
      sortOrder: 3,
      krs: [
        { id: 'k1', title: '网关与统一登录', done: true, doneDate: `${y}-03-18`, listId: null },
        { id: 'k2', title: 'Scribe 写作台', done: true, doneDate: `${y}-09-05`, listId: null },
        { id: 'k3', title: 'Space 书签模块', done: true, doneDate: `${y}-09-21`, listId: null },
        { id: 'k4', title: 'Space 个人工作台', done: false, doneDate: null, listId: 'l1' },
        { id: 'k5', title: '部署文档与演示环境', done: false, doneDate: null, listId: null },
      ],
    },
    { ...goalBase(), id: 'g4', title: '存下 10 万', icon: '💰', kind: 'metric', target: 100000, unit: '¥', source: 'ledger', baseline: 0, sortOrder: 4 },
    { ...goalBase(), id: 'g5', title: '拍完 10 卷胶片', icon: '📷', kind: 'metric', target: 10, unit: '卷', source: 'manual', manualValue: 4, sortOrder: 5 },
  ]
}

const seedAnniversaries = (): AnnivRow[] => {
  const t = todayYmd()
  const y = Number(t.slice(0, 4))
  const base = { calendar: 'solar' as const, lunarMonth: null, lunarDay: null, remindDays: null, createTask: false, taskTitle: '', taskFor: null, fileId: null, note: '', tag: '', createTime: `${addDays(t, -30)} 10:00:00` }
  return [
    { ...base, id: 'a1', title: '国庆假期', icon: '🎉', type: 'countdown', date: `${y}-10-01`, note: '放假安排以官方公布为准', tag: '假期' },
    { ...base, id: 'a2', title: '妈妈生日', icon: '🎂', type: 'annual', date: '1962-10-09', calendar: 'lunar', lunarMonth: 8, lunarDay: 29, remindDays: 7, createTask: true, taskTitle: '买生日礼物', tag: '生日' },
    { ...base, id: 'a3', title: '护照到期', icon: '🛂', type: 'countdown', date: `${y + 1}-02-15`, remindDays: 90, fileId: 'fl23', tag: '证件' },
    { ...base, id: 'a4', title: '租约到期', icon: '🏠', type: 'countdown', date: `${y + 1}-03-19`, remindDays: 30, createTask: true, taskTitle: '和房东谈续租', note: '月租 ¥ 3,500', tag: '合同' },
    { ...base, id: 'a5', title: '结婚纪念日', icon: '💍', type: 'annual', date: `${y - 4}-05-20`, remindDays: 7, tag: '纪念日' },
    { ...base, id: 'a6', title: '团子到家', icon: '🐱', type: 'countup', date: `${y - 3}-07-08`, tag: '正数日' },
  ]
}

const goals = createMockTable<GoalRow>('goals.v1', seedGoals)
const annivs = createMockTable<AnnivRow>('anniversaries.v1', seedAnniversaries)

const mock: typeof real = {
  fetchGoals: (year) => delay(structuredClone(goals.all().filter((g) => g.year === year).sort((a, b) => a.sortOrder - b.sortOrder)), 100),
  createGoal: async (body) => delay(structuredClone(goals.insert({ ...goalBase(), ...body, id: nextId(), sortOrder: goals.all().length + 1 } as GoalRow)), 120),
  updateGoal: async (id, body) => {
    const row = goals.update(String(id), body as Partial<GoalRow>)
    if (!row) throw new Error('目标不存在或已删除')
    return delay(structuredClone(row), 80)
  },
  deleteGoal: async (id) => {
    goals.remove(String(id))
    return delay(undefined, 80)
  },
  fetchAnniversaries: () => delay(structuredClone(annivs.all()), 100),
  createAnniversary: async (body) => {
    const row = annivs.insert({
      icon: '📅',
      type: 'countdown',
      calendar: 'solar',
      lunarMonth: null,
      lunarDay: null,
      remindDays: null,
      createTask: false,
      taskTitle: '',
      taskFor: null,
      fileId: null,
      note: '',
      tag: '',
      ...body,
      id: nextId(),
      createTime: nowStamp(),
    } as AnnivRow)
    return delay(structuredClone(row), 120)
  },
  updateAnniversary: async (id, body) => {
    const row = annivs.update(String(id), body as Partial<AnnivRow>)
    if (!row) throw new Error('纪念日不存在或已删除')
    return delay(structuredClone(row), 80)
  },
  deleteAnniversary: async (id) => {
    annivs.remove(String(id))
    return delay(undefined, 80)
  },
}

const api = useMockFor('goals') ? mock : real

export const { fetchGoals, createGoal, updateGoal, deleteGoal, fetchAnniversaries, createAnniversary, updateAnniversary, deleteAnniversary } = api
