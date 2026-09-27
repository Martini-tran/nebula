<script setup lang="ts">
/**
 * 标签多选：点选已有标签，或在输入框里敲新名字回车直接创建并选中。
 * 书签表单与批量打标签共用。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { createTag } from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import { nextTagColor } from '../tagColors'

const props = withDefaults(
  defineProps<{
    allowCreate?: boolean
    /** 建议标签（如同域名书签常用的），显示在列表下方 */
    suggested?: string[]
    suggestedLabel?: string
    /** 批量模式：每个标签在选中书签里已有几条 */
    usage?: Map<string, number>
    usageTotal?: number
  }>(),
  { allowCreate: true, suggested: () => [], suggestedLabel: '', usage: undefined, usageTotal: 0 },
)
const model = defineModel<string[]>({ required: true })

const space = useSpaceStore()
const draft = ref('')
const creating = ref(false)

const toggle = (id: string) => {
  model.value = model.value.includes(id) ? model.value.filter((item) => item !== id) : [...model.value, id]
}

const suggestions = computed(() =>
  props.suggested.filter((id) => !model.value.includes(id)).map((id) => space.findTag(id)).filter((t) => t),
)

const create = async () => {
  const name = draft.value.trim()
  if (!name || creating.value) return
  const existing = space.tags.find((tag) => tag.name === name)
  if (existing) {
    if (!model.value.includes(String(existing.id))) toggle(String(existing.id))
    draft.value = ''
    return
  }
  creating.value = true
  try {
    const id = await createTag({ name, color: nextTagColor(space.tags.length) })
    await space.reload()
    model.value = [...model.value, String(id)]
    draft.value = ''
  } catch (error) {
    toast.error(errorText(error, '创建标签失败'))
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="tp">
    <div class="tp__list">
      <button
        v-for="tag in space.tags"
        :key="tag.id"
        type="button"
        class="tp__item"
        :class="{ 'tp__item--on': model.includes(String(tag.id)) }"
        :aria-pressed="model.includes(String(tag.id))"
        @click="toggle(String(tag.id))"
      >
        <span class="tp__dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />
        {{ tag.name }}
        <small v-if="usage?.get(String(tag.id))" class="tp__usage">{{ usage.get(String(tag.id)) }}/{{ usageTotal }}</small>
      </button>
      <label v-if="allowCreate" class="tp__new">
        <Icon :icon="creating ? 'lucide:loader-circle' : 'lucide:plus'" :class="{ spin: creating }" />
        <input
          v-model="draft"
          type="text"
          maxlength="100"
          placeholder="新标签，回车创建"
          aria-label="新标签名称"
          @keydown.enter.prevent="create"
        />
      </label>
    </div>
    <p v-if="suggestions.length" class="tp__suggest">
      <span>{{ suggestedLabel }}</span>
      <button v-for="tag in suggestions" :key="tag!.id" type="button" class="tag" @click="toggle(String(tag!.id))">
        <Icon icon="lucide:plus" />{{ tag!.name }}
      </button>
    </p>
  </div>
</template>

<style scoped>
.tp {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.tp__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.tp__item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
}

.tp__item--on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.tp__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.tp__usage {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.tp__new {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.6rem;
  border: 1px dashed var(--color-border);
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.tp__new:focus-within {
  border-color: var(--color-brand);
}

.tp__new svg {
  flex: none;
  width: 0.85rem;
  height: 0.85rem;
}

.tp__new input {
  width: 8.5rem;
  padding: 0.12rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.tp__suggest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.tp__suggest button {
  border: 0;
  cursor: pointer;
}

.tp__suggest svg {
  width: 0.75rem;
  height: 0.75rem;
}
</style>
