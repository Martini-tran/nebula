<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchEssays, searchEssays, type PostListItem } from '../../api/post'

const essays = ref<PostListItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref<string | null>(null)
const nextCursor = ref<string | null>(null)
const searchInput = ref('')
const searchKeyword = ref('')
const PAGE_SIZE = 10

const fetcher = computed(() => (searchKeyword.value ? searchEssays : fetchEssays))

const tagIndex = computed(() => {
  const tags = new Map<string, { name: string; count: number }>()
  essays.value.forEach((essay) => {
    essay.tags?.forEach((tag) => {
      const key = String(tag.id)
      const existing = tags.get(key)
      tags.set(key, { name: tag.name, count: (existing?.count ?? 0) + 1 })
    })
  })
  return [...tags.values()].sort((a, b) => b.count - a.count).slice(0, 8)
})

const latestDate = computed(() => essays.value[0]?.publishedAt ? formatDate(essays.value[0].publishedAt) : '等待更新')
const totalViews = computed(() => essays.value.reduce((sum, essay) => sum + (Number(essay.viewCount) || 0), 0))

const loadEssays = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await fetcher.value({ keyword: searchKeyword.value, limit: PAGE_SIZE })
    essays.value = data?.items ?? []
    nextCursor.value = data?.nextCursor ?? null
  } catch {
    essays.value = []
    nextCursor.value = null
    error.value = '加载随笔失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const data = await fetcher.value({ keyword: searchKeyword.value, cursor: nextCursor.value, limit: PAGE_SIZE })
    if (data?.items?.length) essays.value = [...essays.value, ...data.items]
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
  loadEssays()
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
  loadEssays()
}

