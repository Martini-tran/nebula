<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { MdPreview, MdCatalog } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import {
  fetchArticleContent,
  fetchArticleDetail,
  type PostDetail,
} from '../../api/post'

/** 与 MdPreview 的 editor-id 保持一致，MdCatalog 通过此 ID 关联 */
const EDITOR_ID = 'article-preview'

const props = defineProps<{
  /** 外部传入的文章 slug，优先级高于 route.query.slug */
  slug?: string
  /** 隐藏右侧目录侧栏（嵌入到其他布局时常用） */
  hideToc?: boolean
}>()

const route = useRoute()

const detail = ref<PostDetail | null>(null)
const content = ref<string>('')
const loading = ref(false)
const error = ref<string | null>(null)
const readingProgress = ref(0)
const fontScale = ref<'normal' | 'large'>('normal')
const focusMode = ref(false)

/** MdCatalog 的滚动容器：页面级滚动使用 document.documentElement */
const scrollElement = ref<HTMLElement | null>(null)

const slug = computed(() => {
  if (props.slug) return props.slug
  const value = route.query.slug
  return Array.isArray(value) ? value[0] ?? '' : (value ?? '')
})

const primaryCategory = computed(() => detail.value?.categories[0]?.name ?? '未分类')

const isStandalone = computed(() => !props.slug)

const characterCount = computed(() => content.value.replace(/\s/g, '').length)

const estimatedMinutes = computed(() => Math.max(1, Math.ceil(characterCount.value / 420)))

const formattedCharacterCount = computed(() => characterCount.value.toLocaleString('zh-CN'))

const formattedDate = computed(() => {
  const value = detail.value?.publishedAt
  if (!value) return ''
  // 兼容后端 "yyyy-MM-dd HH:mm:ss" 格式（Safari 不接受空格分隔的日期字符串）
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
})

const loadArticle = async (currentSlug: string) => {
  if (!currentSlug) {
    error.value = '缺少文章标识'
    detail.value = null
    content.value = ''
    return
  }
  loading.value = true
  error.value = null
  try {
    const [meta, body] = await Promise.all([
      fetchArticleDetail(currentSlug),
      fetchArticleContent(currentSlug),
    ])
    detail.value = meta ?? null
    content.value = body?.content ?? ''
  } catch {
    detail.value = null
    content.value = ''
    error.value = '文章加载失败'
  } finally {
    loading.value = false
  }
}

const updateReadingProgress = () => {
  if (!isStandalone.value) return
  const scrollHeight = document.documentElement.scrollHeight - window.innerHeight
  readingProgress.value = scrollHeight > 0
    ? Math.min(100, Math.round((window.scrollY / scrollHeight) * 100))
    : 0
}

const toggleFontScale = () => {
  fontScale.value = fontScale.value === 'normal' ? 'large' : 'normal'
}

const toggleFocusMode = () => {
  if (!isStandalone.value) return
  focusMode.value = !focusMode.value
}

const handleKeydown = (event: KeyboardEvent) => {
  if (!isStandalone.value || ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName ?? '')) return
  if (event.key.toLowerCase() === 'f') toggleFocusMode()
  if (event.key === 'Escape' && focusMode.value) focusMode.value = false
}

watch(slug, (value) => {
  loadArticle(value)
})

