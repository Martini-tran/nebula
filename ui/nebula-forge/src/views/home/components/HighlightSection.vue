<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { features } from '../../../data/product'

const items = features.slice(0, 4)
</script>

<template>
  <section id="features" class="highlights">
    <div class="highlights__inner">
      <div v-for="item in items" :key="item.title" class="item">
        <div class="item__head">
          <Icon :icon="item.icon" class="item__icon" />
          <h3 class="item__title">{{ item.title }}</h3>
        </div>
        <p class="item__desc">{{ item.description }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.highlights {
  padding: clamp(2.5rem, 6vw, 4rem) var(--space-page-x);
}

.highlights__inner {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 2rem 2.5rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.item__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.item__icon {
  width: 1.1rem;
  height: 1.1rem;
  color: var(--color-text-secondary);
}

.item__title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.item__desc {
  margin-top: 0.55rem;
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--color-text-secondary);
}

@media (max-width: 820px) {
  .highlights__inner {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 460px) {
  .highlights__inner {
    grid-template-columns: 1fr;
  }
}
</style>
