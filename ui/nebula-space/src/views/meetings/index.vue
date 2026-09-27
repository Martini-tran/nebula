<script setup lang="ts">
/**
 * 会议列表：按天分组，每场会一行（时间、主题、参会人、状态）。
 * 右栏是跨会议汇总的「我还没做完的会议待办」与会议模板。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import NewMeetingDialog from './components/NewMeetingDialog.vue'
import { fetchMeetings } from '../../api/meetings'
import { completeTask, fetchTasks } from '../../api/tasks'
import { useMyNames } from '../../composables/useMeetingSync'
import { errorText, toast } from '../../composables/useToast'
import { addDays, diffDays, monthDay, relativeDay, todayYmd, weekdayLabel } from '../../utils/date'
import { parseMeetingItems } from '../../utils/meetingItems'
import { statusOf, summaryOf } from './meetingInfo'
import { MEETING_TEMPLATES, type Meeting } from '../../types/meetings'
import type { Task } from '../../types/tasks'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()

const meetings = ref<Meeting[]>([])
const actions = ref<Task[]>([])
const loading = ref(true)
const loadError = ref('')

const tab = computed(() => (route.query.tab === 'all' || route.query.tab === 'actions' ? route.query.tab : 'recent'))

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [list, tasks] = await Promise.all([fetchMeetings(), fetchTasks({ view: 'all', sourceType: 'meeting' })])
    meetings.value = list
    actions.value = tasks.filter((t) => !t.done)
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const today = todayYmd()
  if (tab.value === 'recent') return meetings.value.filter((m) => m.date >= addDays(today, -14) && m.date <= addDays(today, 14))
  if (tab.value === 'actions') {
    return meetings.value.filter((m) => parseMeetingItems(m.content, myNames, m.date).actions.length > 0)
  }
  return meetings.value
})

/** 今天和以后的按时间正序在前，过去的倒序在后 */
const groups = computed(() => {
  const today = todayYmd()
  const byDay = new Map<string, Meeting[]>()
  for (const m of filtered.value) byDay.set(m.date, [...(byDay.get(m.date) ?? []), m])
  const days = [...byDay.keys()]
  const upcoming = days.filter((d) => d >= today).sort()
  const past = days.filter((d) => d < today).sort().reverse()
  return [...upcoming, ...past].map((day) => ({
    day,
    title: `${relativeDay(day)}${['今天', '明天', '昨天', '后天'].includes(relativeDay(day)) ? ` · ${monthDay(day)} ${weekdayLabel(day)}` : ''}`,
    past: day < today,
    items: byDay.get(day)!.sort((a, b) => a.startTime.localeCompare(b.startTime)),
  }))
})

const setTab = (value: string) => router.replace({ query: value === 'recent' ? {} : { tab: value } })

// ── 新会议 ──

const dialog = ref<{ open: boolean; template: string | null }>({ open: false, template: null })
const onCreated = (meeting: Meeting) => {
  dialog.value.open = false
  router.push({ name: 'meeting', params: { id: String(meeting.id) } })
}

// ── 我的会议待办 ──

const dueText = (task: Task) => {
  if (!task.dueDate) return '没定日期'
  const days = diffDays(todayYmd(), task.dueDate)
  return days < 0 ? `逾期 ${-days} 天` : relativeDay(task.dueDate)
}

const finish = async (task: Task) => {
  actions.value = actions.value.filter((t) => t.id !== task.id)
  try {
    await completeTask(task.id, true)
    toast.ok(`已完成「${task.title}」`, {
      action: {
        label: '撤销',
        run: async () => {
          await completeTask(task.id, false)
          load()
        },
      },
    })
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
    load()
  }
}

const initials = (name: string) => (name === '我' ? '我' : name.charAt(0))

onMounted(() => {
  load()
  if (route.query.new) dialog.value = { open: true, template: typeof route.query.new === 'string' && route.query.new !== '1' ? route.query.new : null }
})
</script>

