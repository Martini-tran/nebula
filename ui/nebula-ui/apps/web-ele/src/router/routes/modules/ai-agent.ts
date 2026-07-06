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
        name: 'AiAgentDefinition',
        path: '/ai-agent/agents',
        component: () => import('#/views/ai-agent/agents.vue'),
        meta: {
          icon: 'lucide:bot',
          title: '智能体定义',
        },
      },
      {
        name: 'AiAgentInstance',
        path: '/ai-agent/instances',
        component: () => import('#/views/ai-agent/index.vue'),
        meta: {
          icon: 'lucide:activity',
          title: '智能体实例',
        },
      },
      {
        name: 'AiAgentReplay',
        path: '/ai-agent/replay',
        component: () => import('#/views/ai-agent/replay/index.vue'),
        meta: {
          activePath: '/ai-agent/instances',
          hideInMenu: true,
          icon: 'lucide:play-circle',
          title: '实例回放',
        },
      },
    ],
  },
];

export default routes;
