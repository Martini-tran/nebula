<script setup lang="ts">
import { Icon } from '@iconify/vue'
import ScreenshotFrame from './ScreenshotFrame.vue'
import { showcaseItems } from '../../../data/product'
</script>

<template>
  <section id="showcase" class="showcase">
    <div class="showcase__inner">
      <article
        v-for="(item, i) in showcaseItems"
        :key="item.image"
        class="row"
        :class="{ 'row--reverse': i % 2 === 1 }"
      >
        <div class="row__text">
          <span class="row__badge">
            <Icon :icon="item.icon" class="row__badge-icon" />
            {{ item.badge }}
          </span>
          <h3 class="row__title">{{ item.title }}</h3>
          <p class="row__desc">{{ item.description }}</p>
          <ul class="row__bullets">
            <li v-for="b in item.bullets" :key="b" class="row__bullet">
              <Icon icon="lucide:check" class="row__check" />
              <span>{{ b }}</span>
            </li>
          </ul>
        </div>

        <div class="row__media">
          <ScreenshotFrame :name="item.image" :label="item.title" aspect="16 / 10" />
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
.showcase {
  padding: clamp(3rem, 7vw, 5rem) var(--space-page-x);
}

.showcase__inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: clamp(3rem, 7vw, 5rem);
}

.row {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  align-items: center;
  gap: clamp(2rem, 5vw, 4rem);
}

.row--reverse .row__text {
  order: 2;
}

.row__badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.7rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.8rem;
  font-weight: 700;
}

.row__badge-icon {
  width: 0.95rem;
  height: 0.95rem;
}

.row__title {
  margin-top: 1rem;
  font-size: clamp(1.4rem, 2.6vw, 1.9rem);
  font-weight: 800;
  line-height: 1.3;
  color: var(--color-text-primary);
}

.row__desc {
  margin-top: 0.8rem;
  font-size: 1.02rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.row__bullets {
  list-style: none;
  margin: 1.2rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.row__bullet {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  color: var(--color-text-primary);
  font-size: 0.95rem;
}

.row__check {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
  margin-top: 0.15rem;
  padding: 0.1rem;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

@media (max-width: 820px) {
  .row {
    grid-template-columns: 1fr;
    gap: 1.75rem;
  }

  .row--reverse .row__text {
    order: 0;
  }
}
</style>
