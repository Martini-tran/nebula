<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import TagPicker from './TagPicker.vue'
import {
  bindBookmarkTags,
  createBookmark,
  fetchBookmarks,
  hostOf,
  normalizeUrl,
  updateBookmark,
} from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { formatDate } from '../../../utils/format'
import type { Bookmark, EntityId } from '../../../types/space'

const props = defineProps<{
  open: boolean
  /** 传入则为编辑，否则新建 */
  bookmark?: Bookmark | null
  /** 新建时预选的目录（侧栏当前选中的目录） */
  defaultFolderId?: EntityId
}>()
const emit = defineEmits<{ close: []; saved: [message: string]; openExisting: [bookmark: Bookmark] }>()

const space = useSpaceStore()
const folderOptions = computed(() => space.flatFolders())
const isEdit = computed(() => Boolean(props.bookmark))

const url = ref('')
const title = ref('')
const folderId = ref<string>('0')
const tagIds = ref<string[]>([])
const description = ref('')
const remark = ref('')

const submitting = ref(false)
const errorMessage = ref('')
/** 懒校验：提交过一次后才标红 */
const submitted = ref(false)
const urlInput = ref<HTMLInputElement | null>(null)

/** 同域名的已有书签：用来查重，也用来推荐常用标签 */
const sameDomain = ref<Bookmark[]>([])
const checkedUrl = ref('')

const urlValid = computed(() => /^https?:\/\/\S+$/i.test(url.value.trim()))
const urlError = computed(() => {
  if (!submitted.value) return ''
  if (!url.value.trim()) return '请填写网址'
  return urlValid.value ? '' : '网址需以 http:// 或 https:// 开头'
})
const titleMissing = computed(() => submitted.value && !title.value.trim())

const duplicate = computed(() => {
  if (!urlValid.value || checkedUrl.value !== url.value.trim()) return undefined
  const target = normalizeUrl(url.value)
  return sameDomain.value.find(
    (item) => normalizeUrl(item.url) === target && String(item.id) !== String(props.bookmark?.id ?? ''),
  )
})

const suggestedTags = computed(() => {
  const count = new Map<string, number>()
  for (const b of sameDomain.value) {
    if (String(b.id) === String(props.bookmark?.id ?? '')) continue
    for (const t of b.tags ?? []) count.set(String(t.id), (count.get(String(t.id)) ?? 0) + 1)
  }
  return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4).map(([id]) => id)
})

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    const b = props.bookmark
    url.value = b?.url ?? ''
    title.value = b?.title ?? ''
    folderId.value = String(b?.folderId ?? props.defaultFolderId ?? 0)
    tagIds.value = (b?.tags ?? []).map((tag) => String(tag.id))
    description.value = b?.description ?? ''
    remark.value = b?.remark ?? ''
    errorMessage.value = ''
    submitted.value = false
    sameDomain.value = []
    checkedUrl.value = ''
    await nextTick()
    if (!b) urlInput.value?.focus()
    else checkUrl()
  },
)

let checkSeq = 0

/** 网址失焦：查同域名书签（查重 + 推荐标签），并用域名先顶上空标题 */
const checkUrl = async () => {
  const value = url.value.trim()
  if (!urlValid.value) return
  if (!title.value.trim()) title.value = hostOf(value).replace(/^www\./, '')
  if (checkedUrl.value === value) return
  const seq = ++checkSeq
  try {
    const page = await fetchBookmarks({ domain: hostOf(value), pageSize: 500 })
    if (seq !== checkSeq) return
    sameDomain.value = page?.records ?? []
    checkedUrl.value = value
  } catch {
    // 查重失败不挡保存
  }
}

