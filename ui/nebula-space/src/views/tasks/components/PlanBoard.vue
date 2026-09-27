<script setup lang="ts">
/**
 * 计划：未来 7 天看板。每列顶部一条负荷条（按预估时长，没估的按 30 分钟算），
 * 超过 6 小时变红提示「这天排满了」。拖动任务卡到另一天即改期；周末两列底色更浅。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { useTaskStore } from '../../../stores/tasks'
import { addDays, fromYmd, todayYmd, weekdayLabel, weekdayOf } from '../../../utils/date'
import type { Task } from '../../../types/tasks'
import type { Meeting } from '../../../types/meetings'

const props = defineProps<{ tasks: Task[]; activeId: string | null; meetings?: Meeting[] }>()
const emit = defineEmits<{ open: [task: Task]; move: [task: Task, date: string]; add: [date: string] }>()

const store = useTaskStore()
const FULL_MIN = 360
const DEFAULT_MIN = 30

const columns = computed(() => {
  const today = todayYmd()
  return Array.from({ length: 7 }, (_, i) => {
    const ymd = addDays(today, i)
    const items = props.tasks
      .filter((t) => t.dueDate === ymd)
      .sort((a, b) => String(a.dueTime ?? '99').localeCompare(String(b.dueTime ?? '99')) || b.priority - a.priority)
    // 会议作为只读卡片混排进来，时长也算进当天负荷，让人看见哪天没空
    const meets = (props.meetings ?? []).filter((m) => m.date === ymd).sort((a, b) => a.startTime.localeCompare(b.startTime))
    const minutes =
      items.reduce((sum, t) => sum + (t.estimateMin ?? DEFAULT_MIN), 0) + meets.reduce((sum, m) => sum + m.durationMin, 0)
    return {
      ymd,
      label: i === 0 ? '今天' : i === 1 ? '明天' : weekdayLabel(ymd),
      day: fromYmd(ymd).getDate(),
      weekend: [0, 6].includes(weekdayOf(ymd)),
      items,
      meets,
      minutes,
      full: minutes > FULL_MIN,
    }
  })
})

const hours = (min: number) => {
  const h = min / 60
  return Number.isInteger(h) ? `${h}` : h.toFixed(1)
}

const metaOf = (t: Task) =>
  [
    t.dueTime,
    t.estimateMin ? `预计 ${t.estimateMin >= 60 ? `${hours(t.estimateMin)}h` : `${t.estimateMin}m`}` : '',
    store.findList(t.listId)?.name,
  ]
    .filter(Boolean)
    .join(' · ')

const dragId = ref<string | null>(null)
const overCol = ref<string | null>(null)

const onDrop = (ymd: string) => {
  const task = props.tasks.find((t) => String(t.id) === dragId.value)
  dragId.value = null
  overCol.value = null
  if (task && task.dueDate !== ymd) emit('move', task, ymd)
}
</script>

<template>
  <div class="pb">
    <section
      v-for="col in columns"
      :key="col.ymd"
      class="pb__col"
      :class="{ 'pb__col--weekend': col.weekend, 'pb__col--over': overCol === col.ymd }"
      @dragover.prevent="overCol = col.ymd"
      @dragleave.self="overCol = null"
      @drop.prevent="onDrop(col.ymd)"
    >
      <header class="pb__head">
        <span><b>{{ col.label }}</b> {{ col.day }}</span>
        <button type="button" :title="`在${col.label}添加任务`" @click="emit('add', col.ymd)"><Icon icon="lucide:plus" /></button>
      </header>
      <div class="pb__load" :class="{ 'pb__load--full': col.full }" :title="`约 ${hours(col.minutes)} 小时（未预估的按 30 分钟算）`">
        <i :style="{ width: `${Math.min(100, (col.minutes / FULL_MIN) * 100)}%` }" />
      </div>
      <p class="pb__sum" :class="{ 'pb__sum--full': col.full }">
        <template v-if="col.items.length || col.meets.length">约 {{ hours(col.minutes) }} 小时<template v-if="col.full"> · 排满了</template></template>
        <template v-else>空闲</template>
      </p>

      <router-link
        v-for="m in col.meets"
        :key="`m${m.id}`"
        :to="{ name: 'meeting', params: { id: String(m.id) } }"
        class="pb__meet"
        :title="`会议：${m.title}`"
      >
        <b>{{ m.title }}</b>
        <small>{{ m.startTime }} · 会议 · {{ m.durationMin }} 分钟</small>
      </router-link>
      <article
        v-for="t in col.items"
        :key="t.id"
        class="pb__card"
        :class="[`pb__card--p${t.priority}`, { 'pb__card--on': activeId === String(t.id), 'pb__card--drag': dragId === String(t.id) }]"
        draggable="true"
        tabindex="0"
        @dragstart="dragId = String(t.id)"
        @dragend="dragId = null; overCol = null"
        @click="emit('open', t)"
        @keydown.enter="emit('open', t)"
      >
        <b>{{ t.title }}</b>
        <small v-if="metaOf(t)">{{ metaOf(t) }}</small>
      </article>
      <p v-if="dragId && overCol === col.ymd" class="pb__drop">放到{{ col.label }}</p>
    </section>
  </div>
</template>

<style scoped>
.pb {
  display: grid;
  grid-template-columns: repeat(7, minmax(8.4rem, 1fr));
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.5rem;
}

.pb__col {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  min-height: 22rem;
  padding: 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.pb__col--weekend {
  background: color-mix(in srgb, var(--color-bg-surface) 55%, var(--color-bg-canvas));
}

.pb__col--over {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.pb__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.pb__head b {
  color: var(--color-text-primary);
}

.pb__head button {
  display: inline-grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.pb__head button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.pb__load {
  height: 0.3rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.pb__load i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent);
}

.pb__load--full i {
  background: var(--color-danger);
}

.pb__sum {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.pb__sum--full {
  color: var(--color-danger);
  font-weight: 600;
}

.pb__card {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  font-size: 0.82rem;
  cursor: grab;
}

.pb__card:hover {
  border-color: var(--color-brand);
}

.pb__card--p3 {
  border-left-color: #dc2626;
}

.pb__card--p2 {
  border-left-color: #ea8a0c;
}

.pb__card--p1 {
  border-left-color: #3b82f6;
}

.pb__card--on {
  background: var(--color-brand-soft);
}

.pb__card--drag {
  opacity: 0.4;
}

.pb__card b {
  font-weight: 600;
  line-height: 1.4;
}

.pb__card small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.pb__meet {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.45rem 0.6rem;
  border-left: 3px solid #3b82f6;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, #3b82f6 10%, var(--color-bg-surface));
  font-size: 0.8rem;
}

.pb__meet b {
  font-weight: 600;
}

.pb__meet small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.pb__drop {
  padding: 0.5rem;
  border: 1px dashed var(--color-brand);
  border-radius: var(--radius-md);
  font-size: 0.76rem;
  color: var(--color-brand);
  text-align: center;
}
</style>
