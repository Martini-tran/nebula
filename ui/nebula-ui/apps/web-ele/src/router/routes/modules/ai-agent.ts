import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:bot',
      order: 315,
      title: '智能体',
    },
    name: 'AiAgent',
    path: '/ai-agent',
    children: [
      {
        name: 'AiAgentInstance',
        path: '/ai-agent/instances',
        component: () => import('#/views/ai-agent/index.vue'),
        meta: {
          icon: 'lucide:activity',
          title: '智能体实例',
        },
      },
    ],
  },
];

export default routes;
