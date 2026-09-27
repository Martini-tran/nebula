import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { createFocusSession, fetchFocusSessions } from '../api/focus'
import { fetchHabitLogs, fetchHabits, setHabitLog } from '../api/habits'
import { todayYmd } from '../utils/date'
import type { EntityId } from '../types/space'
import type { FocusSettings } from '../types/focus'

const SETTINGS_KEY = 'nebula-space:focus-settings'
const STATE_KEY = 'nebula-space:focus-state'

export type FocusPhase = 'idle' | 'focus' | 'ask' | 'break'

interface Running {
  taskId: EntityId | null
  taskTitle: string
  plannedMin: number
  /** 毫秒时间戳 */
  startedAt: number
  pausedAt: number | null
  pausedMs: number
  interruptions: number
  round: number
}

interface Result {
  taskId: EntityId | null
  taskTitle: string
  actualMin: number
  interruptions: number
  round: number
  /** 这个任务累计专注分钟（写入本轮记录后再算） */
  totalMin?: number
}

const read = <T>(key: string, fallback: T): T => {
  try {
    const raw = localStorage.getItem(key)
    return raw ? { ...fallback, ...(JSON.parse(raw) as T) } : fallback
  } catch {
    return fallback
  }
}

const stamp = (ms: number) => {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 专注（番茄钟）：挂在任务上，每一段都记到某个任务名下。
 * 计时以前端为准，状态存 localStorage，刷新页面、切换路由都不中断；一轮结束（完成或放弃）上报一次。
 */
export const useFocusStore = defineStore('focus', () => {
  const settings = ref<FocusSettings>(read(SETTINGS_KEY, { minutes: 25, autoBreak: true, breakMinutes: 5, titleCountdown: true, notify: false }))
  const saved = read<{ phase: FocusPhase; running: Running | null; breakEndsAt: number; result: Result | null }>(STATE_KEY, {
    phase: 'idle',
    running: null,
    breakEndsAt: 0,
    result: null,
  })

  const phase = ref<FocusPhase>(saved.phase)
  const running = ref<Running | null>(saved.running)
  const breakEndsAt = ref(saved.breakEndsAt)
  const result = ref<Result | null>(saved.result)
  /** 全屏显示；Esc 收起成顶栏的小胶囊，计时继续 */
  const expanded = ref(phase.value !== 'idle')
  /** 发起设置卡：要为哪个任务开始 */
  const setupFor = ref<{ taskId: EntityId | null; taskTitle: string; subtitle?: string } | null>(null)
  const now = ref(Date.now())

  watch(settings, (value) => localStorage.setItem(SETTINGS_KEY, JSON.stringify(value)), { deep: true })
  watch(
    [phase, running, breakEndsAt, result],
    () => {
      try {
        localStorage.setItem(STATE_KEY, JSON.stringify({ phase: phase.value, running: running.value, breakEndsAt: breakEndsAt.value, result: result.value }))
      } catch {
        // 存不了就只在本页有效
      }
    },
    { deep: true },
  )

  const elapsedMs = computed(() => {
    const r = running.value
    if (!r) return 0
    const end = r.pausedAt ?? now.value
    return Math.max(0, end - r.startedAt - r.pausedMs)
  })

  const remainingSec = computed(() => {
    if (phase.value === 'break') return Math.max(0, Math.ceil((breakEndsAt.value - now.value) / 1000))
    const r = running.value
    if (!r) return 0
    return Math.max(0, Math.ceil(r.plannedMin * 60 - elapsedMs.value / 1000))
  })

  const paused = computed(() => Boolean(running.value?.pausedAt))

  const notify = (title: string, body: string) => {
    if (!settings.value.notify || typeof Notification === 'undefined' || Notification.permission !== 'granted') return
    try {
      new Notification(title, { body })
    } catch {
      // 某些环境不支持直接 new Notification
    }
  }

  // ── 计时 ──

  let ticker: ReturnType<typeof setInterval> | undefined
  const ensureTicker = () => {
    clearInterval(ticker)
    if (phase.value === 'idle' || phase.value === 'ask') return
    ticker = setInterval(() => {
      now.value = Date.now()
      if (phase.value === 'focus' && !paused.value && remainingSec.value <= 0) finish('done')
      if (phase.value === 'break' && remainingSec.value <= 0) {
        phase.value = 'idle'
        expanded.value = false
        notify('休息结束', '可以开始下一轮了')
        ensureTicker()
      }
    }, 500)
  }
  watch(phase, ensureTicker, { immediate: true })

  const start = (taskId: EntityId | null, taskTitle: string, minutes = settings.value.minutes) => {
    const round = result.value && String(result.value.taskId) === String(taskId) ? result.value.round + 1 : 1
    running.value = { taskId, taskTitle, plannedMin: minutes, startedAt: Date.now(), pausedAt: null, pausedMs: 0, interruptions: 0, round }
    now.value = Date.now()
    phase.value = 'focus'
    expanded.value = true
    setupFor.value = null
    ensureTicker()
  }

  const pause = () => {
    if (running.value && !running.value.pausedAt) running.value.pausedAt = Date.now()
  }

  const resume = () => {
    const r = running.value
    if (!r?.pausedAt) return
    r.pausedMs += Date.now() - r.pausedAt
    r.pausedAt = null
  }

  const interrupt = () => {
    if (running.value) running.value.interruptions += 1
  }

  /** 时长型、勾了「专注自动累加」、任务标题里含习惯名的习惯，把这轮分钟数加进今天 */
  const feedHabits = async (title: string, minutes: number) => {
    try {
      const habits = (await fetchHabits()).filter((h) => h.kind === 'duration' && h.fromFocus && title.includes(h.name))
      const today = todayYmd()
      for (const h of habits) {
        const [log] = await fetchHabitLogs({ habitId: h.id, from: today, to: today })
        await setHabitLog(h.id, today, (log?.value ?? 0) + minutes, log?.note)
      }
    } catch {
      // 习惯累加失败不影响专注记录
    }
  }

  /** 结束本轮：完成（到点或手动）/ 放弃，都上报一次 */
  const finish = async (status: 'done' | 'abandoned') => {
    const r = running.value
    if (!r || phase.value !== 'focus') return
    const endedAt = Date.now()
    const actualMin = Math.max(1, Math.round(elapsedMs.value / 60000))
    phase.value = status === 'done' ? 'ask' : 'idle'
    result.value = { taskId: r.taskId, taskTitle: r.taskTitle, actualMin, interruptions: r.interruptions, round: r.round }
    running.value = null
    if (status === 'abandoned') expanded.value = false
    else notify('完成一轮专注', `「${r.taskTitle}」${actualMin} 分钟`)
    try {
      await createFocusSession({
        taskId: r.taskId,
        taskTitle: r.taskTitle,
        startedAt: stamp(r.startedAt),
        endedAt: stamp(endedAt),
        plannedMin: r.plannedMin,
        actualMin,
        status,
        interruptions: r.interruptions,
      })
      if (status === 'done') {
        if (r.taskId !== null) {
          const list = await fetchFocusSessions({ taskId: r.taskId })
          const totalMin = list.filter((x) => x.status === 'done').reduce((sum, x) => sum + x.actualMin, 0)
          if (result.value) result.value = { ...result.value, totalMin }
        }
        await feedHabits(r.taskTitle, actualMin)
      }
    } catch {
      // mock / 网络失败时只丢这一条记录，不打断流程
    }
  }

  /** 一轮结束后：进入休息，或直接回到空闲 */
  const takeBreak = () => {
    if (!settings.value.autoBreak) {
      phase.value = 'idle'
      expanded.value = false
      return
    }
    breakEndsAt.value = Date.now() + settings.value.breakMinutes * 60_000
    now.value = Date.now()
    phase.value = 'break'
  }

  const skipBreak = () => {
    phase.value = 'idle'
    expanded.value = false
  }

  const openSetup = (taskId: EntityId | null, taskTitle: string, subtitle?: string) => {
    if (phase.value === 'focus') {
      expanded.value = true
      return
    }
    setupFor.value = { taskId, taskTitle, subtitle }
  }

  return {
    settings,
    phase,
    running,
    result,
    expanded,
    setupFor,
    remainingSec,
    elapsedMs,
    paused,
    start,
    pause,
    resume,
    interrupt,
    finish,
    takeBreak,
    skipBreak,
    openSetup,
  }
})
