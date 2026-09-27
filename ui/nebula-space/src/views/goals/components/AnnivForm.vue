<script setup lang="ts">
/**
 * 新建 / 编辑纪念日：倒数日、每年的纪念日（支持农历生日）、正数日。
 * 提醒可以顺手生成任务（「提前 7 天：买礼物」）；证件到期可以关联文件柜里的扫描件。底部一句话复述设置。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { addDays, monthDay, todayYmd, weekdayLabel } from '../../../utils/date'
import { lunarDayName, lunarMonthName, lunarOf } from '../../../utils/lunar'
import { nextOccurrence } from '../annivDates'
import { ANNIV_TYPES, type Anniversary, type AnniversarySaveRequest, type AnnivType } from '../../../types/goals'
import type { SpaceFile } from '../../../types/files'

const props = defineProps<{ editing: Anniversary | null; files: SpaceFile[] }>()
const emit = defineEmits<{ save: [body: AnniversarySaveRequest & { title: string; date: string }]; cancel: []; remove: [] }>()

const ICONS = ['🎂', '💍', '🎉', '🛂', '🏠', '🐱', '📅', '✈️', '🎓', '🩺', '🚗', '💼']
const REMIND: { value: number | null; label: string }[] = [
  { value: null, label: '不提醒' },
  { value: 0, label: '当天' },
  { value: 1, label: '提前 1 天' },
  { value: 7, label: '提前 7 天' },
  { value: 30, label: '提前 30 天' },
  { value: 90, label: '提前 90 天' },
]

const today = todayYmd()
const type = ref<AnnivType>('annual')
const icon = ref('🎂')
const title = ref('')
const calendar = ref<'solar' | 'lunar'>('solar')
const date = ref(today)
const lunarMonth = ref(1)
const lunarDay = ref(1)
const remindDays = ref<number | null>(7)
const createTask = ref(false)
const taskTitle = ref('')
const fileId = ref('')
const note = ref('')

const reset = () => {
  const a = props.editing
  type.value = a?.type ?? 'annual'
  icon.value = a?.icon ?? '🎂'
  title.value = a?.title ?? ''
  calendar.value = a?.calendar ?? 'solar'
  date.value = a?.date ?? today
  const l = lunarOf(a?.date ?? today)
  lunarMonth.value = a?.lunarMonth ?? l.month
  lunarDay.value = a?.lunarDay ?? l.day
  remindDays.value = a ? a.remindDays : 7
  createTask.value = a?.createTask ?? false
  taskTitle.value = a?.taskTitle ?? ''
  fileId.value = a?.fileId ? String(a.fileId) : ''
  note.value = a?.note ?? ''
}
watch(() => props.editing, reset, { immediate: true })

// 公历日期改了，农历跟着换算（反之亦然只在保存时用）
watch(date, (d) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(d)) return
  const l = lunarOf(d)
  if (!l.leap) {
    lunarMonth.value = l.month
    lunarDay.value = l.day
  }
})

const docs = computed(() => props.files.filter((f) => !f.isFolder && !f.deleteTime))

const draft = computed<Anniversary>(() => ({
  id: props.editing?.id ?? 'draft',
  title: title.value,
  icon: icon.value,
  type: type.value,
  date: date.value,
  calendar: type.value === 'annual' ? calendar.value : 'solar',
  lunarMonth: type.value === 'annual' && calendar.value === 'lunar' ? lunarMonth.value : null,
  lunarDay: type.value === 'annual' && calendar.value === 'lunar' ? lunarDay.value : null,
  remindDays: type.value === 'countup' ? null : remindDays.value,
  createTask: type.value !== 'countup' && remindDays.value !== null && createTask.value,
  taskTitle: taskTitle.value,
  taskFor: props.editing?.taskFor ?? null,
  fileId: fileId.value || null,
  note: note.value,
  tag: props.editing?.tag ?? '',
  createTime: props.editing?.createTime ?? '',
}))

/** 复述：今年是哪天、哪天提醒、会不会加任务 */
const preview = computed(() => {
  const a = draft.value
  if (!a.title.trim()) return ''
  if (a.type === 'countup') return `从 ${monthDay(a.date)} 开始算，今天是第 ${Math.max(0, Math.round((Date.parse(today) - Date.parse(a.date)) / 86_400_000))} 天。`
  const next = nextOccurrence(a, today)
  if (!next) return ''
  const when = `${next.slice(0, 4) === today.slice(0, 4) ? '今年' : `${next.slice(0, 4)} 年`}是 ${monthDay(next)} ${weekdayLabel(next)}`
  const remind = a.remindDays === null ? '' : a.remindDays === 0 ? '，当天提醒你' : `，${monthDay(addDays(next, -a.remindDays))}提醒你`
  const task = a.createTask ? `并在任务里加上「${a.taskTitle.trim() || a.title}」` : ''
  const repeat = a.type === 'annual' ? (a.calendar === 'lunar' ? '每年自动按农历换算。' : '每年重复。') : ''
  return `${when}${remind}${task}。${repeat}`
})

const valid = computed(() => Boolean(title.value.trim()) && /^\d{4}-\d{2}-\d{2}$/.test(date.value))

