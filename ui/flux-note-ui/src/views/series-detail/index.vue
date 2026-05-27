<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  fetchSeriesDetail,
  type SeriesCatalogNode,
  type SeriesChapter,
  type SeriesDetail,
} from '../../api/series'
import ArticleView from '../../components/article/index.vue'
import SeriesCatalogTree from './SeriesCatalogTree.vue'

const route = useRoute()
const router = useRouter()

const series = ref<SeriesDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const slug = computed(() => {
  const v = route.params.slug
  return Array.isArray(v) ? v[0] : v
})

/** 当前选中的文章 slug，由 ?article=xxx 控制 */
const activeArticleSlug = computed(() => {
  const v = route.query.article
  return Array.isArray(v) ? v[0] ?? '' : (v ?? '')
})

const updatedAt = computed(() => series.value?.update_time?.slice(0, 10) ?? '')

/** 把目录树打平，按顺序拿出第一篇已发布的文章 */
const firstPublishedChapter = (nodes: SeriesCatalogNode[] | undefined): SeriesChapter | null => {
  if (!nodes?.length) return null
  for (const node of nodes) {
    const hit = node.posts?.find((p) => p.status === 'published')
    if (hit) return hit
    const fromChild = firstPublishedChapter(node.children)
    if (fromChild) return fromChild
  }
  return null
}

const fallbackFirstChapter = computed(() => {
  if (!series.value) return null
  return (
    firstPublishedChapter(series.value.catalog) ??
    series.value.chapters.find((c) => c.status === 'published') ??
    null
  )
})

const hasCatalog = computed(
  () => (series.value?.catalog?.length ?? 0) > 0,
)

const selectChapter = (chapter: SeriesChapter) => {
  if (chapter.status !== 'published') return
  router.replace({
    name: 'series-detail',
    params: { slug: slug.value },
    query: { article: chapter.slug },
  })
}

