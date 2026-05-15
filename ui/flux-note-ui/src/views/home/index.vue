<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import CategoryNav from './components/CategoryNav.vue'
import TagCloud from './components/TagCloud.vue'
import ArticleCard from './components/ArticleCard.vue'
import AuthorCard from './components/AuthorCard.vue'
import HotList from './components/HotList.vue'
import { fetchCategoryTree, type CategoryNode } from '../../api/category'
import { fetchArticles, fetchHotArticles, type PostListItem } from '../../api/post'
import { fetchPopularTags, type PopularTag } from '../../api/tag'

const categoryItems = ref<CategoryNode[]>([])
const categoryLoading = ref(false)
const activeCategoryId = ref<number | null>(null)

const articles = ref<PostListItem[]>([])
const articlesLoading = ref(false)
const articlesLoadingMore = ref(false)
const nextCursor = ref<string | null>(null)
const articlesError = ref<string | null>(null)

const hotArticles = ref<PostListItem[]>([])
const hotLoading = ref(false)

const tagItems = ref<PopularTag[]>([])
const tagsLoading = ref(false)
const activeTagId = ref<number | null>(null)

const PAGE_SIZE = 10

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
    const data = await fetchArticles({
      categoryId: activeCategoryId.value,
      tagId: activeTagId.value,
      limit: PAGE_SIZE,
    })
    articles.value = data?.items ?? []
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    articles.value = []
    nextCursor.value = null
    articlesError.value = '加载文章失败,请稍后重试'
  } finally {
    articlesLoading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || articlesLoadingMore.value) return
  articlesLoadingMore.value = true
  try {
    const data = await fetchArticles({
      categoryId: activeCategoryId.value,
      tagId: activeTagId.value,
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

const handleCategorySelect = (id: number | null) => {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
}

const handleTagSelect = (id: number | null) => {
  if (activeTagId.value === id) return
  activeTagId.value = id
}

watch([activeCategoryId, activeTagId], () => {
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
  <div class="home-grid">
    <aside class="home-sidebar">
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

    <main>
      <h2 class="section-title">最新文章</h2>

      <div v-if="articlesLoading" class="state-card">正在加载...</div>
      <div v-else-if="articlesError" class="state-card state-card--error">{{ articlesError }}</div>
      <div v-else-if="articles.length === 0" class="state-card">暂无文章</div>

      <div v-else class="space-y-4">
        <ArticleCard
          v-for="item in articles"
          :key="item.id"
          :slug="item.slug"
          :title="item.title"
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
          :disabled="articlesLoadingMore"
          @click="loadMore"
        >
          {{ articlesLoadingMore ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </main>

    <aside class="home-sidebar">
      <AuthorCard
        name="FluxLu"
        bio="计算机专业开发者，记录后端开发、数据库、系统设计与日常踩坑。"
      />
      <HotList :items="hotArticles" :loading="hotLoading" />
    </aside>
  </div>
</template>

<style scoped>
.home-grid {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: 1fr;
}

@media (min-width: 1024px) {
  .home-grid {
    grid-template-columns: 260px minmax(0, 1fr) 300px;
  }
}

.home-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

@media (min-width: 1024px) {
  .home-sidebar {
    position: sticky;
    top: 6.5rem;
    align-self: start;
    max-height: calc(100vh - 7.5rem);
  }
}

.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
  margin-bottom: 1rem;
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
  display: block;
  width: 100%;
  margin-top: 0.5rem;
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
