<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { features } from '../../../data/product'
</script>

<template>
  <section id="features" class="features">
    <div class="features__inner">
      <header class="section-head">
        <p class="section-head__eyebrow">核心能力</p>
        <h2 class="section-head__title">一个面板，掌控全部操作</h2>
        <p class="section-head__sub">
          从启动应用到运行插件，orccode 把高频操作收敛进一个键盘驱动的命令面板。
        </p>
      </header>

      <div class="grid">
        <article v-for="feature in features" :key="feature.title" class="card">
          <div class="card__icon">
            <Icon :icon="feature.icon" />
          </div>
          <h3 class="card__title">{{ feature.title }}</h3>
          <p class="card__desc">{{ feature.description }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.features {
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x);
}

.features__inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.section-head {
  max-width: 40rem;
  margin: 0 auto 2.75rem;
  text-align: center;
}

.section-head__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.section-head__title {
  margin-top: 0.6rem;
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.section-head__sub {
  margin-top: 0.85rem;
  color: var(--color-text-secondary);
  font-size: 1.05rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.25rem;
}

.card {
  padding: 1.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.card:hover {
  transform: translateY(-4px);
  border-color: var(--color-brand);
  box-shadow: var(--shadow-md);
}

.card__icon {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  margin-bottom: 1rem;
}

.card__icon svg {
  width: 1.5rem;
  height: 1.5rem;
}

.card__title {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.card__desc {
  margin-top: 0.5rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
