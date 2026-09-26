import { createApp } from 'vue'
import './styles/tailwind.css'
import './styles/index.scss'
import './styles/components.scss'
import App from './App.vue'
import router from './router'
import { initTheme } from './utils/theme'
import { pinia } from './stores'

initTheme()

const app = createApp(App)

app.use(pinia)
app.use(router)
app.mount('#app')
