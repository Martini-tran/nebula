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

const updatedAt = computed(() => series.value?.update_time?.slice(0, 10) ?? '')

const initialLetter = (name: string) => {
  if (!name) return ''
  return Array.from(name)[0] ?? ''
}

// ── 系列介绍：默认收起，仅在文字溢出时显示「展开」 ──
const descRef = ref<HTMLParagraphElement | null>(null)
const descExpanded = ref(false)
const descOverflow = ref(false)

const measureDesc = () => {
  const el = descRef.value
  // 仅在收起态测量真实溢出
  descOverflow.value =
    !descExpanded.value && !!el && el.scrollHeight - el.clientHeight > 2
}

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

// 数据变化后重置介绍展开态并重新测量是否溢出
watch(
  () => series.value?.description,
  async () => {
    descExpanded.value = false
    await nextTick()
    measureDesc()
  },
)

onMounted(() => {
  load(slug.value)
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', measureDesc)
    window.addEventListener('keydown', handleKeydown)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', measureDesc)
    window.removeEventListener('keydown', handleKeydown)
  }
})
</script>

<template>
  <p v-if="loading" class="state-block">
    <span class="state-block__spinner" aria-hidden="true" />
    加载中…
  </p>

  <div v-else-if="series" class="detail-page">
    <!-- ── 顶部：系列头卡（= 列表页横向卡放大版） ── -->
    <section class="series-hero">
      <div
        class="hero__cover"
        :class="{ 'hero__cover--image': !!series.cover_url }"
        aria-hidden="true"
      >
        <img v-if="series.cover_url" :src="series.cover_url" alt="cover" />
        <span v-else class="hero__cover-letter">
          {{ initialLetter(series.name) }}
        </span>
      </div>

      <div class="hero__body">
        <p class="series-info__eyebrow">Series</p>
        <h1 class="hero__title">{{ series.name }}</h1>

        <div v-if="series.description" class="series-info__desc-wrap">
          <p
            ref="descRef"
            class="series-info__desc"
            :class="{ 'series-info__desc--expanded': descExpanded }"
          >
            {{ series.description }}
          </p>
          <button
            v-if="descOverflow || descExpanded"
            type="button"
            class="series-info__desc-toggle"
            @click="descExpanded = !descExpanded"
          >
            {{ descExpanded ? '收起' : '展开' }}
          </button>
        </div>

        <div class="hero__meta">
          <span
            class="series-info__badge"
            :class="series.is_finished ? 'series-info__badge--done' : 'series-info__badge--ongoing'"
          >
            <span class="series-info__badge-dot" aria-hidden="true" />
            {{ series.is_finished ? '已完结' : '连载中' }}
          </span>
          <span class="hero__count">{{ series.article_count }} 篇</span>
          <span v-if="updatedAt" class="hero__updated">
            <Icon icon="lucide:calendar" />
            更新于 {{ updatedAt }}
          </span>
        </div>

        <!-- 阅读进度 -->
        <div v-if="publishedChapters.length > 0" class="series-info__progress">
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

        <RouterLink to="/series" class="back-link">
          <Icon icon="lucide:arrow-left" />
          全部系列
        </RouterLink>
      </div>
    </section>

    <!-- ── 下方：目录 + 正文 ── -->
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
            <header class="catalog__header">
              <span>目录</span>
              <span
                v-if="hasCatalog || series.chapters?.length"
                class="catalog__count"
              >
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

/* ── 页面整体 ── */
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  width: 100%;
  min-width: 0;
}

/* ── 系列头卡（= 列表页横向卡放大版） ── */
.series-hero {
  position: relative;
  display: flex;
  align-items: stretch;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 85% 0%, color-mix(in srgb, var(--color-accent) 16%, transparent), transparent 55%),
    linear-gradient(160deg, var(--color-bg-surface), var(--color-bg-soft));
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.hero__cover {
  position: relative;
  flex-shrink: 0;
  width: 9.5rem;
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 60%, #6366f1));
}

.hero__cover--image {
  background: var(--color-bg-soft);
}

.hero__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hero__cover-letter {
  font-size: 2.8rem;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.95);
  letter-spacing: -0.02em;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

.hero__body {
  flex: 1;
  min-width: 0;
  padding: 1.4rem 1.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.hero__title {
  margin: 0;
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--color-text-primary);
}

.series-info__eyebrow {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

/* 介绍：clamp 2 行 + 展开 */
.series-info__desc-wrap {
  margin: 0.15rem 0 0;
}

.series-info__desc {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.series-info__desc--expanded {
  display: block;
  -webkit-line-clamp: unset;
  line-clamp: unset;
  overflow: visible;
}

.series-info__desc-toggle {
  margin-top: 0.25rem;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--color-accent-text);
  cursor: pointer;
}

.series-info__desc-toggle:hover {
  text-decoration: underline;
}

/* 元信息行 */
.hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem 0.6rem;
  margin-top: 0.35rem;
  font-size: 0.74rem;
  color: var(--color-text-muted);
}

.series-info__badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.68rem;
  font-weight: 700;
  padding: 0.16rem 0.6rem;
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

.hero__count {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  padding: 0.16rem 0.6rem;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.hero__updated {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  font-variant-numeric: tabular-nums;
}

.hero__updated :deep(svg) {
  width: 0.8rem;
  height: 0.8rem;
}

/* 进度条 */
.series-info__progress {
  margin-top: 0.6rem;
  max-width: 26rem;
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

/* 返回链接 */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  margin-top: 0.55rem;
  padding: 0.3rem 0.55rem 0.3rem 0.45rem;
  border-radius: 0.5rem;
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

/* hero 响应式：窄屏封面转顶部整条 */
@media (max-width: 600px) {
  .series-hero {
    flex-direction: column;
  }
  .hero__cover {
    width: 100%;
    height: 7rem;
  }
  .hero__body {
    padding: 1.1rem 1.15rem;
  }
}

/* ── 下方：目录 + 正文 ── */
.detail-body {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  width: 100%;
  align-items: start;
}

@media (min-width: 980px) {
  .detail-body {
    grid-template-columns: 260px minmax(0, 1fr);
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
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow-y: auto;
    padding-right: 4px;
    scrollbar-width: thin;
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
    padding: 0.7rem 0.85rem;
    border: 1px solid var(--color-border);
    border-radius: 0.85rem;
    background: var(--color-bg-surface);
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
