<script setup lang="ts">
import type { PopularTag } from '../../../api/tag'

defineProps<{
  tags: PopularTag[]
  loading?: boolean
  activeId?: number | null
}>()

defineEmits<{
  (e: 'select', id: number | null): void
}>()
</script>

<template>
  <div class="card">
    <h3 class="card-title">标签</h3>
    <div class="mt-4 flex flex-wrap gap-2">
      <span v-if="loading" class="placeholder">加载中...</span>
      <span v-else-if="tags.length === 0" class="placeholder">暂无标签</span>
      <button
        v-for="tag in tags"
        v-else
        :key="tag.id"
        type="button"
        class="tag"
        :class="activeId === tag.id ? 'tag--active' : ''"
        :title="`${tag.post_count} 篇文章`"
        @click="$emit('select', activeId === tag.id ? null : tag.id)"
      >
        {{ tag.name }}
        <span class="tag__count">{{ tag.post_count }}</span>
      </button>
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

.tag {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.3rem 0.7rem;
  border-radius: 9999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.tag:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 30%, transparent);
}

.tag--active {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 50%, transparent);
}

.tag--active:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.tag__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.125rem;
  height: 1rem;
  padding: 0 0.3rem;
  border-radius: 9999px;
  background: color-mix(in srgb, var(--color-text-secondary) 12%, transparent);
  font-size: 0.625rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  line-height: 1;
}

.tag--active .tag__count {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
  color: var(--color-accent-text);
}

.placeholder {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  opacity: 0.7;
}
</style>
