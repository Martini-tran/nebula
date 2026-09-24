import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { AuthUser, LoginResult } from '../types/auth'

const TOKEN_STORAGE_KEY = 'nebula-scribe:token'
const USER_STORAGE_KEY = 'nebula-scribe:user'

const readToken = () => {
  if (typeof window === 'undefined') {
    return ''
  }
  return localStorage.getItem(TOKEN_STORAGE_KEY) ?? ''
}

const readUser = (): AuthUser | null => {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

/**
 * 登录态：只存 token 与展示用的用户信息。
 * token 是否仍有效以服务端为准——请求返回 401 时由 request.ts 调 logout() 清掉。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(readToken())
  const user = ref<AuthUser | null>(readUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  /** 页头展示名：昵称优先，没有就用用户名 */
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  watch(token, (value) => {
    if (typeof window === 'undefined') return
    if (value) {
      localStorage.setItem(TOKEN_STORAGE_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  })

  watch(user, (value) => {
    if (typeof window === 'undefined') return
    if (value) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(USER_STORAGE_KEY)
    }
  })

  const login = (result: LoginResult) => {
    token.value = result.tokenValue
    user.value = {
      userId: result.userId,
      username: result.username,
      nickname: result.nickname,
    }
  }

  const logout = () => {
    token.value = ''
    user.value = null
  }

  return {
    token,
    user,
    isLoggedIn,
    displayName,
    login,
    logout,
  }
})
