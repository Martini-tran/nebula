<script setup lang="ts">
/**
 * 目标与纪念日：两类「以年为单位」的东西。
 * - 年度目标：不用手动更新进度，订阅其他模块的数据（跑量来自习惯、读书来自稍后读、存款来自记账、项目来自任务清单）。
 *   进度条上的竖线是年度时间进度，落后于竖线变黄并标「落后」；卡片底部写明数据从哪来、最近一次更新是什么。
 * - 纪念日与倒数日：按离今天最近排序，最近的一条放大成横幅；提醒可以自动生成任务。
 * ?tab=days &year=2025
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import GoalDialog from './components/GoalDialog.vue'
import AnnivForm from './components/AnnivForm.vue'
import { createAnniversary, createGoal, deleteAnniversary, deleteGoal, fetchAnniversaries, fetchGoals, updateAnniversary, updateGoal } from '../../api/goals'
import { fetchHabitLogs, fetchHabits } from '../../api/habits'
import { fetchReadingItems } from '../../api/reading'
import { fetchEntries } from '../../api/ledger'
import { fetchTaskLists, fetchTasks } from '../../api/tasks'
import { fetchFiles } from '../../api/files'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { diffDays, monthDay, todayYmd, weekdayLabel } from '../../utils/date'
import { computeProgress, formatValue, STATE_LABEL, yearPace, type GoalData } from './goalProgress'
import { daysOf, nextOccurrence, subtitleOf, syncAnniversaryTasks } from './annivDates'
import { ANNIV_TYPES, type Anniversary, type AnniversarySaveRequest, type Goal, type GoalSaveRequest, type KeyResult } from '../../types/goals'
import type { SpaceFile } from '../../types/files'

const route = useRoute()
const router = useRouter()
const today = todayYmd()
const thisYear = Number(today.slice(0, 4))

const tab = computed(() => (route.query.tab === 'days' ? 'days' : 'goals'))
const year = computed(() => {
  const y = Number(route.query.year)
  return Number.isInteger(y) && y > 2000 && y <= thisYear + 1 ? y : thisYear
})
const setTab = (t: 'goals' | 'days') => router.replace({ query: { ...route.query, tab: t === 'days' ? 'days' : undefined } })
const setYear = (y: number) => router.replace({ query: { ...route.query, year: y === thisYear ? undefined : String(y) } })

// ── 数据 ──

const goals = ref<Goal[]>([])
const annivs = ref<Anniversary[]>([])
const files = ref<SpaceFile[]>([])
const data = ref<GoalData>({ habits: [], logs: [], reading: [], entries: [], tasks: [], lists: [] })
const loading = ref(true)
const loadError = ref('')

const settle = <T,>(p: Promise<T>, fallback: T) => p.catch(() => fallback)

const load = async () => {
  loadError.value = ''
  const y = year.value
  try {
    const [g, a, habits, logs, reading, readingArch, entries, tasks, lists, fs] = await Promise.all([
      fetchGoals(y),
      fetchAnniversaries(),
      settle(fetchHabits(true), []),
      settle(fetchHabitLogs({ from: `${y}-01-01`, to: `${y}-12-31` }), []),
      settle(fetchReadingItems(), []),
      settle(fetchReadingItems({ archived: true }), []),
      settle(fetchEntries({ from: `${y}-01-01`, to: `${y}-12-31` }), []),
      settle(fetchTasks({ view: 'all' }), []),
      settle(fetchTaskLists(), []),
      settle(fetchFiles({ view: 'all' }), []),
    ])
    goals.value = g
    annivs.value = a
    files.value = fs
    data.value = { habits, logs, reading: [...reading, ...readingArch], entries, tasks, lists }
    // 快到日子的纪念日按设置生成任务
    const created = await syncAnniversaryTasks(annivs.value, today)
    if (created) toast.ok(`按纪念日提醒新建了 ${created} 条任务`, { action: { label: '去看看', run: () => router.push('/tasks') } })
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}
watch(year, () => {
  loading.value = true
  load()
})

// ── 年度进度 ──

const pace = computed(() => yearPace(year.value, today))
const daysLeft = computed(() => (year.value === thisYear ? diffDays(today, `${thisYear}-12-31`) : 0))

// ── 目标 ──

const cards = computed(() => goals.value.map((g) => ({ goal: g, p: computeProgress(g, data.value, today) })))

const listProgress = (kr: KeyResult) => {
  if (kr.listId === null) return ''
  const list = data.value.lists.find((l) => String(l.id) === String(kr.listId))
  const all = data.value.tasks.filter((t) => String(t.listId) === String(kr.listId))
  return list ? `任务清单「${list.name}」${all.filter((t) => t.done).length} / ${all.length}` : ''
}

const dialogOpen = ref(false)
const editingGoal = ref<Goal | null>(null)
const openGoal = (g: Goal | null) => {
  editingGoal.value = g
  dialogOpen.value = true
}
const saveGoal = async (body: GoalSaveRequest & { title: string; year: number }) => {
  try {
    if (editingGoal.value) await updateGoal(editingGoal.value.id, body)
    else await createGoal(body)
    dialogOpen.value = false
    toast.ok(editingGoal.value ? '目标已更新' : `已添加「${body.title}」`)
    load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}
const removeGoal = async (g: Goal) => {
  const ok = await confirm({ title: `删除目标「${g.title}」？`, message: '只删目标本身，习惯、记账这些来源数据不受影响。', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await deleteGoal(g.id)
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const bump = async (g: Goal, delta: number) => {
  const manualValue = Math.max(0, g.manualValue + delta)
  goals.value = goals.value.map((x) => (x.id === g.id ? { ...x, manualValue } : x))
  try {
    await updateGoal(g.id, { manualValue })
  } catch (error) {
    toast.error(errorText(error, '没存上'))
    load()
  }
}

const toggleKr = async (g: Goal, kr: KeyResult) => {
  const krs = g.krs.map((k) => (k.id === kr.id ? { ...k, done: !k.done, doneDate: k.done ? null : today } : k))
  goals.value = goals.value.map((x) => (x.id === g.id ? { ...x, krs } : x))
  try {
    await updateGoal(g.id, { krs })
    if (krs.every((k) => k.done)) toast.ok(`「${g.title}」全部完成 🎉`)
  } catch (error) {
    toast.error(errorText(error, '没存上'))
    load()
  }
}

const menuFor = ref<string | null>(null)

// ── 纪念日 ──

const sortedAnnivs = computed(() =>
  [...annivs.value].sort((a, b) => {
    // 倒数、每年的按还有几天；过去的倒数日与正数日放后面
    const key = (x: Anniversary) => (x.type === 'countup' ? 1e6 + daysOf(x, today) : daysOf(x, today) < 0 ? 2e6 - daysOf(x, today) : daysOf(x, today))
    return key(a) - key(b)
  }),
)
const hero = computed(() => sortedAnnivs.value.find((a) => a.type !== 'countup' && daysOf(a, today) >= 0))
const rows = computed(() => sortedAnnivs.value.filter((a) => a !== hero.value))

const editingAnniv = ref<Anniversary | null>(null)
const formKey = ref(0)
const editAnniv = (a: Anniversary) => {
  editingAnniv.value = a
  formKey.value += 1
}
const resetForm = () => {
  editingAnniv.value = null
  formKey.value += 1
}
const saveAnniv = async (body: AnniversarySaveRequest & { title: string; date: string }) => {
  try {
    if (editingAnniv.value) await updateAnniversary(editingAnniv.value.id, body)
    else await createAnniversary(body)
    toast.ok(editingAnniv.value ? '已保存' : `已添加「${body.title}」`)
    resetForm()
    load()
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}
const removeAnniv = async () => {
  const a = editingAnniv.value
  if (!a) return
  const ok = await confirm({ title: `删除「${a.title}」？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await deleteAnniversary(a.id)
    resetForm()
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const tagsOf = (a: Anniversary) => {
  const out: { text: string; brand?: boolean }[] = []
  if (a.type === 'annual') out.push({ text: '每年' })
  else if (a.type === 'countup') out.push({ text: '正数日' })
  else if (a.tag) out.push({ text: a.tag })
  if (a.remindDays !== null && a.type !== 'countup') {
    const r = a.remindDays === 0 ? '当天提醒' : `提前 ${a.remindDays} 天${a.createTask ? '建任务' : '提醒'}`
    out.push({ text: r, brand: true })
  }
  return out
}
const fileOf = (a: Anniversary) => files.value.find((f) => String(f.id) === String(a.fileId))
const openFile = (f: SpaceFile) => router.push({ path: '/files', query: { folder: f.folderId ? String(f.folderId) : undefined, f: String(f.id) } })
const heroDate = (a: Anniversary) => {
  const next = nextOccurrence(a, today)!
  return `${monthDay(next)} ${weekdayLabel(next)}`
}

onMounted(load)
</script>

<template>
  <div class="goals page">
    <header class="gh">
      <div class="seg" role="tablist" aria-label="视图">
        <button type="button" role="tab" :aria-selected="tab === 'goals'" :class="{ on: tab === 'goals' }" @click="setTab('goals')">年度目标</button>
        <button type="button" role="tab" :aria-selected="tab === 'days'" :class="{ on: tab === 'days' }" @click="setTab('days')">纪念日</button>
      </div>
      <template v-if="tab === 'goals'">
        <h1 class="page-title">{{ year }} 年目标</h1>
        <span class="gh__nav">
          <button class="btn btn--ghost" type="button" aria-label="上一年" @click="setYear(year - 1)"><Icon icon="lucide:chevron-left" /></button>
          <button class="btn btn--ghost" type="button" aria-label="下一年" :disabled="year > thisYear" @click="setYear(year + 1)"><Icon icon="lucide:chevron-right" /></button>
        </span>
        <div v-if="year === thisYear" class="yearbar">
          <div class="yearbar__bar"><i :style="{ width: `${pace * 100}%` }" /></div>
          <small><span>今年已过 {{ (pace * 100).toFixed(1) }}%</span><span>还剩 {{ daysLeft }} 天</span></small>
        </div>
        <button class="btn btn--primary gh__new" type="button" @click="openGoal(null)"><Icon icon="lucide:plus" />新目标</button>
      </template>
      <h1 v-else class="page-title">纪念日与倒数日</h1>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <!-- 年度目标 -->
    <template v-else-if="tab === 'goals'">
      <StateBlock v-if="!cards.length" state="empty" :title="`还没有 ${year} 年的目标`" description="定几个一年后想看到的结果；数值型会自动从习惯、稍后读、记账、任务里取进度。" action-label="新目标" @action="openGoal(null)" />
      <div v-else class="cards">
        <article v-for="{ goal: g, p } in cards" :key="g.id" class="goal surface" :class="`goal--${p.state}`">
          <div class="goal__top">
            <span class="goal__ico">{{ g.icon }}</span>
            <div class="goal__t">
              <h2>{{ g.title }}</h2>
              <small>{{ g.kind === 'metric' ? (g.source === 'manual' ? '数值 · 手动' : '数值 · 自动') : `关键结果 · ${p.value} / ${g.krs.length}` }}</small>
            </div>
            <span class="state">{{ STATE_LABEL[p.state] }}</span>
            <span class="menu">
              <button type="button" class="menu__btn" :aria-label="`${g.title}的更多操作`" @click="menuFor = menuFor === String(g.id) ? null : String(g.id)"><Icon icon="lucide:ellipsis" /></button>
              <span v-if="menuFor === String(g.id)" class="menu__pop surface" @mouseleave="menuFor = null">
                <button type="button" @click="menuFor = null; openGoal(g)"><Icon icon="lucide:pencil" />编辑</button>
                <button type="button" class="danger" @click="menuFor = null; removeGoal(g)"><Icon icon="lucide:trash-2" />删除</button>
              </span>
            </span>
          </div>
          <div class="gbar" :title="`进度 ${Math.round(p.pct * 100)}%，年度时间 ${Math.round(pace * 100)}%`">
            <i :style="{ width: `${Math.min(100, p.pct * 100)}%` }" />
            <span v-if="pace > 0 && pace < 1 && p.state !== 'done'" class="pace" :style="{ left: `${pace * 100}%` }" />
          </div>
          <template v-if="g.kind === 'metric'">
            <div class="gnum">
              <span><b>{{ formatValue(p.value, g.unit === '¥' ? '¥' : '').trim() }}</b> / {{ formatValue(g.target, g.unit) }}</span>
              <span>{{ p.projection }}</span>
            </div>
            <div class="feed">
              <span>{{ p.feed }}<template v-if="p.feedDelta"> · <b>{{ p.feedDelta }}</b></template></span>
              <span v-if="g.source === 'manual'" class="bump">
                <button type="button" :aria-label="`${g.title}减 1`" @click="bump(g, -1)"><Icon icon="lucide:minus" /></button>
                <button type="button" :aria-label="`${g.title}加 1`" @click="bump(g, 1)"><Icon icon="lucide:plus" /></button>
              </span>
            </div>
          </template>
          <div v-else class="krs">
            <label v-for="k in g.krs" :key="k.id" class="kr">
              <input type="checkbox" :checked="k.done" @change="toggleKr(g, k)" />
              <span class="ck" :class="{ 'ck--done': k.done }"><Icon v-if="k.done" icon="lucide:check" /></span>
              <span :class="{ done: k.done }">{{ k.title }}</span>
              <small>{{ k.done && k.doneDate ? `${Number(k.doneDate.slice(5, 7))} 月` : listProgress(k) }}</small>
            </label>
          </div>
        </article>
      </div>
    </template>

    <!-- 纪念日 -->
    <div v-else class="dd">
      <section>
        <div v-if="hero" class="hero" role="button" tabindex="0" @click="editAnniv(hero)" @keydown.enter="editAnniv(hero)">
          <div class="hero__n">{{ daysOf(hero) }}<small>{{ daysOf(hero) === 0 ? '就是今天' : '天' }}</small></div>
          <div class="hero__t">
            <h2>{{ hero.icon }} {{ hero.title }}</h2>
            <p>{{ heroDate(hero) }}<template v-if="hero.note"> · {{ hero.note }}</template></p>
          </div>
          <span class="hero__tag">{{ ANNIV_TYPES[hero.type].label }}</span>
        </div>

        <div class="panel surface">
          <p v-if="!rows.length && !hero" class="empty">还没有纪念日。右边加一个：生日、纪念日、证件到期都行。</p>
          <div v-for="a in rows" :key="a.id" class="drow" :class="{ on: editingAnniv?.id === a.id }" role="button" tabindex="0" @click="editAnniv(a)" @keydown.enter="editAnniv(a)">
            <span class="drow__ico">{{ a.icon }}</span>
            <div class="drow__main">
              <b>{{ a.title }}</b>
              <small>
                {{ subtitleOf(a) }}
                <template v-if="fileOf(a)"> · <button type="button" class="link" @click.stop="openFile(fileOf(a)!)">扫描件在文件柜</button></template>
              </small>
            </div>
            <span class="drow__tags"><span v-for="t in tagsOf(a)" :key="t.text" class="tag" :class="{ 'tag--brand': t.brand }">{{ t.text }}</span></span>
            <span class="drow__n" :class="{ soon: a.type !== 'countup' && daysOf(a) >= 0 && daysOf(a) <= 30, up: a.type === 'countup', past: a.type === 'countdown' && daysOf(a) < 0 }">
              {{ Math.abs(daysOf(a)).toLocaleString('zh-CN') }}<small>{{ a.type === 'countup' ? '天' : daysOf(a) < 0 ? '天前' : '天后' }}</small>
            </span>
          </div>
        </div>
      </section>

      <section class="panel surface">
        <header class="panel__head"><h2>{{ editingAnniv ? `编辑「${editingAnniv.title}」` : '新建' }}</h2></header>
        <AnnivForm :key="formKey" :editing="editingAnniv" :files="files" @save="saveAnniv" @cancel="resetForm" @remove="removeAnniv" />
      </section>
    </div>

    <GoalDialog :open="dialogOpen" :goal="editingGoal" :year="year" :habits="data.habits.filter((h) => !h.archived)" :lists="data.lists" @close="dialogOpen = false" @save="saveGoal" />
  </div>
</template>

<style scoped>
.gh {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
  margin-bottom: 1.2rem;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 0.9rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.gh__nav {
  display: inline-flex;
  gap: 0.25rem;
}

.gh__nav .btn {
  padding: 0.35rem;
}

.yearbar {
  flex: 1;
  min-width: 12rem;
  max-width: 22rem;
}

.yearbar__bar {
  height: 0.4rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.yearbar__bar i {
  display: block;
  height: 100%;
  background: var(--color-text-secondary);
  opacity: 0.55;
}

.yearbar small {
  display: flex;
  justify-content: space-between;
  margin-top: 0.25rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.gh__new {
  margin-left: auto;
}

.cards {
  columns: 3 20rem;
  column-gap: 1rem;
}

.goal {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
  break-inside: avoid;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
}

.goal__top {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.goal__ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 1.25rem;
}

.goal__t {
  flex: 1;
  min-width: 0;
}

.goal__t h2 {
  font-size: 1rem;
  font-weight: 800;
}

.goal__t small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.state {
  padding: 0.05rem 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
  font-weight: 600;
  white-space: nowrap;
}

.goal--ahead .state,
.goal--ok .state,
.goal--done .state {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.goal--behind .state {
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
}

.menu {
  position: relative;
}

.menu__btn {
  display: grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.menu__btn:hover {
  background: var(--color-bg-soft);
}

.menu__pop {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  width: 7rem;
  padding: 0.25rem;
  box-shadow: var(--shadow-lg);
}

.menu__pop button {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  font-size: 0.84rem;
  cursor: pointer;
}

.menu__pop button:hover {
  background: var(--color-bg-soft);
}

.menu__pop .danger {
  color: var(--color-danger);
}

.gbar {
  position: relative;
  height: 0.6rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.gbar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent);
}

.goal--behind .gbar i {
  background: #f59e0b;
}

.goal--active .gbar i {
  background: var(--color-brand);
}

.pace {
  position: absolute;
  top: -0.25rem;
  bottom: -0.25rem;
  width: 2px;
  background: var(--color-text-primary);
  opacity: 0.55;
}

.gnum {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.3rem 1rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.gnum b {
  font-size: 1.15rem;
  color: var(--color-text-primary);
}

.feed {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding-top: 0.6rem;
  border-top: 1px dashed var(--color-border);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.feed > span:first-child {
  flex: 1;
}

.feed b {
  color: var(--color-accent-text);
}

.bump {
  display: flex;
  gap: 0.2rem;
}

.bump button {
  display: grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  cursor: pointer;
}

.bump button:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.krs {
  display: flex;
  flex-direction: column;
}

.kr {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.3rem 0;
  font-size: 0.86rem;
  cursor: pointer;
}

.kr input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.kr:focus-within .ck {
  outline: 2px solid var(--color-brand);
  outline-offset: 1px;
}

.ck {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 1rem;
  height: 1rem;
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

.kr .done {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.kr small {
  margin-left: auto;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.dd {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 21rem;
  gap: 1rem;
  align-items: start;
}

.hero {
  display: flex;
  align-items: center;
  gap: 1.2rem;
  margin-bottom: 1rem;
  padding: 1.1rem 1.3rem;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-brand), color-mix(in srgb, var(--color-brand) 55%, #db2777));
  color: #fff;
  cursor: pointer;
}

.hero__n {
  font-size: 2.6rem;
  font-weight: 800;
  line-height: 1;
}

.hero__n small {
  margin-left: 0.2rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.hero__t {
  flex: 1;
}

.hero__t h2 {
  font-size: 1.1rem;
  font-weight: 800;
}

.hero__t p {
  font-size: 0.84rem;
  opacity: 0.9;
}

.hero__tag {
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 0.76rem;
}

.panel {
  border-radius: var(--radius-lg);
}

.panel__head {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.panel__head h2 {
  font-size: 0.92rem;
  font-weight: 800;
}

.empty {
  padding: 1rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.drow {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.drow:last-child {
  border-bottom: 0;
}

.drow:hover,
.drow.on {
  background: var(--color-bg-soft);
}

.drow__ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.3rem;
  height: 2.3rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 1.15rem;
}

.drow__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.drow__main small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.76rem;
  cursor: pointer;
}

.drow__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.25rem;
}

.tag {
  padding: 0 0.45rem;
  font-size: 0.7rem;
}

.drow__n {
  min-width: 4.2rem;
  font-size: 1.3rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.drow__n small {
  margin-left: 0.15rem;
  font-size: 0.72rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.drow__n.soon {
  color: var(--color-brand);
}

.drow__n.up {
  color: var(--color-accent-text);
}

.drow__n.past {
  color: var(--color-text-secondary);
}

@media (max-width: 1000px) {
  .dd {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .cards {
    columns: 1;
  }

  .drow__tags {
    display: none;
  }

  .gh__new {
    margin-left: 0;
  }
}
</style>
