<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

const route = useRoute()
const router = useRouter()

const series = ref<SeriesDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const slug = computed(() => {
  const v = route.params.slug
  return Array.isArray(v) ? v[0] : v
})

const activeArticleSlug = computed(() => {
  const v = route.query.article
  return Array.isArray(v) ? v[0] ?? '' : (v ?? '')
})

const updatedAt = computed(() => series.value?.updateTime?.slice(0, 10) ?? '')

// ── 移动端目录面板：默认收起，选中章节后自动收起 ──
const mobileNavOpen = ref(false)
const catalogRef = ref<HTMLElement | null>(null)

// 选中章节后把激活项滚动到目录可视区
const scrollActiveIntoView = () => {
  const el = catalogRef.value?.querySelector(
    '.catalog-post--active, .flat-chapters__item--active',
  )
  el?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}

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

const hasCatalog = computed(() => (series.value?.catalog?.length ?? 0) > 0)

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
  mobileNavOpen.value = false
  router.replace({
    name: 'series-detail',
    params: { slug: slug.value },
    query: { article: chapter.slug },
  })
}

// 桌面/任意带键盘设备：← / → 翻章（输入框聚焦或带修饰键时不拦截）
const handleKeydown = (e: KeyboardEvent) => {
  if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.altKey) return
  const t = e.target as HTMLElement | null
  if (
    t &&
    (t.isContentEditable || /^(?:INPUT|TEXTAREA|SELECT)$/.test(t.tagName))
  ) {
    return
  }
  if (e.key === 'ArrowLeft' && prevChapter.value) {
    e.preventDefault()
    selectChapter(prevChapter.value)
  } else if (e.key === 'ArrowRight' && nextChapter.value) {
    e.preventDefault()
    selectChapter(nextChapter.value)
  }
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

// 切换章节：滚动激活项进入可视区；无激活章节时在移动端展开目录
watch(activeArticleSlug, async (val) => {
  if (!val) {
    mobileNavOpen.value = true
    return
  }
  await nextTick()
  scrollActiveIntoView()
})

onMounted(() => {
  load(slug.value)
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', handleKeydown)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', handleKeydown)
  }
})
</script>

