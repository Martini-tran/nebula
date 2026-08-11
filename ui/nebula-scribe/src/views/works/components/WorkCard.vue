<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { formatCount, formatRelative } from '../../../utils/format'
import { WORK_STATUS_LABEL, type WorkListItem } from '../../../types/work'

const props = defineProps<{ work: WorkListItem }>()

const router = useRouter()

const progress = computed(() => {
  const target = props.work.targetWordCount
  if (!target || target <= 0) return null
  return Math.min(1, props.work.wordCount / target)
})

const statusTone = computed(() => {
  switch (props.work.status) {
    case 'serializing':
      return 'tag--brand'
    case 'finished':
      return 'tag--accent'
    default:
      return ''
  }
})
</script>

<template>
  <article class="work surface" @click="router.push(`/works/${work.id}`)">
    <header class="work__head">
      <h3 class="work__title">{{ work.title }}</h3>
      <span class="tag" :class="statusTone">{{ WORK_STATUS_LABEL[work.status] }}</span>
    </header>

    <p class="work__summary">{{ work.summary || '还没有写简介。' }}</p>

    <div class="work__tags">
      <span v-if="work.genre" class="tag">{{ work.genre }}</span>
      <span v-for="tag in work.tags ?? []" :key="tag" class="tag">{{ tag }}</span>
    </div>

    <div v-if="progress !== null" class="work__progress" :title="`目标 ${formatCount(work.targetWordCount)} 字`">
      <div class="work__progress-bar">
        <span :style="{ width: `${progress * 100}%` }" />
      </div>
      <span class="work__progress-text">{{ Math.round(progress * 100) }}%</span>
    </div>

    <footer class="work__meta">
      <span><Icon icon="lucide:file-text" />{{ formatCount(work.wordCount) }} 字</span>
      <span><Icon icon="lucide:list" />{{ work.chapterCount }} 章</span>
      <span><Icon icon="lucide:clock" />{{ formatRelative(work.updateTime) }}</span>
    </footer>

    <button
      class="btn btn--primary work__cta"
      type="button"
      @click.stop="router.push(`/editor/${work.id}`)"
    >
      <Icon icon="lucide:pen-line" />
      继续写
    </button>
  </article>
</template>

<style scoped lang="scss">
.work {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 1.25rem;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.work:hover {
  transform: translateY(-2px);
  border-color: var(--color-brand);
  box-shadow: var(--shadow-md);
}

.work:hover .work__cta {
  opacity: 1;
}

.work__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.work__title {
  font-size: 1.1rem;
  font-weight: 700;
}

.work__summary {
  font-size: 0.92rem;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.work__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.work__progress {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.work__progress-bar {
  flex: 1;
  height: 0.35rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  overflow: hidden;
}

.work__progress-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-brand);
}

.work__progress-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.work__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
  margin-top: auto;
  padding-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.work__meta span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.work__meta svg {
  width: 0.9rem;
  height: 0.9rem;
}

.work__cta {
  margin-top: 0.3rem;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.work__cta svg {
  width: 1rem;
  height: 1rem;
}

/* 触屏设备没有 hover，操作按钮常驻 */
@media (hover: none) {
  .work__cta {
    opacity: 1;
  }
}
</style>
