<script lang="ts" setup>
import type { NotificationItem } from '@nebula/layouts';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@nebula/common-ui';
import { useWatermark } from '@nebula/hooks';
import {
  DualSidebarLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@nebula/layouts';
import { preferences, usePreferences } from '@nebula/preferences';
import { useAccessStore, useUserStore } from '@nebula/stores';

import { layoutTheme } from '#/preferences';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

import NebulaBrand from './components/NebulaBrand.vue';
import { buildNavigationGroups } from './navigation';

const notifications = ref<NotificationItem[]>([]);

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const navigationGroups = computed(() =>
  buildNavigationGroups(accessStore.accessMenus),
);
const { destroyWatermark, updateWatermark } = useWatermark();
const { isDark } = usePreferences();
const showDot = computed(() =>
  notifications.value.some((item) => !item.isRead),
);

const menus = computed(() => []);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

async function handleLogout() {
  await authStore.logout(false);
}

function handleNoticeClear() {
  notifications.value = [];
}

function markRead(id: number | string) {
  const item = notifications.value.find((item) => item.id === id);
  if (item) {
    item.isRead = true;
  }
}

function remove(id: number | string) {
  notifications.value = notifications.value.filter((item) => item.id !== id);
}

function handleMakeAll() {
  notifications.value.forEach((item) => (item.isRead = true));
}

const viewAll = () => {};

const handleClick = (item: NotificationItem) => {
  // 如果通知项有链接，点击时跳转
  if (item.link) {
    navigateTo(item.link, item.query, item.state);
  }
};

function navigateTo(
  link: string,
  query?: Record<string, any>,
  state?: Record<string, any>,
) {
  if (link.startsWith('http://') || link.startsWith('https://')) {
    // 外部链接，在新标签页打开
    window.open(link, '_blank');
  } else {
    // 内部路由链接，支持 query 参数和 state
    router.push({
      path: link,
      query: query || {},
      state,
    });
  }
}

watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
    isDark: isDark.value,
  }),
  async ({ enable, content, isDark: isDarkValue }) => {
    if (enable) {
      const watermarkColor = isDarkValue
        ? 'rgba(255, 255, 255, 0.12)'
        : 'rgba(0, 0, 0, 0.12)';

      await updateWatermark({
        advancedStyle: {
          colorStops: [
            {
              color: watermarkColor,
              offset: 0,
            },
            {
              color: watermarkColor,
              offset: 1,
            },
          ],
          type: 'linear',
        },
        content:
          content ||
          `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <DualSidebarLayout
    :groups="navigationGroups"
    :style="layoutTheme"
    @clear-preferences-and-logout="handleLogout"
  >
    <template #rail-logo><NebulaBrand mark-only /></template>
    <template #brand><NebulaBrand /></template>
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus
        :text="userStore.userInfo?.realName"
        :description="userStore.userInfo?.username"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @read="(item) => item.id && markRead(item.id)"
        @remove="(item) => item.id && remove(item.id)"
        @make-all="handleMakeAll"
        @on-click="handleClick"
        @view-all="viewAll"
      />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </DualSidebarLayout>
</template>
