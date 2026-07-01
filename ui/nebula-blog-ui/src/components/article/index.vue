<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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

/** MdCatalog 的滚动容器：页面级滚动使用 document.documentElement */
const scrollElement = ref<HTMLElement | null>(null)

const slug = computed(() => {
  if (props.slug) return props.slug
  const value = route.query.slug
  return Array.isArray(value) ? value[0] ?? '' : (value ?? '')
})

const primaryCategory = computed(() => detail.value?.categories[0]?.name ?? '未分类')

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

watch(slug, (value) => {
  loadArticle(value)
})

onMounted(() => {
  scrollElement.value = document.documentElement
  loadArticle(slug.value)
})
</script>

<template>
  <div class="article-layout">
    <!-- 主体内容区 -->
    <div class="article-main">
      <div v-if="loading" class="state">正在加载...</div>
      <div v-else-if="error" class="state state--error">{{ error }}</div>

      <template v-else-if="detail">
        <!-- 文章 header：分类、标题、元信息、标签 -->
        <header class="article-header">
          <span class="badge">{{ primaryCategory }}</span>
          <h1 class="article-title">{{ detail.title }}</h1>
          <div class="article-meta">
            <span>{{ formattedDate }}</span>
            <span class="meta-dot">·</span>
            <span>{{ detail.viewCount }} 次阅读</span>
          </div>
          <div v-if="detail.tags.length" class="article-tags">
            <span v-for="tag in detail.tags" :key="tag.id" class="tag">{{ tag.name }}</span>
          </div>
        </header>

        <!-- 封面图（有则展示，紧跟 header） -->
        <div v-if="detail.coverUrl" class="article-cover-wrapper">
          <img
            :src="detail.coverUrl"
            :alt="detail.title"
            class="article-cover"
            loading="lazy"
          />
        </div>

        <hr class="divider" />

        <!-- 正文 Markdown 渲染 -->
        <MdPreview
          :editor-id="EDITOR_ID"
          :model-value="content"
          class="article-body"
        />
      </template>
    </div>

    <!-- 目录导航侧栏（仅 lg 以上可见，且有正文内容时才渲染） -->
    <aside v-if="content && scrollElement && !hideToc" class="article-toc">
      <p class="toc-title">目录</p>
      <MdCatalog
        :editor-id="EDITOR_ID"
        :scroll-element="scrollElement"
        class="toc-catalog"
      />
    </aside>
  </div>
</template>

<style scoped>
/* ── 整体布局 ── */
.article-layout {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: 1fr;
  align-items: start;
}

@media (min-width: 1024px) {
  .article-layout {
    grid-template-columns: minmax(0, 1fr) 220px;
  }
}

/* ── 主体卡片 ── */
.article-main {
  min-width: 0;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 2.25rem 2.75rem;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
}

@media (max-width: 640px) {
  .article-main {
    padding: 1.25rem;
  }
}

/* ── 文章 header ── */
.article-header {
  margin-bottom: 1.5rem;
}

.badge {
  display: inline-block;
  padding: 0.2rem 0.65rem;
  border-radius: 9999px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 600;
}

.article-title {
  margin-top: 0.875rem;
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.article-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-top: 0.875rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.meta-dot {
  opacity: 0.4;
}

.article-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.875rem;
}

