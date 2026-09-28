<script setup lang="ts">
/**
 * 专注的全部界面，挂在应用外壳上：
 * - 发起设置卡：时长四选一、结束后自动休息、标签页标题显示倒计时、结束时通知（设置会记住）
 * - 专注中：全屏，只留任务名、倒计时圆环和三个按钮；右栏子任务、记一笔（进随手记）、被打断计数
 * - 一轮结束先问「这项任务完成了吗？」，然后休息倒计时，给具体建议
 * Esc 退出全屏但计时继续，顶栏出现计时胶囊（AppHeader）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../base/BaseDialog.vue'
import { useFocusStore } from '../../stores/focus'
import { fetchFocusSessions } from '../../api/focus'
import { completeTask, fetchTask, updateTask } from '../../api/tasks'
import { createNote } from '../../api/notes'
import { errorText, toast } from '../../composables/useToast'
import { confirm } from '../../composables/useConfirm'
import { todayYmd } from '../../utils/date'
import type { Task } from '../../types/tasks'

const focus = useFocusStore()

// ── 设置卡 ──

const PRESETS = [25, 45, 60]
const custom = ref(false)
const customMin = ref(30)
const minutes = computed(() => (custom.value ? Math.max(1, customMin.value) : focus.settings.minutes))

watch(
  () => focus.setupFor,
  (value) => {
    if (!value) return
    custom.value = !PRESETS.includes(focus.settings.minutes)
    customMin.value = focus.settings.minutes
  },
)

const pick = (m: number) => {
  custom.value = false
  focus.settings.minutes = m
}

const toggleNotify = async () => {
  if (!focus.settings.notify && typeof Notification !== 'undefined' && Notification.permission === 'default') {
    await Notification.requestPermission()
  }
  focus.settings.notify = !focus.settings.notify && (typeof Notification === 'undefined' || Notification.permission === 'granted')
  if (!focus.settings.notify && typeof Notification !== 'undefined' && Notification.permission === 'denied') {
    toast.info('浏览器拒绝了通知权限，可以在地址栏左侧的站点设置里打开')
  }
}

const begin = () => {
  const target = focus.setupFor
  if (!target) return
  if (custom.value) focus.settings.minutes = minutes.value
  focus.start(target.taskId, target.taskTitle, minutes.value)
}

// ── 专注中：子任务、记一笔、今天累计 ──

const task = ref<Task | null>(null)
const todayMin = ref(0)
const todayRounds = ref(0)

const loadContext = async () => {
  const id = focus.running?.taskId ?? focus.result?.taskId
  task.value = null
  if (id !== null && id !== undefined) task.value = await fetchTask(id).catch(() => null)
  const today = todayYmd()
  const sessions = await fetchFocusSessions({ from: today, to: today }).catch(() => [])
  const done = sessions.filter((s) => s.status === 'done')
  todayMin.value = done.reduce((sum, s) => sum + s.actualMin, 0)
  todayRounds.value = done.length
}

watch(
  () => [focus.phase, focus.expanded],
  () => {
    if (focus.phase !== 'idle' && focus.expanded) loadContext()
  },
  { immediate: true },
)

const toggleSub = async (id: string) => {
  if (!task.value) return
  const subtasks = task.value.subtasks.map((s) => (s.id === id ? { ...s, done: !s.done } : s))
  task.value = { ...task.value, subtasks }
  try {
    task.value = await updateTask(task.value.id, { subtasks })
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const jot = ref('')
const saveJot = async () => {
  const content = jot.value.trim()
  if (!content) return
  try {
    await createNote({ content })
    jot.value = ''
    toast.ok('已记进随手记，回头再说')
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const abandon = async () => {
  if (focus.elapsedMs > 60_000) {
    const ok = await confirm({ title: '放弃这一轮？', message: '已经专注的时间会记为「放弃」，不计入完成轮数。', confirmText: '放弃', cancelText: '继续专注', danger: true })
    if (!ok) return
  }
  focus.finish('abandoned')
  toast.info('这一轮已放弃，用时照样记下')
}

// ── 一轮结束 ──

const taskTotal = computed(() => focus.result?.totalMin ?? focus.result?.actualMin ?? 0)

const taskDone = async () => {
  const id = focus.result?.taskId
  if (id !== null && id !== undefined) {
    try {
      await completeTask(id, true)
      toast.ok(`「${focus.result?.taskTitle}」已完成`)
    } catch (error) {
      toast.error(errorText(error, '操作失败'))
    }
  }
  focus.takeBreak()
}

const again = () => {
  const r = focus.result
  if (r) focus.start(r.taskId, r.taskTitle)
}

const TIPS = ['站起来走走，看看远处。', '喝口水，活动一下肩膀和手腕。', '闭眼深呼吸几次，别刷手机。', '去窗边站一会儿，让眼睛放松。']
const tip = computed(() => TIPS[(focus.result?.round ?? 0) % TIPS.length])

// ── 显示 ──

const clock = (sec: number) => `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`
const total = computed(() => (focus.phase === 'break' ? focus.settings.breakMinutes * 60 : (focus.running?.plannedMin ?? 25) * 60))
const ratio = computed(() => (total.value ? 1 - focus.remainingSec / total.value : 0))
const RADIUS = 110
const CIRC = 2 * Math.PI * RADIUS

/** 标签页标题显示倒计时，切到别的标签也能看到（标题里放不了图标，用文字前缀） */
const TITLE_PREFIX = /^(专注|暂停|休息) \d{2}:\d{2}/
let baseTitle = document.title
watch(
  () => [focus.phase, focus.remainingSec, focus.paused] as const,
  ([phase, sec, paused]) => {
    if (phase === 'focus' && focus.settings.titleCountdown) {
      if (!TITLE_PREFIX.test(document.title)) baseTitle = document.title
      document.title = `${paused ? '暂停' : '专注'} ${clock(sec)} · ${focus.running?.taskTitle ?? ''}`
    } else if (phase === 'break' && focus.settings.titleCountdown) {
      if (!TITLE_PREFIX.test(document.title)) baseTitle = document.title
      document.title = `休息 ${clock(sec)}`
    } else if (TITLE_PREFIX.test(document.title)) {
      document.title = baseTitle
    }
  },
)

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && focus.expanded && focus.phase !== 'idle' && !focus.setupFor && !document.querySelector('.dialog-mask')) {
    focus.expanded = false
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <!-- 发起设置卡 -->
  <BaseDialog :open="Boolean(focus.setupFor)" title="开始专注" width="26rem" @close="focus.setupFor = null">
    <div v-if="focus.setupFor" class="setup">
      <p class="setup__task"><b>{{ focus.setupFor.taskTitle }}</b><small v-if="focus.setupFor.subtitle">{{ focus.setupFor.subtitle }}</small></p>
      <div class="field">
        <span class="field__label">时长</span>
        <div class="durs">
          <button v-for="m in PRESETS" :key="m" type="button" :class="{ on: !custom && focus.settings.minutes === m }" @click="pick(m)">{{ m }} 分钟</button>
          <button type="button" :class="{ on: custom }" @click="custom = true">自定义</button>
        </div>
        <label v-if="custom" class="custom"><input v-model.number="customMin" type="number" min="1" max="180" class="field__input" /> 分钟</label>
      </div>
      <label class="sw"><input v-model="focus.settings.autoBreak" type="checkbox" />结束后自动休息 {{ focus.settings.breakMinutes }} 分钟</label>
      <label class="sw"><input v-model="focus.settings.titleCountdown" type="checkbox" />标签页标题显示倒计时</label>
      <label class="sw"><input :checked="focus.settings.notify" type="checkbox" @change="toggleNotify" />结束时发浏览器通知</label>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="focus.setupFor = null">取消</button>
      <button class="btn btn--primary" type="button" @click="begin"><Icon icon="lucide:play" />开始 {{ minutes }} 分钟</button>
    </template>
  </BaseDialog>

  <!-- 全屏 -->
  <Teleport to="body">
    <transition name="fx">
      <div v-if="focus.expanded && focus.phase !== 'idle'" class="fx" role="dialog" aria-modal="true" aria-label="专注">
        <button class="fx__min" type="button" @click="focus.expanded = false"><kbd>Esc</kbd> 退出全屏，计时继续</button>

        <!-- 专注中 -->
        <div v-if="focus.phase === 'focus' && focus.running" class="fx__body">
          <section class="fx__center">
            <p class="fx__eyebrow">第 {{ focus.running.round }} 轮 · 共 {{ focus.running.plannedMin }} 分钟</p>
            <h1 class="fx__title">{{ focus.running.taskTitle }}</h1>
            <div class="fx-ring">
              <svg viewBox="0 0 240 240" aria-hidden="true">
                <circle cx="120" cy="120" :r="RADIUS" class="fx-ring__bg" />
                <circle cx="120" cy="120" :r="RADIUS" class="fx-ring__fg" :stroke-dasharray="CIRC" :stroke-dashoffset="CIRC * ratio" />
              </svg>
              <div class="fx-ring__text">
                <b>{{ clock(focus.remainingSec) }}</b>
                <span>{{ focus.paused ? '已暂停' : '剩余' }}</span>
              </div>
            </div>
            <div class="fx__acts">
              <button v-if="focus.paused" class="btn btn--ghost" type="button" @click="focus.resume()"><Icon icon="lucide:play" />继续</button>
              <button v-else class="btn btn--ghost" type="button" @click="focus.pause()"><Icon icon="lucide:pause" />暂停</button>
              <button class="btn btn--primary" type="button" @click="focus.finish('done')"><Icon icon="lucide:check" />完成本轮</button>
              <button class="btn btn--quiet" type="button" @click="abandon"><Icon icon="lucide:x" />放弃</button>
            </div>
          </section>

          <aside class="fx__side">
            <section v-if="task?.subtasks.length" class="fx__card">
              <h3>子任务 <small>{{ task.subtasks.filter((s) => s.done).length }} / {{ task.subtasks.length }}</small></h3>
              <label v-for="s in task.subtasks" :key="s.id" class="sub" :class="{ done: s.done }">
                <input type="checkbox" :checked="s.done" @change="toggleSub(s.id)" />{{ s.title }}
              </label>
            </section>
            <section class="fx__card">
              <h3>记一笔 <small>进随手记</small></h3>
              <textarea v-model="jot" rows="3" placeholder="脑子里冒出别的事？写下来，回头再说…" @keydown.enter.exact.prevent="saveJot" />
              <button class="btn btn--ghost" type="button" :disabled="!jot.trim()" @click="saveJot">记下</button>
            </section>
            <section class="fx__card fx__row">
              <span>本轮被打断 <b>{{ focus.running.interruptions }}</b> 次</span>
              <button class="btn btn--ghost" type="button" @click="focus.interrupt()">+1</button>
            </section>
            <p class="fx__today">今天已专注 {{ todayMin }} 分钟 · {{ todayRounds }} 轮</p>
          </aside>
        </div>

        <!-- 一轮结束 -->
        <div v-else-if="focus.phase === 'ask' && focus.result" class="fx__done">
          <span class="fx__emoji"><Icon icon="lucide:timer" /></span>
          <h1>完成一轮</h1>
          <p>
            「{{ focus.result.taskTitle }}」这一轮 {{ focus.result.actualMin }} 分钟，累计 {{ taskTotal }} 分钟<template v-if="focus.result.interruptions">，被打断 {{ focus.result.interruptions }} 次</template>。
          </p>
          <h2>这项任务完成了吗？</h2>
          <div class="fx__acts">
            <button v-if="focus.result.taskId !== null" class="btn btn--primary" type="button" @click="taskDone"><Icon icon="lucide:check" />任务已完成</button>
            <button class="btn btn--ghost" type="button" @click="again"><Icon icon="lucide:rotate-ccw" />再来一轮</button>
            <button class="btn btn--ghost" type="button" @click="focus.takeBreak()">先放着</button>
          </div>
        </div>

        <!-- 休息 -->
        <div v-else-if="focus.phase === 'break'" class="fx__done">
          <span class="fx__emoji"><Icon icon="lucide:coffee" /></span>
          <h1>休息一下</h1>
          <p>{{ tip }}</p>
          <div class="fx-ring fx-ring--small">
            <svg viewBox="0 0 240 240" aria-hidden="true">
              <circle cx="120" cy="120" :r="RADIUS" class="fx-ring__bg" />
              <circle cx="120" cy="120" :r="RADIUS" class="fx-ring__fg ring__fg--break" :stroke-dasharray="CIRC" :stroke-dashoffset="CIRC * ratio" />
            </svg>
            <div class="fx-ring__text"><b>{{ clock(focus.remainingSec) }}</b></div>
          </div>
          <div class="fx__acts">
            <button class="btn btn--ghost" type="button" @click="again"><Icon icon="lucide:rotate-ccw" />再来一轮</button>
            <button class="btn btn--primary" type="button" @click="focus.skipBreak()">跳过休息</button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.setup {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1rem 1.35rem 0.25rem;
}

.setup__task {
  display: flex;
  flex-direction: column;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.setup__task small {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.durs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.35rem;
}

.durs button {
  padding: 0.45rem 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.86rem;
  cursor: pointer;
}

.durs button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 700;
}

.custom {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.4rem;
  font-size: 0.86rem;
}

.custom input {
  width: 5rem;
}

.sw {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.88rem;
}

.fx {
  position: fixed;
  inset: 0;
  z-index: 95;
  display: flex;
  flex-direction: column;
  padding: calc(1rem + env(safe-area-inset-top, 0px)) 1.5rem calc(1rem + env(safe-area-inset-bottom, 0px));
  overflow-y: auto;
  background: var(--color-bg-canvas);
}

.fx__min {
  align-self: flex-end;
  padding: 0.35rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
}

.fx__body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 20rem;
  gap: 2rem;
  align-items: center;
  width: min(64rem, 100%);
  margin: 0 auto;
}

