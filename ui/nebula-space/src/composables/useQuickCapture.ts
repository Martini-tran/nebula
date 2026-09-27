import { reactive } from 'vue'

export type CaptureMode = 'note' | 'task' | 'bookmark' | 'meeting' | 'ledger'

/** 全局快速记录浮层的开关：任何页面 Ctrl+Shift+Space 或页头「+」呼出 */
export const quickCapture = reactive({
  open: false,
  mode: 'note' as CaptureMode,
  show(mode: CaptureMode = 'note') {
    this.mode = mode
    this.open = true
  },
  hide() {
    this.open = false
  },
})