const load = async (raw: string | undefined) => {
  if (!raw) {
    series.value = null
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    series.value = await fetchSeriesDetail(raw)
    // 没指定文章时，默认打开第一篇已发布
    if (!activeArticleSlug.value && fallbackFirstChapter.value) {
      router.replace({
        name: 'series-detail',
        params: { slug: raw },
        query: { article: fallbackFirstChapter.value.slug },
      })
    }
  } catch (err) {
    series.value = null
    errorMessage.value = (err as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

watch(slug, (val) => load(val))
onMounted(() => load(slug.value))
</script>

<template>
  <p v-if="loading" class="series-state">加载中 ···</p>

  <div v-else-if="series" class="series-detail-shell">
    <!-- 左侧：当前系列的目录 + 文章 -->
    <aside class="series-sidebar" aria-label="系列目录">
      <RouterLink to="/series" class="back-link">
        <span aria-hidden="true">←</span>
        全部系列
      </RouterLink>

      <div class="series-card">
        <p class="series-card__eyebrow">Series</p>
        <h2 class="series-card__title">{{ series.name }}</h2>
        <p v-if="series.description" class="series-card__desc">
          {{ series.description }}
        </p>
        <div class="series-card__meta">
          <span :class="['badge', series.is_finished ? 'badge--done' : 'badge--ongoing']">
            {{ series.is_finished ? '已完结' : '连载中' }}
          </span>
          <span>{{ series.article_count }} 篇</span>
          <span v-if="updatedAt">更新 {{ updatedAt }}</span>
        </div>
      </div>

      <div class="catalog-wrap">
        <p class="catalog-wrap__title">目录</p>

        <SeriesCatalogTree
          v-if="hasCatalog"
          :nodes="series.catalog"
          :active-slug="activeArticleSlug"
          @select="selectChapter"
        />

        <!-- 没有 catalog 时，回退展示扁平 chapters -->
        <ul v-else-if="series.chapters?.length" class="flat-chapters">
          <li
            v-for="chapter in series.chapters"
            :key="chapter.post_id"
            :class="{
              'flat-chapters__item--active': activeArticleSlug === chapter.slug,
              'flat-chapters__item--draft': chapter.status !== 'published',
            }"
            class="flat-chapters__item"
          >
            <button
              type="button"
              class="flat-chapters__btn"
              :disabled="chapter.status !== 'published'"
              @click="selectChapter(chapter)"
            >
              <span class="flat-chapters__title">{{ chapter.title }}</span>
              <span
                v-if="chapter.status !== 'published'"
                class="flat-chapters__badge"
              >整理中</span>
            </button>
          </li>
        </ul>

        <p v-else class="series-state series-state--inline">该系列暂未发布章节</p>
      </div>
    </aside>

    <!-- 右侧：文章正文 -->
    <main class="series-main">
      <div v-if="!activeArticleSlug" class="empty-hint">
        <p class="empty-hint__eyebrow">从左侧目录开始</p>
        <h3>选择一篇章节即可开始阅读</h3>
        <p>整个系列的章节都在左侧导航里，点击即可在此处展开正文。</p>
      </div>

      <ArticleView
        v-else
        :key="activeArticleSlug"
        :slug="activeArticleSlug"
        hide-toc
      />
    </main>
  </div>

  <div v-else class="series-empty">
    <p class="series-empty__eyebrow">Series Missing</p>
    <h1>{{ errorMessage || '没有找到这个系列' }}</h1>
    <RouterLink to="/series" class="back-link">返回系列列表</RouterLink>
  </div>
</template>

<style scoped>
.series-state {
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  padding: 3rem 0;
}

.series-state--inline {
  text-align: left;
  padding: 0.5rem 0.5rem 0.25rem;
}

/* ── 整体两栏布局 ── */
.series-detail-shell {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: 1fr;
  width: 100%;
  align-items: start;
}

@media (min-width: 980px) {
  .series-detail-shell {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

/* ── 左侧 ── */
.series-sidebar {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

@media (min-width: 980px) {
  .series-sidebar {
    position: sticky;
    top: var(--space-page-y);
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow-y: auto;
  }
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
  transition: color 0.15s ease, transform 0.15s ease;
}

.back-link:hover {
  color: var(--color-accent-text);
  transform: translateX(-2px);
}

.series-card {
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background:
    linear-gradient(160deg, color-mix(in srgb, var(--color-accent) 10%, transparent), transparent 65%),
    var(--color-bg-surface);
  padding: 1rem;
  box-shadow: var(--shadow-sm);
}

.series-card__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.series-card__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.3;
}

.series-card__desc {
  margin: 0.5rem 0 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.series-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.7rem;
  font-size: 0.74rem;
  color: var(--color-text-muted);
}

.badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.badge--done {
  color: #16a34a;
  border-color: rgba(22, 163, 74, 0.4);
  background: rgba(22, 163, 74, 0.08);
}

.badge--ongoing {
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  background: var(--color-accent-soft);
}

.catalog-wrap {
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: color-mix(in srgb, var(--color-bg-surface) 82%, transparent);
  padding: 0.55rem;
  box-shadow: var(--shadow-sm);
}

.catalog-wrap__title {
  margin: 0.25rem 0.6rem 0.5rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

/* 扁平回退列表 */
.flat-chapters {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.flat-chapters__btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0.5rem 0.6rem;
  border-radius: 0.55rem;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.flat-chapters__btn:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.flat-chapters__btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.flat-chapters__item--active .flat-chapters__btn {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.flat-chapters__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flat-chapters__badge {
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
}

/* ── 右侧 ── */
.series-main {
  min-width: 0;
}

.empty-hint {
  border: 1px dashed var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  padding: 2.5rem 2rem;
  text-align: center;
  color: var(--color-text-secondary);
}

.empty-hint__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.empty-hint h3 {
  margin: 0 0 0.5rem;
  color: var(--color-text-primary);
  font-size: 1.1rem;
}

.empty-hint p {
  margin: 0;
  font-size: 0.85rem;
}

/* ── 空态 ── */
.series-empty {
  max-width: 560px;
  margin: var(--space-page-y) auto;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  padding: 2rem;
  text-align: center;
}

.series-empty__eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.series-empty h1 {
  margin: 0 0 1rem;
  color: var(--color-text-primary);
  font-size: 1.5rem;
}

.series-empty .back-link {
  margin: 0 auto;
}
</style>