onMounted(() => {
  scrollElement.value = document.documentElement
  window.addEventListener('scroll', updateReadingProgress, { passive: true })
  window.addEventListener('resize', updateReadingProgress)
  window.addEventListener('keydown', handleKeydown)
  updateReadingProgress()
  loadArticle(slug.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateReadingProgress)
  window.removeEventListener('resize', updateReadingProgress)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div
    class="article-reader"
    :class="{
      'article-reader--embedded': !isStandalone,
      'article-reader--large': fontScale === 'large',
      'article-reader--focus': focusMode,
    }"
  >
    <div v-if="isStandalone" class="reading-progress" aria-hidden="true">
      <span :style="{ width: readingProgress + '%' }" />
    </div>

    <div v-if="isStandalone" class="reader-toolbar" aria-label="阅读工具">
      <span class="reader-toolbar__label">{{ String(readingProgress).padStart(2, '0') }}% 已读</span>
      <div class="reader-toolbar__actions">
        <button
          type="button"
          class="reader-action"
          :class="{ 'reader-action--active': fontScale === 'large' }"
          :aria-pressed="fontScale === 'large'"
          title="切换字号"
          @click="toggleFontScale"
        >
          A+
        </button>
        <button
          type="button"
          class="reader-action"
          :class="{ 'reader-action--active': focusMode }"
          :aria-pressed="focusMode"
          title="切换专注模式（F）"
          @click="toggleFocusMode"
        >
          专注
        </button>
      </div>
    </div>

    <div class="reader-layout">
      <aside v-if="isStandalone && content && scrollElement && !hideToc" class="reader-rail reader-rail--left">
        <p class="rail-kicker">章节</p>
        <MdCatalog
          :editor-id="EDITOR_ID"
          :scroll-element="scrollElement"
          class="toc-catalog"
        />
        <div class="rail-progress">
          <span>阅读进度</span>
          <strong>{{ readingProgress }}%</strong>
        </div>
      </aside>

      <main class="reader-main">
        <div v-if="loading" class="state">正在打开这篇记录...</div>
        <div v-else-if="error" class="state state--error">{{ error }}</div>

        <template v-else-if="detail">
          <header class="article-header">
            <p class="article-eyebrow">{{ primaryCategory }} · {{ estimatedMinutes }} 分钟</p>
            <h1 class="article-title">{{ detail.title }}</h1>
            <p v-if="detail.summary" class="article-lede">{{ detail.summary }}</p>
            <div class="article-meta">
              <span>{{ formattedDate }}</span>
              <span>{{ detail.viewCount }} 次阅读</span>
              <span v-if="detail.categories.length">{{ detail.categories.map((item) => item.name).join(' · ') }}</span>
            </div>
            <div v-if="detail.tags.length" class="article-tags">
              <span v-for="tag in detail.tags" :key="tag.id" class="tag">#{{ tag.name }}</span>
            </div>
          </header>

          <figure v-if="detail.coverUrl" class="article-cover-wrapper">
            <img
              :src="detail.coverUrl"
              :alt="detail.title"
              class="article-cover"
              loading="lazy"
            />
          </figure>

          <MdPreview
            :editor-id="EDITOR_ID"
            :model-value="content"
            class="article-body"
          />
        </template>
      </main>

      <aside v-if="isStandalone && detail" class="reader-rail reader-rail--right">
        <section class="reader-fact">
          <p class="rail-kicker">文章信息</p>
          <dl class="facts">
            <div><dt>字数</dt><dd>{{ formattedCharacterCount }} <small>估算</small></dd></div>
            <div><dt>阅读</dt><dd>{{ estimatedMinutes }} min</dd></div>
            <div><dt>发布</dt><dd>{{ formattedDate }}</dd></div>
            <div><dt>浏览</dt><dd>{{ detail.viewCount }}</dd></div>
          </dl>
        </section>
        <section class="reader-fact reader-fact--next">
          <p class="rail-kicker">继续浏览</p>
          <RouterLink to="/articles" class="reader-link">返回文章索引 <span aria-hidden="true">↗</span></RouterLink>
          <RouterLink to="/essays" class="reader-link">去看一篇随笔 <span aria-hidden="true">↗</span></RouterLink>
        </section>
      </aside>

      <aside v-if="!isStandalone && content && scrollElement && !hideToc" class="article-toc article-toc--embedded">
        <p class="toc-title">目录</p>
        <MdCatalog
          :editor-id="EDITOR_ID"
          :scroll-element="scrollElement"
          class="toc-catalog"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.article-reader {
  --reader-paper: #f5f7f2;
  --reader-paper-soft: #e9efe7;
  --reader-ink: #17252c;
  --reader-muted: #60717a;
  --reader-line: #c8d4cf;
  --reader-accent: #cc674e;
  --reader-link: #1d4762;
  min-height: calc(100vh - 6.5rem);
  padding: 3.5rem 0 6rem;
  background: var(--reader-paper);
  color: var(--reader-ink);
  font-family: var(--font-sans), sans-serif;
}

:global(html[data-theme='dark']) .article-reader,
:global(html[data-theme='ocean']) .article-reader {
  --reader-paper: #12232b;
  --reader-paper-soft: #1b3439;
  --reader-ink: #e4eee9;
  --reader-muted: #a9bec1;
  --reader-line: #385158;
  --reader-accent: #f2c94c;
  --reader-link: #b6d5e5;
}

.article-reader--embedded {
  min-height: 0;
  padding: 0;
  background: transparent;
  color: var(--color-text-primary);
}

.reading-progress {
  position: fixed;
  z-index: 35;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: transparent;
  pointer-events: none;
}

.reading-progress span {
  display: block;
  height: 100%;
  background: var(--reader-accent);
  transition: width 0.15s ease;
}

.reader-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
  width: min(90rem, calc(100% - 3rem));
  margin: 0 auto 1rem;
  color: var(--reader-muted);
  font-size: 0.72rem;
}

