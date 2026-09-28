<script setup lang="ts">
/**
 * 24 小时制时间选择（HH:mm）。
 * 原生 <input type="time"> 跟随系统区域设置，中文 Windows 下显示成「上午/下午」12 小时制，
 * 小时里没有 00，这里用两个下拉固定成 00–23 时、按 step 分钟一档。
 * 先选小时时分钟自动补 00；clearable 时小时可选「--」清空。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 分钟间隔 */
    step?: number
    clearable?: boolean
    disabled?: boolean
    ariaLabel?: string
    title?: string
  }>(),
  { modelValue: '', step: 5, clearable: false, disabled: false, ariaLabel: '时间', title: '' },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** 用户改动后的完整值，清空时为空串 */
  change: [value: string]
}>()

const pad = (n: number) => String(n).padStart(2, '0')
const HOURS = Array.from({ length: 24 }, (_, i) => pad(i))

const hour = computed(() => (props.modelValue ?? '').slice(0, 2))
const minute = computed(() => (props.modelValue ?? '').slice(3, 5))

/** 已有值不在间隔上（如 07:13）也要能显示出来 */
const minutes = computed(() => {
  const list = Array.from({ length: Math.ceil(60 / props.step) }, (_, i) => pad(i * props.step))
  if (minute.value && !list.includes(minute.value)) list.push(minute.value)
  return list.sort()
})

const set = (value: string) => {
  emit('update:modelValue', value)
  emit('change', value)
}

const onHour = (event: Event) => {
  const h = (event.target as HTMLSelectElement).value
  set(h ? `${h}:${minute.value || '00'}` : '')
}

const onMinute = (event: Event) => {
  const m = (event.target as HTMLSelectElement).value
  set(`${hour.value || '00'}:${m}`)
}
</script>

<template>
  <span class="ts field__input" :class="{ 'ts--disabled': disabled }" :title="title" role="group" :aria-label="ariaLabel">
    <select :value="hour" :disabled="disabled" :aria-label="`${ariaLabel}：时`" @change="onHour">
      <option v-if="clearable || !hour" value="">--</option>
      <option v-for="h in HOURS" :key="h" :value="h">{{ h }}</option>
    </select>
    <span class="ts__sep">:</span>
    <select :value="minute" :disabled="disabled || !hour" :aria-label="`${ariaLabel}：分`" @change="onMinute">
      <option v-if="!minute" value="">--</option>
      <option v-for="m in minutes" :key="m" :value="m">{{ m }}</option>
    </select>
  </span>
</template>

<style scoped>
.ts {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
  width: auto;
  font-variant-numeric: tabular-nums;
}

.ts select {
  padding: 0 0.1rem;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  appearance: none;
  text-align: center;
}

.ts select:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 1px;
  border-radius: 2px;
}

.ts select:disabled {
  cursor: not-allowed;
}

.ts__sep {
  color: var(--color-text-secondary);
}

.ts--disabled {
  opacity: 0.6;
}
</style>
