<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import {
  createChapter,
  createVolume,
  deleteChapter,
  deleteVolume,
  fetchToc,
  saveToc,
  updateVolume,
} from '../../../api/work'
import StateBlock from '../../../components/StateBlock.vue'
import { formatCount } from '../../../utils/format'
import {
  canMoveChapter,
  describeVolumeRemoval,
  groupToc,
  moveChapter,
  moveVolume,
  sameId,
  toSortRequest,
  type TocGroup,
} from '../../../utils/toc'
import { CHAPTER_STATUS_LABEL, type ChapterListItem, type EntityId, type Toc, type Volume } from '../../../types/work'

const props = defineProps<{ workId: EntityId }>()

/** 章节增删后把章节数、字数交给父页面，作品头部的统计不必整页重载 */
const emit = defineEmits<{ stats: [stats: { chapterCount: number; wordCount: number }] }>()

const router = useRouter()

const toc = ref<Toc>({ volumes: [], chapters: [] })
const loading = ref(true)
const loadError = ref('')
const busy = ref(false)
const actionError = ref('')

/** 正在确认删除的对象，同一时间只确认一个 */
const confirming = ref<{ kind: 'chapter' | 'volume'; id: string } | null>(null)
/** 正在改名的卷 */
const renaming = reactive<{ id: string | null; title: string }>({ id: null, title: '' })
const renameInput = ref<HTMLInputElement[]>([])
/** 折叠起来的卷 */
const collapsed = ref(new Set<string>())

const groups = computed(() => groupToc(toc.value))
const hasVolumes = computed(() => toc.value.volumes.length > 0)
const isEmpty = computed(() => toc.value.chapters.length === 0 && !hasVolumes.value)

const isConfirming = (kind: 'chapter' | 'volume', id: EntityId) =>
  confirming.value?.kind === kind && confirming.value.id === String(id)

const emitStats = () => {
  emit('stats', {
    chapterCount: toc.value.chapters.length,
    wordCount: toc.value.chapters.reduce((sum, c) => sum + (c.wordCount ?? 0), 0),
  })
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    toc.value = await fetchToc(props.workId)
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
    // 409 说明目录在别处变了，顺手拉一次最新的，免得作者对着旧目录继续操作
    await load()
  } finally {
    busy.value = false
  }
}

const openEditor = (chapterId: EntityId) =>
  router.push({ name: 'editor', params: { workId: String(props.workId), chapterId: String(chapterId) } })

/** 新建后直接进写作台：新章节的下一步几乎总是开始写 */
const addChapter = (volumeId?: EntityId) =>
  run(async () => {
    const created = await createChapter(props.workId, { volumeId })
    await openEditor(created.id)
  }, '新建章节失败，请稍后重试')

/** 分卷 / 新建卷：第一卷会把现有章节全部收进去；建好后直接进入改名 */
const addVolume = () =>
  run(async () => {
    const created = await createVolume(props.workId)
    toc.value = await fetchToc(props.workId)
    await startRename(created)
  }, '新建卷失败，请稍后重试')

const startRename = async (volume: Volume) => {
  confirming.value = null
  renaming.id = String(volume.id)
  renaming.title = volume.title
  await nextTick()
  renameInput.value[0]?.select()
}

const commitRename = (volume: Volume) => {
  // 回车提交后输入框卸载还会补一次 blur，Esc 取消也一样，只认仍在编辑中的那一次
  if (renaming.id !== String(volume.id)) return
  const title = renaming.title.trim()
  renaming.id = null
  if (!title || title === volume.title) return
  return run(async () => {
    const saved = await updateVolume(props.workId, volume.id, { title, synopsis: volume.synopsis })
    toc.value = {
      ...toc.value,
      volumes: toc.value.volumes.map((v) => (sameId(v.id, saved.id) ? saved : v)),
    }
  }, '改名失败，请稍后重试')
}

const applyOrder = (next: TocGroup[] | null) => {
  if (!next) return
  return run(async () => {
    toc.value = await saveToc(props.workId, toSortRequest(next))
  }, '调整顺序失败，请稍后重试')
}

const removeVolume = (volume: Volume) =>
  run(async () => {
    toc.value = await deleteVolume(props.workId, volume.id)
    confirming.value = null
  }, '删除失败，请稍后重试')

const removeChapter = (chapter: ChapterListItem) =>
  run(async () => {
    await deleteChapter(props.workId, chapter.id)
    toc.value = { ...toc.value, chapters: toc.value.chapters.filter((c) => !sameId(c.id, chapter.id)) }
    confirming.value = null
    emitStats()
  }, '删除失败，请稍后重试')

const toggle = (volumeId: EntityId) => {
  const next = new Set(collapsed.value)
  const key = String(volumeId)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  collapsed.value = next
}
</script>

