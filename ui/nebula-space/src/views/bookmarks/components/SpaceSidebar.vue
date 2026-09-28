<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import FolderNode from './FolderNode.vue'
import { fetchImportTasks } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { formatDate } from '../../../utils/format'
import { TaskStatus, type Folder, type ImportTask, type SpaceTag } from '../../../types/space'
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
  { filter: { kind: 'broken' }, icon: 'lucide:link-2-off', label: '失效链接' },
]

/** 上次成功导入的摘要；没有导入记录权限时静默不显示 */
const lastImport = ref<ImportTask | null>(null)

const loadLastImport = async () => {
  try {
    const page = await fetchImportTasks({ pageNum: 1, pageSize: 5 }, true)
    lastImport.value = page?.records?.find((task) => task.status === TaskStatus.SUCCESS) ?? null
  } catch {
    lastImport.value = null
  }
}

/**
 * 目录树滚动区：内容在上/下边缘被截断时，那一侧渐隐，提示还能继续滚动。
 * 标题区和目录内容的高度交给样式算目录区的最小高度——目录多时至少露出几行，目录少时不留空白。
 */
const foldersGroup = ref<HTMLElement | null>(null)
const treeScroll = ref<HTMLElement | null>(null)
const treeList = ref<HTMLElement | null>(null)
const treeFade = reactive({ top: false, bottom: false })
const treeSize = reactive({ head: 0, content: 0 })

const updateTreeScroll = () => {
  const el = treeScroll.value
  if (!el) return
  if (foldersGroup.value) {
    treeSize.head = el.getBoundingClientRect().top - foldersGroup.value.getBoundingClientRect().top
  }
  treeSize.content = treeList.value?.offsetHeight ?? 0
  treeFade.top = el.scrollTop > 1
  treeFade.bottom = el.scrollTop + el.clientHeight < el.scrollHeight - 1
}

// 窗口高度变化改变的是滚动区高度，展开/收起子目录改变的是内容高度，两者都要观察。
// 更新后目录区最小高度会变、滚动区尺寸随之再变，推到下一帧处理，避免触发 ResizeObserver 循环告警
watch(
  [treeScroll, treeList],
  ([scroller, list], _, onCleanup) => {
    if (!scroller || !list) return
    let frame = 0
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(updateTreeScroll)
    })
    observer.observe(scroller)
    observer.observe(list)
    onCleanup(() => {
      cancelAnimationFrame(frame)
      observer.disconnect()
    })
  },
  { flush: 'post' },
)

onMounted(loadLastImport)
defineExpose({ reloadLastImport: loadLastImport })
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

    <section
      ref="foldersGroup"
      class="group"
      :class="{ 'group--folders': space.folders.length }"
      :style="{ '--folders-head-height': `${treeSize.head}px`, '--tree-content-height': `${treeSize.content}px` }"
    >
      <header class="group__head">
        <span>目录</span>
        <button class="group__add" type="button" title="新建目录" @click="emit('createFolder', null)">
          <Icon icon="lucide:plus" />
        </button>
      </header>
      <p v-if="!space.folders.length" class="group__empty">还没有目录</p>
      <div
        v-else
        ref="treeScroll"
        class="tree-scroll scrollbar-slim"
        :class="{ 'tree-scroll--fade-top': treeFade.top, 'tree-scroll--fade-bottom': treeFade.bottom }"
        @scroll.passive="updateTreeScroll"
      >
        <ul ref="treeList" class="tree">
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
      </div>
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

    <footer class="foot">
      <router-link class="item" to="/bookmarks/organize">
        <Icon icon="lucide:list-tree" class="item__icon" />
        整理目录与标签
      </router-link>
      <router-link class="item" :to="{ path: '/bookmarks/organize', query: { tab: 'ai' } }">
        <Icon icon="lucide:sparkles" class="item__icon" />
        AI 整理
      </router-link>
      <router-link v-if="lastImport" class="foot__import" :to="{ path: '/bookmarks/organize', query: { tab: 'records' } }">
        上次导入 {{ formatDate(lastImport.createTime) }}<br />
        新增 {{ lastImport.successCount ?? 0 }} 条，跳过重复 {{ lastImport.duplicateCount ?? 0 }} 条
      </router-link>
    </footer>
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
  flex-shrink: 0;
  gap: 0.15rem;
}

/*
 * 侧栏高度受限时只有目录区收缩，目录树在自己的区域内滚动，视图、标签和底部入口保持可见。
 * 最多收缩到标题 + 约 5 行目录（目录更少时就是它本身的高度），窗口再矮就退回整个侧栏滚动，不把目录挤没。
 */
.group--folders {
  flex-shrink: 1;
  min-height: calc(var(--folders-head-height, 0px) + min(var(--tree-content-height, 0px), 12rem));
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

.foot {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 0.35rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.foot__import {
  padding: 0 0.6rem;
  font-size: 0.76rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.foot__import:hover {
  color: var(--color-brand);
}

/*
 * 目录树滚动区。滚动条（宽度见全局 .scrollbar-slim）用负右外边距挪进侧栏右侧留出的同宽内边距里（见书签页 .space__side），
 * 再用 scrollbar-gutter 常驻占位，这样有没有滚动条，目录行都和上方视图项右缘对齐。
 * 渐隐用遮罩只作用在内容列，滚动条那一列始终完整显示。
 */
@property --tree-fade-top {
  syntax: '<length>';
  inherits: false;
  initial-value: 0px;
}

@property --tree-fade-bottom {
  syntax: '<length>';
  inherits: false;
  initial-value: 0px;
}

.tree-scroll {
  min-height: 0;
  margin-right: calc(-1 * var(--scrollbar-size));
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  mask:
    linear-gradient(
        to bottom,
        transparent,
        #000 var(--tree-fade-top),
        #000 calc(100% - var(--tree-fade-bottom)),
        transparent
      )
      left / calc(100% - var(--scrollbar-size)) 100% no-repeat,
    linear-gradient(#000, #000) right / var(--scrollbar-size) 100% no-repeat;
  transition:
    --tree-fade-top var(--duration-leave) ease-out,
    --tree-fade-bottom var(--duration-leave) ease-out;
}

.tree-scroll--fade-top {
  --tree-fade-top: 1.5rem;
}

.tree-scroll--fade-bottom {
  --tree-fade-bottom: 1.5rem;
}

.tree {
  margin: 0;
  padding: 0;
  list-style: none;
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
