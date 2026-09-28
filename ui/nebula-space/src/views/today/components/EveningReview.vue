<script setup lang="ts">
/**
 * 晚间回顾：下午 6 点后替换「今天」顶部。完成了多少、剩下的放到哪（明天 / 下周 / 放弃）、
 * 今天的临时笔记要不要转长期，最后一句话日志存成日记笔记。全程可跳过；底部可以直接去写今天的日报。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { createNote, updateNote } from '../../../api/notes'
import { deleteTask, updateTask } from '../../../api/tasks'
import { errorText, toast } from '../../../composables/useToast'
import { addDays, monthDay, nextWeekday, todayYmd, weekdayLabel } from '../../../utils/date'
import { firstLine } from '../../../utils/markdown'
import { MOODS } from '../../../config/icons'
import type { Note } from '../../../types/notes'
import type { Task } from '../../../types/tasks'

const props = defineProps<{
  done: number
  total: number
  remaining: Task[]
  tempNotes: Note[]
  stats: { meetings: number; actions: number; notes: number; bookmarks: number | null }
}>()
const emit = defineEmits<{ changed: []; close: [saved: boolean] }>()

/** 已处理的条目：id → 结果说明 */
const handled = ref(new Map<string, string>())
const mark = (key: string, text: string) => (handled.value = new Map(handled.value).set(key, text))

const tomorrow = addDays(todayYmd(), 1)
const monday = nextWeekday(1)

const moveTask = async (task: Task, to: 'tomorrow' | 'week' | 'drop') => {
  try {
    if (to === 'drop') await deleteTask(task.id)
    else await updateTask(task.id, { dueDate: to === 'tomorrow' ? tomorrow : monday })
    mark(`t${task.id}`, to === 'drop' ? '已放弃' : to === 'tomorrow' ? '→ 明天' : `→ ${monthDay(monday)}`)
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const keepNote = async (note: Note) => {
  try {
    await updateNote(note.id, { pinned: true })
    mark(`n${note.id}`, '已转长期')
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const mood = ref('')
const journal = ref('')
const saving = ref(false)

const saveJournal = async () => {
  if (!journal.value.trim() && !mood.value) return
  saving.value = true
  try {
    const today = todayYmd()
    await createNote({
      content: `## ${monthDay(today)} ${weekdayLabel(today)}${mood.value ? ` · ${mood.value}` : ''}\n${journal.value.trim()}`,
      tags: ['日记'],
      color: 'purple',
    })
    toast.ok('已存进日记（随手记 · #日记）')
    emit('close', true)
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

const rate = computed(() => (props.total ? Math.round((props.done / props.total) * 100) : 0))
</script>

<template>
  <section class="er surface">
    <header class="er__head">
      <div>
        <p class="er__eyebrow"><Icon icon="lucide:moon" />晚间回顾</p>
        <h2>今天完成 <b>{{ done }} / {{ total }}</b> 项任务</h2>
        <p class="er__stats">
          {{ stats.meetings }} 个会议<template v-if="stats.actions"> · 产生 {{ stats.actions }} 项待办</template>
          · 记下 {{ stats.notes }} 条随手记<template v-if="stats.bookmarks !== null"> · 收藏 {{ stats.bookmarks }} 个网址</template>
        </p>
      </div>
      <div class="er__ring" :style="{ '--p': `${rate}%` }"><span>{{ rate }}%</span></div>
    </header>

    <div class="er__cols">
      <section v-if="remaining.length">
        <h3>剩下 {{ remaining.length }} 项，放到哪？</h3>
        <ul>
          <li v-for="t in remaining" :key="t.id">
            <span class="er__text">{{ t.title }}</span>
            <em v-if="handled.has(`t${t.id}`)" class="er__done">{{ handled.get(`t${t.id}`) }}</em>
            <span v-else class="er__acts">
              <button type="button" @click="moveTask(t, 'tomorrow')">明天</button>
              <button type="button" @click="moveTask(t, 'week')">下周一</button>
              <button type="button" class="danger" @click="moveTask(t, 'drop')">放弃</button>
            </span>
          </li>
        </ul>
      </section>

      <section v-if="tempNotes.length">
        <h3>今天的临时笔记</h3>
        <ul>
          <li v-for="n in tempNotes" :key="n.id">
            <span class="er__text">{{ firstLine(n.content) }}</span>
            <em v-if="handled.has(`n${n.id}`)" class="er__done">{{ handled.get(`n${n.id}`) }}</em>
            <span v-else class="er__acts">
              <button type="button" @click="keepNote(n)">转长期</button>
              <button type="button" @click="mark(`n${n.id}`, '随它')">随它</button>
            </span>
          </li>
        </ul>
      </section>

      <section class="er__journal">
        <h3>一句话日志</h3>
        <div class="moods" role="radiogroup" aria-label="今天感觉">
          <button v-for="m in MOODS" :key="m.key" type="button" role="radio" :aria-checked="mood === m.key" :class="{ on: mood === m.key }" @click="mood = mood === m.key ? '' : m.key">
            <Icon :icon="m.icon" />{{ m.key }}
          </button>
        </div>
        <textarea v-model="journal" class="field__input" rows="3" placeholder="今天最值得记一笔的是……" />
      </section>
    </div>

    <footer class="er__foot">
      <router-link class="btn btn--ghost er__daily" :to="{ path: '/review/report', query: { type: 'day' } }"><Icon icon="lucide:file-pen-line" />写今天的日报</router-link>
      <button class="btn btn--quiet" type="button" @click="emit('close', false)">跳过</button>
      <button class="btn btn--primary" type="button" :disabled="saving || (!journal.trim() && !mood)" @click="saveJournal">
        <Icon icon="lucide:book-heart" />保存到日记
      </button>
    </footer>
  </section>
</template>

<style scoped>
.er {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.25rem;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, color-mix(in srgb, var(--color-brand) 10%, var(--color-bg-surface)), var(--color-bg-surface) 60%);
}

.er__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.er__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-brand);
}

.er__head h2 {
  font-size: 1.2rem;
  font-weight: 800;
}

.er__head h2 b {
  color: var(--color-brand);
}

.er__stats {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.er__ring {
  display: grid;
  flex: none;
  place-items: center;
  width: 4rem;
  height: 4rem;
  border-radius: 50%;
  background: conic-gradient(var(--color-brand) var(--p), var(--color-bg-soft) 0);
}

.er__ring span {
  display: grid;
  place-items: center;
  width: 3.1rem;
  height: 3.1rem;
  border-radius: 50%;
  background: var(--color-bg-surface);
  font-size: 0.82rem;
  font-weight: 800;
}

.er__cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1rem 1.5rem;
}

.er__cols h3 {
  margin-bottom: 0.4rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.er__cols ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.er__cols li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0;
  font-size: 0.86rem;
}

.er__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.er__acts {
  display: flex;
  flex: none;
  gap: 0.2rem;
}

.er__acts button {
  padding: 0.15rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.76rem;
  cursor: pointer;
}

.er__acts button:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.er__acts .danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.er__done {
  flex: none;
  font-size: 0.76rem;
  font-style: normal;
  color: var(--color-accent-text);
}

.er__journal {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.moods {
  display: flex;
  gap: 0.3rem;
}

.moods button {
  padding: 0.2rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.8rem;
  cursor: pointer;
}

.moods button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.er__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.er__daily {
  margin-right: auto;
}
</style>
