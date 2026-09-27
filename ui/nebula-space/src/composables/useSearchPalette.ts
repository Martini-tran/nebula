import { reactive } from 'vue'

/** 全局搜索（Ctrl+K）的开关；输入以「>」开头时是命令模式 */
export const searchPalette = reactive({
  open: false,
  initial: '',
  show(initial = '') {
    this.initial = initial
    this.open = true
  },
  hide() {
    this.open = false
  },
})
