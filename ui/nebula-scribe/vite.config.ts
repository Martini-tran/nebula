import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue(), tailwindcss()],
    server: {
      port: 28259,
      host: true,
      proxy: {
        // 网关默认端口 19000（见 nebula-service-gateway/application.yml），
        // 经 /scribe/** 路由到写作服务。可用 VITE_PROXY_TARGET 覆盖。
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:19000',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
    build: {
      terserOptions: {},
    },
  }
})
