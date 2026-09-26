<script setup lang="ts">
import { Icon } from '@iconify/vue'
import FolderNode from './FolderNode.vue'
import { useSpaceStore } from '../../../stores/space'
import type { Folder, SpaceTag } from '../../../types/space'
import { isSameFilter, type SpaceFilter } from '../filter'

defineProps<{ active: SpaceFilter }>()
const emit = defineEmits<{
  select: [filter: SpaceFilter]
  createFolder: [parent: Folder | null]
  renameFolder: [folder: Folder]
  removeFolder: [folder: Folder]
  createTag: []
  removeTag: [tag: SpaceTag]
}>()

const space = useSpaceStore()

const views: { filter: SpaceFilter; icon: string; label: string }[] = [
  { filter: { kind: 'all' }, icon: 'lucide:bookmark', label: '全部书签' },
  { filter: { kind: 'uncategorized' }, icon: 'lucide:inbox', label: '未分类' },
  { filter: { kind: 'archived' }, icon: 'lucide:archive', label: '已归档' },
]
</script>

<template>
  <aside class="sidebar">
    <nav class="group">
      <button
        v-for="view in views"
        :key="view.filter.kind"
        class="item"
        :class="{ 'item--active': isSameFilter(active, view.filter) }"
        type="button"
        @click="emit('select', view.filter)"
      >
        <Icon :icon="view.icon" class="item__icon" />
        {{ view.label }}
      </button>
    </nav>

    <section class="group">
      <header class="group__head">
        <span>目录</span>
        <button class="group__add" type="button" title="新建目录" @click="emit('createFolder', null)">
          <Icon icon="lucide:plus" />
        </button>
      </header>
      <p v-if="!space.folders.length" class="group__empty">还没有目录</p>
      <ul v-else class="tree">
        <FolderNode
          v-for="folder in space.folders"
          :key="folder.id"
          :folder="folder"
          :depth="0"
          :active="active"
          @select="emit('select', $event)"
          @create="emit('createFolder', $event)"
          @rename="emit('renameFolder', $event)"
          @remove="emit('removeFolder', $event)"
        />
      </ul>
    </section>

    <section class="group">
      <header class="group__head">
        <span>标签</span>
        <button class="group__add" type="button" title="新建标签" @click="emit('createTag')">
          <Icon icon="lucide:plus" />
        </button>
      </header>
      <p v-if="!space.tags.length" class="group__empty">还没有标签</p>
      <div v-else class="tags">
        <span
          v-for="tag in space.tags"
          :key="tag.id"
          class="chip"
          :class="{ 'chip--active': isSameFilter(active, { kind: 'tag', id: tag.id }) }"
        >
          <button class="chip__label" type="button" @click="emit('select', { kind: 'tag', id: tag.id })">
            <span class="chip__dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />
            {{ tag.name }}
          </button>
          <button
            class="chip__remove"
            type="button"
            :aria-label="`删除标签 ${tag.name}`"
            @click="emit('removeTag', tag)"
          >
            <Icon icon="lucide:x" />
          </button>
        </span>
      </div>
    </section>
  </aside>
</template>

<style scoped lang="scss">
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.25rem 0.35rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.group__add {
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

.group__add:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.group__empty {
  padding: 0.25rem 0.6rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.6rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.item:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.item--active,
.item--active:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.item__icon {
  width: 1.05rem;
  height: 1.05rem;
}

.tree {
  list-style: none;
  margin: 0;
  padding: 0;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding-inline: 0.4rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.82rem;
}

.chip--active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.chip__label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.2rem 0.2rem 0.6rem;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}

.chip__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.chip__remove {
  display: inline-grid;
  place-items: center;
  width: 1.3rem;
  height: 1.3rem;
  margin-right: 0.15rem;
  border: 0;
  border-radius: 50%;
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  opacity: 0.6;
}

.chip__remove:hover {
  opacity: 1;
  color: var(--color-danger);
}
</style>
