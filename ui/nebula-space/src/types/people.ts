/**
 * 人物卡：只是自己的备忘，对方看不到，也不关联系统账号。对应后端 space_person 表，
 * 其他叫法、信息、联系记录、承诺存 JSON 列。
 * 往来时间线、别人欠我的待办都是按名字从会议、随手记、任务里实时汇总的，不落库。
 */
import type { EntityId } from './space'

export interface PersonFact {
  label: string
  value: string
}

/** 手记的承诺：会议之外答应的事 */
export interface PersonPromise {
  id: string
  /** me = 我答应他的；them = 他答应我的 */
  who: 'me' | 'them'
  text: string
  due: string | null
  done: boolean
  createTime: string
}

/** 手记的一次联系（打电话、吃饭），会议和随手记之外的往来 */
export interface ContactLog {
  date: string
  note: string
}

export interface Person {
  id: EntityId
  /** 姓名 */
  name: string
  /** 平时怎么称呼：张工、妈妈 */
  alias: string
  /** 会议、随手记里可能出现的其他叫法（至少两个字，避免误配） */
  extraNames: string[]
  group: string
  color: string
  /** MM-DD */
  birthday: string | null
  /** 多少天没联系就提醒；null 不提醒 */
  contactEvery: number | null
  /** 认识于 / 一句话介绍 */
  intro: string
  facts: PersonFact[]
  memo: string
  contacts: ContactLog[]
  promises: PersonPromise[]
  createTime: string
}

export type PersonSaveRequest = Partial<Omit<Person, 'id' | 'createTime'>>

export const PERSON_GROUPS = ['同事', '朋友', '家人']
