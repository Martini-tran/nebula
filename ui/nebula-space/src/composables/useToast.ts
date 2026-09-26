import { reactive } from 'vue'

export type ToastType = 'ok' | 'error' | 'info'

export interface Toast {
  id: number
  type: ToastType
  text: string
  /** 可选操作按钮，如「撤销」 */
  action?: { label: string; run: () => void }
}

const state = reactive({ items: [] as Toast[] })
let seq = 0

const dismiss = (id: number) => {
  const index = state.items.findIndex((item) => item.id === id)
  if (index >= 0) state.items.splice(index, 1)
}

const show = (type: ToastType, text: string, options: { action?: Toast['action']; duration?: number } = {}) => {
  const id = ++seq
  state.items.push({ id, type, text, action: options.action })
  // 同屏最多 3 条，旧的先走
  if (state.items.length > 3) state.items.shift()
  setTimeout(() => dismiss(id), options.duration ?? (options.action ? 5000 : 3200))
  return id
}

/**
 * 全局轻提示。页面里调用 toast.ok('已保存') 即可，渲染由 App.vue 里的 ToastHost 负责。
 */
export const toast = {
  state,
  dismiss,
  ok: (text: string, options?: { action?: Toast['action']; duration?: number }) => show('ok', text, options),
  error: (text: string) => show('error', text),
  info: (text: string) => show('info', text),
}

/** 把未知异常转成可展示的文案。 */
export const errorText = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback
