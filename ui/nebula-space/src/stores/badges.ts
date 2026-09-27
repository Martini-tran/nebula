import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchNoteStats } from '../api/notes'
import { fetchTaskStats } from '../api/tasks'
import type { ModuleKey } from '../config/modules'

/**
 * 顶部导航的数字角标：任务 = 今天待办（含过期），随手记 = 临时笔记数。
 * 切换页面、快速记录保存后刷新；取不到就不显示，不打扰。
 */
export const useBadgeStore = defineStore('badges', () => {
  const counts = ref<Partial<Record<ModuleKey, number>>>({})
  let timer: ReturnType<typeof setTimeout> | undefined

  const load = async () => {
    const [tasks, notes] = await Promise.allSettled([fetchTaskStats(), fetchNoteStats()])
    counts.value = {
      tasks: tasks.status === 'fulfilled' ? tasks.value.today : undefined,
      notes: notes.status === 'fulfilled' ? notes.value.temporary : undefined,
    }
  }

  /** 合并短时间内的多次刷新 */
  const refresh = () => {
    clearTimeout(timer)
    timer = setTimeout(load, 300)
  }

  return { counts, refresh }
})