function formatDate(value: string) {
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const formatReads = (value: number) => {
  const count = Number(value) || 0
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w 阅读`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k 阅读`
  return `${count} 阅读`
}

onMounted(loadEssays)
</script>

<template>
  <div class="atlas-essays">
    <header class="essay-hero">
      <div class="atlas-wrap essay-hero__inner">
        <div>
          <p class="hero-eyebrow">side notes / field journal</p>
          <h1>把心事<br>写成小路。</h1>
          <p class="hero-description">一些不急着归档的想法、午后的片段和读书时留下的批注。</p>
        </div>
        <div class="essay-index" aria-label="随笔统计">
          <span><strong>{{ String(essays.length).padStart(2, '0') }}</strong> 当前片段</span>
          <span><strong>{{ String(tagIndex.length).padStart(2, '0') }}</strong> 标签线索</span>
          <span><strong>{{ formatReads(totalViews) }}</strong> 总阅读</span>
        </div>
      </div>
    </header>

    <main class="atlas-wrap essay-body">
      <aside class="essay-rail">
        <div class="rail-sticky">
          <div class="rail-heading">
            <h2>写作路线</h2>
            <Icon icon="lucide:pen-line" width="15" height="15" aria-hidden="true" />
          </div>
          <div class="rail-route">
            <div class="rail-route__line" aria-hidden="true" />
            <div class="rail-route__node rail-route__node--active" aria-hidden="true" />
            <p><strong>随笔</strong><br>不必抵达结论<br>先把过程留下来</p>
          </div>

          <p class="rail-label">标签线索</p>
          <div v-if="tagIndex.length" class="tag-index">
            <span v-for="tag in tagIndex" :key="tag.name">#{{ tag.name }} <small>{{ tag.count }}</small></span>
          </div>
          <p v-else class="rail-state">等待后端标签</p>

          <div class="rail-note">
            <strong>{{ latestDate }}</strong>
            <p>最后一次更新<br>随笔行进记录</p>
          </div>
        </div>
      </aside>

      <section class="essay-column" aria-labelledby="essay-title">
        <div class="essay-toolbar">
          <div class="section-head">
            <div>
              <p class="section-kicker">quiet observations</p>
              <h2 id="essay-title">{{ searchKeyword ? '搜索结果' : '随笔记录' }}</h2>
            </div>
            <span>{{ searchKeyword ? `keyword / ${searchKeyword}` : 'newest first' }}</span>
          </div>
          <form class="search-form" @submit.prevent="submitSearch">
            <label class="search-input-wrap">
              <Icon icon="lucide:search" width="16" height="16" aria-hidden="true" />
              <span class="sr-only">搜索随笔</span>
              <input v-model="searchInput" class="search-input" type="search" placeholder="搜索随笔标题或摘要" autocomplete="off">
            </label>
            <button type="submit" class="search-button"><Icon icon="lucide:arrow-right" width="16" height="16" aria-hidden="true" /><span>检索</span></button>
            <button v-if="searchKeyword" type="button" class="search-clear" @click="clearSearch"><Icon icon="lucide:x" width="15" height="15" aria-hidden="true" /><span>清空</span></button>
          </form>
        </div>

        <div v-if="loading" class="timeline-state" aria-live="polite">正在读取随笔路线...</div>
        <div v-else-if="error" class="timeline-state timeline-state--error">{{ error }}</div>
        <div v-else-if="essays.length === 0" class="timeline-state">{{ searchKeyword ? '没有找到相关随笔。' : '还没有随笔节点。' }}</div>

        <ol v-else class="essay-list">
          <li v-for="(item, index) in essays" :key="item.id" class="essay-entry" :class="{ 'essay-entry--first': index === 0 }">
            <div class="entry-meta">
              <span class="entry-type">随笔</span>
              <time :datetime="item.publishedAt">{{ formatDate(item.publishedAt) }}</time>
            </div>
            <h3><RouterLink :to="{ path: '/article', query: { slug: item.slug, type: 'essay' } }">{{ item.title }}</RouterLink></h3>
            <p>{{ item.summary || '这段想法暂时没有摘要，打开节点查看完整内容。' }}</p>
            <div class="entry-actions">
              <RouterLink :to="{ path: '/article', query: { slug: item.slug, type: 'essay' } }">打开节点 <span aria-hidden="true">↗</span></RouterLink>
              <span>{{ formatReads(item.viewCount) }}</span>
              <span v-if="item.likeCount">{{ item.likeCount }} 赞</span>
            </div>
            <div v-if="item.tags?.length" class="entry-tags"><span v-for="tag in item.tags.slice(0, 4)" :key="tag.id">#{{ tag.name }}</span></div>
          </li>

          <li v-if="nextCursor" class="load-more-row">
            <button type="button" class="load-more" :disabled="loadingMore" @click="loadMore">
              <Icon :icon="loadingMore ? 'lucide:loader-2' : 'lucide:arrow-down'" width="16" height="16" :class="{ spin: loadingMore }" aria-hidden="true" />
              <span>{{ loadingMore ? '正在读取下一段' : '加载更多片段' }}</span>
            </button>
          </li>
        </ol>
      </section>

      <aside class="side-rail">
        <section class="side-block side-note">
          <p class="side-kicker">field journal</p>
          <div class="note-mark">SN</div>
          <h2>留一点空白</h2>
          <p>随笔不追求完整的论证，只记录某个瞬间如何改变了方向。</p>
        </section>
        <section class="side-block">
          <div class="side-title-row"><h2>阅读方式</h2><Icon icon="lucide:bookmark" width="15" height="15" aria-hidden="true" /></div>
          <p class="side-copy">从最新片段开始，也可以沿着标签线索回到某个反复出现的主题。</p>
        </section>
        <section class="side-block side-legend">
          <p class="side-kicker">index note</p>
          <p>所有内容由后端返回，页面只负责整理成一条可回看的路线。</p>
        </section>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.atlas-essays { --atlas-navy:#102a43; --atlas-paper:#f5f7f2; --atlas-paper-2:#e9efe7; --atlas-ink:#17252c; --atlas-muted:#60717a; --atlas-line:#c8d4cf; --atlas-yellow:#f2c94c; --atlas-green:#2f855a; --atlas-coral:#cc674e; min-height:100vh; overflow:hidden; background:var(--atlas-paper); color:var(--atlas-ink); font-family:var(--font-sans),sans-serif; }
.atlas-wrap { width:min(92rem,calc(100% - 3rem)); margin-inline:auto; }
.essay-hero { padding:4.25rem 0 3rem; color:#fff; background:var(--atlas-navy); }
.essay-hero__inner { display:flex; align-items:end; justify-content:space-between; gap:3rem; }
.hero-eyebrow,.section-kicker,.side-kicker { margin:0 0 .85rem; color:var(--atlas-yellow); font-size:.68rem; font-weight:800; letter-spacing:.14em; text-transform:uppercase; }
.essay-hero h1 { margin:0; font-size:clamp(3.1rem,7vw,5.4rem); font-weight:800; line-height:.95; }
.hero-description { max-width:34rem; margin:1.35rem 0 0; color:#cbd9e0; font-size:.98rem; line-height:1.8; }
.essay-index { display:grid; gap:.7rem; min-width:11rem; color:#a9bec9; font-size:.72rem; text-align:right; }
.essay-index span { border-top:1px solid rgba(255,255,255,.22); padding-top:.55rem; }
.essay-index strong { margin-right:.35rem; color:#fff; font-size:1.05rem; font-variant-numeric:tabular-nums; }
.essay-body { display:grid; grid-template-columns:13rem minmax(0,1fr) 15rem; gap:4rem; padding-top:3.5rem; padding-bottom:5rem; }
.essay-rail,.side-rail { color:var(--atlas-muted); font-size:.8rem; }
.rail-sticky { position:sticky; top:5.75rem; }
.rail-heading,.side-title-row { display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
.rail-heading h2,.side-title-row h2,.side-note h2 { margin:0; color:var(--atlas-ink); font-size:.72rem; font-weight:800; letter-spacing:.13em; text-transform:uppercase; }
.rail-heading>svg,.side-title-row>svg { color:var(--atlas-green); }
.rail-route { position:relative; min-height:9rem; margin-top:1.1rem; padding:1rem 0 0 1.8rem; }
.rail-route__line { position:absolute; left:.4rem; top:.6rem; bottom:.5rem; width:2px; background:var(--atlas-green); }
.rail-route__node { position:absolute; left:.05rem; top:.85rem; width:.8rem; height:.8rem; border:3px solid var(--atlas-yellow); border-radius:50%; background:var(--atlas-yellow); box-shadow:0 0 0 4px var(--atlas-paper); }
.rail-route p { margin:0; color:var(--atlas-muted); font-size:.75rem; line-height:1.7; }
.rail-route strong { color:var(--atlas-green); font-size:1rem; }
.rail-label { margin:1.4rem 0 .55rem; color:var(--atlas-ink); font-size:.65rem; font-weight:800; letter-spacing:.12em; text-transform:uppercase; }
.tag-index { display:flex; flex-wrap:wrap; gap:.35rem .55rem; }
.tag-index span { border-bottom:1px solid var(--atlas-line); padding-bottom:.15rem; color:var(--atlas-muted); font-size:.7rem; }
.tag-index small { color:var(--atlas-green); font-size:.62rem; }
.rail-state { border-left:1px dashed var(--atlas-line); padding:.35rem .6rem; color:var(--atlas-muted); font-size:.72rem; }
.rail-note { margin-top:2rem; border-top:1px solid var(--atlas-line); padding-top:1rem; }
.rail-note strong { display:block; margin-bottom:.25rem; color:var(--atlas-green); font-size:1.05rem; }
.rail-note p { margin:0; font-size:.72rem; line-height:1.55; }
.essay-column { min-width:0; }
.essay-toolbar { margin-bottom:1.2rem; }
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
.essay-list { position:relative; list-style:none; margin:0 0 0 1rem; padding:0 0 0 2.4rem; }
.essay-list::before { position:absolute; left:.35rem; top:.45rem; bottom:1rem; width:2px; background:var(--atlas-green); content:''; }
.essay-entry { position:relative; margin-bottom:1.45rem; border-bottom:1px solid var(--atlas-line); padding-bottom:1.45rem; }
.essay-entry::before { position:absolute; left:-2.4rem; top:.35rem; width:.8rem; height:.8rem; border:3px solid var(--atlas-green); border-radius:50%; background:var(--atlas-paper); content:''; }
.essay-entry--first::before { border-color:var(--atlas-yellow); background:var(--atlas-yellow); box-shadow:0 0 0 4px var(--atlas-paper); }
.entry-meta { display:flex; flex-wrap:wrap; align-items:center; gap:.55rem .7rem; margin-bottom:.4rem; color:var(--atlas-muted); font-size:.69rem; }
.entry-type { color:var(--atlas-coral); font-size:.66rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
.essay-entry h3 { margin:0 0 .5rem; color:var(--atlas-ink); font-size:1.25rem; font-weight:700; line-height:1.35; }
.essay-entry h3 a:hover { color:var(--atlas-green); }
.essay-entry>p { max-width:52rem; margin:0; color:var(--atlas-muted); font-size:.84rem; line-height:1.7; display:-webkit-box; overflow:hidden; -webkit-box-orient:vertical; -webkit-line-clamp:3; }
.entry-actions { display:flex; flex-wrap:wrap; gap:.7rem 1rem; margin-top:.75rem; color:var(--atlas-green); font-size:.72rem; font-weight:700; }
.entry-actions a:hover { text-decoration:underline; text-underline-offset:3px; }
.entry-tags { display:flex; flex-wrap:wrap; gap:.4rem; margin-top:.65rem; color:var(--atlas-muted); font-size:.67rem; }
.load-more-row { margin-left:-2.4rem; }
.load-more { width:calc(100% + 2.4rem); }
.load-more:disabled { cursor:wait; opacity:.65; }
.spin { animation:spin .9s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
.side-block { border-top:1px solid var(--atlas-line); padding-top:1rem; }
.side-block+.side-block { margin-top:2rem; }
.side-note { border-top:0; padding-top:0; }
.side-kicker { color:var(--atlas-coral); }
.note-mark { display:grid; width:3.25rem; height:3.25rem; margin:0 0 .85rem; place-items:center; color:var(--atlas-navy); background:var(--atlas-yellow); font-size:1rem; font-weight:900; letter-spacing:.04em; }
.side-note h2 { font-size:1.05rem; letter-spacing:0; text-transform:none; }
.side-note>p:not(.side-kicker),.side-copy,.side-legend p:last-child { margin:.55rem 0 0; color:var(--atlas-muted); font-size:.78rem; line-height:1.65; }
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
:global(html[data-theme='dark'] .atlas-essays),:global(html[data-theme='ocean'] .atlas-essays) { --atlas-paper:#12232b; --atlas-paper-2:#1b3439; --atlas-ink:#e4eee9; --atlas-muted:#a9bec1; --atlas-line:#385158; }
@media (max-width:1150px) { .essay-body { grid-template-columns:11rem minmax(0,1fr); gap:3rem; } .side-rail { display:none; } }
@media (max-width:720px) { .atlas-wrap { width:min(100% - 2rem,92rem); } .essay-hero { padding:3.5rem 0 2.5rem; } .essay-hero__inner { display:block; } .essay-index { grid-template-columns:repeat(3,1fr); gap:.5rem; margin-top:2rem; text-align:left; } .essay-index strong { display:block; margin:0 0 .2rem; } .essay-body { display:block; padding-top:2.5rem; } .essay-rail { margin-bottom:2.5rem; } .rail-sticky { position:static; } .rail-route { min-height:6rem; } .rail-note { display:none; } .search-form { grid-template-columns:minmax(0,1fr) auto; } .search-clear { grid-column:1 / -1; justify-self:start; } .essay-list { margin-left:.5rem; padding-left:2rem; } .essay-list::before { left:.35rem; } .essay-entry::before { left:-2rem; } .load-more-row { margin-left:-2rem; } .load-more { width:calc(100% + 2rem); } .section-head { align-items:flex-start; flex-direction:column; gap:.25rem; } .section-head>span { max-width:100%; white-space:normal; } }
@media (prefers-reduced-motion:reduce) { .search-button,.search-clear,.load-more,.spin { transition:none; animation:none; } }
</style>
