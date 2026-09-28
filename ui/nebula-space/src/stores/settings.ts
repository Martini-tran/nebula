import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchSettings, saveSettings } from '../api/settings'
import { setWeekStart } from '../utils/date'
import { DEFAULT_SETTINGS, type SpaceSettings } from '../types/settings'
import { NOTE_TTL_DAYS } from '../types/notes'
import { pinia } from './index'
import { useAuthStore } from './auth'
import type { ModuleKey } from '../config/modules'

/**
 * 本机缓存一份，刷新时先用它渲染（周从哪天开始、首页这些不能等接口）。
 * 缓存和「待提交」标记都按用户分开：同一个浏览器换账号登录，不能把上一个人的偏好推到新账号上。
 */
const userKey = (name: string) => `nebula-space:${name}:${useAuthStore(pinia).user?.userId ?? 'anon'}`
const cacheKey = () => userKey('settings-cache')
/** 改了还没存上（离开页面太快、接口失败）：下次打开先把本机这份推上去，而不是被服务端旧值盖掉 */
const pendingKey = () => userKey('settings-pending')
/** 接后端之前不分用户的旧缓存：第一次登录时拿来顶上，推到服务端后删掉 */
const LEGACY_CACHE_KEY = 'nebula-space:settings-cache'

const flag = (on: boolean) => {
  try {
    if (on) localStorage.setItem(pendingKey(), '1')
    else localStorage.removeItem(pendingKey())
  } catch {
    // 同上
  }
}
const pending = () => {
  try {
    return localStorage.getItem(pendingKey()) === '1'
  } catch {
    return false
  }
}

/** 合并时补齐新加的字段；嵌套对象只有一层，逐个合并 */
const merge = (base: SpaceSettings, patch: Partial<SpaceSettings>): SpaceSettings => {
  const next = { ...base, ...patch }
  next.eveningReview = { ...base.eveningReview, ...patch.eveningReview }
  next.weeklyReview = { ...base.weeklyReview, ...patch.weeklyReview }
  next.remind = { ...base.remind, ...patch.remind }
  return next
}

const readCache = (): SpaceSettings => {
  try {
    return merge(DEFAULT_SETTINGS, JSON.parse(localStorage.getItem(cacheKey()) ?? localStorage.getItem(LEGACY_CACHE_KEY) ?? '{}'))
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

/**
 * 个人偏好：改动即时生效并保存（合并 600ms 内的连续改动，只提交一次）。
 */
export const useSettingsStore = defineStore('settings', () => {
  const data = ref<SpaceSettings>(readCache())
  const loaded = ref(false)
  /** 最近一次保存成功的时间，设置页据此闪一下「已保存」 */
  const savedAt = ref(0)
  const saveError = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined

  const apply = () => {
    setWeekStart(data.value.weekStart)
    try {
      localStorage.setItem(cacheKey(), JSON.stringify(data.value))
    } catch {
      // 缓存不了只影响刷新时的第一眼
    }
  }
  apply()

  const load = async () => {
    // 换过账号时 store 里还是上一个人的：先换成当前用户的本机缓存
    data.value = readCache()
    apply()
    if (pending()) {
      await flush()
      loaded.value = true
      return
    }
    try {
      const remote = await fetchSettings()
      if (Object.keys(remote ?? {}).length) {
        data.value = merge(DEFAULT_SETTINGS, remote)
        apply()
      } else {
        // 服务端还没有（第一次用、刚接上后端）：把本机这份推上去，之前在浏览器里调过的偏好不丢
        await flush()
      }
      try {
        localStorage.removeItem(LEGACY_CACHE_KEY)
      } catch {
        // 删不掉也只是多一份用不上的缓存
      }
    } catch {
      // 取不到就用本机缓存，不打扰
    } finally {
      loaded.value = true
    }
  }

  const flush = async () => {
    clearTimeout(timer)
    timer = undefined
    try {
      await saveSettings(data.value)
      flag(false)
      saveError.value = ''
      savedAt.value = Date.now()
    } catch (error) {
      saveError.value = error instanceof Error ? error.message : '保存失败'
    }
  }

  const update = (patch: Partial<SpaceSettings>) => {
    data.value = merge(data.value, patch)
    apply()
    flag(true)
    clearTimeout(timer)
    timer = setTimeout(flush, 600)
  }

  // 离开页面前把没来得及提交的改动发出去（发不出去也有 PENDING 标记兜底）
  window.addEventListener('pagehide', () => {
    if (timer) flush()
  })

  const isEnabled = (key: ModuleKey) => key === 'today' || !data.value.disabledModules.includes(key)

  const toggleModule = (key: ModuleKey, on: boolean) => {
    const set = new Set(data.value.disabledModules)
    if (on) set.delete(key)
    else set.add(key)
    const patch: Partial<SpaceSettings> = { disabledModules: [...set] }
    // 首页被关掉了，回到「今天」
    if (!on && data.value.home === key) patch.home = 'today'
    update(patch)
  }

  return { data, loaded, savedAt, saveError, load, update, isEnabled, toggleModule }
})

/** 临时笔记的寿命（天）。设置选了「长期」时新笔记默认置顶，取消置顶后仍按 7 天算 */
export const noteTtl = () => useSettingsStore(pinia).data.noteTtlDays || NOTE_TTL_DAYS
export const notesLongByDefault = () => useSettingsStore(pinia).data.noteTtlDays === 0
