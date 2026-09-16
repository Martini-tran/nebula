<script lang="ts" setup>
import type { CSSProperties } from 'vue';

import type { DualSidebarGroup } from './types';

import { computed, nextTick, ref, watch, watchEffect } from 'vue';
import { useRoute } from 'vue-router';

import { useRefresh } from '@nebula/hooks';
import { PanelLeft } from '@nebula/icons';
import { $t, i18n } from '@nebula/locales';
import {
  preferences,
  updatePreferences,
  usePreferences,
} from '@nebula/preferences';
import {
  useAccessStore,
  useTabbarStore,
  useTimezoneStore,
} from '@nebula/stores';
import { mapTree } from '@nebula/utils';

import {
  SCROLL_FIXED_CLASS,
  useLayoutContentStyle,
  useLayoutFooterStyle,
  useLayoutHeaderStyle,
} from '@nebula-core/composables';
import { Menu } from '@nebula-core/menu-ui';
import { nebulaBackTop, nebulaIcon } from '@nebula-core/shadcn-ui';
import { ELEMENT_ID_MAIN_CONTENT } from '@nebula-core/shared/constants';

import { useEventListener } from '@vueuse/core';

import { CheckUpdates } from '../../widgets';
import { LayoutContent, LayoutContentSpinner } from '../content';
import { LayoutHeader } from '../header';
import { useNavigation } from '../menu/use-navigation';
import { LayoutTabbar } from '../tabbar';
import { findNavigationMenu, firstNavigationMenu } from './navigation';

defineOptions({ name: 'DualSidebarLayout' });
const props = defineProps<{ groups: DualSidebarGroup[] }>();
const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();
const route = useRoute();
const { isMobile, isDark } = usePreferences();
const accessStore = useAccessStore();
const tabbarStore = useTabbarStore();
const timezoneStore = useTimezoneStore();
const { refresh } = useRefresh();
const { navigation, willOpenedByWindow } = useNavigation();
const contentLayout = useLayoutContentStyle();
const { overlayStyle } = contentLayout;
const { setLayoutHeaderHeight } = useLayoutHeaderStyle();
const { setLayoutFooterHeight } = useLayoutFooterStyle();
const mobileOpen = ref(false);
const selectedId = ref('');
const toggleButton = ref<HTMLButtonElement>();
const secondaryElement = ref<HTMLElement>();
const lastVisited = new Map<string, string>();
const activePath = computed(() => route.meta.activePath || route.path);
const currentGroup = computed(() =>
  props.groups.find((group) => group.id === selectedId.value),
);
const darkSecondary = computed(
  () => isDark.value || preferences.theme.semiDarkSidebarSub,
);
const fullContent = computed(() => preferences.app.layout === 'full-content');
const sidebarVisible = computed(
  () =>
    !fullContent.value &&
    preferences.sidebar.enable &&
    !preferences.sidebar.hidden,
);
const secondaryVisible = computed(
  () =>
    sidebarVisible.value &&
    !!currentGroup.value &&
    (isMobile.value ? mobileOpen.value : !preferences.sidebar.extraCollapse),
);
const showHeader = computed(
  () =>
    !fullContent.value &&
    preferences.header.enable &&
    !preferences.header.hidden,
);
const showTabs = computed(
  () => !fullContent.value && preferences.tabbar.enable,
);
const translatedMenus = computed(() =>
  mapTree(currentGroup.value?.menus ?? [], (menu) => ({
    ...menu,
    name: $t(menu.name),
  })),
);
const shellStyle = computed((): CSSProperties => {
  const rail = sidebarVisible.value
    ? (isMobile.value
      ? preferences.sidebar.collapseWidth
      : preferences.sidebar.mixedWidth)
    : 0;
  const secondary =
    secondaryVisible.value && !isMobile.value ? preferences.sidebar.width : 0;
  return {
    '--dual-rail-width': `${rail}px`,
    '--dual-secondary-width': `${preferences.sidebar.width}px`,
    '--dual-offset': `${rail + secondary}px`,
    '--dual-header-height': `${preferences.header.height}px`,
    '--dual-tabs-height': `${preferences.tabbar.height}px`,
    '--dual-z-index': preferences.app.zIndex,
  };
});

