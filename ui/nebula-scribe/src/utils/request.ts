import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
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

/**
 * 登录失效：清本地登录态并带上当前地址跳登录页，登录后回到原处。
 * 已在登录页时不再跳，避免登录接口本身的 401 造成循环。
 */
const handleUnauthorized = async () => {
  useAuthStore(pinia).logout()
  const { default: router } = await import('../router')
  const current = router.currentRoute.value
  if (current.name === 'login') {
    return
  }
  await router.push({ name: 'login', query: { redirect: current.fullPath } })
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
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore(pinia)
    if (authStore.token) {
      // Sa-Token 未配置 token 前缀，直接传原值；加 Bearer 会被当成另一个 token
      config.headers.Authorization = authStore.token
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

request.interceptors.response.use(
  async (response) => {
    const payload = response.data as ApiResponse | unknown

    if (payload && typeof payload === 'object' && 'code' in (payload as ApiResponse)) {
      const body = payload as ApiResponse
      if (body.code === ResponseCode.SUCCESS) {
        return body.data as unknown as typeof response
      }

      const message = body.message || '请求失败'
      showError(message)
      // 部分服务以 HTTP 200 + 业务码 401 表示未登录，与 HTTP 401 同样处理
      if (body.code === ResponseCode.UNAUTHORIZED) {
        await handleUnauthorized()
      }
      return Promise.reject(new Error(message))
    }

    return payload as typeof response
  },
  async (error: AxiosError<ApiResponse>) => {
    const status = error.response?.status

    if (status === ResponseCode.UNAUTHORIZED) {
      showError('登录已失效，请重新登录')
      await handleUnauthorized()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }

    // 统一抛出带后端文案的 Error，页面直接展示 error.message 即可
    const message = extractMessage(error.response?.data, error.message || '网络异常，请稍后重试')
    showError(message)
    return Promise.reject(new Error(message))
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
