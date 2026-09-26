<script setup lang="ts">
/**
 * 弹窗外壳：遮罩、卡片、标题栏、Esc 关闭。各弹窗只写自己的内容和按钮。
 * 内容放默认插槽，底部按钮放 footer 插槽（不传则不渲染底栏）。
 */
import { onBeforeUnmount, useId, watch } from 'vue'
import { Icon } from '@iconify/vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    /** 卡片宽度，默认 34rem */
    width?: string
    /** 进行中的操作不允许关闭（如提交中） */
    locked?: boolean
  }>(),
  { width: '34rem', locked: false },
)
const emit = defineEmits<{ close: [] }>()

const titleId = useId()

const close = () => {
  if (!props.locked) emit('close')
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    if (open) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <transition name="dialog">
      <div v-if="open" class="dialog-mask" @click.self="close">
        <div
          class="dialog surface"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :style="{ width: `min(${width}, 100%)` }"
        >
          <header class="dialog__head">
            <h2 :id="titleId" class="dialog__title">{{ title }}</h2>
            <button class="btn btn--quiet dialog__close" type="button" aria-label="关闭" :disabled="locked" @click="close">
              <Icon icon="lucide:x" />
            </button>
          </header>
          <slot />
          <footer v-if="$slots.footer" class="dialog__foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>