watchEffect(() => {
  setLayoutHeaderHeight(
    (showHeader.value ? preferences.header.height : 0) +
      (showTabs.value ? preferences.tabbar.height : 0),
  );
  setLayoutFooterHeight(0);
});

// 路由、权限树刷新和隐藏详情页均重新定位，缓存的业务域不能绕过新的权限树。
watch(
  [() => props.groups, activePath],
  () => {
    const group = props.groups.find((item) =>
      findNavigationMenu(item.menus, activePath.value),
    );
    if (group) {
      selectedId.value = group.id;
      lastVisited.set(group.id, activePath.value);
    } else if (!currentGroup.value) {
      selectedId.value = props.groups[0]?.id ?? '';
    }
  },
  { immediate: true },
);

watch(
  () => route.fullPath,
  () => {
    closeMobileMenu();
  },
);
watch(isMobile, () => {
  mobileOpen.value = false;
});

function refreshAll() {
  tabbarStore.cachedTabs.clear();
  refresh();
}
watch(i18n.global.locale, refreshAll, { flush: 'post' });
watch(() => timezoneStore.timezone, refreshAll, { flush: 'post' });

async function selectGroup(group: DualSidebarGroup) {
  const remembered = lastVisited.get(group.id);
  const menu =
    (remembered && findNavigationMenu(group.menus, remembered)) ||
    firstNavigationMenu(group.menus);
  if (!menu) return;
  if (willOpenedByWindow(menu.path)) {
    await navigation(menu.path, menu.query);
    return;
  }
  selectedId.value = group.id;
  if (!isMobile.value) updatePreferences({ sidebar: { extraCollapse: false } });
  await navigation(menu.path, menu.query);
  if (isMobile.value) {
    mobileOpen.value = true;
    await focusSecondary();
  }
}

async function selectMenu(path: string) {
  const menu = findNavigationMenu(currentGroup.value?.menus ?? [], path);
  if (!menu) return;
  await navigation(path, menu.query);
  closeMobileMenu();
}

async function focusSecondary() {
  await nextTick();
  secondaryElement.value
    ?.querySelector<HTMLElement>('[role="menuitem"], button')
    ?.focus();
}

function closeMobileMenu() {
  if (!mobileOpen.value) return;
  mobileOpen.value = false;
  nextTick(() => toggleButton.value?.focus());
}

async function toggleSecondary() {
  if (isMobile.value) {
    if (mobileOpen.value) closeMobileMenu();
    else {
      mobileOpen.value = true;
      await focusSecondary();
    }
  } else {
    updatePreferences({
      sidebar: { extraCollapse: !preferences.sidebar.extraCollapse },
    });
  }
}

useEventListener('keydown', (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeMobileMenu();
});

function toggleSecondaryTheme() {
  updatePreferences({
    theme: { semiDarkSidebarSub: !preferences.theme.semiDarkSidebarSub },
  });
}
</script>

<template>
  <div
    class="dual-layout"
    :class="{ 'is-mobile': isMobile }"
    :style="shellStyle"
  >
    <aside v-show="sidebarVisible" class="domain-rail" aria-label="业务域导航">
      <div class="rail-logo">
        <slot name="rail-logo">
<span>{{
            preferences.app.name.slice(0, 1).toUpperCase()
          }}</span>
