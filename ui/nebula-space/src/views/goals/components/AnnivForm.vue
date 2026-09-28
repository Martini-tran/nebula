<script setup lang="ts">
/**
 * 新建 / 编辑纪念日：倒数日、每年的纪念日（支持农历生日）、正数日。
 * 提醒可以顺手生成任务（「提前 7 天：买礼物」）；证件到期可以关联文件柜里的扫描件。底部一句话复述设置。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import IconPicker from '../../../components/base/IconPicker.vue'
import { ANNIV_ICONS, ANNIV_ICON_DEFAULT, iconOr } from '../../../config/icons'
import { addDays, monthDay, todayYmd, weekdayLabel } from '../../../utils/date'
import { lunarDayName, lunarMonthName, lunarOf } from '../../../utils/lunar'
import { nextOccurrence } from '../annivDates'
import { ANNIV_TYPES, type Anniversary, type AnniversarySaveRequest, type AnnivType } from '../../../types/goals'
import type { SpaceFile } from '../../../types/files'

const props = defineProps<{ editing: Anniversary | null; files: SpaceFile[] }>()
const emit = defineEmits<{ save: [body: AnniversarySaveRequest & { title: string; date: string }]; cancel: []; remove: [] }>()

/** 三种类型在分段按钮上的短名，完整说明在下面的提示里 */
const TYPE_SHORT: Record<AnnivType, string> = { countdown: '倒数日', annual: '每年纪念日', countup: '正数日' }
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
const icon = ref('lucide:cake')
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
  icon.value = a ? iconOr(a.icon, ANNIV_ICON_DEFAULT) : 'lucide:cake'
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

const lunar = computed(() => type.value === 'annual' && calendar.value === 'lunar')
const dateOk = computed(() => /^\d{4}-\d{2}-\d{2}$/.test(date.value))
/** 点过一次提交后才标红 */
const submitted = ref(false)
const problem = computed(() => (!title.value.trim() ? '写一下名称' : !lunar.value && !dateOk.value ? '选一个日期' : ''))

const submit = () => {
  submitted.value = true
  if (problem.value) return
  const { id: _id, createTime: _c, ...rest } = draft.value
  // 改了日期或提醒，已生成任务的标记作废，按新的日子重新生成
  const changed = props.editing && (props.editing.date !== rest.date || props.editing.remindDays !== rest.remindDays || props.editing.lunarDay !== rest.lunarDay || props.editing.lunarMonth !== rest.lunarMonth)
  emit('save', { ...rest, title: rest.title.trim(), taskFor: changed ? null : rest.taskFor })
}
</script>

