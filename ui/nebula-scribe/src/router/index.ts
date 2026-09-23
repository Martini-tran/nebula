import { createRouter, createWebHistory } from 'vue-router'

/**
 * 两套外壳：
 * - DefaultLayout：带页头页脚的常规页面。
 * - 写作台 /editor 独占全屏，不套外壳，避免页头挤占正文空间。
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
        },
        {
          path: 'works/:id',
          name: 'work-detail',
          component: () => import('../views/works/detail.vue'),
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
      path: '/editor/:workId/:chapterId?',
      name: 'editor',
      component: () => import('../views/editor/index.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

export default router