.reader-toolbar__label {
  font-variant-numeric: tabular-nums;
}

.reader-toolbar__actions {
  display: flex;
  gap: 0.4rem;
}

.reader-action {
  min-width: 2.25rem;
  height: 2rem;
  padding: 0 0.55rem;
  border: 1px solid var(--reader-line);
  border-radius: 0;
  background: transparent;
  color: var(--reader-muted);
  cursor: pointer;
  font-size: 0.72rem;
  transition: color 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.reader-action:hover,
.reader-action--active {
  border-color: var(--reader-accent);
  background: color-mix(in srgb, var(--reader-accent) 9%, transparent);
  color: var(--reader-accent);
}

.reader-action:focus-visible,
.reader-link:focus-visible {
  outline: 2px solid var(--reader-accent);
  outline-offset: 3px;
}

.reader-layout {
  display: grid;
  grid-template-columns: 11rem minmax(0, 78ch) 11rem;
  justify-content: center;
  align-items: start;
  gap: clamp(2rem, 3vw, 3.5rem);
  width: min(96rem, calc(100% - 3rem));
  margin: 0 auto;
}

.reader-main {
  min-width: 0;
}

.reader-rail {
  position: sticky;
  top: 6.5rem;
  align-self: start;
  max-height: calc(100vh - 8rem);
  overflow-y: auto;
  color: var(--reader-muted);
  font-size: 0.78rem;
  scrollbar-width: thin;
  scrollbar-color: var(--reader-line) transparent;
}

.reader-rail::-webkit-scrollbar {
  width: 4px;
}

.reader-rail::-webkit-scrollbar-thumb {
  background: var(--reader-line);
}

.rail-kicker,
.toc-title {
  margin: 0 0 1rem;
  color: var(--reader-ink);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.toc-catalog {
  border-left: 1px solid var(--reader-line);
  padding-left: 1rem;
}

.toc-catalog :deep(.md-editor-catalog-link) {
  display: block;
  padding: 0.3rem 0.4rem;
  border-radius: 0;
  color: var(--reader-muted);
  font-size: 0.78rem;
  line-height: 1.5;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.16s ease, border-color 0.16s ease;
}

.toc-catalog :deep(.md-editor-catalog-link:hover),
.toc-catalog :deep(.md-editor-catalog-active > a) {
  color: var(--reader-accent);
}

.toc-catalog :deep(.md-editor-catalog-active > a) {
  border-left: 2px solid var(--reader-accent);
  margin-left: -1.05rem;
  padding-left: calc(1rem + 0.4rem);
}

.rail-progress {
  display: grid;
  gap: 0.35rem;
  margin-top: 2rem;
  padding-top: 1rem;
  border-top: 1px solid var(--reader-line);
}

.rail-progress strong {
  color: var(--reader-ink);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 1rem;
  font-weight: 500;
}

.article-header {
  padding-bottom: 2.1rem;
  border-bottom: 1px solid var(--reader-line);
}

.article-eyebrow {
  margin: 0 0 0.9rem;
  color: var(--reader-accent);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.article-title {
  margin: 0 0 1.25rem;
  color: var(--reader-ink);
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: 3.5rem;
  font-weight: 700;
  line-height: 1.12;
}

.article-lede {
  max-width: 58ch;
  margin: 0;
  color: color-mix(in srgb, var(--reader-ink) 78%, var(--reader-muted));
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: 1.08rem;
  line-height: 1.85;
}

.article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem 1.2rem;
  margin-top: 1.45rem;
  color: var(--reader-muted);
  font-size: 0.78rem;
}

.article-meta span + span::before {
  content: '·';
  margin-right: 1.2rem;
  color: var(--reader-line);
}

.article-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 1rem;
}

.tag {
  color: var(--reader-muted);
  font-size: 0.76rem;
}

.article-cover-wrapper {
  margin: 2rem 0 0;
}

.article-cover {
  display: block;
  width: 100%;
  max-height: 28rem;
  object-fit: cover;
  filter: saturate(0.82) contrast(0.96);
}

.article-body {
  --md-color: var(--reader-ink);
  --md-bk-color: transparent;
  margin-top: 2.2rem;
  font-size: 1.06rem;
}

.article-reader--large .article-body {
  font-size: 1.18rem;
}

.article-body :deep(.md-editor-preview) {
  color: var(--reader-ink);
  background: transparent;
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: inherit;
  line-height: 1.95;
}

.article-body :deep(.md-editor-preview p) {
  margin: 0 0 1.35rem;
  line-height: inherit;
}

.article-body :deep(.md-editor-preview h1),
.article-body :deep(.md-editor-preview h2),
.article-body :deep(.md-editor-preview h3),
.article-body :deep(.md-editor-preview h4) {
  color: var(--reader-ink);
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  line-height: 1.35;
}

.article-body :deep(.md-editor-preview h1) {
  margin: 2.7rem 0 1rem;
  font-size: 1.85rem;
}

.article-body :deep(.md-editor-preview h2) {
  margin: 2.7rem 0 0.9rem;
  font-size: 1.55rem;
}

.article-body :deep(.md-editor-preview h2)::before {
  content: '/';
  margin-right: 0.4rem;
  color: var(--reader-accent);
}

.article-body :deep(.md-editor-preview h3) {
  margin: 2rem 0 0.7rem;
  font-size: 1.28rem;
}

.article-body :deep(.md-editor-preview h4) {
  margin: 1.7rem 0 0.6rem;
  font-size: 1.1rem;
}

.article-body :deep(.md-editor-preview strong) {
  color: var(--reader-ink);
}

.article-body :deep(.md-editor-preview a) {
  color: var(--reader-link);
  text-decoration: underline;
  text-decoration-color: color-mix(in srgb, var(--reader-link) 45%, transparent);
  text-underline-offset: 3px;
}

.article-body :deep(.md-editor-preview ul),
.article-body :deep(.md-editor-preview ol) {
  margin: 1rem 0 1.35rem;
  padding-left: 1.5rem;
}

.article-body :deep(.md-editor-preview li) {
  margin: 0.35rem 0;
  line-height: inherit;
}

.article-body :deep(.md-editor-preview blockquote) {
  margin: 2rem 0;
  padding: 0.2rem 0 0.2rem 1.4rem;
  border-left: 3px solid var(--reader-accent);
  background: transparent;
  color: color-mix(in srgb, var(--reader-ink) 82%, var(--reader-muted));
  font-style: italic;
}

.article-body :deep(.md-editor-preview pre) {
  margin: 1.7rem 0;
  padding: 1rem 1.15rem;
  overflow: auto;
  border: 1px solid var(--reader-line);
  border-radius: 0;
  background: var(--reader-paper-soft);
  color: var(--reader-ink);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.82rem;
  line-height: 1.7;
}

.article-body :deep(.md-editor-preview code) {
  color: var(--reader-accent);
  background: var(--reader-paper-soft);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.86em;
}

.article-body :deep(.md-editor-preview p code),
.article-body :deep(.md-editor-preview li code) {
  padding: 0.08em 0.25em;
}

.article-body :deep(.md-editor-preview img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1.5rem 0;
}

.article-body :deep(.md-editor-preview hr) {
  margin: 2rem 0;
  border: 0;
  border-top: 1px solid var(--reader-line);
}

.article-body :deep(.md-editor-preview table) {
  display: block;
  max-width: 100%;
  margin: 1.5rem 0;
  overflow-x: auto;
  font-size: 0.92rem;
}

.reader-fact {
  padding-top: 1rem;
  border-top: 1px solid var(--reader-line);
}

.reader-fact--next {
  margin-top: 2.2rem;
}

.facts {
  display: grid;
  gap: 0.55rem;
  margin: 0;
}

.facts div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--reader-line);
  font-size: 0.76rem;
}