<template>
  <form class="af" novalidate @submit.prevent="submit">
    <div class="field">
      <span class="field__label">类型</span>
      <div class="seg seg--full" role="radiogroup" aria-label="类型">
        <button v-for="(label, key) in TYPE_SHORT" :key="key" type="button" role="radio" :aria-checked="type === key" :class="{ on: type === key }" @click="type = key">{{ label }}</button>
      </div>
      <span class="field__hint">{{ ANNIV_TYPES[type].hint }}</span>
    </div>

    <div class="field">
      <label class="field__label" for="af-title">名称 <span class="field__required">*</span></label>
      <input id="af-title" v-model="title" class="field__input" :class="{ 'field__input--invalid': submitted && !title.trim() }" type="text" placeholder="妈妈生日、护照到期…" maxlength="30" />
    </div>

    <div class="field">
      <span class="field__label">图标</span>
      <IconPicker v-model="icon" :icons="ANNIV_ICONS" aria-label="纪念日图标" />
    </div>

    <div class="field">
      <div class="field__head">
        <label class="field__label" :for="lunar ? 'af-lmonth' : 'af-date'">{{ type === 'countup' ? '从哪天开始' : '日期' }} <span class="field__required">*</span></label>
        <div v-if="type === 'annual'" class="seg seg--sm" role="radiogroup" aria-label="历法">
          <button type="button" role="radio" :aria-checked="calendar === 'solar'" :class="{ on: calendar === 'solar' }" @click="calendar = 'solar'">公历</button>
          <button type="button" role="radio" :aria-checked="calendar === 'lunar'" :class="{ on: calendar === 'lunar' }" @click="calendar = 'lunar'">农历</button>
        </div>
      </div>
      <div v-if="lunar" class="lunar">
        <select id="af-lmonth" v-model.number="lunarMonth" class="field__input" aria-label="农历月">
          <option v-for="m in 12" :key="m" :value="m">{{ lunarMonthName(m) }}</option>
        </select>
        <select v-model.number="lunarDay" class="field__input" aria-label="农历日">
          <option v-for="d in 30" :key="d" :value="d">{{ lunarDayName(d) }}</option>
        </select>
      </div>
      <input v-else id="af-date" v-model="date" type="date" class="field__input" :class="{ 'field__input--invalid': submitted && !dateOk }" />
      <span v-if="lunar" class="field__hint">每年按农历换算成当年的公历日期</span>
    </div>

    <div v-if="type !== 'countup'" class="field">
      <span class="field__label">提醒</span>
      <div class="chips" role="radiogroup" aria-label="提醒">
        <button v-for="r in REMIND" :key="String(r.value)" type="button" role="radio" :aria-checked="remindDays === r.value" :class="{ on: remindDays === r.value }" @click="remindDays = r.value">{{ r.label }}</button>
      </div>
      <label v-if="remindDays !== null" class="check"><input v-model="createTask" type="checkbox" />到提醒那天在任务里加一条</label>
      <input
        v-if="remindDays !== null && createTask"
        v-model="taskTitle"
        class="field__input"
        type="text"
        :placeholder="type === 'annual' ? '任务名，例如：买生日礼物' : '任务名，例如：续签 / 办理'"
        aria-label="任务名"
        maxlength="30"
      />
    </div>

    <div v-if="type === 'countdown'" class="field">
      <label class="field__label" for="af-file">关联文件 <span class="field__hint">证件、合同的扫描件</span></label>
      <select id="af-file" v-model="fileId" class="field__input">
        <option value="">不关联</option>
        <option v-for="f in docs" :key="f.id" :value="String(f.id)">{{ f.name }}</option>
      </select>
    </div>

    <div class="field">
      <label class="field__label" for="af-note">备注 <span class="field__hint">可选</span></label>
      <input id="af-note" v-model="note" class="field__input" type="text" placeholder="例如：月租 ¥ 3,500" maxlength="40" />
    </div>

    <p v-if="preview" class="preview"><Icon icon="lucide:info" />{{ preview }}</p>
    <p v-if="submitted && problem" class="form__error">{{ problem }}</p>

    <div class="acts">
      <button v-if="editing" type="button" class="btn btn--quiet danger" @click="emit('remove')">删除</button>
      <span class="grow" />
      <button v-if="editing" type="button" class="btn btn--ghost" @click="emit('cancel')">取消</button>
      <button type="submit" class="btn btn--primary">{{ editing ? '保存' : '添加' }}</button>
    </div>
  </form>
</template>

<style scoped>
.af {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem 1.1rem 1.1rem;
}

.field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.field__label .field__hint {
  font-weight: 400;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg--full {
  display: flex;
}

.seg--full button {
  flex: 1;
}

.seg button {
  padding: 0.3rem 0.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
}

.seg--sm button {
  padding: 0.18rem 0.6rem;
  font-size: 0.8rem;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.lunar {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.4rem;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.chips button {
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.82rem;
  cursor: pointer;
}

.chips button:hover {
  border-color: var(--color-brand);
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.86rem;
  cursor: pointer;
}

.preview {
  display: flex;
  gap: 0.4rem;
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  font-size: 0.82rem;
  line-height: 1.6;
}

.preview svg {
  flex: none;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.acts {
  display: flex;
  gap: 0.5rem;
}

.grow {
  flex: 1;
}

.danger:hover {
  color: var(--color-danger);
}
</style>
