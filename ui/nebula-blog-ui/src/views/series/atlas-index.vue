<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchSeriesList, type SeriesListItem } from '../../api/series'

type StatusFilter = 'all' | 'ongoing' | 'finished'

const items = ref<SeriesListItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)

const searchInput = ref('')
const searchKeyword = ref('')
const statusFilter = ref<StatusFilter>('all')
const brokenCoverIds = ref<Set<string>>(new Set())

const totalArticles = computed(() =>
  items.value.reduce((sum, item) => sum + (item.articleCount ?? 0), 0),
)

const finishedCount = computed(() => items.value.filter((item) => item.isFinished).length)
const ongoingCount = computed(() => items.value.filter((item) => !item.isFinished).length)

const filteredItems = computed(() => {
  if (statusFilter.value === 'all') return items.value
  if (statusFilter.value === 'ongoing') return items.value.filter((item) => !item.isFinished)
  return items.value.filter((item) => item.isFinished)
})

const featuredSeries = computed(() => [...items.value]
  .filter((item) => (item.articleCount ?? 0) > 0)
  .sort((a, b) => (b.articleCount ?? 0) - (a.articleCount ?? 0))
  .slice(0, 3))

const formatUpdatedAt = (value?: string | null) => value?.slice(0, 7).replace('-', '/') ?? ''
const initialLetter = (name: string) => Array.from(name)[0] ?? ''
const hasCover = (item: SeriesListItem) => Boolean(item.coverUrl) && !brokenCoverIds.value.has(String(item.id))
const handleCoverError = (item: SeriesListItem) => {
  brokenCoverIds.value = new Set(brokenCoverIds.value).add(String(item.id))
}

const buildParams = (cursor?: string | null) => ({
  limit: 12,
  keyword: searchKeyword.value || undefined,
  isFinished: statusFilter.value === 'finished'
    ? true
    : statusFilter.value === 'ongoing'
      ? false
      : undefined,
  cursor: cursor ?? undefined,
})

