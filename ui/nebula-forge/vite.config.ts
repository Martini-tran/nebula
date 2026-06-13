import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue(), tailwindcss()],
    server: {
      port: 28257,
      host: true,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:9000',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
        // 开发态把 /docs 反向代理到 VitePress 文档开发服务器，
        // 使主站「文档」链接同源可达（需同时运行文档开发服务器）。
        '/docs': {
          target: 'http://localhost:5174',
          changeOrigin: true,
          ws: true,
        },
      },
    },
    build: {
      terserOptions: {},
    },
  }
})
