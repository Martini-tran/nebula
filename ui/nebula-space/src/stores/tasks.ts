import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchTaskLists, fetchTaskStats, type TaskStats } from '../api/tasks'
import type { EntityId } from '../types/space'
import type { TaskList } from '../types/tasks'

/** 任务清单与各视图计数：侧栏、快速添加（#清单）、详情里的清单下拉共用 */
export const useTaskStore = defineStore('tasks', () => {
  const lists = ref<TaskList[]>([])
  const stats = ref<TaskStats | null>(null)

  const reloadLists = async () => {
    lists.value = (await fetchTaskLists()) ?? []
  }

  const reloadStats = async () => {
    stats.value = await fetchTaskStats()
  }

  const reload = () => Promise.all([reloadLists(), reloadStats()])

  const findList = (id: EntityId | null | undefined) =>
    id === null || id === undefined ? undefined : lists.value.find((list) => String(list.id) === String(id))

  const findListByName = (name: string) => {
    const key = name.trim().toLowerCase()
    return lists.value.find((list) => list.name.toLowerCase() === key) ?? lists.value.find((list) => list.name.toLowerCase().startsWith(key))
  }

  return { lists, stats, reload, reloadLists, reloadStats, findList, findListByName }
})