const loadInitial = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchSeriesList(buildParams())
    items.value = result.items ?? []
    nextCursor.value = result.nextCursor
    brokenCoverIds.value = new Set()
  } catch (error) {
    errorMessage.value = (error as Error)?.message || '系列列表加载失败'
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
    nextCursor.value = result.nextCursor
  } catch (error) {
    errorMessage.value = (error as Error)?.message || '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

const submitSearch = () => {
  const keyword = searchInput.value.trim()
  if (keyword === searchKeyword.value) return
  searchKeyword.value = keyword
  loadInitial()
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
  loadInitial()
}

const setStatus = (value: StatusFilter) => {
  if (statusFilter.value === value) return
  statusFilter.value = value
  loadInitial()
}

onMounted(loadInitial)
</script>

<template>
  <div class="atlas-series">
    <header class="series-hero">
      <div class="atlas-wrap series-hero__inner">
        <div class="series-hero__copy">
          <p class="hero-eyebrow">library / sequences</p>
          <h1>系列</h1>
          <p class="series-hero__lead">
            把零散的文章串成一条清晰的学习路径，按章节继续前行。
          </p>
        </div>
        <div class="series-index" aria-label="系列统计">
          <span><strong>{{ items.length }}</strong> 个系列</span>
          <span><strong>{{ totalArticles }}</strong> 篇文章</span>
          <span><strong>{{ finishedCount }}</strong> 已完结</span>
        </div>
      </div>
    </header>

    <main class="atlas-wrap series-body">
      <aside class="series-rail">
        <div class="rail-sticky">
          <section class="rail-block">
            <p class="rail-kicker">status</p>
            <h2>浏览系列</h2>
            <div class="status-filters" role="tablist" aria-label="系列状态">
              <button
                type="button"
                role="tab"
                class="status-filter"
                :class="{ 'status-filter--active': statusFilter === 'all' }"
                :aria-selected="statusFilter === 'all'"
                @click="setStatus('all')"
              >
                <span>全部</span><span>{{ items.length }}</span>
              </button>
              <button
                type="button"
                role="tab"
                class="status-filter"
                :class="{ 'status-filter--active': statusFilter === 'ongoing' }"
                :aria-selected="statusFilter === 'ongoing'"
                @click="setStatus('ongoing')"
              >
                <span>连载中</span><span>{{ ongoingCount }}</span>
              </button>
              <button
                type="button"
                role="tab"
                class="status-filter"
                :class="{ 'status-filter--active': statusFilter === 'finished' }"
                :aria-selected="statusFilter === 'finished'"
                @click="setStatus('finished')"
              >
                <span>已完结</span><span>{{ finishedCount }}</span>
              </button>
            </div>
          </section>

          <section class="rail-note">
            <p class="rail-kicker">signal</p>
            <strong>{{ ongoingCount }}</strong>
            <p>正在持续更新的系列，适合从最新章节开始跟进。</p>
          </section>
        </div>
      </aside>

      <section class="series-content" aria-labelledby="series-list-title">
        <div class="series-toolbar">
          <div>
            <p class="section-kicker">collection / {{ statusFilter }}</p>
            <h2 id="series-list-title">学习路径</h2>
          </div>
          <form class="search-form" @submit.prevent="submitSearch">
            <label class="search-input-wrap">
              <Icon icon="lucide:search" aria-hidden="true" />
              <input
                v-model="searchInput"
                class="search-input"
                type="search"
                placeholder="搜索系列"
                autocomplete="off"
              />
            </label>
            <button class="search-button" type="submit">搜索</button>
            <button
              v-if="searchInput || searchKeyword"
              class="search-clear"
              type="button"
              aria-label="清空搜索"
              @click="clearSearch"
            >
              <Icon icon="lucide:x" />
              清空
            </button>
          </form>
        </div>

        <section
          v-if="!loading && featuredSeries.length > 1 && statusFilter === 'all' && !searchKeyword"
          class="featured-block"
          aria-labelledby="featured-title"
        >
          <div class="section-head">
            <div>
              <p class="section-kicker">editor's route</p>
              <h3 id="featured-title">推荐路径</h3>
            </div>
            <span>按文章数量排序</span>
          </div>
          <div class="featured-list">
            <RouterLink
              v-for="(item, index) in featuredSeries"
              :key="item.id"
              :to="`/series/${item.slug}`"
              class="featured-entry"
            >
              <span class="featured-entry__rank">0{{ index + 1 }}</span>
              <span class="featured-entry__name">{{ item.name }}</span>
              <span class="featured-entry__count">{{ item.articleCount }} 篇</span>
              <Icon icon="lucide:arrow-up-right" aria-hidden="true" />
            </RouterLink>
          </div>
        </section>

        <p v-if="loading" class="timeline-state">
          <span class="state-spinner" aria-hidden="true" />
          正在读取系列档案…
        </p>
        <p v-else-if="errorMessage" class="timeline-state timeline-state--error">{{ errorMessage }}</p>
        <p v-else-if="filteredItems.length === 0" class="timeline-state">
          {{ searchKeyword ? `没有找到与「${searchKeyword}」相关的系列` : '暂无系列' }}
        </p>

        <div v-else class="series-list">
          <RouterLink
            v-for="(item, index) in filteredItems"
            :key="item.id"
            :to="`/series/${item.slug}`"
            class="series-entry"
          >
            <span class="series-entry__marker" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="series-entry__cover" :class="{ 'series-entry__cover--image': hasCover(item) }" aria-hidden="true">
              <img v-if="hasCover(item)" :src="item.coverUrl || ''" alt="" @error="handleCoverError(item)" />
              <span v-else>{{ initialLetter(item.name) }}</span>
            </span>
            <span class="series-entry__copy">
              <span class="series-entry__meta">
                <span class="series-entry__status" :class="{ 'series-entry__status--done': item.isFinished }">
                  {{ item.isFinished ? '已完结' : '连载中' }}
                </span>
                <span>{{ item.articleCount }} 篇文章</span>
                <span v-if="item.updateTime">更新于 {{ formatUpdatedAt(item.updateTime) }}</span>
              </span>
              <strong class="series-entry__title">{{ item.name }}</strong>
              <span v-if="item.description" class="series-entry__desc">{{ item.description }}</span>
              <span v-if="item.tags?.length" class="series-entry__tags">
                <span v-for="tag in item.tags" :key="tag.id">{{ tag.name }}</span>
              </span>
            </span>
            <Icon icon="lucide:arrow-up-right" class="series-entry__arrow" aria-hidden="true" />
          </RouterLink>
        </div>

        <div v-if="nextCursor && !loading" class="load-more-row">
          <button class="load-more" type="button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </div>
        <p v-else-if="!loading && filteredItems.length" class="end-hint">已经到底了 ···</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.atlas-series {
  --atlas-navy: #102a43;
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

.atlas-wrap { width: min(92rem, calc(100% - 3rem)); margin-inline: auto; }
.series-hero { padding: 4.25rem 0 3rem; color: #fff; background: var(--atlas-navy); }
.series-hero__inner { display: flex; align-items: flex-end; justify-content: space-between; gap: 3rem; }
.hero-eyebrow, .section-kicker, .rail-kicker { margin: 0 0 .8rem; color: var(--atlas-yellow); font-size: .68rem; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
.series-hero h1 { margin: 0; font-size: clamp(3.2rem, 7vw, 5.5rem); font-weight: 800; line-height: .95; }
.series-hero__lead { max-width: 34rem; margin: 1.25rem 0 0; color: #cbd9e0; font-size: .92rem; line-height: 1.8; }
.series-index { display: grid; min-width: 12rem; gap: .7rem; color: #a9bec9; font-size: .72rem; text-align: right; }
.series-index span { border-top: 1px solid rgba(255,255,255,.22); padding-top: .55rem; }
.series-index strong { margin-right: .35rem; color: #fff; font-size: 1.15rem; font-variant-numeric: tabular-nums; }
.series-body { display: grid; grid-template-columns: 13rem minmax(0,1fr); gap: 4rem; padding-top: 3.5rem; padding-bottom: 5rem; }
.series-rail { color: var(--atlas-muted); font-size: .8rem; }
.rail-sticky { position: sticky; top: 5.75rem; }
.rail-block, .rail-note { border-top: 1px solid var(--atlas-line); padding-top: 1rem; }
.rail-block h2 { margin: 0 0 .7rem; color: var(--atlas-ink); font-size: .72rem; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }
.rail-block .rail-kicker { margin-bottom: .55rem; color: var(--atlas-green); }
.status-filters { display: grid; gap: .1rem; }
.status-filter { display: flex; align-items: center; justify-content: space-between; width: 100%; border: 0; border-left: 2px solid transparent; padding: .45rem .6rem; color: var(--atlas-muted); background: transparent; font-size: .78rem; text-align: left; cursor: pointer; transition: background .18s ease,color .18s ease,border-color .18s ease; }
.status-filter:hover, .status-filter--active { border-left-color: var(--atlas-green); color: var(--atlas-ink); background: var(--atlas-paper-2); font-weight: 700; }
.status-filter span:last-child { color: var(--atlas-muted); font-size: .65rem; font-variant-numeric: tabular-nums; }
.rail-note { margin-top: 2rem; }
.rail-note .rail-kicker { color: var(--atlas-coral); }
.rail-note strong { display: block; margin-bottom: .25rem; color: var(--atlas-green); font-size: 1.25rem; }
.rail-note p:last-child { margin: 0; line-height: 1.65; }
.series-content { min-width: 0; }
.series-toolbar { display: grid; grid-template-columns: minmax(0,1fr) minmax(15rem,22rem); align-items: end; gap: 2rem; margin-bottom: 1.6rem; border-bottom: 2px solid var(--atlas-navy); padding-bottom: .7rem; }
.series-toolbar .section-kicker, .section-head .section-kicker { margin-bottom: .35rem; color: var(--atlas-green); font-size: .62rem; }
.series-toolbar h2, .section-head h3 { margin: 0; color: var(--atlas-ink); font-size: 1.35rem; }
.search-form { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: .6rem; }
.search-input-wrap { display: flex; align-items: center; gap: .5rem; min-width: 0; border-bottom: 1px solid var(--atlas-line); color: var(--atlas-muted); }
.search-input-wrap:focus-within { border-bottom-color: var(--atlas-green); }
.search-input-wrap :deep(svg) { flex: 0 0 auto; }
.search-input { width: 100%; min-width: 0; border: 0; outline: 0; padding: .55rem 0; color: var(--atlas-ink); background: transparent; font-size: .82rem; }
.search-input::placeholder { color: var(--atlas-muted); }
.search-button, .search-clear, .load-more { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; border: 1px solid var(--atlas-line); padding: .5rem .75rem; color: var(--atlas-ink); background: transparent; font-size: .74rem; font-weight: 700; cursor: pointer; transition: background .18s ease,border-color .18s ease,color .18s ease; }
.search-button { border-color: var(--atlas-navy); color: #fff; background: var(--atlas-navy); }
.search-button:hover, .search-clear:hover, .load-more:hover:not(:disabled) { border-color: var(--atlas-green); color: var(--atlas-green); background: var(--atlas-paper-2); }
.search-clear :deep(svg) { width: .8rem; }
.featured-block { margin-bottom: 2rem; }
.section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; margin-bottom: .7rem; }
.section-head > span { color: var(--atlas-muted); font-size: .68rem; }
.featured-list { border-top: 1px solid var(--atlas-line); }
.featured-entry { display: grid; grid-template-columns: 2.5rem minmax(0,1fr) auto 1rem; align-items: center; gap: .7rem; border-bottom: 1px solid var(--atlas-line); padding: .75rem 0; color: inherit; text-decoration: none; }
.featured-entry:hover .featured-entry__name, .series-entry:hover .series-entry__title { color: var(--atlas-green); }
.featured-entry__rank { color: var(--atlas-coral); font-size: .72rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.featured-entry__name { overflow: hidden; color: var(--atlas-ink); font-size: .84rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.featured-entry__count { color: var(--atlas-muted); font-size: .7rem; font-variant-numeric: tabular-nums; }
.featured-entry > :last-child { color: var(--atlas-green); }
.timeline-state { border: 1px dashed var(--atlas-line); padding: 1.4rem; color: var(--atlas-muted); font-size: .82rem; text-align: center; }
.timeline-state--error { border-color: color-mix(in srgb,var(--atlas-coral) 55%,var(--atlas-line)); color: var(--atlas-coral); }
.state-spinner { display: inline-block; width: .85rem; height: .85rem; margin-right: .4rem; border: 2px solid var(--atlas-line); border-top-color: var(--atlas-green); border-radius: 50%; vertical-align: -.15rem; animation: atlas-spin .8s linear infinite; }
@keyframes atlas-spin { to { transform: rotate(360deg); } }
.series-list { position: relative; margin-left: 1rem; padding-left: 2.4rem; }
.series-list::before { position: absolute; left: .35rem; top: .45rem; bottom: 1rem; width: 2px; background: var(--atlas-green); content: ''; }
.series-entry { position: relative; display: grid; grid-template-columns: 3.75rem minmax(5rem,7rem) minmax(0,1fr) auto; align-items: start; gap: 1rem; margin-bottom: 1.45rem; border-bottom: 1px solid var(--atlas-line); padding-bottom: 1.45rem; color: inherit; text-decoration: none; }
.series-entry::before { position: absolute; left: -2.4rem; top: .35rem; width: .8rem; height: .8rem; border: 3px solid var(--atlas-green); border-radius: 50%; background: var(--atlas-paper); content: ''; }
.series-entry:first-child::before { border-color: var(--atlas-yellow); background: var(--atlas-yellow); box-shadow: 0 0 0 4px var(--atlas-paper); }
.series-entry__marker { padding-top: .35rem; color: var(--atlas-muted); font-size: .7rem; font-variant-numeric: tabular-nums; }
.series-entry__cover { display: grid; width: 7rem; height: 5rem; place-items: center; border: 1px solid var(--atlas-line); color: var(--atlas-navy); background: var(--atlas-yellow); font-size: 1.45rem; font-weight: 900; }
.series-entry__cover--image { background: var(--atlas-paper-2); }
.series-entry__cover img { width: 100%; height: 100%; object-fit: cover; filter: saturate(.8); }
.series-entry__copy { display: flex; min-width: 0; flex-direction: column; gap: .42rem; }
.series-entry__meta { display: flex; flex-wrap: wrap; align-items: center; gap: .55rem .7rem; color: var(--atlas-muted); font-size: .68rem; }
.series-entry__status { color: var(--atlas-green); font-size: .64rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.series-entry__status--done { color: var(--atlas-coral); }
.series-entry__title { color: var(--atlas-ink); font-size: 1.25rem; font-weight: 700; line-height: 1.35; }
.series-entry__desc { display: -webkit-box; max-width: 52rem; overflow: hidden; color: var(--atlas-muted); font-size: .84rem; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.series-entry__tags { display: flex; flex-wrap: wrap; gap: .45rem; color: var(--atlas-muted); font-size: .67rem; }
.series-entry__tags span { border-bottom: 1px solid var(--atlas-line); padding-bottom: .12rem; }
.series-entry__arrow { margin-top: .35rem; color: var(--atlas-green); transition: transform .18s ease; }
.series-entry:hover .series-entry__arrow { transform: translate(2px,-2px); }
.load-more-row { display: flex; justify-content: center; margin-top: .5rem; }
.load-more:disabled { cursor: not-allowed; opacity: .55; }
.end-hint { margin: 0; padding: .5rem 0 1rem; color: var(--atlas-muted); font-size: .72rem; letter-spacing: .04em; text-align: center; }
.status-filter:focus-visible, .search-button:focus-visible, .search-clear:focus-visible, .load-more:focus-visible, .series-entry:focus-visible, .featured-entry:focus-visible { outline: 2px solid var(--atlas-yellow); outline-offset: 3px; }
:global(html[data-theme='dark'] .atlas-series), :global(html[data-theme='ocean'] .atlas-series) { --atlas-paper:#12232b; --atlas-paper-2:#1b3439; --atlas-ink:#e4eee9; --atlas-muted:#a9bec1; --atlas-line:#385158; }

@media (max-width: 1050px) {
  .series-body { grid-template-columns: 11rem minmax(0,1fr); gap: 3rem; }
  .series-entry { grid-template-columns: 2.75rem minmax(5rem,7rem) minmax(0,1fr) auto; }
}

@media (max-width: 720px) {
  .atlas-wrap { width: min(100% - 2rem,92rem); }
  .series-hero { padding: 3.5rem 0 2.5rem; }
  .series-hero__inner { display: block; }
  .series-index { grid-template-columns: repeat(3,1fr); gap: .5rem; margin-top: 2rem; text-align: left; }
  .series-index strong { display: block; margin: 0 0 .2rem; }
  .series-body { display: block; padding-top: 2.5rem; }
  .series-rail { margin-bottom: 2.5rem; }
  .rail-sticky { position: static; }
  .status-filters { display: flex; flex-wrap: wrap; gap: .35rem; }
  .status-filter { width: auto; border: 1px solid var(--atlas-line); padding: .35rem .55rem; }
  .status-filter--active { border-color: var(--atlas-green); }
  .rail-note { display: none; }
  .series-toolbar { display: block; }
  .search-form { grid-template-columns: minmax(0,1fr) auto; margin-top: 1rem; }
  .search-clear { grid-column: 1 / -1; justify-self: start; }
  .section-head { align-items: flex-start; flex-direction: column; gap: .25rem; }
  .series-list { margin-left: .5rem; padding-left: 2rem; }
  .series-list::before { left: .35rem; }
  .series-entry { grid-template-columns: 2rem minmax(4.5rem,6rem) minmax(0,1fr); gap: .7rem; }
  .series-entry::before { left: -2rem; }
  .series-entry__marker { font-size: .64rem; }
  .series-entry__cover { width: 6rem; height: 4.35rem; }
  .series-entry__title { font-size: 1rem; }
  .series-entry__arrow { display: none; }
}

@media (max-width: 520px) {
  .series-entry { grid-template-columns: 2rem minmax(0,1fr); }
  .series-entry__cover { width: 100%; height: 6rem; }
  .series-entry__copy { grid-column: 2; }
  .series-entry__marker { grid-row: span 2; }
}

@media (prefers-reduced-motion: reduce) {
  .status-filter, .search-button, .search-clear, .load-more, .series-entry__arrow { transition: none; }
  .state-spinner { animation: none; }
  .series-entry:hover .series-entry__arrow { transform: none; }
}
</style>
