<script setup lang="ts">
/**
 * 新建 / 编辑习惯：先给常见预设（点一下填好名称、图标、类型与目标），再允许自定义。
 * 频率三选一：每天 / 每周 N 次 / 指定星期几。底部用一句话复述设置结果。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { createHabit, updateHabit } from '../../../api/habits'
import { errorText } from '../../../composables/useToast'
import { describeHabit } from '../../../utils/habitStats'
import type { Habit, HabitFreq, HabitKind } from '../../../types/habits'

const props = defineProps<{ open: boolean; habit?: Habit | null }>()
const emit = defineEmits<{ close: []; saved: [habit: Habit] }>()

const PRESETS: { icon: string; name: string; kind: HabitKind; target: number; unit: string; reminders?: string[] }[] = [
  { icon: '🏃', name: '跑步', kind: 'check', target: 1, unit: '', reminders: ['07:00'] },
  { icon: '💧', name: '喝水', kind: 'count', target: 8, unit: '杯', reminders: ['10:00', '15:00'] },
  { icon: '📖', name: '阅读', kind: 'duration', target: 30, unit: '分钟' },
  { icon: '🧘', name: '冥想', kind: 'duration', target: 10, unit: '分钟' },
  { icon: '✍️', name: '写日记', kind: 'check', target: 1, unit: '', reminders: ['22:00'] },
  { icon: '🌙', name: '早睡', kind: 'check', target: 1, unit: '', reminders: ['23:00'] },
]
const ICONS = ['✅', '🏃', '💧', '📖', '🧘', '✍️', '🌙', '🏋️', '🥗', '🎸', '🧹', '💊']

const icon = ref('✅')
const name = ref('')
const kind = ref<HabitKind>('check')
const target = ref(1)
const unit = ref('')
const freqType = ref<HabitFreq['type']>('daily')
const weeklyN = ref(3)
const weekdays = ref<number[]>([1, 3, 5])
const reminders = ref<string[]>([])
const reminderDraft = ref('')
const fromFocus = ref(false)
const saving = ref(false)
const error = ref('')
const submitted = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const h = props.habit
    icon.value = h?.icon ?? '✅'
    name.value = h?.name ?? ''
    kind.value = h?.kind ?? 'check'
    target.value = h?.target ?? 1
    unit.value = h?.unit ?? ''
    freqType.value = h?.freq.type ?? 'daily'
    weeklyN.value = h?.freq.type === 'weekly_n' ? h.freq.n : 3
    weekdays.value = h?.freq.type === 'weekdays' ? [...h.freq.days] : [1, 3, 5]
    reminders.value = [...(h?.reminders ?? [])]
    fromFocus.value = h?.fromFocus ?? false
    error.value = ''
    submitted.value = false
  },
)

watch(kind, (k, old) => {
  if (!old || props.habit) return
  if (k === 'check') target.value = 1
  if (k === 'count' && target.value < 2) target.value = 8
  if (k === 'duration' && target.value < 5) target.value = 30
})

const applyPreset = (p: (typeof PRESETS)[number]) => {
  icon.value = p.icon
  name.value = p.name
  kind.value = p.kind
  target.value = p.target
  unit.value = p.unit
  reminders.value = [...(p.reminders ?? [])]
  fromFocus.value = p.kind === 'duration'
}

const freq = computed<HabitFreq>(() =>
  freqType.value === 'daily'
    ? { type: 'daily' }
    : freqType.value === 'weekly_n'
      ? { type: 'weekly_n', n: weeklyN.value }
      : { type: 'weekdays', days: [...weekdays.value] },
)

const summary = computed(() =>
  name.value.trim()
    ? describeHabit({ name: name.value.trim(), kind: kind.value, target: target.value, unit: unit.value, freq: freq.value, reminders: reminders.value })
    : '',
)

const toggleDay = (d: number) => {
  weekdays.value = weekdays.value.includes(d) ? weekdays.value.filter((x) => x !== d) : [...weekdays.value, d]
}

const addReminder = () => {
  const value = reminderDraft.value
  if (value && !reminders.value.includes(value)) reminders.value = [...reminders.value, value].sort()
  reminderDraft.value = ''
}

const submit = async () => {
  submitted.value = true
  if (!name.value.trim() || saving.value) return
  if (freqType.value === 'weekdays' && !weekdays.value.length) {
    error.value = '至少选一天'
    return
  }
  saving.value = true
  error.value = ''
  const body = {
    name: name.value.trim(),
    icon: icon.value,
    kind: kind.value,
    target: kind.value === 'check' ? 1 : Math.max(1, target.value),
    unit: kind.value === 'count' ? unit.value.trim() : kind.value === 'duration' ? '分钟' : '',
    freq: freq.value,
    reminders: reminders.value,
    fromFocus: kind.value === 'duration' && fromFocus.value,
  }
  try {
    emit('saved', props.habit ? await updateHabit(props.habit.id, body) : await createHabit(body))
  } catch (err) {
    error.value = errorText(err, '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseDialog :open="open" :title="habit ? '编辑习惯' : '新习惯'" width="32rem" :locked="saving" @close="emit('close')">
    <form class="form hd" novalidate @submit.prevent="submit">
      <div v-if="!habit" class="field">
        <span class="field__label">常用</span>
        <div class="presets">
          <button v-for="p in PRESETS" :key="p.name" type="button" @click="applyPreset(p)">{{ p.icon }} {{ p.name }}</button>
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="hb-name">名称 <span class="field__required">*</span></label>
        <div class="name">
          <select v-model="icon" class="field__input name__icon" aria-label="图标">
            <option v-for="i in ICONS" :key="i" :value="i">{{ i }}</option>
          </select>
          <input id="hb-name" v-model="name" class="field__input" :class="{ 'field__input--invalid': submitted && !name.trim() }" maxlength="30" placeholder="比如：喝水" />
        </div>
      </div>

      <div class="field">
        <span class="field__label">打卡方式</span>
        <div class="opts">
          <label :class="{ on: kind === 'check' }"><input v-model="kind" type="radio" value="check" /><b>勾选</b><small>做了就行</small></label>
          <label :class="{ on: kind === 'count' }"><input v-model="kind" type="radio" value="count" /><b>计数</b><small>每天 N 次</small></label>
          <label :class="{ on: kind === 'duration' }"><input v-model="kind" type="radio" value="duration" /><b>时长</b><small>每天 N 分钟</small></label>
        </div>
      </div>

      <div v-if="kind !== 'check'" class="field">
        <span class="field__label">目标</span>
        <div class="target">
          <input v-model.number="target" type="number" min="1" class="field__input" aria-label="目标值" />
          <input v-if="kind === 'count'" v-model="unit" class="field__input" placeholder="单位，如「杯」" maxlength="6" aria-label="单位" />
          <span v-else>分钟 / 天</span>
        </div>
        <label v-if="kind === 'duration'" class="check"><input v-model="fromFocus" type="checkbox" />专注记录自动累加进来（任务标题里含习惯名时）</label>
      </div>

      <div class="field">
        <span class="field__label">频率</span>
        <div class="seg">
          <button type="button" :class="{ on: freqType === 'daily' }" @click="freqType = 'daily'">每天</button>
          <button type="button" :class="{ on: freqType === 'weekly_n' }" @click="freqType = 'weekly_n'">每周 N 次</button>
          <button type="button" :class="{ on: freqType === 'weekdays' }" @click="freqType = 'weekdays'">指定星期</button>
        </div>
        <label v-if="freqType === 'weekly_n'" class="inline">每周 <input v-model.number="weeklyN" type="number" min="1" max="7" class="field__input" /> 次，哪天做都行</label>
        <div v-if="freqType === 'weekdays'" class="days">
          <button v-for="d in [1, 2, 3, 4, 5, 6, 0]" :key="d" type="button" :class="{ on: weekdays.includes(d) }" @click="toggleDay(d)">
            {{ '日一二三四五六'[d] }}
          </button>
        </div>
      </div>

      <div class="field">
        <span class="field__label">提醒 <span class="field__hint">可选</span></span>
        <div class="reminders">
          <span v-for="r in reminders" :key="r" class="tag">
            {{ r }}<button type="button" :aria-label="`删除提醒 ${r}`" @click="reminders = reminders.filter((x) => x !== r)"><Icon icon="lucide:x" /></button>
          </span>
          <input v-model="reminderDraft" type="time" class="field__input" aria-label="添加提醒时间" @change="addReminder" />
        </div>
      </div>

      <p v-if="summary" class="summary"><Icon icon="lucide:message-circle" />{{ summary }}</p>
      <p v-if="error" class="form__error">{{ error }}</p>

      <div class="form__actions">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="submit" :disabled="saving">{{ habit ? '保存' : '创建' }}</button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.hd {
  gap: 1rem;
}

.presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.presets button {
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
}

.presets button:hover {
  border-color: var(--color-brand);
}

.name {
  display: flex;
  gap: 0.4rem;
}

.name__icon {
  width: 4.2rem;
  flex: none;
  font-size: 1.1rem;
}

.opts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.4rem;
}

.opts label {
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.opts label.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.opts input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.opts b {
  font-size: 0.9rem;
}

.opts small {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.target,
.inline {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.88rem;
}

.target .field__input,
.inline .field__input {
  width: 6rem;
}

.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 0.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.days {
  display: flex;
  gap: 0.25rem;
}

.days button {
  width: 2.2rem;
  height: 2.2rem;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  cursor: pointer;
}

.days button.on {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.reminders {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.reminders .tag button {
  display: inline-grid;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}

.reminders input {
  width: 8rem;
  padding-block: 0.3rem;
}

.summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.88rem;
}
</style>
