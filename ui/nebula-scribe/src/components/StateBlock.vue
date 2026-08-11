<script setup lang="ts">
/**
 * 通用状态块：加载中 / 出错 / 空列表 三态复用同一套排版，
 * 避免每个列表页各写一遍。
 */
import { Icon } from '@iconify/vue'

withDefaults(
  defineProps<{
    state: 'loading' | 'error' | 'empty'
    title?: string
    description?: string
    /** 空态下的操作按钮文案，留空则不渲染按钮 */
    actionLabel?: string
  }>(),
  { title: '', description: '', actionLabel: '' },
)

defineEmits<{ action: [] }>()

const preset = {
  loading: { icon: 'lucide:loader-circle', title: '加载中…' },
  error: { icon: 'lucide:triangle-alert', title: '加载失败' },
  empty: { icon: 'lucide:inbox', title: '这里还没有内容' },
} as const
</script>

<template>
  <div class="state" role="status" aria-live="polite">
    <Icon
      :icon="preset[state].icon"
      class="state__icon"
      :class="{ 'state__icon--spin': state === 'loading' }"
    />
    <p class="state__title">{{ title || preset[state].title }}</p>
    <p v-if="description" class="state__desc">{{ description }}</p>
    <button v-if="actionLabel" class="btn btn--primary" type="button" @click="$emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 3.5rem 1rem;
  text-align: center;
  color: var(--color-text-secondary);
}

.state__icon {
  width: 2.25rem;
  height: 2.25rem;
  color: var(--color-text-secondary);
  opacity: 0.7;
}

.state__icon--spin {
  animation: state-spin 1s linear infinite;
}

.state__title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.state__desc {
  max-width: 26rem;
  font-size: 0.9rem;
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .state__icon--spin {
    animation: none;
  }
}
</style>
