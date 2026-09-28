<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'

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
    /** 打开时选中扩展名前面的部分（文件重命名用，改名不会误删扩展名） */
    selectBase?: boolean
    save: (name: string) => Promise<void>
  }>(),
  { initial: '', maxlength: 100, selectBase: false },
)
const emit = defineEmits<{ close: [] }>()

const name = ref('')
const input = ref<HTMLInputElement | null>(null)
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
    if (props.selectBase) {
      nextTick(() => {
        const el = input.value
        if (!el) return
        const dot = name.value.lastIndexOf('.')
        el.focus()
        el.setSelectionRange(0, dot > 0 ? dot : name.value.length)
      })
    }
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
  <BaseDialog :open="open" :title="title" width="26rem" :locked="submitting" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="name-dialog-input">{{ label }}</label>
        <input
          id="name-dialog-input"
          ref="input"
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
  </BaseDialog>
</template>

