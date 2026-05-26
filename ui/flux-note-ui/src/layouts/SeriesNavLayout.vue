<template>
  <div class="series-shell">
    <main class="series-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.series-shell {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

.series-main {
  width: 100%;
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}
</style>
