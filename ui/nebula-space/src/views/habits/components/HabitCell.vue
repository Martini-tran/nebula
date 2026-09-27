<script setup lang="ts">
/**
 * 打卡格：勾选型点一下切换；计数 / 时长型显示扇形进度，点开小浮层调数值、写备注。
 * 没安排的日子（指定星期型）或「每周 N 次」型没做的日子画虚线圈，不制造负罪感。
 * 未来的日子不能打；过去只允许补前 2 天。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { HABIT_BACKFILL_DAYS, type Habit, type HabitLog } from '../../../types/habits'
import { isDone, isScheduled, progressOf } from '../../../utils/habitStats'
import { diffDays, todayYmd } from '../../../utils/date'

const props = defineProps<{ habit: Habit; date: string; log?: HabitLog }>()
const emit = defineEmits<{ set: [value: number, note?: string] }>()

const today = todayYmd()
const age = computed(() => diffDays(props.date, today))
const future = computed(() => props.date > today)
const locked = computed(() => future.value || age.value > HABIT_BACKFILL_DAYS)
const done = computed(() => isDone(props.habit, props.log))
const progress = computed(() => progressOf(props.habit, props.log))
const soft = computed(() => !isScheduled(props.habit, props.date) || props.habit.freq.type === 'weekly_n')

const open = ref(false)
const value = ref(0)
const note = ref('')

const title = computed(() => {
  if (future.value) return '还没到这天'
  if (locked.value && !props.log) return `只能补打前 ${HABIT_BACKFILL_DAYS} 天`
  const v = props.log?.value ?? 0
  if (props.habit.kind === 'check') return v ? '已完成' : '点一下打卡'
  return `${v} / ${props.habit.target} ${props.habit.kind === 'duration' ? '分钟' : props.habit.unit || '次'}`
})

const click = () => {
  if (locked.value) return
  if (props.habit.kind === 'check') {
    emit('set', done.value ? 0 : 1)
    return
  }
  value.value = props.log?.value ?? 0
  note.value = props.log?.note ?? ''
  open.value = true
}

const step = computed(() => (props.habit.kind === 'duration' ? 5 : 1))
const save = () => {
  open.value = false
  emit('set', Math.max(0, value.value), note.value.trim())
}
</script>

<template>
  <span class="hc-wrap">
    <button
      type="button"
      class="hc"
      :class="{ 'hc--done': done, 'hc--soft': soft && !log, 'hc--locked': locked, 'hc--today': date === today, 'hc--back': log?.backfilled }"
      :style="{ '--p': `${progress * 100}%` }"
      :title="title"
      :aria-label="`${habit.name} ${date}：${title}`"
      :disabled="locked && !log"
      @click="click"
    >
      <Icon v-if="done && habit.kind === 'check'" icon="lucide:check" />
      <span v-else-if="habit.kind !== 'check' && log" class="hc__num">{{ log.value }}</span>
    </button>
    <div v-if="open" class="hc-pop surface" @click.stop>
      <div class="hc-pop__row">
        <button type="button" aria-label="减少" @click="value = Math.max(0, value - step)"><Icon icon="lucide:minus" /></button>
        <input v-model.number="value" type="number" min="0" :step="step" aria-label="数值" @keydown.enter="save" />
        <button type="button" aria-label="增加" @click="value += step"><Icon icon="lucide:plus" /></button>
        <span>{{ habit.kind === 'duration' ? '分钟' : habit.unit || '次' }}</span>
      </div>
      <button type="button" class="hc-pop__fill" @click="value = habit.target">达标（{{ habit.target }}）</button>
      <input v-model="note" class="hc-pop__note" placeholder="备注（可选）" maxlength="100" @keydown.enter="save" />
      <div class="hc-pop__foot">
        <button type="button" class="btn btn--quiet" @click="open = false">取消</button>
        <button type="button" class="btn btn--primary" @click="save">保存</button>
      </div>
    </div>
    <div v-if="open" class="hc-mask" @click="open = false" />
  </span>
</template>

<style scoped>
.hc-wrap {
  position: relative;
  display: inline-grid;
  place-items: center;
}

.hc {
  position: relative;
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  padding: 0;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  background: conic-gradient(var(--color-accent) var(--p), transparent 0);
  color: #fff;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.hc:hover:not(:disabled) {
  transform: scale(1.08);
  border-color: var(--color-accent);
}

.hc--done {
  border-color: var(--color-accent);
  background: var(--color-accent);
}

.hc--soft {
  border-style: dashed;
}

.hc--today {
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.hc--locked {
  cursor: default;
}

.hc:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.hc--back::after {
  content: '补';
  position: absolute;
  right: -0.45rem;
  bottom: -0.3rem;
  padding: 0 0.15rem;
  border-radius: 3px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.55rem;
  font-weight: 700;
}

.hc svg {
  width: 1rem;
  height: 1rem;
}

.hc__num {
  font-size: 0.66rem;
  font-weight: 800;
  color: var(--color-text-primary);
  text-shadow: 0 0 3px var(--color-bg-surface);
}

.hc--done .hc__num {
  color: #fff;
  text-shadow: none;
}

.hc-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
}

.hc-pop {
  position: absolute;
  top: calc(100% + 0.4rem);
  left: 50%;
  z-index: 31;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 13.5rem;
  padding: 0.75rem;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  transform: translateX(-50%);
}

.hc-pop__row {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.hc-pop__row button {
  display: grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  cursor: pointer;
}

.hc-pop__row input {
  width: 3.4rem;
  padding: 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  text-align: center;
}

.hc-pop__fill {
  padding: 0.25rem;
  border: 1px dashed var(--color-accent);
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-accent-text);
  font-size: 0.8rem;
  cursor: pointer;
}

.hc-pop__note {
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.82rem;
}

.hc-pop__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.3rem;
}

.hc-pop__foot .btn {
  padding: 0.25rem 0.7rem;
  font-size: 0.8rem;
}
</style>
