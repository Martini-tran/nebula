<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import type { Folder } from '../../../types/space'
import { isSameFilter, type SpaceFilter } from '../filter'

const props = defineProps<{ folder: Folder; depth: number; active: SpaceFilter }>()
const emit = defineEmits<{
  select: [filter: SpaceFilter]
  create: [parent: Folder]
  rename: [folder: Folder]
  remove: [folder: Folder]
}>()

/** 顶层目录默认展开，更深的层级默认收起 */
const expanded = ref(props.depth < 1)
const hasChildren = computed(() => Boolean(props.folder.children?.length))
const isActive = computed(() => isSameFilter(props.active, { kind: 'folder', id: props.folder.id }))
</script>

<template>
  <li>
    <div
      class="node"
      :class="{ 'node--active': isActive }"
      :style="{ paddingLeft: `${0.2 + depth * 0.9}rem` }"
    >
      <button
        class="node__toggle"
        type="button"
        :aria-label="expanded ? '收起' : '展开'"
        :style="{ visibility: hasChildren ? 'visible' : 'hidden' }"
        @click="expanded = !expanded"
      >
        <Icon :icon="expanded ? 'lucide:chevron-down' : 'lucide:chevron-right'" />
      </button>
      <button class="node__label" type="button" @click="emit('select', { kind: 'folder', id: folder.id })">
        <Icon :icon="expanded && hasChildren ? 'lucide:folder-open' : 'lucide:folder'" class="node__icon" />
        <span class="node__name">{{ folder.name }}</span>
      </button>
      <span class="node__actions">
        <button type="button" title="新建子目录" @click="emit('create', folder)">
          <Icon icon="lucide:folder-plus" />
        </button>
        <button type="button" title="重命名" @click="emit('rename', folder)">
          <Icon icon="lucide:pencil" />
        </button>
        <button type="button" title="删除" @click="emit('remove', folder)">
          <Icon icon="lucide:trash-2" />
        </button>
      </span>
    </div>
    <ul v-if="hasChildren && expanded" class="tree">
      <FolderNode
        v-for="child in folder.children ?? []"
        :key="child.id"
        :folder="child"
        :depth="depth + 1"
        :active="active"
        @select="emit('select', $event)"
        @create="emit('create', $event)"
        @rename="emit('rename', $event)"
        @remove="emit('remove', $event)"
      />
    </ul>
  </li>
</template>

<style scoped lang="scss">
.tree {
  list-style: none;
  margin: 0;
  padding: 0;
}

.node {
  display: flex;
  align-items: center;
  gap: 0.1rem;
  border-radius: var(--radius-md);
  padding-right: 0.25rem;
  color: var(--color-text-secondary);
}

.node:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.node--active,
.node--active:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.node__toggle,
.node__actions button {
  display: inline-grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  flex-shrink: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: inherit;
  cursor: pointer;
}

.node__actions button:hover {
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.node__label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex: 1;
  min-width: 0;
  padding: 0.4rem 0.2rem;
  border: 0;
  background: none;
  color: inherit;
  font-size: 0.92rem;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
}

.node__icon {
  flex-shrink: 0;
  width: 1rem;
  height: 1rem;
}

.node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作按钮只在悬停/聚焦时出现，触屏设备常显 */
.node__actions {
  display: none;
}

.node:hover .node__actions,
.node:focus-within .node__actions {
  display: inline-flex;
}

@media (hover: none) {
  .node__actions {
    display: inline-flex;
  }
}
</style>
