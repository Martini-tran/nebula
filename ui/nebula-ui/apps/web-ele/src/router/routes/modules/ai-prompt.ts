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
        name: 'AiPrompt',
        path: '/ai-model/prompts',
        component: () => import('#/views/ai-prompt/index.vue'),
        meta: {
          icon: 'lucide:message-square-text',
          title: '提示词',
        },
      },
    ],
  },
];

export default routes;
