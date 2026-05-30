<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchSeriesList, type SeriesListItem } from '../../api/series'
import { useThemeStore } from '../../stores/theme'
import logoLight from '../../assets/logo-light.png'
import logoDark from '../../assets/logo-dark.png'

type StatusFilter = 'all' | 'ongoing' | 'finished'

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

const items = ref<SeriesListItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)

const searchInput = ref('')
const searchKeyword = ref('')
const statusFilter = ref<StatusFilter>('all')

const totalArticles = computed(() =>
  items.value.reduce((sum, s) => sum + (s.article_count ?? 0), 0),
)

const finishedCount = computed(
  () => items.value.filter((s) => s.is_finished).length,
)

const ongoingCount = computed(
  () => items.value.filter((s) => !s.is_finished).length,
)

const filteredItems = computed(() => {
  if (statusFilter.value === 'all') return items.value
  if (statusFilter.value === 'ongoing')
    return items.value.filter((s) => !s.is_finished)
  return items.value.filter((s) => s.is_finished)
})

const featuredSeries = computed(() => {
  return [...items.value]
    .filter((s) => (s.article_count ?? 0) > 0)
    .sort((a, b) => (b.article_count ?? 0) - (a.article_count ?? 0))
    .slice(0, 3)
})

const formatUpdatedAt = (value?: string | null) => {
  if (!value) return ''
  return value.slice(0, 7).replace('-', '/')
}

const initialLetter = (name: string) => {
  if (!name) return ''
  return Array.from(name)[0] ?? ''
}

const buildParams = (cursor?: string | null) => {
  const isFinished =
    statusFilter.value === 'finished'
      ? true
      : statusFilter.value === 'ongoing'
        ? false
        : undefined
  return {
    limit: 12,
    keyword: searchKeyword.value || undefined,
    isFinished,
    cursor: cursor ?? undefined,
  }
}

