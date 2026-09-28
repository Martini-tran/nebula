<script setup lang="ts">
/**
 * 图标选择：一排可选的 Iconify 图标（值是「集合:名字」，如 lucide:target），单选。
 * 项目里不用表情当图标，要新图标从 lucide 里挑名字加进调用方的列表。
 */
import { Icon } from '@iconify/vue'

defineProps<{ modelValue: string; icons: { icon: string; label: string }[]; ariaLabel?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <div class="ip" role="radiogroup" :aria-label="ariaLabel ?? '图标'">
    <button
      v-for="i in icons"
      :key="i.icon"
      type="button"
      role="radio"
      :aria-checked="modelValue === i.icon"
      :aria-label="i.label"
      :title="i.label"
      :class="{ on: modelValue === i.icon }"
      @click="emit('update:modelValue', i.icon)"
    >
      <Icon :icon="i.icon" />
    </button>
  </div>
</template>

<style scoped>
.ip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.ip button {
  display: grid;
  place-items: center;
  width: 2.1rem;
  height: 2.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
  cursor: pointer;
}

.ip button:hover {
  border-color: var(--color-brand);
  color: var(--color-text-primary);
}

.ip button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.ip svg {
  width: 1.1rem;
  height: 1.1rem;
}
</style>
