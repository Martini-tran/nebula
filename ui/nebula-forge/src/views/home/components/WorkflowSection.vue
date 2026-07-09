<script setup lang="ts">
import { Icon } from '@iconify/vue'
import ScreenshotFrame from './ScreenshotFrame.vue'
import { workflowSteps } from '../../../data/product'
</script>

<template>
  <section id="workflow" class="workflow">
    <div class="workflow__inner">
      <header class="section-head">
        <p class="section-head__eyebrow">三步上手</p>
        <h2 class="section-head__title">唤起、搜索、启动，一气呵成</h2>
        <p class="section-head__sub">从按下快捷键到打开目标，整个过程不超过一秒。</p>
      </header>

      <div class="workflow__media">
        <ScreenshotFrame name="workflow" ext="gif" label="工作流演示动图" aspect="16 / 9" />
      </div>

      <ol class="steps">
        <li v-for="(step, i) in workflowSteps" :key="step.title" class="step">
          <div class="step__index">{{ i + 1 }}</div>
          <div class="step__body">
            <div class="step__head">
              <Icon :icon="step.icon" class="step__icon" />
              <h3 class="step__title">{{ step.title }}</h3>
            </div>
            <p class="step__desc">{{ step.description }}</p>
          </div>
          <Icon
            v-if="i < workflowSteps.length - 1"
            icon="lucide:chevron-right"
            class="step__arrow"
          />
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped lang="scss">
.workflow {
  padding: clamp(3rem, 6vw, 4.5rem) var(--space-page-x);
  background: var(--color-bg-soft);
  border-bottom: 1px solid var(--color-border);
}

.workflow__inner {
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

.workflow__media {
  max-width: 56rem;
  margin: clamp(1.75rem, 4vw, 2.75rem) auto 0;
}

.steps {
  list-style: none;
  margin: clamp(1.75rem, 4vw, 2.75rem) 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.25rem;
}

.step {
  position: relative;
  display: flex;
  gap: 0.9rem;
  padding: 1.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.step__index {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 800;
  font-family: var(--font-mono);
}

.step__head {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.step__icon {
  width: 1.15rem;
  height: 1.15rem;
  color: var(--color-brand);
}

.step__title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.step__desc {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.step__arrow {
  position: absolute;
  right: -1.1rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1.4rem;
  height: 1.4rem;
  color: var(--color-text-secondary);
  opacity: 0.6;
  z-index: 1;
}

@media (max-width: 760px) {
  .steps {
    grid-template-columns: 1fr;
  }

  .step__arrow {
    right: 50%;
    top: auto;
    bottom: -1.1rem;
    transform: translateX(50%) rotate(90deg);
  }
}
</style>