<template>
  <!-- ── 加载态：目录 + 正文骨架屏 ── -->
  <div v-if="loading" class="detail-page detail-page--loading" aria-busy="true">
    <span class="sr-only">加载中…</span>
    <span class="sk sk--breadcrumb" aria-hidden="true" />
    <div class="detail-body" aria-hidden="true">
      <div class="sk sk--block sk--catalog" />
      <div class="sk sk--block sk--reading" />
    </div>
  </div>

  <div v-else-if="series" class="detail-page">
    <!-- 面包屑返回（从信息卡抽离，让卡片只承载系列信息） -->
    <RouterLink to="/series" class="back-link back-link--top">
      <Icon icon="lucide:arrow-left" />
      全部系列
    </RouterLink>

    <!-- ── 目录 + 正文 ── -->
    <div class="detail-body">
      <aside ref="catalogRef" class="catalog-pane" aria-label="系列目录">
        <!-- 移动端目录开关（仅 < 980px 可见） -->
        <button
          type="button"
          class="mobile-nav-toggle"
          :aria-expanded="mobileNavOpen"
          @click="mobileNavOpen = !mobileNavOpen"
        >
          <Icon icon="lucide:list-tree" class="mobile-nav-toggle__lead" />
          <span class="mobile-nav-toggle__name">目录</span>
          <span class="mobile-nav-toggle__count">{{ publishedChapters.length }} 篇</span>
          <Icon
            :icon="mobileNavOpen ? 'lucide:chevron-up' : 'lucide:chevron-down'"
            class="mobile-nav-toggle__chev"
          />
        </button>

        <div
          class="catalog-collapse"
          :class="{ 'catalog-collapse--open': mobileNavOpen }"
        >
          <nav class="catalog" aria-label="章节目录">
            <RouterLink to="/series" class="catalog__back">
              <Icon icon="lucide:arrow-left" />
              全部系列
            </RouterLink>

            <!-- 系列名称（替代原顶部信息卡） -->
            <h1 class="catalog__series-name">{{ series.name }}</h1>

            <!-- 系列元信息：状态 / 篇数 / 更新时间（从信息卡移入固定头部） -->
            <div class="catalog__meta">
              <span
                class="stat-chip"
                :class="series.isFinished ? 'stat-chip--done' : 'stat-chip--ongoing'"
              >
                <span class="stat-chip__dot" aria-hidden="true" />
                {{ series.isFinished ? '已完结' : '连载中' }}
              </span>
              <span class="stat-chip">
                <Icon icon="lucide:book-marked" />
                {{ series.articleCount }} 篇
              </span>
              <span v-if="updatedAt" class="stat-chip">
                <Icon icon="lucide:calendar" />
                更新于 {{ updatedAt }}
              </span>
            </div>

            <!-- 阅读进度：常驻 sticky 目录卡，正文滚动时仍可见 -->
            <div
              v-if="publishedChapters.length > 0"
              class="catalog__progress"
            >
              <div class="catalog__progress-head">
                <span class="catalog__progress-label">阅读进度</span>
                <span class="catalog__progress-text">
                  {{
                    currentChapterIndex >= 0
                      ? `第 ${progressText} 章`
                      : `共 ${publishedChapters.length} 章`
                  }}
                </span>
              </div>
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
            </div>

            <header class="catalog__header">
              <span class="catalog__heading">
                <Icon icon="lucide:list-tree" />
                目录
              </span>
              <span
                v-if="hasCatalog || series.chapters?.length"
                class="catalog__count"
              >
                {{ publishedChapters.length }}
              </span>
            </header>

            <!-- 章节列表：仅此区域内部滚动，不影响上方返回/进度 -->
            <div class="catalog__scroll">
              <SeriesCatalogTree
                v-if="hasCatalog"
                :nodes="series.catalog"
                :active-slug="activeArticleSlug"
                @select="selectChapter"
              />

              <ul v-else-if="series.chapters?.length" class="flat-chapters">
                <li
                  v-for="(chapter, idx) in series.chapters"
                  :key="chapter.postId"
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
            </div>
          </nav>
        </div>
      </aside>

      <!-- ── 右侧：文章正文 + 上下篇 ── -->
      <main class="reading">
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
          <ArticleView :key="activeArticleSlug" :slug="activeArticleSlug" />

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
/* ── 无障碍：屏幕阅读器专用文本 ── */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* ── 页面整体 ── */
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  width: 100%;
  min-width: 0;
}

/* ── 面包屑返回 ── */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  padding: 0.32rem 0.7rem 0.32rem 0.55rem;
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.15s, background 0.15s, transform 0.15s;
}

.back-link--top {
  margin-bottom: -0.35rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

/* 桌面端改由 sticky 目录卡里的返回入口承担，避免重复 */
@media (min-width: 980px) {
  .back-link--top {
    display: none;
  }
}

.back-link:hover {
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
  transform: translateX(-2px);
}

.back-link :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
}

/* ── 侧栏：系列名称（替代原顶部信息卡） ── */
.catalog__series-name {
  margin: 0.1rem 0.1rem 0;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  line-height: 1.3;
  color: var(--color-text-primary);
}

/* 统计 chips（移至侧栏目录卡固定头部） */
.stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.28rem 0.7rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.stat-chip :deep(svg) {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--color-text-muted);
}

.stat-chip__dot {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 22%, transparent);
}

.stat-chip--ongoing {
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.stat-chip--done {
  color: #15803d;
  background: rgba(22, 163, 74, 0.12);
}

/* 进度条（复用于目录卡） */
.progress-bar {
  width: 100%;
  height: 5px;
  border-radius: 999px;
  background: var(--color-bg-soft);
  overflow: hidden;
}

.progress-bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 65%, #6366f1));
  transition: width 0.4s ease;
}

/* ── 目录 + 正文 ── */
.detail-body {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  width: 100%;
  align-items: start;
}

@media (min-width: 980px) {
  .detail-body {
    grid-template-columns: 268px minmax(0, 1fr);
  }
}

/* 在窄于 1280px 时隐藏文章组件自带的目录侧栏，避免三栏拥挤 */
@media (max-width: 1279px) {
  .reading :deep(.article-layout) {
    grid-template-columns: minmax(0, 1fr) !important;
  }
  .reading :deep(.article-toc) {
    display: none !important;
  }
}

