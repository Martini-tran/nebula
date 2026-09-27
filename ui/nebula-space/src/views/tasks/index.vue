<script setup lang="ts">
/**
 * 任务：个人待办，不做团队协作。视图 = 收件箱（没定日期）/ 今天（含过期）/ 计划（未来 7 天看板）/ 已完成，
 * 另按清单归类。点一行在右侧打开详情，不跳页。视图与选中的任务同步到地址栏（?v= / ?task=）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import SideNav, { type SideNavGroup } from '../../components/layout/SideNav.vue'
import StateBlock from '../../components/StateBlock.vue'
import NameDialog from '../bookmarks/components/NameDialog.vue'
import TaskQuickAdd from './components/TaskQuickAdd.vue'
import TaskRow from './components/TaskRow.vue'
import TaskDetail from './components/TaskDetail.vue'
import PlanBoard from './components/PlanBoard.vue'
import { completeTask, createTaskList, deleteTask, deleteTaskList, fetchTasks, updateTask, updateTaskList } from '../../api/tasks'
import { useTaskStore } from '../../stores/tasks'
import { useFocusStore } from '../../stores/focus'
import { fetchMeetings } from '../../api/meetings'
import type { Meeting } from '../../types/meetings'
import { useDeferredDelete } from '../../composables/useDeferredDelete'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { addDays, monthDay, relativeDay, todayYmd, weekdayLabel, ymdOf } from '../../utils/date'
import { nextTagColor } from '../bookmarks/tagColors'
import type { Task, TaskQuery } from '../../types/tasks'

const route = useRoute()
const router = useRouter()
const store = useTaskStore()
const focusStore = useFocusStore()

const today = ref(todayYmd())
const active = computed(() => (typeof route.query.v === 'string' && route.query.v ? route.query.v : 'today'))
const listId = computed(() => (active.value.startsWith('list:') ? active.value.slice(5) : null))
const currentList = computed(() => store.findList(listId.value))

/** 当前视图的任务 + 全部任务（小日历圆点、跨视图打开详情用） */
const tasks = ref<Task[]>([])
const planMeetings = ref<Meeting[]>([])
const allTasks = ref<Task[]>([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')
const sideOpen = ref(false)

const viewQuery = computed<TaskQuery>(() => {
  if (listId.value) return { view: 'all', listId: listId.value }
  return { view: active.value as TaskQuery['view'] }
})

let seq = 0
const load = async () => {
  const current = ++seq
  loading.value = true
  loadError.value = ''
  today.value = todayYmd()
  try {
    const [list, all] = await Promise.all([fetchTasks(viewQuery.value), fetchTasks({ view: 'all' }), store.reloadStats()])
    if (current !== seq) return
    tasks.value = list
    allTasks.value = all
    if (active.value === 'plan') {
      planMeetings.value = await fetchMeetings({ from: today.value, to: addDays(today.value, 6) }).catch(() => [])
    }
  } catch (error) {
    if (current === seq) loadError.value = errorText(error, '加载失败')
  } finally {
    if (current === seq) {
      loading.value = false
      loaded.value = true
    }
  }
}

watch(active, load)

const select = (key: string) => {
  sideOpen.value = false
  router.push({ query: key === 'today' ? {} : { v: key } })
}

// ── 侧栏 ──

const groups = computed<SideNavGroup[]>(() => {
  const s = store.stats
  return [
    {
      key: 'views',
      items: [
        { key: 'inbox', label: '收件箱', icon: 'lucide:inbox', count: s?.inbox },
        { key: 'today', label: '今天', icon: 'lucide:sun', count: s?.today, alert: Boolean(s?.overdue) },
        { key: 'plan', label: '计划', icon: 'lucide:calendar-range', count: s?.plan },
        { key: 'done', label: '已完成', icon: 'lucide:circle-check' },
      ],
    },
    {
      key: 'lists',
      title: '清单',
      addLabel: '新清单',
      empty: '还没有清单',
      items: store.lists.map((l) => ({ key: `list:${l.id}`, label: l.name, dot: l.color, count: s?.lists[String(l.id)] })),
    },
  ]
})

const heading = computed(() => {
  if (currentList.value) return currentList.value.name
  return { inbox: '收件箱', today: '今天', plan: '计划', done: '已完成' }[active.value] ?? '任务'
})

const open = computed(() => visible.value.filter((t) => !t.done))

const subheading = computed(() => {
  const t = today.value
  if (active.value === 'today') return `${monthDay(t)} ${weekdayLabel(t)} · 剩 ${open.value.length} 项`
  if (active.value === 'inbox') return '还没定日期的事，得空时挑几件排进日程'
  if (active.value === 'plan') return `${monthDay(t)} – ${monthDay(addDays(t, 6))} · 拖动卡片即改期`
  if (active.value === 'done') return '最近完成的任务'
  return `${open.value.length} 项未完成`
})

// ── 分组 ──

const deleter = useDeferredDelete({ remove: deleteTask, onCommitted: () => store.reloadStats() })
const visible = computed(() => tasks.value.filter((t) => !deleter.isHidden(t.id)))

interface Group {
  key: string
  title: string
  tasks: Task[]
  tone?: 'late'
  action?: { label: string; run: () => void }
  collapsible?: boolean
}

const doneOpen = ref(false)

const groupsOf = computed<Group[]>(() => {
  const t = today.value
  const list = visible.value
  const undone = list.filter((x) => !x.done)
  const late = undone.filter((x) => x.dueDate && x.dueDate < t)
  const lateGroup: Group = {
    key: 'late',
    title: '已过期',
    tasks: late,
    tone: 'late',
    action: { label: '全部改到今天', run: () => moveAll(late, t) },
  }

  if (active.value === 'today') {
    const todays = undone.filter((x) => x.dueDate === t)
    const doneToday = allTasks.value.filter((x) => x.done && !deleter.isHidden(x.id) && x.doneTime && ymdOf(x.doneTime) === t)
    return [
      lateGroup,
      { key: 'timed', title: '有时间', tasks: todays.filter((x) => x.dueTime).sort((a, b) => a.dueTime!.localeCompare(b.dueTime!)) },
      { key: 'untimed', title: '没定时间', tasks: todays.filter((x) => !x.dueTime) },
      { key: 'done', title: `已完成 ${doneToday.length} 项`, tasks: doneToday, collapsible: true },
    ].filter((g) => g.tasks.length)
  }

  if (active.value === 'inbox') return [{ key: 'inbox', title: '', tasks: undone }].filter((g) => g.tasks.length)

  if (active.value === 'done') {
    const byDay = new Map<string, Task[]>()
    list.forEach((x) => {
      const day = x.doneTime ? ymdOf(x.doneTime) : ''
      byDay.set(day, [...(byDay.get(day) ?? []), x])
    })
    return [...byDay.entries()].map(([day, items]) => ({ key: day, title: day ? relativeDay(day) : '更早', tasks: items }))
  }

  // 清单视图：按日期远近分组
  const done = list.filter((x) => x.done)
  return [
    lateGroup,
    { key: 'today', title: '今天', tasks: undone.filter((x) => x.dueDate === t) },
    { key: 'tomorrow', title: '明天', tasks: undone.filter((x) => x.dueDate === addDays(t, 1)) },
    { key: 'later', title: '以后', tasks: undone.filter((x) => x.dueDate && x.dueDate > addDays(t, 1)) },
    { key: 'nodate', title: '没定日期', tasks: undone.filter((x) => !x.dueDate) },
    { key: 'done', title: `已完成 ${done.length} 项`, tasks: done, collapsible: true },
  ].filter((g) => g.tasks.length)
})

// ── 详情 ──

const selectedId = computed(() => (typeof route.query.task === 'string' ? route.query.task : null))
const selected = computed(
  () =>
    tasks.value.find((t) => String(t.id) === selectedId.value) ??
    allTasks.value.find((t) => String(t.id) === selectedId.value) ??
    null,
)

const openTask = (task: Task) =>
  router.replace({ query: { ...route.query, task: selectedId.value === String(task.id) ? undefined : String(task.id) } })
const closeDetail = () => router.replace({ query: { ...route.query, task: undefined } })

const marked = computed(() => new Set(allTasks.value.filter((t) => !t.done && t.dueDate).map((t) => t.dueDate!)))

/** 本地替换一条任务；视图归属变了的（如改了日期）刷新一次列表 */
const onChanged = (task: Task) => {
  for (const arr of [tasks.value, allTasks.value]) {
    const index = arr.findIndex((t) => t.id === task.id)
    if (index >= 0) arr[index] = task
  }
  store.reloadStats()
}

// ── 完成 / 改期 / 删除 ──

const toggle = async (task: Task) => {
  const done = !task.done
  onChanged({ ...task, done, doneTime: done ? `${todayYmd()} 00:00:00` : null })
  try {
    const result = await completeTask(task.id, done)
    onChanged(result.task)
    if (done) {
      toast.ok(result.next ? `已完成，下一次：${relativeDay(result.next.dueDate!)}` : `已完成「${task.title}」`, {
        action: { label: '撤销', run: () => toggle(result.task) },
      })
    }
    if (result.next) load()
  } catch (error) {
    onChanged(task)
    toast.error(errorText(error, '操作失败'))
  }
}

const moveAll = async (items: Task[], date: string) => {
  try {
    await Promise.all(items.map((t) => updateTask(t.id, { dueDate: date })))
    toast.ok(`${items.length} 项已改到${relativeDay(date)}`)
    load()
  } catch (error) {
    toast.error(errorText(error, '改期失败'))
  }
}

const moveTo = async (task: Task, date: string) => {
  onChanged({ ...task, dueDate: date })
  try {
    onChanged(await updateTask(task.id, { dueDate: date }))
    toast.ok(`「${task.title}」改到${relativeDay(date)}`)
  } catch (error) {
    onChanged(task)
    toast.error(errorText(error, '改期失败'))
  }
}

const remove = (task: Task) => {
  closeDetail()
  deleter.schedule(task.id, `已删除「${task.title}」`)
}

// ── 添加 ──

const quickDefaults = computed(() => ({
  date: active.value === 'today' || active.value === 'plan' ? today.value : null,
  listId: listId.value,
  placeholder:
    active.value === 'inbox'
      ? '添加到收件箱…'
      : active.value === 'today'
        ? '添加今天的任务…'
        : currentList.value
          ? `添加到「${currentList.value.name}」…`
          : '添加任务…',
}))

const onAdded = (task: Task) => {
  load()
  const where = task.dueDate ? relativeDay(task.dueDate) : '收件箱'
  const inView =
    (active.value === 'today' && task.dueDate && task.dueDate <= today.value) ||
    (active.value === 'inbox' && !task.dueDate) ||
    (active.value === 'plan' && task.dueDate && task.dueDate <= addDays(today.value, 6)) ||
    (listId.value && String(task.listId) === listId.value)
  if (!inView) toast.ok(`已添加到${where}`, { action: { label: '查看', run: () => openTask(task) } })
}

const quickAdd = ref<InstanceType<typeof TaskQuickAdd> | null>(null)
const addOn = (date: string) => quickAdd.value?.prefill(`${monthDay(date)} `)

// ── 清单 ──

const nameDialog = ref({ open: false, title: '', initial: '', save: async (_: string) => {} })

const onSideAdd = () => {
  nameDialog.value = {
    open: true,
    title: '新清单',
    initial: '',
    save: async (name) => {
      const list = await createTaskList({ name, color: nextTagColor(store.lists.length) })
      nameDialog.value.open = false
      await store.reloadLists()
      select(`list:${list.id}`)
    },
  }
}

const renameList = () => {
  const list = currentList.value
  if (!list) return
  nameDialog.value = {
    open: true,
    title: '重命名清单',
    initial: list.name,
    save: async (name) => {
      await updateTaskList(list.id, { name })
      nameDialog.value.open = false
      await store.reloadLists()
    },
  }
}

const removeList = async () => {
  const list = currentList.value
  if (!list) return
  const ok = await confirm({
    title: `删除清单「${list.name}」？`,
    message: '清单里的任务不会被删除，只是不再属于任何清单。',
    confirmText: '删除清单',
    danger: true,
  })
  if (!ok) return
  try {
    await deleteTaskList(list.id)
    await store.reloadLists()
    select('today')
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

// ── 生命周期 ──

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && selected.value && !document.querySelector('.dialog-mask, .td__pop')) closeDetail()
}

onMounted(() => {
  store.reloadLists()
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="tasks page" :class="{ 'tasks--detail': selected }">
    <button class="side-toggle btn btn--ghost" type="button" :aria-expanded="sideOpen" @click="sideOpen = !sideOpen">
      <Icon icon="lucide:panel-left" /><span>{{ heading }}</span>
      <Icon :icon="sideOpen ? 'lucide:chevron-up' : 'lucide:chevron-down'" />
    </button>

    <aside class="tasks__side" :class="{ 'tasks__side--open': sideOpen }">
      <SideNav :groups="groups" :active="active" @select="select" @add="onSideAdd">
        <router-link to="/focus" class="focus-link"><Icon icon="lucide:timer" />专注统计</router-link>
      </SideNav>
    </aside>

    <section class="tasks__main">
      <header class="head">
        <div>
          <h1 class="page-title">
            <i v-if="currentList" class="head__dot" :style="{ background: currentList.color }" />{{ heading }}
          </h1>
          <p class="page-subtitle">{{ subheading }}</p>
        </div>
        <div v-if="currentList" class="head__acts">
          <button class="btn btn--ghost" type="button" @click="renameList"><Icon icon="lucide:pencil" />重命名</button>
          <button class="btn btn--ghost" type="button" @click="removeList"><Icon icon="lucide:trash-2" />删除清单</button>
        </div>
      </header>

      <TaskQuickAdd
        v-if="active !== 'done'"
        ref="quickAdd"
        :default-date="quickDefaults.date"
        :default-list-id="quickDefaults.listId"
        :placeholder="quickDefaults.placeholder"
        @added="onAdded"
      />

      <StateBlock v-if="loading && !loaded" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

      <PlanBoard
        v-else-if="active === 'plan'"
        :tasks="visible"
        :meetings="planMeetings"
        :active-id="selectedId"
        @open="openTask"
        @move="moveTo"
        @add="addOn"
      />

      <template v-else>
        <StateBlock
          v-if="!groupsOf.length"
          state="empty"
          :title="active === 'today' ? '今天没有待办了' : active === 'inbox' ? '收件箱是空的' : active === 'done' ? '还没有完成的任务' : '这个清单还是空的'"
          :description="active === 'today' ? '可以去收件箱挑几件，或者早点休息。' : '在上面输入框里添加，支持「明天下午3点 …」这样的写法。'"
        />
        <section v-for="g in groupsOf" :key="g.key" class="grp">
          <header v-if="g.title" class="grp__head" :class="{ 'grp__head--late': g.tone === 'late' }">
            <button v-if="g.collapsible" type="button" class="grp__fold" :aria-expanded="doneOpen" @click="doneOpen = !doneOpen">
              <Icon :icon="doneOpen ? 'lucide:chevron-down' : 'lucide:chevron-right'" />{{ g.title }}
            </button>
            <span v-else>{{ g.title }} <small>{{ g.tasks.length }}</small></span>
            <button v-if="g.action" type="button" class="grp__act" @click="g.action.run">{{ g.action.label }}</button>
          </header>
          <ul v-if="!g.collapsible || doneOpen" class="grp__list surface">
            <TaskRow
              v-for="t in g.tasks"
              :key="t.id"
              :task="t"
              :active="selectedId === String(t.id)"
              :show-date="active !== 'today' || g.key === 'late'"
              @open="openTask(t)"
              @toggle="toggle(t)"
              @focus="focusStore.openSetup(t.id, t.title)"
            />
          </ul>
        </section>
      </template>
    </section>

    <transition name="detail">
      <TaskDetail
        v-if="selected"
        :key="selected.id"
        class="tasks__detail"
        :task="selected"
        :marked="marked"
        @changed="onChanged"
        @toggle="toggle(selected)"
        @remove="remove(selected)"
        @close="closeDetail"
      />
    </transition>

    <NameDialog
      :open="nameDialog.open"
      :title="nameDialog.title"
      label="清单名称"
      :initial="nameDialog.initial"
      :save="nameDialog.save"
      @close="nameDialog.open = false"
    />
  </div>
</template>

<style scoped>
.tasks {
  display: grid;
  grid-template-columns: 14rem minmax(0, 1fr);
  gap: 2rem;
  align-items: start;
}

.tasks--detail {
  grid-template-columns: 14rem minmax(0, 1fr) 22rem;
  gap: 1.5rem;
}

.side-toggle {
  display: none;
}

.tasks__side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
}

.focus-link {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 0.6rem 0.4rem;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
}

.focus-link:hover {
  color: var(--color-brand);
}

.tasks__main {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  min-width: 0;
}

.tasks__detail {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  max-height: calc(100vh - var(--header-height) - 2.5rem);
  overflow-y: auto;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem;
}

.head .page-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.head__dot {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 50%;
}

.head__acts {
  display: flex;
  gap: 0.4rem;
}

.head__acts .btn {
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.grp {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.grp__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline: 0.25rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.grp__head small {
  margin-left: 0.25rem;
  font-weight: 600;
  opacity: 0.8;
}

.grp__head--late {
  color: var(--color-danger);
}

.grp__fold {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.grp__act {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}

.grp__list {
  margin: 0;
  padding: 0;
  overflow: hidden;
  list-style: none;
  border-radius: var(--radius-lg);
}

.detail-enter-active,
.detail-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s var(--ease-soft);
}

.detail-enter-from,
.detail-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

@media (max-width: 1180px) {
  .tasks--detail {
    grid-template-columns: 14rem minmax(0, 1fr);
  }

  /* 窄屏下详情改为右侧浮层 */
  .tasks__detail {
    position: fixed;
    top: var(--header-height);
    right: 0;
    bottom: 0;
    z-index: 60;
    width: min(24rem, 100%);
    max-height: none;
    border-radius: 0;
    box-shadow: var(--shadow-lg);
  }
}

@media (max-width: 900px) {
  .tasks,
  .tasks--detail {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .side-toggle {
    display: inline-flex;
    justify-self: start;
  }

  .tasks__side {
    display: none;
    position: static;
    padding: 1rem;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    background: var(--color-bg-surface);
  }

  .tasks__side--open {
    display: block;
  }
}
</style>
