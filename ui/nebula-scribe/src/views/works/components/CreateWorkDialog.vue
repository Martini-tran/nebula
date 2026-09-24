<script setup lang="ts">
import { ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { createWork } from '../../../api/work'
import type { WorkListItem } from '../../../types/work'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [work: WorkListItem] }>()

const title = ref('')
const genre = ref('')
const logline = ref('')
const targetWordCount = ref<number | null>(null)

const submitting = ref(false)
const errorMessage = ref('')
/** 懒校验：提交过一次后才对空标题标红 */
const submitted = ref(false)

watch(
  () => props.open,
  (open) => {
    if (open) {
      title.value = ''
      genre.value = ''
      logline.value = ''
      targetWordCount.value = null
      errorMessage.value = ''
      submitted.value = false
    }
  },
)

const submit = async () => {
  submitted.value = true
  if (!title.value.trim()) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const created = await createWork({
      title: title.value.trim(),
      genre: genre.value.trim() || undefined,
      logline: logline.value.trim() || undefined,
      summary: logline.value.trim() || undefined,
      targetWordCount: targetWordCount.value ?? undefined,
    })
    emit('created', created)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <transition name="dialog">
    <div v-if="open" class="mask" @click.self="emit('close')">
      <div class="dialog surface" role="dialog" aria-modal="true" aria-labelledby="create-work-title">
        <header class="dialog__head">
          <h2 id="create-work-title" class="dialog__title">新建作品</h2>
          <button class="btn btn--quiet dialog__close" type="button" aria-label="关闭" @click="emit('close')">
            <Icon icon="lucide:x" />
          </button>
        </header>

        <form class="form" @submit.prevent="submit">
          <div class="field">
            <label class="field__label" for="work-title">
              标题
              <span class="field__required" aria-hidden="true">*</span>
            </label>
            <input
              id="work-title"
              v-model="title"
              class="field__input"
              :class="{ 'field__input--invalid': submitted && !title.trim() }"
              type="text"
              placeholder="还没想好也可以先写「无名稿」"
              autocomplete="off"
              :aria-invalid="submitted && !title.trim()"
            />
            <p v-if="submitted && !title.trim()" class="field__error">请填写标题</p>
          </div>

          <div class="field">
            <label class="field__label" for="work-genre">题材</label>
            <input
              id="work-genre"
              v-model="genre"
              class="field__input"
              type="text"
              placeholder="如：古代悬疑 / 东方玄幻"
              autocomplete="off"
            />
          </div>

          <div class="field">
            <label class="field__label" for="work-logline">一句话立意</label>
            <textarea
              id="work-logline"
              v-model="logline"
              class="field__input field__input--area"
              rows="3"
              placeholder="用一句话说清这本书讲什么，越具体越好"
            />
            <p class="field__hint">这句话会作为 AI 拆大纲时的核心依据。</p>
          </div>

          <div class="field">
            <label class="field__label" for="work-target">目标字数</label>
            <input
              id="work-target"
              v-model.number="targetWordCount"
              class="field__input"
              type="number"
              min="0"
              step="10000"
              placeholder="留空表示暂不设定"
            />
          </div>

          <p v-if="errorMessage" class="form__error" role="alert">{{ errorMessage }}</p>

          <footer class="form__actions">
            <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
            <button class="btn btn--primary" type="submit" :disabled="submitting">
              <Icon v-if="submitting" icon="lucide:loader-circle" class="spin" />
              {{ submitting ? '创建中…' : '创建' }}
            </button>
          </footer>
        </form>
      </div>
    </div>
  </transition>
</template>

<style scoped lang="scss">
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(10, 8, 6, 0.45);
  backdrop-filter: blur(2px);
}

.dialog {
  width: min(34rem, 100%);
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.15rem 1.35rem;
  border-bottom: 1px solid var(--color-border);
}

.dialog__title {
  font-size: 1.1rem;
  font-weight: 700;
}

.dialog__close {
  padding: 0.35rem;
}

.dialog__close svg {
  width: 1.1rem;
  height: 1.1rem;
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.18s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}
</style>
