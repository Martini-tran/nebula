<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchTripDetail, type TravelTripDetail } from '../../api/travel'

const route = useRoute()

const trip = ref<TravelTripDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const lightboxOpen = ref(false)
const lightboxImages = ref<string[]>([])
const lightboxIndex = ref(0)

const slug = computed(() => {
  const value = route.query.slug
  return Array.isArray(value) ? value[0] ?? '' : value ?? ''
})

const load = async () => {
  if (!slug.value) {
    error.value = '缺少游记标识'
    return
  }
  loading.value = true
  error.value = null
  try {
    const data = await fetchTripDetail(String(slug.value))
    trip.value = data ?? null
    if (!trip.value) error.value = '游记不存在'
  } catch (e) {
    trip.value = null
    error.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const formatDate = (val?: string | null) => {
  if (!val) return ''
  const [y, m, d] = val.split('-')
  return y && m && d ? `${y}.${m}.${d}` : val
}

const formatDateRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) return ''
  if (start && end) return `${formatDate(start)} – ${formatDate(end)}`
  return formatDate(start || end)
}

const formatTime = (val?: string | null) => {
  if (!val) return ''
  // backend returns "yyyy-MM-dd HH:mm:ss"
  const t = val.replace('T', ' ')
  const part = t.split(' ')[1]
  return part ? part.slice(0, 5) : ''
}

const formatCost = (val?: number | null, currency?: string | null) => {
  if (val == null) return ''
  const symbol = currency === 'USD' ? '$' : currency === 'EUR' ? '€' : '¥'
  return `${symbol}${Math.round(Number(val))}`
}

const sumDayCost = (day: { meal_cost?: number | null; transport_cost?: number | null; other_cost?: number | null }) => {
  const total = (Number(day.meal_cost) || 0) + (Number(day.transport_cost) || 0) + (Number(day.other_cost) || 0)
  return total > 0 ? total : null
}

const ratingStars = (val?: number | null) => {
  if (val == null) return null
  const v = Math.max(0, Math.min(5, Number(val)))
  const full = Math.floor(v)
  const half = v - full >= 0.5
  return { full, half, empty: 5 - full - (half ? 1 : 0) }
}

const openLightbox = (urls: string[], index: number) => {
  lightboxImages.value = urls
  lightboxIndex.value = index
  lightboxOpen.value = true
}

const closeLightbox = () => {
  lightboxOpen.value = false
}

const lightboxPrev = () => {
  if (!lightboxImages.value.length) return
  lightboxIndex.value = (lightboxIndex.value - 1 + lightboxImages.value.length) % lightboxImages.value.length
}

const lightboxNext = () => {
  if (!lightboxImages.value.length) return
  lightboxIndex.value = (lightboxIndex.value + 1) % lightboxImages.value.length
}

