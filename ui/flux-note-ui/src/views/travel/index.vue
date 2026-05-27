<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import {
  fetchTrips,
  searchTrips,
  fetchTravelDestinations,
  type TravelTripListItem,
  type TravelDestinationSummary,
} from '../../api/travel'

const trips = ref<TravelTripListItem[]>([])
const destinations = ref<TravelDestinationSummary[]>([])
const activeDestinationId = ref<number | string | null>(null)

const loading = ref(false)
const loadingMore = ref(false)
const error = ref<string | null>(null)
const nextCursor = ref<string | null>(null)

const searchInput = ref('')
const searchKeyword = ref('')

const PAGE_SIZE = 12

const fetcher = computed(() => (searchKeyword.value ? searchTrips : fetchTrips))

const queryParams = (cursor: string | null = null) => ({
  keyword: searchKeyword.value || undefined,
  destinationId: activeDestinationId.value ?? undefined,
  cursor: cursor ?? undefined,
  limit: PAGE_SIZE,
})

const loadTrips = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await fetcher.value(queryParams())
    trips.value = data?.items ?? []
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    trips.value = []
    nextCursor.value = null
    error.value = '加载游记失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const data = await fetcher.value(queryParams(nextCursor.value))
    if (data?.items?.length) trips.value = [...trips.value, ...data.items]
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    error.value = '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

const submitSearch = () => {
  const kw = searchInput.value.trim()
  if (searchKeyword.value === kw) return
  searchKeyword.value = kw
  loadTrips()
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
  loadTrips()
}

const selectDestination = (id: number | string | null) => {
  if (activeDestinationId.value === id) return
  activeDestinationId.value = id
  loadTrips()
}

const loadDestinations = async () => {
  try {
    const data = await fetchTravelDestinations(30)
    destinations.value = data ?? []
  } catch {
    destinations.value = []
  }
}

const formatDateRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) return ''
  const fmt = (v?: string | null) => {
    if (!v) return ''
    const [y, m, d] = v.split('-')
    return y && m && d ? `${y}.${m}.${d}` : v
  }
  if (start && end) return `${fmt(start)} – ${fmt(end)}`
  return fmt(start || end)
}

const formatCost = (val?: number | null, currency?: string | null) => {
  if (val == null) return ''
  const symbol = currency === 'USD' ? '$' : currency === 'EUR' ? '€' : '¥'
  return `${symbol}${Math.round(Number(val))}`
}

const initialLetter = (name: string) => Array.from(name)[0] ?? ''

onMounted(() => {
  loadDestinations()
  loadTrips()
})
</script>

<template>
  <div class="travel-page">
    <!-- ── 搜索面板 ── -->
    <section class="search-panel" aria-label="游记搜索">
      <div>
        <p class="search-eyebrow">Travel Log</p>
        <h2 class="section-title">
          {{ searchKeyword ? '搜索结果' : '旅行' }}
        </h2>
        <p v-if="!searchKeyword" class="search-desc">
          路线、风景、花费和现场感，把走过的路慢慢记下来。
        </p>
      </div>
      <form class="search-form" @submit.prevent="submitSearch">
        <label class="search-input-wrap">
          <Icon icon="lucide:search" class="search-icon" aria-hidden="true" />
          <input
            v-model="searchInput"
            class="search-input"
            type="search"
            placeholder="搜索游记标题或摘要"
            autocomplete="off"
          >
        </label>
        <button type="submit" class="search-button">搜索</button>
        <button
          v-if="searchKeyword"
          type="button"
          class="search-clear"
          @click="clearSearch"
        >
          清空
        </button>
      </form>
      <p v-if="searchKeyword" class="search-meta">
        正在搜索「{{ searchKeyword }}」
      </p>
    </section>

    <!-- ── Destination chips ── -->
    <nav v-if="destinations.length" class="dest-chips" aria-label="按目的地筛选">
      <button
        type="button"
        class="dest-chip"
        :class="{ 'dest-chip--active': activeDestinationId == null }"
        @click="selectDestination(null)"
      >
        全部
      </button>
      <button
        v-for="d in destinations"
        :key="d.id"
        type="button"
        class="dest-chip"
        :class="{ 'dest-chip--active': activeDestinationId === d.id }"
        @click="selectDestination(d.id)"
      >
        {{ d.name }}
      </button>
    </nav>

    <!-- ── 状态 ── -->
    <div v-if="loading" class="state-block">
      <span class="state-block__spinner" aria-hidden="true" />
      正在加载…
    </div>
    <div v-else-if="error" class="state-block state-block--error">{{ error }}</div>
    <div v-else-if="trips.length === 0" class="state-block">
      {{ searchKeyword || activeDestinationId != null ? '没有找到相关游记' : '还没有游记，敬请期待' }}
    </div>

    <!-- ── 游记网格 ── -->
    <section v-else class="trip-grid" aria-label="游记列表">
      <RouterLink
        v-for="item in trips"
        :key="item.id"
        :to="{ path: '/travel/detail', query: { slug: item.slug } }"
        class="trip-card"
      >
        <div
          class="trip-card__cover"
          :class="{ 'trip-card__cover--image': !!item.cover_url }"
          aria-hidden="true"
        >
          <img v-if="item.cover_url" :src="item.cover_url" alt="">
          <span v-else class="trip-card__cover-letter">
            {{ initialLetter(item.title) }}
          </span>
          <span v-if="item.days_count" class="trip-card__days-badge">
            {{ item.days_count }} 天
          </span>
        </div>

        <div class="trip-card__body">
          <h3 class="trip-card__title">{{ item.title }}</h3>
          <p v-if="item.summary" class="trip-card__summary">{{ item.summary }}</p>

          <div class="trip-card__meta">
            <span v-if="item.start_date || item.end_date" class="trip-card__meta-item">
              <Icon icon="lucide:calendar" />
              {{ formatDateRange(item.start_date, item.end_date) }}
            </span>
            <span v-if="item.persons" class="trip-card__meta-item">
              <Icon icon="lucide:users" />
              {{ item.persons }} 人
            </span>
            <span v-if="item.cost_total != null" class="trip-card__meta-item">
              <Icon icon="lucide:wallet" />
              {{ formatCost(item.cost_total, item.cost_currency) }}
            </span>
          </div>

          <div class="trip-card__stats">
            <span><Icon icon="lucide:eye" /> {{ item.view_count ?? 0 }}</span>
            <span><Icon icon="lucide:heart" /> {{ item.like_count ?? 0 }}</span>
          </div>
        </div>
      </RouterLink>
    </section>

    <!-- ── 加载更多 ── -->
    <button
      v-if="nextCursor && !loading"
      type="button"
      class="load-more"
      :disabled="loadingMore"
      @click="loadMore"
    >
      {{ loadingMore ? '加载中…' : '加载更多' }}
    </button>
    <p v-else-if="!loading && trips.length" class="end-hint">
      已经到底了 ···
    </p>
  </div>
