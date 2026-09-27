import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'
import { useSettingsStore } from '../stores/settings'
import { MODULES, defaultHomePath, type ModuleKey } from '../config/modules'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录：个人空间的数据按账号隔离，未登录一律先去登录页 */
    requiresAuth?: boolean
    /** 所属模块，占位页据此展示说明 */
    module?: ModuleKey
  }
}

/** 已实现模块的视图；未列出的模块路由指向占位页 */
const MODULE_VIEWS: Partial<Record<ModuleKey, RouteRecordRaw['component']>> = {
  today: () => import('../views/today/index.vue'),
  bookmarks: () => import('../views/bookmarks/index.vue'),
  notes: () => import('../views/notes/index.vue'),
  tasks: () => import('../views/tasks/index.vue'),
  meetings: () => import('../views/meetings/index.vue'),
  habits: () => import('../views/habits/index.vue'),
  calendar: () => import('../views/calendar/index.vue'),
  reading: () => import('../views/reading/index.vue'),
  files: () => import('../views/files/index.vue'),
  ledger: () => import('../views/ledger/index.vue'),
  goals: () => import('../views/goals/index.vue'),
  people: () => import('../views/people/index.vue'),
  share: () => import('../views/share/index.vue'),
}

const moduleRoutes: RouteRecordRaw[] = MODULES.map((m) => ({
  path: m.path.slice(1),
  name: m.key,
  component: MODULE_VIEWS[m.key] ?? (() => import('../views/placeholder/index.vue')),
  meta: { requiresAuth: true, module: m.key },
}))

/**
 * 两套外壳：
 * - AppLayout：带模块导航的工作区，所有模块页面。
 * - BlankLayout：无导航，登录页与无权限页。
 */
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path !== from.path) return { top: 0 }
    return undefined
  },
  routes: [
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: () => defaultHomePath() },
        ...moduleRoutes,
        {
          path: 'focus',
          name: 'focus',
          component: () => import('../views/focus/index.vue'),
          meta: { requiresAuth: true, module: 'tasks' },
        },
        {
          path: 'review',
          name: 'review',
          component: () => import('../views/review/index.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'review/report',
          name: 'review-report',
          component: () => import('../views/review/report.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/settings/index.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'reading/highlights',
          name: 'reading-highlights',
          component: () => import('../views/reading/highlights.vue'),
          meta: { requiresAuth: true, module: 'reading' },
        },
        {
          path: 'reading/:id',
          name: 'reader',
          component: () => import('../views/reading/reader.vue'),
          meta: { requiresAuth: true, module: 'reading' },
        },
        {
          path: 'meetings/:id',
          name: 'meeting',
          component: () => import('../views/meetings/detail.vue'),
          meta: { requiresAuth: true, module: 'meetings' },
        },
        {
          path: 'notes/:id',
          name: 'note-editor',
          component: () => import('../views/notes/editor.vue'),
          meta: { requiresAuth: true, module: 'notes' },
        },
        {
          path: 'bookmarks/organize',
          name: 'bookmarks-organize',
          component: () => import('../views/bookmarks/organize/index.vue'),
          meta: { requiresAuth: true, module: 'bookmarks' },
        },
      ],
    },
    {
      path: '/',
      component: () => import('../layouts/BlankLayout.vue'),
      children: [
        { path: 'login', name: 'login', component: () => import('../views/login/index.vue') },
        // 文件分享下载页：拿到链接的人不需要登录
        { path: 's/:code', name: 'share-download', component: () => import('../views/files/shared.vue') },
        // 公开主页：不需要登录
        { path: '@:handle', name: 'public-profile', component: () => import('../views/share/public.vue') },
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('../views/error/forbidden.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authStore.isLoggedIn) {
    return defaultHomePath()
  }
  // 在设置里关掉的模块：数据还在，只是不再进入
  if (to.meta.module && !useSettingsStore(pinia).isEnabled(to.meta.module)) {
    return defaultHomePath()
  }
  return true
})

export default router
