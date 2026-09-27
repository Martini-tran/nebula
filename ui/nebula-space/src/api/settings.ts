/**
 * 个人偏好接口。后端还没有（设计见 space-search.html「后端待补」），路径按设计拟定为 /space/me/settings，
 * 整份 JSON 读写；未接通时走下面的 mock，存在浏览器 localStorage。
 */
import { get, put } from '../utils/request'
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
