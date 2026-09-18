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
const brokenCoverIds = ref<Set<string>>(new Set())
const featureImageBroken = ref(false)
const PAGE_SIZE = 12

const fetcher = computed(() => (searchKeyword.value ? searchTrips : fetchTrips))
const queryParams = (cursor: string | null = null) => ({
  keyword: searchKeyword.value || undefined,
  destinationId: activeDestinationId.value ?? undefined,
  cursor: cursor ?? undefined,
  limit: PAGE_SIZE,
})

const totalDays = computed(() => trips.value.reduce((sum, trip) => sum + (Number(trip.daysCount) || 0), 0))
const latestTrip = computed(() => trips.value[0] ?? null)
const resultLabel = computed(() => searchKeyword.value ? '搜索结果' : activeDestinationId.value === null ? '全部旅程' : '目的地旅程')

const loadTrips = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await fetcher.value(queryParams())
    trips.value = data?.items ?? []
    nextCursor.value = data?.nextCursor ?? null
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
    nextCursor.value = data?.nextCursor ?? null
  } catch {
    error.value = '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

const submitSearch = () => {
  const keyword = searchInput.value.trim()
  if (searchKeyword.value === keyword) return
  searchKeyword.value = keyword
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
    destinations.value = (await fetchTravelDestinations(30)) ?? []
  } catch {
    destinations.value = []
  }
}

const formatDateRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) return '日期待补充'
  const fmt = (value?: string | null) => {
    if (!value) return ''
    const [year, month, day] = value.split('-')
    return year && month && day ? `${year}.${month}.${day}` : value
  }
  if (start && end) return `${fmt(start)} – ${fmt(end)}`
  return fmt(start || end)
}

const formatCost = (value?: number | null, currency?: string | null) => {
  if (value == null) return ''
  const symbol = currency === 'USD' ? '$' : currency === 'EUR' ? '€' : '¥'
  return `${symbol}${Math.round(Number(value))}`
}

const formatReads = (value: number) => {
  const count = Number(value) || 0
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w 阅读`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k 阅读`
  return `${count} 阅读`
}

const initialLetter = (title: string) => Array.from(title)[0] ?? '旅'

const coverAvailable = (trip: TravelTripListItem) => Boolean(trip.coverUrl) && !brokenCoverIds.value.has(String(trip.id))

const markCoverBroken = (id: number | string) => {
  brokenCoverIds.value = new Set([...brokenCoverIds.value, String(id)])
}

onMounted(() => {
  loadDestinations()
  loadTrips()
})
</script>

