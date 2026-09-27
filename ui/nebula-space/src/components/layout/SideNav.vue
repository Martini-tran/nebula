<script setup lang="ts">
/**
 * 模块左栏：若干分组，每组若干项（图标 + 名称 + 计数）。随手记、任务等模块共用。
 * 窄屏下由页面决定收起（加 class），这里只管排版。
 */
import { Icon } from '@iconify/vue'

export interface SideNavItem {
  key: string
  label: string
  icon?: string
  /** 色点，替代图标（标签、清单） */
  dot?: string
  count?: number | null
  /** 计数标红（如过期） */
  alert?: boolean
}

export interface SideNavGroup {
  key: string
  title?: string
  items: SideNavItem[]
  /** 组标题右侧的「+」 */
  addLabel?: string
  empty?: string
}

defineProps<{ groups: SideNavGroup[]; active: string }>()
const emit = defineEmits<{ select: [key: string]; add: [groupKey: string] }>()
</script>

<template>
  <nav class="sn">
    <section v-for="group in groups" :key="group.key" class="sn__group">
      <header v-if="group.title" class="sn__head">
        <span>{{ group.title }}</span>
        <button v-if="group.addLabel" type="button" class="sn__add" :title="group.addLabel" @click="emit('add', group.key)">
          <Icon icon="lucide:plus" />
        </button>
      </header>
      <p v-if="!group.items.length && group.empty" class="sn__empty">{{ group.empty }}</p>
      <button
        v-for="item in group.items"
        :key="item.key"
        type="button"
        class="sn__item"
        :class="{ 'sn__item--on': active === item.key }"
        :aria-current="active === item.key ? 'page' : undefined"
        @click="emit('select', item.key)"
      >
        <Icon v-if="item.icon" :icon="item.icon" class="sn__icon" />
        <span v-else-if="item.dot" class="sn__dot" :style="{ background: item.dot }" />
        <span class="sn__label">{{ item.label }}</span>
        <span v-if="item.count" class="sn__count" :class="{ 'sn__count--alert': item.alert }">{{ item.count }}</span>
      </button>
    </section>
    <slot />
  </nav>
</template>

<style scoped>
.sn {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.sn__group {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.sn__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.25rem 0.35rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.sn__add {
  display: inline-grid;
  place-items: center;
  width: 1.6rem;
  height: 1.6rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.sn__add:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.sn__empty {
  padding: 0.2rem 0.6rem;
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.sn__item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.48rem 0.6rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.sn__item:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.sn__item--on,
.sn__item--on:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.sn__icon {
  flex: none;
  width: 1.05rem;
  height: 1.05rem;
}

.sn__dot {
  flex: none;
  width: 0.55rem;
  height: 0.55rem;
  margin-inline: 0.25rem;
  border-radius: 50%;
}

.sn__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sn__count {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.sn__count--alert {
  color: var(--color-danger);
}
</style>
