<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import {
  fetchArticleContent,
  fetchArticleDetail,
  type PostDetail,
} from '../../api/post'

const route = useRoute()

const detail = ref<PostDetail | null>(null)
const content = ref<string>('')
const loading = ref(false)
const error = ref<string | null>(null)

const slug = computed(() => {
  const value = route.query.slug
  return Array.isArray(value) ? value[0] ?? '' : (value ?? '')
})

const primaryCategory = computed(() => detail.value?.categories[0]?.name ?? '未分类')

const formattedDate = computed(() => {
  const value = detail.value?.published_at
  if (!value) return ''
  const date = new Date(value)
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

watch(
  slug,
  (value) => {
    loadArticle(value)
  },
)

onMounted(() => {
  loadArticle(slug.value)
})
</script>

<template>
  <div class="article-layout">
    <div class="article-main">
      <div v-if="loading" class="state">正在加载...</div>
      <div v-else-if="error" class="state state--error">{{ error }}</div>
      <template v-else-if="detail">
        <header class="article-header">
          <span class="badge">{{ primaryCategory }}</span>
          <h1 class="article-title">{{ detail.title }}</h1>
          <div class="article-meta">
            <span>{{ formattedDate }}</span>
            <span class="meta-dot">·</span>
            <span>{{ detail.view_count }} 次阅读</span>
          </div>
          <div v-if="detail.tags.length" class="article-tags">
            <span v-for="tag in detail.tags" :key="tag.id" class="tag">{{ tag.name }}</span>
          </div>
        </header>

        <hr class="divider" />

        <MdPreview
          editor-id="article-preview"
          :model-value="content"
          class="article-body"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
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

.divider {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 1.5rem 0;
}

.article-main {
  min-width: 0;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 2rem 2.5rem;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
}

@media (max-width: 640px) {
  .article-main {
    padding: 1.25rem;
  }
}

.article-body {
  --md-color: var(--color-text-primary);
  --md-bk-color: transparent;
}

.state {
  padding: 2rem 0;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state--error {
  color: #b91c1c;
}
</style>
