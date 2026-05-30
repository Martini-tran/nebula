import { createApp } from 'vue'
import './styles/tailwind.css'
import './styles/index.scss'
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
