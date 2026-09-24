import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录：作品数据按账号隔离，未登录一律先去登录页 */
    requiresAuth?: boolean
  }
}

/**
 * 两套外壳：
 * - DefaultLayout：带页头页脚的常规页面。
 * - 写作台 /editor 与创作对话 /chat 独占全屏，不套外壳，避免页头挤占正文空间。
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
      component: () => import('../layouts/DefaultLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/home/index.vue'),
        },
        {
          path: 'works',
          name: 'works',
          component: () => import('../views/works/index.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'works/:id',
          name: 'work-detail',
          component: () => import('../views/works/detail.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('../views/login/index.vue'),
        },
        {
          path: 'lore',
          name: 'lore',
          component: () => import('../views/lore/index.vue'),
        },
        {
          path: 'discover',
          name: 'discover',
          component: () => import('../views/discover/index.vue'),
        },
      ],
    },
    {
      // 创作对话同样独占全屏（设计文档 7.6）；dialogId 为空即「新对话」草稿
      path: '/chat/:dialogId?',
      name: 'chat',
      component: () => import('../views/chat/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      // 早期入口的兼容跳转，保留 ?q 等参数
      path: '/ai',
      redirect: (to) => ({ name: 'chat', query: to.query }),
    },
    {
      path: '/editor/:workId/:chapterId?',
      name: 'editor',
      component: () => import('../views/editor/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authStore.isLoggedIn) {
    return { name: 'works' }
  }
  return true
})

export default router
