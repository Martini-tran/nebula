<script setup lang="ts">
/**
 * 批量打标签：给选中的书签加上或摘掉若干标签。
 * 后端只有「整体替换一条书签的标签」接口，这里按每条原有标签算出新集合再逐条提交。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import TagPicker from './TagPicker.vue'
import { bindBookmarkTags } from '../../../api/space'
import type { Bookmark } from '../../../types/space'

const props = defineProps<{ open: boolean; bookmarks: Bookmark[] }>()
const emit = defineEmits<{ close: []; done: [changed: number, failed: number] }>()

const mode = ref<'add' | 'remove'>('add')
const tagIds = ref<string[]>([])
const running = ref(false)
const progress = ref(0)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    mode.value = 'add'
    tagIds.value = []
    progress.value = 0
  },
)

/** 每个标签在选中书签里已有几条，帮助判断要不要加 */
const usage = computed(() => {
  const map = new Map<string, number>()
  for (const b of props.bookmarks) for (const t of b.tags ?? []) map.set(String(t.id), (map.get(String(t.id)) ?? 0) + 1)
  return map
})

const plan = computed(() =>
  props.bookmarks
    .map((b) => {
      const before = (b.tags ?? []).map((t) => String(t.id))
      const after =
        mode.value === 'add'
          ? [...new Set([...before, ...tagIds.value])]
          : before.filter((id) => !tagIds.value.includes(id))
      const changed = after.length !== before.length
      return { bookmark: b, after, changed }
    })
    .filter((item) => item.changed),
)

const submit = async () => {
  if (!plan.value.length || running.value) return
  running.value = true
  progress.value = 0
  let failed = 0
  for (const item of plan.value) {
    try {
      await bindBookmarkTags(item.bookmark.id, item.after)
    } catch {
      failed += 1
    }
    progress.value += 1
  }
  running.value = false
  emit('done', plan.value.length - failed, failed)
}
</script>

<template>
  <BaseDialog :open="open" :title="`给 ${bookmarks.length} 条书签打标签`" width="30rem" :locked="running" @close="emit('close')">
    <div class="bt">
      <div class="seg" role="radiogroup" aria-label="操作">
        <button type="button" role="radio" :aria-checked="mode === 'add'" :class="{ on: mode === 'add' }" @click="mode = 'add'">
          <Icon icon="lucide:plus" />加上
        </button>
        <button type="button" role="radio" :aria-checked="mode === 'remove'" :class="{ on: mode === 'remove' }" @click="mode = 'remove'">
          <Icon icon="lucide:minus" />摘掉
        </button>
      </div>

      <TagPicker v-model="tagIds" :allow-create="mode === 'add'" :usage="usage" :usage-total="bookmarks.length" />

      <p class="bt__hint">
        <template v-if="!tagIds.length">选择要{{ mode === 'add' ? '加上' : '摘掉' }}的标签；每条书签原有的其他标签保持不变。</template>
        <template v-else-if="plan.length">将修改 {{ plan.length }} 条书签<template v-if="plan.length < bookmarks.length">，其余 {{ bookmarks.length - plan.length }} 条无需变动</template>。</template>
        <template v-else>选中的书签{{ mode === 'add' ? '都已经有这些标签' : '都没有这些标签' }}，无需修改。</template>
      </p>
    </div>

    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="running" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!plan.length || running" @click="submit">
        <Icon v-if="running" icon="lucide:loader-circle" class="spin" />
        {{ running ? `处理中 ${progress}/${plan.length}` : '应用' }}
      </button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.bt {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.35rem 0.25rem;
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.bt__hint {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}
</style>
