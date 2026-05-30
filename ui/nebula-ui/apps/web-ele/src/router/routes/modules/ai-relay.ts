import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:plug-zap',
      order: 300,
      title: 'AI中转管理',
    },
    name: 'AiRelay',
    path: '/ai-relay',
    children: [
      {
        name: 'AiRelayProvider',
        path: '/ai-relay/provider',
        component: () => import('#/views/ai-relay/provider/index.vue'),
        meta: {
          icon: 'lucide:server',
          title: '服务商管理',
        },
      },
      {
        name: 'AiRelayPackage',
        path: '/ai-relay/package',
        component: () => import('#/views/ai-relay/package/index.vue'),
        meta: {
          icon: 'lucide:package',
          title: '套餐管理',
        },
      },
      {
        name: 'AiRelayPackageType',
        path: '/ai-relay/package-type',
        component: () => import('#/views/ai-relay/package-type/index.vue'),
        meta: {
          icon: 'lucide:list-checks',
          title: '套餐类型',
        },
      },
      {
        name: 'AiRelayModel',
        path: '/ai-relay/model',
        component: () => import('#/views/ai-relay/model/index.vue'),
        meta: {
          icon: 'lucide:bot',
          title: 'AI模型',
        },
      },
      {
        name: 'AiRelayPaymentMethod',
        path: '/ai-relay/payment-method',
        component: () => import('#/views/ai-relay/payment-method/index.vue'),
        meta: {
          icon: 'lucide:credit-card',
          title: '支付方式',
        },
      },
      {
        name: 'AiRelayRecommend',
        path: '/ai-relay/recommend',
        component: () => import('#/views/ai-relay/recommend/index.vue'),
        meta: {
          icon: 'lucide:star',
          title: '推荐与测评',
        },
      },
      {
        name: 'AiRelayRecharge',
        path: '/ai-relay/recharge',
        component: () => import('#/views/ai-relay/recharge/index.vue'),
        meta: {
          icon: 'lucide:wallet',
          title: '充值记录',
        },
      },
    ],
  },
];

export default routes;