const loadInitial = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchSeriesList(buildParams())
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
    const result = await fetchSeriesList(buildParams(nextCursor.value))
    items.value = items.value.concat(result.items ?? [])
    nextCursor.value = result.next_cursor
  } catch (err) {
    errorMessage.value = (err as Error)?.message || '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

const submitSearch = () => {
  const kw = searchInput.value.trim()
  if (kw === searchKeyword.value) return
  searchKeyword.value = kw
  loadInitial()
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
  loadInitial()
}

const setStatus = (val: StatusFilter) => {
  if (statusFilter.value === val) return
  statusFilter.value = val
  loadInitial()
}

onMounted(loadInitial)
</script>

<template>
  <div class="series-page">
    <!-- ── Hero ── -->
    <header class="hero">
      <div class="hero__brand">
        <img
          :src="isDark ? logoDark : logoLight"
          alt="FluxLu"
          class="hero__logo"
        />
        <div class="hero__brand-text">
          <p class="hero__eyebrow">Series · 系列</p>
          <h1 class="hero__title">系列文章</h1>
        </div>
      </div>
      <p class="hero__desc">
        将零散的文章组织成体系，每个系列都是一段完整的学习路径。
      </p>
      <div class="hero__bottom">
        <div class="hero__stats">
          <div class="hero-stat">
            <span class="hero-stat__num">{{ items.length }}</span>
            <span class="hero-stat__label">个系列</span>
          </div>
          <span class="hero-stat__sep" aria-hidden="true" />
          <div class="hero-stat">
            <span class="hero-stat__num">{{ totalArticles }}</span>
            <span class="hero-stat__label">篇文章</span>
          </div>
          <span class="hero-stat__sep" aria-hidden="true" />
          <div class="hero-stat">
            <span class="hero-stat__num">{{ finishedCount }}</span>
            <span class="hero-stat__label">已完结</span>
          </div>
        </div>
      </div>
    </header>

    <!-- ── 工具栏 ── -->
    <div class="toolbar">
      <form class="search" @submit.prevent="submitSearch">
        <label class="search__field">
          <Icon icon="lucide:search" class="search__icon" aria-hidden="true" />
          <input
            v-model="searchInput"
            class="search__input"
            type="search"
            placeholder="搜索系列"
            autocomplete="off"
          />
          <button
            v-if="searchInput || searchKeyword"
            type="button"
            class="search__clear"
            aria-label="清空"
            @click="clearSearch"
          >
            <Icon icon="lucide:x" />
          </button>
        </label>
      </form>

      <div class="filter-pills" role="tablist">
        <button
          type="button"
          role="tab"
          class="filter-pill"
          :class="{ 'filter-pill--active': statusFilter === 'all' }"
          @click="setStatus('all')"
        >
          全部 <span class="filter-pill__num">{{ items.length }}</span>
        </button>
        <button
          type="button"
          role="tab"
          class="filter-pill"
          :class="{ 'filter-pill--active': statusFilter === 'ongoing' }"
          @click="setStatus('ongoing')"
        >
          连载中 <span class="filter-pill__num">{{ ongoingCount }}</span>
        </button>
        <button
          type="button"
          role="tab"
          class="filter-pill"
          :class="{ 'filter-pill--active': statusFilter === 'finished' }"
          @click="setStatus('finished')"
        >
          已完结 <span class="filter-pill__num">{{ finishedCount }}</span>
        </button>
      </div>
    </div>

    <!-- ── 推荐 Top 3（横向 row） ── -->
    <section
      v-if="!loading && featuredSeries.length > 1 && statusFilter === 'all' && !searchKeyword"
      class="featured"
      aria-label="推荐"
    >
      <header class="section-header">
        <Icon icon="lucide:sparkles" />
        <span>推荐</span>
        <span class="section-header__sub">文章最多的系列</span>
      </header>
      <div class="featured__list">
        <RouterLink
          v-for="(item, idx) in featuredSeries"
          :key="item.id"
          :to="`/series/${item.slug}`"
          class="featured-row"
          :class="`featured-row--rank-${idx + 1}`"
        >
          <span class="featured-row__rank" aria-hidden="true">{{ idx + 1 }}</span>
          <div class="featured-row__main">
            <h3 class="featured-row__title">{{ item.name }}</h3>
            <p v-if="item.description" class="featured-row__desc">
              {{ item.description }}
            </p>
          </div>
          <div class="featured-row__meta">
            <span class="featured-row__count">{{ item.article_count }} 篇</span>
            <span v-if="item.is_finished" class="featured-row__badge">完结</span>
            <Icon icon="lucide:arrow-right" class="featured-row__arrow" />
          </div>
        </RouterLink>
      </div>
    </section>

    <!-- ── 状态/错误 ── -->
    <p v-if="loading" class="state-block">
      <span class="state-block__spinner" aria-hidden="true" />
      加载中…
    </p>
    <p v-else-if="errorMessage" class="state-block state-block--error">
      {{ errorMessage }}
    </p>
    <p v-else-if="filteredItems.length === 0" class="state-block">
      {{ searchKeyword ? `没有找到与「${searchKeyword}」相关的系列` : '暂无系列' }}
    </p>

    <!-- ── 系列列表 ── -->
    <section
      v-if="!loading && filteredItems.length"
      class="series-grid"
      aria-label="系列列表"
    >
      <RouterLink
        v-for="item in filteredItems"
        :key="item.id"
        :to="`/series/${item.slug}`"
        class="series-card"
      >
        <div
          class="series-card__cover"
          :class="{ 'series-card__cover--image': !!item.cover_url }"
          aria-hidden="true"
        >
          <img v-if="item.cover_url" :src="item.cover_url" alt="cover" />
          <span v-else class="series-card__cover-letter">
            {{ initialLetter(item.name) }}
          </span>
          <span
            v-if="item.is_finished"
            class="series-card__cover-badge series-card__cover-badge--finished"
          >
            已完结
          </span>
          <span v-else class="series-card__cover-badge">连载中</span>
        </div>

        <div class="series-card__body">
          <div class="series-card__meta">
            <span class="series-card__count">{{ item.article_count }} 篇</span>
            <span v-if="item.update_time" class="series-card__updated">
              更新于 {{ formatUpdatedAt(item.update_time) }}
            </span>
          </div>
          <h2 class="series-card__title">{{ item.name }}</h2>
          <p v-if="item.description" class="series-card__desc">
            {{ item.description }}
          </p>
          <div v-if="item.tags?.length" class="series-card__tags">
            <span
              v-for="tag in item.tags"
              :key="tag.id"
              class="series-tag"
            >{{ tag.name }}</span>
          </div>
        </div>

        <div class="series-card__arrow" aria-hidden="true">
          <Icon icon="lucide:arrow-right" />
        </div>
      </RouterLink>
    </section>

    <!-- ── 加载更多 ── -->
    <div v-if="nextCursor && !loading" class="load-more-row">
      <button
        class="load-more"
        :disabled="loadingMore"
        @click="loadMore"
      >
        {{ loadingMore ? '加载中…' : '加载更多' }}
      </button>
    </div>
    <p
      v-else-if="!loading && filteredItems.length"
      class="end-hint"
    >
      已经到底了 ···
    </p>
  </div>
</template>

<style scoped>
.series-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  width: 100%;
  min-width: 0;
}

