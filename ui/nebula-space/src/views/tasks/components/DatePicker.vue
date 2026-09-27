<script setup lang="ts">
/**
 * 日期选择：先给四个快捷项（今天 / 明天 / 下周一 / 不定），再是月历；
 * 已有任务的日子下方带小绿点，避免把事情都堆在同一天。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { addDays, fromYmd, nextWeekday, startOfWeek, todayYmd, toYmd, weekdayLabel, weekHeads } from '../../../utils/date'

const props = defineProps<{ modelValue: string | null; marked?: Set<string> }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()

const today = todayYmd()
const monthOf = (ymd: string) => ymd.slice(0, 7)
const cursor = ref(monthOf(props.modelValue ?? today))
watch(
  () => props.modelValue,
  (value) => (cursor.value = monthOf(value ?? today)),
)

const quick = computed(() => [
  { label: '今天', sub: weekdayLabel(today), value: today },
  { label: '明天', sub: weekdayLabel(addDays(today, 1)), value: addDays(today, 1) },
  { label: '下周一', sub: nextWeekday(1).slice(5).replace('-', '/'), value: nextWeekday(1) },
  { label: '不定', sub: '收件箱', value: null },
])

const title = computed(() => {
  const [y, m] = cursor.value.split('-')
  return `${y} 年 ${Number(m)} 月`
})

const shiftMonth = (delta: number) => {
  const date = fromYmd(`${cursor.value}-01`)
  date.setMonth(date.getMonth() + delta)
  cursor.value = toYmd(date).slice(0, 7)
}

/** 6 行 × 7 列，从周一开始 */
const days = computed(() => {
  const start = startOfWeek(`${cursor.value}-01`)
  return Array.from({ length: 42 }, (_, i) => {
    const ymd = addDays(start, i)
    return { ymd, day: Number(ymd.slice(8)), inMonth: monthOf(ymd) === cursor.value }
  })
})
</script>

<template>
  <div class="dp">
    <div class="dp__quick">
      <button
        v-for="q in quick"
        :key="q.label"
        type="button"
        :class="{ on: modelValue === q.value }"
        @click="emit('update:modelValue', q.value)"
      >
        <b>{{ q.label }}</b><small>{{ q.sub }}</small>
      </button>
    </div>
    <div class="dp__head">
      <button type="button" aria-label="上个月" @click="shiftMonth(-1)"><Icon icon="lucide:chevron-left" /></button>
      <span>{{ title }}</span>
      <button type="button" aria-label="下个月" @click="shiftMonth(1)"><Icon icon="lucide:chevron-right" /></button>
    </div>
    <div class="dp__grid" role="grid">
      <span v-for="w in weekHeads()" :key="w" class="dp__w">{{ w }}</span>
      <button
        v-for="d in days"
        :key="d.ymd"
        type="button"
        class="dp__d"
        :class="{
          'dp__d--out': !d.inMonth,
          'dp__d--today': d.ymd === today,
          'dp__d--on': d.ymd === modelValue,
          'dp__d--past': d.ymd < today,
        }"
        :aria-label="d.ymd"
        :aria-pressed="d.ymd === modelValue"
        @click="emit('update:modelValue', d.ymd)"
      >
        {{ d.day }}
        <i v-if="marked?.has(d.ymd)" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.dp {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  width: 17rem;
}

.dp__quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.3rem;
}

.dp__quick button {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.35rem 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.82rem;
  cursor: pointer;
}

.dp__quick button small {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.dp__quick button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.dp__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.86rem;
  font-weight: 700;
}

.dp__head button {
  display: inline-grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.dp__head button:hover {
  background: var(--color-bg-soft);
}

.dp__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.15rem;
  text-align: center;
}

.dp__w {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.dp__d {
  position: relative;
  height: 2rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-primary);
  font-size: 0.84rem;
  cursor: pointer;
}

.dp__d:hover {
  background: var(--color-bg-soft);
}

.dp__d--out,
.dp__d--past {
  color: var(--color-text-secondary);
  opacity: 0.6;
}

.dp__d--today {
  color: var(--color-brand);
  font-weight: 800;
  opacity: 1;
}

.dp__d--on,
.dp__d--on:hover {
  background: var(--color-brand);
  color: var(--color-on-brand);
  opacity: 1;
}

.dp__d i {
  position: absolute;
  bottom: 0.2rem;
  left: 50%;
  width: 0.28rem;
  height: 0.28rem;
  border-radius: 50%;
  background: var(--color-accent);
  transform: translateX(-50%);
}
</style>
