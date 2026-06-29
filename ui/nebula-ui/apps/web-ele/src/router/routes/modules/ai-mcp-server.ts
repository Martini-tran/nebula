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
        name: 'AiMcpServer',
        path: '/ai-model/mcp-servers',
        component: () => import('#/views/ai-mcp-server/index.vue'),
        meta: {
          icon: 'lucide:plug',
          title: 'MCP服务器',
        },
      },
    ],
  },
];

export default routes;
