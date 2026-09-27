import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'
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
  return true
})

export default router