.facts dt {
  color: var(--reader-muted);
}

.facts dd {
  margin: 0;
  color: var(--reader-ink);
  text-align: right;
}

.facts small {
  color: var(--reader-muted);
  font-size: 0.64rem;
}

.reader-link {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--reader-line);
  color: var(--reader-ink);
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: 0.88rem;
  line-height: 1.4;
  transition: color 0.16s ease;
}

.reader-link:hover {
  color: var(--reader-accent);
}

.state {
  padding: 5rem 0;
  color: var(--reader-muted);
  font-size: 0.88rem;
  text-align: center;
}

.state--error {
  color: #ad463d;
}

.article-toc--embedded {
  display: block;
  position: sticky;
  top: 6.5rem;
  align-self: start;
  max-height: calc(100vh - 8rem);
  overflow-y: auto;
  scrollbar-width: thin;
}

.article-toc--embedded .toc-title {
  color: var(--color-text-secondary);
}

.article-reader--focus .reader-layout {
  grid-template-columns: minmax(0, 70ch);
}

.article-reader--focus .reader-rail {
  display: none;
}

.article-reader--embedded .reader-layout {
  grid-template-columns: minmax(0, 1fr) 220px;
  width: 100%;
  gap: 1.5rem;
}

.article-reader--embedded .article-title {
  max-width: none;
  font-size: 2rem;
}

