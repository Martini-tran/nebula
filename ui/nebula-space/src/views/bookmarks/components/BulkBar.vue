<script setup lang="ts">
/**
 * 批量操作条：有选中项时浮在列表顶部（吸顶），深色以区别于普通工具栏。
 * 移动 / 删除走批量接口；打标签、归档由页面逐条调用。
 */
import { Icon } from '@iconify/vue'

defineProps<{
  count: number
  /** 本页条数，用于「全选本页」 */
  pageCount: number
  /** 当前是「已归档」视图：归档按钮变成「恢复」 */
  restoring: boolean
  busy: boolean
}>()
const emit = defineEmits<{
  move: []
  tag: []
  archive: []
  remove: []
  selectPage: []
  clear: []
}>()
</script>

<template>
  <div class="bulk" role="toolbar" aria-label="批量操作">
    <span class="bulk__count">
      已选 <b>{{ count }}</b> 条
      <button v-if="count < pageCount" type="button" class="bulk__link" @click="emit('selectPage')">全选本页 {{ pageCount }} 条</button>
    </span>
    <span class="bulk__acts">
      <button type="button" :disabled="busy" @click="emit('move')"><Icon icon="lucide:folder-input" /><span>移动到…</span></button>
      <button type="button" :disabled="busy" @click="emit('tag')"><Icon icon="lucide:tags" /><span>打标签</span></button>
      <button type="button" :disabled="busy" @click="emit('archive')">
        <Icon :icon="restoring ? 'lucide:archive-restore' : 'lucide:archive'" /><span>{{ restoring ? '恢复' : '归档' }}</span>
      </button>
      <button type="button" class="bulk__danger" :disabled="busy" @click="emit('remove')">
        <Icon icon="lucide:trash-2" /><span>删除</span>
      </button>
      <button type="button" class="bulk__close" title="取消选择（Esc）" @click="emit('clear')"><Icon icon="lucide:x" /></button>
    </span>
  </div>
</template>

<style scoped>
.bulk {
  position: sticky;
  top: calc(var(--header-height) + 0.5rem);
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  padding: 0.5rem 0.6rem 0.5rem 1rem;
  border-radius: var(--radius-lg);
  background: var(--color-bg-elevated);
  color: #f3f4f6;
  box-shadow: var(--shadow-md);
  font-size: 0.9rem;
}

.bulk__count b {
  font-size: 1rem;
}

.bulk__link {
  margin-left: 0.6rem;
  border: 0;
  background: none;
  color: #a5b4fc;
  font-weight: 600;
  cursor: pointer;
}

.bulk__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.bulk__acts button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.7rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: inherit;
  font-weight: 600;
  cursor: pointer;
}

.bulk__acts button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
}

.bulk__acts button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.bulk__acts svg {
  width: 1rem;
  height: 1rem;
}

.bulk__acts .bulk__danger {
  color: #fca5a5;
}

.bulk__close {
  margin-left: 0.25rem;
}

@media (max-width: 640px) {
  .bulk__acts span {
    display: none;
  }
}
</style>
