<script setup lang="ts">
import AppHeader from '../components/layout/AppHeader.vue'
import QuickCapture from '../components/QuickCapture.vue'
import FocusHost from '../components/focus/FocusHost.vue'
import SearchPalette from '../components/search/SearchPalette.vue'
import { useSettingsStore } from '../stores/settings'
import { useReminders } from '../composables/useReminders'

// 偏好先用本机缓存渲染，再从服务端取一次最新的
useSettingsStore().load()
useReminders()
</script>

<template>
  <div class="layout">
    <AppHeader />
    <main class="layout__main">
      <router-view v-slot="{ Component, route }">
        <transition name="view-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>
    <QuickCapture />
    <FocusHost />
    <SearchPalette />
  </div>
</template>

<style scoped lang="scss">
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.layout__main {
  flex: 1;
}

.view-fade-enter-active {
  transition:
    opacity var(--duration-enter) var(--ease-soft),
    transform var(--duration-enter) var(--ease-soft);
}

.view-fade-leave-active {
  transition: opacity var(--duration-leave) ease-out;
}

.view-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.view-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .view-fade-enter-active,
  .view-fade-leave-active {
    transition: none;
  }

  .view-fade-enter-from,
  .view-fade-leave-to {
    transform: none;
  }
}
</style>