.article-reader--embedded .article-body {
  margin-top: 1.5rem;
}

.article-reader--embedded .article-toc--embedded {
  min-width: 0;
}

@media (max-width: 1120px) {
  .reader-layout {
    grid-template-columns: 10rem minmax(0, 70ch) 11rem;
    gap: 2rem;
  }

  .article-title {
    font-size: 3rem;
  }
}

@media (max-width: 920px) {
  .reader-layout {
    grid-template-columns: 10rem minmax(0, 1fr);
  }

  .reader-rail--right {
    display: none;
  }

  .article-reader--embedded .reader-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .article-reader--embedded .article-toc--embedded {
    display: none;
  }
}

@media (max-width: 720px) {
  .article-reader {
    padding: 2.25rem 0 4rem;
  }

  .reader-toolbar,
  .reader-layout {
    width: calc(100% - 2rem);
  }

  .reader-layout {
    display: block;
  }

  .reader-rail {
    display: none;
  }

  .article-title {
    max-width: none;
    font-size: 2.35rem;
  }

  .article-lede {
    font-size: 1rem;
  }

  .article-body {
    margin-top: 1.7rem;
    font-size: 1rem;
  }

  .article-reader--large .article-body {
    font-size: 1.1rem;
  }

  .article-reader--embedded .article-title {
    font-size: 1.65rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .reading-progress span,
  .reader-action,
  .reader-link,
  .toc-catalog :deep(.md-editor-catalog-link) {
    transition: none;
  }
}
</style>