const onKeydown = (e: KeyboardEvent) => {
  if (!lightboxOpen.value) return
  if (e.key === 'Escape') closeLightbox()
  else if (e.key === 'ArrowLeft') lightboxPrev()
  else if (e.key === 'ArrowRight') lightboxNext()
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

watch(slug, load)
</script>

<template>
  <div class="trip-detail-page">
    <!-- ── 返回链接 ── -->
    <div class="trip-back">
      <RouterLink to="/travel" class="trip-back__link">
        <Icon icon="lucide:arrow-left" />
        返回旅行
      </RouterLink>
    </div>

    <!-- ── 加载/错误态 ── -->
    <div v-if="loading" class="state-block">
      <span class="state-block__spinner" aria-hidden="true" />
      正在加载…
    </div>
    <div v-else-if="error" class="state-block state-block--error">{{ error }}</div>

    <template v-else-if="trip">
      <!-- ── Hero ── -->
      <header class="trip-hero" :class="{ 'trip-hero--with-cover': !!trip.cover_url }">
        <div v-if="trip.cover_url" class="trip-hero__cover">
          <img :src="trip.cover_url" :alt="trip.title" />
          <div class="trip-hero__cover-mask" />
        </div>
        <div class="trip-hero__content">
          <p class="trip-hero__eyebrow">Travel Log</p>
          <h1 class="trip-hero__title">{{ trip.title }}</h1>
          <p v-if="trip.summary" class="trip-hero__summary">{{ trip.summary }}</p>

          <div class="trip-hero__meta">
            <span v-if="trip.start_date || trip.end_date" class="trip-hero__meta-item">
              <Icon icon="lucide:calendar" />
              {{ formatDateRange(trip.start_date, trip.end_date) }}
            </span>
            <span v-if="trip.days_count" class="trip-hero__meta-item">
              <Icon icon="lucide:clock" />
              {{ trip.days_count }} 天
            </span>
            <span v-if="trip.persons" class="trip-hero__meta-item">
              <Icon icon="lucide:users" />
              {{ trip.persons }} 人
            </span>
            <span v-if="trip.cost_total != null" class="trip-hero__meta-item">
              <Icon icon="lucide:wallet" />
              {{ formatCost(trip.cost_total, trip.cost_currency) }}
            </span>
            <span class="trip-hero__meta-item">
              <Icon icon="lucide:eye" />
              {{ trip.view_count ?? 0 }}
            </span>
            <span class="trip-hero__meta-item">
              <Icon icon="lucide:heart" />
              {{ trip.like_count ?? 0 }}
            </span>
          </div>
        </div>
      </header>

      <!-- ── 足迹 ── -->
      <section v-if="trip.destinations?.length" class="footprint" aria-label="足迹">
        <span class="footprint__label">
          <Icon icon="lucide:map-pin" />
          足迹
        </span>
        <div class="footprint__chips">
          <span v-for="d in trip.destinations" :key="d.id" class="footprint__chip">
            {{ d.name }}
          </span>
        </div>
      </section>

      <!-- ── 行程时间轴 ── -->
      <section v-if="trip.days?.length" class="timeline" aria-label="行程">
        <article v-for="day in trip.days" :key="day.id" class="day">
          <div class="day__rail" aria-hidden="true">
            <span class="day__dot" />
            <span class="day__line" />
          </div>

          <div class="day__body">
            <header class="day__header">
              <span class="day__badge">DAY {{ day.day_number }}</span>
              <h2 v-if="day.title" class="day__title">{{ day.title }}</h2>
            </header>

            <p v-if="day.description" class="day__desc">{{ day.description }}</p>

            <div v-if="day.accommodation || sumDayCost(day) != null" class="day__meta">
              <span v-if="day.accommodation" class="day__meta-item">
                <Icon icon="lucide:bed" />
                {{ day.accommodation }}
              </span>
              <span v-if="day.meal_cost" class="day__meta-item">
                <Icon icon="lucide:utensils" />
                餐 {{ formatCost(day.meal_cost, trip.cost_currency) }}
              </span>
              <span v-if="day.transport_cost" class="day__meta-item">
                <Icon icon="lucide:bus" />
                行 {{ formatCost(day.transport_cost, trip.cost_currency) }}
              </span>
              <span v-if="day.other_cost" class="day__meta-item">
                <Icon icon="lucide:more-horizontal" />
                其他 {{ formatCost(day.other_cost, trip.cost_currency) }}
              </span>
            </div>

            <!-- 打卡点 -->
            <div v-if="day.checkins?.length" class="checkin-list">
              <article v-for="c in day.checkins" :key="c.id" class="checkin">
                <header class="checkin__header">
                  <h3 class="checkin__name">
                    <Icon icon="lucide:map-pin" class="checkin__name-icon" />
                    {{ c.destination_name || c.custom_name || '未命名地点' }}
                  </h3>
                  <span v-if="c.arrival_time || c.departure_time" class="checkin__time">
                    <template v-if="c.arrival_time && c.departure_time">
                      {{ formatTime(c.arrival_time) }} – {{ formatTime(c.departure_time) }}
                    </template>
                    <template v-else>
                      {{ formatTime(c.arrival_time || c.departure_time) }}
                    </template>
                  </span>
                </header>

                <div v-if="ratingStars(c.rating)" class="checkin__rating" :aria-label="`评分 ${c.rating}`">
                  <Icon
                    v-for="i in ratingStars(c.rating)!.full"
                    :key="`f${i}`"
                    icon="lucide:star"
                    class="checkin__star checkin__star--full"
                  />
                  <Icon
                    v-if="ratingStars(c.rating)!.half"
                    icon="lucide:star-half"
                    class="checkin__star checkin__star--full"
                  />
                  <Icon
                    v-for="i in ratingStars(c.rating)!.empty"
                    :key="`e${i}`"
                    icon="lucide:star"
                    class="checkin__star"
                  />
                </div>

                <p v-if="c.notes" class="checkin__notes">{{ c.notes }}</p>

                <div v-if="c.photo_urls?.length" class="checkin__photos">
                  <button
                    v-for="(url, idx) in c.photo_urls"
                    :key="idx"
                    type="button"
                    class="checkin__photo"
                    @click="openLightbox(c.photo_urls, idx)"
                  >
                    <img :src="url" :alt="`${c.destination_name || c.custom_name || ''} 照片 ${idx + 1}`" loading="lazy" />
                  </button>
                </div>
              </article>
            </div>
          </div>
        </article>
      </section>

      <div v-else class="state-block">
        作者还没有写下行程
      </div>
    </template>

    <!-- ── Lightbox ── -->
    <div
      v-if="lightboxOpen"
      class="lightbox"
      role="dialog"
      aria-modal="true"
      @click.self="closeLightbox"
    >
      <button type="button" class="lightbox__close" aria-label="关闭" @click="closeLightbox">
        <Icon icon="lucide:x" />
      </button>
      <button
        v-if="lightboxImages.length > 1"
        type="button"
        class="lightbox__nav lightbox__nav--prev"
        aria-label="上一张"
        @click="lightboxPrev"
      >
        <Icon icon="lucide:chevron-left" />
      </button>
      <img
        :src="lightboxImages[lightboxIndex]"
        class="lightbox__img"
        :alt="`照片 ${lightboxIndex + 1} / ${lightboxImages.length}`"
      >
      <button
        v-if="lightboxImages.length > 1"
        type="button"
        class="lightbox__nav lightbox__nav--next"
        aria-label="下一张"
        @click="lightboxNext"
      >
        <Icon icon="lucide:chevron-right" />
      </button>
      <span v-if="lightboxImages.length > 1" class="lightbox__counter">
        {{ lightboxIndex + 1 }} / {{ lightboxImages.length }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.trip-detail-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  max-width: 880px;
  margin: 0 auto;
  width: 100%;
}

/* ── 返回 ── */
.trip-back {
  font-size: 0.85rem;
}

.trip-back__link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--color-text-muted);
  text-decoration: none;
  transition: color 0.15s;
}