/** 把本次选的标签并进已有书签，而不是再存一条 */
const mergeIntoExisting = async () => {
  const existing = duplicate.value
  if (!existing || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const merged = [...new Set([...(existing.tags ?? []).map((t) => String(t.id)), ...tagIds.value])]
    await bindBookmarkTags(existing.id, merged)
    emit('saved', `标签已合并到「${existing.title}」`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '合并失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

const submit = async () => {
  submitted.value = true
  if (!urlValid.value || !title.value.trim() || submitting.value) return
  // 新建时先确认查过重：后端遇到重复网址会用本次标签覆盖已有书签的标签
  if (!isEdit.value) await checkUrl()
  if (duplicate.value) return

  submitting.value = true
  errorMessage.value = ''
  const body = {
    url: url.value.trim(),
    title: title.value.trim(),
    folderId: folderId.value,
    tagIds: tagIds.value,
    description: description.value.trim(),
    remark: remark.value.trim(),
  }
  try {
    if (props.bookmark) {
      await updateBookmark(props.bookmark.id, body)
      emit('saved', '书签已更新')
    } else {
      await createBookmark(body)
      emit('saved', '书签已添加')
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <BaseDialog :open="open" :title="isEdit ? '编辑书签' : '添加书签'" width="36rem" :locked="submitting" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit" @keydown="onKeydown">
      <div class="field">
        <label class="field__label" for="bm-url">
          网址 <span class="field__required" aria-hidden="true">*</span>
        </label>
        <input
          id="bm-url"
          ref="urlInput"
          v-model="url"
          class="field__input"
          :class="{ 'field__input--invalid': urlError }"
          type="url"
          placeholder="https://"
          autocomplete="off"
          @blur="checkUrl"
        />
        <p v-if="urlError" class="field__error">{{ urlError }}</p>

        <div v-if="duplicate" class="dup" role="alert">
          <p class="dup__title">
            <Icon icon="lucide:copy-check" />这个网址已经收藏过了
            <small>（忽略协议与域名大小写、#锚点后相同）</small>
          </p>
          <p class="dup__meta">
            <b>{{ duplicate.title }}</b>
            <span>{{ space.folderPath(duplicate.folderId) }} · {{ formatDate(duplicate.createTime) }} 收藏</span>
          </p>
          <div class="dup__acts">
            <button class="btn btn--ghost" type="button" @click="emit('openExisting', duplicate)">打开已有</button>
            <button
              v-if="!isEdit"
              class="btn btn--ghost"
              type="button"
              :disabled="!tagIds.length || submitting"
              :title="tagIds.length ? '' : '先在下方选几个标签'"
              @click="mergeIntoExisting"
            >
              把标签合并到已有
            </button>
          </div>
        </div>
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

      <div class="field">
        <span class="field__label">标签</span>
        <TagPicker
          v-model="tagIds"
          :suggested="suggestedTags"
          :suggested-label="`${hostOf(url)} 的其他书签常用：`"
        />
      </div>

      <div class="field">
        <label class="field__label" for="bm-desc">描述</label>
        <textarea
          id="bm-desc"
          v-model="description"
          class="field__input field__input--area"
          rows="2"
          maxlength="1000"
        />
      </div>

      <div class="field">
        <label class="field__label" for="bm-remark">
          备注 <span class="field__hint">只有你看得到</span>
        </label>
        <textarea
          id="bm-remark"
          v-model="remark"
          class="field__input field__input--area"
          rows="2"
          maxlength="500"
          placeholder="为什么收藏它？"
        />
      </div>

      <p v-if="errorMessage" class="form__error">{{ errorMessage }}</p>

      <div class="form__actions">
        <span class="kbd-hint"><kbd>Ctrl</kbd> <kbd>Enter</kbd> 保存</span>
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="submit" :disabled="submitting || Boolean(duplicate)">
          <Icon v-if="submitting" icon="lucide:loader-circle" class="spin" />
          保存
        </button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.dup {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin-top: 0.2rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--color-brand) 35%, var(--color-border));
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
}

.dup__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--color-brand);
}

.dup__title small {
  font-weight: 400;
  color: var(--color-text-secondary);
}

.dup__meta {
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
}

.dup__meta span {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.dup__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.dup__acts .btn {
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
}

.form__actions {
  align-items: center;
}

.kbd-hint {
  margin-right: auto;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.field__label .field__hint {
  font-weight: 400;
}
</style>
