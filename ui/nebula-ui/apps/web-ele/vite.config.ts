import { defineConfig } from '@nebula/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
      server: {
        proxy: {
          // 前端 VITE_GLOB_API_URL=/api，统一打到 nebula-service-gateway:9000
          // 由 gateway 路由到 auth(:9001) / system(:9002) / 验证码(/captcha/**) 等下游
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            target: 'http://localhost:19000',
            ws: true,
          },
        },
      },
    },
  };
});






