<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@nebula/layouts';
import { preferences } from '@nebula/preferences';

import { $t } from '#/locales';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="logo"
    :logo-dark="logoDark"
    :page-description="$t('authentication.pageDesc')"
    :page-title="$t('authentication.pageTitle')"
    :toolbar="false"
  >
    <!-- 工具栏整体关闭：主题色 / 面板居中两个按钮属于个人偏好，
         与 preferences.ts 的视觉锁定冲突；语言与主题切换本就已在
         widget 中关掉，留着只会渲染一个空的玻璃胶囊。 -->
  </AuthPageLayout>
</template>






