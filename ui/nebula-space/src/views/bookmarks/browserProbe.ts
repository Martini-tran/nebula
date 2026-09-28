/**
 * 用用户自己的浏览器探一下网址连不连得上：服务器那边查不清（超时、被墙、连接被重置）时再试一次，
 * 走的是用户自己的网络和代理。
 *
 * no-cors 请求拿不到状态码，只分得出「有响应」和「网络错误」。实测约一成能打开的站点也会报网络错误
 * （响应头 Cross-Origin-Resource-Policy、Cloudflare 人机验证等会让浏览器拦下），
 * 所以「连上了」是可靠结论，「连不上」只能当嫌疑，交给用户确认。
 */

/** 内网、本机地址：公网页面去请求会被浏览器拦下（私有网络访问限制），也不该替服务器去探 */
const PRIVATE_HOST = /^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.|\[(::1|f[cd][0-9a-f]*:.*)\]$)|\.(local|lan|internal)$/i

export const canProbeInBrowser = (url: string) => {
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return false
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return false
  // https 页面里请求 http 地址属于混合内容，会被直接拦下
  if (parsed.protocol === 'http:' && window.location.protocol === 'https:') return false
  return !PRIVATE_HOST.test(parsed.hostname)
}

/** 连得上（拿到了任意响应）返回 true；网络错误、超时返回 false */
export const probeInBrowser = async (url: string, timeoutMs = 15000): Promise<boolean> => {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  try {
    // 不带 Cookie、不带来源：对目标站点来说就是一次匿名访问
    await fetch(url, { mode: 'no-cors', credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer', signal: ctrl.signal })
    return true
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}
