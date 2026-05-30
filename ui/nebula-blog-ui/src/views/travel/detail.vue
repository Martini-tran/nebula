<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchTripDetail, type TravelTripDetail } from '../../api/travel'
import ArticleView from '../../components/article/index.vue'

const route = useRoute()

const trip = ref<TravelTripDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const lightboxOpen = ref(false)
const lightboxImages = ref<string[]>([])
const lightboxIndex = ref(0)

const activeDayId = ref<number | string | null>(null)
const activePostSlug = ref<string | null>(null)
const activePostTitle = ref<string>('')
const galleryView = ref(false)

const slug = computed(() => {
  const value = route.query.slug
  return Array.isArray(value) ? value[0] ?? '' : value ?? ''
})

const totalCheckins = computed(() => {
  if (!trip.value?.days) return 0
  return trip.value.days.reduce((sum, d) => sum + (d.checkins?.length ?? 0), 0)
})

const allPhotos = computed(() => {
  const out: { url: string; from: string }[] = []
  for (const day of trip.value?.days ?? []) {
    for (const c of day.checkins ?? []) {
      const label = c.destination_name || c.custom_name || ''
      for (const url of c.photo_urls ?? []) {
        out.push({ url, from: label })
      }
    }
  }
  return out
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
    if (trip.value?.days?.length) {
      activeDayId.value = trip.value.days[0]?.id ?? null
    }
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
  const t = val.replace('T', ' ')
  const part = t.split(' ')[1]
  return part ? part.slice(0, 5) : ''
}

const formatCost = (val?: number | null, currency?: string | null) => {
  if (val == null) return ''
  const symbol = currency === 'USD' ? '$' : currency === 'EUR' ? '€' : '¥'
  return `${symbol}${Math.round(Number(val))}`
}

const ratingStars = (val?: number | null) => {
  if (val == null) return null
  const v = Math.max(0, Math.min(5, Number(val)))
  const full = Math.floor(v)
  const half = v - full >= 0.5
  return { full, half, empty: 5 - full - (half ? 1 : 0) }
}

const dayAnchorId = (id: number | string) => `day-${id}`

const scrollToDay = (id: number | string) => {
  // 切回行程视图（如果当前在文章预览或相册）
  activePostSlug.value = null
  activePostTitle.value = ''
  galleryView.value = false
  setTimeout(() => {
    const el = document.getElementById(dayAnchorId(id))
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    activeDayId.value = id
  }, 0)
}

const openPost = (slug?: string | null, title?: string | null) => {
  if (!slug) return
  activePostSlug.value = slug
  activePostTitle.value = title ?? ''
  galleryView.value = false
  // 平滑滚到主区顶部
  setTimeout(() => {
    document.querySelector('.main')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 0)
}

const closePost = () => {
  activePostSlug.value = null
  activePostTitle.value = ''
}

const openGallery = () => {
  galleryView.value = true
  activePostSlug.value = null
  activePostTitle.value = ''
  setTimeout(() => {
    document.querySelector('.main')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 0)
}

const closeGallery = () => {
  galleryView.value = false
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

let observer: IntersectionObserver | null = null
const setupObserver = () => {
  observer?.disconnect()
  if (!trip.value?.days?.length) return
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
      if (visible) {
        const id = visible.target.getAttribute('data-day-id')
        if (id != null) activeDayId.value = id
      }
    },
    { rootMargin: '-30% 0px -55% 0px', threshold: [0, 0.25, 0.5, 0.75, 1] },
  )
  // 等 DOM 渲染好
  setTimeout(() => {
    document.querySelectorAll('[data-day-id]').forEach((el) => observer?.observe(el))
  }, 50)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  observer?.disconnect()
})

watch(slug, load)
watch(
  () => trip.value?.days,
  () => setupObserver(),
)
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

      <!-- ── 两栏：左侧目录 / 右侧时间轴 ── -->
      <div class="layout">
        <!-- ── 左：导览 ── -->
        <aside class="sidebar">
          <!-- 行程目录 -->
          <section v-if="trip.days?.length" class="sidebar-card" aria-label="行程目录">
            <header class="sidebar-card__header">
              <Icon icon="lucide:list" />
              <span>行程目录</span>
            </header>
            <ul class="day-toc">
              <li
                v-for="day in trip.days"
                :key="day.id"
                class="day-toc__item"
                :class="{ 'day-toc__item--active': String(day.id) === String(activeDayId) }"
              >
                <button type="button" class="day-toc__btn" @click="scrollToDay(day.id)">
                  <span class="day-toc__num">D{{ day.day_number }}</span>
                  <span class="day-toc__title">{{ day.title || '未命名行程' }}</span>
                  <span v-if="day.checkins?.length" class="day-toc__count">
                    {{ day.checkins.length }}
                  </span>
                </button>
              </li>
            </ul>
          </section>

          <!-- 数据概览 -->
          <section class="sidebar-card sidebar-card--stats" aria-label="概览">
            <div class="stat">
              <span class="stat__value">{{ trip.days_count ?? trip.days?.length ?? 0 }}</span>
              <span class="stat__label">天</span>
            </div>
            <div class="stat">
              <span class="stat__value">{{ totalCheckins }}</span>
              <span class="stat__label">打卡</span>
            </div>
            <div class="stat">
              <span class="stat__value">{{ allPhotos.length }}</span>
              <span class="stat__label">照片</span>
            </div>
          </section>

          <!-- 足迹 -->
          <section v-if="trip.destinations?.length" class="sidebar-card" aria-label="足迹">
            <header class="sidebar-card__header">
              <Icon icon="lucide:map-pin" />
              <span>足迹</span>
            </header>
            <div class="footprint__chips">
              <span v-for="d in trip.destinations" :key="d.id" class="footprint__chip">
                {{ d.name }}
              </span>
            </div>
          </section>

          <!-- 关联文章 -->
          <section v-if="trip.posts?.length" class="sidebar-card" aria-label="关联文章">
            <header class="sidebar-card__header">
              <Icon icon="lucide:link-2" />
              <span>关联文章</span>
            </header>
            <ul class="related-posts__list">
              <li v-for="p in trip.posts" :key="p.post_id">
                <button
                  type="button"
                  class="related-post"
                  :class="{ 'related-post--active': activePostSlug === p.slug }"
                  @click="openPost(p.slug, p.title)"
                >
                  <div
                    class="related-post__cover"
                    :class="{ 'related-post__cover--image': !!p.cover_url }"
                    aria-hidden="true"
                  >
                    <img v-if="p.cover_url" :src="p.cover_url" alt="">
                    <Icon v-else icon="lucide:file-text" class="related-post__cover-icon" />
                  </div>
                  <div class="related-post__body">
                    <span
                      v-if="p.post_type === 0"
                      class="related-post__badge related-post__badge--primary"
                    >主要</span>
                    <h3 class="related-post__title">{{ p.title }}</h3>
                    <p v-if="p.summary" class="related-post__summary">{{ p.summary }}</p>
                  </div>
                </button>
              </li>
            </ul>
          </section>

          <!-- 照片合辑入口 -->
          <section
            v-if="allPhotos.length"
            class="sidebar-card sidebar-card--gallery"
            :class="{ 'sidebar-card--active': galleryView }"
            aria-label="照片合辑"
          >
            <button type="button" class="gallery-entry" @click="openGallery">
              <header class="sidebar-card__header gallery-entry__header">
                <Icon icon="lucide:image" />
                <span>照片合辑</span>
                <span class="gallery-entry__count">{{ allPhotos.length }}</span>
              </header>
              <div class="gallery-entry__preview">
                <span
                  v-for="(p, idx) in allPhotos.slice(0, 9)"
                  :key="idx"
                  class="gallery-entry__cell"
                >
                  <img :src="p.url" :alt="p.from" loading="lazy" />
                </span>
                <span
                  v-if="allPhotos.length > 9"
                  class="gallery-entry__more"
                >
                  +{{ allPhotos.length - 9 }}
                </span>
              </div>
              <span class="gallery-entry__cta">
                查看全部
                <Icon icon="lucide:arrow-right" />
              </span>
            </button>
          </section>
        </aside>

        <!-- ── 右：行程时间轴 / 文章预览 ── -->
        <main class="main">
          <!-- 文章预览态 -->
          <section v-if="activePostSlug" class="post-panel" aria-label="文章预览">
            <header class="post-panel__bar">
              <button type="button" class="post-panel__back" @click="closePost">
                <Icon icon="lucide:arrow-left" />
                返回行程
              </button>
              <span v-if="activePostTitle" class="post-panel__title">
                {{ activePostTitle }}
              </span>
            </header>
            <div class="post-panel__body">
              <ArticleView :slug="activePostSlug" hide-toc />
            </div>
          </section>

          <!-- 照片合辑视图 -->
          <section
            v-else-if="galleryView"
            class="post-panel gallery-panel"
            aria-label="照片合辑"
          >
            <header class="post-panel__bar">
              <button type="button" class="post-panel__back" @click="closeGallery">
                <Icon icon="lucide:arrow-left" />
                返回行程
              </button>
              <span class="post-panel__title">
                <Icon icon="lucide:image" />
                照片合辑 · {{ allPhotos.length }} 张
              </span>
            </header>
            <div class="gallery gallery--full">
              <button
                v-for="(p, idx) in allPhotos"
                :key="idx"
                type="button"
                class="gallery__item"
                @click="openLightbox(allPhotos.map((g) => g.url), idx)"
              >
                <img :src="p.url" :alt="p.from" loading="lazy" />
              </button>
            </div>
          </section>

          <!-- 行程视图 -->
          <template v-else>
          <section v-if="trip.days?.length" class="timeline" aria-label="行程">
            <article
              v-for="day in trip.days"
              :key="day.id"
              :id="dayAnchorId(day.id)"
              :data-day-id="day.id"
              class="day"
            >
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

                <div
                  v-if="day.accommodation || day.meal_cost || day.transport_cost || day.other_cost"
                  class="day__meta"
                >
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
                  <article v-for="(c, idx) in day.checkins" :key="c.id" class="checkin">
                    <div class="checkin__seq" aria-hidden="true">{{ idx + 1 }}</div>
                    <div class="checkin__content">
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
                          v-for="(url, pIdx) in c.photo_urls"
                          :key="pIdx"
                          type="button"
                          class="checkin__photo"
                          @click="openLightbox(c.photo_urls, pIdx)"
                        >
                          <img :src="url" :alt="`${c.destination_name || c.custom_name || ''} 照片 ${pIdx + 1}`" loading="lazy" />
                        </button>
                      </div>
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
        </main>
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
  max-width: 1180px;
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

/* ── 两栏布局 ── */
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  align-items: flex-start;
}

