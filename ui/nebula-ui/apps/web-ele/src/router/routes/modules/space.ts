import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:bookmark',
      order: 250,
      title: '个人空间',
    },
    name: 'Space',
    path: '/space',
    children: [
      {
        name: 'SpaceBookmark',
        path: '/space/bookmark',
        component: () => import('#/views/space/bookmark/index.vue'),
        meta: {
          icon: 'lucide:bookmark-plus',
          title: '书签管理',
        },
      },
      {
        name: 'SpaceFolder',
        path: '/space/folder',
        component: () => import('#/views/space/folder/index.vue'),
        meta: {
          icon: 'lucide:folder-tree',
          title: '目录管理',
        },
      },
      {
        name: 'SpaceTag',
        path: '/space/tag',
        component: () => import('#/views/space/tag/index.vue'),
        meta: {
          icon: 'lucide:tags',
          title: '标签管理',
        },
      },
      {
        name: 'SpaceImportTask',
        path: '/space/import-task',
        component: () => import('#/views/space/import-task/index.vue'),
        meta: {
          icon: 'lucide:download',
          title: '导入任务',
        },
      },
      {
        name: 'SpaceExportTask',
        path: '/space/export-task',
        component: () => import('#/views/space/export-task/index.vue'),
        meta: {
          icon: 'lucide:upload',
          title: '导出任务',
        },
      },
    ],
  },
];

export default routes;
