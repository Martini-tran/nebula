import { post } from '../utils/request'
import type {
  CaptchaCheckResult,
  CaptchaGetResult,
  CaptchaType,
  LoginRequest,
  LoginResult,
} from '../types/auth'

/**
 * 登录与验证码接口。
 *
 * scribe 不实现登录注册，直接调用 manager 现有端点（均在网关白名单内）。
 * 登录没有 mock 分支：假登录拿不到真实 token，接通后端后没有意义。
 */
const BASE = '/manager'

/** 获取滑块验证码图片。 */
export const fetchCaptcha = (captchaType: CaptchaType, clientUid: string) =>
  post<CaptchaGetResult>(`${BASE}/captcha/get`, { captchaType, clientUid })

/** 校验滑块位置，通过后返回一次性 verifyToken。 */
export const checkCaptcha = (captchaType: CaptchaType, token: string, pointJson: string) =>
  post<CaptchaCheckResult>(`${BASE}/captcha/check`, { captchaType, token, pointJson })

/** 用户名密码登录。 */
export const login = (body: LoginRequest) => post<LoginResult>(`${BASE}/auth/login`, body)

/** 退出登录。 */
export const logout = () => post<void>(`${BASE}/auth/logout`)
