<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { BookmarkStatus, type Bookmark } from '../../../types/space'
import { formatRelative } from '../../../utils/format'

const props = defineProps<{ bookmark: Bookmark }>()
const emit = defineEmits<{ edit: []; toggleArchive: []; remove: [] }>()

/** 图标加载失败就退回首字母头像，避免破图 */
const faviconBroken = ref(false)

const host = computed(() => {
  if (props.bookmark.domain) return props.bookmark.domain
  try {
    return new URL(props.bookmark.url).hostname
  } catch {
    return props.bookmark.url
  }
})

const initial = computed(() => (props.bookmark.title || host.value).trim().charAt(0).toUpperCase())
const archived = computed(() => props.bookmark.status === BookmarkStatus.ARCHIVED)
const broken = computed(() => props.bookmark.status === BookmarkStatus.BROKEN)
</script>

<template>
  <article class="card surface" :class="{ 'card--muted': archived || broken }">
    <a class="card__main" :href="bookmark.url" target="_blank" rel="noopener noreferrer">
      <span class="card__icon">
        <img
          v-if="bookmark.faviconUrl && !faviconBroken"
          :src="bookmark.faviconUrl"
          alt=""
          loading="lazy"
          @error="faviconBroken = true"
        />
        <span v-else>{{ initial }}</span>
      </span>
      <span class="card__text">
        <span class="card__title">{{ bookmark.title }}</span>
        <span class="card__host">{{ host }}</span>
      </span>
    </a>

    <p v-if="bookmark.description" class="card__desc">{{ bookmark.description }}</p>

    <footer class="card__foot">
      <div class="card__tags">
        <span v-if="broken" class="tag">已失效</span>
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
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 1rem 0.7rem;
  min-width: 0;
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

.card__main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.card__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 800;
  overflow: hidden;
}

.card__icon img {
  width: 1.35rem;
  height: 1.35rem;
  object-fit: contain;
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
