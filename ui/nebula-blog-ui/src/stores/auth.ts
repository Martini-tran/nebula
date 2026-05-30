import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const AUTH_STORAGE_KEY = 'stilldoing:is-logged-in'
const TOKEN_STORAGE_KEY = 'stilldoing:token'

const readInitialAuthState = () => {
  if (typeof window === 'undefined') {
    return false
  }

  return localStorage.getItem(AUTH_STORAGE_KEY) === '1'
}

const readInitialToken = () => {
  if (typeof window === 'undefined') {
    return ''
  }

  return localStorage.getItem(TOKEN_STORAGE_KEY) ?? ''
}

export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(readInitialAuthState())
  const token = ref(readInitialToken())

  watch(
    isLoggedIn,
    (value) => {
      if (typeof window !== 'undefined') {
        localStorage.setItem(AUTH_STORAGE_KEY, value ? '1' : '0')
      }
    },
    { immediate: true },
  )

  watch(
    token,
    (value) => {
      if (typeof window === 'undefined') {
        return
      }

      if (value) {
        localStorage.setItem(TOKEN_STORAGE_KEY, value)
      } else {
        localStorage.removeItem(TOKEN_STORAGE_KEY)
      }
    },
    { immediate: true },
  )

  const login = (newToken?: string) => {
    if (newToken) {
      token.value = newToken
    }
    isLoggedIn.value = true
  }

  const logout = () => {
    token.value = ''
    isLoggedIn.value = false
  }

  return {
    isLoggedIn,
    token,
    login,
    logout,
  }
})
