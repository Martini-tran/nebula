<script setup lang="ts">
/**
 * 删除目录。先统计里面有什么，空目录直接删；不空时让用户三选一：
 * 移到上级（推荐）/ 书签移到未分类 / 连同书签一起删除——服务端在一个事务里做完，失败时什么都不改。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { countBookmarks, deleteFolder } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import type { EntityId, Folder, FolderDeleteStrategy } from '../../../types/space'

const props = defineProps<{ folder: Folder | null }>()
const emit = defineEmits<{ close: []; deleted: [folder: Folder] }>()

const space = useSpaceStore()

const strategy = ref<FolderDeleteStrategy>('moveUp')
const loading = ref(false)
const running = ref(false)
const errorMessage = ref('')
const directCount = ref(0)
const nestedCount = ref(0)

const subtree = computed(() => (props.folder ? space.subtree(props.folder.id) : []))
const childFolders = computed(() => props.folder?.children ?? [])
const nestedFolderCount = computed(() => Math.max(0, subtree.value.length - 1))
const totalBookmarks = computed(() => directCount.value + nestedCount.value)
const isEmpty = computed(() => !loading.value && !childFolders.value.length && totalBookmarks.value === 0)
const parentId = computed<EntityId>(() => props.folder?.parentId ?? 0)
const parentIsRoot = computed(() => String(parentId.value) === '0')
const moveUpHint = computed(() => {
  const folders = childFolders.value.length ? `子目录一并上移一层${parentIsRoot.value ? '成为顶层目录' : ''}，` : ''
  const bookmarks = parentIsRoot.value ? '本目录的书签放进未分类' : '本目录的书签放进上级目录'
  return `${folders}${bookmarks}。书签不丢，推荐。`
})
const parentName = computed(() => (parentIsRoot.value ? '' : (space.findFolder(parentId.value)?.name ?? '')))

watch(
  () => props.folder,
  async (folder) => {
    if (!folder) return
    strategy.value = 'moveUp'
    errorMessage.value = ''
    loading.value = true
    try {
      const counts = await Promise.all(subtree.value.map((f) => countBookmarks({ folderId: f.id })))
      directCount.value = counts[0] ?? 0
      nestedCount.value = counts.slice(1).reduce((sum, n) => sum + n, 0)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '统计目录内容失败'
    } finally {
      loading.value = false
    }
  },
)

const run = async () => {
  const folder = props.folder
  if (!folder || running.value) return
  running.value = true
  errorMessage.value = ''
  try {
    await deleteFolder(folder.id, isEmpty.value ? undefined : strategy.value)
    emit('deleted', folder)
  } catch (error) {
    const reason = error instanceof Error ? error.message : '操作失败'
    errorMessage.value = `删除失败：${reason}。目录和书签都没有改动。`
  } finally {
    running.value = false
  }
}
</script>

<template>
  <BaseDialog
    :open="Boolean(folder)"
    :title="`删除目录「${folder?.name ?? ''}」`"
    width="32rem"
    :locked="running"
    @close="emit('close')"
  >
    <div class="fd">
      <p v-if="loading" class="fd__loading"><Icon icon="lucide:loader-circle" class="spin" />正在统计目录内容…</p>

      <template v-else-if="isEmpty">
        <p>这是一个空目录，删除后无法撤销。</p>
      </template>

      <template v-else>
        <div class="fd__stats">
          <span><b>{{ directCount }}</b>条书签</span>
          <span><b>{{ nestedFolderCount }}</b>个子目录</span>
          <span><b>{{ nestedCount }}</b>子目录内书签</span>
        </div>

        <fieldset class="fd__opts">
          <legend>目录删了，里面的书签怎么办？</legend>
          <label class="opt" :class="{ on: strategy === 'moveUp' }">
            <input v-model="strategy" type="radio" value="moveUp" />
            <span>
              <b>{{ parentIsRoot ? '上移一层' : `移到上级目录「${parentName}」` }}</b>
              <small>{{ moveUpHint }}</small>
            </span>
          </label>
          <label class="opt" :class="{ on: strategy === 'uncategorize' }">
            <input v-model="strategy" type="radio" value="uncategorize" />
            <span>
              <b>移到未分类</b>
              <small>{{ totalBookmarks }} 条书签全部放进未分类，子目录删除。</small>
            </span>
          </label>
          <label class="opt opt--danger" :class="{ on: strategy === 'cascade' }">
            <input v-model="strategy" type="radio" value="cascade" />
            <span>
              <b>连同书签一起删除</b>
              <small>{{ totalBookmarks }} 条书签会被删除，无法撤销。</small>
            </span>
          </label>
        </fieldset>
      </template>

      <p v-if="running" class="fd__step"><Icon icon="lucide:loader-circle" class="spin" />正在删除…</p>
      <p v-if="errorMessage" class="form__error">{{ errorMessage }}</p>
    </div>

    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="running" @click="emit('close')">取消</button>
      <button
        class="btn"
        :class="strategy === 'cascade' || isEmpty ? 'btn--danger' : 'btn--primary'"
        type="button"
        :disabled="loading || running"
        @click="run"
      >
        删除目录
      </button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.fd {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.35rem 0.25rem;
  font-size: 0.92rem;
}

.fd__loading,
.fd__step {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--color-text-secondary);
}

.fd__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.fd__stats span {
  display: flex;
  flex-direction: column;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.fd__stats b {
  font-size: 1.3rem;
  color: var(--color-text-primary);
}

.fd__opts {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.fd__opts legend {
  margin-bottom: 0.45rem;
  font-weight: 600;
}

.opt {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.opt input {
  margin-top: 0.25rem;
  accent-color: var(--color-brand);
}

.opt span {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.opt small {
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.opt.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.opt--danger.on {
  border-color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 8%, transparent);
}

.opt--danger input {
  accent-color: var(--color-danger);
}
</style>
