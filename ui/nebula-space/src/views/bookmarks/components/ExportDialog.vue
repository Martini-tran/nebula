<script setup lang="ts">
/**
 * 导出为 Chrome 书签 HTML。范围默认跟随左栏当前选择，实时显示将导出的条数。
 * 后端口径：只导出「正常」状态的书签；按目录导出只含该目录本身，不含子目录。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { countBookmarks, exportChromeBookmarks } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText } from '../../../composables/useToast'
import { BookmarkStatus, type EntityId, type ExportScope } from '../../../types/space'

const props = defineProps<{ open: boolean; initialScope: ExportScope }>()
const emit = defineEmits<{ close: []; exported: [count: number] }>()

const space = useSpaceStore()

const scopeType = ref<ExportScope['scopeType']>('all')
const folderId = ref('')
const tagId = ref('')
const count = ref<number | null>(null)
const counting = ref(false)
const exporting = ref(false)
const errorMessage = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const scope = props.initialScope
    scopeType.value = scope.scopeType
    folderId.value = scope.scopeType === 'folder' ? String(scope.scopeId) : String(space.flat[0]?.id ?? '')
    tagId.value = scope.scopeType === 'tag' ? String(scope.scopeId) : String(space.tags[0]?.id ?? '')
    errorMessage.value = ''
  },
)

const scope = computed<ExportScope | null>(() => {
  if (scopeType.value === 'all') return { scopeType: 'all' }
  const id: EntityId = scopeType.value === 'folder' ? folderId.value : tagId.value
  return id ? { scopeType: scopeType.value, scopeId: id } : null
})

const childCount = computed(() =>
  scopeType.value === 'folder' && folderId.value ? space.subtree(folderId.value).length - 1 : 0,
)

let seq = 0
watch(
  [scope, () => props.open],
  async ([value, open]) => {
    if (!open || !value) {
      count.value = null
      return
    }
    const current = ++seq
    counting.value = true
    try {
      const n = await countBookmarks({
        status: BookmarkStatus.NORMAL,
        folderId: value.scopeType === 'folder' ? value.scopeId : undefined,
        tagId: value.scopeType === 'tag' ? value.scopeId : undefined,
      })
      if (current === seq) count.value = n
    } catch {
      if (current === seq) count.value = null
    } finally {
      if (current === seq) counting.value = false
    }
  },
  { immediate: true },
)

const submit = async () => {
  if (!scope.value || exporting.value) return
  exporting.value = true
  errorMessage.value = ''
  try {
    await exportChromeBookmarks(scope.value)
    emit('exported', count.value ?? 0)
  } catch (error) {
    errorMessage.value = errorText(error, '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <BaseDialog :open="open" title="导出书签" width="32rem" :locked="exporting" @close="emit('close')">
    <div class="ex">
      <fieldset class="ex__group">
        <legend>范围</legend>
        <label class="opt" :class="{ on: scopeType === 'all' }">
          <input v-model="scopeType" type="radio" value="all" />
          <span>全部书签</span>
        </label>
        <label class="opt" :class="{ on: scopeType === 'folder' }">
          <input v-model="scopeType" type="radio" value="folder" :disabled="!space.flat.length" />
          <span>一个目录</span>
          <select v-model="folderId" class="field__input" :disabled="scopeType !== 'folder'" aria-label="选择目录" @click="scopeType = 'folder'">
            <option v-for="f in space.flatFolders()" :key="f.id" :value="String(f.id)">{{ f.label }}</option>
          </select>
        </label>
        <label class="opt" :class="{ on: scopeType === 'tag' }">
          <input v-model="scopeType" type="radio" value="tag" :disabled="!space.tags.length" />
          <span>一个标签</span>
          <select v-model="tagId" class="field__input" :disabled="scopeType !== 'tag'" aria-label="选择标签">
            <option v-for="t in space.tags" :key="t.id" :value="String(t.id)">{{ t.name }}</option>
          </select>
        </label>
      </fieldset>

      <fieldset class="ex__group">
        <legend>格式</legend>
        <label class="opt on">
          <input type="radio" checked />
          <span>Chrome 书签 HTML</span>
          <small>可导回 Chrome / Edge / Firefox</small>
        </label>
        <label class="opt opt--off">
          <input type="radio" disabled />
          <span>JSON（含标签与备注）</span>
          <small class="tag">规划中</small>
        </label>
      </fieldset>

      <p class="ex__note">
        只导出正常状态的书签，已归档与失效的不导出。
        <template v-if="childCount > 0">按目录导出只包含这个目录本身的书签，其下 {{ childCount }} 个子目录不包含。</template>
        HTML 格式里没有标签和备注。
      </p>

      <p class="ex__sum">
        <Icon :icon="counting ? 'lucide:loader-circle' : 'lucide:file-down'" :class="{ spin: counting }" />
        <template v-if="count === null">正在统计…</template>
        <template v-else>将导出 <b>{{ count }}</b> 条书签</template>
      </p>
      <p v-if="errorMessage" class="form__error">{{ errorMessage }}</p>
    </div>

    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="exporting" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!scope || exporting || count === 0" @click="submit">
        <Icon :icon="exporting ? 'lucide:loader-circle' : 'lucide:download'" :class="{ spin: exporting }" />下载
      </button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.ex {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.35rem 0.25rem;
}

.ex__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.ex__group legend {
  margin-bottom: 0.4rem;
  font-size: 0.88rem;
  font-weight: 600;
}

.opt {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.55rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 0.9rem;
  cursor: pointer;
}

.opt.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.opt--off {
  cursor: not-allowed;
  color: var(--color-text-secondary);
}

.opt input[type='radio'] {
  accent-color: var(--color-brand);
}

.opt small {
  margin-left: auto;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.opt select {
  flex: 1;
  min-width: 9rem;
  margin-left: auto;
  padding-block: 0.3rem;
}

.ex__note {
  font-size: 0.8rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.ex__sum {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.92rem;
}
</style>
