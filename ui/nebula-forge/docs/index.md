---
layout: home

hero:
  name: orccode
  text: 文档
  tagline: Windows 快速启动器 · 插件开发指南
  actions:
    - theme: brand
      text: 插件开发文档
      link: /guide/plugin-development
    - theme: alt
      text: 简介
      link: /guide/

features:
  - title: inline 内联插件
    details: 直接向启动器的搜索结果列表贡献条目，用户输入时实时出现。
  - title: view 视图插件
    details: 提供自带 UI，运行在隔离、受限的 webview 沙箱中，通过白名单 rpc 与主进程通信。
  - title: 安全模型
    details: plugin:// 独立来源、严格 CSP、宿主推导 pluginId，插件之间互不越权。
---
