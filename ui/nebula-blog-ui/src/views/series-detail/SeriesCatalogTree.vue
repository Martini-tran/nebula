<script setup lang="ts">
import { computed } from 'vue'
import type { SeriesCatalogNode, SeriesChapter } from '../../api/series'

const props = defineProps<{
  nodes: SeriesCatalogNode[]
  /** 当前选中的文章 slug */
  activeSlug?: string
  /** 缩进层级，递归时 +1 */
  depth?: number
}>()

const emit = defineEmits<{
  (e: 'select', chapter: SeriesChapter): void
}>()

/**
 * 顶层仅有一个「内容集合」节点（非链接、无子目录、带文章）时，
 * 这层分组标题是冗余的——直接平铺其文章，避免「目录 + 文章」两层。
 */
const unwrapSingle = computed(() => {
  if (props.depth || props.nodes.length !== 1) return false
  const only = props.nodes[0]
  return (
    !!only &&
    only.node_type !== 2 &&
    !only.children?.length &&
    (only.posts?.length ?? 0) > 0
  )
})

const onPick = (chapter: SeriesChapter) => {
  if (chapter.status !== 'published') return
  emit('select', chapter)
}
</script>

<template>
  <ul class="catalog-tree" :class="{ 'catalog-tree--root': !depth }">
    <li
      v-for="node in nodes"
      :key="`node-${node.id}`"
      class="catalog-node"
    >
      <!-- 目录节点：链接型直接当外链，其它显示为分组标题；单一集合时隐藏标题 -->
      <a
        v-if="node.node_type === 2 && node.link_url"
        class="catalog-node__title catalog-node__title--link"
        :href="node.link_url"
        :target="node.link_target || '_self'"
        rel="noopener"
      >
        <span class="catalog-node__chevron" aria-hidden="true">›</span>
        {{ node.title }}
      </a>
      <div v-else-if="!unwrapSingle" class="catalog-node__title">
        <span class="catalog-node__chevron" aria-hidden="true">▸</span>
        {{ node.title }}
      </div>

      <!-- 该节点下挂的文章列表 -->
      <ul
        v-if="node.posts?.length"
        class="catalog-posts"
        :class="{ 'catalog-posts--flat': unwrapSingle }"
      >
        <li
          v-for="post in node.posts"
          :key="`post-${post.post_id}`"
          class="catalog-post"
          :class="{
            'catalog-post--active': activeSlug === post.slug,
            'catalog-post--draft': post.status !== 'published',
          }"
        >
          <button
            type="button"
            class="catalog-post__btn"
            :disabled="post.status !== 'published'"
            @click="onPick(post)"
          >
            <span class="catalog-post__dot" aria-hidden="true" />
            <span class="catalog-post__title">{{ post.title }}</span>
            <span
              v-if="post.status !== 'published'"
              class="catalog-post__badge"
            >整理中</span>
          </button>
        </li>
      </ul>

      <!-- 递归渲染子目录 -->
      <SeriesCatalogTree
        v-if="node.children?.length"
        :nodes="node.children"
        :active-slug="activeSlug"
        :depth="(depth ?? 0) + 1"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>

<style scoped>
.catalog-tree {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.catalog-tree:not(.catalog-tree--root) {
  margin-left: 0.85rem;
  padding-left: 0.5rem;
  border-left: 1px dashed var(--color-border);
}

.catalog-node {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.catalog-node__title {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.5rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  text-decoration: none;
  border-radius: 0.5rem;
}

.catalog-node__title--link {
  text-transform: none;
  letter-spacing: 0.02em;
  color: var(--color-text-secondary);
  transition: color 0.15s, background 0.15s;
}

.catalog-node__title--link:hover {
  color: var(--color-accent-text);
  background: var(--color-bg-soft);
}

.catalog-node__chevron {
  color: var(--color-accent);
  font-size: 0.75rem;
}

.catalog-posts {
  list-style: none;
  margin: 0;
  padding: 0 0 0 0.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

/* 单一集合：标题已隐藏，文章直接平铺，去掉额外缩进 */
.catalog-posts--flat {
  padding-left: 0;
}

.catalog-post__btn {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem 0.55rem;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.catalog-post__btn:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-left-color: color-mix(in srgb, var(--color-accent) 40%, transparent);
}

.catalog-post__btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.catalog-post--active .catalog-post__btn {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-left-color: var(--color-accent);
}

.catalog-post__dot {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.4;
}

.catalog-post--active .catalog-post__dot {
  opacity: 1;
}

.catalog-post__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-post__badge {
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--color-text-muted);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
}
</style>
