<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { fetchCategoryTree, type CategoryNode } from '../../api/category'
import { fetchArticles, fetchHotArticles, searchArticles, type PostListItem } from '../../api/post'
import { fetchPopularTags, type PopularTag } from '../../api/tag'

type CategoryRow = { id: number | string; name: string; depth: number }

const categoryItems = ref<CategoryNode[]>([])
const categoryLoading = ref(false)
const activeCategoryId = ref<number | string | null>(null)
const articles = ref<PostListItem[]>([])
const articlesLoading = ref(false)
const articlesLoadingMore = ref(false)
const nextCursor = ref<string | null>(null)
const articlesError = ref<string | null>(null)
const searchInput = ref('')
const searchKeyword = ref('')
const hotArticles = ref<PostListItem[]>([])
const hotLoading = ref(false)
const tagItems = ref<PopularTag[]>([])
const tagsLoading = ref(false)
const activeTagId = ref<number | string | null>(null)
const PAGE_SIZE = 10

const getArticleFetcher = () => (searchKeyword.value ? searchArticles : fetchArticles)

const categoryRows = computed<CategoryRow[]>(() => {
  const rows: CategoryRow[] = []
  const visit = (nodes: CategoryNode[], depth: number) => {
    nodes.forEach((node) => {
      rows.push({ id: node.id, name: node.name, depth })
      if (node.children?.length) visit(node.children, depth + 1)
    })
  }
  visit(categoryItems.value, 0)
  return rows
})

const categoryCount = computed(() => categoryRows.value.length)
const resultLabel = computed(() => {
  if (searchKeyword.value) return '搜索结果'
  if (activeTagId.value !== null) return '标签文章'
  if (activeCategoryId.value !== null) return '分类文章'
  return '全部文章'
})

const normalizeDate = (value?: string | null) => {
  if (!value) return '未标注日期'
  const parsed = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return value.slice(0, 10)
  return `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}`
}

const formatReads = (value: number) => {
  const count = Number(value) || 0
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w 阅读`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k 阅读`
  return `${count} 阅读`
}

const postTypeLabel = (item: PostListItem) => item.postType === 'essay' ? '随笔' : '文章'
const primaryCategory = (item: PostListItem) => item.categories?.[0]?.name || postTypeLabel(item)

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    categoryItems.value = (await fetchCategoryTree()) ?? []
  } catch {
    categoryItems.value = []
  } finally {
    categoryLoading.value = false
  }
}

const loadArticles = async () => {
  articlesLoading.value = true
  articlesError.value = null
  try {
    const data = await getArticleFetcher()({
      categoryId: activeCategoryId.value,
      tagId: activeTagId.value,
      keyword: searchKeyword.value,
      limit: PAGE_SIZE,
    })
    articles.value = data?.items ?? []
    nextCursor.value = data?.nextCursor ?? null
  } catch {
    articles.value = []
    nextCursor.value = null
    articlesError.value = '加载文章失败，请稍后重试'
  } finally {
    articlesLoading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || articlesLoadingMore.value) return
  articlesLoadingMore.value = true
  try {
    const data = await getArticleFetcher()({
      categoryId: activeCategoryId.value,
      tagId: activeTagId.value,
      keyword: searchKeyword.value,
      cursor: nextCursor.value,
      limit: PAGE_SIZE,
    })
    if (data?.items?.length) articles.value = [...articles.value, ...data.items]
    nextCursor.value = data?.nextCursor ?? null
  } catch {
    articlesError.value = '加载更多失败'
  } finally {
    articlesLoadingMore.value = false
  }
}

const handleCategorySelect = (id: number | string | null) => {
  if (activeCategoryId.value !== id) activeCategoryId.value = id
}

const handleTagSelect = (id: number | string | null) => {
  if (activeTagId.value !== id) activeTagId.value = id
}

