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
        name: 'AiModelProfile',
        path: '/ai-model/profiles',
        component: () => import('#/views/ai-model-profile/index.vue'),
        meta: {
          icon: 'lucide:server-cog',
          title: '模型档案',
        },
      },
    ],
  },
];

export default routes;
