<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import CategoryNav from '../home/components/CategoryNav.vue'
import TagCloud from '../home/components/TagCloud.vue'
import ArticleCard from '../home/components/ArticleCard.vue'
import AuthorCard from '../home/components/AuthorCard.vue'
import HotList from '../home/components/HotList.vue'
import { fetchCategoryTree, type CategoryNode } from '../../api/category'
import { fetchArticles, fetchHotArticles, searchArticles, type PostListItem } from '../../api/post'
import { fetchPopularTags, type PopularTag } from '../../api/tag'

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

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    const data = await fetchCategoryTree()
    categoryItems.value = data ?? []
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
    nextCursor.value = data?.next_cursor ?? null
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
    if (data?.items?.length) {
      articles.value = [...articles.value, ...data.items]
    }
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    articlesError.value = '加载更多失败'
  } finally {
    articlesLoadingMore.value = false
  }
}

const handleCategorySelect = (id: number | string | null) => {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
}

const handleTagSelect = (id: number | string | null) => {
  if (activeTagId.value === id) return
  activeTagId.value = id
}

const submitSearch = () => {
  const keyword = searchInput.value.trim()
  if (searchKeyword.value === keyword) return
  searchKeyword.value = keyword
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
}

watch([activeCategoryId, activeTagId, searchKeyword], () => {
  loadArticles()
})

const loadHotArticles = async () => {
  hotLoading.value = true
  try {
    const data = await fetchHotArticles(5)
    hotArticles.value = data ?? []
  } catch {
    hotArticles.value = []
  } finally {
    hotLoading.value = false
  }
}

const loadTags = async () => {
  tagsLoading.value = true
  try {
    const data = await fetchPopularTags(20)
    tagItems.value = data ?? []
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
  <div class="articles-browser">
    <!-- 左侧边栏 -->
    <aside class="articles-sidebar">
      <CategoryNav
        :items="categoryItems"
        :loading="categoryLoading"
        :active-id="activeCategoryId"
        @select="handleCategorySelect"
      />
      <TagCloud
        :tags="tagItems"
        :loading="tagsLoading"
        :active-id="activeTagId"
        @select="handleTagSelect"
      />
    </aside>

    <!-- 主内容区 -->
    <main class="articles-main">
      <!-- 搜索区 -->
      <section class="search-panel" aria-label="文章搜索">
        <div>
          <p class="search-eyebrow">Articles · Search</p>
          <h2 class="section-title">
            {{ searchKeyword ? '搜索结果' : activeCategoryId ? '分类文章' : '全部文章' }}
          </h2>
        </div>
        <form class="search-form" @submit.prevent="submitSearch">
          <label class="search-input-wrap">
            <span class="search-icon" aria-hidden="true">⌕</span>
            <input
              v-model="searchInput"
              class="search-input"
              type="search"
              placeholder="搜索文章标题、摘要、分类或标签"
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

      <!-- 文章列表 -->
      <div v-if="articlesLoading" class="state-card">正在加载...</div>
      <div v-else-if="articlesError" class="state-card state-card--error">{{ articlesError }}</div>
      <div v-else-if="articles.length === 0" class="state-card">
        {{ searchKeyword ? '没有找到相关文章' : '暂无文章' }}
      </div>

      <div v-else class="article-list">
        <ArticleCard
          v-for="item in articles"
          :key="item.id"
          :slug="item.slug"
          :title="item.title"
          :summary="item.summary"
          :post-type="item.post_type"
          :categories="item.categories"
          :tags="item.tags"
          :published-at="item.published_at"
          :cover-url="item.cover_url"
        />

        <button
          v-if="nextCursor"
          type="button"
          class="load-more"
          :disabled="articlesLoadingMore"
          @click="loadMore"
        >
          {{ articlesLoadingMore ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </main>

    <!-- 右侧边栏 -->
    <aside class="articles-sidebar">
      <AuthorCard
        name="FluxLu"
        bio="计算机专业开发者，记录后端开发、数据库、系统设计与日常踩坑。"
      />
      <HotList :items="hotArticles" :loading="hotLoading" />
    </aside>
  </div>
</template>

<style scoped>
/* ── 布局 ── */
.articles-browser {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: 1fr;
}

@media (min-width: 1024px) {
  .articles-browser {
    grid-template-columns: 260px minmax(0, 1fr) 300px;
  }
}

.articles-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

@media (min-width: 1024px) {
  .articles-sidebar {
    position: sticky;
    top: 6.5rem;
    align-self: start;
    max-height: calc(100vh - 7.5rem);
  }
}

.articles-main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

/* ── 搜索面板 ── */
.search-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background:
    radial-gradient(circle at 12% 10%, color-mix(in srgb, var(--color-accent) 16%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  padding: 1rem;
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.search-eyebrow {
  margin: 0 0 0.25rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.625rem;
  margin-top: 0.875rem;
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
  font-size: 1.05rem;
  line-height: 1;
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
  transition: transform 0.15s ease, opacity 0.15s ease, background 0.15s ease;
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
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

@media (min-width: 640px) {
  .search-panel {
    padding: 1.1rem 1.2rem;
  }

  .search-form {
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
  }
}

/* ── 状态卡片 ── */
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

/* ── 文章列表 ── */
.article-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* ── 加载更多 ── */
.load-more {
  display: block;
  width: 100%;
  padding: 0.75rem 1rem;
  border-radius: 0.75rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.load-more:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-text-secondary) 30%, var(--color-border));
}

.load-more:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
