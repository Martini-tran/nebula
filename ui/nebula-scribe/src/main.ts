import { createApp } from 'vue'
import './styles/tailwind.css'
import './styles/index.scss'
import './styles/components.scss'
// Element Plus X 的 AI 组件内部用到 Element Plus 组件，但不自带其样式，需全局引一次；
// 桥接文件紧随其后，把 --el-* 指向 scribe 设计令牌，跟随 light/dark/paper 主题
import 'element-plus/dist/index.css'
import './styles/element-bridge.scss'
import '@imengyu/vue3-context-menu/lib/vue3-context-menu.css'
import App from './App.vue'
import router from './router'
import { initTheme } from './utils/theme'
import { pinia } from './stores'
import ContextMenu from '@imengyu/vue3-context-menu'

initTheme()

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ContextMenu)
app.mount('#app')
