<script setup lang="ts">
import type { ToolbarType } from './types';

import { computed } from 'vue';

import { preferences } from '@nebula/preferences';

import {
  AuthenticationColorToggle,
  AuthenticationLayoutToggle,
  LanguageToggle,
  ThemeToggle,
} from '../widgets';

interface Props {
  toolbarList?: ToolbarType[];
}

defineOptions({
  name: 'AuthenticationToolbar',
});

const props = withDefaults(defineProps<Props>(), {
  toolbarList: () => ['color', 'language', 'layout', 'theme'],
});

const showColor = computed(() => props.toolbarList.includes('color'));
const showLayout = computed(() => props.toolbarList.includes('layout'));
const showLanguage = computed(() => props.toolbarList.includes('language'));
const showTheme = computed(() => props.toolbarList.includes('theme'));

/** 是否以悬浮玻璃面板形式呈现（多于一个工具时） */
const isPanel = computed(() => props.toolbarList.length > 1);
</script>

<template>
  <div
    :class="{
      'gap-1 rounded-full border border-border/60 bg-background/70 px-2 py-1 shadow-sm backdrop-blur-md':
        isPanel,
    }"
    class="absolute top-4 right-4 z-20 flex items-center"
  >
    <!-- 仅在中等及以上屏幕显示主题色与布局切换 -->
    <div class="hidden items-center gap-1 md:flex">
      <AuthenticationColorToggle v-if="showColor" />
      <AuthenticationLayoutToggle v-if="showLayout" />
    </div>
    <!-- 语言与主题切换始终可见 -->
    <LanguageToggle v-if="showLanguage && preferences.widget.languageToggle" />
    <ThemeToggle v-if="showTheme && preferences.widget.themeToggle" />
  </div>
</template>
