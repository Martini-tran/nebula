<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { createBookmark, updateBookmark } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import type { Bookmark, EntityId } from '../../../types/space'

const props = defineProps<{
  open: boolean
  /** 传入则为编辑，否则新建 */
  bookmark?: Bookmark | null
  /** 新建时预选的目录（侧栏当前选中的目录） */
  defaultFolderId?: EntityId
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const space = useSpaceStore()
const folderOptions = computed(() => space.flatFolders())
const isEdit = computed(() => Boolean(props.bookmark))

const url = ref('')
const title = ref('')
const folderId = ref<string>('0')
const tagIds = ref<string[]>([])
const description = ref('')

const submitting = ref(false)
const errorMessage = ref('')
/** 懒校验：提交过一次后才标红 */
const submitted = ref(false)

const urlValid = computed(() => /^https?:\/\/\S+$/i.test(url.value.trim()))
const urlError = computed(() => {
  if (!submitted.value) return ''
  if (!url.value.trim()) return '请填写网址'
  return urlValid.value ? '' : '网址需以 http:// 或 https:// 开头'
})
const titleMissing = computed(() => submitted.value && !title.value.trim())

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const b = props.bookmark
    url.value = b?.url ?? ''
    title.value = b?.title ?? ''
    folderId.value = String(b?.folderId ?? props.defaultFolderId ?? 0)
    tagIds.value = (b?.tags ?? []).map((tag) => String(tag.id))
    description.value = b?.description ?? ''
    errorMessage.value = ''
    submitted.value = false
  },
)

/** 只填了网址就离开输入框时，用域名先顶上标题，省一步 */
const fillTitleFromUrl = () => {
  if (title.value.trim() || !urlValid.value) return
  try {
    title.value = new URL(url.value.trim()).hostname.replace(/^www\./, '')
  } catch {
    // 忽略，交给校验提示
  }
}

const toggleTag = (id: EntityId) => {
  const key = String(id)
  tagIds.value = tagIds.value.includes(key)
    ? tagIds.value.filter((item) => item !== key)
    : [...tagIds.value, key]
}

const submit = async () => {
  submitted.value = true
  if (!urlValid.value || !title.value.trim() || submitting.value) return

  submitting.value = true
  errorMessage.value = ''
  const body = {
    url: url.value.trim(),
    title: title.value.trim(),
    folderId: folderId.value,
    tagIds: tagIds.value,
    description: description.value.trim(),
  }
  try {
    if (props.bookmark) {
      await updateBookmark(props.bookmark.id, body)
    } else {
      await createBookmark(body)
    }
    emit('saved')
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
      <div class="dialog surface" role="dialog" aria-modal="true" aria-labelledby="bookmark-dialog-title">
        <header class="dialog__head">
          <h2 id="bookmark-dialog-title" class="dialog__title">{{ isEdit ? '编辑书签' : '添加书签' }}</h2>
          <button class="btn btn--quiet dialog__close" type="button" aria-label="关闭" @click="emit('close')">
            <Icon icon="lucide:x" />
          </button>
        </header>

        <form class="form" novalidate @submit.prevent="submit">
          <div class="field">
            <label class="field__label" for="bm-url">
              网址 <span class="field__required" aria-hidden="true">*</span>
            </label>
            <input
              id="bm-url"
              v-model="url"
              class="field__input"
              :class="{ 'field__input--invalid': urlError }"
              type="url"
              placeholder="https://"
              autocomplete="off"
              @blur="fillTitleFromUrl"
            />
            <p v-if="urlError" class="field__error">{{ urlError }}</p>
          </div>

          <div class="field">
            <label class="field__label" for="bm-title">
              标题 <span class="field__required" aria-hidden="true">*</span>
            </label>
            <input
              id="bm-title"
              v-model="title"
              class="field__input"
              :class="{ 'field__input--invalid': titleMissing }"
              type="text"
              maxlength="500"
            />
            <p v-if="titleMissing" class="field__error">请填写标题</p>
          </div>

          <div class="field">
            <label class="field__label" for="bm-folder">目录</label>
            <select id="bm-folder" v-model="folderId" class="field__input">
              <option value="0">未分类</option>
              <option v-for="option in folderOptions" :key="option.id" :value="String(option.id)">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div v-if="space.tags.length" class="field">
            <span class="field__label">标签</span>
            <div class="tag-picker">
              <button
                v-for="tag in space.tags"
                :key="tag.id"
                type="button"
                class="tag-picker__item"
                :class="{ 'tag-picker__item--on': tagIds.includes(String(tag.id)) }"
                :aria-pressed="tagIds.includes(String(tag.id))"
                @click="toggleTag(tag.id)"
              >
                <span class="tag-picker__dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />
                {{ tag.name }}
              </button>
            </div>
          </div>

          <div class="field">
            <label class="field__label" for="bm-desc">描述</label>
            <textarea
              id="bm-desc"
              v-model="description"
              class="field__input field__input--area"
              rows="3"
              maxlength="1000"
            />
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

<style scoped lang="scss">
.tag-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.tag-picker__item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  font-size: 0.85rem;
  cursor: pointer;
}

.tag-picker__item--on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.tag-picker__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}
</style>
