<script setup lang="ts">
/** 月视图右侧的当天明细：会议、任务（可勾选、可在这天新建）、习惯、日记。不跳页。 */
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { completeTask, createTask } from '../../../api/tasks'
import { errorText, toast } from '../../../composables/useToast'
import { monthDay, relativeDay, todayYmd, weekdayLabel } from '../../../utils/date'
import { holidayOf } from '../../../utils/holidays'
import { valueText } from '../../../utils/habitStats'
import { renderMarkdown } from '../../../utils/markdown'
import { statusOf } from '../../meetings/meetingInfo'
import type { CalendarData } from '../useCalendarData'

const props = defineProps<{ date: string; data: CalendarData; focusAdd: number }>()
const emit = defineEmits<{ changed: []; openDay: [] }>()

const today = todayYmd()
const meetings = computed(() => props.data.meetingsOn(props.date))
const tasks = computed(() => {
  const list = props.data.tasksOn(props.date)
  // 今天也列出过期未完成的，免得看不见
  const late = props.date === today ? props.data.tasks.value.filter((t) => !t.done && t.dueDate && t.dueDate < today) : []
  return [...late, ...list].sort((a, b) => Number(a.done) - Number(b.done) || String(a.dueTime ?? '99').localeCompare(String(b.dueTime ?? '99')))
})
const doneCount = computed(() => tasks.value.filter((t) => t.done).length)
const habits = computed(() => props.data.habitsOn(props.date))
const journal = computed(() => props.data.journalOn(props.date))

const draft = ref('')
const input = ref<HTMLInputElement | null>(null)
watch(
  () => props.focusAdd,
  async () => {
    await nextTick()
    input.value?.focus()
  },
)

const add = async () => {
  const title = draft.value.trim()
  if (!title) return
  try {
    await createTask({ title, dueDate: props.date })
    draft.value = ''
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '添加失败'))
  }
}

const toggle = async (id: string | number, done: boolean) => {
  try {
    await completeTask(id, !done)
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}
</script>

<template>
  <aside class="dp surface">
    <header class="dp__head">
      <div>
        <h2>{{ monthDay(date) }} · {{ weekdayLabel(date) }}<small v-if="relativeDay(date) !== monthDay(date)">{{ relativeDay(date) }}</small></h2>
        <p>
          {{ meetings.length }} 个会议 · 任务 {{ doneCount }} / {{ tasks.length }}
          <template v-if="habits.length"> · 习惯 {{ habits.filter((h) => h.done).length }} / {{ habits.length }}</template>
          <template v-if="holidayOf(date)"> · {{ holidayOf(date) }}</template>
        </p>
      </div>
      <button class="btn btn--ghost" type="button" @click="emit('openDay')"><Icon icon="lucide:clock" />排时间</button>
    </header>

    <section>
      <h3>会议</h3>
      <p v-if="!meetings.length" class="muted">没有会议</p>
      <router-link v-for="m in meetings" :key="m.id" :to="{ name: 'meeting', params: { id: String(m.id) } }" class="row">
        <b class="row__time">{{ m.startTime }}</b>
        <span class="row__text">{{ m.title }}</span>
        <small>{{ statusOf(m).text }}</small>
      </router-link>
    </section>

    <section>
      <h3>任务</h3>
      <p v-if="!tasks.length" class="muted">没有任务</p>
      <div v-for="t in tasks" :key="t.id" class="row" :class="{ done: t.done }">
        <button type="button" class="check" :class="{ on: t.done }" :aria-label="t.done ? '标记为未完成' : '完成'" @click="toggle(t.id, t.done)">
          <Icon v-if="t.done" icon="lucide:check" />
        </button>
        <router-link :to="{ path: '/tasks', query: { v: 'all', task: String(t.id) } }" class="row__text">{{ t.title }}</router-link>
        <small :class="{ late: !t.done && t.dueDate! < today }">{{ t.dueDate !== date ? relativeDay(t.dueDate!) + '过期' : (t.dueTime ?? '') }}</small>
      </div>
      <label class="add">
        <Icon icon="lucide:plus" />
        <input ref="input" v-model="draft" :placeholder="`在${relativeDay(date)}添加任务，回车`" @keydown.enter.prevent="add" />
      </label>
    </section>

    <section v-if="habits.length">
      <h3>习惯</h3>
      <div class="habits">
        <span v-for="h in habits" :key="h.habit.id" class="habit" :class="{ on: h.done }">
          {{ h.done ? '✓' : '○' }} {{ h.habit.icon }} {{ h.habit.name }}
          <small v-if="h.habit.kind !== 'check' && h.log">（{{ valueText(h.habit, h.log.value) }}）</small>
        </span>
      </div>
    </section>

    <section>
      <h3>日记</h3>
      <div v-if="journal" class="journal md" v-html="renderMarkdown(journal.content)" />
      <p v-else class="muted">晚间回顾后出现在这里。</p>
    </section>
  </aside>
</template>

<style scoped>
.dp {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
}

.dp__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem;
}

.dp__head h2 {
  font-size: 1.05rem;
  font-weight: 800;
}

.dp__head h2 small {
  margin-left: 0.4rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-brand);
}

.dp__head p {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.dp__head .btn {
  flex: none;
  padding: 0.3rem 0.7rem;
  font-size: 0.8rem;
}

h3 {
  margin-bottom: 0.35rem;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.muted {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.2rem;
  font-size: 0.86rem;
}

.row__time {
  width: 2.8rem;
  flex: none;
  font-variant-numeric: tabular-nums;
}

.row__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.row:hover .row__text,
a.row__text:hover {
  color: var(--color-brand);
}

.row small {
  flex: none;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.row small.late {
  color: var(--color-danger);
}

.row.done .row__text {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.check {
  display: grid;
  flex: none;
  place-items: center;
  width: 1rem;
  height: 1rem;
  padding: 0;
  border: 2px solid var(--color-text-secondary);
  border-radius: 50%;
  background: none;
  color: #fff;
  cursor: pointer;
}

.check.on {
  border-color: var(--color-text-secondary);
  background: var(--color-text-secondary);
}

.check svg {
  width: 0.65rem;
  height: 0.65rem;
}

.add {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.25rem;
  color: var(--color-text-secondary);
}

.add input {
  flex: 1;
  padding: 0.35rem 0;
  border: 0;
  border-bottom: 1px dashed var(--color-border);
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.86rem;
}

.add input:focus {
  border-bottom-color: var(--color-brand);
}

.habits {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.8rem;
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.habit.on {
  color: var(--color-accent-text);
  font-weight: 600;
}

.journal {
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--note-purple);
  font-size: 0.86rem;
}
</style>
