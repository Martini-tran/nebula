<template>
  <router-view v-slot="{ Component, route }">
    <transition name="layout-fade" mode="out-in">
      <component :is="Component" :key="route.matched[0]?.path ?? route.path" />
    </transition>
  </router-view>
</template>

<style>
.layout-fade-enter-active {
  transition:
    opacity var(--duration-enter) var(--ease-soft),
    transform var(--duration-enter) var(--ease-soft);
}

.layout-fade-leave-active {
  transition: opacity var(--duration-leave) ease-out;
}

/* 进入只轻轻上浮，离开只淡出，不再上下跳动 */
.layout-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.layout-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .layout-fade-enter-active,
  .layout-fade-leave-active {
    transition: none;
  }

  .layout-fade-enter-from,
  .layout-fade-leave-to {
    transform: none;
  }
}
</style>
