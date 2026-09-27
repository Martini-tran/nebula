<script setup lang="ts">
/**
 * 任务行：优先级用复选框描边颜色表达（红 / 橙 / 蓝 / 无），不额外占列。
 * 点行在右侧打开详情；右侧小字依次是时间、来源、子任务进度、重复、清单。
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useTaskStore } from '../../../stores/tasks'
import { relativeDay, todayYmd } from '../../../utils/date'
import { describeRepeat } from '../../../utils/repeat'
import { SOURCE_META, type Task } from '../../../types/tasks'

const props = withDefaults(defineProps<{ task: Task; active?: boolean; showDate?: boolean }>(), {
  active: false,
  showDate: true,
})
const emit = defineEmits<{ open: []; toggle: []; focus: [] }>()

const store = useTaskStore()
const list = computed(() => store.findList(props.task.listId))
const overdue = computed(() => !props.task.done && Boolean(props.task.dueDate) && props.task.dueDate! < todayYmd())

const when = computed(() => {
  const { dueDate, dueTime } = props.task
  if (!dueDate) return ''
  const day = relativeDay(dueDate)
  if (!props.showDate && day === '今天') return dueTime ?? ''
  return dueTime ? `${day} ${dueTime}` : day
})

const subDone = computed(() => props.task.subtasks.filter((s) => s.done).length)
const sourceTitle = computed(() =>
  props.task.source ? `来自${SOURCE_META[props.task.source.type].name}「${props.task.source.label}」` : '',
)
</script>

<template>
  <li
    class="tr"
    :class="{ 'tr--on': active, 'tr--done': task.done }"
    tabindex="0"
    @click="emit('open')"
    @keydown.enter.self="emit('open')"
    @keydown.space.self.prevent="emit('toggle')"
  >
    <button
      type="button"
      class="tr__check"
      :class="`tr__check--p${task.priority}`"
      role="checkbox"
      :aria-checked="task.done"
      :aria-label="task.done ? `标记「${task.title}」为未完成` : `完成「${task.title}」`"
      @click.stop="emit('toggle')"
    >
      <Icon v-if="task.done" icon="lucide:check" />
    </button>
    <span class="tr__title">{{ task.title }}</span>
    <button v-if="!task.done" type="button" class="tr__focus" :title="`专注：${task.title}`" @click.stop="emit('focus')">
      <Icon icon="lucide:play" />专注
    </button>
    <span class="tr__meta">
      <span v-if="task.source" class="tr__chip" :title="sourceTitle">
        <Icon :icon="SOURCE_META[task.source.type].icon" />
        <span class="tr__src">{{ task.source.label }}</span>
      </span>
      <span v-if="task.subtasks.length" class="tr__chip" title="子任务"><Icon icon="lucide:list-checks" />{{ subDone }}/{{ task.subtasks.length }}</span>
      <span v-if="task.note" class="tr__chip" title="有备注"><Icon icon="lucide:align-left" /></span>
      <span v-if="task.repeat" class="tr__chip" :title="describeRepeat(task.repeat, task.dueDate)"><Icon icon="lucide:repeat" />{{ describeRepeat(task.repeat, task.dueDate) }}</span>
      <span v-if="when" class="tr__when" :class="{ 'tr__when--late': overdue }">{{ when }}</span>
      <span v-if="list" class="tr__list"><i :style="{ background: list.color }" />{{ list.name }}</span>
    </span>
  </li>
</template>

<style scoped>
.tr {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-height: 2.8rem;
  padding: 0.35rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
  outline: none;
}

.tr:last-child {
  border-bottom: 0;
}

.tr:hover {
  background: var(--color-bg-soft);
}

.tr:focus-visible {
  box-shadow: inset 0 0 0 2px var(--color-brand-soft);
}

.tr--on,
.tr--on:hover {
  background: var(--color-brand-soft);
}

.tr__check {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.15rem;
  height: 1.15rem;
  padding: 0;
  border: 2px solid var(--color-text-secondary);
  border-radius: 50%;
  background: var(--color-bg-surface);
  color: #fff;
  cursor: pointer;
  opacity: 0.8;
  transition:
    background 0.15s ease,
    transform 0.15s ease;
}

.tr__check:hover {
  transform: scale(1.1);
}

.tr__check svg {
  width: 0.75rem;
  height: 0.75rem;
}

.tr__check--p3 {
  border-color: #dc2626;
  background: color-mix(in srgb, #dc2626 10%, var(--color-bg-surface));
  opacity: 1;
}

.tr__check--p2 {
  border-color: #ea8a0c;
  background: color-mix(in srgb, #ea8a0c 10%, var(--color-bg-surface));
  opacity: 1;
}

.tr__check--p1 {
  border-color: #3b82f6;
  background: color-mix(in srgb, #3b82f6 10%, var(--color-bg-surface));
  opacity: 1;
}

.tr--done .tr__check {
  border-color: var(--color-text-secondary);
  background: var(--color-text-secondary);
  opacity: 0.6;
}

.tr__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.93rem;
}

.tr--done .tr__title {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.tr__focus {
  display: none;
  flex: none;
  align-items: center;
  gap: 0.2rem;
  padding: 0.15rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-brand);
  font-size: 0.74rem;
  font-weight: 700;
  cursor: pointer;
}

.tr__focus svg {
  width: 0.7rem;
  height: 0.7rem;
}

.tr:hover .tr__focus,
.tr:focus-within .tr__focus {
  display: inline-flex;
}

.tr__meta {
  display: flex;
  flex-shrink: 1;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.tr__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  min-width: 0;
  white-space: nowrap;
}

.tr__chip svg {
  flex: none;
  width: 0.85rem;
  height: 0.85rem;
}

.tr__src {
  max-width: 7rem;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tr__when {
  font-weight: 600;
  white-space: nowrap;
}

.tr__when--late {
  color: var(--color-danger);
}

.tr__list {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  white-space: nowrap;
}

.tr__list i {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
}

@media (max-width: 760px) {
  .tr__chip,
  .tr__list {
    display: none;
  }
}
</style>
