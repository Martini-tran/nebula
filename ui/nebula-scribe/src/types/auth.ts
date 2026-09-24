/**
 * 账号相关类型：scribe 不自建账号，复用 manager 的 sys_user 登录体系。
 * 字段与后端 LoginRequest / LoginResponse / 验证码接口一一对应。
 */

/** 验证码类型，目前只接滑块拼图。 */
export type CaptchaType = 'blockPuzzle'

/** 登录入参。 */
export interface LoginRequest {
  username: string
  password: string
  captchaType: CaptchaType
  /** 滑块校验通过后拿到的一次性令牌 */
  captchaVerifyToken: string
}

/** 登录返回。 */
export interface LoginResult {
  userId: number | string
  username: string
  nickname?: string | null
  tokenName: string
  tokenValue: string
}

/** 当前登录用户（落本地存储）。 */
export interface AuthUser {
  userId: number | string
  username: string
  nickname?: string | null
}

/** /captcha/get 返回（透传 aj-captcha 结构）。 */
export interface CaptchaGetResult {
  repCode: string
  repMsg?: string | null
  repData?: {
    originalImageBase64?: string
    jigsawImageBase64?: string
    /** AES 密钥，服务端开启加密时存在，用于加密坐标 */
    secretKey?: string
    token?: string
  } | null
}

/** /captcha/check 返回。 */
export interface CaptchaCheckResult {
  captchaType: CaptchaType
  verifyToken: string
  expiresInSeconds: number
}
