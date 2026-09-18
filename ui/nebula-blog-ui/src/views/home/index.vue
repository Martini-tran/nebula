<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  fetchArticles,
  fetchEssays,
  type PostListItem,
} from '../../api/post'
import { fetchTrips, type TravelTripListItem } from '../../api/travel'
import { fetchSeriesList, type SeriesListItem } from '../../api/series'

type Channel = 'all' | 'engineering' | 'essay' | 'travel' | 'handbook'

type RouteStop = {
  id: string
  title: string
  summary: string
  channel: Exclude<Channel, 'all'>
  channelLabel: string
  publishedAt: string
  dateLabel: string
  durationLabel: string | null
  viewCount: number
  href: string
  isFeatured: boolean
}

type SeriesProgress = {
  id: string
  name: string
  articleCount: number
  isFinished: boolean
  progress: number
  href: string
}

type TravelFeature = {
  title: string
  summary: string
  dateLabel: string
  daysLabel: string
  viewCount: number
  coverUrl?: string | null
  href: string
}

const routeStops = ref<RouteStop[]>([])
const seriesItems = ref<SeriesProgress[]>([])
const travelFeature = ref<TravelFeature | null>(null)
const selectedChannel = ref<Channel>('all')
const isLoading = ref(true)
const travelImageBroken = ref(false)

const normalizeDate = (value?: string | null) => {
  if (!value) return ''
  const parsed = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return value.slice(0, 10)
  return String(parsed.getFullYear()) + '-' + String(parsed.getMonth() + 1).padStart(2, '0') + '-' + String(parsed.getDate()).padStart(2, '0')
}