<template>
  <section class="panel surface toc" aria-labelledby="toc-title">
    <header class="toc__head">
      <h2 id="toc-title" class="toc__title">章节目录</h2>
      <button v-if="!isEmpty" class="btn btn--quiet toc__add" type="button" :disabled="busy" @click="addChapter()">
        <Icon icon="lucide:plus" />
        新建章节
      </button>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
    <StateBlock
      v-else-if="isEmpty"
      state="empty"
      title="还没有章节"
      description="从第一章开始写吧。"
      action-label="新建第一章"
      @action="addChapter()"
    />

    <div v-else class="toc__body">
      <section
        v-for="(group, gi) in groups"
        :key="group.volume ? String(group.volume.id) : 'flat'"
        class="vol"
        :aria-label="group.volume?.title ?? '章节'"
      >
        <!-- 卷头：无卷时不渲染 -->
        <div v-if="group.volume" class="vol__head">
          <template v-if="isConfirming('volume', group.volume.id)">
            <div class="confirm">
              <p class="confirm__ask">删除「{{ group.volume.title }}」？</p>
              <p class="confirm__hint">{{ describeVolumeRemoval(groups, group.volume.id) }}</p>
              <div class="confirm__actions">
                <button class="btn btn--ghost btn--sm" type="button" :disabled="busy" @click="confirming = null">取消</button>
                <button class="btn btn--sm danger" type="button" :disabled="busy" @click="removeVolume(group.volume)">
                  删除卷
                </button>
              </div>
            </div>
          </template>
          <template v-else>
            <button
              class="vol__toggle"
              type="button"
              :aria-expanded="!collapsed.has(String(group.volume.id))"
              @click="toggle(group.volume.id)"
            >
              <Icon
                icon="lucide:chevron-down"
                :class="['vol__chevron', { 'vol__chevron--closed': collapsed.has(String(group.volume.id)) }]"
              />
              <input
                v-if="renaming.id === String(group.volume.id)"
                ref="renameInput"
                v-model="renaming.title"
                class="vol__rename"
                maxlength="100"
                aria-label="卷名"
                @click.stop
                @keydown.enter.prevent="commitRename(group.volume)"
                @keydown.esc.prevent="renaming.id = null"
                @blur="commitRename(group.volume)"
              />
              <span v-else class="vol__title">{{ group.volume.title }}</span>
            </button>
            <span class="vol__meta">{{ group.chapters.length }} 章 · {{ formatCount(group.wordCount) }} 字</span>
            <div class="ops">
              <button class="icon-btn" type="button" title="在本卷新建章节" aria-label="在本卷新建章节" :disabled="busy" @click="addChapter(group.volume.id)">
                <Icon icon="lucide:plus" />
              </button>
              <button class="icon-btn" type="button" title="重命名" aria-label="重命名" :disabled="busy" @click="startRename(group.volume)">
                <Icon icon="lucide:pencil" />
              </button>
              <button class="icon-btn" type="button" title="上移" aria-label="上移卷" :disabled="busy || gi === 0" @click="applyOrder(moveVolume(groups, group.volume.id, -1))">
                <Icon icon="lucide:chevron-up" />
              </button>
              <button class="icon-btn" type="button" title="下移" aria-label="下移卷" :disabled="busy || gi === groups.length - 1" @click="applyOrder(moveVolume(groups, group.volume.id, 1))">
                <Icon icon="lucide:chevron-down" />
              </button>
              <button
                class="icon-btn icon-btn--danger"
                type="button"
                title="删除卷"
                aria-label="删除卷"
                :disabled="busy"
                @click="confirming = { kind: 'volume', id: String(group.volume.id) }"
              >
                <Icon icon="lucide:trash-2" />
              </button>
            </div>
          </template>
        </div>

        <ol v-show="!group.volume || !collapsed.has(String(group.volume.id))" :class="['chaps', { 'chaps--nested': group.volume }]">
          <li v-if="group.volume && group.chapters.length === 0" class="chaps__empty">
            空卷。
            <button class="link" type="button" :disabled="busy" @click="addChapter(group.volume.id)">在这里新建一章</button>
          </li>
          <li v-for="chapter in group.chapters" :key="chapter.id" class="chap">
            <template v-if="isConfirming('chapter', chapter.id)">
              <p class="chap__ask">删除「{{ chapter.title }}」？</p>
              <div class="confirm__actions">
                <button class="btn btn--ghost btn--sm" type="button" :disabled="busy" @click="confirming = null">取消</button>
                <button class="btn btn--sm danger" type="button" :disabled="busy" @click="removeChapter(chapter)">删除</button>
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
              <div class="ops">
                <button
                  class="icon-btn"
                  type="button"
                  title="上移（在卷首会移到上一卷）"
                  aria-label="上移"
                  :disabled="busy || !canMoveChapter(groups, chapter.id, -1)"
                  @click="applyOrder(moveChapter(groups, chapter.id, -1))"
                >
                  <Icon icon="lucide:chevron-up" />
                </button>
                <button
                  class="icon-btn"
                  type="button"
                  title="下移（在卷尾会移到下一卷）"
                  aria-label="下移"
                  :disabled="busy || !canMoveChapter(groups, chapter.id, 1)"
                  @click="applyOrder(moveChapter(groups, chapter.id, 1))"
                >
                  <Icon icon="lucide:chevron-down" />
                </button>
                <button
                  class="icon-btn icon-btn--danger"
                  type="button"
                  title="删除"
                  aria-label="删除"
                  :disabled="busy"
                  @click="confirming = { kind: 'chapter', id: String(chapter.id) }"
                >
                  <Icon icon="lucide:trash-2" />
                </button>
              </div>
            </template>
          </li>
        </ol>
      </section>

      <footer class="toc__foot">
        <button class="btn btn--quiet btn--sm" type="button" :disabled="busy" @click="addVolume">
          <Icon icon="lucide:library" />
          {{ hasVolumes ? '新建卷' : '分卷' }}
        </button>
        <span v-if="!hasVolumes" class="toc__foot-hint">现有章节会收进第一卷</span>
      </footer>
    </div>

    <p v-if="actionError" class="form__error toc__error" role="alert">{{ actionError }}</p>
  </section>
