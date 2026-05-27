<template>
  <div class="series-shell">
    <div class="series-layout">
      <aside class="series-sidebar" aria-label="系列导航">
        <RouterLink to="/" class="series-brand">
          <span class="series-brand__mark" aria-hidden="true">F</span>
          <span>
            <strong>Flux Series</strong>
            <small>学习路径</small>
          </span>
        </RouterLink>

        <nav class="series-nav">
          <RouterLink
            to="/series"
            class="series-nav__item"
            :class="{ 'series-nav__item--active': route.path === '/series' }"
          >
            <span class="series-nav__dot" aria-hidden="true" />
            <span class="series-nav__label">全部系列</span>
            <span class="series-nav__count">{{ items.length }}</span>
          </RouterLink>

          <RouterLink
            v-for="item in items"
            :key="item.slug"
            :to="`/series/${item.slug}`"
            class="series-nav__item"
            :class="{ 'series-nav__item--active': route.params.slug === item.slug }"
          >
            <span class="series-nav__dot" aria-hidden="true" />
            <span class="series-nav__label">{{ item.name }}</span>
            <span class="series-nav__count">{{ item.article_count }}</span>
          </RouterLink>
        </nav>
      </aside>

      <main class="series-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { fetchSeriesList, type SeriesListItem } from '../api/series'

const route = useRoute()
const items = ref<SeriesListItem[]>([])

onMounted(async () => {
  try {
    const result = await fetchSeriesList({ limit: 50 })
    items.value = result.items ?? []
  } catch {
    items.value = []
  }
})
</script>

<style scoped>
.series-shell {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

.series-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  width: 100%;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}

@media (min-width: 980px) {
  .series-layout {
    grid-template-columns: 240px minmax(0, 1fr);
    align-items: start;
  }
}

.series-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 980px) {
  .series-sidebar {
    position: sticky;
    top: var(--space-page-y);
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow-y: auto;
  }
}

.series-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  padding: 0.85rem;
  color: var(--color-text-primary);
  text-decoration: none;
  box-shadow: var(--shadow-sm);
}

.series-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 0.7rem;
  background: var(--color-accent);
  color: var(--color-bg-surface);
  font-weight: 900;
}

.series-brand strong,
.series-brand small {
  display: block;
  line-height: 1.2;
}

.series-brand strong {
  font-size: 0.95rem;
}

.series-brand small {
  margin-top: 0.15rem;
  color: var(--color-text-muted);
  font-size: 0.74rem;
  font-weight: 700;
}

.series-nav {
  display: grid;
  gap: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: color-mix(in srgb, var(--color-bg-surface) 82%, transparent);
  padding: 0.45rem;
  box-shadow: var(--shadow-sm);
}

.series-nav__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.55rem;
  min-height: 2.6rem;
  border-radius: 0.75rem;
  padding: 0.55rem 0.65rem;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 700;
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.series-nav__item:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  transform: translateX(2px);
}

.series-nav__item--active {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.series-nav__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.45;
}

.series-nav__item--active .series-nav__dot {
  opacity: 1;
}

.series-nav__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-nav__count {
  min-width: 1.45rem;
  border-radius: 999px;
  background: var(--color-bg-surface);
  border: 1px solid color-mix(in srgb, currentColor 20%, var(--color-border));
  color: currentColor;
  font-size: 0.72rem;
  font-weight: 800;
  line-height: 1.35;
  padding: 0.1rem 0.35rem;
  text-align: center;
}

.series-main {
  min-width: 0;
}
</style>
