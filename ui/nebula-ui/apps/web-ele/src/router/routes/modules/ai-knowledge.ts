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
        name: 'AiKnowledge',
        path: '/ai-model/knowledge',
        component: () => import('#/views/ai-knowledge/index.vue'),
        meta: {
          icon: 'lucide:book-open-text',
          title: '知识库',
        },
      },
    ],
  },
];

export default routes;