<template>
  <div class="atlas-travel">
    <header class="travel-hero">
      <div class="atlas-wrap travel-hero__inner">
        <div>
          <p class="hero-eyebrow">travel field notes / route log</p>
          <h1>把远方<br>走成记录。</h1>
          <p class="hero-description">路线、风景、花费和现场感。每一次出发都留下一个可以重新抵达的节点。</p>
        </div>
        <div class="travel-compass" role="img" aria-label="旅行路线标记">
          <span class="compass-ring compass-ring--outer" aria-hidden="true" />
          <span class="compass-ring compass-ring--inner" aria-hidden="true" />
          <span class="compass-line" aria-hidden="true" />
          <span class="compass-pin" aria-hidden="true" />
          <span class="compass-label compass-label--top">FIELD ROUTE</span>
          <span class="compass-label compass-label--bottom">N / 25°41' · E / 100°10'</span>
          <strong>{{ latestTrip?.title || 'TRAVEL LOG' }}</strong>
        </div>
        <div class="travel-index" aria-label="旅行统计">
          <span><strong>{{ String(trips.length).padStart(2, '0') }}</strong> 当前旅程</span>
          <span><strong>{{ String(destinations.length).padStart(2, '0') }}</strong> 目的地</span>
          <span><strong>{{ totalDays }}</strong> 行进天数</span>
        </div>
      </div>
    </header>

    <main class="atlas-wrap travel-body">
      <aside class="filter-rail">
        <div class="rail-sticky">
          <div class="rail-heading">
            <h2>目的地筛选</h2>
            <Icon icon="lucide:map" width="15" height="15" aria-hidden="true" />
          </div>
          <div class="filters" role="group" aria-label="目的地筛选">
            <button type="button" class="filter-button" :class="{ 'filter-button--active': activeDestinationId === null }" :aria-pressed="activeDestinationId === null" @click="selectDestination(null)">
              <span>全部旅程</span><span class="filter-count">{{ trips.length }}</span>
            </button>
            <button v-for="destination in destinations" :key="destination.id" type="button" class="filter-button" :class="{ 'filter-button--active': activeDestinationId === destination.id }" :aria-pressed="activeDestinationId === destination.id" @click="selectDestination(destination.id)">
              <span>{{ destination.name }}</span>
            </button>
          </div>
          <div class="rail-note">
            <strong>{{ resultLabel }}</strong>
            <p>从一条目的地路线开始<br>重新打开走过的路</p>
          </div>
        </div>
      </aside>

      <section class="travel-column" aria-labelledby="travel-title">
        <div class="travel-toolbar">
          <div class="section-head">
            <div>
              <p class="section-kicker">route index</p>
              <h2 id="travel-title">{{ resultLabel }}</h2>
            </div>
            <span>{{ searchKeyword ? `keyword / ${searchKeyword}` : 'newest first' }}</span>
          </div>
          <form class="search-form" @submit.prevent="submitSearch">
            <label class="search-input-wrap">
              <Icon icon="lucide:search" width="16" height="16" aria-hidden="true" />
              <span class="sr-only">搜索游记</span>
              <input v-model="searchInput" class="search-input" type="search" placeholder="搜索游记标题或摘要" autocomplete="off">
            </label>
            <button type="submit" class="search-button"><Icon icon="lucide:arrow-right" width="16" height="16" aria-hidden="true" /><span>检索</span></button>
            <button v-if="searchKeyword" type="button" class="search-clear" @click="clearSearch"><Icon icon="lucide:x" width="15" height="15" aria-hidden="true" /><span>清空</span></button>
          </form>
        </div>

        <div v-if="loading" class="timeline-state" aria-live="polite">正在读取旅行路线...</div>
        <div v-else-if="error" class="timeline-state timeline-state--error">{{ error }}</div>
        <div v-else-if="trips.length === 0" class="timeline-state">{{ searchKeyword || activeDestinationId !== null ? '没有找到相关游记。' : '还没有旅行节点。' }}</div>

        <ol v-else class="trip-list">
          <li v-for="(item, index) in trips" :key="item.id" class="trip-entry" :class="{ 'trip-entry--first': index === 0 }">
            <div class="entry-meta">
              <span class="entry-type">旅行</span>
              <span>{{ formatDateRange(item.startDate, item.endDate) }}</span>
              <span v-if="item.daysCount">{{ item.daysCount }} 天</span>
            </div>
            <div class="entry-main">
              <div class="entry-copy">
                <h3><RouterLink :to="{ path: '/travel/detail', query: { slug: item.slug } }">{{ item.title }}</RouterLink></h3>
                <p>{{ item.summary || '这段旅程暂时没有摘要，打开节点查看完整记录。' }}</p>
                <div class="entry-actions">
                  <RouterLink :to="{ path: '/travel/detail', query: { slug: item.slug } }">打开节点 <span aria-hidden="true">↗</span></RouterLink>
                  <span>{{ formatReads(item.viewCount) }}</span>
                  <span v-if="item.persons">{{ item.persons }} 人同行</span>
                  <span v-if="item.costTotal != null">{{ formatCost(item.costTotal, item.costCurrency) }}</span>
                </div>
              </div>
              <div class="entry-cover" :class="{ 'entry-cover--image': coverAvailable(item) }">
                <img v-if="coverAvailable(item)" :src="item.coverUrl!" :alt="item.title" loading="lazy" @error="markCoverBroken(item.id)">
                <span v-else>{{ initialLetter(item.title) }}</span>
              </div>
            </div>
          </li>

          <li v-if="nextCursor" class="load-more-row">
            <button type="button" class="load-more" :disabled="loadingMore" @click="loadMore">
              <Icon :icon="loadingMore ? 'lucide:loader-2' : 'lucide:arrow-down'" width="16" height="16" :class="{ spin: loadingMore }" aria-hidden="true" />
              <span>{{ loadingMore ? '正在读取下一段' : '加载更多旅程' }}</span>
            </button>
          </li>
        </ol>
      </section>

      <aside class="side-rail">
        <section class="side-block side-feature">
          <p class="side-kicker">field note / latest</p>
          <div class="feature-visual">
            <img v-if="latestTrip?.coverUrl && !featureImageBroken" :src="latestTrip.coverUrl" :alt="latestTrip.title" loading="lazy" @error="featureImageBroken = true">
            <span v-else class="feature-visual__shape" aria-hidden="true" />
            <span class="feature-label">FIELD / TRAVEL</span>
          </div>
          <h2>{{ latestTrip?.title || '等待下一段旅程' }}</h2>
          <p>{{ latestTrip?.summary || '后端返回新的旅行记录后，这里会显示最近一次出发。' }}</p>
          <RouterLink v-if="latestTrip" class="feature-link" :to="{ path: '/travel/detail', query: { slug: latestTrip.slug } }">查看最近节点 ↗</RouterLink>
        </section>

        <section class="side-block">
          <div class="side-title-row"><h2>路线说明</h2><Icon icon="lucide:compass" width="15" height="15" aria-hidden="true" /></div>
          <p class="side-copy">目的地、日期和花费都来自旅行记录本身，页面只负责把它们重新排成一条路线。</p>
        </section>

        <section class="side-block legend-block">
          <p class="side-kicker">map legend</p>
          <div class="legend"><span><i class="legend-dot legend-dot--green" />已完成路线</span><span><i class="legend-dot legend-dot--yellow" />最近节点</span><span><i class="legend-dot legend-dot--coral" />现场记录</span></div>
        </section>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.atlas-travel { --atlas-navy:#102a43; --atlas-paper:#f5f7f2; --atlas-paper-2:#e9efe7; --atlas-ink:#17252c; --atlas-muted:#60717a; --atlas-line:#c8d4cf; --atlas-yellow:#f2c94c; --atlas-green:#2f855a; --atlas-coral:#cc674e; min-height:100vh; overflow:hidden; background:var(--atlas-paper); color:var(--atlas-ink); font-family:var(--font-sans),sans-serif; }
.atlas-wrap { width:min(92rem,calc(100% - 3rem)); margin-inline:auto; }
.travel-hero { padding:4rem 0 3rem; color:#fff; background:var(--atlas-navy); }
.travel-hero__inner { display:grid; grid-template-columns:minmax(0,1fr) minmax(14rem,.72fr) auto; align-items:end; gap:3rem; }
.hero-eyebrow,.section-kicker,.side-kicker { margin:0 0 .85rem; color:var(--atlas-yellow); font-size:.68rem; font-weight:800; letter-spacing:.14em; text-transform:uppercase; }
.travel-hero h1 { margin:0; font-size:clamp(3.1rem,7vw,5.4rem); font-weight:800; line-height:.95; }
.hero-description { max-width:34rem; margin:1.35rem 0 0; color:#cbd9e0; font-size:.98rem; line-height:1.8; }
.travel-index { display:grid; gap:.7rem; min-width:10rem; color:#a9bec9; font-size:.72rem; text-align:right; }
.travel-index span { border-top:1px solid rgba(255,255,255,.22); padding-top:.55rem; }
.travel-index strong { margin-right:.35rem; color:#fff; font-size:1.05rem; font-variant-numeric:tabular-nums; }
.travel-compass { position:relative; min-height:12rem; overflow:hidden; border:1px solid rgba(255,255,255,.3); background:#163c58; }
.compass-ring { position:absolute; border:1px dashed rgba(242,201,76,.55); border-radius:50%; transform:rotate(-18deg); }
.compass-ring--outer { inset:10% 8%; }
.compass-ring--inner { inset:28% 24% 18% 30%; border-color:rgba(220,238,224,.3); }
.compass-line { position:absolute; left:16%; top:54%; width:70%; height:2px; background:var(--atlas-yellow); transform:rotate(-17deg); box-shadow:3.2rem 1rem 0 -1px var(--atlas-yellow); }
.compass-pin { position:absolute; left:52%; top:42%; width:.72rem; height:.72rem; border:2px solid var(--atlas-navy); border-radius:50%; background:var(--atlas-yellow); box-shadow:0 0 0 4px rgba(242,201,76,.22); }
.compass-label { position:absolute; color:#dceee0; font-size:.61rem; letter-spacing:.1em; }
.compass-label--top { left:.8rem; top:.75rem; }
.compass-label--bottom { right:.7rem; bottom:.6rem; color:#b8cdd5; }
.travel-compass strong { position:absolute; left:1rem; bottom:1.7rem; max-width:75%; overflow:hidden; color:#fff; font-size:.73rem; text-overflow:ellipsis; white-space:nowrap; }
.travel-body { display:grid; grid-template-columns:13rem minmax(0,1fr) 15rem; gap:4rem; padding-top:3.5rem; padding-bottom:5rem; }
.filter-rail,.side-rail { color:var(--atlas-muted); font-size:.8rem; }
.rail-sticky { position:sticky; top:5.75rem; }
.rail-heading,.side-title-row { display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
.rail-heading h2,.side-title-row h2,.side-feature h2 { margin:0; color:var(--atlas-ink); font-size:.72rem; font-weight:800; letter-spacing:.13em; text-transform:uppercase; }
.rail-heading>svg,.side-title-row>svg { color:var(--atlas-green); }
.filters { display:grid; gap:.35rem; margin-top:.85rem; }
.filter-button { display:flex; align-items:center; justify-content:space-between; width:100%; border:0; border-left:2px solid transparent; padding:.45rem .6rem; color:var(--atlas-muted); background:transparent; font-size:.78rem; text-align:left; cursor:pointer; transition:background .18s ease,color .18s ease,border-color .18s ease; }
.filter-button:hover,.filter-button--active { border-left-color:var(--atlas-green); color:var(--atlas-ink); background:var(--atlas-paper-2); font-weight:700; }
.filter-button:focus-visible,.search-button:focus-visible,.search-clear:focus-visible,.load-more:focus-visible { outline:2px solid var(--atlas-yellow); outline-offset:2px; }
.filter-count { color:var(--atlas-muted); font-size:.65rem; font-variant-numeric:tabular-nums; }
.rail-note { margin-top:2rem; border-top:1px solid var(--atlas-line); padding-top:1rem; }
.rail-note strong { display:block; margin-bottom:.25rem; color:var(--atlas-green); font-size:1.05rem; }
.rail-note p { margin:0; font-size:.72rem; line-height:1.55; }
.travel-column { min-width:0; }
.travel-toolbar { margin-bottom:1.2rem; }
.section-head { display:flex; align-items:end; justify-content:space-between; gap:1rem; border-bottom:2px solid var(--atlas-navy); padding-bottom:.65rem; }
.section-kicker { margin-bottom:.35rem; color:var(--atlas-green); font-size:.62rem; }
.section-head h2 { margin:0; color:var(--atlas-ink); font-size:1.35rem; }
.section-head>span { max-width:15rem; overflow:hidden; color:var(--atlas-muted); font-size:.68rem; text-overflow:ellipsis; white-space:nowrap; }
.search-form { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:.55rem; margin-top:.95rem; }
.search-input-wrap { display:flex; align-items:center; gap:.5rem; min-width:0; border-bottom:1px solid var(--atlas-line); color:var(--atlas-muted); }
.search-input-wrap:focus-within { border-bottom-color:var(--atlas-green); }
.search-input { width:100%; min-width:0; border:0; outline:0; padding:.55rem 0; color:var(--atlas-ink); background:transparent; font-size:.82rem; }
.search-input::placeholder { color:var(--atlas-muted); }
.search-button,.search-clear,.load-more { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; border:1px solid var(--atlas-line); padding:.5rem .75rem; color:var(--atlas-ink); background:transparent; font-size:.74rem; font-weight:700; cursor:pointer; transition:background .18s ease,border-color .18s ease,color .18s ease; }
.search-button { border-color:var(--atlas-navy); color:#fff; background:var(--atlas-navy); }
.search-button:hover,.search-clear:hover,.load-more:hover:not(:disabled) { border-color:var(--atlas-green); color:var(--atlas-green); background:var(--atlas-paper-2); }
.timeline-state { border:1px dashed var(--atlas-line); padding:1.4rem; color:var(--atlas-muted); font-size:.82rem; text-align:center; }
.timeline-state--error { border-color:color-mix(in srgb,var(--atlas-coral) 55%,var(--atlas-line)); color:var(--atlas-coral); }
.trip-list { position:relative; list-style:none; margin:0 0 0 1rem; padding:0 0 0 2.4rem; }
.trip-list::before { position:absolute; left:.35rem; top:.45rem; bottom:1rem; width:2px; background:var(--atlas-green); content:''; }
.trip-entry { position:relative; margin-bottom:1.45rem; border-bottom:1px solid var(--atlas-line); padding-bottom:1.45rem; }
.trip-entry::before { position:absolute; left:-2.4rem; top:.35rem; width:.8rem; height:.8rem; border:3px solid var(--atlas-coral); border-radius:50%; background:var(--atlas-paper); content:''; }
.trip-entry--first::before { border-color:var(--atlas-yellow); background:var(--atlas-yellow); box-shadow:0 0 0 4px var(--atlas-paper); }
.entry-meta { display:flex; flex-wrap:wrap; align-items:center; gap:.55rem .7rem; margin-bottom:.4rem; color:var(--atlas-muted); font-size:.69rem; }
.entry-type { color:var(--atlas-coral); font-size:.66rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
.entry-main { display:flex; align-items:flex-start; gap:1.25rem; }
.entry-copy { min-width:0; flex:1; }
.trip-entry h3 { margin:0 0 .5rem; color:var(--atlas-ink); font-size:1.25rem; font-weight:700; line-height:1.35; }
.trip-entry h3 a:hover { color:var(--atlas-green); }
.trip-entry p { max-width:52rem; margin:0; color:var(--atlas-muted); font-size:.84rem; line-height:1.7; display:-webkit-box; overflow:hidden; -webkit-box-orient:vertical; -webkit-line-clamp:3; }
.entry-actions { display:flex; flex-wrap:wrap; gap:.7rem 1rem; margin-top:.75rem; color:var(--atlas-green); font-size:.72rem; font-weight:700; }
.entry-actions a:hover { text-decoration:underline; text-underline-offset:3px; }
.entry-cover { position:relative; display:grid; width:7rem; height:5.25rem; flex:0 0 7rem; place-items:center; overflow:hidden; border:1px solid var(--atlas-line); color:var(--atlas-green); background:var(--atlas-paper-2); font-size:2rem; font-weight:900; }
.entry-cover::after { position:absolute; inset:.45rem; border:1px solid rgba(16,42,67,.35); content:''; }
.entry-cover--image { background:var(--atlas-paper-2); }
.entry-cover img { width:100%; height:100%; object-fit:cover; filter:saturate(.8); }
.load-more-row { margin-left:-2.4rem; }
.load-more { width:calc(100% + 2.4rem); }
.load-more:disabled { cursor:wait; opacity:.65; }
.spin { animation:spin .9s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
.side-block { border-top:1px solid var(--atlas-line); padding-top:1rem; }
.side-block+.side-block { margin-top:2rem; }
.side-feature { border-top:0; padding-top:0; }
.side-kicker { color:var(--atlas-coral); }
.feature-visual { position:relative; height:11rem; overflow:hidden; margin:1rem 0; border:1px solid var(--atlas-line); background:var(--atlas-paper-2); }
.feature-visual::after { position:absolute; inset:.75rem; z-index:2; border:1px solid rgba(16,42,67,.4); content:''; }
.feature-visual img { width:100%; height:100%; object-fit:cover; filter:saturate(.8); }
.feature-visual__shape { position:absolute; left:-10%; right:-10%; bottom:-5%; height:75%; background:var(--atlas-green); clip-path:polygon(0 100%,22% 32%,37% 61%,53% 18%,71% 50%,100% 0,100% 100%); }
.feature-label { position:absolute; left:.9rem; top:.8rem; z-index:3; color:var(--atlas-navy); font-size:.62rem; font-weight:800; letter-spacing:.1em; }
.side-feature h2 { font-size:1rem; letter-spacing:0; text-transform:none; }
.side-feature>p { margin:.55rem 0 0; color:var(--atlas-muted); font-size:.77rem; line-height:1.65; }
.feature-link { display:block; margin-top:.75rem; color:var(--atlas-green); font-size:.72rem; font-weight:700; }
.feature-link:hover { text-decoration:underline; text-underline-offset:3px; }
.side-copy { margin:.65rem 0 0; color:var(--atlas-muted); font-size:.77rem; line-height:1.65; }
.legend { display:grid; gap:.4rem; margin-top:.65rem; }
.legend span { display:flex; align-items:center; gap:.5rem; color:var(--atlas-muted); font-size:.74rem; }
.legend-dot { width:.6rem; height:.6rem; border-radius:50%; }
.legend-dot--green { background:var(--atlas-green); }
.legend-dot--yellow { background:var(--atlas-yellow); }
.legend-dot--coral { background:var(--atlas-coral); }
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
:global(html[data-theme='dark'] .atlas-travel),:global(html[data-theme='ocean'] .atlas-travel) { --atlas-paper:#12232b; --atlas-paper-2:#1b3439; --atlas-ink:#e4eee9; --atlas-muted:#a9bec1; --atlas-line:#385158; }
@media (max-width:1150px) { .travel-hero__inner { grid-template-columns:minmax(0,1fr) minmax(14rem,.75fr); } .travel-index { display:none; } .travel-body { grid-template-columns:11rem minmax(0,1fr); gap:3rem; } .side-rail { display:none; } }
@media (max-width:720px) { .atlas-wrap { width:min(100% - 2rem,92rem); } .travel-hero { padding:3.5rem 0 2.5rem; } .travel-hero__inner { display:block; } .travel-compass { margin-top:2rem; min-height:11rem; } .travel-body { display:block; padding-top:2.5rem; } .filter-rail { margin-bottom:2.5rem; } .rail-sticky { position:static; } .filters { display:flex; flex-wrap:wrap; gap:.35rem; } .filter-button { width:auto; border:1px solid var(--atlas-line); padding:.35rem .55rem; } .filter-button--active { border-color:var(--atlas-green); } .rail-note { display:none; } .search-form { grid-template-columns:minmax(0,1fr) auto; } .search-clear { grid-column:1 / -1; justify-self:start; } .trip-list { margin-left:.5rem; padding-left:2rem; } .trip-list::before { left:.35rem; } .trip-entry::before { left:-2rem; } .entry-main { display:block; } .entry-cover { width:100%; height:9rem; margin-top:.85rem; } .load-more-row { margin-left:-2rem; } .load-more { width:calc(100% + 2rem); } .section-head { align-items:flex-start; flex-direction:column; gap:.25rem; } .section-head>span { max-width:100%; white-space:normal; } }
@media (prefers-reduced-motion:reduce) { .filter-button,.search-button,.search-clear,.load-more,.spin { transition:none; animation:none; } }
</style>