.fx__center,
.fx__done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  text-align: center;
}

.fx__done {
  flex: 1;
  justify-content: center;
  max-width: 32rem;
  margin: 0 auto;
}

.fx__eyebrow {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.fx__title {
  font-size: clamp(1.3rem, 3vw, 1.8rem);
  font-weight: 800;
}

.fx-ring {
  position: relative;
  width: min(17rem, 70vw);
  aspect-ratio: 1;
}

.fx-ring--small {
  width: min(12rem, 60vw);
}

.fx-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.fx-ring__bg {
  fill: none;
  stroke: var(--color-bg-soft);
  stroke-width: 12;
}

.fx-ring__fg {
  fill: none;
  stroke: var(--color-brand);
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.5s linear;
}

.fx-ring__fg--break {
  stroke: var(--color-accent);
}

.fx-ring__text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.fx-ring__text b {
  font-size: clamp(2.6rem, 7vw, 3.6rem);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.fx-ring__text span {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.fx__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
}

.fx__side {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.fx__card {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.fx__card h3 {
  font-size: 0.82rem;
  font-weight: 700;
}

.fx__card h3 small {
  margin-left: 0.3rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.fx__card textarea {
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  font-size: 0.86rem;
  resize: none;
  outline: none;
}

.fx__card textarea:focus {
  border-color: var(--color-brand);
}

.fx__card .btn {
  align-self: flex-end;
  padding: 0.3rem 0.75rem;
  font-size: 0.82rem;
}

.fx__row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  font-size: 0.88rem;
}

.fx__row .btn {
  align-self: center;
}

.sub {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.88rem;
}

.sub.done {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.fx__today {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  text-align: center;
}

.fx__emoji {
  display: grid;
  place-items: center;
  color: var(--color-brand);
}

.fx__emoji svg {
  width: 3rem;
  height: 3rem;
}

.fx__done h1 {
  font-size: 1.6rem;
  font-weight: 800;
}

.fx__done h2 {
  margin-top: 0.5rem;
  font-size: 1rem;
  font-weight: 700;
}

.fx__done p {
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.fx-enter-active,
.fx-leave-active {
  transition: opacity 0.2s ease;
}

.fx-enter-from,
.fx-leave-to {
  opacity: 0;
}

@media (max-width: 860px) {
  .fx__body {
    grid-template-columns: 1fr;
    align-items: start;
  }
}
</style>