</template>

<style scoped lang="scss">
.toc {
  padding-bottom: 0.5rem;
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

.toc__add svg,
.toc__foot svg {
  width: 0.95rem;
  height: 0.95rem;
}

.toc__body {
  max-height: 32rem;
  margin-top: 0.6rem;
  padding: 0 0.6rem;
  overflow-y: auto;
}

.vol + .vol {
  margin-top: 0.35rem;
}

.vol__head {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-height: 2.4rem;
  padding: 0.15rem 0.2rem;
  border-radius: var(--radius-md);
  transition: background var(--duration-leave) var(--ease-soft);
}

.vol__head:hover {
  background: var(--color-bg-soft);
}

.vol__toggle {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.3rem;
  min-width: 0;
  padding: 0.3rem 0.3rem;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}

.vol__chevron {
  flex-shrink: 0;
  width: 1rem;
  height: 1rem;
  color: var(--color-text-secondary);
  transition: transform var(--duration-panel) var(--ease-soft);
}

.vol__chevron--closed {
  transform: rotate(-90deg);
}

.vol__title {
  overflow: hidden;
  font-size: 0.9rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vol__rename {
  flex: 1;
  min-width: 0;
  padding: 0.15rem 0.4rem;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  font-size: 0.9rem;
  font-weight: 700;
}

.vol__rename:focus {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.vol__meta {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

/*
 * 卷头按钮多（5 个），只用透明度隐藏仍会占宽、把卷名挤成「第二…」；
 * 所以卷头平时显示章数字数，悬停/聚焦时两者对调。
 */
.vol__head .ops {
  display: none;
}

.vol__head:hover .vol__meta,
.vol__head:focus-within .vol__meta {
  display: none;
}

.vol__head:hover .ops,
.vol__head:focus-within .ops {
  display: flex;
}

.chaps--nested {
  margin-left: 0.9rem;
  padding-left: 0.4rem;
  border-left: 1px solid var(--color-border);
}

.chaps__empty {
  padding: 0.45rem 0.6rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: inherit;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
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

.chap__ask {
  flex: 1;
  min-width: 0;
  padding: 0.45rem 0.5rem;
  overflow: hidden;
  font-size: 0.88rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作按钮悬停才显示，目录平时保持安静；键盘聚焦与触屏时常显 */
.ops {
  display: flex;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--duration-leave) var(--ease-soft);
}

.chap:hover .ops,
.chap:focus-within .ops,
.vol__head:hover .ops,
.vol__head:focus-within .ops {
  opacity: 1;
}

@media (hover: none) {
  .ops {
    opacity: 1;
  }

  .vol__head .ops {
    display: flex;
  }

  .vol__meta {
    display: none;
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

.confirm {
  flex: 1;
  padding: 0.4rem 0.5rem;
}

.confirm__ask {
  font-size: 0.88rem;
  font-weight: 600;
}

.confirm__hint {
  margin-top: 0.15rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.confirm__actions {
  display: flex;
  gap: 0.35rem;
  margin-top: 0.4rem;
}

.chap .confirm__actions {
  margin-top: 0;
}

.btn--sm {
  padding: 0.3rem 0.6rem;
  font-size: 0.82rem;
}

.danger {
  color: #fff;
  background: #dc2626;
}

.danger:not(:disabled):hover {
  background: #b91c1c;
}

.toc__foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.4rem;
  padding: 0.5rem 0.2rem 0.2rem;
  border-top: 1px dashed var(--color-border);
}

.toc__foot-hint {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
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
