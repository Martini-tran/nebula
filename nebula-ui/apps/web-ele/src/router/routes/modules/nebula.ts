import type { RouteRecordRaw } from 'vue-router';

import {
  nebula_ANT_PREVIEW_URL,
  nebula_ANTDV_NEXT_PREVIEW_URL,
  nebula_DOC_URL,
  nebula_GITHUB_URL,
  nebula_LOGO_URL,
  nebula_NAIVE_PREVIEW_URL,
  nebula_TD_PREVIEW_URL,
} from '@nebula/constants';
import {
  SvgAntdvLogoIcon,
  SvgAntdvNextLogoIcon,
  SvgTDesignIcon,
} from '@nebula/icons';

import { IFrameView } from '#/layouts';
import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      badgeType: 'dot',
      icon: nebula_LOGO_URL,
      order: 9998,
      title: $t('demos.nebula.title'),
    },
    name: 'nebulaProject',
    path: '/nebula-admin',
    children: [
      {
        name: 'nebulaDocument',
        path: '/nebula-admin/document',
        component: IFrameView,
        meta: {
          icon: 'lucide:book-open-text',
          link: nebula_DOC_URL,
          title: $t('demos.nebula.document'),
        },
      },
      {
        name: 'nebulaGithub',
        path: '/nebula-admin/github',
        component: IFrameView,
        meta: {
          icon: 'mdi:github',
          link: nebula_GITHUB_URL,
          title: 'Github',
        },
      },
      {
        name: 'nebulaNaive',
        path: '/nebula-admin/naive',
        component: IFrameView,
        meta: {
          badgeType: 'dot',
          icon: 'logos:naiveui',
          link: nebula_NAIVE_PREVIEW_URL,
          title: $t('demos.nebula.naive-ui'),
        },
      },
      {
        name: 'nebulaAntd',
        path: '/nebula-admin/antd',
        component: IFrameView,
        meta: {
          badgeType: 'dot',
          icon: SvgAntdvLogoIcon,
          link: nebula_ANT_PREVIEW_URL,
          title: $t('demos.nebula.antdv'),
        },
      },
      {
        name: 'nebulaAntdVNext',
        path: '/nebula-admin/antdv-next',
        component: IFrameView,
        meta: {
          badgeType: 'dot',
          icon: SvgAntdvNextLogoIcon,
          link: nebula_ANTDV_NEXT_PREVIEW_URL,
          title: $t('demos.nebula.antdv-next'),
        },
      },
      {
        name: 'nebulaTDesign',
        path: '/nebula-admin/tdesign',
        component: IFrameView,
        meta: {
          badgeType: 'dot',
          icon: SvgTDesignIcon,
          link: nebula_TD_PREVIEW_URL,
          title: $t('demos.nebula.tdesign'),
        },
      },
    ],
  },
  {
    name: 'nebulaAbout',
    path: '/nebula-admin/about',
    component: () => import('#/views/_core/about/index.vue'),
    meta: {
      icon: 'lucide:copyright',
      title: $t('demos.nebula.about'),
      order: 9999,
    },
  },
  {
    name: 'Profile',
    path: '/profile',
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      icon: 'lucide:user',
      hideInMenu: true,
      title: $t('page.auth.profile'),
    },
  },
];

export default routes;






