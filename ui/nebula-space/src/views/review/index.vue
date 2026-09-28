<script setup lang="ts">
/**
 * 周回顾：每周一次，把散落在任务、会议、随手记、专注、习惯里的记录收成一页，给自己看。
 * 顶部五个数与上周对比（绿升红降，但「逾期」增加才是红）；左列完成的任务与本周决议；
 * 右列没做完的（逐条决定下周做还是放弃）、七天状态、留下来的笔记。?week=YYYY-MM-DD（这周第一天）
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { deleteTask, updateTask } from '../../api/tasks'
import { useDeferredDelete } from '../../composables/useDeferredDelete'
import { useMyNames } from '../../composables/useMeetingSync'
import { errorText, toast } from '../../composables/useToast'
import { addDays, diffDays, fromYmd, monthDay, relativeDay, startOfWeek, todayYmd, weekdayLabel, weekNumberOf, ymdOf } from '../../utils/date'
import { moodIcon } from '../../config/icons'
import { formatMinutes } from '../../utils/format'
import { loadWeek, summarize, type WeekSource } from './weekData'
import type { Task } from '../../types/tasks'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()
const today = todayYmd()
const thisWeek = startOfWeek(today)

const week = computed(() => {
  const q = route.query.week
  const start = typeof q === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(q) ? startOfWeek(q) : thisWeek
  return start > thisWeek ? thisWeek : start
})
const end = computed(() => addDays(week.value, 6))
const isCurrent = computed(() => week.value === thisWeek)
const rangeText = computed(() => {
  const a = fromYmd(week.value)
  const b = fromYmd(end.value)
  return a.getMonth() === b.getMonth()
    ? `${a.getMonth() + 1} 月 ${a.getDate()} 日 – ${b.getDate()} 日`
    : `${a.getMonth() + 1} 月 ${a.getDate()} 日 – ${b.getMonth() + 1} 月 ${b.getDate()} 日`
})
const go = (offset: number) => {
  const next = addDays(week.value, offset * 7)
  router.replace({ query: next === thisWeek ? {} : { week: next } })
}

const src = ref<WeekSource | null>(null)
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    src.value = await loadWeek(week.value)
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}
watch(week, () => {
  loading.value = true
  load()
})

const deferred = useDeferredDelete({ remove: deleteTask, onCommitted: load })
const sum = computed(() => (src.value ? summarize(src.value, myNames) : null))

// ── 五个数 ──

type Tone = 'good' | 'bad' | 'flat'
const trend = (diff: number, text: string, higherIsBetter = true): { text: string; tone: Tone } => {
  if (!diff) return { text: '与上周持平', tone: 'flat' }
  const good = diff > 0 === higherIsBetter
  return { text: `${diff > 0 ? '↑' : '↓'} ${text}`, tone: good ? 'good' : 'bad' }
}

const kpis = computed(() => {
  const s = sum.value
  if (!s) return []
  const { cur, prev } = s
  const rate = (r: number | null) => (r === null ? null : Math.round(r * 100))
  const curRate = rate(cur.habitRate)
  const prevRate = rate(prev.habitRate)
  return [
    { key: 'done', label: '完成任务', icon: 'lucide:check', value: String(cur.done.length), ...trend(cur.done.length - prev.done.length, `${Math.abs(cur.done.length - prev.done.length)} 比上周`) },
    {
      key: 'meet',
      label: '会议',
      icon: 'lucide:users',
      value: String(cur.meetings.length),
      text: cur.meetings.length ? `共 ${formatMinutes(cur.meetings.reduce((a, m) => a + m.durationMin, 0))}` : '这周没开会',
      tone: 'flat' as Tone,
    },
    { key: 'focus', label: '专注', icon: 'lucide:timer', value: cur.focusMin ? formatMinutes(cur.focusMin) : '0', ...trend(cur.focusMin - prev.focusMin, formatMinutes(Math.abs(cur.focusMin - prev.focusMin))) },
    {
      key: 'habit',
      label: '习惯完成率',
      icon: 'lucide:activity',
      value: curRate === null ? '—' : `${curRate}%`,
      ...(curRate === null || prevRate === null ? { text: curRate === null ? '没有安排的习惯' : '上周没有数据', tone: 'flat' as Tone } : trend(curRate - prevRate, `${Math.abs(curRate - prevRate)}%`)),
    },
    { key: 'over', label: '逾期', icon: 'lucide:circle-alert', value: String(cur.overdue.length), ...trend(cur.overdue.length - prev.overdue.length, `${Math.abs(cur.overdue.length - prev.overdue.length)} 比上周`, false) },
  ]
})

// ── 完成了什么 ──

const expanded = ref(new Set<string>())
const SHOW = 4
const toggleGroup = (key: string) => {
  const next = new Set(expanded.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expanded.value = next
}

const doneMeta = (t: Task) => {
  const focus = sum.value?.focusByTask.get(String(t.id))
  if (focus) return `专注 ${formatMinutes(focus)}`
  if (t.source) return `来自${t.source.label}`
  return t.doneTime ? weekdayLabel(ymdOf(t.doneTime)) : ''
}

const openTask = (t: Task) => router.push({ path: '/tasks', query: { v: 'all', task: String(t.id) } })

// ── 没做完的 ──

const leftover = computed(() => (sum.value?.leftover ?? []).filter((t) => !deferred.isHidden(t.id)))
const nextWeek = computed(() => addDays(thisWeek, 7))
const menuFor = ref<string | null>(null)
const moveOptions = computed(() => [
  { label: '今天', value: today },
  ...Array.from({ length: 7 }, (_, i) => {
    const date = addDays(nextWeek.value, i)
    return { label: `下${weekdayLabel(date)}`, value: date }
  }),
  { label: '不定日期（收件箱）', value: null },
])

const moveTask = async (t: Task, date: string | null) => {
  menuFor.value = null
  try {
    await updateTask(t.id, { dueDate: date })
    toast.ok(date ? `「${t.title}」移到${relativeDay(date)}` : `「${t.title}」放回收件箱`)
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const giveUp = (t: Task) => {
  menuFor.value = null
  deferred.schedule(t.id, `已放弃「${t.title}」`)
}

const moveAll = async () => {
  const list = leftover.value
  try {
    await Promise.all(list.map((t) => updateTask(t.id, { dueDate: nextWeek.value })))
    toast.ok(`${list.length} 项已移到${relativeDay(nextWeek.value)}`)
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
    load()
  }
}

const overdueDays = (t: Task) => (t.dueDate && t.dueDate < today ? diffDays(t.dueDate, today) : 0)

const onDocClick = (event: MouseEvent) => {
  if (menuFor.value && !(event.target as HTMLElement).closest('.mv')) menuFor.value = null
}

// ── 七天 ──

const hours = (min: number) => (min ? `${Math.round((min / 60) * 10) / 10}h` : '')

onMounted(() => {
  load()
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div class="review page">
    <header class="rhead">
      <h1 class="page-title">第 {{ weekNumberOf(week) }} 周回顾</h1>
      <span class="rhead__range">{{ rangeText }}</span>
      <span class="rhead__nav">
        <button type="button" class="btn btn--ghost" aria-label="上一周" @click="go(-1)"><Icon icon="lucide:chevron-left" /></button>
        <button type="button" class="btn btn--ghost" aria-label="下一周" :disabled="isCurrent" @click="go(1)"><Icon icon="lucide:chevron-right" /></button>
      </span>
      <button v-if="!isCurrent" type="button" class="btn btn--quiet" @click="router.replace({ query: {} })">回到本周</button>
      <div class="rhead__right">
        <router-link class="btn btn--primary" :to="{ path: '/review/report', query: { type: 'week', date: week } }"><Icon icon="lucide:file-text" />写周报</router-link>
      </div>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <template v-else-if="sum">
      <div class="kpis">
        <div v-for="k in kpis" :key="k.key" class="kpi">
          <span><Icon :icon="k.icon" />{{ k.label }}</span>
          <b>{{ k.value }}</b>
          <small :class="`t--${k.tone}`">{{ k.text }}</small>
        </div>
      </div>

      <div class="rgrid">
        <div class="col">
          <section class="panel surface">
            <header class="panel__head"><h2><Icon icon="lucide:check" />完成了什么</h2><span class="tag">{{ sum.cur.done.length }}</span></header>
            <div class="panel__body">
              <p v-if="!sum.groups.length" class="empty">这周还没有完成的任务。</p>
              <div v-for="g in sum.groups" :key="g.key" class="grp">
                <h3><i :style="{ background: g.color }" />{{ g.name }} · {{ g.tasks.length }}</h3>
                <button
                  v-for="t in expanded.has(g.key) ? g.tasks : g.tasks.slice(0, SHOW)"
                  :key="t.id"
                  type="button"
                  class="li"
                  @click="openTask(t)"
                >
                  <span class="ck ck--done"><Icon icon="lucide:check" /></span>
                  <span class="li__text">{{ t.title }}</span>
                  <small>{{ doneMeta(t) }}</small>
                </button>
                <button v-if="g.tasks.length > SHOW" type="button" class="more" @click="toggleGroup(g.key)">
                  {{ expanded.has(g.key) ? '收起' : `+ ${g.tasks.length - SHOW} 项` }}
                </button>
              </div>
            </div>
          </section>

          <section class="panel surface">
            <header class="panel__head"><h2><Icon icon="lucide:gavel" />本周决议</h2><span class="tag">{{ sum.decisions.length }}</span></header>
            <div class="panel__body">
              <p v-if="!sum.decisions.length" class="empty">这周的会议里没有记下决议。会上写「决议：」开头的一行就会收到这里。</p>
              <router-link v-for="d in sum.decisions" :key="d.key" class="dec" :to="`/meetings/${d.meeting.id}`">
                {{ d.text }}<small>{{ d.meeting.title }} · {{ weekdayLabel(d.meeting.date) }}</small>
              </router-link>
            </div>
          </section>
        </div>

        <div class="col">
          <section class="panel surface">
            <header class="panel__head">
              <h2><Icon icon="lucide:clock" />没做完的 · {{ leftover.length }}</h2>
              <button v-if="leftover.length" class="btn btn--ghost btn--sm" type="button" @click="moveAll">全部移到下周</button>
            </header>
            <div class="panel__body">
              <p v-if="!leftover.length" class="empty">这周该做的都做完了。</p>
              <div v-for="t in leftover" :key="t.id" class="left">
                <span class="ck" :class="`ck--p${t.priority}`" />
                <button type="button" class="left__text" @click="openTask(t)">
                  {{ t.title }}
                  <small v-if="overdueDays(t)" class="over">逾期 {{ overdueDays(t) }} 天</small>
                  <small v-else>{{ relativeDay(t.dueDate!) }}</small>
                </button>
                <span class="mv">
                  <button type="button" class="chip" :aria-expanded="menuFor === String(t.id)" @click="menuFor = menuFor === String(t.id) ? null : String(t.id)">
                    移到… <Icon icon="lucide:chevron-down" />
                  </button>
                  <div v-if="menuFor === String(t.id)" class="mv__menu surface" role="menu">
                    <button v-for="o in moveOptions" :key="o.label" type="button" role="menuitem" @click="moveTask(t, o.value)">
                      {{ o.label }}<small v-if="o.value && o.value !== today">{{ monthDay(o.value) }}</small>
                    </button>
                    <button type="button" role="menuitem" class="danger" @click="giveUp(t)">放弃（删除）</button>
                  </div>
                </span>
              </div>
            </div>
          </section>

          <section class="panel surface">
            <header class="panel__head"><h2><Icon icon="lucide:activity" />这一周的状态</h2></header>
            <div class="panel__body">
              <div class="days7">
                <span v-for="d in sum.days" :key="d.date" :class="{ future: d.future, today: d.date === today }" :title="d.focusMin ? `专注 ${formatMinutes(d.focusMin)}` : undefined">
                  <b><Icon v-if="d.mood" :icon="moodIcon(d.mood)" :aria-label="d.mood" /><template v-else>·</template></b>{{ weekdayLabel(d.date) }}<small>{{ d.future ? '' : hours(d.focusMin) || '—' }}</small>
                </span>
              </div>
              <p class="days7__hint">心情来自晚间回顾写的日记，时长是当天专注。</p>
              <p v-if="sum.cur.perHabit.length" class="habits">
                <span>习惯：</span>
                <template v-for="(h, i) in sum.cur.perHabit" :key="h.habit.id">{{ i ? ' · ' : '' }}{{ h.habit.name }} {{ h.done }}/{{ h.expected }}</template>
              </p>
            </div>
          </section>

          <section class="panel surface">
            <header class="panel__head">
              <h2><Icon icon="lucide:pencil-line" />留下来的笔记</h2>
              <span class="muted">记 {{ sum.notes.total }} · 留 {{ sum.notes.kept }} · 归档 {{ sum.notes.archived }}</span>
            </header>
            <div class="panel__body">
              <p v-if="!sum.notes.list.length" class="empty">这周记的笔记没有转成长期的。</p>
              <router-link v-for="n in sum.notes.list" :key="n.id" class="li" :to="`/notes/${n.id}`">
                <span class="li__text">{{ n.title }}</span>
                <small>{{ n.tags.length ? n.tags.map((t) => `#${t}`).join(' ') : '长期' }}</small>
              </router-link>
            </div>
          </section>

          <section v-if="sum.lastPlan.length" class="panel surface">
            <header class="panel__head">
              <h2><Icon icon="lucide:history" />上周周报里的计划</h2>
              <router-link class="muted" :to="{ path: '/review/report', query: { type: 'week', date: addDays(week, -7) } }">看上周周报 →</router-link>
            </header>
            <div class="panel__body">
              <div v-for="(p, i) in sum.lastPlan" :key="i" class="li li--static">
                <span class="ck" :class="{ 'ck--done': p.done }"><Icon v-if="p.done" icon="lucide:check" /></span>
                <span class="li__text">{{ p.text }}</span>
                <small>{{ p.done === null ? '' : p.done ? '已完成' : '还没做' }}</small>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.rhead {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  margin-bottom: 1.1rem;
}

.rhead__range {
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.rhead__nav {
  display: inline-flex;
  gap: 0.25rem;
}

.rhead__nav .btn {
  padding: 0.35rem;
}

.rhead__right {
  margin-left: auto;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.7rem;
  margin-bottom: 1.1rem;
}

.kpi {
  display: flex;
  flex-direction: column;
  padding: 0.8rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.kpi span {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.kpi b {
  margin: 0.15rem 0;
  font-size: 1.5rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.kpi small {
  font-size: 0.72rem;
}

.t--good {
  color: var(--color-accent-text);
}

.t--bad {
  color: var(--color-danger);
}

.t--flat {
  color: var(--color-text-secondary);
}

.rgrid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.panel {
  border-radius: var(--radius-xl);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.panel__head h2 {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.95rem;
  font-weight: 800;
}

.panel__head h2 svg {
  color: var(--color-brand);
}

.panel__body {
  padding: 0.6rem 1rem 1rem;
}

.btn--sm {
  padding: 0.3rem 0.6rem;
  font-size: 0.8rem;
}

.muted {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.empty {
  padding: 0.6rem 0;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.grp h3 {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0.8rem 0 0.3rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.grp:first-child h3 {
  margin-top: 0.2rem;
}

.grp h3 i {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 0.2rem;
}

.li {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  width: 100%;
  padding: 0.35rem 0.3rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: inherit;
  text-align: left;
  font-size: 0.86rem;
  cursor: pointer;
}

.li:hover {
  background: var(--color-bg-soft);
}

.li--static {
  cursor: default;
}

.li--static:hover {
  background: none;
}

.li__text {
  flex: 1;
  min-width: 0;
}

.li small {
  margin-left: auto;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.ck {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 1rem;
  height: 1rem;
  margin-top: 0.12rem;
  border: 1.8px solid var(--color-border);
  border-radius: 50%;
}

.ck svg {
  width: 0.65rem;
  height: 0.65rem;
}

.ck--done {
  border-color: var(--color-accent);
  background: var(--color-accent);
  color: #fff;
}

.ck--p3 {
  border-color: #dc2626;
  background: color-mix(in srgb, #dc2626 10%, transparent);
}

.ck--p2 {
  border-color: #d97706;
  background: color-mix(in srgb, #d97706 10%, transparent);
}

.ck--p1 {
  border-color: #2563eb;
  background: color-mix(in srgb, #2563eb 10%, transparent);
}

.more {
  margin: 0.1rem 0 0 1.85rem;
  border: 0;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
}

.more:hover {
  color: var(--color-brand);
}

.dec {
  display: block;
  margin-bottom: 0.45rem;
  padding: 0.5rem 0.65rem;
  border-left: 3px solid var(--color-accent);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-accent-soft);
  font-size: 0.86rem;
  line-height: 1.6;
}

.dec small {
  display: block;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.left {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.left:last-child {
  border-bottom: 0;
}

.left .ck {
  margin-top: 0;
}

.left__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.left__text small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.left__text small.over {
  color: var(--color-danger);
}

.mv {
  position: relative;
  flex: none;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.18rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.78rem;
  cursor: pointer;
}

.chip:hover,
.chip[aria-expanded='true'] {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.mv__menu {
  position: absolute;
  top: calc(100% + 0.3rem);
  right: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  width: 12rem;
  padding: 0.3rem;
  box-shadow: var(--shadow-lg);
}

.mv__menu button {
  display: flex;
  justify-content: space-between;
  padding: 0.4rem 0.55rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  text-align: left;
  font-size: 0.84rem;
  cursor: pointer;
}

.mv__menu button:hover {
  background: var(--color-bg-soft);
}

.mv__menu small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.mv__menu .danger {
  margin-top: 0.2rem;
  border-top: 1px solid var(--color-border);
  border-radius: 0;
  color: var(--color-danger);
}

.days7 {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.35rem;
  padding-top: 0.25rem;
  text-align: center;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.days7 span {
  padding: 0.45rem 0;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  white-space: nowrap;
}

.days7 span.today {
  outline: 1.5px solid var(--color-brand);
}

.days7 span.future {
  opacity: 0.4;
}

.days7 small {
  display: block;
  min-height: 1.1em;
  font-size: 0.7rem;
}

.days7 b {
  display: grid;
  place-items: center;
  height: 1.2rem;
  margin-bottom: 0.1rem;
  font-size: 1rem;
}

.days7 b svg {
  width: 1rem;
  height: 1rem;
  color: var(--color-brand);
}

.days7__hint {
  margin-top: 0.5rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.habits {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  line-height: 1.7;
}

.habits span {
  color: var(--color-text-secondary);
}

@media (max-width: 1000px) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rgrid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .days7 {
    font-size: 0.64rem;
    gap: 0.2rem;
  }
}
</style>