@media (min-width: 1024px) {
  .layout {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

@media (min-width: 1024px) {
  .sidebar {
    position: sticky;
    top: 1rem;
    max-height: calc(100vh - 2rem);
    overflow-y: auto;
    padding-right: 2px;
    scrollbar-width: thin;
  }
}

.main {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

/* ── 侧栏卡片 ── */
.sidebar-card {
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  padding: 0.85rem 0.95rem 0.95rem;
}

.sidebar-card__header {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  margin-bottom: 0.65rem;
}

.sidebar-card__header :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-accent);
}

/* ── 行程目录 ── */
.day-toc {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.day-toc__btn {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0.5rem 0.55rem;
  border-radius: 0.6rem;
  cursor: pointer;
  text-align: left;
  color: var(--color-text-secondary);
  transition: background 0.15s, color 0.15s;
}

.day-toc__btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.day-toc__item--active .day-toc__btn {
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  color: var(--color-accent-text);
}

.day-toc__num {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2rem;
  height: 1.5rem;
  padding: 0 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.day-toc__item--active .day-toc__num {
  background: var(--color-accent);
  color: #fff;
}

.day-toc__title {
  flex: 1;
  font-size: 0.85rem;
  font-weight: 600;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-toc__count {
  flex-shrink: 0;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── 概览统计 ── */
.sidebar-card--stats {
  display: flex;
  align-items: center;
  justify-content: space-around;
  text-align: center;
  padding: 0.95rem 0.5rem;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--color-accent) 5%, var(--color-bg-surface)), var(--color-bg-surface));
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.stat__value {
  font-size: 1.4rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.stat__label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

/* ── 足迹 ── */
.footprint__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.footprint__chip {
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  border-radius: 999px;
  padding: 0.25rem 0.65rem;
}

/* ── 关联文章 ── */
.related-posts__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.related-post {
  display: flex;
  align-items: stretch;
  gap: 0.65rem;
  width: 100%;
  padding: 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 0.75rem;
  background: var(--color-bg-surface);
  text-align: left;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  font: inherit;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.related-post:hover {
  background: var(--color-bg-soft);
  border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
  transform: translateY(-1px);
}

.related-post--active,
.related-post--active:hover {
  background: color-mix(in srgb, var(--color-accent) 10%, var(--color-bg-surface));
  border-color: color-mix(in srgb, var(--color-accent) 55%, var(--color-border));
  transform: none;
}

.related-post__cover {
  flex-shrink: 0;
  width: 3rem;
  height: 3rem;
  border-radius: 0.55rem;
  overflow: hidden;
  background: linear-gradient(135deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 60%, var(--color-bg-soft)));
  display: flex;
  align-items: center;
  justify-content: center;
}

.related-post__cover--image {
  background: var(--color-bg-soft);
}

.related-post__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.related-post__cover-icon {
  width: 1.2rem;
  height: 1.2rem;
  color: rgba(255, 255, 255, 0.9);
}

.related-post__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.related-post__badge {
  align-self: flex-start;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
}

.related-post__badge--primary {
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
}

.related-post__title {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.related-post:hover .related-post__title {
  color: var(--color-accent-text);
}

.related-post__summary {
  margin: 0;
  font-size: 0.72rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ── 文章预览面板 ── */
.post-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 1.1rem;
  background: var(--color-bg-surface);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.post-panel__bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.post-panel__back {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0;
  background: transparent;
  padding: 0.35rem 0.6rem;
  border-radius: 0.5rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.post-panel__back:hover {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
}

.post-panel__back :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
}

.post-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-panel__title :deep(svg) {
  width: 0.95rem;
  height: 0.95rem;
  color: var(--color-accent);
}

.post-panel__body {
  padding: 0.5rem 0.5rem 1rem;
}

/* 嵌入文章组件时去掉它自身的两栏布局 */
.post-panel__body :deep(.article-layout) {
  display: block;
  max-width: none;
  margin: 0;
  padding: 0;
}

.post-panel__body :deep(.article-toc) {
  display: none;
}

.post-panel__body :deep(.article-main) {
  max-width: none;
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
  scroll-margin-top: 1rem;
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
  font-size: 1.1rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.4;
}

.day__desc {
  margin: 0 0 0.75rem;
  font-size: 0.9rem;
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
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.day__meta-item :deep(svg) {
  width: 0.85rem;
  height: 0.85rem;
}

/* ── 打卡点（每条带序号节点） ── */
.checkin-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  margin-top: 0.5rem;
}

.checkin {
  display: flex;
  gap: 0.75rem;
}

.checkin__seq {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  margin-top: 0.15rem;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  color: var(--color-accent-text);
  font-size: 0.74rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.checkin__content {
  flex: 1;
  min-width: 0;
  border-left: 2px solid color-mix(in srgb, var(--color-accent) 25%, var(--color-border));
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
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
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

/* ── 侧栏照片合辑入口 ── */
.sidebar-card--gallery {
  padding: 0;
  overflow: hidden;
}

.sidebar-card--gallery.sidebar-card--active {
  border-color: color-mix(in srgb, var(--color-accent) 55%, var(--color-border));
  background: color-mix(in srgb, var(--color-accent) 8%, var(--color-bg-surface));
}

.gallery-entry {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0.85rem 0.95rem 0.95rem;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: background 0.15s;
}

.gallery-entry:hover {
  background: var(--color-bg-soft);
}

.gallery-entry__header {
  margin-bottom: 0;
}

.gallery-entry__count {
  margin-left: auto;
  padding: 0.1rem 0.5rem;
  background: var(--color-bg-soft);
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--color-text-muted);
  letter-spacing: 0;
  text-transform: none;
  font-variant-numeric: tabular-nums;
}

.sidebar-card--gallery.sidebar-card--active .gallery-entry__count {
  background: var(--color-accent);
  color: #fff;
}

.gallery-entry__preview {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  border-radius: 0.55rem;
  overflow: hidden;
  background: var(--color-bg-soft);
}

.gallery-entry__cell {
  display: block;
  aspect-ratio: 1 / 1;
  background: var(--color-bg-soft);
  overflow: hidden;
}

.gallery-entry__cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.gallery-entry:hover .gallery-entry__cell img {
  transform: scale(1.04);
}

.gallery-entry__more {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 0 0.65rem 0.5rem 0;
  background: linear-gradient(
    180deg,
    transparent 0%,
    transparent 55%,
    rgba(0, 0, 0, 0.5) 100%
  );
  color: #fff;
  font-size: 0.95rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  pointer-events: none;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
}

.gallery-entry__cta {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  align-self: flex-end;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  transition: color 0.15s, transform 0.15s;
}

.gallery-entry:hover .gallery-entry__cta {
  color: var(--color-accent-text);
  transform: translateX(2px);
}

.gallery-entry__cta :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
}

/* ── 主区照片合辑视图 ── */
.gallery-panel .gallery--full {
  padding: 0.85rem;
}

.gallery--full {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 0.45rem;
}

.gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 0.45rem;
}

.gallery__item {
  aspect-ratio: 1 / 1;
  border: 0;
  padding: 0;
  margin: 0;
  border-radius: 0.55rem;
  overflow: hidden;
  cursor: zoom-in;
  background: var(--color-bg-soft);
  transition: transform 0.15s, box-shadow 0.15s;
}

.gallery__item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px color-mix(in srgb, var(--color-text-primary) 12%, transparent);
}

.gallery__item img {
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