const submitSearch = () => {
  const keyword = searchInput.value.trim()
  if (searchKeyword.value !== keyword) searchKeyword.value = keyword
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
}

watch([activeCategoryId, activeTagId, searchKeyword], () => loadArticles())

const loadHotArticles = async () => {
  hotLoading.value = true
  try {
    hotArticles.value = (await fetchHotArticles(5)) ?? []
  } catch {
    hotArticles.value = []
  } finally {
    hotLoading.value = false
  }
}

const loadTags = async () => {
  tagsLoading.value = true
  try {
    tagItems.value = (await fetchPopularTags(20)) ?? []
  } catch {
    tagItems.value = []
  } finally {
    tagsLoading.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadArticles()
  loadHotArticles()
  loadTags()
})
</script>

<template>
  <div class="atlas-archive">
    <header class="archive-hero">
      <div class="atlas-wrap archive-hero__inner">
        <div>
          <p class="hero-eyebrow">archive / field notes</p>
          <h1>文章档案</h1>
          <p class="hero-description">按路线、标签和关键词重新走一遍已经写下的东西。</p>
        </div>
        <div class="archive-index" aria-label="档案索引统计">
          <span><strong>{{ String(articles.length).padStart(2, '0') }}</strong> 当前结果</span>
          <span><strong>{{ String(categoryCount).padStart(2, '0') }}</strong> 分类节点</span>
          <span><strong>{{ String(tagItems.length).padStart(2, '0') }}</strong> 标签索引</span>
        </div>
      </div>
    </header>

    <main class="atlas-wrap archive-body">
      <aside class="filter-rail">
        <div class="rail-sticky">
          <div class="rail-heading">
            <h2>路线筛选</h2>
            <Icon icon="lucide:sliders-horizontal" width="15" height="15" aria-hidden="true" />
          </div>
          <div class="filters" role="group" aria-label="文章筛选">
            <button
              type="button"
              class="filter-button"
              :class="{ 'filter-button--active': activeCategoryId === null && activeTagId === null }"
              :aria-pressed="activeCategoryId === null && activeTagId === null"
              @click="handleCategorySelect(null); handleTagSelect(null)"
            >
              <span>全部文章</span><span class="filter-count">{{ articles.length }}</span>
            </button>
          </div>

          <p class="rail-label">分类</p>
          <div v-if="categoryLoading" class="rail-state">正在读取分类...</div>
          <div v-else-if="categoryRows.length === 0" class="rail-state">暂无分类节点</div>
          <div v-else class="filters">
            <button
              v-for="category in categoryRows"
              :key="`category-${category.id}`"
              type="button"
              class="filter-button filter-button--category"
              :class="{ 'filter-button--active': activeCategoryId === category.id }"
              :style="{ paddingLeft: `${0.6 + category.depth * 0.8}rem` }"
              :aria-pressed="activeCategoryId === category.id"
              @click="handleCategorySelect(category.id); handleTagSelect(null)"
            >
              <span>{{ category.name }}</span>
            </button>
          </div>

          <p class="rail-label">标签索引</p>
          <div v-if="tagsLoading" class="rail-state">正在读取标签...</div>
          <div v-else-if="tagItems.length === 0" class="rail-state">暂无标签节点</div>
          <div v-else class="tag-index">
            <button
              v-for="tag in tagItems"
              :key="tag.id"
              type="button"
              class="tag-button"
              :class="{ 'tag-button--active': activeTagId === tag.id }"
              :aria-pressed="activeTagId === tag.id"
              @click="handleTagSelect(activeTagId === tag.id ? null : tag.id); handleCategorySelect(null)"
            >
              #{{ tag.name }} <span>{{ tag.postCount }}</span>
            </button>
          </div>

          <div class="rail-note">
            <strong>{{ resultLabel }}</strong>
            <p>所有结果来自后端<br>按最新发布排序</p>
          </div>
        </div>
      </aside>

      <section class="archive-column" aria-labelledby="archive-title">
        <div class="archive-toolbar">
          <div class="section-head">
            <div>
              <p class="section-kicker">route index</p>
              <h2 id="archive-title">{{ resultLabel }}</h2>
            </div>
            <span>{{ searchKeyword ? `keyword / ${searchKeyword}` : 'newest first' }}</span>
          </div>
          <form class="search-form" @submit.prevent="submitSearch">
            <label class="search-input-wrap">
              <Icon icon="lucide:search" width="16" height="16" aria-hidden="true" />
              <span class="sr-only">搜索文章</span>
              <input v-model="searchInput" class="search-input" type="search" placeholder="搜索标题、摘要、分类或标签" autocomplete="off">
            </label>
            <button type="submit" class="search-button">
              <Icon icon="lucide:arrow-right" width="16" height="16" aria-hidden="true" />
              <span>检索</span>
            </button>
            <button v-if="searchKeyword" type="button" class="search-clear" @click="clearSearch">
              <Icon icon="lucide:x" width="15" height="15" aria-hidden="true" />
              <span>清空</span>
            </button>
          </form>
        </div>

        <div v-if="articlesLoading" class="timeline-state" aria-live="polite">正在读取文章路线...</div>
        <div v-else-if="articlesError" class="timeline-state timeline-state--error">{{ articlesError }}</div>
        <div v-else-if="articles.length === 0" class="timeline-state">{{ searchKeyword ? '没有找到相关文章。' : '暂无文章节点。' }}</div>

        <div v-else class="archive-list">
          <article v-for="(item, index) in articles" :key="item.id" class="archive-entry" :class="{ 'archive-entry--first': index === 0 }">
            <div class="entry-meta">
              <span class="entry-type">{{ primaryCategory(item) }}</span>
              <span>{{ normalizeDate(item.publishedAt) }}</span>
              <span>{{ postTypeLabel(item) }}</span>
            </div>
            <div class="entry-main">
              <div class="entry-copy">
                <h3><RouterLink :to="{ path: '/article', query: { slug: item.slug, type: item.postType } }">{{ item.title }}</RouterLink></h3>
                <p>{{ item.summary || '这篇记录暂时没有摘要，打开节点查看完整内容。' }}</p>
                <div class="entry-actions">
                  <RouterLink :to="{ path: '/article', query: { slug: item.slug, type: item.postType } }">打开节点 <span aria-hidden="true">↗</span></RouterLink>
                  <span>{{ formatReads(item.viewCount) }}</span>
                  <span v-if="item.likeCount">{{ item.likeCount }} 赞</span>
                </div>
                <div v-if="item.tags?.length" class="entry-tags"><span v-for="tag in item.tags.slice(0, 3)" :key="tag.id">#{{ tag.name }}</span></div>
              </div>
              <img v-if="item.coverUrl" class="entry-cover" :src="item.coverUrl" :alt="item.title" loading="lazy">
            </div>
          </article>

          <button v-if="nextCursor" type="button" class="load-more" :disabled="articlesLoadingMore" @click="loadMore">
            <Icon :icon="articlesLoadingMore ? 'lucide:loader-2' : 'lucide:arrow-down'" width="16" height="16" :class="{ spin: articlesLoadingMore }" aria-hidden="true" />
            <span>{{ articlesLoadingMore ? '正在读取下一段' : '加载更多节点' }}</span>
          </button>
        </div>
      </section>

      <aside class="side-rail">
        <section class="side-block side-author">
          <p class="side-kicker">field recorder</p>
          <div class="author-mark">FL</div>
          <h2>FluxLu</h2>
          <p>记录后端开发、数据库、系统设计与日常踩坑。</p>
          <span class="side-signature">orccode / archive</span>
        </section>

        <section class="side-block">
          <div class="side-title-row"><h2>热门节点</h2><Icon icon="lucide:flame" width="15" height="15" aria-hidden="true" /></div>
          <div v-if="hotLoading" class="side-state">正在读取...</div>
          <div v-else-if="hotArticles.length === 0" class="side-state">暂无热门文章</div>
          <ol v-else class="hot-list">
            <li v-for="(item, index) in hotArticles" :key="item.id">
              <RouterLink :to="{ path: '/article', query: { slug: item.slug, type: item.postType } }">
                <span class="hot-rank" :class="{ 'hot-rank--top': index === 0 }">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="hot-copy"><strong>{{ item.title }}</strong><small>{{ formatReads(item.viewCount) }}</small></span>
              </RouterLink>
            </li>
          </ol>
        </section>

        <section class="side-block side-note">
          <p class="side-kicker">how to read</p>
          <p>从筛选器选择一条路线，或直接搜索关键词。每个节点都连接到后端返回的完整文章内容。</p>
        </section>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.atlas-archive { --atlas-navy:#102a43; --atlas-navy-2:#1d4762; --atlas-paper:#f5f7f2; --atlas-paper-2:#e9efe7; --atlas-ink:#17252c; --atlas-muted:#60717a; --atlas-line:#c8d4cf; --atlas-yellow:#f2c94c; --atlas-green:#2f855a; --atlas-coral:#cc674e; min-height:100vh; overflow:hidden; background:var(--atlas-paper); color:var(--atlas-ink); font-family:var(--font-sans),sans-serif; }
.atlas-wrap { width:min(92rem,calc(100% - 3rem)); margin-inline:auto; }
.archive-hero { padding:4.25rem 0 3rem; color:#fff; background:var(--atlas-navy); }
.archive-hero__inner { display:flex; align-items:end; justify-content:space-between; gap:3rem; }
.hero-eyebrow,.section-kicker,.side-kicker { margin:0 0 .85rem; color:var(--atlas-yellow); font-size:.68rem; font-weight:800; letter-spacing:.14em; text-transform:uppercase; }
.archive-hero h1 { margin:0; font-size:clamp(3.1rem,7vw,5.4rem); font-weight:800; line-height:.95; }
.hero-description { max-width:34rem; margin:1.35rem 0 0; color:#cbd9e0; font-size:.98rem; line-height:1.8; }
.archive-index { display:grid; gap:.7rem; min-width:10rem; color:#a9bec9; font-size:.72rem; text-align:right; }
.archive-index span { border-top:1px solid rgba(255,255,255,.22); padding-top:.55rem; }
.archive-index strong { margin-right:.35rem; color:#fff; font-size:1.15rem; font-variant-numeric:tabular-nums; }
.archive-body { display:grid; grid-template-columns:13rem minmax(0,1fr) 15rem; gap:4rem; padding-top:3.5rem; padding-bottom:5rem; }
.filter-rail,.side-rail { color:var(--atlas-muted); font-size:.8rem; }
.rail-sticky { position:sticky; top:5.75rem; }
.rail-heading,.side-title-row { display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
.rail-heading h2,.side-title-row h2,.side-author h2 { margin:0; color:var(--atlas-ink); font-size:.72rem; font-weight:800; letter-spacing:.13em; text-transform:uppercase; }
.rail-heading>svg,.side-title-row>svg { color:var(--atlas-green); }
.filters { display:grid; gap:.35rem; margin-top:.85rem; }
.filter-button { display:flex; align-items:center; justify-content:space-between; width:100%; border:0; border-left:2px solid transparent; padding:.45rem .6rem; color:var(--atlas-muted); background:transparent; font-size:.78rem; text-align:left; cursor:pointer; transition:background .18s ease,color .18s ease,border-color .18s ease; }
.filter-button:hover,.filter-button--active { border-left-color:var(--atlas-green); color:var(--atlas-ink); background:var(--atlas-paper-2); font-weight:700; }
.filter-button:focus-visible,.tag-button:focus-visible,.search-button:focus-visible,.search-clear:focus-visible,.load-more:focus-visible { outline:2px solid var(--atlas-yellow); outline-offset:2px; }
.filter-count { color:var(--atlas-muted); font-size:.65rem; font-variant-numeric:tabular-nums; }
.rail-label { margin:1.7rem 0 .5rem; color:var(--atlas-ink); font-size:.65rem; font-weight:800; letter-spacing:.12em; text-transform:uppercase; }
.rail-state { border-left:1px dashed var(--atlas-line); padding:.35rem .6rem; color:var(--atlas-muted); font-size:.72rem; }
.tag-index { display:flex; flex-wrap:wrap; gap:.35rem .5rem; }
.tag-button { border:0; border-bottom:1px solid var(--atlas-line); padding:.15rem 0; color:var(--atlas-muted); background:transparent; font-size:.7rem; cursor:pointer; }
.tag-button span { color:var(--atlas-green); font-size:.62rem; }
.tag-button:hover,.tag-button--active { border-bottom-color:var(--atlas-green); color:var(--atlas-green); }
.rail-note { margin-top:2rem; border-top:1px solid var(--atlas-line); padding-top:1rem; }
.rail-note strong { display:block; margin-bottom:.25rem; color:var(--atlas-green); font-size:1.05rem; }
.rail-note p { margin:0; font-size:.72rem; line-height:1.55; }
.archive-column { min-width:0; }
.archive-toolbar { margin-bottom:1.2rem; }
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
.archive-list { position:relative; margin-left:1rem; padding-left:2.4rem; }
.archive-list::before { position:absolute; left:.35rem; top:.45rem; bottom:1rem; width:2px; background:var(--atlas-green); content:''; }
.archive-entry { position:relative; margin-bottom:1.45rem; border-bottom:1px solid var(--atlas-line); padding-bottom:1.45rem; }
.archive-entry::before { position:absolute; left:-2.4rem; top:.35rem; width:.8rem; height:.8rem; border:3px solid var(--atlas-green); border-radius:50%; background:var(--atlas-paper); content:''; }
.archive-entry--first::before { border-color:var(--atlas-yellow); background:var(--atlas-yellow); box-shadow:0 0 0 4px var(--atlas-paper); }
.entry-meta { display:flex; flex-wrap:wrap; align-items:center; gap:.55rem .7rem; margin-bottom:.4rem; color:var(--atlas-muted); font-size:.69rem; }
.entry-type { color:var(--atlas-green); font-size:.66rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
.entry-main { display:flex; align-items:flex-start; gap:1.25rem; }
.entry-copy { min-width:0; flex:1; }
.entry-copy h3 { margin:0 0 .5rem; color:var(--atlas-ink); font-size:1.25rem; font-weight:700; line-height:1.35; }
.entry-copy h3 a:hover { color:var(--atlas-green); }
.entry-copy p { max-width:52rem; margin:0; color:var(--atlas-muted); font-size:.84rem; line-height:1.7; display:-webkit-box; overflow:hidden; -webkit-box-orient:vertical; -webkit-line-clamp:3; }
.entry-actions { display:flex; flex-wrap:wrap; gap:.7rem 1rem; margin-top:.75rem; color:var(--atlas-green); font-size:.72rem; font-weight:700; }
.entry-actions a:hover { text-decoration:underline; text-underline-offset:3px; }
.entry-tags { display:flex; flex-wrap:wrap; gap:.4rem; margin-top:.65rem; color:var(--atlas-muted); font-size:.67rem; }
.entry-cover { width:7rem; height:5.25rem; flex:0 0 7rem; border:1px solid var(--atlas-line); object-fit:cover; filter:saturate(.8); }
.load-more { width:calc(100% + 2.4rem); margin-left:-2.4rem; }
.load-more:disabled { cursor:wait; opacity:.65; }
.spin { animation:spin .9s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
.side-block { border-top:1px solid var(--atlas-line); padding-top:1rem; }
.side-block+.side-block { margin-top:2rem; }
.side-author { border-top:0; padding-top:0; }
.side-author .side-kicker { color:var(--atlas-coral); }
.author-mark { display:grid; width:3.25rem; height:3.25rem; margin:0 0 .85rem; place-items:center; color:var(--atlas-navy); background:var(--atlas-yellow); font-size:1rem; font-weight:900; letter-spacing:.04em; }
.side-author h2 { font-size:1.05rem; letter-spacing:0; text-transform:none; }
.side-author>p:not(.side-kicker) { margin:.55rem 0 0; color:var(--atlas-muted); font-size:.78rem; line-height:1.65; }
.side-signature { display:block; margin-top:.9rem; color:var(--atlas-green); font-size:.65rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
.hot-list { display:grid; gap:0; list-style:none; margin:.65rem 0 0; padding:0; }
.hot-list li { border-bottom:1px solid var(--atlas-line); }
.hot-list a { display:flex; align-items:flex-start; gap:.65rem; padding:.7rem 0; }
.hot-rank { flex:0 0 1.65rem; color:var(--atlas-muted); font-size:.68rem; font-variant-numeric:tabular-nums; }
.hot-rank--top { color:var(--atlas-coral); font-weight:800; }
.hot-copy { display:grid; gap:.25rem; min-width:0; }
.hot-copy strong { overflow:hidden; color:var(--atlas-ink); font-size:.78rem; font-weight:700; line-height:1.45; text-overflow:ellipsis; display:-webkit-box; -webkit-box-orient:vertical; -webkit-line-clamp:2; }
.hot-list a:hover .hot-copy strong { color:var(--atlas-green); }
.hot-copy small,.side-state { color:var(--atlas-muted); font-size:.67rem; }
.side-state { padding:.7rem 0; }
.side-note p:last-child { margin:0; color:var(--atlas-muted); font-size:.76rem; line-height:1.65; }
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
:global(html[data-theme='dark'] .atlas-archive),:global(html[data-theme='ocean'] .atlas-archive) { --atlas-paper:#12232b; --atlas-paper-2:#1b3439; --atlas-ink:#e4eee9; --atlas-muted:#a9bec1; --atlas-line:#385158; }
@media (max-width:1150px) { .archive-body { grid-template-columns:11rem minmax(0,1fr); gap:3rem; } .side-rail { display:none; } }
@media (max-width:720px) { .atlas-wrap { width:min(100% - 2rem,92rem); } .archive-hero { padding:3.5rem 0 2.5rem; } .archive-hero__inner { display:block; } .archive-index { grid-template-columns:repeat(3,1fr); gap:.5rem; margin-top:2rem; text-align:left; } .archive-index strong { display:block; margin:0 0 .2rem; } .archive-body { display:block; padding-top:2.5rem; } .filter-rail { margin-bottom:2.5rem; } .rail-sticky { position:static; } .filters { display:flex; flex-wrap:wrap; gap:.35rem; } .filter-button { width:auto; border:1px solid var(--atlas-line); padding:.35rem .55rem; } .filter-button--active { border-color:var(--atlas-green); } .rail-label { margin-top:1.25rem; } .rail-note { display:none; } .search-form { grid-template-columns:minmax(0,1fr) auto; } .search-clear { grid-column:1 / -1; justify-self:start; } .archive-list { margin-left:.5rem; padding-left:2rem; } .archive-list::before { left:.35rem; } .archive-entry::before { left:-2rem; } .entry-main { display:block; } .entry-cover { width:100%; height:9rem; margin-top:.85rem; } .load-more { width:calc(100% + 2rem); margin-left:-2rem; } .section-head { align-items:flex-start; flex-direction:column; gap:.25rem; } .section-head>span { max-width:100%; white-space:normal; } }
@media (prefers-reduced-motion:reduce) { .filter-button,.search-button,.search-clear,.load-more,.spin { transition:none; animation:none; } }
</style>
