import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  /** 危险操作用红色按钮 */
  danger?: boolean
}

const state = reactive({
  open: false,
  options: { title: '' } as ConfirmOptions,
  resolve: null as ((ok: boolean) => void) | null,
})

/**
 * 替代 window.confirm 的确认框：await confirm({...}) 返回 true / false。
 * 渲染由 App.vue 里的 ConfirmHost 负责。
 */
export const confirm = (options: ConfirmOptions) =>
  new Promise<boolean>((resolve) => {
    // 上一个还没关就当取消处理，避免悬空的 Promise
    state.resolve?.(false)
    state.options = options
    state.resolve = resolve
    state.open = true
  })

export const settleConfirm = (ok: boolean) => {
  state.resolve?.(ok)
  state.resolve = null
  state.open = false
}

export const confirmState = state
