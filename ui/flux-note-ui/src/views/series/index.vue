<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { seriesList } from '../../data/series'
</script>

<template>
  <div class="series-page">
    <!-- 页头 -->
    <header class="series-hero">
      <p class="series-hero__eyebrow">Series · 系列</p>
      <h1 class="series-hero__title">系列文章</h1>
      <p class="series-hero__desc">
        将零散的文章组织成体系，每个系列都是一段完整的学习路径。
      </p>
      <div class="series-hero__stats">
        <span class="series-stat">
          <span class="series-stat__num">{{ seriesList.length }}</span>
          <span class="series-stat__label">个系列</span>
        </span>
        <span class="series-stat__divider" aria-hidden="true">·</span>
        <span class="series-stat">
          <span class="series-stat__num">{{ seriesList.reduce((s, i) => s + i.articleCount, 0) }}</span>
          <span class="series-stat__label">篇文章</span>
        </span>
      </div>
    </header>

    <!-- 系列卡片网格 -->
    <section class="series-grid" aria-label="系列列表">
      <RouterLink
        v-for="item in seriesList"
        :key="item.id"
        :to="`/series/${item.slug}`"
        class="series-card"
      >
        <!-- 封面色块 -->
        <div class="series-card__cover" aria-hidden="true">
          <span class="series-card__cover-letter">{{ item.title.charAt(0) }}</span>
        </div>

        <div class="series-card__body">
          <div class="series-card__meta">
            <span class="series-card__count">{{ item.articleCount }} 篇</span>
            <span class="series-card__updated">更新于 {{ item.updatedAt }}</span>
          </div>
          <h2 class="series-card__title">{{ item.title }}</h2>
          <p class="series-card__desc">{{ item.description }}</p>
          <div class="series-card__tags">
            <span
              v-for="tag in item.tags"
              :key="tag"
              class="series-tag"
            >{{ tag }}</span>
          </div>
        </div>

        <div class="series-card__arrow" aria-hidden="true">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 8h10M9 4l4 4-4 4"/>
          </svg>
        </div>
      </RouterLink>
    </section>

    <!-- 空状态（数据接入后可移除） -->
    <p class="series-coming-soon">
      更多系列整理中，敬请期待 ···
    </p>
  </div>
</template>

<style scoped>
/* ── 页面容器 ── */
.series-page {
  max-width: 980px;
  min-height: 100vh;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

/* ── 页头 ── */
.series-hero {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.5rem;
  background:
    radial-gradient(ellipse at 80% 0%, color-mix(in srgb, var(--color-accent) 18%, transparent), transparent 55%),
    linear-gradient(160deg, var(--color-bg-surface), var(--color-bg-soft));
  padding: 2rem 2rem 1.75rem;
  box-shadow: 0 20px 50px color-mix(in srgb, var(--color-text-primary) 6%, transparent);
}

.series-hero__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-accent);
}

.series-hero__title {
  margin: 0 0 0.6rem;
  font-size: clamp(1.6rem, 4vw, 2.2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--color-text-primary);
  line-height: 1.15;
}

.series-hero__desc {
  margin: 0 0 1.25rem;
  font-size: 0.95rem;
  color: var(--color-text-secondary);
  max-width: 480px;
  line-height: 1.65;
}

.series-hero__stats {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.series-stat {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
}

.series-stat__num {
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.series-stat__label {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.series-stat__divider {
  color: var(--color-text-muted);
  font-size: 1rem;
}

/* ── 卡片网格 ── */
.series-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

@media (min-width: 640px) {
  .series-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ── 系列卡片 ── */
.series-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  overflow: hidden;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
  outline: none;
}

.series-card:hover,
.series-card:focus-visible {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  box-shadow:
    0 12px 32px color-mix(in srgb, var(--color-accent) 12%, transparent),
    0 2px 8px color-mix(in srgb, var(--color-text-primary) 6%, transparent);
  transform: translateY(-2px);
}

.series-card:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 35%, transparent);
}

/* 封面色块 */
.series-card__cover {
  height: 5rem;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.series-card__cover-letter {
  font-size: 2rem;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: -0.02em;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

/* 卡片内容 */
.series-card__body {
  padding: 1rem 1rem 0.75rem;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.series-card__meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.series-card__count {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-accent);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
}

.series-card__updated {
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.series-card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.3;
}

.series-card__desc {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.series-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.25rem;
}

.series-tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
}

/* 箭头 */
.series-card__arrow {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0.5rem 1rem 0.75rem;
  color: var(--color-text-muted);
  transition: color 0.2s ease, transform 0.2s ease;
}

.series-card:hover .series-card__arrow {
  color: var(--color-accent);
  transform: translateX(3px);
}

/* ── 底部提示 ── */
.series-coming-soon {
  text-align: center;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  padding: 0.5rem 0 1rem;
  letter-spacing: 0.04em;
}

@media (prefers-reduced-motion: reduce) {
  .series-card,
  .series-card__arrow {
    transition: none;
  }
  .series-card:hover {
    transform: none;
  }
}
</style>