<template>
  <div class="mt page">
    <section class="mt__main">
      <header class="mt__head">
        <h1 class="page-title">会议</h1>
        <div class="seg" role="tablist" aria-label="范围">
          <button v-for="t in [{ k: 'recent', l: '近期' }, { k: 'all', l: '全部' }, { k: 'actions', l: '有待办' }]" :key="t.k" type="button" role="tab" :aria-selected="tab === t.k" :class="{ on: tab === t.k }" @click="setTab(t.k)">
            {{ t.l }}
          </button>
        </div>
        <button class="btn btn--primary" type="button" @click="dialog = { open: true, template: null }"><Icon icon="lucide:plus" />新会议</button>
      </header>

      <StateBlock v-if="loading && !meetings.length" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
      <StateBlock
        v-else-if="!groups.length"
        state="empty"
        title="还没有会议记录"
        description="会前准备议程，会中边听边记，会后一键出纪要，分给你的待办自动进入任务。"
      />

      <section v-for="g in groups" :key="g.day" class="day" :class="{ 'day--past': g.past }">
        <h2 class="day__title">{{ g.title }}</h2>
        <ul class="day__list surface">
          <li v-for="m in g.items" :key="m.id">
            <router-link :to="{ name: 'meeting', params: { id: String(m.id) } }" class="row">
              <span class="row__time"><b>{{ m.startTime }}</b><small>{{ m.durationMin }} 分钟</small></span>
              <span class="row__main">
                <b>{{ m.title }}</b>
                <span class="row__sub">
                  <span class="faces">
                    <i v-for="a in m.attendees.slice(0, 5)" :key="a.name" :class="{ me: a.me, absent: a.absent }" :title="a.name">{{ initials(a.name) }}</i>
                  </span>
                  {{ summaryOf(m, myNames) }}
                </span>
              </span>
              <span class="row__status" :class="`st--${statusOf(m).tone}`">{{ statusOf(m).text }}</span>
              <span v-if="m.status === 'planned' && m.date === todayYmd()" class="btn btn--ghost row__go">开始记录</span>
            </router-link>
          </li>
        </ul>
      </section>
    </section>

    <aside class="mt__side">
      <section class="panel surface">
        <h3>我的会议待办 <small>{{ actions.length }}</small></h3>
        <p v-if="!actions.length" class="panel__empty">会上分给你的待办都做完了。</p>
        <ul class="acts">
          <li v-for="t in actions" :key="t.id">
            <button type="button" class="check" :aria-label="`完成「${t.title}」`" @click="finish(t)" />
            <router-link :to="{ path: '/tasks', query: { v: 'all', task: String(t.id) } }" class="acts__text">
              <b>{{ t.title }}</b>
              <small :class="{ late: t.dueDate && t.dueDate < todayYmd() }">{{ dueText(t) }} · {{ t.source?.label }}</small>
            </router-link>
          </li>
        </ul>
      </section>

      <section class="panel surface">
        <h3>模板</h3>
        <button v-for="t in MEETING_TEMPLATES" :key="t.key" type="button" class="tpl" @click="dialog = { open: true, template: t.key }">
          <b>{{ t.name }}</b>
          <small>{{ t.agenda.map((a) => a.title).join(' · ') }}</small>
        </button>
      </section>
    </aside>

    <NewMeetingDialog :open="dialog.open" :template="dialog.template" @close="dialog.open = false" @created="onCreated" />
  </div>
</template>

<style scoped>
.mt {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 19rem;
  gap: 2rem;
  align-items: start;
}

.mt__main {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

.mt__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
}

.mt__head .btn--primary {
  margin-left: auto;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 0.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.day {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.day__title {
  padding-left: 0.25rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.day__list {
  margin: 0;
  padding: 0;
  overflow: hidden;
  list-style: none;
  border-radius: var(--radius-lg);
}

.day__list li + li {
  border-top: 1px solid var(--color-border);
}

.day--past .row {
  opacity: 0.8;
}

.row {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
}

.row:hover {
  background: var(--color-bg-soft);
}

.row__time {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 3.8rem;
}

.row__time b {
  font-variant-numeric: tabular-nums;
}

.row__time small,
.row__sub {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.row__main b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__sub {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.faces {
  display: inline-flex;
}

.faces i {
  display: grid;
  place-items: center;
  width: 1.3rem;
  height: 1.3rem;
  margin-right: -0.3rem;
  border: 2px solid var(--color-bg-surface);
  border-radius: 50%;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.62rem;
  font-style: normal;
  font-weight: 700;
}

.faces i.me {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.faces i.absent {
  opacity: 0.45;
}

.faces + * {
  margin-left: 0.3rem;
}

.row__status {
  flex: none;
  padding: 0.12rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.st--done {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.st--live {
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
}

.st--soon {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.st--muted {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.row__go {
  flex: none;
  padding: 0.3rem 0.75rem;
  font-size: 0.82rem;
}

.mt__side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border-radius: var(--radius-lg);
}

.panel h3 {
  font-size: 0.86rem;
  font-weight: 700;
}

.panel h3 small {
  margin-left: 0.2rem;
  color: var(--color-text-secondary);
}

.panel__empty {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.acts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.acts li {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.45rem 0;
}

.acts li + li {
  border-top: 1px solid var(--color-border);
}

.check {
  flex: none;
  width: 1rem;
  height: 1rem;
  margin-top: 0.2rem;
  border: 2px solid var(--color-text-secondary);
  border-radius: 50%;
  background: none;
  cursor: pointer;
}

.check:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.acts__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  font-size: 0.86rem;
}

.acts__text b {
  font-weight: 600;
}

.acts__text:hover b {
  color: var(--color-brand);
}

.acts__text small {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.acts__text small.late {
  color: var(--color-danger);
}

.tpl {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.tpl:hover {
  border-color: var(--color-brand);
}

.tpl small {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

@media (max-width: 960px) {
  .mt {
    grid-template-columns: 1fr;
  }

  .mt__side {
    position: static;
  }
}

@media (max-width: 560px) {
  .row__go,
  .faces {
    display: none;
  }

  .row {
    gap: 0.6rem;
  }
}
</style>
