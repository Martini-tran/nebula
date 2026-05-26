<template>
  <div class="reviews-shell">
    <main class="reviews-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.reviews-shell {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

.reviews-main {
  width: 100%;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}
</style>
