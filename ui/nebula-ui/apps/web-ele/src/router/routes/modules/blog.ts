import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:notebook-pen',
      order: 200,
      title: '鍗氬绠＄悊',
    },
    name: 'Blog',
    path: '/blog',
    children: [
      {
        name: 'BlogArticle',
        path: '/blog/article',
        component: () => import('#/views/blog/article/index.vue'),
        meta: {
          icon: 'lucide:file-text',
          title: '鏂囩珷绠＄悊',
        },
      },
      {
        name: 'BlogEssay',
        path: '/blog/essay',
        component: () => import('#/views/blog/article/index.vue'),
        meta: {
          icon: 'lucide:pen-line',
          title: '随笔管理',
        },
      },
      {
        name: 'BlogCategory',
        path: '/blog/category',
        component: () => import('#/views/blog/category/index.vue'),
        meta: {
          icon: 'lucide:folder-tree',
          title: '鍒嗙被绠＄悊',
        },
      },
      {
        name: 'BlogTag',
        path: '/blog/tag',
        component: () => import('#/views/blog/tag/index.vue'),
        meta: {
          icon: 'lucide:tag',
          title: '鏍囩绠＄悊',
        },
      },
    ],
  },
];

export default routes;
