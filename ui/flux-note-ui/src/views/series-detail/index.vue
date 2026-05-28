<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import {
  fetchSeriesDetail,
  type SeriesCatalogNode,
  type SeriesChapter,
  type SeriesDetail,
} from '../../api/series'
import ArticleView from '../../components/article/index.vue'
import SeriesCatalogTree from './SeriesCatalogTree.vue'
import { useThemeStore } from '../../stores/theme'
import logoLight from '../../assets/logo-light.png'
import logoDark from '../../assets/logo-dark.png'

const route = useRoute()
const router = useRouter()

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

const series = ref<SeriesDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const sidebarCollapsed = ref(false)

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const slug = computed(() => {
  const v = route.params.slug
  return Array.isArray(v) ? v[0] : v
})

const activeArticleSlug = computed(() => {
  const v = route.query.article
  return Array.isArray(v) ? v[0] ?? '' : (v ?? '')
})

const updatedAt = computed(() => series.value?.update_time?.slice(0, 10) ?? '')

const firstPublishedChapter = (
  nodes: SeriesCatalogNode[] | undefined,
): SeriesChapter | null => {
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

const publishedChapters = computed(() =>
  series.value?.chapters?.filter((c) => c.status === 'published') ?? [],
)

const currentChapterIndex = computed(() => {
  if (!activeArticleSlug.value) return -1
  return publishedChapters.value.findIndex(
    (c) => c.slug === activeArticleSlug.value,
  )
})

const prevChapter = computed(() => {
  const idx = currentChapterIndex.value
  return idx > 0 ? publishedChapters.value[idx - 1] : null
})

const nextChapter = computed(() => {
  const idx = currentChapterIndex.value
  if (idx < 0) return null
  return idx + 1 < publishedChapters.value.length
    ? publishedChapters.value[idx + 1]
    : null
})

const progressText = computed(() => {
  const idx = currentChapterIndex.value
  if (idx < 0) return ''
  return `${idx + 1} / ${publishedChapters.value.length}`
})

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
  <p v-if="loading" class="state-block">
    <span class="state-block__spinner" aria-hidden="true" />
    加载中…
  </p>

  <div
    v-else-if="series"
    class="detail-shell"
    :class="{ 'detail-shell--collapsed': sidebarCollapsed }"
  >
    <!-- ── 左侧：品牌 / 系列信息 / 章节目录 ── -->
    <aside class="sidebar" aria-label="系列目录">
      <!-- 折叠/展开按钮（仅 ≥ 980px 时可见） -->
      <button
        type="button"
        class="sidebar-toggle"
        :aria-label="sidebarCollapsed ? '展开目录' : '收起目录'"
        :title="sidebarCollapsed ? '展开目录' : '收起目录'"
        @click="toggleSidebar"
      >
        <Icon
          :icon="
            sidebarCollapsed ? 'lucide:panel-left-open' : 'lucide:panel-left-close'
          "
        />
      </button>

      <!-- 收起态：仅图标条 -->
      <template v-if="sidebarCollapsed">
        <RouterLink to="/" class="sidebar-mini" title="首页">
          <img
            :src="isDark ? logoDark : logoLight"
            alt="FluxLu"
            class="sidebar-mini__logo"
          />
        </RouterLink>
        <RouterLink to="/series" class="sidebar-mini sidebar-mini--icon" title="全部系列">
          <Icon icon="lucide:layers" />
        </RouterLink>
      </template>

      <!-- 展开态：完整内容 -->
      <template v-else>
      <RouterLink to="/" class="brand">
        <img
          :src="isDark ? logoDark : logoLight"
          alt="FluxLu"
          class="brand__logo"
        />
        <div class="brand__text">
          <span class="brand__kicker">Flux Series</span>
          <span class="brand__title">学习路径</span>
        </div>
      </RouterLink>

      <RouterLink to="/series" class="back-link">
        <Icon icon="lucide:arrow-left" />
        全部系列
      </RouterLink>

      <!-- 系列信息卡 -->
      <section class="series-info">
        <p class="series-info__eyebrow">Series</p>
        <h2 class="series-info__title">{{ series.name }}</h2>
        <p v-if="series.description" class="series-info__desc">
          {{ series.description }}
        </p>
        <div class="series-info__meta">
          <span
            class="series-info__badge"
            :class="series.is_finished ? 'series-info__badge--done' : 'series-info__badge--ongoing'"
          >
            <span class="series-info__badge-dot" aria-hidden="true" />
            {{ series.is_finished ? '已完结' : '连载中' }}
          </span>
          <span class="series-info__count">{{ series.article_count }} 篇</span>
          <span v-if="updatedAt" class="series-info__updated">
            <Icon icon="lucide:calendar" />
            {{ updatedAt }}
          </span>
        </div>
        <!-- 阅读进度 -->
        <div
          v-if="publishedChapters.length > 0"
          class="series-info__progress"
        >
          <div class="progress-bar" aria-hidden="true">
            <span
              class="progress-bar__fill"
              :style="{
                width:
                  currentChapterIndex >= 0
                    ? `${((currentChapterIndex + 1) / publishedChapters.length) * 100}%`
                    : '0%',
              }"
            />
          </div>
          <span class="series-info__progress-text">
            {{
              currentChapterIndex >= 0
                ? `第 ${progressText} 章`
                : `共 ${publishedChapters.length} 章`
            }}
          </span>
        </div>
      </section>

      <!-- 目录 -->
      <nav class="catalog" aria-label="章节目录">
        <header class="catalog__header">
          <span>目录</span>
          <span v-if="hasCatalog || series.chapters?.length" class="catalog__count">
            {{ publishedChapters.length }}
          </span>
        </header>

        <SeriesCatalogTree
          v-if="hasCatalog"
          :nodes="series.catalog"
          :active-slug="activeArticleSlug"
          @select="selectChapter"
        />

        <ul v-else-if="series.chapters?.length" class="flat-chapters">
          <li
            v-for="(chapter, idx) in series.chapters"
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
              <span class="flat-chapters__index" aria-hidden="true">
                {{ String(idx + 1).padStart(2, '0') }}
              </span>
              <span class="flat-chapters__title">{{ chapter.title }}</span>
              <span
                v-if="chapter.status !== 'published'"
                class="flat-chapters__badge"
              >整理中</span>
            </button>
          </li>
        </ul>

        <p v-else class="catalog__empty">该系列暂未发布章节</p>
      </nav>
      </template>
    </aside>

    <!-- ── 右侧：文章正文 + 上下篇 ── -->
    <main class="main">
      <div v-if="!activeArticleSlug" class="empty-hint">
        <div class="empty-hint__icon">
          <Icon icon="lucide:book-open" />
        </div>
        <p class="empty-hint__eyebrow">从左侧目录开始</p>
        <h3>选择一篇章节即可开始阅读</h3>
        <p class="empty-hint__desc">
          整个系列的章节都在左侧导航里，点击即可在此处展开正文。
        </p>
      </div>

      <template v-else>
        <ArticleView
          :key="activeArticleSlug"
          :slug="activeArticleSlug"
        />

        <!-- 上下篇 -->
        <nav
          v-if="prevChapter || nextChapter"
          class="chapter-nav"
          aria-label="章节翻页"
        >
          <button
            type="button"
            class="chapter-nav__btn chapter-nav__btn--prev"
            :disabled="!prevChapter"
            @click="prevChapter && selectChapter(prevChapter)"
          >
            <Icon icon="lucide:arrow-left" class="chapter-nav__arrow" />
            <span class="chapter-nav__text">
              <span class="chapter-nav__label">上一章</span>
              <span v-if="prevChapter" class="chapter-nav__title">
                {{ prevChapter.title }}
              </span>
            </span>
          </button>

          <button
            type="button"
            class="chapter-nav__btn chapter-nav__btn--next"
            :disabled="!nextChapter"
            @click="nextChapter && selectChapter(nextChapter)"
          >
            <span class="chapter-nav__text chapter-nav__text--end">
              <span class="chapter-nav__label">下一章</span>
              <span v-if="nextChapter" class="chapter-nav__title">
                {{ nextChapter.title }}
              </span>
            </span>
            <Icon icon="lucide:arrow-right" class="chapter-nav__arrow" />
          </button>
        </nav>
      </template>
    </main>
  </div>

  <div v-else class="series-empty">
    <div class="series-empty__icon">
      <Icon icon="lucide:file-x" />
    </div>
    <p class="series-empty__eyebrow">Series Missing</p>
    <h1>{{ errorMessage || '没有找到这个系列' }}</h1>
    <RouterLink to="/series" class="back-link">
      <Icon icon="lucide:arrow-left" />
      返回系列列表
    </RouterLink>
  </div>