.trip-back__link:hover {
  color: var(--color-text-primary);
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

/* ── Hero ── */
.trip-hero {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.5rem;
  background:
    radial-gradient(circle at 78% 20%, rgba(14, 165, 233, 0.18), transparent 32%),
    radial-gradient(circle at 18% 80%, color-mix(in srgb, var(--color-accent) 20%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.trip-hero__cover {
  position: relative;
  aspect-ratio: 21 / 9;
  overflow: hidden;
}

.trip-hero__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.trip-hero__cover-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 0%, transparent 40%, rgba(0, 0, 0, 0.45) 100%);
}

.trip-hero__content {
  padding: clamp(1.4rem, 3.5vw, 2.4rem);
}

.trip-hero--with-cover .trip-hero__content {
  position: relative;
  margin-top: -3.5rem;
  padding-top: 1rem;
}

.trip-hero__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.trip-hero__title {
  margin: 0 0 0.6rem;
  font-size: clamp(1.7rem, 5vw, 2.6rem);
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.15;
  color: var(--color-text-primary);
}

.trip-hero__summary {
  margin: 0 0 1.1rem;
  max-width: 38rem;
  font-size: 0.95rem;
  line-height: 1.75;
  color: var(--color-text-secondary);
}

.trip-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem 1.1rem;
}

.trip-hero__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.trip-hero__meta-item :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
}

/* ── 足迹 ── */
.footprint {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  padding: 0.75rem 1rem;
  overflow: hidden;
}

.footprint__label {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.footprint__label :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
}