.tag {
  display: inline-block;
  padding: 0.25rem 0.65rem;
  border-radius: 9999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* ── 封面图 ── */
.article-cover-wrapper {
  /* 突破 padding 让图片占满卡片宽度 */
  margin: 0 -2.75rem 1.5rem;
}

@media (max-width: 640px) {
  .article-cover-wrapper {
    margin: 0 -1.25rem 1.25rem;
  }
}

.article-cover {
  display: block;
  width: 100%;
  max-height: 460px;
  object-fit: cover;
}

/* ── 分割线 ── */
.divider {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 1.5rem 0;
}

/* ── Markdown 正文 ── */
.article-body {
  --md-color: var(--color-text-primary);
  --md-bk-color: transparent;
  font-size: 16px;
}

/* md-editor-v3 正文字号/行距增强 */
.article-body :deep(.md-editor-preview) {
  font-size: 16px;
  line-height: 1.85;
  color: var(--color-text-primary);
}

.article-body :deep(.md-editor-preview p) {
  margin: 1rem 0;
  line-height: 1.85;
}

.article-body :deep(.md-editor-preview h1) {
  font-size: 1.75rem;
  margin-top: 2.25rem;
  margin-bottom: 1rem;
  letter-spacing: -0.02em;
}

.article-body :deep(.md-editor-preview h2) {
  font-size: 1.4rem;
  margin-top: 2rem;
  margin-bottom: 0.85rem;
  letter-spacing: -0.01em;
}

.article-body :deep(.md-editor-preview h3) {
  font-size: 1.18rem;
  margin-top: 1.6rem;
  margin-bottom: 0.6rem;
}

.article-body :deep(.md-editor-preview h4) {
  font-size: 1.05rem;
  margin-top: 1.4rem;
}

.article-body :deep(.md-editor-preview ul),
.article-body :deep(.md-editor-preview ol) {
  padding-left: 1.5rem;
  margin: 1rem 0;
}

.article-body :deep(.md-editor-preview li) {
  line-height: 1.85;
  margin: 0.35rem 0;
}

.article-body :deep(.md-editor-preview blockquote) {
  border-left: 3px solid var(--color-accent);
  background: color-mix(in srgb, var(--color-accent) 6%, transparent);
  margin: 1.25rem 0;
  padding: 0.75rem 1.1rem;
  border-radius: 0 0.5rem 0.5rem 0;
  color: var(--color-text-secondary);
}

.article-body :deep(.md-editor-preview img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
  margin: 1.25rem 0;
}

.article-body :deep(.md-editor-preview pre) {
  font-size: 14px;
  line-height: 1.7;
  margin: 1.25rem 0;
  border-radius: 0.6rem;
}

.article-body :deep(.md-editor-preview code) {
  font-size: 0.92em;
}

.article-body :deep(.md-editor-preview p code),
.article-body :deep(.md-editor-preview li code) {
  padding: 0.1em 0.4em;
  border-radius: 0.3em;
  background: color-mix(in srgb, var(--color-accent) 10%, var(--color-bg-soft));
  color: var(--color-accent-text);
  font-weight: 600;
}

.article-body :deep(.md-editor-preview hr) {
  margin: 2rem 0;
  border: 0;
  border-top: 1px solid var(--color-border);
}

.article-body :deep(.md-editor-preview table) {
  margin: 1.25rem 0;
  font-size: 0.95rem;
}

/* ── 状态提示 ── */
.state {
  padding: 2rem 0;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state--error {
  color: #b91c1c;
}

/* ── 目录侧栏 ── */
.article-toc {
  display: none;
}

@media (min-width: 1024px) {
  .article-toc {
    display: block;
    position: sticky;
    top: 6.5rem;
    align-self: start;
    max-height: calc(100vh - 8rem);
    overflow-y: auto;
    overscroll-behavior: contain;
    border-radius: 0.875rem;
    border: 1px solid var(--color-border);
    background: var(--color-bg-surface);
    padding: 1rem 0.875rem;
    box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
    scrollbar-width: thin;
    scrollbar-color: color-mix(in srgb, var(--color-text-secondary) 25%, transparent) transparent;
  }

  .article-toc::-webkit-scrollbar {
    width: 4px;
  }

  .article-toc::-webkit-scrollbar-thumb {
    background: color-mix(in srgb, var(--color-text-secondary) 22%, transparent);
    border-radius: 9999px;
  }
}

.toc-title {
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  opacity: 0.6;
  margin-bottom: 0.625rem;
  padding: 0 0.25rem;
}

/* md-editor-v3 MdCatalog 默认样式覆盖 */
.toc-catalog :deep(.md-editor-catalog-link) {
  display: block;
  padding: 0.25rem 0.375rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: background 0.12s ease, color 0.12s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toc-catalog :deep(.md-editor-catalog-link:hover) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.toc-catalog :deep(.md-editor-catalog-active > a) {
  color: var(--color-accent-text);
  font-weight: 600;
}
</style>
