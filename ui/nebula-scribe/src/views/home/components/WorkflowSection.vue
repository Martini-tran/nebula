<script setup lang="ts">
import { workflow } from '../../../data/product'
</script>

<template>
  <section class="workflow">
    <div class="workflow__inner page">
      <header class="workflow__head">
        <h2 class="page-title">三步把一个念头变成一本书</h2>
      </header>

      <ol class="workflow__list">
        <li v-for="item in workflow" :key="item.step" class="step">
          <span class="step__num">{{ item.step }}</span>
          <h3 class="step__title">{{ item.title }}</h3>
          <p class="step__desc">{{ item.desc }}</p>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped lang="scss">
.workflow {
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.workflow__head {
  max-width: 42rem;
  margin-bottom: 2rem;
}

.workflow__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: none;
}

.step {
  position: relative;
  padding-top: 1.25rem;
  border-top: 2px solid var(--color-brand);
}

.step__num {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-brand);
}

.step__title {
  margin-top: 0.35rem;
  font-size: 1.15rem;
  font-weight: 700;
}

.step__desc {
  margin-top: 0.5rem;
  color: var(--color-text-secondary);
}
</style>
