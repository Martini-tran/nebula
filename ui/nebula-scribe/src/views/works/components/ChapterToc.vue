<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { createChapter, deleteChapter, fetchChapters, sortChapters } from '../../../api/work'
import StateBlock from '../../../components/StateBlock.vue'
import { formatCount } from '../../../utils/format'
import { CHAPTER_STATUS_LABEL, type ChapterListItem, type EntityId } from '../../../types/work'

const props = defineProps<{ workId: EntityId }>()

/** 章节增删后把章节数、字数交给父页面，作品头部的统计不必整页重载 */
const emit = defineEmits<{ stats: [stats: { chapterCount: number; wordCount: number }] }>()

const router = useRouter()

const chapters = ref<ChapterListItem[]>([])
const loading = ref(true)
const loadError = ref('')
const busy = ref(false)
const actionError = ref('')
/** 正在确认删除的章节 id，同一时间只确认一条 */
const confirmingId = ref<string | null>(null)

const emitStats = () => {
  emit('stats', {
    chapterCount: chapters.value.length,
    wordCount: chapters.value.reduce((sum, c) => sum + (c.wordCount ?? 0), 0),
  })
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    chapters.value = await fetchChapters(props.workId)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => props.workId, load, { immediate: true })

const run = async (task: () => Promise<void>, fallback: string) => {
  busy.value = true
  actionError.value = ''
  try {
    await task()
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : fallback
  } finally {
    busy.value = false
  }
}

const openEditor = (chapterId: EntityId) =>
  router.push({ name: 'editor', params: { workId: String(props.workId), chapterId: String(chapterId) } })

/** 新建后直接进写作台：新章节的下一步几乎总是开始写 */
const create = () =>
  run(async () => {
    const created = await createChapter(props.workId)
    await openEditor(created.id)
  }, '新建章节失败，请稍后重试')

const move = (index: number, offset: -1 | 1) =>
  run(async () => {
    const ids = chapters.value.map((c) => c.id)
    const target = index + offset
    ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
    chapters.value = await sortChapters(props.workId, ids)
  }, '调整顺序失败，请稍后重试')

const remove = (chapter: ChapterListItem) =>
  run(async () => {
    await deleteChapter(props.workId, chapter.id)
    chapters.value = chapters.value.filter((c) => c.id !== chapter.id)
    confirmingId.value = null
    emitStats()
  }, '删除失败，请稍后重试')
</script>

<template>
  <section class="panel surface toc" aria-labelledby="toc-title">
    <header class="toc__head">
      <h2 id="toc-title" class="toc__title">章节目录</h2>
      <button v-if="chapters.length > 0" class="btn btn--quiet toc__add" type="button" :disabled="busy" @click="create">
        <Icon icon="lucide:plus" />
        新建章节
      </button>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
    <StateBlock
      v-else-if="chapters.length === 0"
      state="empty"
      title="还没有章节"
      description="从第一章开始写吧。"
      action-label="新建第一章"
      @action="create"
    />

    <ol v-else class="toc__list">
      <li v-for="(chapter, index) in chapters" :key="chapter.id" class="chap">
        <template v-if="confirmingId === String(chapter.id)">
          <p class="chap__ask">删除「{{ chapter.title }}」？</p>
          <div class="chap__confirm">
            <button class="btn btn--ghost btn--sm" type="button" :disabled="busy" @click="confirmingId = null">取消</button>
            <button class="btn btn--sm chap__danger" type="button" :disabled="busy" @click="remove(chapter)">删除</button>
          </div>
        </template>
        <template v-else>
          <button class="chap__main" type="button" @click="openEditor(chapter.id)">
            <span class="chap__title">{{ chapter.title }}</span>
            <span class="chap__sub">
              <i :class="['dot', `dot--${chapter.status}`]" aria-hidden="true" />
              {{ CHAPTER_STATUS_LABEL[chapter.status] }} · {{ formatCount(chapter.wordCount) }} 字
            </span>
          </button>
          <div class="chap__ops">
            <button
              class="icon-btn"
              type="button"
              title="上移"
              aria-label="上移"
              :disabled="busy || index === 0"
              @click="move(index, -1)"
            >
              <Icon icon="lucide:chevron-up" />
            </button>
            <button
              class="icon-btn"
              type="button"
              title="下移"
              aria-label="下移"
              :disabled="busy || index === chapters.length - 1"
              @click="move(index, 1)"
            >
              <Icon icon="lucide:chevron-down" />
            </button>
            <button
              class="icon-btn icon-btn--danger"
              type="button"
              title="删除"
              aria-label="删除"
              :disabled="busy"
              @click="confirmingId = String(chapter.id)"
            >
              <Icon icon="lucide:trash-2" />
            </button>
          </div>
        </template>
      </li>
    </ol>

    <p v-if="actionError" class="form__error toc__error" role="alert">{{ actionError }}</p>
  </section>
</template>

<style scoped lang="scss">
.toc {
  padding-bottom: 0.75rem;
}

.toc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 1.1rem 0.9rem 0 1.35rem;
}

.toc__title {
  font-size: 1.05rem;
  font-weight: 700;
}

.toc__add {
  padding: 0.3rem 0.5rem;
  font-size: 0.85rem;
}

.toc__add svg {
  width: 0.95rem;
  height: 0.95rem;
}

.toc__list {
  display: flex;
  flex-direction: column;
  max-height: 26rem;
  margin-top: 0.6rem;
  padding: 0 0.6rem;
  overflow-y: auto;
}

.chap {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  transition: background var(--duration-leave) var(--ease-soft);
}

.chap:hover {
  background: var(--color-bg-soft);
}

.chap__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 0.45rem 0.5rem;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}

.chap__title {
  overflow: hidden;
  font-size: 0.92rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chap__sub {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.15rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

/* 操作按钮悬停才显示，目录平时保持安静；键盘聚焦时也要能看到 */
.chap__ops {
  display: flex;
  opacity: 0;
  transition: opacity var(--duration-leave) var(--ease-soft);
}

.chap:hover .chap__ops,
.chap:focus-within .chap__ops {
  opacity: 1;
}

@media (hover: none) {
  .chap__ops {
    opacity: 1;
  }
}

.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-btn:not(:disabled):hover {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
}

.icon-btn:disabled {
  opacity: 0.35;
  cursor: default;
}

.icon-btn--danger:not(:disabled):hover {
  color: #dc2626;
}

.icon-btn svg {
  width: 1rem;
  height: 1rem;
}

.chap__ask {
  flex: 1;
  min-width: 0;
  padding: 0.45rem 0.5rem;
  overflow: hidden;
  font-size: 0.88rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chap__confirm {
  display: flex;
  gap: 0.35rem;
}

.btn--sm {
  padding: 0.3rem 0.6rem;
  font-size: 0.82rem;
}

.chap__danger {
  color: #fff;
  background: #dc2626;
}

.chap__danger:not(:disabled):hover {
  background: #b91c1c;
}

.toc__error {
  margin: 0.5rem 1.35rem 0;
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
