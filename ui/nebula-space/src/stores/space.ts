import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchFolderTree, fetchTags } from '../api/space'
import type { EntityId, Folder, SpaceTag } from '../types/space'

export interface FlatFolder {
  id: EntityId
  name: string
  depth: number
  /** 从顶层到自身的名称路径，如「后端与架构 / 微服务」 */
  path: string
  folder: Folder
}

const RECENT_KEY = 'nebula-space:recent-folders'

const readRecent = (): string[] => {
  try {
    const raw = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')
    return Array.isArray(raw) ? raw.map(String).slice(0, 3) : []
  } catch {
    return []
  }
}

/**
 * 目录树与标签：侧栏、书签表单、移动弹窗都要用，集中缓存一份，增删后调 reload 刷新。
 */
export const useSpaceStore = defineStore('space', () => {
  const folders = ref<Folder[]>([])
  const tags = ref<SpaceTag[]>([])
  const loading = ref(false)
  const error = ref('')
  /** 最近移入过的目录 id，移动弹窗顶部快捷选择 */
  const recentFolderIds = ref<string[]>(readRecent())

  const reload = async () => {
    loading.value = true
    error.value = ''
    try {
      const [folderTree, tagList] = await Promise.all([fetchFolderTree(), fetchTags()])
      folders.value = folderTree ?? []
      tags.value = tagList ?? []
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 先序遍历拍平的目录树 */
  const flat = computed<FlatFolder[]>(() => {
    const result: FlatFolder[] = []
    const walk = (nodes: Folder[], depth: number, prefix: string) => {
      for (const node of nodes) {
        const path = prefix ? `${prefix} / ${node.name}` : node.name
        result.push({ id: node.id, name: node.name, depth, path, folder: node })
        if (node.children?.length) walk(node.children, depth + 1, path)
      }
    }
    walk(folders.value, 0, '')
    return result
  })

  /** 给下拉框用；name 前按层级缩进 */
  const flatFolders = (): { id: EntityId; label: string }[] =>
    flat.value.map((item) => ({ id: item.id, label: `${'　'.repeat(item.depth)}${item.name}` }))

  const findFolder = (id: EntityId): Folder | undefined =>
    flat.value.find((item) => String(item.id) === String(id))?.folder

  /** 目录的完整路径；0 / 找不到返回「未分类」 */
  const folderPath = (id: EntityId | null | undefined): string => {
    if (id === null || id === undefined || String(id) === '0') return '未分类'
    return flat.value.find((item) => String(item.id) === String(id))?.path ?? '未分类'
  }

  /** 自身及全部后代目录 */
  const subtree = (id: EntityId): Folder[] => {
    const root = findFolder(id)
    if (!root) return []
    const result: Folder[] = []
    const walk = (node: Folder) => {
      result.push(node)
      node.children?.forEach(walk)
    }
    walk(root)
    return result
  }

  const findTag = (id: EntityId): SpaceTag | undefined => tags.value.find((tag) => String(tag.id) === String(id))

  const rememberFolder = (id: EntityId) => {
    const key = String(id)
    recentFolderIds.value = [key, ...recentFolderIds.value.filter((item) => item !== key)].slice(0, 3)
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(recentFolderIds.value))
    } catch {
      // 存不了就只在本次会话里记住
    }
  }

  return {
    folders,
    tags,
    loading,
    error,
    recentFolderIds,
    flat,
    reload,
    flatFolders,
    findFolder,
    folderPath,
    subtree,
    findTag,
    rememberFolder,
  }
})
