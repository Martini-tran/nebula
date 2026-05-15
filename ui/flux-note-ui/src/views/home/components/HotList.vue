<script setup lang="ts">
import type { PostListItem } from '../../../api/post'

defineProps<{
  items: PostListItem[]
  loading?: boolean
}>()

const formatReads = (count: number) => {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w 阅读`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k 阅读`
  return `${count} 阅读`
}
</script>

<template>
  <div class="card">
    <h3 class="card-title">热门文章</h3>
    <div class="mt-4 space-y-3">
      <div v-if="loading" class="hot-placeholder">加载中...</div>
      <div v-else-if="items.length === 0" class="hot-placeholder">暂无数据</div>
      <RouterLink
        v-for="(item, index) in items"
        v-else
        :key="item.id"
        :to="{ path: '/article', query: { slug: item.slug } }"
        class="hot-item"
      >
        <span class="hot-item__rank" :class="index === 0 ? 'hot-item__rank--top' : ''">
          {{ index + 1 }}
        </span>
        <div class="min-w-0">
          <p class="hot-item__title">{{ item.title }}</p>
          <p class="hot-item__reads">{{ formatReads(item.view_count) }}</p>
        </div>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.card {
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.25rem;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
}

.card-title {
  font-size: 0.9375rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.hot-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border-radius: 0.625rem;
  background: var(--color-bg-soft);
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition: background 0.15s ease;
}

.hot-item:hover {
  background: var(--color-accent-soft);
}

.hot-item__rank {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.375rem;
  height: 1.375rem;
  border-radius: 0.375rem;
  background: var(--color-border);
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin-top: 0.1rem;
}

.hot-item__rank--top {
  background: var(--color-accent);
  color: #fff;
}

.hot-item__title {
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.45;
  color: var(--color-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hot-item:hover .hot-item__title {
  color: var(--color-accent-text);
}

.hot-item__reads {
  margin-top: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.hot-placeholder {
  padding: 0.625rem 0.75rem;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  opacity: 0.7;
}
</style>
