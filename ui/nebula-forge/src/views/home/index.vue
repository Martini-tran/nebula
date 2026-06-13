<template>
  <main class="forge-shell">
    <section class="forge-hero">
      <div class="forge-hero__content">
        <p class="forge-hero__eyebrow">Nebula Forge</p>
        <h1>前台工程骨架已就绪</h1>
        <p class="forge-hero__summary">
          这里保留了博客前台的技术栈和工程依赖，但没有携带博客业务页面、文章数据或接口内容。
        </p>
      </div>

      <div class="forge-actions" aria-label="工程状态">
        <span>Vue 3</span>
        <span>Vite</span>
        <span>Pinia</span>
        <span>Router</span>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.forge-shell {
  min-height: 100vh;
  padding: var(--space-page-y) var(--space-page-x);
}

.forge-hero {
  display: grid;
  min-height: calc(100vh - (var(--space-page-y) * 2));
  align-content: center;
  gap: 2rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.forge-hero__content {
  display: grid;
  gap: 1rem;
  max-width: 46rem;
}

.forge-hero__eyebrow {
  color: var(--color-accent-text);
  font-size: 0.875rem;
  font-weight: 700;
  text-transform: uppercase;
}

.forge-hero h1 {
  color: var(--color-text-primary);
  font-size: clamp(2.5rem, 8vw, 5rem);
  font-weight: 800;
  line-height: 1.05;
}

.forge-hero__summary {
  max-width: 38rem;
  color: var(--color-text-secondary);
  font-size: 1.125rem;
}

.forge-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.forge-actions span {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-brand);
  padding: 0.65rem 0.9rem;
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

@media (max-width: 640px) {
  .forge-hero h1 {
    font-size: 2.5rem;
  }

  .forge-hero__summary {
    font-size: 1rem;
  }
}
</style>
