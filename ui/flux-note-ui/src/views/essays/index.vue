<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ArticleCard from '../home/components/ArticleCard.vue'
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

const loadEssays = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await fetcher.value({
      keyword: searchKeyword.value,
      limit: PAGE_SIZE,
    })
    essays.value = data?.items ?? []
    nextCursor.value = data?.next_cursor ?? null
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
    const data = await fetcher.value({
      keyword: searchKeyword.value,
      cursor: nextCursor.value,
      limit: PAGE_SIZE,
    })
    if (data?.items?.length) {
      essays.value = [...essays.value, ...data.items]
    }
    nextCursor.value = data?.next_cursor ?? null
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

onMounted(loadEssays)
</script>

<template>
  <section class="essay-page">
    <div class="essay-hero">
      <p>Essays</p>
      <h2>随笔</h2>
      <span>不一定完整，但尽量诚实。这里放一些路上想到的、读完留下的、以及一时半会儿还没归档成文章的东西。</span>
    </div>

    <section class="essay-toolbar" aria-label="随笔搜索">
      <form class="essay-search" @submit.prevent="submitSearch">
        <input
          v-model="searchInput"
          type="search"
          placeholder="搜索随笔标题或摘要"
          autocomplete="off"
        >
        <button type="submit">搜索</button>
        <button v-if="searchKeyword" type="button" class="ghost" @click="clearSearch">
          清空
        </button>
      </form>
      <p v-if="searchKeyword">正在搜索「{{ searchKeyword }}」</p>
    </section>

    <div v-if="loading" class="state-card">正在加载...</div>
    <div v-else-if="error" class="state-card state-card--error">{{ error }}</div>
    <div v-else-if="essays.length === 0" class="state-card">
      {{ searchKeyword ? '没有找到相关随笔' : '暂无随笔' }}
    </div>

    <div v-else class="essay-list">
      <ArticleCard
        v-for="item in essays"
        :key="item.id"
        :slug="item.slug"
        :title="item.title"
        :post-type="item.post_type"
        :summary="item.summary"
        :categories="item.categories"
        :tags="item.tags"
        :published-at="item.published_at"
        :cover-url="item.cover_url"
      />

      <button
        v-if="nextCursor"
        type="button"
        class="load-more"
        :disabled="loadingMore"
        @click="loadMore"
      >
        {{ loadingMore ? '加载中...' : '加载更多' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.essay-page {
  display: grid;
  gap: 1rem;
  max-width: 820px;
  margin: 0 auto;
}

.essay-hero {
  border-radius: 1.5rem;
  border: 1px solid var(--color-border);
  padding: clamp(1.35rem, 4vw, 2.6rem);
  background:
    linear-gradient(120deg, color-mix(in srgb, var(--color-accent-soft) 70%, transparent), transparent),
    var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.essay-hero p {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.essay-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
}

.essay-hero span {
  display: block;
  max-width: 42rem;
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.essay-toolbar {
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1rem;
}

.essay-toolbar p {
  margin: 0.75rem 0 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.essay-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.65rem;
}

.essay-search input {
  min-width: 0;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  padding: 0.75rem 1rem;
  color: var(--color-text-primary);
  outline: 0;
}

.essay-search input:focus {
  border-color: color-mix(in srgb, var(--color-accent) 55%, var(--color-border));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 14%, transparent);
}

.essay-search button,
.load-more {
  border: 0;
  border-radius: 999px;
  padding: 0.75rem 1rem;
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
  font-weight: 700;
  cursor: pointer;
}

.essay-search .ghost,
.load-more {
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
}

.essay-list {
  display: grid;
  gap: 1rem;
}

.state-card {
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.5rem;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state-card--error {
  color: #b91c1c;
}

.load-more {
  width: 100%;
}

.load-more:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (min-width: 640px) {
  .essay-search {
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
  }
}
</style>