</slot>
      </div>
      <nav class="domain-items" aria-label="一级导航">
        <button
          v-for="group in groups"
          :key="group.id"
          type="button"
          class="domain-button"
          :class="{ active: currentGroup?.id === group.id }"
          :aria-current="currentGroup?.id === group.id ? 'true' : undefined"
          :aria-label="$t(group.name)"
          :title="$t(group.name)"
          :disabled="!firstNavigationMenu(group.menus)"
          @click="selectGroup(group)"
        >
          <nebulaIcon :icon="group.icon" fallback class="size-5" />
          <span>{{ $t(group.label) }}</span>
        </button>
      </nav>
      <div class="rail-footer">
        <span>{{ preferences.app.name }}</span>
      </div>
    </aside>

    <button
      v-if="isMobile && secondaryVisible"
      class="navigation-scrim"
      type="button"
      aria-label="关闭二级菜单"
      @click="closeMobileMenu"
    ></button>
    <aside
      ref="secondaryElement"
      class="secondary-nav"
      :class="{
        'secondary-dark': darkSecondary,
        'secondary-hidden': !secondaryVisible,
      }"
      :aria-hidden="!secondaryVisible"
      :inert="!secondaryVisible"
      aria-label="二级导航"
    >
      <div class="secondary-brand">
        <slot name="brand">
<strong>{{ preferences.app.name }}</strong>
</slot>
      </div>
      <div class="domain-title">
        <nebulaIcon
          :icon="currentGroup?.icon"
          fallback
          class="size-4"
        /><span>{{ currentGroup ? $t(currentGroup.name) : '导航' }}</span>
      </div>
      <div class="secondary-scroll">
        <Menu
          v-if="currentGroup"
          :key="currentGroup.id"
          :menus="translatedMenus"
          :accordion="preferences.navigation.accordion"
          :default-active="activePath"
          :rounded="true"
          :theme="darkSecondary ? 'dark' : 'light'"
          mode="vertical"
          scroll-to-active
          @select="selectMenu"
        />
        <p v-else class="empty-menu">暂无可访问的菜单</p>
      </div>
      <div class="secondary-footer">
        <button
          type="button"
          class="theme-switch"
          role="switch"
          :aria-checked="darkSecondary"
          :disabled="isDark"
          @click="toggleSecondaryTheme"
        >
          <span>深色二级菜单</span><span
            class="switch-track"
            :class="{ checked: darkSecondary }"
            aria-hidden="true"
            ><i></i></span>
        </button>
      </div>
    </aside>

    <section class="main-shell" :inert="isMobile && secondaryVisible">
      <header
        v-show="showHeader"
        class="dual-header"
        :class="SCROLL_FIXED_CLASS"
      >
        <button
          ref="toggleButton"
          type="button"
          class="menu-toggle"
          :aria-label="secondaryVisible ? '收起二级菜单' : '展开二级菜单'"
          :aria-expanded="secondaryVisible"
          :disabled="!currentGroup || !sidebarVisible"
          @click="toggleSecondary"
        >
          <PanelLeft class="size-[18px]" />
        </button>
        <LayoutHeader
          theme="light"
          @clear-preferences-and-logout="emit('clearPreferencesAndLogout')"
        >
          <template #breadcrumb>
<div class="dual-breadcrumb">
              <span>控制台</span><i>/</i><span v-if="currentGroup">{{ $t(currentGroup.name) }}</span><i v-if="currentGroup">/</i><strong>{{
                $t(String(route.meta.title || route.name || ''))
              }}</strong>
            </div>
</template>
          <template #user-dropdown><slot name="user-dropdown"></slot></template>
          <template #notification><slot name="notification"></slot></template>
        </LayoutHeader>
      </header>
      <div v-if="showTabs" class="dual-tabs" :class="SCROLL_FIXED_CLASS">
        <LayoutTabbar :show-icon="preferences.tabbar.showIcon" theme="light" />
      </div>
      <main
        :id="ELEMENT_ID_MAIN_CONTENT"
        :ref="contentLayout.contentElement"
        class="dual-content"
      >
        <LayoutContent />
        <LayoutContentSpinner
          v-if="preferences.transition.loading"
          :style="overlayStyle"
        />
      </main>
    </section>
    <slot name="extra"></slot>
    <CheckUpdates
      v-if="preferences.app.enableCheckUpdates"
      :check-updates-interval="preferences.app.checkUpdatesInterval"
    />
    <slot
      v-if="preferences.widget.lockScreen && accessStore.isLockScreen"
      name="lock-screen"
    ></slot>
    <nebulaBackTop />
  </div>
</template>

