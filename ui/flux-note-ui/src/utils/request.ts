import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

export const ResponseCode = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  VALIDATION_ERROR: 422,
  INTERNAL_ERROR: 500,
  NOT_IMPLEMENTED: 501,
} as const

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T | null
}

const showError = (message: string) => {
  if (typeof window !== 'undefined') {
    console.error('[request]', message)
  }
}

const redirectToLogin = async () => {
  const { default: router } = await import('../router')
  await router.push('/')
}

const extractMessage = (payload: unknown, fallback: string): string => {
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const value = (payload as { message?: unknown }).message
    if (typeof value === 'string' && value.length > 0) {
      return value
    }
  }
  return fallback
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore(pinia)
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse | unknown

    if (payload && typeof payload === 'object' && 'code' in (payload as ApiResponse)) {
      const body = payload as ApiResponse
      if (body.code === ResponseCode.SUCCESS) {
        return body.data as unknown as typeof response
      }

      const message = body.message || '请求失败'
      showError(message)
      return Promise.reject(new Error(message))
    }

    return payload as typeof response
  },
  async (error: AxiosError<ApiResponse>) => {
    const status = error.response?.status

    if (status === ResponseCode.UNAUTHORIZED) {
      const authStore = useAuthStore(pinia)
      authStore.logout()
      showError('登录已失效,请重新登录')
      await redirectToLogin()
      return Promise.reject(error)
    }

    const message = extractMessage(error.response?.data, error.message || '网络异常,请稍后重试')
    showError(message)
    return Promise.reject(error)
  },
)

export const get = <T = unknown>(url: string, config?: AxiosRequestConfig) =>
  request.get<unknown, T>(url, config)

export const post = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  request.post<unknown, T>(url, data, config)

export const put = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  request.put<unknown, T>(url, data, config)

export const del = <T = unknown>(url: string, config?: AxiosRequestConfig) =>
  request.delete<unknown, T>(url, config)

export default request