const toTimestamp = (value?: string | null) => {
  if (!value) return 0
  const timestamp = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

const channelForPost = (item: PostListItem): Exclude<Channel, 'all'> => {
  const names = [...(item.categories ?? []), ...(item.tags ?? [])]
    .map((entry) => entry.name + ' ' + entry.slug)
    .join(' ')
    .toLowerCase()
  if (item.postType === 'essay') return 'essay'
  if (names.includes('旅行') || names.includes('travel')) return 'travel'
  if (names.includes('宝典') || names.includes('handbook')) return 'handbook'
  return 'engineering'
}

const postToStop = (item: PostListItem, index: number): RouteStop => {
  const channel = channelForPost(item)
  const categoryName = item.categories?.[0]?.name
  const channelLabels: Record<Exclude<Channel, 'all'>, string> = {
    engineering: '工程',
    essay: '随笔',
    travel: '旅行',
    handbook: '宝典',
  }
  return {
    id: 'post-' + String(item.id),
    title: item.title,
    summary: item.summary?.trim() || '这篇记录暂时没有摘要，打开节点查看完整内容。',
    channel,
    channelLabel: categoryName || channelLabels[channel],
    publishedAt: item.publishedAt,
    dateLabel: normalizeDate(item.publishedAt),
    durationLabel: null,
    viewCount: Number(item.viewCount) || 0,
    href: '/article?slug=' + encodeURIComponent(item.slug) + '&type=' + (item.postType === 'essay' ? 'essay' : 'article'),
    isFeatured: index === 0,
  }
}

const tripToStop = (item: TravelTripListItem, index: number): RouteStop => ({
  id: 'trip-' + String(item.id),
  title: item.title,
  summary: item.summary?.trim() || '一段旅行记录，等待从路线上重新打开。',
  channel: 'travel',
  channelLabel: '旅行',
  publishedAt: item.publishedAt,
  dateLabel: normalizeDate(item.publishedAt),
  durationLabel: item.daysCount ? String(item.daysCount) + ' 天' : null,
  viewCount: Number(item.viewCount) || 0,
  href: '/travel/detail?slug=' + encodeURIComponent(item.slug),
  isFeatured: index === 0,
})

const listItems = <T,>(result: PromiseSettledResult<{ items: T[]; nextCursor?: string | null }>) => {
  if (result.status !== 'fulfilled') return []
  return result.value?.items ?? []
}

const seriesToProgress = (items: SeriesListItem[]): SeriesProgress[] => {
  const maxCount = Math.max(...items.map((item) => Number(item.articleCount) || 0), 1)
  return items.map((item) => {
    const articleCount = Number(item.articleCount) || 0
    return {
      id: 'series-' + String(item.id),
      name: item.name,
      articleCount,
      isFinished: Boolean(item.isFinished),
      progress: item.isFinished ? 100 : Math.max(18, Math.round((articleCount / maxCount) * 82)),
      href: '/series/' + encodeURIComponent(item.slug),
    }
  })
}

const tripToFeature = (item: TravelTripListItem): TravelFeature => ({
  title: item.title,
  summary: item.summary?.trim() || '带一台笔记本出发，记录那些在路上才想明白的事。',
  dateLabel: normalizeDate(item.publishedAt),
  daysLabel: item.daysCount ? String(item.daysCount) + ' 天' : '旅行记录',
  viewCount: Number(item.viewCount) || 0,
  coverUrl: item.coverUrl,
  href: '/travel/detail?slug=' + encodeURIComponent(item.slug),
})

const loadAtlasData = async () => {
  isLoading.value = true
  travelImageBroken.value = false

  const [articlesResult, essaysResult, tripsResult, seriesResult] = await Promise.allSettled([
    fetchArticles({ limit: 8 }),
    fetchEssays({ limit: 4 }),
    fetchTrips({ limit: 3 }),
    fetchSeriesList({ limit: 4 }),
  ])

  const articles = listItems(articlesResult)
  const essays = listItems(essaysResult)
  const trips = listItems(tripsResult)
  const series = listItems(seriesResult)

  const seen = new Set<string>()
  const realStops = articles
    .map((item, index) => postToStop(item, index))
    .concat(essays.map((item, index) => postToStop({ ...item, postType: 'essay' }, index)))
    .concat(trips.map((item, index) => tripToStop(item, index)))
    .filter((item) => {
      if (seen.has(item.id)) return false
      seen.add(item.id)
      return true
    })
    .sort((a, b) => toTimestamp(b.publishedAt) - toTimestamp(a.publishedAt))

  routeStops.value = realStops.slice(0, 8)
  routeStops.value.forEach((item, index) => {
    item.isFeatured = index === 0
  })
  seriesItems.value = seriesToProgress(series)
  travelFeature.value = trips[0] ? tripToFeature(trips[0]) : null
  isLoading.value = false
}

const filteredStops = computed(() => {
  if (selectedChannel.value === 'all') return routeStops.value
  return routeStops.value.filter((stop) => stop.channel === selectedChannel.value)
})

const channelOptions = computed(() => {
  const countFor = (channel: Channel) => channel === 'all'
    ? routeStops.value.length
    : routeStops.value.filter((stop) => stop.channel === channel).length
  return [
    { key: 'all' as const, label: '全部' },
    { key: 'engineering' as const, label: '工程' },
    { key: 'essay' as const, label: '随笔' },
    { key: 'travel' as const, label: '旅行' },
    { key: 'handbook' as const, label: '宝典' },
  ].map((item) => ({ ...item, count: countFor(item.key) }))
})

const routeCount = computed(() => new Set(routeStops.value.map((stop) => stop.channel)).size)
const totalViews = computed(() => routeStops.value.reduce((sum, stop) => sum + stop.viewCount, 0))
const latestDate = computed(() => routeStops.value[0]?.dateLabel || '等待更新')
const routeCountLabel = computed(() => 'newest first / ' + String(filteredStops.value.length).padStart(2, '0') + ' stops')

const stats = computed(() => [
  { value: String(routeStops.value.length).padStart(2, '0'), label: '当前节点' },
  { value: String(routeCount.value).padStart(2, '0'), label: '活跃路线' },
  { value: totalViews.value >= 1000 ? (totalViews.value / 1000).toFixed(1) + 'k' : String(totalViews.value), label: '路线阅读' },
])

const selectChannel = (channel: Channel) => {
  selectedChannel.value = channel
}

const formatViews = (value: number) => value >= 1000 ? (value / 1000).toFixed(1) + 'k' : String(value)

onMounted(loadAtlasData)
</script>

<template>
  <div class="atlas-home">
    <section class="atlas-hero">
      <div class="atlas-wrap hero-grid">
        <div class="hero-copy">
          <p class="hero-eyebrow">field notes / 2023—2026</p>
          <h1>沿着问题<br>继续走。</h1>
          <p class="hero-description">
            把技术文章、随笔和旅行记成一张可以回看的路线图。每个节点都是一次已经走过的现场。
          </p>
          <div class="hero-meta" aria-label="路线概览">
            <span v-for="stat in stats" :key="stat.label">
              <strong>{{ stat.value }}</strong> {{ stat.label }}
            </span>
          </div>
        </div>

        <div class="atlas-map" role="img" aria-label="文章路线示意图">
          <span class="map-contour map-contour--one" aria-hidden="true" />
          <span class="map-contour map-contour--two" aria-hidden="true" />
          <span class="map-route" aria-hidden="true" />
          <span class="map-pin map-pin--a" aria-hidden="true" />
          <span class="map-pin map-pin--b" aria-hidden="true" />
          <span class="map-pin map-pin--c" aria-hidden="true" />
          <span class="map-label map-label--a">大理 / 08.2026</span>
          <span class="map-label map-label--b">编排 / 09.2026</span>
          <span class="map-label map-label--c">RAG / 09.2026</span>
          <span class="map-caption">N25° 41' · E100° 10' · FIELD ROUTE</span>
        </div>
      </div>
    </section>

    <main class="atlas-wrap atlas-body">
      <aside class="filter-rail">
        <div class="rail-sticky">
          <h2>路线筛选</h2>
          <div class="filters" role="group" aria-label="路线筛选">
            <button
              v-for="option in channelOptions"
              :key="option.key"
              class="filter-button"
              :class="{ 'filter-button--active': selectedChannel === option.key }"
              type="button"
              :aria-pressed="selectedChannel === option.key"
              @click="selectChannel(option.key)"
            >
              <span>{{ option.label }}</span><span class="filter-count">{{ String(option.count).padStart(2, '0') }}</span>
            </button>
          </div>
          <div class="rail-note">
            <strong>{{ latestDate }}</strong>
            <p>最后一次更新<br>行进记录</p>
          </div>
        </div>
      </aside>

      <section id="route" class="route-column" aria-labelledby="route-title">
        <div class="section-head">
          <h2 id="route-title">行进记录</h2>
          <span>{{ isLoading ? 'loading' : routeCountLabel }}</span>
        </div>

        <div v-if="isLoading" class="timeline-state" aria-live="polite">正在读取路线...</div>
        <div v-else-if="filteredStops.length === 0" class="timeline-state">该路线暂时没有节点。</div>
        <div v-else class="route-list">
          <article
            v-for="stop in filteredStops"
            :key="stop.id"
            class="route-stop"
            :class="{ 'route-stop--featured': stop.isFeatured }"
          >
            <div class="stop-meta">
              <span class="stop-channel">{{ stop.channelLabel }}</span>
              <span>{{ stop.dateLabel }}</span>
              <span v-if="stop.durationLabel">{{ stop.durationLabel }}</span>
            </div>
            <h3>{{ stop.title }}</h3>
            <p>{{ stop.summary }}</p>
            <div class="stop-actions">
              <RouterLink :to="stop.href">
                打开节点 <span aria-hidden="true">↗</span>
              </RouterLink>
              <span>{{ formatViews(stop.viewCount) }} 阅读</span>
            </div>
          </article>
        </div>
      </section>

      <aside class="side-rail">
        <h2>现场记录</h2>
        <template v-if="travelFeature">
          <RouterLink class="travel-feature" :to="travelFeature.href">
            <div class="travel-visual">
              <img
                v-if="travelFeature.coverUrl && !travelImageBroken"
                :src="travelFeature.coverUrl"
                :alt="travelFeature.title"
                @error="travelImageBroken = true"
              >
              <span class="travel-visual-label">FIELD NOTE / TRAVEL</span>
            </div>
            <div class="travel-copy">
              <strong>{{ travelFeature.title }}</strong>
              <p>{{ travelFeature.summary }}</p>
              <span class="travel-meta">{{ travelFeature.dateLabel }} · {{ travelFeature.daysLabel }} · {{ formatViews(travelFeature.viewCount) }} 阅读</span>
            </div>
          </RouterLink>
        </template>
        <p v-else class="side-empty">暂无旅行记录</p>

        <div class="side-block">
          <h3>路线进度</h3>
          <ul v-if="seriesItems.length" class="series-list">
            <li v-for="item in seriesItems" :key="item.id">
              <RouterLink :to="item.href">
                <span class="series-name">{{ item.name }}</span>
                <span class="series-count">{{ item.articleCount }} 篇</span>
                <span class="series-bar" aria-hidden="true"><i :style="{ width: item.progress + '%' }" /></span>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="side-empty">暂无系列记录</p>
          <p class="side-footnote">未完结系列按当前发布量显示相对进度。</p>
        </div>

        <div class="side-block">
          <h3>图例</h3>
          <div class="legend">
            <span><i class="legend-dot legend-dot--green" />工程节点</span>
            <span><i class="legend-dot legend-dot--yellow" />精选节点</span>
            <span><i class="legend-dot legend-dot--coral" />旅行节点</span>
          </div>
        </div>
      </aside>
    </main>

    <footer class="atlas-footer atlas-wrap">
      <span>© 2026 orccode.com · atlas field notes</span>
      <nav aria-label="Footer">
        <RouterLink to="/articles">归档</RouterLink>
        <RouterLink to="/travel">旅行</RouterLink>
        <RouterLink to="/series">系列</RouterLink>
      </nav>
    </footer>
  </div>
</template>

<style scoped>
.atlas-home {
  --atlas-navy: #102a43;
  --atlas-navy-2: #1d4762;
  --atlas-paper: #f5f7f2;
  --atlas-paper-2: #e9efe7;
  --atlas-ink: #17252c;
  --atlas-muted: #60717a;
  --atlas-line: #c8d4cf;
  --atlas-yellow: #f2c94c;
  --atlas-green: #2f855a;
  --atlas-coral: #cc674e;
  min-height: 100vh;
  overflow: hidden;
  background: var(--atlas-paper);
  color: var(--atlas-ink);
  font-family: var(--font-sans), sans-serif;
}

.atlas-wrap {
  width: min(92rem, calc(100% - 3rem));
  margin-inline: auto;
}

.atlas-hero {
  padding: 5.5rem 0 3.5rem;
  color: #fff;
  background: var(--atlas-navy);
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(20rem, 0.85fr);
  gap: 5rem;
  align-items: end;
}

.hero-eyebrow {
  margin: 0 0 1rem;
  color: var(--atlas-yellow);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-copy h1 {
  max-width: 8ch;
  margin: 0 0 1.5rem;
  font-size: 5.4rem;
  font-weight: 800;
  line-height: 0.95;
  letter-spacing: 0;
}

.hero-description {
  max-width: 38rem;
  margin: 0;
  color: #cbd9e0;
  font-size: 1rem;
  line-height: 1.8;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem 1.4rem;
  margin-top: 2rem;
  color: #a9bec9;
  font-size: 0.75rem;
}

.hero-meta strong {
  color: #fff;
  font-size: 1rem;
}

.atlas-map {
  position: relative;
  height: 20rem;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background-color: #163c58;
  background-image:
    repeating-linear-gradient(12deg, transparent 0, transparent 2.4rem, rgba(220, 238, 224, 0.14) 2.45rem, transparent 2.5rem),
    repeating-linear-gradient(102deg, transparent 0, transparent 3.2rem, rgba(220, 238, 224, 0.12) 3.25rem, transparent 3.3rem);
}

.map-contour {
  position: absolute;
  inset: 10% 8%;
  border: 1px dashed rgba(242, 201, 76, 0.6);
  border-radius: 48% 38% 52% 35%;
  transform: rotate(-8deg);
}

.map-contour--two {
  inset: 22% 18% 18% 28%;
  border-color: rgba(220, 238, 224, 0.28);
  transform: rotate(16deg);
}

.map-route {
  position: absolute;
  left: 12%;
  top: 52%;
  width: 72%;
  height: 2px;
  background: var(--atlas-yellow);
  transform: rotate(-16deg);
  box-shadow: 3.4rem 1.2rem 0 -1px var(--atlas-yellow), 7rem -1.4rem 0 -1px var(--atlas-yellow);
}

.map-pin {
  position: absolute;
  z-index: 1;
  width: 0.7rem;
  height: 0.7rem;
  border: 2px solid var(--atlas-navy);
  border-radius: 50%;
  background: var(--atlas-yellow);
  box-shadow: 0 0 0 4px rgba(242, 201, 76, 0.22);
}

.map-pin--a { left: 20%; top: 61%; }
.map-pin--b { left: 55%; top: 43%; }
.map-pin--c { right: 17%; top: 27%; }

.map-label {
  position: absolute;
  z-index: 1;
  color: #e8f0e7;
  font-size: 0.65rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.map-label--a { left: 12%; bottom: 17%; }
.map-label--b { left: 51%; top: 33%; }
.map-label--c { right: 10%; top: 18%; }

.map-caption {
  position: absolute;
  right: 0.75rem;
  bottom: 0.65rem;
  color: #b8cdd5;
  font-size: 0.62rem;
  letter-spacing: 0.09em;
}

.atlas-body {
  display: grid;
  grid-template-columns: 13rem minmax(0, 1fr) 15rem;
  gap: 4rem;
  padding-top: 3.5rem;
  padding-bottom: 5rem;
}

.filter-rail,
.side-rail {
  color: var(--atlas-muted);
  font-size: 0.8rem;
}

.rail-sticky {
  position: sticky;
  top: 5.75rem;
}

.filter-rail h2,
.side-rail > h2,
.side-block h3 {
  margin: 0 0 1rem;
  color: var(--atlas-ink);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.filters {
  display: grid;
  gap: 0.35rem;
}

.filter-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 0;
  border-left: 2px solid transparent;
  padding: 0.45rem 0.6rem;
  color: var(--atlas-muted);
  background: transparent;
  font-size: 0.78rem;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.filter-button:hover,
.filter-button--active {
  border-left-color: var(--atlas-green);
  color: var(--atlas-ink);
  background: var(--atlas-paper-2);
  font-weight: 700;
}

.filter-button:focus-visible {
  outline: 2px solid var(--atlas-yellow);
  outline-offset: 2px;
}

.filter-count {
  color: var(--atlas-muted);
  font-size: 0.65rem;
  font-variant-numeric: tabular-nums;
}

.rail-note {
  margin-top: 2rem;
  border-top: 1px solid var(--atlas-line);
  padding-top: 1rem;
}

.rail-note strong {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--atlas-green);
  font-size: 1.35rem;
  line-height: 1.1;
}

.rail-note p {
  margin: 0;
  font-size: 0.78rem;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
  border-bottom: 2px solid var(--atlas-navy);
  padding-bottom: 0.65rem;
}

.section-head h2 {
  margin: 0;
  font-size: 1.25rem;
  letter-spacing: 0;
}

.section-head span {
  color: var(--atlas-muted);
  font-size: 0.68rem;
  white-space: nowrap;
}

.route-list {
  position: relative;
  margin-left: 1rem;
  padding-left: 2.4rem;
}

.route-list::before {
  position: absolute;
  left: 0.35rem;
  top: 0.4rem;
  bottom: 1rem;
  width: 2px;
  background: var(--atlas-green);
  content: "";
}

.route-stop {
  position: relative;
  margin: 0 0 1.45rem;
  border-bottom: 1px solid var(--atlas-line);
  padding: 0 0 1.45rem;
}

.route-stop::before {
  position: absolute;
  left: -2.4rem;
  top: 0.35rem;
  width: 0.8rem;
  height: 0.8rem;
  border: 3px solid var(--atlas-green);
  border-radius: 50%;
  background: var(--atlas-paper);
  content: "";
}

.route-stop--featured::before {
  border-color: var(--atlas-yellow);
  background: var(--atlas-yellow);
  box-shadow: 0 0 0 4px var(--atlas-paper);
}

.stop-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.55rem 0.7rem;
  margin-bottom: 0.4rem;
  color: var(--atlas-muted);
  font-size: 0.7rem;
}

.stop-channel {
  color: var(--atlas-green);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.route-stop h3 {
  margin: 0 0 0.5rem;
  color: var(--atlas-ink);
  font-size: 1.25rem;
  font-weight: 700;
  line-height: 1.35;
}

.route-stop:hover h3 {
  color: var(--atlas-green);
}

.route-stop p {
  max-width: 52rem;
  margin: 0;
  color: var(--atlas-muted);
  font-size: 0.84rem;
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.stop-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem 1rem;
  margin-top: 0.75rem;
  color: var(--atlas-green);
  font-size: 0.74rem;
  font-weight: 700;
}

.stop-actions a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.timeline-state {
  border: 1px dashed var(--atlas-line);
  padding: 1.25rem;
  color: var(--atlas-muted);
  font-size: 0.82rem;
  text-align: center;
}

.travel-feature {
  display: block;
  color: inherit;
}

.travel-visual {
  position: relative;
  height: 13rem;
  overflow: hidden;
  margin-bottom: 1rem;
  border: 1px solid var(--atlas-line);
  background: var(--atlas-paper-2);
}

.travel-visual::before {
  position: absolute;
  left: -10%;
  right: -10%;
  bottom: -5%;
  z-index: 1;
  height: 74%;
  background: var(--atlas-green);
  clip-path: polygon(0 100%, 22% 32%, 37% 61%, 53% 18%, 71% 50%, 100% 0, 100% 100%);
  content: "";
}

.travel-visual::after {
  position: absolute;
  inset: 0.8rem;
  z-index: 2;
  border: 1px solid rgba(16, 42, 67, 0.45);
  content: "";
}

.travel-visual img {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.8;
}

.travel-visual-label {
  position: absolute;
  left: 1rem;
  top: 1rem;
  z-index: 3;
  color: var(--atlas-navy);
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.travel-copy strong {
  display: block;
  margin-bottom: 0.35rem;
  color: var(--atlas-ink);
  font-size: 0.96rem;
}

.travel-copy p {
  margin: 0;
  color: var(--atlas-muted);
  font-size: 0.8rem;
  line-height: 1.65;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.travel-meta {
  display: block;
  margin-top: 0.55rem;
  color: var(--atlas-muted);
  font-size: 0.68rem;
}

.side-empty {
  margin: 0;
  border: 1px dashed var(--atlas-line);
  padding: 0.8rem;
  color: var(--atlas-muted);
  font-size: 0.72rem;
  line-height: 1.5;
}

.side-block {
  margin-top: 1.8rem;
  border-top: 1px solid var(--atlas-line);
  padding-top: 1rem;
}

.side-block h3 {
  margin-bottom: 0.7rem;
}

.series-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.series-list li {
  border-bottom: 1px solid var(--atlas-line);
}

.series-list a {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.25rem 0.5rem;
  padding: 0.55rem 0;
}

.series-name {
  overflow: hidden;
  color: var(--atlas-ink);
  font-size: 0.76rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-count {
  color: var(--atlas-muted);
  font-size: 0.68rem;
  white-space: nowrap;
}

.series-bar {
  grid-column: 1 / -1;
  height: 3px;
  background: var(--atlas-paper-2);
}

.series-bar i {
  display: block;
  height: 100%;
  background: var(--atlas-green);
  transition: width 0.3s ease;
}

.side-footnote {
  margin: 0.65rem 0 0;
  color: var(--atlas-muted);
  font-size: 0.66rem;
  line-height: 1.5;
}

.legend {
  display: grid;
  gap: 0.4rem;
}

.legend span {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--atlas-muted);
  font-size: 0.74rem;
}

.legend-dot {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 50%;
}

.legend-dot--green { background: var(--atlas-green); }
.legend-dot--yellow { background: var(--atlas-yellow); }
.legend-dot--coral { background: var(--atlas-coral); }

.atlas-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 1rem;
  border-top: 1px solid var(--atlas-line);
  padding-top: 1.5rem;
  padding-bottom: 2.8rem;
  color: var(--atlas-muted);
  font-size: 0.72rem;
}

.atlas-footer nav {
  display: flex;
  gap: 1.1rem;
}

.atlas-footer a:hover {
  color: var(--atlas-green);
}

:global(html[data-theme='dark'] .atlas-home),
:global(html[data-theme='ocean'] .atlas-home) {
  --atlas-paper: #12232b;
  --atlas-paper-2: #1b3439;
  --atlas-ink: #e4eee9;
  --atlas-muted: #a9bec1;
  --atlas-line: #385158;
}

@media (max-width: 1150px) {
  .atlas-body {
    grid-template-columns: 11rem minmax(0, 1fr);
    gap: 3rem;
  }

  .side-rail {
    display: none;
  }
}

@media (max-width: 900px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 2.5rem;
  }

  .hero-copy h1 {
    font-size: 4.2rem;
  }

  .atlas-map {
    max-width: 42rem;
  }
}

@media (max-width: 720px) {
  .atlas-wrap {
    width: min(100% - 2rem, 92rem);
  }

  .atlas-hero {
    padding: 3.5rem 0 2.5rem;
  }

  .hero-copy h1 {
    font-size: 3.35rem;
  }

  .atlas-map {
    height: 15rem;
  }

  .atlas-body {
    display: block;
    padding-top: 2.5rem;
  }

  .filter-rail {
    margin-bottom: 2.5rem;
  }

  .rail-sticky {
    position: static;
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
  }

  .filter-button {
    width: auto;
    border: 1px solid var(--atlas-line);
    padding: 0.35rem 0.55rem;
  }

  .filter-button--active {
    border-color: var(--atlas-green);
  }

  .filter-count {
    margin-left: 0.25rem;
  }

  .rail-note {
    display: none;
  }

  .route-list {
    margin-left: 0.5rem;
    padding-left: 2rem;
  }

  .route-list::before {
    left: 0.35rem;
  }

  .route-stop::before {
    left: -2rem;
  }

  .route-stop h3 {
    font-size: 1.1rem;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.25rem;
  }

  .section-head span {
    white-space: normal;
  }
}

@media (prefers-reduced-motion: reduce) {
  .filter-button,
  .series-bar i {
    transition: none;
  }
}
</style>
