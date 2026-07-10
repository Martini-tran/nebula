import { fileURLToPath } from 'node:url';

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
      optimizeDeps: {
        // src/lib/monaco.ts 里的 `?worker` 后缀导入必须由 Vite 的 worker 插件处理。
        // 若让 dep optimizer 预构建 monaco，它会把 `xxx.worker.js?worker` 当成
        // 磁盘路径去读，Windows 下路径不允许含 `?`，直接 os error 123 起不来 dev。
        //
        // monaco 是纯 ESM，无需预构建。
        exclude: ['monaco-editor'],
      },
      resolve: {
        alias: [
          {
            // monaco 默认入口（esm/vs/editor/editor.main）会拉进 82 门语言的
            // tokenizer 与四套语言服务，单 chunk 3.7 MB。ai-flow 只编辑 JSON，
            // 重定向到 src/lib/monaco.ts：编辑器核心 + JSON 语言 + 对应 worker。
            // JsonField 里的 `from 'monaco-editor'` 因此自动收敛到精简入口。
            //
            // 必须用正则精确匹配裸包名：字符串 alias 是前缀匹配，会连带改写
            // src/lib/monaco.ts 自己的 `monaco-editor/esm/vs/...` 子路径导入，
            // 造成模块自引用死循环。
            find: /^monaco-editor$/,
            replacement: fileURLToPath(
              new URL('./src/lib/monaco.ts', import.meta.url),
            ),
          },
        ],
      },
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






