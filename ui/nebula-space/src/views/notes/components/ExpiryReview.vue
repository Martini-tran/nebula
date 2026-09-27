<script setup lang="ts">
/**
 * 到期整理：明天（及今天）到期的临时笔记，每条三选一——留着（转长期）、变任务、归档。
 * 什么都不做也没关系，到点自动归档。
 */
import { ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { fetchNotes, updateNote } from '../../../api/notes'
import { noteToTask } from '../../../composables/useNoteTask'
import { errorText, toast } from '../../../composables/useToast'
import { addDays, monthDay, todayYmd, ymdOf } from '../../../utils/date'
import { firstLine } from '../../../utils/markdown'
import type { Note } from '../../../types/notes'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const items = ref<Note[]>([])
const loading = ref(false)
const busy = ref<string | null>(null)
const done = ref(new Map<string, string>())

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    loading.value = true
    done.value = new Map()
    try {
      const tomorrow = addDays(todayYmd(), 1)
      const list = await fetchNotes({ view: 'temporary' })
      items.value = list.filter((n) => n.expireDate && n.expireDate <= tomorrow)
    } catch (error) {
      toast.error(errorText(error, '加载失败'))
    } finally {
      loading.value = false
    }
  },
)

const act = async (note: Note, action: 'keep' | 'task' | 'archive') => {
  busy.value = String(note.id)
  try {
    if (action === 'keep') await updateNote(note.id, { pinned: true })
    if (action === 'task') await noteToTask(note)
    if (action === 'archive') await updateNote(note.id, { archived: true })
    done.value = new Map(done.value).set(String(note.id), { keep: '已转长期', task: '已变成任务', archive: '已归档' }[action])
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  } finally {
    busy.value = null
  }
}

const archiveAll = async () => {
  for (const note of items.value.filter((n) => !done.value.has(String(n.id)))) await act(note, 'archive')
}
</script>

<template>
  <BaseDialog :open="open" :title="`到期整理 · ${items.length} 条`" width="36rem" @close="emit('close')">
    <div class="er">
      <p class="er__hint">临时笔记到期只归档、不删除，已归档里还能搜到。什么都不做也没关系，到点会自动归档。</p>
      <p v-if="loading" class="er__empty"><Icon icon="lucide:loader-circle" class="spin" />加载中…</p>
      <p v-else-if="!items.length" class="er__empty">没有快到期的临时笔记。</p>
      <ul v-else class="er__list">
        <li v-for="note in items" :key="note.id" class="er__item" :class="{ 'er__item--done': done.has(String(note.id)) }">
          <div class="er__text">
            <b>{{ firstLine(note.content) || '（空笔记）' }}</b>
            <small>{{ monthDay(ymdOf(note.createTime)) }} · 临时<template v-if="note.tags.length"> · {{ note.tags.join('、') }}</template></small>
          </div>
          <span v-if="done.has(String(note.id))" class="er__done"><Icon icon="lucide:check" />{{ done.get(String(note.id)) }}</span>
          <span v-else class="er__acts">
            <button class="btn btn--ghost" type="button" :disabled="busy !== null" @click="act(note, 'keep')">留着</button>
            <button class="btn btn--ghost" type="button" :disabled="busy !== null" @click="act(note, 'task')">变任务</button>
            <button class="btn btn--ghost" type="button" :disabled="busy !== null" @click="act(note, 'archive')">归档</button>
          </span>
        </li>
      </ul>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="busy !== null || items.every((n) => done.has(String(n.id)))" @click="archiveAll">
        剩下的全部归档
      </button>
      <button class="btn btn--primary" type="button" @click="emit('close')">完成</button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.er {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem 1.35rem 0.25rem;
}

.er__hint {
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.er__empty {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 1rem 0;
  color: var(--color-text-secondary);
}

.er__list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.er__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--color-border);
}

.er__item:last-child {
  border-bottom: 0;
}

.er__item--done .er__text {
  opacity: 0.55;
}

.er__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 12rem;
}

.er__text b {
  font-size: 0.9rem;
  font-weight: 600;
}

.er__text small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.er__acts {
  display: flex;
  gap: 0.3rem;
}

.er__acts .btn {
  padding: 0.3rem 0.65rem;
  font-size: 0.82rem;
}

.er__done {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.82rem;
  color: var(--color-accent-text);
}
</style>
