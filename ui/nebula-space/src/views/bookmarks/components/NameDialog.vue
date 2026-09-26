<script setup lang="ts">
import { ref, watch } from 'vue'
import { Icon } from '@iconify/vue'

/**
 * 单字段命名弹窗：新建/重命名目录、新建标签共用。
 * 保存逻辑由调用方通过 save 传入，出错时就地显示后端文案。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    label: string
    initial?: string
    maxlength?: number
    save: (name: string) => Promise<void>
  }>(),
  { initial: '', maxlength: 100 },
)
const emit = defineEmits<{ close: [] }>()

const name = ref('')
const submitting = ref(false)
const submitted = ref(false)
const errorMessage = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    name.value = props.initial
    submitted.value = false
    errorMessage.value = ''
  },
)

const submit = async () => {
  submitted.value = true
  if (!name.value.trim() || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await props.save(name.value.trim())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <transition name="dialog">
    <div v-if="open" class="dialog-mask" @click.self="emit('close')">
      <div class="dialog dialog--sm surface" role="dialog" aria-modal="true" aria-labelledby="name-dialog-title">
        <header class="dialog__head">
          <h2 id="name-dialog-title" class="dialog__title">{{ title }}</h2>
          <button class="btn btn--quiet dialog__close" type="button" aria-label="关闭" @click="emit('close')">
            <Icon icon="lucide:x" />
          </button>
        </header>
        <form class="form" novalidate @submit.prevent="submit">
          <div class="field">
            <label class="field__label" for="name-dialog-input">{{ label }}</label>
            <input
              id="name-dialog-input"
              v-model="name"
              class="field__input"
              :class="{ 'field__input--invalid': submitted && !name.trim() }"
              type="text"
              :maxlength="maxlength"
              autofocus
            />
            <p v-if="submitted && !name.trim()" class="field__error">请填写{{ label }}</p>
          </div>
          <p v-if="errorMessage" class="form__error">{{ errorMessage }}</p>
          <div class="form__actions">
            <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
            <button class="btn btn--primary" type="submit" :disabled="submitting">
              <Icon v-if="submitting" icon="lucide:loader-circle" class="spin" />
              保存
            </button>
          </div>
        </form>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.dialog--sm {
  width: min(26rem, 100%);
}
</style>
