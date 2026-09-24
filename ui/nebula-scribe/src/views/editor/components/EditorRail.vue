<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { formatCount } from '../../../utils/format'
import type { TocGroup } from '../../../utils/toc'
import { CHAPTER_STATUS_LABEL, type EntityId } from '../../../types/work'

const props = defineProps<{
  workTitle: string
  /** 按卷分组的目录；无卷时只有一组（volume 为 null） */
  groups: TocGroup[]
  activeId: EntityId | null
  busy: boolean
}>()

const emit = defineEmits<{
  select: [id: EntityId]
  create: []
  back: []
}>()

const chapterTotal = computed(() => props.groups.reduce((sum, g) => sum + g.chapters.length, 0))
</script>

<template>
  <aside class="rail" aria-label="章节目录">
    <div class="rail__top">
      <button class="rail__back" type="button" :title="`回到《${workTitle}》`" @click="emit('back')">
        <Icon icon="lucide:chevron-left" />
        <span class="rail__work">{{ workTitle || '作品' }}</span>
      </button>
    </div>

    <p class="rail__label">章节 · {{ chapterTotal }}</p>
    <div class="rail__list">
      <section v-for="group in groups" :key="group.volume ? String(group.volume.id) : 'flat'" class="rail__group">
        <h3 v-if="group.volume" class="rail__volume" :title="group.volume.title">{{ group.volume.title }}</h3>
        <p v-if="group.volume && group.chapters.length === 0" class="rail__empty">空卷</p>
        <ol>
          <li v-for="chapter in group.chapters" :key="chapter.id">
            <button
              :class="['chap', { 'chap--active': String(chapter.id) === String(activeId) }]"
              type="button"
              :aria-current="String(chapter.id) === String(activeId) ? 'page' : undefined"
              @click="emit('select', chapter.id)"
            >
              <span class="chap__title">{{ chapter.title }}</span>
              <span class="chap__sub">
                <i :class="['dot', `dot--${chapter.status}`]" aria-hidden="true" />
                {{ CHAPTER_STATUS_LABEL[chapter.status] }} · {{ formatCount(chapter.wordCount) }} 字
              </span>
            </button>
          </li>
        </ol>
      </section>
    </div>

    <div class="rail__foot">
      <button class="btn btn--ghost rail__new" type="button" :disabled="busy" @click="emit('create')">
        <Icon icon="lucide:plus" />
        新建章节
      </button>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.rail {
  display: flex;
  flex-direction: column;
  width: 16rem;
  height: 100%;
  background: var(--color-bg-soft);
  border-right: 1px solid var(--color-border);
}

.rail__top {
  padding: 0.8rem 0.8rem 0.4rem;
}

.rail__back {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  max-width: 100%;
  padding: 0.3rem 0.45rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  font-weight: 800;
  cursor: pointer;
}

.rail__back:hover {
  background: var(--color-bg-surface);
}

.rail__back svg {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
}

.rail__work {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail__label {
  padding: 0.5rem 1.25rem 0.35rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.rail__list {
  flex: 1;
  min-height: 0;
  padding: 0 0.5rem 0.5rem;
  overflow-y: auto;
}

.rail__group + .rail__group {
  margin-top: 0.6rem;
}

.rail__volume {
  padding: 0.35rem 0.7rem 0.25rem;
  overflow: hidden;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail__empty {
  padding: 0.2rem 0.7rem 0.4rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  opacity: 0.7;
}

.chap {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0.5rem 0.7rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-leave) var(--ease-soft);
}

.chap:hover {
  background: var(--color-bg-surface);
}

.chap--active {
  background: var(--color-bg-surface);
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.chap__title {
  overflow: hidden;
  font-size: 0.9rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chap--active .chap__title {
  font-weight: 700;
}

.chap__sub {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.15rem;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.rail__foot {
  padding: 0.7rem 0.8rem;
  border-top: 1px solid var(--color-border);
}

.rail__new {
  width: 100%;
}

.rail__new svg {
  width: 1rem;
  height: 1rem;
}

.dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 999px;
  background: var(--color-text-secondary);
}

.dot--outline {
  background: transparent;
  box-shadow: inset 0 0 0 1.5px var(--color-text-secondary);
}

.dot--drafting {
  background: #d97706;
}

.dot--revising {
  background: var(--color-brand);
}

.dot--done {
  background: #16a34a;
}
</style>
