/**
 * 个人偏好接口：后端 /space/me/settings（nebula-service-space，表 space_user_setting，每人一行）。
 * 整份 JSON 读写，字段由前端定义（types/settings.ts），后端不逐项解释；从没保存过返回空对象。
 * VITE_REAL_MODULES 不含 settings 时走下面的 mock，存在浏览器 localStorage。
 */
import request, { blobOrError, get, put } from '../utils/request'
import { delay, useMockFor } from './mock'
import { DEFAULT_SETTINGS, type SpaceSettings } from '../types/settings'

const BASE = '/space/me/settings'

const real = {
  fetchSettings: () => get<Partial<SpaceSettings>>(BASE),
  saveSettings: (body: SpaceSettings) => put<void>(BASE, body),
}

const KEY = 'nebula-space:mock:settings.v1'

const mock: typeof real = {
  fetchSettings: () => {
    try {
      return delay(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<SpaceSettings>, 80)
    } catch {
      return delay({ ...DEFAULT_SETTINGS }, 80)
    }
  },
  saveSettings: (body) => {
    localStorage.setItem(KEY, JSON.stringify(body))
    return delay(undefined, 120)
  },
}

const api = useMockFor('settings') ? mock : real

export const { fetchSettings, saveSettings } = api

/** 「导出全部数据」：GET /space/me/export 由服务端取齐各模块数据打成一份 JSON（只有接通后端时可用） */
export const downloadAllData = () =>
  blobOrError(request.get<unknown, Blob>('/space/me/export', { responseType: 'blob', timeout: 0 }))