<style scoped>
.dual-layout {
  --rail-bg: var(--nebula-rail-background, 184 42% 14%);
  --rail-fg: var(--nebula-rail-foreground, 172 13% 61%);
  --rail-active: var(--nebula-rail-active, 137 51% 83%);
  --rail-active-fg: var(--nebula-rail-active-foreground, 170 51% 21%);
  --sub-bg: var(--nebula-secondary-background, 120 14% 99%);
  --sub-fg: var(--nebula-secondary-foreground, 184 9% 35%);
  --sub-active: var(--nebula-secondary-active, 145 33% 93%);
  --sub-border: var(--nebula-layout-border, 140 13% 91%);
  --sub-active-fg: var(--primary);

  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: hsl(var(--nebula-canvas, 140 11% 96%));
}

.domain-rail {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: calc(var(--dual-z-index) + 3);
  display: flex;
  flex-direction: column;
  width: var(--dual-rail-width);
  padding: 23px 9px 17px;
  color: hsl(var(--rail-fg));
  background: hsl(var(--rail-bg));
}

.rail-logo {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  height: 38px;
  margin-bottom: 28px;
  color: hsl(var(--rail-active));
}

.domain-items {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  scrollbar-width: none;
}

.domain-button {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 9px 3px;
  font-size: 11px;
  color: inherit;
  border-radius: 11px;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.domain-button span {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow-wrap: anywhere;
}

.domain-button:hover:not(:disabled) {
  color: hsl(var(--rail-active));
  background: hsl(var(--rail-fg) / 12%);
}

.domain-button.active {
  font-weight: 600;
  color: hsl(var(--rail-active-fg));
  background: hsl(var(--rail-active));
}

.domain-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.rail-footer {
  flex-shrink: 0;
  padding-top: 20px;
  overflow: hidden;
  font-size: 9px;
  text-align: center;
  letter-spacing: 1px;
}

.secondary-nav {
  position: fixed;
  inset: 0 auto 0 var(--dual-rail-width);
  z-index: calc(var(--dual-z-index) + 2);
  display: flex;
  flex-direction: column;
  width: var(--dual-secondary-width);
  color: hsl(var(--sub-fg));
  background: hsl(var(--sub-bg));
  border-right: 1px solid hsl(var(--sub-border));
  transition:
    transform 0.2s,
    background-color 0.2s;
}

.secondary-dark {
  --sub-bg: var(--nebula-secondary-dark-background, 184 32% 17%);
  --sub-fg: var(--nebula-secondary-dark-foreground, 176 18% 76%);
  --sub-active: var(--nebula-secondary-dark-active, 180 26% 23%);
  --sub-border: var(--nebula-secondary-dark-border, 184 25% 23%);
  --sub-active-fg: var(--rail-active);
}

.secondary-hidden {
  pointer-events: none;
  transform: translateX(-103%);
}

.secondary-brand {
  flex-shrink: 0;
  padding: 27px 24px 25px;
}

.secondary-brand strong {
  font-size: 24px;
}

.domain-title {
  display: flex;
  flex-shrink: 0;
  gap: 9px;
  align-items: center;
  padding: 13px 23px 20px;
  font-size: 13px;
  font-weight: 600;
}

.domain-title :deep(svg) {
  color: hsl(var(--sub-active-fg));
}

.secondary-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 10px 20px;
  overflow: auto;
  scrollbar-width: thin;
}

.secondary-scroll :deep(.nebula-menu) {
  --menu-font-size: 13px;
  --menu-item-height: 42px;
  --menu: var(--sub-bg);
  --menu-foreground: var(--sub-fg);
  --menu-item-active-color: hsl(var(--sub-active-fg));
  --menu-item-active-background-color: hsl(var(--sub-active));
  --menu-item-radius: 7px;
  --menu-item-margin-x: 0px;
  --menu-item-color: hsl(var(--sub-fg));
  --menu-item-hover-color: hsl(var(--sub-active-fg));
  --menu-item-hover-background-color: hsl(var(--sub-active));
  --menu-submenu-background-color: transparent;
  --menu-submenu-hover-color: hsl(var(--sub-active-fg));
  --menu-submenu-hover-background-color: hsl(var(--sub-active));
  --menu-submenu-active-color: hsl(var(--sub-active-fg));
  --menu-submenu-active-background-color: transparent;

  background: transparent;
}