</template>

<style scoped>
/* ── 加载状态 ── */
.state-block {
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 4rem 1.5rem;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-block__spinner {
  display: inline-block;
  width: 0.95rem;
  height: 0.95rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ── 整体两栏布局 ── */
.detail-shell {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: 1fr;
  width: 100%;
  align-items: start;
}

@media (min-width: 980px) {
  .detail-shell {
    grid-template-columns: 220px minmax(0, 1fr);
    transition: grid-template-columns 0.25s ease;
  }

  .detail-shell--collapsed {
    grid-template-columns: 56px minmax(0, 1fr);
  }
}

/* 在窄于 1280px 时隐藏文章组件自带的目录侧栏，避免三栏拥挤 */
@media (max-width: 1279px) {
  .main :deep(.article-layout) {
    grid-template-columns: minmax(0, 1fr) !important;
  }
  .main :deep(.article-toc) {
    display: none !important;
  }
}

/* sidebar 收起时，即便 1024 ~ 1279 也让文章 toc 显示 */
@media (min-width: 1024px) and (max-width: 1279px) {
  .detail-shell--collapsed .main :deep(.article-layout) {
    grid-template-columns: minmax(0, 1fr) 220px !important;
  }
  .detail-shell--collapsed .main :deep(.article-toc) {
    display: block !important;
  }
}

/* ── 左侧 ── */
.sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 980px) {
  .sidebar {
    position: sticky;
    top: var(--space-page-y);
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow-y: auto;
    padding-right: 4px;
    scrollbar-width: thin;
  }
}

.detail-shell--collapsed .sidebar {
  gap: 0.5rem;
  align-items: center;
  padding-right: 0;
}

/* ── 折叠按钮 ── */
.sidebar-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 1.85rem;
  height: 1.85rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  border-radius: 0.5rem;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.sidebar-toggle:hover {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  background: var(--color-bg-soft);
}

.sidebar-toggle :deep(svg) {
  width: 1rem;
  height: 1rem;
}

@media (min-width: 980px) {
  .sidebar-toggle {
    display: inline-flex;
    align-self: flex-end;
    flex-shrink: 0;
  }
  .detail-shell--collapsed .sidebar-toggle {
    align-self: center;
  }
}

/* ── 收起态：仅图标条 ── */
.sidebar-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.6rem;
  text-decoration: none;
  color: inherit;
  transition: background 0.15s, transform 0.15s;
}

