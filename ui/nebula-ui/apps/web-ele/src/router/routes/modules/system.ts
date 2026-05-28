import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 100,
      title: '系统管理',
    },
    name: 'System',
    path: '/system',
    children: [
      {
        name: 'SystemUser',
        path: '/system/user',
        component: () => import('#/views/system/user/index.vue'),
        meta: {
          icon: 'lucide:users',
          title: '用户管理',
        },
      },
      {
        name: 'SystemFile',
        path: '/system/file',
        component: () => import('#/views/system/file/index.vue'),
        meta: {
          icon: 'lucide:files',
          title: '文件管理',
        },
      },
    ],
  },
];

export default routes;
