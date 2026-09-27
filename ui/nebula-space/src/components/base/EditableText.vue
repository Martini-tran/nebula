<script setup lang="ts">
/**
 * 行内可编辑文字（contenteditable）：看起来就是正文，点进去直接改。
 * 只收纯文本，回车结束编辑；外部改了值且当前没在编辑时才同步，避免光标乱跳。
 */
import { onMounted, ref, watch } from 'vue'

const model = defineModel<string>({ required: true })
defineProps<{ label: string; placeholder?: string }>()
const emit = defineEmits<{ done: [] }>()

const el = ref<HTMLElement | null>(null)

const sync = () => {
  if (el.value && document.activeElement !== el.value && el.value.textContent !== model.value) el.value.textContent = model.value
}
onMounted(sync)
watch(model, sync)

const onInput = () => {
  model.value = (el.value?.textContent ?? '').replace(/\n/g, ' ')
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault()
    el.value?.blur()
    emit('done')
  }
}

const onPaste = (event: ClipboardEvent) => {
  event.preventDefault()
  const text = (event.clipboardData?.getData('text/plain') ?? '').replace(/\s*\n\s*/g, ' ')
  document.execCommand('insertText', false, text)
}

defineExpose({ focus: () => el.value?.focus() })
</script>

<template>
  <span
    ref="el"
    class="et"
    contenteditable="true"
    role="textbox"
    :aria-label="label"
    :data-placeholder="placeholder"
    spellcheck="false"
    @input="onInput"
    @keydown="onKeydown"
    @paste="onPaste"
    @blur="sync"
  />
</template>

<style scoped>
.et {
  outline: none;
  border-radius: 0.2rem;
  cursor: text;
}

.et:focus {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-brand) 35%, transparent);
}

.et:empty::before {
  content: attr(data-placeholder);
  color: var(--color-text-secondary);
}
</style>