.sidebar-mini:hover {
  background: var(--color-bg-soft);
}

.sidebar-mini__logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 0.55rem;
  box-shadow:
    0 4px 12px -4px color-mix(in srgb, var(--color-accent) 35%, transparent),
    0 0 0 1px color-mix(in srgb, var(--color-border) 80%, transparent);
}

.sidebar-mini--icon {
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-muted);
}

.sidebar-mini--icon:hover {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
}

.sidebar-mini--icon :deep(svg) {
  width: 1.05rem;
  height: 1.05rem;
}

/* ── 品牌 ── */
.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.5rem 0.25rem;
  text-decoration: none;
  color: inherit;
  transition: opacity 0.15s;
}

.brand:hover {
  opacity: 0.85;
}

.brand__logo {
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.6rem;
  object-fit: contain;
  flex-shrink: 0;
  box-shadow:
    0 4px 12px -4px color-mix(in srgb, var(--color-accent) 35%, transparent),
    0 0 0 1px color-mix(in srgb, var(--color-border) 80%, transparent);
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand__kicker {
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.brand__title {
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.2;
}

/* ── 返回链接 ── */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  padding: 0.25rem 0.45rem;
  margin-left: 0.15rem;
  border-radius: 0.4rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.15s, background 0.15s, transform 0.15s;
}

.back-link:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-soft);
  transform: translateX(-2px);
}

.back-link :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
}

/* ── 系列信息卡 ── */
.series-info {
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--color-accent) 14%, transparent), transparent 60%),
    var(--color-bg-surface);
  padding: 1rem 1.05rem 1.05rem;
}

.series-info__eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.series-info__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.35;
}

.series-info__desc {
  margin: 0.4rem 0 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.series-info__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.55rem;
  margin-top: 0.7rem;
  font-size: 0.7rem;
  color: var(--color-text-muted);
}

.series-info__badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.66rem;
  font-weight: 700;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

.series-info__badge-dot {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 25%, transparent);
}

.series-info__badge--ongoing {
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
}

.series-info__badge--done {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.12);
}

