<script setup lang="ts">
/**
 * 选目录弹窗：书签移动（单条 / 批量）与目录移动共用。
 * 带搜索、顶部「最近移入」、当前所在目录置灰；书签模式下可就地新建目录并直接选中。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { createFolder } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import type { EntityId } from '../../../types/space'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    /** 当前所在目录（置灰，不可选）；0 = 未分类 / 顶层 */
    currentId?: EntityId | null
    /** 额外不可选的目录（移动目录时：自身及后代） */
    disabledIds?: EntityId[]
    /** 第一项的名字：书签叫「未分类」，目录叫「顶层」 */
    rootLabel?: string
    /** 是否允许就地新建目录 */
    allowCreate?: boolean
    busy?: boolean
  }>(),
  { currentId: null, disabledIds: () => [], rootLabel: '未分类', allowCreate: true, busy: false },
)
const emit = defineEmits<{ close: []; pick: [id: EntityId, label: string] }>()

const space = useSpaceStore()
const keyword = ref('')
const selected = ref<string | null>(null)
const creating = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    keyword.value = ''
    selected.value = null
    await nextTick()
    searchInput.value?.focus()
  },
)

const disabled = computed(() => new Set([...props.disabledIds.map(String), String(props.currentId ?? '__none__')]))

const rows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const root = { id: '0', name: props.rootLabel, depth: 0, path: props.rootLabel }
  const items = [root, ...space.flat.map((f) => ({ id: String(f.id), name: f.name, depth: f.depth + 1, path: f.path }))]
  if (!kw) return items
  // 搜索时按完整路径匹配并平铺显示，免得只看到一个孤零零的子目录名
  return items.filter((item) => item.path.toLowerCase().includes(kw)).map((item) => ({ ...item, depth: 0 }))
})

const recent = computed(() =>
  space.recentFolderIds
    .map((id) => space.flat.find((f) => String(f.id) === id))
    .filter((f): f is NonNullable<typeof f> => Boolean(f) && !disabled.value.has(String(f!.id))),
)

const labelOf = (id: string) => (id === '0' ? props.rootLabel : (space.flat.find((f) => String(f.id) === id)?.path ?? ''))

const selectedLabel = computed(() => (selected.value ? labelOf(selected.value) : ''))

/** 新建目录放在当前选中的目录下；没选就放顶层 */
const createParent = computed(() => (selected.value && selected.value !== '0' ? selected.value : '0'))
const canCreate = computed(() => {
  const name = keyword.value.trim()
  if (!props.allowCreate || !name) return false
  return !space.flat.some((f) => String(f.folder.parentId) === createParent.value && f.name === name)
})

const createAndPick = async () => {
  if (!canCreate.value || creating.value) return
  creating.value = true
  try {
    const id = await createFolder({ parentId: createParent.value, name: keyword.value.trim() })
    await space.reload()
    selected.value = String(id)
    keyword.value = ''
    toast.ok('目录已创建')
  } catch (error) {
    toast.error(errorText(error, '创建目录失败'))
  } finally {
    creating.value = false
  }
}

const confirm = () => {
  if (!selected.value || props.busy) return
  emit('pick', selected.value === '0' ? 0 : selected.value, selectedLabel.value)
}

const onDblClick = (id: string) => {
  if (disabled.value.has(id)) return
  selected.value = id
  confirm()
}

/** 搜索框里回车：唯一匹配直接选中，已选则确认 */
const onEnter = () => {
  const selectable = rows.value.filter((row) => !disabled.value.has(row.id))
  if (selected.value) confirm()
  else if (selectable.length === 1) selected.value = selectable[0]!.id
  else if (!selectable.length && canCreate.value) createAndPick()
}
</script>

<template>
  <BaseDialog :open="open" :title="title" width="30rem" :locked="busy" @close="emit('close')">
    <div class="picker">
      <label class="picker__search">
        <Icon icon="lucide:search" />
        <input
          ref="searchInput"
          v-model="keyword"
          type="search"
          placeholder="搜索目录"
          aria-label="搜索目录"
          @keydown.enter.prevent="onEnter"
        />
      </label>

      <div v-if="recent.length && !keyword" class="picker__recent">
        <span>最近：</span>
        <button
          v-for="f in recent"
          :key="f.id"
          type="button"
          class="tag"
          :class="{ 'tag--brand': selected === String(f.id) }"
          @click="selected = String(f.id)"
        >
          {{ f.name }}
        </button>
      </div>

      <ul class="picker__list" role="listbox" :aria-label="title">
        <li v-for="row in rows" :key="row.id">
          <button
            type="button"
            class="picker__row"
            role="option"
            :aria-selected="selected === row.id"
            :class="{ 'picker__row--on': selected === row.id }"
            :disabled="disabled.has(row.id)"
            :style="{ paddingLeft: `${0.6 + row.depth * 1}rem` }"
            @click="selected = row.id"
            @dblclick="onDblClick(row.id)"
          >
            <Icon :icon="row.id === '0' ? 'lucide:inbox' : 'lucide:folder'" />
            <span class="picker__name">{{ keyword ? row.path : row.name }}</span>
            <span v-if="String(currentId ?? '') === row.id" class="picker__note">当前位置</span>
          </button>
        </li>
        <li v-if="!rows.length" class="picker__empty">没有匹配的目录</li>
      </ul>

      <button v-if="canCreate" type="button" class="picker__create" :disabled="creating" @click="createAndPick">
        <Icon :icon="creating ? 'lucide:loader-circle' : 'lucide:folder-plus'" :class="{ spin: creating }" />
        在「{{ createParent === '0' ? '顶层' : labelOf(createParent) }}」下新建「{{ keyword.trim() }}」
      </button>
    </div>

    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!selected || busy" @click="confirm">
        <Icon v-if="busy" icon="lucide:loader-circle" class="spin" />
        {{ selected ? `移动到「${selectedLabel.split(' / ').pop()}」` : '选择目录' }}
      </button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem 1.35rem 0.25rem;
}

.picker__search {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
}

.picker__search:focus-within {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.picker__search input {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.picker__recent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.picker__recent button {
  border: 0;
  cursor: pointer;
}

.picker__list {
  max-height: min(20rem, 48vh);
  overflow-y: auto;
  margin: 0;
  padding: 0.25rem;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.picker__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.45rem 0.6rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-primary);
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;
}

.picker__row:hover:not(:disabled) {
  background: var(--color-bg-soft);
}

.picker__row--on,
.picker__row--on:hover:not(:disabled) {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.picker__row:disabled {
  color: var(--color-text-secondary);
  opacity: 0.6;
  cursor: not-allowed;
}

.picker__row svg {
  flex: none;
  width: 1rem;
  height: 1rem;
}

.picker__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker__note {
  font-size: 0.75rem;
}

.picker__empty {
  padding: 1rem;
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.picker__create {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.3rem 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.88rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
</style>
