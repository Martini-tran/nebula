<script setup lang="ts">
/**
 * 「粘贴网址即收藏」输入条：收藏动作一步完成，标题先用域名顶上；随后服务端去抓网页，
 * 取到标题和描述就补上（filled 通知列表刷新），取不到再让用户在弹窗里补。
 * 网址已收藏过时不新建（后端会用空标签覆盖已有书签的标签），改为打开已有那条。
 */
import { ref } from 'vue'
import { Icon } from '@iconify/vue'
import { createBookmark, fillBookmarkMeta, findDuplicate, hostOf } from '../../../api/space'
import { errorText, toast } from '../../../composables/useToast'
import type { Bookmark, EntityId } from '../../../types/space'

const props = defineProps<{
  /** 收进哪个目录：0 = 未分类 */
  folderId: EntityId
  /** 目录名，用于占位文案 */
  folderLabel: string
  /** 在标签视图里收藏时顺手打上这个标签 */
  tagId?: EntityId
}>()
const emit = defineEmits<{
  added: [id: EntityId]
  duplicate: [bookmark: Bookmark]
  /** 抓网页补标题结束：got = 标题换成了网页标题 */
  filled: [bookmark: Bookmark, got: boolean]
}>()

const value = ref('')
const saving = ref(false)
const invalid = ref(false)

/** 允许省略协议：example.com/xx → https://example.com/xx */
const toUrl = (raw: string) => {
  const text = raw.trim()
  if (/^https?:\/\//i.test(text)) return text
  if (/^[\w-]+(\.[\w-]+)+(:\d+)?(\/\S*)?$/.test(text)) return `https://${text}`
  return ''
}

const submit = async () => {
  const url = toUrl(value.value)
  invalid.value = !url
  if (!url || saving.value) return
  saving.value = true
  try {
    const existing = await findDuplicate(url)
    if (existing) {
      toast.info(`已经收藏过：「${existing.title}」`)
      emit('duplicate', existing)
      value.value = ''
      return
    }
    const placeholder = hostOf(url).replace(/^www\./, '')
    const id = await createBookmark({
      url,
      title: placeholder,
      folderId: props.folderId,
      tagIds: props.tagId !== undefined ? [props.tagId] : undefined,
    })
    value.value = ''
    emit('added', id)
    // 抓网页可能要几秒，不挡着收藏下一条
    fillBookmarkMeta(id)
      .then((bookmark) => emit('filled', bookmark, bookmark.title !== placeholder))
      .catch(() => undefined)
  } catch (error) {
    toast.error(errorText(error, '收藏失败'))
  } finally {
    saving.value = false
  }
}

/** 粘贴一条网址直接收藏，省掉回车 */
const onPaste = (event: ClipboardEvent) => {
  const text = event.clipboardData?.getData('text') ?? ''
  if (value.value.trim() || !toUrl(text) || /\s/.test(text.trim())) return
  event.preventDefault()
  value.value = text.trim()
  submit()
}
</script>

<template>
  <form class="qa" :class="{ 'qa--invalid': invalid }" @submit.prevent="submit">
    <Icon :icon="saving ? 'lucide:loader-circle' : 'lucide:link'" class="qa__icon" :class="{ spin: saving }" />
    <input
      v-model="value"
      type="text"
      inputmode="url"
      :placeholder="`粘贴网址，回车即收藏到「${folderLabel}」`"
      aria-label="粘贴网址快速收藏"
      :disabled="saving"
      @input="invalid = false"
      @paste="onPaste"
    />
    <span v-if="invalid" class="qa__err">这不像一个网址</span>
    <kbd v-else>Enter</kbd>
  </form>
</template>

<style scoped>
.qa {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0 0.85rem;
  border: 1px dashed color-mix(in srgb, var(--color-brand) 40%, var(--color-border));
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.qa:focus-within {
  border-style: solid;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.qa--invalid,
.qa--invalid:focus-within {
  border-color: var(--color-danger);
}

.qa__icon {
  flex: none;
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-brand);
}

.qa input {
  flex: 1;
  min-width: 0;
  padding: 0.7rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.qa__err {
  flex: none;
  font-size: 0.8rem;
  color: var(--color-danger);
}
</style>
