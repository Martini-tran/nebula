<script setup lang="ts">
/**
 * 设定条目的新建/编辑表单。
 *
 * 标签在上、懒校验（提交后才标红）、错误紧贴输入框；数组字段以逗号分隔编辑，提交时再拆。
 * 名称重复由后端判定（409），父组件把文案通过 nameError 回传，显示在名称框下方。
 */
import { computed, reactive, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import {
  LORE_KIND_ICON,
  LORE_KIND_LABEL,
  type LoreEntry,
  type LoreKind,
  type LoreSaveRequest,
} from '../../../types/lore'

const props = withDefaults(
  defineProps<{
    /** 编辑的条目；为空即新建 */
    entry?: LoreEntry | null
    defaultKind?: LoreKind
    saving?: boolean
    /** 名称冲突等需要落在名称框下的错误 */
    nameError?: string
    /** 其余保存失败的文案 */
    formError?: string
  }>(),
  { entry: null, defaultKind: 'character', saving: false, nameError: '', formError: '' },
)

const emit = defineEmits<{
  submit: [body: LoreSaveRequest]
  cancel: []
  /** 表单是否有未保存的改动，父组件切换条目前据此确认 */
  dirty: [value: boolean]
}>()

const MAX_ITEMS = 10
const MAX_ALIAS = 30
const MAX_TAG = 20

const kindOptions = Object.entries(LORE_KIND_LABEL) as Array<[LoreKind, string]>

interface FormState {
  kind: LoreKind
  name: string
  aliases: string
  summary: string
  detail: string
  tags: string
  pinned: boolean
}

const form = reactive<FormState>({
  kind: 'character',
  name: '',
  aliases: '',
  summary: '',
  detail: '',
  tags: '',
  pinned: false,
})
const snapshot = ref('')
const submitted = ref(false)

const isCreate = computed(() => !props.entry)

/** 中英文逗号、顿号都算分隔符 */
const splitList = (value: string) =>
  value
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)

const fill = () => {
  const entry = props.entry
  form.kind = entry?.kind ?? props.defaultKind
  form.name = entry?.name ?? ''
  form.aliases = (entry?.aliases ?? []).join('，')
  form.summary = entry?.summary ?? ''
  form.detail = entry?.detail ?? ''
  form.tags = (entry?.tags ?? []).join('，')
  form.pinned = Boolean(entry?.pinned)
  snapshot.value = JSON.stringify(form)
  submitted.value = false
}

watch(() => props.entry, fill, { immediate: true })

const dirty = computed(() => JSON.stringify(form) !== snapshot.value)
watch(dirty, (value) => emit('dirty', value), { immediate: true })

const listError = (value: string, max: number, label: string) => {
  const items = splitList(value)
  if (items.length > MAX_ITEMS) {
    return `${label}最多 ${MAX_ITEMS} 个`
  }
  if (items.some((item) => item.length > max)) {
    return `单个${label}不能超过 ${max} 字`
  }
  return ''
}

const errors = computed(() => ({
  name: !form.name.trim() ? '请填写名称' : '',
  aliases: listError(form.aliases, MAX_ALIAS, '别名'),
  tags: listError(form.tags, MAX_TAG, '标签'),
}))

/** 懒校验：提交过一次才显示本地校验错误；后端回传的名称冲突随时显示 */
const shownNameError = computed(() => (submitted.value && errors.value.name) || props.nameError)
const shownAliasesError = computed(() => (submitted.value ? errors.value.aliases : ''))
const shownTagsError = computed(() => (submitted.value ? errors.value.tags : ''))

const submit = () => {
  submitted.value = true
  if (errors.value.name || errors.value.aliases || errors.value.tags) {
    return
  }
  emit('submit', {
    kind: form.kind,
    name: form.name.trim(),
    aliases: splitList(form.aliases),
    summary: form.summary.trim() || undefined,
    detail: form.detail.trim() || undefined,
    tags: splitList(form.tags),
    pinned: form.pinned,
  })
}
</script>

