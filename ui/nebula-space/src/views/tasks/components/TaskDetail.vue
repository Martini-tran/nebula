<script setup lang="ts">
/**
 * 任务详情：在列表右侧打开，不跳页。改动即时保存（标题、备注在失焦时保存）。
 * 重复规则用人话预览下一次日期；提醒需要先定时间。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import DatePicker from './DatePicker.vue'
import { updateTask } from '../../../api/tasks'
import { useTaskStore } from '../../../stores/tasks'
import { useFocusStore } from '../../../stores/focus'
import { fetchFocusSessions } from '../../../api/focus'
import { errorText, toast } from '../../../composables/useToast'
import { monthDay, relativeDay, weekdayLabel, weekdayOf, ymdOf } from '../../../utils/date'
import { describeRepeat, previewRepeat } from '../../../utils/repeat'
import { PRIORITY_LABEL, type RepeatRule, type Task, type TaskPriority, type TaskSaveRequest } from '../../../types/tasks'

const props = defineProps<{ task: Task; marked: Set<string> }>()
const emit = defineEmits<{ changed: [task: Task]; toggle: []; remove: []; close: [] }>()

const store = useTaskStore()
const focusStore = useFocusStore()

/** 这项任务已专注的轮数与分钟（预估 1.5 小时 ≈ 3 个 25 分钟） */
const focusDone = ref({ rounds: 0, minutes: 0 })
const loadFocus = async () => {
  const list = await fetchFocusSessions({ taskId: props.task.id }).catch(() => [])
  const done = list.filter((s) => s.status === 'done')
  focusDone.value = { rounds: done.length, minutes: done.reduce((sum, s) => sum + s.actualMin, 0) }
}
watch(() => [props.task.id, focusStore.phase], loadFocus, { immediate: true })
const plannedRounds = computed(() => (props.task.estimateMin ? Math.max(1, Math.round(props.task.estimateMin / 25)) : 0))
const startFocus = () =>
  focusStore.openSetup(props.task.id, props.task.title, focusDone.value.rounds ? `已专注 ${focusDone.value.rounds} 轮 · ${focusDone.value.minutes} 分钟` : undefined)

const title = ref('')
const note = ref('')
const subDraft = ref('')
const datePop = ref(false)
const titleInput = ref<HTMLTextAreaElement | null>(null)

const sync = () => {
  title.value = props.task.title
  note.value = props.task.note
}
watch(() => props.task.id, sync, { immediate: true })
watch(() => [props.task.title, props.task.note], sync)

