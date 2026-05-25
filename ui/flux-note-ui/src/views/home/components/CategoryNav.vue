<script setup lang="ts">
import { Icon } from '@iconify/vue'
import type { CategoryNode } from '../../../api/category'
import CategoryNavItem from './CategoryNavItem.vue'

const props = defineProps<{
  items: CategoryNode[]
  loading?: boolean
  activeId?: number | string | null
}>()

defineEmits<{
  (e: 'select', id: number | string | null): void
}>()

const totalCount = () =>
  props.items.reduce((acc, item) => acc + 1 + countDescendants(item), 0)

const countDescendants = (node: CategoryNode): number =>
  node.children.reduce((acc, child) => acc + 1 + countDescendants(child), 0)
</script>

<template>
  <div class="card">
    <div class="card-header">
      <h3 class="card-title">分类导航</h3>
      <Icon icon="lucide:layout-list" width="16" height="16" class="card-title-icon" />
    </div>

    <ul class="nav-list">
      <li
        class="nav-row nav-row--all"
        :class="activeId === null || activeId === undefined ? 'nav-row--active' : ''"
        @click="$emit('select', null)"
      >
        <span class="nav-row__icon">
          <Icon icon="lucide:layers" width="15" height="15" />
        </span>
        <span class="nav-row__name">全部文章</span>
        <span v-if="!loading && items.length > 0" class="nav-row__count">{{ totalCount() }}</span>
      </li>

      <li v-if="loading" class="nav-placeholder">
        <Icon icon="lucide:loader-2" width="14" height="14" class="nav-placeholder__spin" />
        <span>加载中...</span>
      </li>
      <li v-else-if="items.length === 0" class="nav-placeholder">
        <Icon icon="lucide:inbox" width="14" height="14" />
        <span>暂无分类</span>
      </li>
      <CategoryNavItem
        v-for="item in items"
        v-else
        :key="item.id"
        :node="item"
        :level="0"
        :active-id="activeId ?? null"
        @select="(id) => $emit('select', id)"
      />
    </ul>
  </div>
</template>

<style scoped>
.card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.125rem 1rem 1rem;
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.25rem;
}

.card-title {
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.card-title-icon {
  color: var(--color-text-secondary);
  opacity: 0.6;
}

.nav-list {
  margin-top: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  list-style: none;
  max-height: clamp(18rem, 55vh, 32rem);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 0.25rem;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--color-text-secondary) 25%, transparent) transparent;
}

.nav-list::-webkit-scrollbar {
  width: 6px;
}

.nav-list::-webkit-scrollbar-track {
  background: transparent;
}

.nav-list::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--color-text-secondary) 22%, transparent);
  border-radius: 9999px;
}

.nav-list::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--color-text-secondary) 40%, transparent);
}

.nav-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4375rem 0.75rem;
  border-radius: 0.625rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.4375rem;
  bottom: 0.4375rem;
  width: 2px;
  border-radius: 2px;
  background: transparent;
  transition: background 0.15s ease;
}

.nav-row:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.nav-row--active {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-weight: 600;
}

.nav-row--active:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.nav-row--active::before {
  background: var(--color-accent);
}

.nav-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.125rem;
  height: 1.125rem;
  flex-shrink: 0;
  opacity: 0.85;
}

.nav-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.005em;
}

.nav-row__count {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.375rem;
  padding: 0 0.4375rem;
  height: 1.125rem;
  border-radius: 9999px;
  background: color-mix(in srgb, var(--color-text-secondary) 12%, transparent);
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  line-height: 1;
}

.nav-row--active .nav-row__count {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
  color: var(--color-accent-text);
}

.nav-placeholder {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  opacity: 0.6;
}

.nav-placeholder__spin {
  animation: nav-spin 0.9s linear infinite;
}

@keyframes nav-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