<template>
  <form class="form lore-form" novalidate @submit.prevent="submit">
    <fieldset class="field">
      <legend class="field__label">类型</legend>
      <div class="kinds" role="radiogroup" aria-label="设定类型">
        <label
          v-for="[value, label] in kindOptions"
          :key="value"
          class="kind"
          :class="{ 'kind--active': form.kind === value }"
        >
          <input v-model="form.kind" class="sr-only" type="radio" name="lore-kind" :value="value" />
          <Icon :icon="LORE_KIND_ICON[value]" />
          {{ label }}
        </label>
      </div>
    </fieldset>

    <div class="field">
      <label class="field__label" for="lore-name">
        名称
        <span class="field__required" aria-hidden="true">*</span>
      </label>
      <input
        id="lore-name"
        v-model="form.name"
        class="field__input"
        :class="{ 'field__input--invalid': shownNameError }"
        type="text"
        maxlength="100"
        autocomplete="off"
        placeholder="如：沈砚"
        :aria-invalid="Boolean(shownNameError)"
        :aria-describedby="shownNameError ? 'lore-name-error' : undefined"
      />
      <p v-if="shownNameError" id="lore-name-error" class="field__error">{{ shownNameError }}</p>
    </div>

    <div class="field">
      <label class="field__label" for="lore-aliases">别名</label>
      <input
        id="lore-aliases"
        v-model="form.aliases"
        class="field__input"
        :class="{ 'field__input--invalid': shownAliasesError }"
        type="text"
        autocomplete="off"
        placeholder="断刀捕快，沈捕头"
        :aria-invalid="Boolean(shownAliasesError)"
        aria-describedby="lore-aliases-help"
      />
      <p v-if="shownAliasesError" id="lore-aliases-help" class="field__error">{{ shownAliasesError }}</p>
      <p v-else id="lore-aliases-help" class="field__hint">用逗号分隔，最多 10 个。正文里按名称和别名识别这条设定。</p>
    </div>

    <div class="field">
      <label class="field__label" for="lore-summary">一句话概述</label>
      <input
        id="lore-summary"
        v-model="form.summary"
        class="field__input"
        type="text"
        maxlength="300"
        autocomplete="off"
        placeholder="展示在列表里，一眼认出是谁、是什么"
      />
    </div>

    <div class="field">
      <label class="field__label" for="lore-detail">详细设定</label>
      <textarea
        id="lore-detail"
        v-model="form.detail"
        class="field__input field__input--area lore-form__detail"
        rows="10"
        maxlength="20000"
        placeholder="外貌、动机、秘密、弧光……"
        aria-describedby="lore-detail-help"
      />
      <p id="lore-detail-help" class="field__hint">支持 Markdown，最多 20000 字。</p>
    </div>

    <div class="field">
      <label class="field__label" for="lore-tags">标签</label>
      <input
        id="lore-tags"
        v-model="form.tags"
        class="field__input"
        :class="{ 'field__input--invalid': shownTagsError }"
        type="text"
        autocomplete="off"
        placeholder="主角，第一视角"
        :aria-invalid="Boolean(shownTagsError)"
        aria-describedby="lore-tags-help"
      />
      <p v-if="shownTagsError" id="lore-tags-help" class="field__error">{{ shownTagsError }}</p>
      <p v-else id="lore-tags-help" class="field__hint">用逗号分隔，最多 10 个。</p>
    </div>

    <label class="pin">
      <input v-model="form.pinned" class="pin__box" type="checkbox" aria-describedby="lore-pin-help" />
      <span class="pin__text">
        <span class="pin__title">固定</span>
        <span id="lore-pin-help" class="field__hint">AI 生成时默认带上这条设定，适合主角和核心规则。</span>
      </span>
    </label>

    <p v-if="formError" class="form__error" role="alert">{{ formError }}</p>

    <footer class="form__actions">
      <button class="btn btn--ghost" type="button" :disabled="saving" @click="emit('cancel')">取消</button>
      <button class="btn btn--primary" type="submit" :disabled="saving || (!isCreate && !dirty)">
        <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />
        {{ saving ? '保存中…' : isCreate ? '创建设定' : '保存修改' }}
      </button>
    </footer>
  </form>
</template>

<style scoped lang="scss">
.lore-form {
  gap: 1.35rem;
}

fieldset.field {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}

.field__input {
  min-height: 2.6rem;
}

.lore-form__detail {
  min-height: 12rem;
  font-family: var(--font-sans);
}

.kinds {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.kind {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.kind svg {
  width: 0.95rem;
  height: 0.95rem;
}

.kind:hover {
  color: var(--color-text-primary);
  border-color: var(--color-brand);
}

.kind:focus-within {
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.kind--active {
  color: var(--color-brand);
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.pin {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  cursor: pointer;
}

.pin__box {
  width: 1.05rem;
  height: 1.05rem;
  margin-top: 0.15rem;
  accent-color: var(--color-brand);
}

.pin__text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.pin__title {
  font-size: 0.88rem;
  font-weight: 600;
}

.form__actions svg {
  width: 1rem;
  height: 1rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
