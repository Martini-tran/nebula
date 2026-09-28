<script setup lang="ts">
/**
 * 整理：一次性大规模整理目录与标签、让 AI 出整理建议、查看导入导出记录。
 * 日常的新建 / 改名仍在书签工作台左栏就地完成。页签同步到 ?tab=，AI 整理的两种方式同步到 ?mode=。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import FolderManager from './FolderManager.vue'
import TagManager from './TagManager.vue'
import TaskRecords from './TaskRecords.vue'
import AiBookmarks from './AiBookmarks.vue'
import AiFolders from './AiFolders.vue'
import ImportDialog from '../components/ImportDialog.vue'
import { useSpaceStore } from '../../../stores/space'

type Tab = 'folders' | 'tags' | 'ai' | 'records'

const route = useRoute()
const router = useRouter()
const space = useSpaceStore()

const tab = computed<Tab>(() => {
  const value = route.query.tab
  return value === 'tags' || value === 'ai' || value === 'records' ? value : 'folders'
})

/** AI 整理：给书签归类，或重排目录结构 */
const aiMode = computed(() => (route.query.mode === 'folders' ? 'folders' : 'bookmarks'))
const goAiMode = (mode: 'bookmarks' | 'folders') =>
  router.replace({ query: mode === 'folders' ? { tab: 'ai', mode } : { ...route.query, tab: 'ai', mode: undefined } })

const tabs = computed(() => [
  { key: 'folders' as const, label: '目录', icon: 'lucide:folder-tree', count: space.flat.length },
  { key: 'tags' as const, label: '标签', icon: 'lucide:tags', count: space.tags.length },
  { key: 'ai' as const, label: 'AI 整理', icon: 'lucide:sparkles', count: null },
  { key: 'records' as const, label: '导入导出记录', icon: 'lucide:history', count: null },
])

const go = (key: Tab) => router.replace({ query: key === 'folders' ? {} : { tab: key } })

const importOpen = ref(false)
const records = ref<InstanceType<typeof TaskRecords> | null>(null)
const onImported = async () => {
  await space.reload()
  records.value?.reload()
}

onMounted(() => space.reload())
</script>

<template>
  <div class="org page">
    <header class="org__head">
      <router-link to="/bookmarks" class="org__back"><Icon icon="lucide:arrow-left" />书签</router-link>
      <h1 class="page-title">整理</h1>
      <p class="page-subtitle">
        {{ tab === 'ai' ? 'AI 只出建议，逐项勾选后才会改动；标签只加不删。' : '拖拽调整目录结构、给标签配色与合并，改动即时保存。' }}
      </p>
    </header>

    <nav class="tabs" role="tablist" aria-label="整理内容">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        role="tab"
        :aria-selected="tab === t.key"
        :class="{ on: tab === t.key }"
        @click="go(t.key)"
      >
        <Icon :icon="t.icon" />{{ t.label }}
        <span v-if="t.count !== null" class="tabs__count">{{ t.count }}</span>
      </button>
    </nav>

    <p v-if="space.error" class="org__error">
      目录与标签加载失败：{{ space.error }}
      <button type="button" @click="space.reload()">重试</button>
    </p>

    <FolderManager v-if="tab === 'folders'" />
    <TagManager v-else-if="tab === 'tags'" />
    <template v-else-if="tab === 'ai'">
      <div class="seg" role="radiogroup" aria-label="AI 整理方式">
        <button type="button" role="radio" :aria-checked="aiMode === 'bookmarks'" :class="{ on: aiMode === 'bookmarks' }" @click="goAiMode('bookmarks')">
          <Icon icon="lucide:bookmark" />书签归类
        </button>
        <button type="button" role="radio" :aria-checked="aiMode === 'folders'" :class="{ on: aiMode === 'folders' }" @click="goAiMode('folders')">
          <Icon icon="lucide:folder-tree" />重排目录
        </button>
      </div>
      <AiFolders v-if="aiMode === 'folders'" />
      <AiBookmarks v-else :key="String(route.query.ids ?? route.query.folder ?? route.query.scope ?? '')" />
    </template>
    <TaskRecords v-else ref="records" @import="importOpen = true" />

    <ImportDialog :open="importOpen" @close="importOpen = false" @imported="onImported" @view-records="importOpen = false" />
  </div>
</template>

<style scoped>
.org {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  max-width: 64rem;
}

.org__back {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: 0.4rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.org__back:hover {
  color: var(--color-brand);
}

.tabs {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
}

.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: -1px;
  padding: 0.6rem 0.9rem;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.tabs button:hover {
  color: var(--color-text-primary);
}

.tabs button.on {
  border-bottom-color: var(--color-brand);
  color: var(--color-brand);
}

.tabs__count {
  padding: 0 0.4rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}

.seg button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.85rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.org__error {
  padding: 0.6rem 0.85rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
  color: var(--color-danger);
  font-size: 0.88rem;
}

.org__error button {
  margin-left: 0.5rem;
  border: 0;
  background: none;
  color: inherit;
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}
</style>
