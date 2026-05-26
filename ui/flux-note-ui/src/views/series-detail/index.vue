<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { findSeries } from '../../data/series'

const route = useRoute()

const series = computed(() => findSeries(route.params.slug))
const publishedCount = computed(() => (
  series.value?.chapters.filter((chapter) => chapter.status === 'published').length ?? 0
))
const totalMinutes = computed(() => (
  series.value?.chapters.reduce((sum, chapter) => sum + chapter.minutes, 0) ?? 0
))
</script>

<template>
  <div v-if="series" class="series-detail-page">
    <RouterLink to="/series" class="back-link">
      <span aria-hidden="true">←</span>
      返回系列
    </RouterLink>

    <section class="series-detail-hero">
      <div class="series-detail-hero__content">
        <p class="series-detail-hero__eyebrow">Series Path</p>
        <h1 class="series-detail-hero__title">{{ series.title }}</h1>
        <p class="series-detail-hero__desc">{{ series.description }}</p>
        <div class="series-detail-hero__tags">
          <span v-for="tag in series.tags" :key="tag" class="series-detail-tag">{{ tag }}</span>
        </div>
      </div>

      <aside class="series-summary" aria-label="系列概览">
        <span class="series-summary__label">学习路径</span>
        <strong>{{ series.level }}</strong>
        <div class="series-summary__grid">
          <span>
            <b>{{ series.articleCount }}</b>
            篇规划
          </span>
          <span>
            <b>{{ publishedCount }}</b>
            篇已发布
          </span>
          <span>
            <b>{{ totalMinutes }}</b>
            分钟
          </span>
          <span>
            <b>{{ series.updatedAt }}</b>
            更新
          </span>
        </div>
      </aside>
    </section>

    <section class="series-roadmap" aria-label="系列目录">
      <div class="section-heading">
        <p class="section-heading__eyebrow">Roadmap</p>
        <h2>系列目录</h2>
      </div>

      <ol class="chapter-list">
        <li
          v-for="(chapter, index) in series.chapters"
          :key="chapter.id"
          class="chapter-item"
          :class="{ 'chapter-item--draft': chapter.status === 'draft' }"
        >
          <span class="chapter-item__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="chapter-item__body">
            <div class="chapter-item__meta">
              <span>{{ chapter.minutes }} 分钟</span>
              <span>{{ chapter.status === 'published' ? '已发布' : '整理中' }}</span>
            </div>
            <h3>{{ chapter.title }}</h3>
            <p>{{ chapter.summary }}</p>
          </div>
        </li>
      </ol>
    </section>
  </div>

  <div v-else class="series-empty">
    <p class="series-empty__eyebrow">Series Missing</p>
    <h1>没有找到这个系列</h1>
    <RouterLink to="/series" class="back-link">返回系列列表</RouterLink>
  </div>
</template>

<style scoped>
.series-detail-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 960px;
  min-height: 100vh;
  margin: 0 auto;
  width: 100%;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
  transition: color 0.15s ease, transform 0.15s ease;
}

.back-link:hover {
  color: var(--color-accent-text);
  transform: translateX(-2px);
}

.series-detail-hero {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
  align-items: stretch;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--color-accent) 12%, transparent), transparent 32%),
    var(--color-bg-surface);
  overflow: hidden;
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

@media (min-width: 820px) {
  .series-detail-hero {
    grid-template-columns: minmax(0, 1fr) 280px;
  }
}

.series-detail-hero__content {
  padding: clamp(1.4rem, 4vw, 2.5rem);
}

.series-detail-hero__eyebrow,
.section-heading__eyebrow,
.series-empty__eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.series-detail-hero__title {
  margin: 0;
  max-width: 13em;
  color: var(--color-text-primary);
  font-size: clamp(1.75rem, 5vw, 3rem);
  font-weight: 850;
  line-height: 1.12;
}

.series-detail-hero__desc {
  margin: 1rem 0 0;
  max-width: 42rem;
  color: var(--color-text-secondary);
  font-size: 0.98rem;
  line-height: 1.75;
}

.series-detail-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 1.25rem;
}

.series-detail-tag {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-bg-surface) 80%, transparent);
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.25rem 0.65rem;
}

.series-summary {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1.25rem;
  background: color-mix(in srgb, var(--color-bg-soft) 76%, transparent);
  border-top: 1px solid var(--color-border);
  padding: 1.25rem;
}

@media (min-width: 820px) {
  .series-summary {
    border-top: 0;
    border-left: 1px solid var(--color-border);
  }
}

.series-summary__label {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 800;
}

.series-summary strong {
  display: block;
  color: var(--color-text-primary);
  font-size: 1.8rem;
  line-height: 1;
}

.series-summary__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.series-summary__grid span {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.875rem;
  background: var(--color-bg-surface);
  color: var(--color-text-muted);
  font-size: 0.75rem;
  padding: 0.75rem;
}

.series-summary__grid b {
  color: var(--color-text-primary);
  font-size: 1.1rem;
}

.series-roadmap {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 1.2rem;
  letter-spacing: -0.02em;
}

.chapter-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chapter-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  padding: 1rem;
}

.chapter-item__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.75rem;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.8rem;
  font-weight: 800;
}

.chapter-item__body {
  min-width: 0;
}

.chapter-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  color: var(--color-text-muted);
  font-size: 0.74rem;
  font-weight: 700;
}

.chapter-item h3 {
  margin: 0.35rem 0 0;
  color: var(--color-text-primary);
  font-size: 1rem;
  line-height: 1.35;
}

.chapter-item p {
  margin: 0.4rem 0 0;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  line-height: 1.65;
}

.chapter-item--draft {
  border-style: dashed;
}

.chapter-item--draft .chapter-item__index {
  background: var(--color-bg-soft);
  color: var(--color-text-muted);
}

.series-empty {
  max-width: 560px;
  margin: var(--space-page-y) auto;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  padding: 2rem;
  text-align: center;
}

.series-empty h1 {
  margin: 0 0 1rem;
  color: var(--color-text-primary);
  font-size: 1.5rem;
}

.series-empty .back-link {
  margin: 0 auto;
}
</style>
