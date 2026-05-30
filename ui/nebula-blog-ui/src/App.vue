<template>
  <router-view v-slot="{ Component, route }">
    <transition name="layout-fade" mode="out-in">
      <component :is="Component" :key="route.matched[0]?.path ?? route.path" />
    </transition>
  </router-view>
</template>

<style>
.layout-fade-enter-active,
.layout-fade-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.layout-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
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
