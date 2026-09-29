<script setup lang="ts">
/**
 * 设定条目的查看态：名称、类型、别名、概述、标签与 Markdown 详细设定。
 * 删除走就地二次确认，不弹窗。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useThemeStore } from '../../../stores/theme'
import { formatRelative } from '../../../utils/format'
import { LORE_KIND_ICON, LORE_KIND_LABEL, type LoreEntry } from '../../../types/lore'

const props = defineProps<{
  entry: LoreEntry
  deleting?: boolean
  deleteError?: string
}>()

const emit = defineEmits<{
  edit: []
  delete: []
}>()

const themeStore = useThemeStore()
const previewTheme = computed<'light' | 'dark'>(() => (themeStore.currentTheme === 'dark' ? 'dark' : 'light'))

const confirming = ref(false)
watch(
  () => props.entry.id,
  () => (confirming.value = false),
)
</script>

<template>
  <article class="view" aria-labelledby="lore-view-title">
    <header class="view__head">
      <p class="view__kind">
        <span class="tag tag--brand">
          <Icon :icon="LORE_KIND_ICON[entry.kind]" />
          {{ LORE_KIND_LABEL[entry.kind] }}
        </span>
        <span v-if="entry.pinned" class="tag tag--accent">
          <Icon icon="lucide:pin" />
          固定
        </span>
      </p>
      <h2 id="lore-view-title" class="view__name">{{ entry.name }}</h2>
      <p v-if="entry.aliases?.length" class="view__aliases">又名 {{ entry.aliases.join('、') }}</p>
    </header>

    <p v-if="entry.summary" class="view__summary">{{ entry.summary }}</p>

    <ul v-if="entry.tags?.length" class="view__tags" aria-label="标签">
      <li v-for="tag in entry.tags" :key="tag" class="tag">{{ tag }}</li>
    </ul>

    <section class="view__detail" aria-label="详细设定">
      <MdPreview v-if="entry.detail" :model-value="entry.detail" :theme="previewTheme" class="md" />
      <p v-else class="view__empty">还没有详细设定，点「编辑」补上外貌、动机、秘密等。</p>
    </section>

    <footer class="view__foot">
      <span v-if="entry.updateTime" class="view__time">{{ formatRelative(entry.updateTime) }}更新</span>

      <template v-if="!confirming">
        <button class="btn btn--ghost danger" type="button" @click="confirming = true">
          <Icon icon="lucide:trash-2" />
          删除
        </button>
        <button class="btn btn--primary" type="button" @click="emit('edit')">
          <Icon icon="lucide:pencil" />
          编辑
        </button>
      </template>
      <div v-else class="confirm">
        <span class="confirm__ask">确定删除「{{ entry.name }}」？</span>
        <button class="btn btn--ghost" type="button" :disabled="deleting" @click="confirming = false">取消</button>
        <button class="btn danger danger--solid" type="button" :disabled="deleting" @click="emit('delete')">
          <Icon v-if="deleting" icon="lucide:loader-circle" class="spin" />
          {{ deleting ? '删除中…' : '确认删除' }}
        </button>
      </div>
    </footer>
    <p v-if="deleteError" class="form__error view__error" role="alert">{{ deleteError }}</p>
  </article>
</template>

<style scoped lang="scss">
.view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem;
}

.view__kind {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.view__kind svg {
  width: 0.85rem;
  height: 0.85rem;
}

.view__name {
  margin-top: 0.6rem;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.view__aliases {
  margin-top: 0.25rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.view__summary {
  padding: 0.75rem 0.9rem;
  border-left: 3px solid var(--color-brand);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-bg-soft);
  line-height: 1.7;
}

.view__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.view__detail {
  padding-top: 0.25rem;
  border-top: 1px solid var(--color-border);
}

.view__empty {
  padding: 1rem 0;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.md {
  background: transparent;
}

.md :deep(.md-editor-preview-wrapper) {
  padding: 0.5rem 0 0;
}

.md :deep(.md-editor-preview) {
  font-size: 0.95rem;
  line-height: 1.85;
  color: var(--color-text-primary);
}

/* md-editor 自带的标题色偏冷灰，与纸墨配色不搭 */
.md :deep(:is(h1, h2, h3, h4, h5, h6)) {
  color: var(--color-text-primary);
}

/* Tailwind 的 preflight 会清掉列表符号，设定里的条目列表要恢复序号/圆点 */
.md :deep(ol) {
  list-style: decimal;
}

.md :deep(ul) {
  list-style: disc;
}

.view__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.6rem;
  padding-top: 0.5rem;
}

.view__foot svg {
  width: 1rem;
  height: 1rem;
}

.view__time {
  margin-right: auto;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.confirm {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.confirm__ask {
  font-size: 0.9rem;
  font-weight: 600;
}

.danger {
  color: #dc2626;
}

.danger--solid {
  color: #fff;
  background: #dc2626;
}

.danger--solid:not(:disabled):hover {
  background: #b91c1c;
}

.view__error {
  text-align: right;
}
</style>