.series-info__count {
  font-weight: 600;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.series-info__updated {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-variant-numeric: tabular-nums;
}

.series-info__updated :deep(svg) {
  width: 0.78rem;
  height: 0.78rem;
}

/* 进度条 */
.series-info__progress {
  margin-top: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.progress-bar {
  width: 100%;
  height: 4px;
  border-radius: 999px;
  background: var(--color-bg-soft);
  overflow: hidden;
}

.progress-bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 70%, #6366f1));
  transition: width 0.4s ease;
}

.series-info__progress-text {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ── 目录 ── */
.catalog {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.catalog__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0 0.15rem 0.4rem;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.catalog__count {
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  text-transform: none;
}

.catalog__empty {
  margin: 0;
  padding: 0.5rem 0.85rem;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

/* 扁平回退列表 */
.flat-chapters {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
}

.flat-chapters__btn {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0.55rem 0.55rem 0.55rem 0.85rem;
  border-left: 2px solid transparent;
  border-radius: 0 0.4rem 0.4rem 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.flat-chapters__btn:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-left-color: color-mix(in srgb, var(--color-accent) 40%, transparent);
}

.flat-chapters__btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.flat-chapters__item--active .flat-chapters__btn {
  background: color-mix(in srgb, var(--color-accent) 8%, transparent);
  color: var(--color-text-primary);
  border-left-color: var(--color-accent);
}

.flat-chapters__index {
  flex-shrink: 0;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-family-mono, ui-monospace, "SF Mono", Menlo, monospace);
}

.flat-chapters__item--active .flat-chapters__index {
  color: var(--color-accent);
}

.flat-chapters__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flat-chapters__badge {
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
}

/* ── 右侧主区 ── */
.main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* ── 上下篇翻页 ── */
.chapter-nav {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

/* 与文章组件的两列布局对齐：让翻页区只占主体那一列，不延伸到 TOC 下方 */
@media (min-width: 1024px) {
  .chapter-nav {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    width: calc(100% - 220px - 1.5rem);
  }
}

/* 窄屏隐藏文章 TOC 时回到全宽 */
@media (max-width: 1279px) {
  .chapter-nav {
    width: 100%;
  }
}

/* sidebar 收起、TOC 重新出现时，再次扣掉 TOC 宽度 */
@media (min-width: 1024px) and (max-width: 1279px) {
  .detail-shell--collapsed .chapter-nav {
    width: calc(100% - 220px - 1.5rem);
  }
}

@media (max-width: 540px) {
  .chapter-nav {
    grid-template-columns: 1fr;
  }
}

.chapter-nav__btn {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.85rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-bg-surface);
  color: inherit;
  cursor: pointer;
  font: inherit;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.chapter-nav__btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  box-shadow: 0 6px 18px color-mix(in srgb, var(--color-accent) 12%, transparent);
}

.chapter-nav__btn--prev:hover:not(:disabled) {
  transform: translateX(-2px);
}

.chapter-nav__btn--next:hover:not(:disabled) {
  transform: translateX(2px);
}

.chapter-nav__btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.chapter-nav__btn--next {
  text-align: right;
}

.chapter-nav__arrow {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
  color: var(--color-text-muted);
  transition: color 0.15s;
}

.chapter-nav__btn:hover:not(:disabled) .chapter-nav__arrow {
  color: var(--color-accent);
}

.chapter-nav__text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
  flex: 1;
}

.chapter-nav__text--end {
  align-items: flex-end;
}

.chapter-nav__label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.chapter-nav__title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.chapter-nav__btn:hover:not(:disabled) .chapter-nav__title {
  color: var(--color-accent-text);
}

/* ── 空态 ── */
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  border: 1px dashed var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  padding: 3rem 2rem;
  color: var(--color-text-secondary);
}

.empty-hint__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
  margin-bottom: 1rem;
}

.empty-hint__icon :deep(svg) {
  width: 1.6rem;
  height: 1.6rem;
}

.empty-hint__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.empty-hint h3 {
  margin: 0 0 0.5rem;
  color: var(--color-text-primary);
  font-size: 1.15rem;
  font-weight: 700;
}

.empty-hint__desc {
  margin: 0;
  font-size: 0.85rem;
  max-width: 28rem;
  line-height: 1.7;
}

/* ── 系列不存在 ── */
.series-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 480px;
  margin: 4rem auto;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-bg-surface);
  padding: 2.5rem 2rem;
}

.series-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: var(--color-bg-soft);
  color: var(--color-text-muted);
  margin-bottom: 1rem;
}

.series-empty__icon :deep(svg) {
  width: 1.6rem;
  height: 1.6rem;
}

.series-empty__eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.series-empty h1 {
  margin: 0 0 1.25rem;
  color: var(--color-text-primary);
  font-size: 1.4rem;
  font-weight: 700;
}

.series-empty .back-link {
  margin: 0 auto;
  padding: 0.5rem 1rem;
  border: 1px solid var(--color-border);
}

@media (prefers-reduced-motion: reduce) {
  .back-link,
  .chapter-nav__btn,
  .progress-bar__fill {
    transition: none;
  }
  .back-link:hover,
  .chapter-nav__btn:hover:not(:disabled) {
    transform: none;
  }
}
</style>
