<script setup lang="ts">
import { computed } from 'vue'
import type { CategorySummary, TagSummary } from '../../../api/post'

const props = defineProps<{
  slug: string
  title: string
  summary?: string | null
  categories: CategorySummary[]
  tags: TagSummary[]
  publishedAt: string
  coverUrl?: string | null
}>()

const primaryCategory = computed(() => props.categories[0]?.name ?? '未分类')

const tagText = computed(() => props.tags.map((t) => t.name).join(' · '))

const formattedDate = computed(() => {
  if (!props.publishedAt) return ''
  const date = new Date(props.publishedAt)
  if (Number.isNaN(date.getTime())) return props.publishedAt
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
})
</script>

<template>
  <RouterLink :to="{ path: '/article', query: { slug } }" class="article-card">
    <span class="badge">{{ primaryCategory }}</span>
    <h3 class="article-title">{{ title }}</h3>
    <p v-if="summary" class="article-summary">{{ summary }}</p>
    <div class="article-meta">
      <span>{{ formattedDate }}</span>
      <span v-if="tagText" class="article-tags">{{ tagText }}</span>
    </div>
  </RouterLink>
</template>

<style scoped>
.article-card {
  display: block;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.25rem;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition: box-shadow 0.2s ease;
}

.article-card:hover {
  box-shadow: 0 4px 16px -4px color-mix(in srgb, var(--color-border) 80%, transparent);
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
  margin-top: 0.75rem;
  font-size: 1.0625rem;
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-text-primary);
  transition: color 0.15s ease;
}

.article-card:hover .article-title {
  color: var(--color-accent);
}

.article-summary {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  line-height: 1.65;
  color: var(--color-text-secondary);
}

.article-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.article-tags {
  color: var(--color-accent-text);
  opacity: 0.85;
}
</style>