const save = async (body: TaskSaveRequest) => {
  try {
    emit('changed', await updateTask(props.task.id, body))
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const saveTitle = () => {
  const value = title.value.replace(/\s+/g, ' ').trim()
  if (!value) {
    title.value = props.task.title
    return
  }
  if (value !== props.task.title) save({ title: value })
}

const saveNote = () => {
  if (note.value !== props.task.note) save({ note: note.value })
}

const setDate = (value: string | null) => {
  datePop.value = false
  const body: TaskSaveRequest = { dueDate: value }
  // 没有日期就谈不上时间、提醒和重复
  if (!value) Object.assign(body, { dueTime: null, remindBefore: null, repeat: null })
  save(body)
}

const setTime = (event: Event) => {
  const value = (event.target as HTMLInputElement).value || null
  save(value ? { dueTime: value } : { dueTime: null, remindBefore: null })
}

const REMIND = [
  { value: null, label: '不提醒' },
  { value: 0, label: '准时' },
  { value: 5, label: '提前 5 分钟' },
  { value: 15, label: '提前 15 分钟' },
  { value: 30, label: '提前 30 分钟' },
  { value: 60, label: '提前 1 小时' },
]

const ESTIMATES = [null, 15, 30, 45, 60, 90, 120, 180, 240, 360]
const estimateLabel = (min: number | null) => (min === null ? '未估' : min < 60 ? `${min} 分钟` : `${min / 60} 小时`)

// ── 重复 ──

const repeatType = computed(() => props.task.repeat?.type ?? 'none')
const WEEK = [1, 2, 3, 4, 5, 6, 0]
const WEEK_NAME = ['日', '一', '二', '三', '四', '五', '六']

const setRepeat = (event: Event) => {
  const type = (event.target as HTMLSelectElement).value
  let repeat: RepeatRule | null = null
  if (type === 'daily' || type === 'weekdays' || type === 'monthly') repeat = { type }
  if (type === 'weekly') repeat = { type: 'weekly', days: [weekdayOf(props.task.dueDate!)] }
  save({ repeat })
}

const toggleWeekday = (day: number) => {
  const rule = props.task.repeat
  if (rule?.type !== 'weekly') return
  const days = rule.days.includes(day) ? rule.days.filter((d) => d !== day) : [...rule.days, day]
  if (!days.length) return
  save({ repeat: { type: 'weekly', days } })
}

const repeatPreview = computed(() =>
  props.task.repeat && props.task.dueDate ? previewRepeat(props.task.repeat, props.task.dueDate) : '',
)

// ── 子任务 ──

const addSub = () => {
  const text = subDraft.value.trim()
  if (!text) return
  subDraft.value = ''
  save({ subtasks: [...props.task.subtasks, { id: `s${Date.now()}`, title: text, done: false }] })
}

const toggleSub = (id: string) =>
  save({ subtasks: props.task.subtasks.map((s) => (s.id === id ? { ...s, done: !s.done } : s)) })

const removeSub = (id: string) => save({ subtasks: props.task.subtasks.filter((s) => s.id !== id) })

// ── 其他 ──

const list = computed(() => store.findList(props.task.listId))
const dateText = computed(() =>
  props.task.dueDate ? `${relativeDay(props.task.dueDate)} · ${monthDay(props.task.dueDate)} ${weekdayLabel(props.task.dueDate)}` : '不定 · 收件箱',
)

const sourceLink = computed(() => {
  const source = props.task.source
  if (!source) return null
  if (source.type === 'note') return { to: { name: 'note-editor', params: { id: String(source.id) } }, text: '笔记' }
  if (source.type === 'meeting') return { to: { path: '/meetings', query: { id: String(source.id) } }, text: '会议' }
  return { to: { path: '/bookmarks' }, text: '书签' }
})

const autoGrow = () => {
  const el = titleInput.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}
watch(title, () => nextTick(autoGrow))

const onPointerDown = (event: PointerEvent) => {
  if (datePop.value && !(event.target as HTMLElement).closest('.td__date')) datePop.value = false
}
onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  autoGrow()
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <aside class="td surface" aria-label="任务详情">
    <header class="td__head">
      <span class="td__crumb">
        <i v-if="list" :style="{ background: list.color }" />{{ list?.name ?? '无清单' }}
      </span>
      <button class="btn btn--quiet td__close" type="button" aria-label="关闭详情" @click="emit('close')"><Icon icon="lucide:x" /></button>
    </header>

    <div class="td__title">
      <button
        type="button"
        class="td__check"
        :class="[`td__check--p${task.priority}`, { 'td__check--done': task.done }]"
        role="checkbox"
        :aria-checked="task.done"
        aria-label="完成"
        @click="emit('toggle')"
      >
        <Icon v-if="task.done" icon="lucide:check" />
      </button>
      <textarea
        ref="titleInput"
        v-model="title"
        rows="1"
        aria-label="任务标题"
        :class="{ done: task.done }"
        @keydown.enter.prevent="($event.target as HTMLTextAreaElement).blur()"
        @blur="saveTitle"
      />
    </div>

    <dl class="td__fields">
      <dt>日期</dt>
      <dd class="td__date">
        <button type="button" class="td__pick" :aria-expanded="datePop" @click="datePop = !datePop">
          <Icon icon="lucide:calendar" />{{ dateText }}
        </button>
        <div v-if="datePop" class="td__pop surface">
          <DatePicker :model-value="task.dueDate" :marked="marked" @update:model-value="setDate" />
        </div>
      </dd>

      <dt>时间</dt>
      <dd>
        <input
          type="time"
          class="field__input td__input"
          :value="task.dueTime ?? ''"
          :disabled="!task.dueDate"
          :title="task.dueDate ? '' : '先选日期'"
          @change="setTime"
        />
      </dd>

      <dt>提醒</dt>
      <dd>
        <select
          class="field__input td__input"
          :value="task.remindBefore === null ? '' : String(task.remindBefore)"
          :disabled="!task.dueTime"
          :title="task.dueTime ? '' : '先定时间'"
          @change="save({ remindBefore: ($event.target as HTMLSelectElement).value === '' ? null : Number(($event.target as HTMLSelectElement).value) })"
        >
          <option v-for="r in REMIND" :key="String(r.value)" :value="r.value === null ? '' : String(r.value)">{{ r.label }}</option>
        </select>
      </dd>

      <dt>优先级</dt>
      <dd class="td__prio" role="radiogroup" aria-label="优先级">
        <button
          v-for="p in [3, 2, 1, 0] as TaskPriority[]"
          :key="p"
          type="button"
          role="radio"
          :aria-checked="task.priority === p"
          :class="[`p${p}`, { on: task.priority === p }]"
          @click="save({ priority: p })"
        >
          {{ PRIORITY_LABEL[p] }}
        </button>
      </dd>

      <dt>清单</dt>
      <dd>
        <select
          class="field__input td__input"
          :value="task.listId === null ? '' : String(task.listId)"
          @change="save({ listId: ($event.target as HTMLSelectElement).value || null })"
        >
          <option value="">无清单</option>
          <option v-for="l in store.lists" :key="l.id" :value="String(l.id)">{{ l.name }}</option>
        </select>
      </dd>

      <dt>重复</dt>
      <dd class="td__repeat">
        <select class="field__input td__input" :value="repeatType" :disabled="!task.dueDate" :title="task.dueDate ? '' : '先选日期'" @change="setRepeat">
          <option value="none">不重复</option>
          <option value="daily">每天</option>
          <option value="weekdays">工作日</option>
          <option value="weekly">每周</option>
          <option value="monthly">每月</option>
        </select>
        <div v-if="task.repeat?.type === 'weekly'" class="td__week">
          <button
            v-for="d in WEEK"
            :key="d"
            type="button"
            :class="{ on: task.repeat.days.includes(d) }"
            :aria-pressed="task.repeat.days.includes(d)"
            @click="toggleWeekday(d)"
          >
            {{ WEEK_NAME[d] }}
          </button>
        </div>
        <p v-if="repeatPreview" class="td__hint">
          {{ describeRepeat(task.repeat, task.dueDate) }}。下一次：{{ repeatPreview }}完成本次后自动生成下一次。
        </p>
      </dd>

      <dt>预估</dt>
      <dd>
        <select
          class="field__input td__input"
          :value="task.estimateMin === null ? '' : String(task.estimateMin)"
          @change="save({ estimateMin: ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null })"
        >
          <option v-for="m in ESTIMATES" :key="String(m)" :value="m === null ? '' : String(m)">{{ estimateLabel(m) }}</option>
        </select>
      </dd>
    </dl>

    <section v-if="!task.done" class="td__sec td__focus">
      <span>
        🍅 已专注 {{ focusDone.rounds }}<template v-if="plannedRounds"> / {{ plannedRounds }}</template> 轮<template v-if="focusDone.minutes"> · {{ focusDone.minutes }} 分钟</template>
      </span>
      <button class="btn btn--ghost" type="button" @click="startFocus"><Icon icon="lucide:play" />开始专注</button>
    </section>

    <section class="td__sec">
      <h3>子任务 <small v-if="task.subtasks.length">{{ task.subtasks.filter((s) => s.done).length }} / {{ task.subtasks.length }}</small></h3>
      <ul class="td__subs">
        <li v-for="s in task.subtasks" :key="s.id" :class="{ done: s.done }">
          <input type="checkbox" :checked="s.done" :aria-label="s.title" @change="toggleSub(s.id)" />
          <span>{{ s.title }}</span>
          <button type="button" :aria-label="`删除子任务 ${s.title}`" @click="removeSub(s.id)"><Icon icon="lucide:x" /></button>
        </li>
      </ul>
      <label class="td__addsub">
        <Icon icon="lucide:plus" />
        <input v-model="subDraft" placeholder="添加子任务，回车确认" aria-label="添加子任务" @keydown.enter.prevent="addSub" />
      </label>
    </section>

    <section v-if="task.source && sourceLink" class="td__sec">
      <h3>来源</h3>
      <router-link :to="sourceLink.to" class="td__source">
        <Icon :icon="{ note: 'lucide:sticky-note', meeting: 'lucide:users', bookmark: 'lucide:bookmark' }[task.source.type]" />
        <span><small>{{ sourceLink.text }}</small>{{ task.source.label }}</span>
        <Icon icon="lucide:arrow-up-right" />
      </router-link>
    </section>

    <section class="td__sec">
      <h3>备注</h3>
      <textarea v-model="note" class="field__input td__note" rows="3" placeholder="补充说明、链接…" @blur="saveNote" />
    </section>

    <footer class="td__foot">
      <span>{{ monthDay(ymdOf(task.createTime)) }}创建<template v-if="task.doneTime"> · {{ monthDay(ymdOf(task.doneTime)) }}完成</template></span>
      <button class="btn btn--quiet danger" type="button" @click="emit('remove')"><Icon icon="lucide:trash-2" />删除</button>
    </footer>
  </aside>
</template>

<style scoped>
.td {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.9rem 1.1rem 0.8rem;
  border-radius: var(--radius-lg);
}

.td__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.td__crumb {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.td__crumb i {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.td__close {
  padding: 0.3rem;
}

.td__title {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
}

.td__title textarea {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  resize: none;
  overflow: hidden;
  background: none;
  color: var(--color-text-primary);
  font-size: 1.1rem;
  font-weight: 700;
  line-height: 1.45;
}

.td__title textarea.done {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.td__check {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.25rem;
  height: 1.25rem;
  margin-top: 0.2rem;
  padding: 0;
  border: 2px solid var(--color-text-secondary);
  border-radius: 50%;
  background: none;
  color: #fff;
  cursor: pointer;
}

.td__check--p3 {
  border-color: #dc2626;
}

.td__check--p2 {
  border-color: #ea8a0c;
}

.td__check--p1 {
  border-color: #3b82f6;
}

.td__check--done {
  border-color: var(--color-text-secondary);
  background: var(--color-text-secondary);
}

.td__check svg {
  width: 0.8rem;
  height: 0.8rem;
}

.td__fields {
  display: grid;
  grid-template-columns: 3.5rem 1fr;
  gap: 0.5rem 0.6rem;
  align-items: center;
  margin: 0;
  font-size: 0.86rem;
}

.td__fields dt {
  color: var(--color-text-secondary);
}

.td__fields dd {
  margin: 0;
  min-width: 0;
}

.td__input {
  padding: 0.35rem 0.55rem;
  font-size: 0.86rem;
}

.td__input:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.td__date {
  position: relative;
}

.td__pick {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  padding: 0.35rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.86rem;
  text-align: left;
  cursor: pointer;
}

.td__pop {
  position: absolute;
  top: calc(100% + 0.35rem);
  right: 0;
  z-index: 20;
  padding: 0.75rem;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.td__prio {
  display: flex;
  gap: 0.25rem;
}

.td__prio button {
  flex: 1;
  padding: 0.3rem 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.td__prio button.on.p3 {
  border-color: #dc2626;
  background: color-mix(in srgb, #dc2626 12%, transparent);
  color: #dc2626;
}

.td__prio button.on.p2 {
  border-color: #ea8a0c;
  background: color-mix(in srgb, #ea8a0c 12%, transparent);
  color: #c2410c;
}

.td__prio button.on.p1 {
  border-color: #3b82f6;
  background: color-mix(in srgb, #3b82f6 12%, transparent);
  color: #2563eb;
}

.td__prio button.on.p0 {
  border-color: var(--color-text-secondary);
  color: var(--color-text-primary);
}

.td__repeat {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.td__week {
  display: flex;
  gap: 0.2rem;
}

.td__week button {
  flex: 1;
  height: 1.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  cursor: pointer;
}

.td__week button.on {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.td__hint {
  font-size: 0.76rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.td__focus {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.84rem;
}

.td__focus .btn {
  padding: 0.3rem 0.7rem;
  font-size: 0.82rem;
}

.td__sec h3 {
  margin-bottom: 0.4rem;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.td__sec h3 small {
  margin-left: 0.3rem;
  font-weight: 600;
}

.td__subs {
  margin: 0;
  padding: 0;
  list-style: none;
}

.td__subs li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.3rem 0;
  font-size: 0.88rem;
}

.td__subs li.done span {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.td__subs span {
  flex: 1;
  min-width: 0;
}

.td__subs button {
  display: inline-grid;
  place-items: center;
  width: 1.4rem;
  height: 1.4rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  opacity: 0;
}

.td__subs li:hover button,
.td__subs button:focus-visible {
  opacity: 1;
}

.td__addsub {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-text-secondary);
}

.td__addsub input {
  flex: 1;
  padding: 0.3rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.86rem;
}

.td__source {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 0.86rem;
}

.td__source:hover {
  border-color: var(--color-brand);
}

.td__source span {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.td__source small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.td__note {
  font-size: 0.86rem;
  resize: vertical;
}

.td__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.td__foot .btn {
  padding: 0.3rem 0.6rem;
  font-size: 0.82rem;
}

.td__foot .danger:hover {
  color: var(--color-danger);
}
</style>
