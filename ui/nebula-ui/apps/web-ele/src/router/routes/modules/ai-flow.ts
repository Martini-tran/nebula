import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:workflow',
      order: 310,
      title: 'AI编排',
    },
    name: 'AiFlow',
    path: '/ai-flow',
    children: [
      {
        name: 'AiFlowList',
        path: '/ai-flow/list',
        component: () => import('#/views/ai-flow/index.vue'),
        meta: {
          disableTransition: true,
          icon: 'lucide:list-tree',
          title: '流程列表',
        },
      },
      {
        name: 'AiChat',
        path: '/ai-flow/chat',
        component: () => import('#/views/ai-flow/chat/index.vue'),
        meta: {
          disableTransition: true,
          icon: 'lucide:message-circle',
          title: 'AI对话',
        },
      },
      {
        name: 'AiFlowCreate',
        path: '/ai-flow/create',
        component: () => import('#/views/ai-flow/create/index.vue'),
        meta: {
          disableTransition: true,
          hideInMenu: true,
          title: '新建流程',
          activePath: '/ai-flow/list',
        },
      },
      {
        name: 'AiFlowEditor',
        path: '/ai-flow/editor',
        component: () => import('#/views/ai-flow/editor/index.vue'),
        meta: {
          disableTransition: true,
          hideInMenu: true,
          title: '流程编辑器',
          activePath: '/ai-flow/list',
        },
      },
    ],
  },
];

export default routes;
