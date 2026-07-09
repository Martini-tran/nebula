import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:bot',
      order: 305,
      title: 'AI模型',
    },
    name: 'AiModel',
    path: '/ai-model',
    children: [
      {
        name: 'AiTool',
        path: '/ai-model/tools',
        component: () => import('#/views/ai-tool/index.vue'),
        meta: {
          icon: 'lucide:wrench',
          title: 'AI工具',
        },
      },
    ],
  },
];

export default routes;
