<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchSeriesList, type SeriesListItem } from '../../api/series'

const items = ref<SeriesListItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)

const totalArticles = computed(() =>
  items.value.reduce((sum, s) => sum + (s.article_count ?? 0), 0),
)

const formatUpdatedAt = (value?: string | null) => {
  if (!value) return ''
  // 后端返回 yyyy-MM-dd HH:mm:ss，截到 yyyy-MM
  return value.slice(0, 7).replace('-', '/')
}

const initialLetter = (name: string) => {
  if (!name) return ''
  // 优先取首个非空字符
  return Array.from(name)[0] ?? ''
}

const loadInitial = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchSeriesList({ limit: 12 })
    items.value = result.items ?? []
    nextCursor.value = result.next_cursor
  } catch (err) {
    errorMessage.value = (err as Error)?.message || '系列列表加载失败'
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const result = await fetchSeriesList({ limit: 12, cursor: nextCursor.value })
    items.value = items.value.concat(result.items ?? [])
    nextCursor.value = result.next_cursor
  } catch (err) {
    errorMessage.value = (err as Error)?.message || '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

onMounted(loadInitial)
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
          <span class="series-stat__num">{{ items.length }}</span>
          <span class="series-stat__label">个系列</span>
        </span>
        <span class="series-stat__divider" aria-hidden="true">·</span>
        <span class="series-stat">
          <span class="series-stat__num">{{ totalArticles }}</span>
          <span class="series-stat__label">篇文章</span>
        </span>
      </div>
    </header>

    <!-- 加载/错误态 -->
    <p v-if="loading" class="series-state">加载中 ···</p>
    <p v-else-if="errorMessage" class="series-state series-state--error">
      {{ errorMessage }}
    </p>
    <p v-else-if="items.length === 0" class="series-state">暂无系列</p>

    <!-- 系列卡片网格 -->
    <section v-if="!loading && items.length" class="series-grid" aria-label="系列列表">
      <RouterLink
        v-for="item in items"
        :key="item.id"
        :to="`/series/${item.slug}`"
        class="series-card"
      >
        <!-- 封面：优先用真实封面，没有时退回字母色块 -->
        <div
          class="series-card__cover"
          :class="{ 'series-card__cover--image': !!item.cover_url }"
          aria-hidden="true"
        >
          <img v-if="item.cover_url" :src="item.cover_url" alt="cover" />
          <span v-else class="series-card__cover-letter">
            {{ initialLetter(item.name) }}
          </span>
        </div>

        <div class="series-card__body">
          <div class="series-card__meta">
            <span class="series-card__count">{{ item.article_count }} 篇</span>
            <span class="series-card__updated">
              更新于 {{ formatUpdatedAt(item.update_time) }}
            </span>
            <span
              v-if="item.is_finished"
              class="series-card__badge series-card__badge--finished"
            >
              已完结
            </span>
            <span v-else class="series-card__badge">连载中</span>
          </div>
          <h2 class="series-card__title">{{ item.name }}</h2>
          <p class="series-card__desc">{{ item.description }}</p>
          <div v-if="item.tags?.length" class="series-card__tags">
            <span
              v-for="tag in item.tags"
              :key="tag.id"
              class="series-tag"
            >{{ tag.name }}</span>
          </div>
        </div>

        <div class="series-card__arrow" aria-hidden="true">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 8h10M9 4l4 4-4 4"/>
          </svg>
        </div>
      </RouterLink>
    </section>

    <!-- 加载更多 -->
    <div v-if="nextCursor" class="series-load-more">
      <button
        class="series-load-more__btn"
        :disabled="loadingMore"
        @click="loadMore"
      >
        {{ loadingMore ? '加载中 ···' : '加载更多' }}
      </button>
    </div>

    <p v-else-if="!loading && items.length" class="series-coming-soon">
      已经到底了 ···
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

/* ── 状态 ── */
.series-state {
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  padding: 2rem 0;
}

.series-state--error {
  color: #dc2626;
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

/* 封面区 */
.series-card__cover {
  height: 5rem;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.series-card__cover--image {
  background: var(--color-bg-soft);
}

.series-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
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
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
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

.series-card__badge {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
}

.series-card__badge--finished {
  color: #16a34a;
  border-color: rgba(22, 163, 74, 0.4);
  background: rgba(22, 163, 74, 0.08);
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

/* ── 加载更多 ── */
.series-load-more {
  display: flex;
  justify-content: center;
  padding: 0.5rem 0 1rem;
}

.series-load-more__btn {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 0.55rem 1.25rem;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.series-load-more__btn:hover:not(:disabled) {
  color: var(--color-accent-text);
  border-color: var(--color-accent);
}

.series-load-more__btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
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
