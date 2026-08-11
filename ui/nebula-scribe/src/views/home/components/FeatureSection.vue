<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { features } from '../../../data/product'
</script>

<template>
  <section id="features" class="features page">
    <header class="features__head">
      <h2 class="page-title">写长篇需要的，不只是一个输入框</h2>
      <p class="page-subtitle">从立骨架到守一致，每个环节都有对应的工具。</p>
    </header>

    <div class="features__grid">
      <article v-for="item in features" :key="item.title" class="feature surface">
        <span class="feature__icon">
          <Icon :icon="item.icon" />
        </span>
        <h3 class="feature__title">{{ item.title }}</h3>
        <p class="feature__desc">{{ item.desc }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
.features__head {
  max-width: 42rem;
  margin-bottom: 2rem;
}

.features__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr));
  gap: 1rem;
}

.feature {
  padding: 1.4rem;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.feature:hover {
  transform: translateY(-2px);
  border-color: var(--color-brand);
  box-shadow: var(--shadow-md);
}

.feature__icon {
  display: inline-grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-lg);
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.feature__icon svg {
  width: 1.3rem;
  height: 1.3rem;
}

.feature__title {
  margin-top: 0.9rem;
  font-size: 1.05rem;
  font-weight: 700;
}

.feature__desc {
  margin-top: 0.5rem;
  font-size: 0.92rem;
  color: var(--color-text-secondary);
}
</style>
