import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:notebook-pen',
      order: 200,
      title: '博客管理',
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
          title: '文章管理',
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
          title: '分类管理',
        },
      },
      {
        name: 'BlogTag',
        path: '/blog/tag',
        component: () => import('#/views/blog/tag/index.vue'),
        meta: {
          icon: 'lucide:tag',
          title: '标签管理',
        },
      },
      {
        name: 'BlogSeries',
        path: '/blog/series',
        component: () => import('#/views/blog/series/index.vue'),
        meta: {
          icon: 'lucide:layers',
          title: '系列管理',
        },
      },
      {
        name: 'BlogSeriesCatalog',
        path: '/blog/series/:id/catalog',
        component: () => import('#/views/blog/series/catalog.vue'),
        meta: {
          activePath: '/blog/series',
          hideInMenu: true,
          icon: 'lucide:list-tree',
          title: '系列目录',
        },
      },
    ],
  },
];

export default routes;