/* ── Hero ── */
.hero {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.5rem;
  background:
    radial-gradient(ellipse at 80% 0%, color-mix(in srgb, var(--color-accent) 18%, transparent), transparent 55%),
    linear-gradient(160deg, var(--color-bg-surface), var(--color-bg-soft));
  padding: 1.6rem 1.85rem 1.4rem;
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hero__brand {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.hero__logo {
  width: 3rem;
  height: 3rem;
  border-radius: 0.75rem;
  object-fit: contain;
  flex-shrink: 0;
  box-shadow:
    0 6px 18px -4px color-mix(in srgb, var(--color-accent) 40%, transparent),
    0 0 0 1px color-mix(in srgb, var(--color-border) 80%, transparent);
}

.hero__brand-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.hero__eyebrow {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.hero__title {
  margin: 0;
  font-size: clamp(1.5rem, 3.5vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--color-text-primary);
}

.hero__desc {
  margin: 0;
  max-width: 36rem;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.hero__bottom {
  margin-top: 0.25rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.85rem;
}

.hero-stat {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.hero-stat__num {
  font-size: 1.4rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.hero-stat__label {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  font-weight: 600;
}

.hero-stat__sep {
  width: 1px;
  height: 1.4rem;
  background: color-mix(in srgb, var(--color-border) 70%, transparent);
}

/* ── 工具栏 ── */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
}

.search {
  flex: 1;
  min-width: 220px;
  max-width: 360px;
}

.search__field {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: 0.6rem;
  background: var(--color-bg-surface);
  padding: 0.1rem 0.65rem;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search__field:focus-within {
  border-color: color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 14%, transparent);
}

.search__icon {
  color: var(--color-text-muted);
  font-size: 0.95rem;
  flex-shrink: 0;
}

.search__input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 0.55rem 0;
  color: var(--color-text-primary);
  font-size: 0.85rem;
}

.search__input::placeholder {
  color: var(--color-text-muted);
}

.search__clear {
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0.2rem;
  color: var(--color-text-muted);
  border-radius: 999px;
  transition: color 0.15s, background 0.15s;
}

.search__clear:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-soft);
}

.search__clear :deep(svg) {
  width: 0.85rem;
  height: 0.85rem;
}

.filter-pills {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.filter-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  border-radius: 999px;
  padding: 0.4rem 0.85rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.filter-pill:hover {
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
}

.filter-pill--active,
.filter-pill--active:hover {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
  border-color: var(--color-text-primary);
}

.filter-pill__num {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.filter-pill--active .filter-pill__num {
  color: color-mix(in srgb, var(--color-bg-surface) 80%, transparent);
}

/* ── 段头 ── */
.section-header {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  margin-bottom: 0.6rem;
  padding-left: 0.15rem;
}

.section-header :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-accent);
}

.section-header__sub {
  margin-left: 0.25rem;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: none;
  color: var(--color-text-muted);
}

/* ── 推荐（横向 row） ── */
.featured__list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.featured-row {
  display: flex;
  align-items: center;
  gap: 0.95rem;
  padding: 0.85rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-bg-surface);
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.featured-row:hover {
  border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
  box-shadow: 0 6px 20px color-mix(in srgb, var(--color-accent) 10%, transparent);
  transform: translateX(2px);
}

.featured-row__rank {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.85rem;
  height: 1.85rem;
  border-radius: 0.5rem;
  background: var(--color-bg-soft);
  font-size: 0.9rem;
  font-weight: 800;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.featured-row--rank-1 .featured-row__rank {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #fff;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.35);
}

.featured-row--rank-2 .featured-row__rank {
  background: linear-gradient(135deg, #cbd5e1, #94a3b8);
  color: #fff;
  box-shadow: 0 4px 12px rgba(148, 163, 184, 0.3);
}

.featured-row--rank-3 .featured-row__rank {
  background: linear-gradient(135deg, #fb923c, #ea580c);
  color: #fff;
  box-shadow: 0 4px 12px rgba(234, 88, 12, 0.3);
}

.featured-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.featured-row__title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-row:hover .featured-row__title {
  color: var(--color-accent-text);
}

.featured-row__desc {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-row__meta {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  flex-shrink: 0;
}

.featured-row__count {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.featured-row__badge {
  font-size: 0.62rem;
  font-weight: 700;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: rgba(22, 163, 74, 0.1);
  color: #16a34a;
}

.featured-row__arrow {
  width: 1rem;
  height: 1rem;
  color: var(--color-text-muted);
  transition: color 0.15s, transform 0.15s;
}

.featured-row:hover .featured-row__arrow {
  color: var(--color-accent);
  transform: translateX(3px);
}

/* ── 状态块 ── */
.state-block {
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem 1.5rem;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state-block--error {
  color: #dc2626;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-block__spinner {
  display: inline-block;
  width: 0.95rem;
  height: 0.95rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ── 系列卡片：单列横铺 row ── */
.series-grid {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.series-card {
  position: relative;
  display: flex;
  align-items: stretch;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.series-card:hover,
.series-card:focus-visible {
  outline: none;
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  box-shadow:
    0 10px 28px color-mix(in srgb, var(--color-accent) 12%, transparent),
    0 2px 6px color-mix(in srgb, var(--color-text-primary) 5%, transparent);
  transform: translateY(-1px);
}

.series-card__cover {
  position: relative;
  flex-shrink: 0;
  width: 9rem;
  align-self: stretch;
  background: linear-gradient(135deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 60%, #6366f1));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

@media (max-width: 540px) {
  .series-card {
    flex-direction: column;
  }
  .series-card__cover {
    width: 100%;
    height: 7rem;
  }
}

.series-card__cover--image {
  background: var(--color-bg-soft);
}

.series-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s ease;
}

.series-card:hover .series-card__cover img {
  transform: scale(1.05);
}

.series-card__cover-letter {
  font-size: 2.4rem;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.95);
  letter-spacing: -0.02em;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

.series-card__cover-badge {
  position: absolute;
  top: 0.55rem;
  left: 0.55rem;
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  backdrop-filter: blur(4px);
}

.series-card__cover-badge--finished {
  background: rgba(22, 163, 74, 0.85);
}

.series-card__body {
  flex: 1;
  min-width: 0;
  padding: 1rem 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.series-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.series-card__count {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  padding: 0.16rem 0.55rem;
  border-radius: 999px;
}

.series-card__updated {
  font-size: 0.7rem;
  color: var(--color-text-muted);
}

.series-card__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.35;
}

.series-card:hover .series-card__title {
  color: var(--color-accent-text);
}

.series-card__desc {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.series-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: auto;
  padding-top: 0.15rem;
}

.series-tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.12rem 0.5rem;
  border-radius: 999px;
}

.series-card__arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 1.1rem;
  color: var(--color-text-muted);
  transition: color 0.2s ease, transform 0.2s ease;
}

.series-card__arrow :deep(svg) {
  width: 1.1rem;
  height: 1.1rem;
}

.series-card:hover .series-card__arrow {
  color: var(--color-accent);
  transform: translateX(3px);
}

@media (max-width: 540px) {
  .series-card__arrow {
    display: none;
  }
}

/* ── 加载更多 ── */
.load-more-row {
  display: flex;
  justify-content: center;
}

.load-more {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 0.65rem 1.5rem;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s, transform 0.15s;
}

.load-more:hover:not(:disabled) {
  color: var(--color-accent-text);
  border-color: var(--color-accent);
  background: var(--color-bg-soft);
  transform: translateY(-1px);
}

.load-more:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.end-hint {
  text-align: center;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
  padding: 0.5rem 0 1rem;
  margin: 0;
}

@media (prefers-reduced-motion: reduce) {
  .series-card,
  .series-card__arrow,
  .featured-row,
  .featured-row__arrow,
  .load-more {
    transition: none;
  }
  .series-card:hover,
  .featured-row:hover,
  .load-more:hover:not(:disabled) {
    transform: none;
  }
}
</style>
