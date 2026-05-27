<template>
  <div class="series-detail-shell-outer">
    <div class="series-detail-shell-outer__inner">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.series-detail-shell-outer {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

.series-detail-shell-outer__inner {
  width: 100%;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}
</style>