</template>

<style scoped>
.travel-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 980px;
  margin: 0 auto;
  width: 100%;
}

/* ── 搜索面板 ── */
.search-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background:
    radial-gradient(circle at 78% 20%, rgba(14, 165, 233, 0.18), transparent 32%),
    radial-gradient(circle at 18% 80%, color-mix(in srgb, var(--color-accent) 20%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  padding: 1.1rem 1.2rem;
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.search-eyebrow {
  margin: 0 0 0.3rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.section-title {
  margin: 0;
  font-size: clamp(1.5rem, 4vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--color-text-primary);
  line-height: 1.15;
}

.search-desc {
  margin: 0.5rem 0 0;
  max-width: 32rem;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.625rem;
  margin-top: 1rem;
}

@media (min-width: 580px) {
  .search-form {
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
  }
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg-surface) 82%, transparent);
  padding: 0.15rem 0.875rem;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.search-input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  background: var(--color-bg-surface);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 14%, transparent);
}

.search-icon {
  color: var(--color-text-muted);
  font-size: 1rem;
  flex-shrink: 0;
}

.search-input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 0.65rem 0;
  color: var(--color-text-primary);
  font-size: 0.9rem;
}

.search-input::placeholder {
  color: var(--color-text-muted);
}

.search-button,
.search-clear {
  border: 0;
  border-radius: 999px;
  padding: 0.72rem 1rem;
  font-size: 0.875rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, background 0.15s ease;
}

.search-button {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
}

.search-clear {
  background: transparent;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.search-button:hover,
.search-clear:hover {
  transform: translateY(-1px);
}

.search-meta {
  margin: 0.75rem 0 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

/* ── Destination chips ── */
.dest-chips {
  display: flex;
  flex-wrap: nowrap;
  gap: 0.4rem;
  overflow-x: auto;
  padding: 0.25rem 0.05rem;
  scrollbar-width: none;
}

.dest-chips::-webkit-scrollbar {
  display: none;
}

.dest-chip {
  flex-shrink: 0;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  padding: 0.4rem 0.85rem;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s, transform 0.15s;
}

.dest-chip:hover {
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
  transform: translateY(-1px);
}

.dest-chip--active,
.dest-chip--active:hover {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

/* ── 状态块 ── */
.state-block {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  padding: 2rem 1.5rem;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state-block--error {
  color: #b91c1c;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-block__spinner {
  display: inline-block;
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ── 游记卡片网格 ── */
.trip-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

@media (min-width: 640px) {
  .trip-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .trip-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.trip-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.trip-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
  box-shadow:
    0 14px 36px color-mix(in srgb, var(--color-accent) 12%, transparent),
    0 2px 8px color-mix(in srgb, var(--color-text-primary) 6%, transparent);
}

.trip-card__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover, var(--color-accent)));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.trip-card__cover--image {
  background: var(--color-bg-soft);
}

.trip-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s ease;
}

.trip-card:hover .trip-card__cover img {
  transform: scale(1.04);
}

.trip-card__cover-letter {
  font-size: 3rem;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: -0.02em;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

.trip-card__days-badge {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  backdrop-filter: blur(4px);
}

.trip-card__body {
  padding: 1rem 1.1rem 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  flex: 1;
}

.trip-card__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.4;
  color: var(--color-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.trip-card:hover .trip-card__title {
  color: var(--color-accent-text);
}

.trip-card__summary {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.65;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.trip-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.85rem;
  margin-top: auto;
}

.trip-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.trip-card__meta-item :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
}

.trip-card__stats {
  display: flex;
  gap: 0.85rem;
  padding-top: 0.4rem;
  border-top: 1px dashed var(--color-border);
  font-size: 0.74rem;
  color: var(--color-text-muted);
}

.trip-card__stats span {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.trip-card__stats :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
}

/* ── 加载更多 ── */
.load-more {
  display: block;
  width: 100%;
  padding: 0.8rem 1rem;
  border-radius: 0.875rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, transform 0.15s;
}

.load-more:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  transform: translateY(-1px);
}

.load-more:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.end-hint {
  text-align: center;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
  padding: 0.5rem 0 1rem;
}
</style>