const submit = () => {
  if (!valid.value) return
  const { id: _id, createTime: _c, ...rest } = draft.value
  // 改了日期或提醒，已生成任务的标记作废，按新的日子重新生成
  const changed = props.editing && (props.editing.date !== rest.date || props.editing.remindDays !== rest.remindDays || props.editing.lunarDay !== rest.lunarDay || props.editing.lunarMonth !== rest.lunarMonth)
  emit('save', { ...rest, title: rest.title.trim(), taskFor: changed ? null : rest.taskFor })
}
</script>

<template>
  <form class="af" @submit.prevent="submit">
    <div>
      <h3>类型</h3>
      <div class="chips" role="radiogroup" aria-label="类型">
        <button v-for="(t, key) in ANNIV_TYPES" :key="key" type="button" role="radio" :aria-checked="type === key" :class="{ on: type === key }" :title="t.hint" @click="type = key">{{ t.label }}</button>
      </div>
    </div>
    <div>
      <h3>名称</h3>
      <div class="name">
        <select v-model="icon" aria-label="图标">
          <option v-for="i in ICONS" :key="i" :value="i">{{ i }}</option>
        </select>
        <input v-model="title" type="text" placeholder="妈妈生日、护照到期…" aria-label="名称" maxlength="30" />
      </div>
    </div>
    <div>
      <h3>日期</h3>
      <div v-if="type === 'annual'" class="chips">
        <button type="button" :class="{ on: calendar === 'solar' }" @click="calendar = 'solar'">公历</button>
        <button type="button" :class="{ on: calendar === 'lunar' }" @click="calendar = 'lunar'">农历</button>
      </div>
      <div v-if="type === 'annual' && calendar === 'lunar'" class="lunar">
        <select v-model.number="lunarMonth" aria-label="农历月">
          <option v-for="m in 12" :key="m" :value="m">{{ lunarMonthName(m) }}</option>
        </select>
        <select v-model.number="lunarDay" aria-label="农历日">
          <option v-for="d in 30" :key="d" :value="d">{{ lunarDayName(d) }}</option>
        </select>
      </div>
      <input v-else v-model="date" type="date" class="date" :aria-label="type === 'countup' ? '从哪天开始' : '日期'" />
    </div>
    <div v-if="type !== 'countup'">
      <h3>提醒</h3>
      <div class="chips" role="radiogroup" aria-label="提醒">
        <button v-for="r in REMIND" :key="String(r.value)" type="button" role="radio" :aria-checked="remindDays === r.value" :class="{ on: remindDays === r.value }" @click="remindDays = r.value">{{ r.label }}</button>
      </div>
      <label v-if="remindDays !== null" class="task">
        <input v-model="createTask" type="checkbox" />
        同时生成任务
        <input v-if="createTask" v-model="taskTitle" type="text" :placeholder="type === 'annual' ? '买生日礼物' : '续签 / 办理'" aria-label="任务名" maxlength="30" />
      </label>
    </div>
    <div v-if="type === 'countdown'">
      <h3>关联文件 <small>证件、合同的扫描件</small></h3>
      <select v-model="fileId" class="date" aria-label="关联文件">
        <option value="">不关联</option>
        <option v-for="f in docs" :key="f.id" :value="String(f.id)">{{ f.name }}</option>
      </select>
    </div>
    <div>
      <h3>备注</h3>
      <input v-model="note" type="text" class="date" placeholder="可选，例如：月租 ¥ 3,500" maxlength="40" />
    </div>
    <p v-if="preview" class="preview"><Icon icon="lucide:info" />{{ preview }}</p>
    <div class="acts">
      <button v-if="editing" type="button" class="btn btn--quiet danger" @click="emit('remove')">删除</button>
      <span class="grow" />
      <button v-if="editing" type="button" class="btn btn--ghost" @click="emit('cancel')">取消</button>
      <button type="submit" class="btn btn--primary" :disabled="!valid">{{ editing ? '保存' : '添加' }}</button>
    </div>
  </form>
</template>

<style scoped>
.af {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 0.9rem 1rem 1rem;
}

h3 {
  margin-bottom: 0.4rem;
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

h3 small {
  font-weight: 400;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.chips button {
  padding: 0.22rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.8rem;
  cursor: pointer;
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.name {
  display: flex;
  gap: 0.4rem;
}

.name select,
.name input,
.lunar select,
.date,
.task input[type='text'] {
  padding: 0.42rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  font-size: 0.88rem;
}

.name select {
  width: 3.9rem;
  flex: none;
  font-size: 1.05rem;
}

.name input,
.date {
  flex: 1;
  width: 100%;
  min-width: 0;
}

.lunar {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.4rem;
}

.task {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.55rem;
  font-size: 0.84rem;
}

.task input[type='text'] {
  flex: 1;
  min-width: 8rem;
}

.preview {
  display: flex;
  gap: 0.4rem;
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  font-size: 0.8rem;
  line-height: 1.6;
}

.preview svg {
  flex: none;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.acts {
  display: flex;
  gap: 0.4rem;
}

.grow {
  flex: 1;
}

.danger:hover {
  color: var(--color-danger);
}
</style>