.secondary-scroll :deep([role='menuitem']) {
  margin-bottom: 4px;
  color: hsl(var(--sub-fg));
  background: transparent;
  border-radius: 7px;
}

.secondary-scroll :deep([role='menuitem']:hover),
.secondary-scroll :deep([role='menuitem'].is-active) {
  color: hsl(var(--sub-active-fg));
  background: hsl(var(--sub-active));
}

.secondary-scroll :deep(.is-disabled) {
  pointer-events: none;
  opacity: 0.45;
}

.secondary-footer {
  flex-shrink: 0;
  padding: 19px 20px;
  border-top: 1px solid hsl(var(--sub-border));
}

.theme-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 11px;
}

.switch-track {
  width: 29px;
  height: 17px;
  padding: 3px;
  background: hsl(var(--sub-border));
  border-radius: 10px;
}

.switch-track i {
  display: block;
  width: 11px;
  height: 11px;
  background: hsl(var(--background));
  border-radius: 50%;
  transition: transform 0.15s;
}

.switch-track.checked {
  background: hsl(var(--primary));
}

.switch-track.checked i {
  transform: translateX(12px);
}

.main-shell {
  display: flex;
  flex-direction: column;
  width: calc(100% - var(--dual-offset));
  min-width: 0;
  height: 100%;
  margin-left: var(--dual-offset);
  transition:
    width 0.2s,
    margin-left 0.2s;
}

.dual-header {
  position: relative;
  z-index: var(--dual-z-index);
  display: flex;
  flex-shrink: 0;
  align-items: center;
  min-width: 0;
  height: var(--dual-header-height);
  padding: 0 25px;
  background: hsl(var(--background));
  border-bottom: 1px solid hsl(var(--sub-border));
}

.menu-toggle {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-right: 7px;
  color: hsl(var(--muted-foreground));
  border-radius: 6px;
}

.menu-toggle:hover {
  color: hsl(var(--primary));
  background: hsl(var(--sub-active));
}

.menu-toggle:disabled {
  cursor: default;
  opacity: 0.4;
}

.dual-breadcrumb {
  display: flex;
  gap: 11px;
  align-items: center;
  min-width: 0;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}

.dual-breadcrumb i {
  font-style: normal;
  opacity: 0.4;
}

.dual-breadcrumb strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  color: hsl(var(--foreground));
}

.dual-tabs {
  position: relative;
  z-index: calc(var(--dual-z-index) - 1);
  display: flex;
  flex-shrink: 0;
  align-items: center;
  min-width: 0;
  height: var(--dual-tabs-height);
  padding-left: 18px;
  background: hsl(var(--background));
  border-bottom: 1px solid hsl(var(--sub-border));
}

.dual-content {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: hsl(var(--nebula-canvas, 140 11% 96%));
}

.dual-tabs :deep(.tab-item) {
  margin: 0 8px;
  background: transparent;
  border: 0;
}

.dual-tabs :deep(.tab-item.is-active) {
  background: transparent;
  box-shadow: inset 0 -2px hsl(var(--primary));
}

.navigation-scrim {
  position: fixed;
  inset: 0;
  z-index: calc(var(--dual-z-index) + 1);
  background: hsl(var(--rail-bg) / 35%);
}

.empty-menu {
  padding: 20px 12px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.is-mobile .domain-rail {
  padding: 17px 6px;
}

.is-mobile .domain-items {
  gap: 6px;
}

.is-mobile .domain-button {
  min-height: 54px;
}

.is-mobile .dual-header {
  padding: 0 10px;
}

.is-mobile .dual-tabs {
  padding-left: 8px;
}

.is-mobile .secondary-nav:not(.secondary-hidden) {
  box-shadow: 12px 0 32px hsl(var(--rail-bg) / 12%);
}

@media (prefers-reduced-motion: reduce) {
  .dual-layout * {
    transition: none;
  }
}
</style>
