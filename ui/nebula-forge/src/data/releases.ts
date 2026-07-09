import type { Release } from '../types/release'

/**
 * 静态版本数据。结构与后端 `/releases` 接口对齐（见 src/api/releases.ts），
 * 后端就绪后只需改 api 层即可切换数据源，页面无需改动。
 *
 * 下载入口指向 GitHub Releases 对应 tag 页面，用户在该页获取 NSIS 安装包。
 */
export const releases: Release[] = [
  {
    version: '1.0.0',
    date: '2026-06-12',
    channel: 'stable',
    isLatest: true,
    highlights: [
      '全局热键唤起命令面板，随手即用',
      '自动扫描已安装应用，支持拼音 / 首字母搜索',
      '插件系统首发，内置剪贴板历史插件',
    ],
    changelog: {
      added: [
        '命令面板（cmdk）核心交互与键盘导航',
        '应用扫描与启动，支持拼音、拼音首字母模糊匹配',
        '插件系统：插件发现、独立插件窗口与运行时',
        '内置「剪贴板历史」插件',
        '系统托盘、全局快捷键与设置中心',
      ],
      changed: [
        '设置中心拆分为快速启动、插件管理、系统设置、关于四个分区',
      ],
      fixed: [
        '修复部分应用图标无法解析的问题',
        '修复无边框窗口在多显示器下的定位偏移',
      ],
    },
    assets: [
      {
        platform: 'windows',
        label: 'Windows 安装包 (.exe)',
        url: 'https://github.com/Martini-tran/forge/releases/tag/release-v1.0.0',
      },
    ],
  },
]
