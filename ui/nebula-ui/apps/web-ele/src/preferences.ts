import { defineOverridesPreferences } from '@nebula/preferences';

/**
 * @description 项目配置文件（视觉规范唯一来源 / SINGLE SOURCE OF TRUTH）
 *
 * ⚠️ 本文件已锁定全站视觉样式，请勿为了「个人看着顺眼」而修改。
 *    要调整样式，先走评审、改本文件，全站统一生效，不要在页面里写局部覆盖。
 *
 * 三条硬约束：
 * 1. `app.enablePreferences: false` —— 右侧「偏好设置」抽屉已关闭，
 *    用户无法再自行改主题/圆角/布局，避免每个人一套样式。
 * 2. 合并优先级：initPreferences 内部是 `merge({}, 缓存, 本文件)`，
 *    而 merge 就是 defu（**前面的参数优先**），所以老用户 localStorage
 *    里的旧配置会**压过**这里的新值。改完本文件后必须让用户清缓存，
 *    否则不生效 —— 已通过下方 VITE_APP_NAMESPACE 版本号方案自动失效旧缓存。
 * 3. 只覆盖需要锁定的项，其余自动走 defaultPreferences。
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    // 路由由后端 /system/menu/all 下发，前端 routes/modules 仅作 fallback
    accessMode: 'backend',
    name: import.meta.env.VITE_APP_TITLE,

    // —— 锁定视觉：关闭偏好设置面板，用户不可再自行调整样式 ——
    enablePreferences: false,
    // 关闭「复制偏好设置」按钮（面板已关，这里一并收敛）
    enableCopyPreferences: false,

    // 方案 05：业务域一级栏 + 模块二级菜单。
    layout: 'sidebar-mixed-nav',
    // 内容区宽度：撑满（wide）。如需定宽改 'compact' + contentCompactWidth
    contentCompact: 'wide',
    compact: false,

    locale: 'zh-CN',
    colorGrayMode: false,
    colorWeakMode: false,
    watermark: false,
  },

  // —— Logo：public/ 下只有 logo.png，默认值 '/logo-dark.png' 会 404 ——
  logo: {
    enable: true,
    fit: 'contain',
    source: '/logo.png',
    sourceDark: '/logo.png',
  },

  // —— 主题：颜色 / 圆角 / 字号，全站统一 ——
  theme: {
    // 亮色模式。改成 'dark' 或 'auto' 前请确认所有页面在暗色下可读
    mode: 'light',
    builtinType: 'default',
    // 圆角：单位 rem，写成字符串。'0.5' => --radius: 0.5rem
    // 可选参考：'0'(直角) '0.25'(小) '0.5'(默认) '0.75' '1'(大圆角)
    radius: '0.5',
    fontSize: 16,
    colorPrimary: 'hsl(170 73% 29%)',
    colorSuccess: 'hsl(144 57% 58%)',
    colorWarning: 'hsl(42 84% 61%)',
    colorDestructive: 'hsl(348 100% 61%)',
    // 一级栏采用深绿；二级栏默认浅色，可单独切换配色。
    semiDarkHeader: false,
    semiDarkSidebar: true,
    semiDarkSidebarSub: false,
  },

  // —— 左侧菜单 ——
  sidebar: {
    enable: true,
    hidden: false,
    // 二级菜单 / 桌面一级栏 / 窄屏一级栏宽度。
    width: 222,
    mixedWidth: 76,
    collapseWidth: 64,
    collapsed: false,
    // 折叠时不显示标题文字
    collapsedShowTitle: false,
    // 保留折叠按钮，但禁止拖拽改宽度（防止每人一个宽度）
    collapsedButton: true,
    fixedButton: false,
    draggable: false,
    // 悬停自动展开：关闭，避免误触抖动
    expandOnHover: false,
    autoActivateChild: true,
    extraCollapse: false,
    extraCollapsedWidth: 0,
  },

  // —— 菜单项样式：圆角风格 + 手风琴 ——
  navigation: {
    // 'rounded' 圆角 | 'plain' 直角。全站统一 rounded
    styleType: 'rounded',
    // 同级菜单同时只展开一个
    accordion: true,
    split: true,
  },

  // —— 顶栏 ——
  header: {
    enable: true,
    hidden: false,
    height: 62,
    mode: 'fixed',
    menuAlign: 'start',
  },

  // —— 多页签 ——
  tabbar: {
    enable: true,
    height: 42,
    // 'chrome' 谷歌风 | 'card' 卡片 | 'plain' 朴素 | 'brisk' 轻快
    styleType: 'plain',
    showIcon: true,
    keepAlive: true,
    persist: true,
    draggable: true,
    // 禁用滚轮切换标签，避免滚动页面时误切
    wheelable: false,
    middleClickToClose: false,
    maxCount: 0,
    showMaximize: true,
    showMore: true,
    showRefresh: true,
    visitHistory: true,
  },

  // —— 面包屑 ——
  breadcrumb: {
    enable: true,
    showIcon: true,
    showHome: false,
    hideOnlyOne: false,
    styleType: 'normal',
  },

  // —— 右上角功能区：只保留后台真正需要的 ——
  widget: {
    // 已关闭偏好面板，主题切换一并关闭，保证全站同一主题
    themeToggle: false,
    // 单语言项目，隐藏语言切换
    languageToggle: false,
    globalSearch: true,
    fullscreen: true,
    refresh: true,
    sidebarToggle: true,
    notification: true,
    lockScreen: false,
    timezone: false,
  },

  // —— 过渡动画 ——
  transition: {
    enable: true,
    name: 'fade-slide',
    loading: true,
    progress: true,
  },

  // —— 页脚：后台不需要 ——
  footer: {
    enable: false,
    fixed: false,
  },

  copyright: {
    enable: false,
    settingShow: false,
  },
});

/** 方案 05 的外壳设计变量；在布局根节点生效，不覆盖业务页面的组件结构。 */
export const layoutTheme = {
  '--nebula-rail-background': '184 42% 14%',
  '--nebula-rail-foreground': '172 13% 61%',
  '--nebula-rail-active': '137 51% 83%',
  '--nebula-rail-active-foreground': '170 51% 21%',
  '--nebula-secondary-background': '120 14% 99%',
  '--nebula-secondary-foreground': '184 9% 35%',
  '--nebula-secondary-active': '145 33% 93%',
  '--nebula-secondary-dark-background': '184 32% 17%',
  '--nebula-secondary-dark-foreground': '176 18% 76%',
  '--nebula-secondary-dark-active': '180 26% 23%',
  '--nebula-secondary-dark-border': '184 25% 23%',
  '--nebula-layout-border': '140 13% 91%',
  '--nebula-canvas': '140 11% 96%',
};

/** .env 不纳入版本控制；代码中的版本后缀保证所有部署都迁移旧布局缓存。 */
export const LAYOUT_PREFERENCES_VERSION = 'dual-sidebar-v2';
