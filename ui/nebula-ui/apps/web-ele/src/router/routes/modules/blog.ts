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
        name: 'BlogCategory',
        path: '/blog/category',
        component: () => import('#/views/blog/category/index.vue'),
        meta: {
          icon: 'lucide:folder-tree',
          title: '分类管理',
        },
      },
    ],
  },
];

export default routes;
