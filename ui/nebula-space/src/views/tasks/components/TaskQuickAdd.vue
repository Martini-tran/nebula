<script setup lang="ts">
/**
 * 任务快速添加：支持自然语言「明天下午3点 回电话 !2 #工作」，输入时即时高亮识别出的片段，
 * 并在下方写明「识别为：明天 15:00 · 中优先级 · 工作清单」。#清单 不存在时回车会顺手新建。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { createTask, createTaskList } from '../../../api/tasks'
import { useTaskStore } from '../../../stores/tasks'
import { errorText, toast } from '../../../composables/useToast'
import { parseTaskInput, type ParsedKind } from '../../../utils/taskParser'
import { relativeDay } from '../../../utils/date'
import { nextTagColor } from '../../bookmarks/tagColors'
import { PRIORITY_LABEL, type Task, type TaskPriority } from '../../../types/tasks'
import type { EntityId } from '../../../types/space'

const props = defineProps<{
  /** 没写日期时的默认日期：今天视图里是今天，收件箱里是 null */
  defaultDate: string | null
  /** 在某个清单视图里添加时默认进这个清单 */
  defaultListId: EntityId | null
  placeholder: string
}>()
const emit = defineEmits<{ added: [task: Task] }>()

const store = useTaskStore()
const text = ref('')
const saving = ref(false)
const input = ref<HTMLInputElement | null>(null)
const mirror = ref<HTMLDivElement | null>(null)

/** 文字超出宽度时输入框会横向滚动，高亮层跟着滚 */
const syncScroll = () => requestAnimationFrame(() => {
  if (mirror.value && input.value) mirror.value.scrollLeft = input.value.scrollLeft
})

const parsed = computed(() => parseTaskInput(text.value))
const list = computed(() => (parsed.value.listName ? store.findListByName(parsed.value.listName) : undefined))

/** 把输入拆成普通文本与识别片段，用于高亮层 */
const segments = computed(() => {
  const out: { text: string; kind?: ParsedKind }[] = []
  let cursor = 0
  for (const span of parsed.value.spans) {
    if (span.start > cursor) out.push({ text: text.value.slice(cursor, span.start) })
    out.push({ text: span.text, kind: span.kind })
    cursor = span.end
  }
  out.push({ text: text.value.slice(cursor) })
  return out
})

const summary = computed(() => {
  const p = parsed.value
  if (!p.spans.length) return ''
  const parts: string[] = []
  if (p.dueDate) parts.push(`${relativeDay(p.dueDate)}${p.dueTime ? ` ${p.dueTime}` : ''}`)
  if (p.priority) parts.push(`${PRIORITY_LABEL[p.priority as TaskPriority]}优先级`)
  if (p.listName) parts.push(list.value ? `${list.value.name}清单` : `新建清单「${p.listName}」`)
  return parts.join(' · ')
})

/** 预填文字并聚焦（计划看板里「在某天添加」用） */
const prefill = (value: string) => {
  text.value = value
  input.value?.focus()
  requestAnimationFrame(() => input.value?.setSelectionRange(value.length, value.length))
}
defineExpose({ prefill })

const submit = async () => {
  const p = parsed.value
  if (!p.title || saving.value) return
  saving.value = true
  try {
    let listId = list.value?.id ?? (p.listName ? undefined : props.defaultListId)
    if (p.listName && !list.value) {
      const created = await createTaskList({ name: p.listName, color: nextTagColor(store.lists.length) })
      listId = created.id
      await store.reloadLists()
    }
    const task = await createTask({
      title: p.title,
      dueDate: p.dueDate ?? props.defaultDate,
      dueTime: p.dueTime ?? null,
      priority: p.priority as TaskPriority,
      listId: listId ?? null,
    })
    text.value = ''
    emit('added', task)
  } catch (error) {
    toast.error(errorText(error, '添加失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="qa">
    <form class="qa__box" @submit.prevent="submit">
      <Icon :icon="saving ? 'lucide:loader-circle' : 'lucide:plus'" class="qa__icon" :class="{ spin: saving }" />
      <div class="qa__field">
        <!-- 高亮层与输入框逐字重叠：输入框文字透明，只显示光标 -->
        <div ref="mirror" class="qa__mirror" aria-hidden="true">
          <template v-for="(seg, i) in segments" :key="i">
            <mark v-if="seg.kind" :class="`qa__hl qa__hl--${seg.kind}`">{{ seg.text }}</mark>
            <span v-else>{{ seg.text }}</span>
          </template>
        </div>
        <input
          ref="input"
          v-model="text"
          type="text"
          :placeholder="placeholder"
          aria-label="添加任务"
          autocomplete="off"
          @input="syncScroll"
          @keyup="syncScroll"
          @click="syncScroll"
          @scroll="syncScroll"
        />
      </div>
      <kbd>Enter</kbd>
    </form>
    <p v-if="summary" class="qa__sum">
      <Icon icon="lucide:sparkles" />识别为：<b>{{ summary }}</b>
      <span v-if="!parsed.title" class="qa__warn">· 还缺任务内容</span>
    </p>
    <p v-else class="qa__sum qa__sum--hint">可以直接写「明天下午3点 回电话 !2 #工作」：日期、时间、!1~!3 优先级、#清单</p>
  </div>
</template>

<style scoped>
.qa {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.qa__box {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.qa__box:focus-within {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.qa__icon {
  flex: none;
  width: 1.1rem;
  height: 1.1rem;
  color: var(--color-brand);
}

.qa__field {
  position: relative;
  flex: 1;
  min-width: 0;
}

.qa__mirror,
.qa__field input {
  padding: 0.7rem 0;
  font: inherit;
  font-size: 0.95rem;
  letter-spacing: normal;
  white-space: pre;
}

.qa__mirror {
  position: absolute;
  inset: 0;
  overflow: hidden;
  color: var(--color-text-primary);
  pointer-events: none;
}

.qa__field input {
  position: relative;
  width: 100%;
  border: 0;
  outline: none;
  background: none;
  color: transparent;
  caret-color: var(--color-text-primary);
}

.qa__field input::placeholder {
  color: var(--color-text-secondary);
  opacity: 0.75;
}

.qa__hl {
  border-radius: 3px;
  color: inherit;
}

.qa__hl--date,
.qa__hl--time {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.qa__hl--priority {
  background: color-mix(in srgb, #f59e0b 22%, transparent);
  color: #b45309;
}

.qa__hl--list {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

:root[data-theme='dark'] .qa__hl--priority {
  color: #fbbf24;
}

.qa__sum {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding-left: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.qa__sum svg {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--color-brand);
}

.qa__sum b {
  color: var(--color-text-primary);
}

.qa__sum--hint {
  opacity: 0.8;
}

.qa__warn {
  color: var(--color-danger);
}
</style>
