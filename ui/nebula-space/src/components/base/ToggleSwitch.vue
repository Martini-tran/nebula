<script setup lang="ts">
/** 开关：设置页逐项开关用。v-model 绑定布尔值 */
const model = defineModel<boolean>({ required: true })
defineProps<{ label: string; disabled?: boolean }>()
</script>

<template>
  <button
    class="switch"
    :class="{ on: model }"
    type="button"
    role="switch"
    :aria-checked="model"
    :aria-label="label"
    :disabled="disabled"
    @click="model = !model"
  />
</template>

<style scoped>
.switch {
  position: relative;
  flex: none;
  width: 2.3rem;
  height: 1.3rem;
  border: 0;
  border-radius: 999px;
  background: var(--color-border);
  cursor: pointer;
  transition: background 0.18s ease;
}

.switch::after {
  content: '';
  position: absolute;
  top: 0.18rem;
  left: 0.18rem;
  width: 0.94rem;
  height: 0.94rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  transition: transform 0.18s var(--ease-soft);
}

.switch.on {
  background: var(--color-brand);
}

.switch.on::after {
  transform: translateX(1rem);
}

.switch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.switch:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .switch,
  .switch::after {
    transition: none;
  }
}
</style>
