import { defineConfig } from 'vitepress'

// 文档站部署在主站的 /docs/ 子路径下（主站导航「文档」即指向此处）。
export default defineConfig({
  base: '/docs/',
  // 直接打包进主站构建产物：dist/docs（与 SPA 同一份 dist 一起部署）
  outDir: '../dist/docs',
  lang: 'zh-CN',
  // 文档开发服务器固定端口，供主站 dev 代理 /docs 使用
  vite: {
    server: { port: 5174 },
  },
  title: 'orccode',
  description: 'orccode 文档 —— Windows 快速启动器与插件开发指南',
  // /versions、/ 等是主站（SPA）路由，不属于文档页，跳过死链检查
  ignoreDeadLinks: ['/versions'],
  themeConfig: {
    nav: [
      { text: '简介', link: '/guide/' },
      { text: '插件开发', link: '/guide/plugin-development' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [
            { text: '简介', link: '/guide/' },
            { text: '插件开发文档', link: '/guide/plugin-development' },
          ],
        },
      ],
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/Martini-tran/forge' },
      {
        icon: {
          svg: '<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Gitee</title><path d="M11.984 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 11.984 0zM6.92 6.851h10.81a.6.6 0 0 1 .6.6v1.5a.6.6 0 0 1-.6.6H8.42a.6.6 0 0 0-.6.6v4.95a.6.6 0 0 0 .6.6h5.851a.6.6 0 0 0 .6-.6v-.75a.6.6 0 0 0-.6-.6h-3.301a.6.6 0 0 1-.6-.6v-1.5a.6.6 0 0 1 .6-.6h6.001a.6.6 0 0 1 .6.6v4.05a3.6 3.6 0 0 1-3.6 3.6H6.92a.6.6 0 0 1-.6-.6V7.451a.6.6 0 0 1 .6-.6z"/></svg>',
        },
        link: 'https://gitee.com/forwardable/forge',
      },
    ],
    footer: {
      message: 'PolyForm Noncommercial License 1.0.0（仅限非商业用途）',
      copyright: '© 2026 xiangqainlu · orccode',
    },
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
    outline: { label: '本页目录' },
    returnToTopLabel: '回到顶部',
  },
})
