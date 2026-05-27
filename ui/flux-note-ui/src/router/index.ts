import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import AppNavLayout from '../layouts/AppNavLayout.vue'
import ReviewsNavLayout from '../layouts/ReviewsNavLayout.vue'
import SeriesNavLayout from '../layouts/SeriesNavLayout.vue'
import SeriesDetailLayout from '../layouts/SeriesDetailLayout.vue'
import { navItems } from './nav'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

const defaultPath = navItems.find((item) => item.key === 'home')?.to ?? navItems[0]?.to ?? '/'

const childRoutes: RouteRecordRaw[] = navItems.map((item) => ({
  path: item.key,
  name: item.key,
  component: item.component,
  meta: {
    requiresLogin: item.requiresLogin,
    folder: item.folder,
    isHomeNav: item.isHomeNav,
  },
}))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppNavLayout,
      children: [
        {
          path: '',
          redirect: defaultPath,
        },
        ...childRoutes,
        {
          path: 'article',
          name: 'article',
          component: () => import('../components/article/index.vue'),
        },
      ],
    },
    {
      path: '/reviews',
      component: ReviewsNavLayout,
      children: [
        {
          path: '',
          name: 'reviews',
          component: () => import('../views/reviews/index.vue'),
        },
      ],
    },
    {
      path: '/series',
      component: SeriesNavLayout,
      children: [
        {
          path: '',
          name: 'series',
          component: () => import('../views/series/index.vue'),
        },
      ],
    },
    {
      path: '/series',
      component: SeriesDetailLayout,
      children: [
        {
          path: ':slug',
          name: 'series-detail',
          component: () => import('../views/series-detail/index.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: defaultPath,
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)

  if (to.meta.requiresLogin && !authStore.isLoggedIn) {
    return defaultPath
  }

  return true
})

export default router
