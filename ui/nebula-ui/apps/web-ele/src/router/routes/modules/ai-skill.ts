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
        name: 'AiSkill',
        path: '/ai-model/skills',
        component: () => import('#/views/ai-skill/index.vue'),
        meta: {
          icon: 'lucide:sparkles',
          title: '技能',
        },
      },
    ],
  },
];

export default routes;