/* ── 目录侧栏 ── */
.catalog-pane {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

@media (min-width: 980px) {
  .catalog-pane {
    position: sticky;
    top: var(--space-page-y);
  }

  /* 卡片限高不超过视口；内部头部固定、仅列表区滚动 */
  .catalog {
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow: hidden;
  }

  .catalog__scroll {
    overflow-y: auto;
    /* 细滚动条 */
    scrollbar-width: thin;
    scrollbar-color: var(--color-border) transparent;
  }

  .catalog__scroll::-webkit-scrollbar {
    width: 6px;
  }

  .catalog__scroll::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: var(--color-border);
  }

  .catalog__scroll::-webkit-scrollbar-thumb:hover {
    background: var(--color-text-muted);
  }
}

/* 桌面：折叠包裹层退出布局；移动端才作为真实盒子做 max-height 折叠 */
.catalog-collapse {
  display: contents;
}

/* ── 移动端目录开关（仅 < 980px） ── */
.mobile-nav-toggle {
  display: none;
}

@media (max-width: 979px) {
  .catalog-pane {
    gap: 0;
  }

  .mobile-nav-toggle {
    display: flex;
    align-items: center;
    gap: 0.55rem;
    width: 100%;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    background: var(--color-bg-surface);
    box-shadow: var(--shadow-sm);
    color: var(--color-text-primary);
    font: inherit;
    cursor: pointer;
    transition: border-color 0.15s, background 0.15s;
  }

  .mobile-nav-toggle:hover {
    border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
  }

  .mobile-nav-toggle__lead {
    width: 1.05rem;
    height: 1.05rem;
    color: var(--color-accent);
    flex-shrink: 0;
  }

  .mobile-nav-toggle__name {
    flex: 1;
    min-width: 0;
    font-size: 0.9rem;
    font-weight: 700;
    text-align: left;
  }

  .mobile-nav-toggle__count {
    flex-shrink: 0;
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--color-text-muted);
    font-variant-numeric: tabular-nums;
  }

  .mobile-nav-toggle__chev {
    width: 1rem;
    height: 1rem;
    color: var(--color-text-muted);
    flex-shrink: 0;
  }

  /* 目录默认收起，max-height 过渡——内容保持自然高度，不会压扁 */
  .catalog-collapse {
    display: block;
    width: 100%;
    overflow: hidden;
    max-height: 0;
    transition: max-height 0.3s ease, margin-top 0.3s ease;
  }

  .catalog-collapse--open {
    max-height: 2400px;
    margin-top: 0.85rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .catalog-collapse {
    transition: none;
  }
}

/* ── 目录卡片 ── */
.catalog {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  padding: 0.85rem 0.7rem;
}

/* 固定头部区：不随列表滚动、不被压缩 */
.catalog__back,
.catalog__series-name,
.catalog__meta,
.catalog__progress,
.catalog__header {
  flex-shrink: 0;
}

/* 仅章节列表区参与内部滚动 */
.catalog__scroll {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

/* 系列元信息行（移自信息卡） */
.catalog__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0 0.2rem 0.2rem;
}

.catalog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.1rem 0.45rem 0.55rem;
  margin-bottom: 0.15rem;
  border-bottom: 1px solid var(--color-border);
}

