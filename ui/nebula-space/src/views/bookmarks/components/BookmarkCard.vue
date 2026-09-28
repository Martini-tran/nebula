<script setup lang="ts">
/**
 * 网格视图的书签卡片。
 * 点标题 = 新标签页打开（最高频，不能多一步）；点卡片空白处 = 打开详情抽屉；
 * 已有选中项时，点卡片空白处改为切换选中。
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import FaviconMark from './FaviconMark.vue'
import { BookmarkStatus, type Bookmark } from '../../../types/space'
import { formatRelative } from '../../../utils/format'

const props = defineProps<{ bookmark: Bookmark; selected: boolean; selecting: boolean }>()
const emit = defineEmits<{
  open: []
  select: [range: boolean]
  edit: []
  move: []
  toggleArchive: []
  remove: []
}>()

const host = computed(() => props.bookmark.domain || props.bookmark.url.replace(/^\w+:\/\//, '').split('/')[0])
const archived = computed(() => props.bookmark.status === BookmarkStatus.ARCHIVED)
const broken = computed(() => props.bookmark.status === BookmarkStatus.BROKEN)

const onCardClick = (event: MouseEvent) => {
  if ((event.target as HTMLElement).closest('a, button, input')) return
  if (props.selecting || event.shiftKey || event.ctrlKey || event.metaKey) emit('select', event.shiftKey)
  else emit('open')
}
</script>

<template>
  <article
    class="card surface"
    :class="{ 'card--muted': archived || broken, 'card--selected': selected, 'card--selecting': selecting }"
    @click="onCardClick"
  >
    <input
      class="card__check"
      type="checkbox"
      :checked="selected"
      :aria-label="`选择 ${bookmark.title}`"
      @click.stop="emit('select', ($event as MouseEvent).shiftKey)"
    />
    <a class="card__main" :href="bookmark.url" target="_blank" rel="noopener noreferrer" :title="bookmark.url">
      <FaviconMark :bookmark="bookmark" />
      <span class="card__text">
        <span class="card__title">{{ bookmark.title }}</span>
        <span class="card__host">{{ host }}</span>
      </span>
    </a>

    <p v-if="bookmark.description" class="card__desc">{{ bookmark.description }}</p>

    <footer class="card__foot">
      <div class="card__tags">
        <span v-if="broken" class="tag card__broken" :title="bookmark.checkResult ?? undefined">已失效</span>
        <span v-for="tag in bookmark.tags ?? []" :key="tag.id" class="tag">
          <span class="card__dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />
          {{ tag.name }}
        </span>
        <span v-if="!bookmark.tags?.length && !broken" class="card__time">
          {{ formatRelative(bookmark.createTime) }}
        </span>
      </div>
      <div class="card__actions">
        <button type="button" title="编辑" @click="emit('edit')"><Icon icon="lucide:pencil" /></button>
        <button type="button" title="移动到…" @click="emit('move')"><Icon icon="lucide:folder-input" /></button>
        <button type="button" :title="archived ? '恢复' : '归档'" @click="emit('toggleArchive')">
          <Icon :icon="archived ? 'lucide:archive-restore' : 'lucide:archive'" />
        </button>
        <button type="button" title="删除" class="card__danger" @click="emit('remove')">
          <Icon icon="lucide:trash-2" />
        </button>
      </div>
    </footer>
  </article>
</template>

<style scoped lang="scss">
.card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 1rem 0.7rem;
  min-width: 0;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s var(--ease-soft);
}

.card:hover {
  border-color: color-mix(in srgb, var(--color-brand) 45%, var(--color-border));
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.card--muted {
  opacity: 0.72;
}

.card--selected,
.card--selected:hover {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 2px var(--color-brand-soft);
  opacity: 1;
}

.card__check {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  width: 1rem;
  height: 1rem;
  margin: 0;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.card:hover .card__check,
.card__check:focus-visible,
.card--selecting .card__check {
  opacity: 1;
}

@media (hover: none) {
  .card__check {
    opacity: 1;
  }
}

.card__main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  padding-right: 1.2rem;
  align-self: flex-start;
  max-width: 100%;
}

.card__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card__title,
.card__host {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__title {
  font-weight: 700;
}

.card__main:hover .card__title {
  color: var(--color-brand);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.card__host {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.card__desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 0.85rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: auto;
}

.card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  min-width: 0;
}

.card__broken {
  color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
}

.card__dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
}

.card__time {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.card__actions {
  display: flex;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.card:hover .card__actions,
.card:focus-within .card__actions {
  opacity: 1;
}

.card--selecting .card__actions {
  visibility: hidden;
}

@media (hover: none) {
  .card__actions {
    opacity: 1;
  }
}

.card__actions button {
  display: inline-grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.card__actions button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.card__actions .card__danger:hover {
  color: var(--color-danger);
}
</style>
