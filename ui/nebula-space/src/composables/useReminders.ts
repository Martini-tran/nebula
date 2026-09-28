/**
 * 页面开着时的提醒：任务的「提前 N 分钟提醒」、习惯的提醒时间。
 * 浏览器已授权通知时发系统通知，页面在前台时同时给一条页内提示；没授权就只有页内提示。
 * 页面关掉就收不到——真正的离线推送要后端（或 Service Worker + Push）来做。
 */
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchTasks } from '../api/tasks'
import { fetchHabitLogs, fetchHabits } from '../api/habits'
import { useAuthStore } from '../stores/auth'
import { useSettingsStore } from '../stores/settings'
import { isDone, isScheduled } from '../utils/habitStats'
import { todayYmd } from '../utils/date'
import { toast } from './useToast'
import type { Task } from '../types/tasks'
import type { Habit, HabitLog } from '../types/habits'

const FIRED_PREFIX = 'nebula-space:reminded:'
/** 过了提醒时间多久以内还补发（刚打开页面时） */
const GRACE_MIN = 15

const minutesOf = (hm: string) => {
  const [h, m] = hm.split(':').map(Number)
  return (h ?? 0) * 60 + (m ?? 0)
}

const firedSet = (date: string) => {
  try {
    return new Set<string>(JSON.parse(localStorage.getItem(FIRED_PREFIX + date) ?? '[]'))
  } catch {
    return new Set<string>()
  }
}

const markFired = (date: string, key: string) => {
  try {
    const set = firedSet(date).add(key)
    localStorage.setItem(FIRED_PREFIX + date, JSON.stringify([...set]))
    // 顺手清掉以前的
    Object.keys(localStorage)
      .filter((k) => k.startsWith(FIRED_PREFIX) && k !== FIRED_PREFIX + date)
      .forEach((k) => localStorage.removeItem(k))
  } catch {
    // 记不住最多重复提醒一次
  }
}

export const notificationState = (): NotificationPermission | 'unsupported' =>
  typeof Notification === 'undefined' ? 'unsupported' : Notification.permission

export const sendNotification = (title: string, body: string, onClick?: () => void) => {
  if (notificationState() !== 'granted') return false
  try {
    const n = new Notification(title, { body, tag: `${title}:${body}` })
    n.onclick = () => {
      window.focus()
      onClick?.()
      n.close()
    }
    return true
  } catch {
    return false
  }
}

export const useReminders = () => {
  const router = useRouter()
  const auth = useAuthStore()
  const settings = useSettingsStore()
  let tasks: Task[] = []
  let habits: Habit[] = []
  let logs: HabitLog[] = []
  let loadedAt = 0
  let timer: ReturnType<typeof setInterval> | undefined

  const refresh = async () => {
    const today = todayYmd()
    const wantTasks = settings.data.remind.tasks && settings.isEnabled('tasks')
    const wantHabits = settings.data.remind.habits && settings.isEnabled('habits')
    const [t, h, l] = await Promise.allSettled([
      wantTasks ? fetchTasks({ view: 'today' }) : Promise.resolve([]),
      wantHabits ? fetchHabits() : Promise.resolve([]),
      wantHabits ? fetchHabitLogs({ from: today, to: today }) : Promise.resolve([]),
    ])
    tasks = t.status === 'fulfilled' ? t.value : []
    habits = h.status === 'fulfilled' ? h.value : []
    logs = l.status === 'fulfilled' ? l.value : []
    loadedAt = Date.now()
  }

  const fire = (date: string, key: string, title: string, body: string, to: string) => {
    markFired(date, key)
    const go = () => router.push(to)
    const sent = sendNotification(title, body, go)
    if (!sent || !document.hidden) toast.info(`${title}：${body}`)
  }

  const check = async () => {
    if (!auth.isLoggedIn) return
    // 数据 5 分钟刷新一次；页面里改了任务也能在下一轮赶上
    if (Date.now() - loadedAt > 5 * 60_000) await refresh()
    const today = todayYmd()
    const now = new Date()
    const nowMin = now.getHours() * 60 + now.getMinutes()
    const fired = firedSet(today)
    const due = (at: number) => nowMin >= at && nowMin - at <= GRACE_MIN

    if (settings.data.remind.tasks) {
      for (const t of tasks) {
        if (t.done || t.dueDate !== today || !t.dueTime || t.remindBefore === null) continue
        const key = `task:${t.id}:${t.dueTime}`
        if (fired.has(key) || !due(minutesOf(t.dueTime) - t.remindBefore)) continue
        fire(today, key, t.remindBefore ? `${t.remindBefore} 分钟后` : '到点了', `${t.dueTime} ${t.title}`, `/tasks?v=today&task=${t.id}`)
      }
    }
    if (settings.data.remind.habits) {
      for (const h of habits) {
        if (h.archived || !isScheduled(h, today)) continue
        if (isDone(h, logs.find((l) => String(l.habitId) === String(h.id)))) continue
        for (const time of h.reminders) {
          const key = `habit:${h.id}:${time}`
          if (fired.has(key) || !due(minutesOf(time))) continue
          fire(today, key, '习惯提醒', h.name, '/habits')
        }
      }
    }
  }

  // 换页时（多半刚改过任务或打过卡）下一轮检查前重新取
  watch(
    () => router.currentRoute.value.path,
    () => (loadedAt = 0),
  )

  onMounted(() => {
    // 登录后先等页面自己的请求走完，再开始
    setTimeout(check, 4000)
    timer = setInterval(check, 30_000)
  })
  onBeforeUnmount(() => clearInterval(timer))
}
