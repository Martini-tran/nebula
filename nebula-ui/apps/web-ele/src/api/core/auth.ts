import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 验证码类型，与后端 sys_config 支持的取值保持一致 */
  export type CaptchaType = 'blockPuzzle' | 'clickWord';

  /** 登录入参，匹配后端 LoginRequest */
  export interface LoginParams {
    captchaType: CaptchaType;
    captchaVerifyToken: string;
    password: string;
    username: string;
  }

  /** 登录返回，匹配后端 LoginResponse */
  export interface LoginResult {
    nickname: string;
    tokenName: string;
    tokenValue: string;
    userId: number | string;
    username: string;
  }

  /** 注册入参，匹配后端 RegisterRequest */
  export interface RegisterParams {
    captchaType: CaptchaType;
    captchaVerifyToken: string;
    email?: string;
    mobile?: string;
    nickname?: string;
    password: string;
    username: string;
  }

  /** 注册返回，匹配后端 RegisterResponse */
  export interface RegisterResult {
    nickname: string;
    userId: number | string;
    username: string;
  }

  /** /auth/session 返回，匹配后端 SessionResponse */
  export interface SessionResult {
    isLogin: boolean;
    loginId: null | number | string;
    tokenTimeout: null | number;
  }

  /** /captcha/get 入参 */
  export interface CaptchaGetParams {
    browserInfo?: string;
    captchaType?: CaptchaType;
    clientUid?: string;
  }

  /** /captcha/get 返回（透传 aj-captcha 结构） */
  export interface CaptchaGetResult {
    repCode: string;
    repData: {
      jigsawImageBase64?: string;
      originalImageBase64?: string;
      /** AES 密钥，开启 aes-status 时存在；前端用其加密 pointJson */
      secretKey?: string;
      token: string;
      wordList?: string[];
      y?: number;
      [k: string]: unknown;
    };
    repMsg: string;
  }

  /** /captcha/check 入参 */
  export interface CaptchaCheckParams {
    captchaType: CaptchaType;
    pointJson: string;
    token: string;
  }

  /** /captcha/check 返回 */
  export interface CaptchaCheckResult {
    captchaType: CaptchaType;
    expiresInSeconds: number;
    verifyToken: string;
  }
}

/** 登录 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/manager/auth/login', data);
}

/** 注册 */
export async function registerApi(data: AuthApi.RegisterParams) {
  return requestClient.post<AuthApi.RegisterResult>('/manager/auth/register', data);
}

/** 当前会话状态 */
export async function getSessionApi() {
  return requestClient.get<AuthApi.SessionResult>('/manager/auth/session');
}

/** 退出登录（baseRequestClient 不会被默认拦截器跳出） */
export async function logoutApi() {
  return baseRequestClient.post('/manager/auth/logout');
}

/** 获取验证码图片（auth 服务挂在 /auth/captcha/**） */
export async function getCaptchaApi(data: AuthApi.CaptchaGetParams) {
  return requestClient.post<AuthApi.CaptchaGetResult>(
    '/manager/auth/captcha/get',
    data,
  );
}

/** 校验验证码，拿到一次性 verifyToken */
export async function checkCaptchaApi(data: AuthApi.CaptchaCheckParams) {
  return requestClient.post<AuthApi.CaptchaCheckResult>(
    '/manager/auth/captcha/check',
    data,
  );
}
