<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { features } from '../../../data/product'
</script>

<template>
  <section id="features" class="highlights">
    <div class="highlights__inner">
      <header class="section-head">
        <p class="section-head__eyebrow">功能特性</p>
        <h2 class="section-head__title">为「快」而生的每一处设计</h2>
        <p class="section-head__sub">常驻后台、随手唤起，把高频操作压缩成几次击键。</p>
      </header>

      <div class="grid">
        <div v-for="item in features" :key="item.title" class="card">
          <div class="card__icon-wrap">
            <Icon :icon="item.icon" class="card__icon" />
          </div>
          <h3 class="card__title">{{ item.title }}</h3>
          <p class="card__desc">{{ item.description }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.highlights {
  padding: clamp(3rem, 6vw, 4.5rem) var(--space-page-x);
  background: var(--color-bg-soft);
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.highlights__inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.section-head {
  max-width: 40rem;
  margin: 0 auto;
  text-align: center;
}

.section-head__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.section-head__title {
  margin-top: 0.6rem;
  font-size: clamp(1.5rem, 3vw, 2.1rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.section-head__sub {
  margin-top: 0.7rem;
  color: var(--color-text-secondary);
  font-size: 1.02rem;
}

.grid {
  margin-top: clamp(1.75rem, 4vw, 2.75rem);
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.25rem;
}

.card {
  padding: 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.card:hover {
  border-color: var(--color-brand);
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.card__icon-wrap {
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
}

.card__icon {
  width: 1.3rem;
  height: 1.3rem;
  color: var(--color-brand);
}

.card__title {
  margin-top: 1rem;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.card__desc {
  margin-top: 0.5rem;
  font-size: 0.92rem;
  line-height: 1.65;
  color: var(--color-text-secondary);
}

@media (max-width: 820px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
