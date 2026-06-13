<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { workflowSteps } from '../../../data/product'
</script>

<template>
  <section id="workflow" class="workflow">
    <div class="workflow__inner">
      <header class="section-head">
        <p class="section-head__eyebrow">工作流</p>
        <h2 class="section-head__title">三步，从想法到执行</h2>
        <p class="section-head__sub">全程键盘操作，双手无需离开主键区。</p>
      </header>

      <ol class="steps">
        <li v-for="(step, index) in workflowSteps" :key="step.title" class="step">
          <span class="step__index">{{ index + 1 }}</span>
          <div class="step__icon"><Icon :icon="step.icon" /></div>
          <h3 class="step__title">{{ step.title }}</h3>
          <p class="step__desc">{{ step.description }}</p>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped lang="scss">
.workflow {
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x);
  background: var(--color-bg-soft);
}

.workflow__inner {
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

.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.25rem;
}

.step {
  position: relative;
  padding: 1.8rem 1.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.step__index {
  position: absolute;
  top: 1.2rem;
  right: 1.4rem;
  font-size: 2.5rem;
  font-weight: 800;
  line-height: 1;
  color: var(--color-brand-soft);
}

.step__icon {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  margin-bottom: 1rem;
}

.step__icon svg {
  width: 1.5rem;
  height: 1.5rem;
}

.step__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.step__desc {
  margin-top: 0.5rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

@media (max-width: 760px) {
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>