.catalog__heading {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.catalog__heading :deep(svg) {
  width: 0.9rem;
  height: 0.9rem;
  color: var(--color-accent);
}

.catalog__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.35rem;
  height: 1.35rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.catalog__empty {
  margin: 0;
  padding: 0.6rem 0.85rem;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

/* 目录卡内的返回入口（桌面 sticky 常驻） */
.catalog__back {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  margin: 0 0.1rem 0.1rem;
  padding: 0.28rem 0.6rem 0.28rem 0.45rem;
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.15s, background 0.15s, transform 0.15s;
}

.catalog__back:hover {
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
  transform: translateX(-2px);
}

.catalog__back :deep(svg) {
  width: 0.85rem;
  height: 0.85rem;
}

/* 阅读进度（常驻目录卡） */
.catalog__progress {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.35rem 0.45rem 0.2rem;
}

.catalog__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.catalog__progress-label {
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.catalog__progress-text {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-accent-text);
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .catalog__back {
    transition: none;
  }
  .catalog__back:hover {
    transform: none;
  }
}

/* 扁平回退列表 */
.flat-chapters {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.flat-chapters__btn {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0.5rem 0.6rem;
  border-left: 2px solid transparent;
  border-radius: 0.5rem;
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
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-left-color: var(--color-accent);
}

.flat-chapters__index {
  flex-shrink: 0;
  font-size: 0.7rem;
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

/* ── 右侧阅读区 ── */
.reading {
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
@media (min-width: 1280px) {
  .chapter-nav {
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
  padding: 0.9rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  color: inherit;
  cursor: pointer;
  font: inherit;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.chapter-nav__btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  box-shadow: var(--shadow-md);
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
  border-radius: var(--radius-xl);
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
  background: var(--color-accent-soft);
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
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
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
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

/* ── 骨架屏 ── */
@keyframes sk-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.sk {
  display: block;
  border-radius: 0.5rem;
  background: var(--color-bg-soft);
  animation: sk-pulse 1.4s ease-in-out infinite;
}

.sk--breadcrumb {
  width: 6rem;
  height: 1.7rem;
  border-radius: 999px;
}

.sk--block {
  border-radius: var(--radius-lg);
}

.sk--catalog {
  min-height: 18rem;
}

.sk--reading {
  min-height: 22rem;
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
  .sk {
    animation: none;
  }
}

/* Atlas reading canvas: the series catalog stays quiet while the article owns the page. */
.detail-page {
  --atlas-navy: #102a43;
  --atlas-paper: #f5f7f2;
  --atlas-paper-2: #e9efe7;
  --atlas-ink: #17252c;
  --atlas-muted: #60717a;
  --atlas-line: #c8d4cf;
  --atlas-yellow: #f2c94c;
  --atlas-green: #2f855a;
  --atlas-coral: #cc674e;
  width: 100%;
  min-height: calc(100vh - 4.6rem);
  margin: 0;
  padding: 3.5rem max(1.5rem, calc((100vw - 92rem) / 2)) 6rem;
  background: var(--atlas-paper);
  color: var(--atlas-ink);
  font-family: var(--font-sans), sans-serif;
}

.detail-body {
  grid-template-columns: 16rem minmax(0, 1fr);
  gap: clamp(2.5rem, 5vw, 5rem);
  width: min(92rem, 100%);
  margin: 0 auto;
}

.catalog-pane {
  position: sticky;
  top: 5.75rem;
  align-self: start;
}

.catalog {
  gap: 0.55rem;
  max-height: calc(100vh - 7.5rem);
  border: 0;
  border-top: 1px solid var(--atlas-line);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding: 1rem 0 0;
}

.catalog__back {
  margin: 0 0 0.5rem;
  border-radius: 0;
  padding: 0.25rem 0;
  color: var(--atlas-green);
  font-size: 0.72rem;
}

.catalog__back:hover {
  background: transparent;
  color: var(--atlas-navy);
  transform: translateX(-2px);
}

.catalog__series-name {
  margin: 0.2rem 0 0.35rem;
  color: var(--atlas-ink);
  font-size: 1.45rem;
  line-height: 1.2;
}

.catalog__meta {
  gap: 0.3rem 0.75rem;
  border-bottom: 1px solid var(--atlas-line);
  padding: 0 0 0.9rem;
}

.stat-chip {
  border-radius: 0;
  border-bottom: 1px solid var(--atlas-line);
  padding: 0.22rem 0;
  color: var(--atlas-muted);
  background: transparent;
  font-size: 0.68rem;
}

.stat-chip--ongoing,
.stat-chip--done {
  color: var(--atlas-green);
  background: transparent;
}

.stat-chip__dot {
  width: 0.35rem;
  height: 0.35rem;
  box-shadow: none;
}

.catalog__progress {
  gap: 0.5rem;
  border-bottom: 1px solid var(--atlas-line);
  padding: 0.9rem 0;
}

.catalog__progress-label,
.catalog__progress-text {
  color: var(--atlas-muted);
  font-size: 0.66rem;
}

.catalog__progress-text {
  color: var(--atlas-green);
}

.progress-bar {
  height: 2px;
  border-radius: 0;
  background: var(--atlas-line);
}

.progress-bar__fill {
  border-radius: 0;
  background: var(--atlas-green);
}

.catalog__header {
  margin: 0.25rem 0 0;
  border-bottom: 1px solid var(--atlas-line);
  padding: 0.65rem 0;
}

.catalog__heading {
  color: var(--atlas-ink);
  font-size: 0.68rem;
}

.catalog__heading :deep(svg) {
  color: var(--atlas-green);
}

.catalog__count {
  min-width: auto;
  height: auto;
  border-radius: 0;
  padding: 0;
  color: var(--atlas-muted);
  background: transparent;
  font-size: 0.66rem;
}

.catalog__scroll {
  scrollbar-color: var(--atlas-line) transparent;
}

.catalog__scroll::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--atlas-line);
}

.catalog__empty {
  padding: 0.6rem 0;
  color: var(--atlas-muted);
}

.catalog-pane :deep(.catalog-tree:not(.catalog-tree--root)) {
  margin-left: 0.55rem;
  border-left: 1px dashed var(--atlas-line);
}

.catalog-pane :deep(.catalog-node__title) {
  border-radius: 0;
  padding: 0.4rem 0.2rem;
  color: var(--atlas-muted);
  font-size: 0.66rem;
}

.catalog-pane :deep(.catalog-node__title--link:hover) {
  color: var(--atlas-green);
  background: transparent;
}

.catalog-pane :deep(.catalog-node__chevron) {
  color: var(--atlas-green);
}

.catalog-pane :deep(.catalog-posts) {
  padding-left: 0.35rem;
}

.catalog-pane :deep(.catalog-post__btn),
.flat-chapters__btn {
  border-left-color: transparent;
  border-radius: 0;
  padding: 0.42rem 0.35rem;
  color: var(--atlas-muted);
  background: transparent;
  font-size: 0.72rem;
}

.catalog-pane :deep(.catalog-post__btn:hover:not(:disabled)),
.flat-chapters__btn:hover:not(:disabled) {
  border-left-color: var(--atlas-green);
  color: var(--atlas-ink);
  background: var(--atlas-paper-2);
}

.catalog-pane :deep(.catalog-post--active .catalog-post__btn),
.flat-chapters__item--active .flat-chapters__btn {
  border-left-color: var(--atlas-green);
  color: var(--atlas-ink);
  background: var(--atlas-paper-2);
}

.catalog-pane :deep(.catalog-post__dot) {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  color: var(--atlas-green);
}

.catalog-pane :deep(.catalog-post__badge),
.flat-chapters__badge {
  border: 0;
  border-radius: 0;
  padding: 0;
  color: var(--atlas-muted);
  background: transparent;
}

.reading {
  min-width: 0;
  gap: 2rem;
}

.reading :deep(.article-reader--embedded) {
  --reader-paper: var(--atlas-paper);
  --reader-paper-soft: var(--atlas-paper-2);
  --reader-ink: var(--atlas-ink);
  --reader-muted: var(--atlas-muted);
  --reader-line: var(--atlas-line);
  --reader-accent: var(--atlas-coral);
  --reader-link: var(--atlas-green);
  min-height: 0;
  padding: 0;
  color: var(--atlas-ink);
  background: transparent;
}

.reading :deep(.article-reader--embedded .reader-layout) {
  display: block;
  width: 100%;
  margin: 0;
}

.reading :deep(.article-reader--embedded .reader-main) {
  width: 100%;
  max-width: 78ch;
  margin: 0;
}

.reading :deep(.article-reader--embedded .article-toc--embedded) {
  display: none;
}

.reading :deep(.article-reader--embedded .article-header) {
  border-top: 2px solid var(--atlas-navy);
  padding-top: 1.5rem;
}

.reading :deep(.article-reader--embedded .article-eyebrow) {
  color: var(--atlas-green);
}

.reading :deep(.article-reader--embedded .article-title) {
  max-width: 22ch;
  color: var(--atlas-ink);
  font-family: var(--font-sans), sans-serif;
  font-size: clamp(2.2rem, 4vw, 4rem);
  letter-spacing: -0.02em;
  line-height: 1.08;
}

.reading :deep(.article-reader--embedded .article-lede),
.reading :deep(.article-reader--embedded .article-meta) {
  color: var(--atlas-muted);
}

.reading :deep(.article-reader--embedded .article-body) {
  margin-top: 2.5rem;
  color: var(--atlas-ink);
}

.reading :deep(.article-reader--embedded .article-body .md-editor-preview) {
  color: var(--atlas-ink);
}

.reading :deep(.article-reader--embedded .article-body .md-editor-preview h1),
.reading :deep(.article-reader--embedded .article-body .md-editor-preview h2),
.reading :deep(.article-reader--embedded .article-body .md-editor-preview h3),
.reading :deep(.article-reader--embedded .article-body .md-editor-preview h4) {
  color: var(--atlas-ink);
}

.reading :deep(.article-reader--embedded .article-body .md-editor-preview a) {
  color: var(--atlas-green);
}

.reading :deep(.article-reader--embedded .article-body .md-editor-preview blockquote) {
  border-left-color: var(--atlas-coral);
  background: transparent;
}

.chapter-nav {
  width: min(78ch, 100%);
  gap: 1.5rem;
  border-top: 1px solid var(--atlas-line);
  padding-top: 1rem;
}

.chapter-nav__btn {
  border: 0;
  border-bottom: 1px solid var(--atlas-line);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding: 0.75rem 0;
}

.chapter-nav__btn:hover:not(:disabled) {
  border-color: var(--atlas-green);
  background: transparent;
  box-shadow: none;
}

.chapter-nav__arrow,
.chapter-nav__label {
  color: var(--atlas-green);
}

.chapter-nav__title {
  color: var(--atlas-ink);
}

.empty-hint {
  border: 1px dashed var(--atlas-line);
  border-radius: 0;
  background: transparent;
  padding: 4rem 2rem;
  color: var(--atlas-muted);
}

.empty-hint__icon {
  border-radius: 0;
  background: var(--atlas-yellow);
  color: var(--atlas-navy);
}

.empty-hint__eyebrow {
  color: var(--atlas-green);
}

.empty-hint h3 {
  color: var(--atlas-ink);
}

.series-empty {
  max-width: 34rem;
  margin: 5rem auto;
  border: 1px dashed var(--atlas-line);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.series-empty__icon {
  border-radius: 0;
  background: var(--atlas-yellow);
  color: var(--atlas-navy);
}

.series-empty__eyebrow {
  color: var(--atlas-coral);
}

.series-empty h1 {
  color: var(--atlas-ink);
}

:global(html[data-theme='dark'] .detail-page),
:global(html[data-theme='ocean'] .detail-page) {
  --atlas-paper: #12232b;
  --atlas-paper-2: #1b3439;
  --atlas-ink: #e4eee9;
  --atlas-muted: #a9bec1;
  --atlas-line: #385158;
}

@media (max-width: 979px) {
  .detail-page {
    padding: 2.25rem 1rem 4rem;
  }

  .detail-body {
    display: block;
    width: 100%;
  }

  .catalog-pane {
    position: static;
    margin-bottom: 2rem;
  }

  .mobile-nav-toggle {
    border: 1px solid var(--atlas-line);
    border-radius: 0;
    background: transparent;
    box-shadow: none;
    color: var(--atlas-ink);
  }

  .mobile-nav-toggle__lead,
  .mobile-nav-toggle__chev {
    color: var(--atlas-green);
  }

  .catalog-collapse--open {
    margin-top: 0.75rem;
  }

  .catalog {
    max-height: none;
    border-bottom: 1px solid var(--atlas-line);
  }

  .reading :deep(.article-reader--embedded .reader-main) {
    max-width: none;
  }

  .reading :deep(.article-reader--embedded .article-title) {
    max-width: none;
    font-size: 2.35rem;
  }
}

@media (max-width: 540px) {
  .chapter-nav {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mobile-nav-toggle,
  .catalog__back,
  .chapter-nav__btn,
  .progress-bar__fill {
    transition: none;
  }
}
</style>