.footprint__chips {
  display: flex;
  flex-wrap: nowrap;
  gap: 0.4rem;
  overflow-x: auto;
  scrollbar-width: none;
}

.footprint__chips::-webkit-scrollbar {
  display: none;
}

.footprint__chip {
  flex-shrink: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  border-radius: 999px;
  padding: 0.3rem 0.7rem;
}

/* ── 时间轴 ── */
.timeline {
  display: flex;
  flex-direction: column;
}

.day {
  display: flex;
  gap: 1rem;
  padding-bottom: 1.5rem;
}

.day:last-child {
  padding-bottom: 0;
}

.day__rail {
  position: relative;
  flex-shrink: 0;
  width: 1.25rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 0.4rem;
}

.day__dot {
  width: 0.85rem;
  height: 0.85rem;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 18%, transparent);
  z-index: 1;
}

.day__line {
  flex: 1;
  width: 2px;
  margin-top: 0.4rem;
  background: linear-gradient(180deg, color-mix(in srgb, var(--color-accent) 40%, transparent), var(--color-border));
  border-radius: 2px;
}

.day:last-child .day__line {
  display: none;
}

.day__body {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 1.1rem;
  background: var(--color-bg-surface);
  padding: 1.1rem 1.2rem 1.2rem;
  box-shadow: var(--shadow-sm);
}

.day__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.55rem;
  margin-bottom: 0.5rem;
}

.day__badge {
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  border-radius: 999px;
  padding: 0.22rem 0.6rem;
}

.day__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.4;
}

.day__desc {
  margin: 0 0 0.75rem;
  font-size: 0.88rem;
  line-height: 1.75;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.day__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.85rem;
  margin-bottom: 0.85rem;
  padding: 0.55rem 0.7rem;
  background: var(--color-bg-soft);
  border-radius: 0.7rem;
}

.day__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.day__meta-item :deep(svg) {
  width: 0.85rem;
  height: 0.85rem;
}

/* ── 打卡点 ── */
.checkin-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  margin-top: 0.5rem;
}

.checkin {
  border-left: 2px solid color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  padding: 0.1rem 0 0.1rem 0.85rem;
}

.checkin__header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.checkin__name {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.checkin__name-icon {
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-accent);
}

.checkin__time {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.checkin__rating {
  display: inline-flex;
  gap: 0.1rem;
  margin-top: 0.3rem;
}

.checkin__star {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--color-border);
}

.checkin__star--full {
  color: #f59e0b;
}

.checkin__notes {
  margin: 0.45rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.checkin__photos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 0.4rem;
  margin-top: 0.6rem;
}

.checkin__photo {
  aspect-ratio: 1 / 1;
  border: 0;
  padding: 0;
  margin: 0;
  border-radius: 0.6rem;
  overflow: hidden;
  cursor: zoom-in;
  background: var(--color-bg-soft);
  transition: transform 0.15s, box-shadow 0.15s;
}

.checkin__photo:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px color-mix(in srgb, var(--color-text-primary) 12%, transparent);
}

.checkin__photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ── Lightbox ── */
.lightbox {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.88);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.lightbox__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 0.5rem;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}

.lightbox__close,
.lightbox__nav {
  position: absolute;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.15s;
}

.lightbox__close:hover,
.lightbox__nav:hover {
  background: rgba(255, 255, 255, 0.22);
  transform: scale(1.05);
}

.lightbox__close {
  top: 1.25rem;
  right: 1.25rem;
  width: 2.4rem;
  height: 2.4rem;
}

.lightbox__close :deep(svg) {
  width: 1.2rem;
  height: 1.2rem;
}

.lightbox__nav {
  top: 50%;
  transform: translateY(-50%);
  width: 2.8rem;
  height: 2.8rem;
}

.lightbox__nav--prev {
  left: 1.25rem;
}

.lightbox__nav--next {
  right: 1.25rem;
}

.lightbox__nav :deep(svg) {
  width: 1.5rem;
  height: 1.5rem;
}

.lightbox__counter {
  position: absolute;
  bottom: 1.5rem;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.85);
  font-variant-numeric: tabular-nums;
}
</style>
