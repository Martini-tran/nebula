import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchFolderTree, fetchTags } from '../api/space'
import type { EntityId, Folder, SpaceTag } from '../types/space'

/**
 * 目录树与标签：侧栏、书签表单的下拉都要用，集中缓存一份，增删后调 reload 刷新。
 */
export const useSpaceStore = defineStore('space', () => {
  const folders = ref<Folder[]>([])
  const tags = ref<SpaceTag[]>([])
  const loading = ref(false)
  const error = ref('')

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

  /** 拍平目录树，给下拉框用；name 前按层级缩进 */
  const flatFolders = (): { id: EntityId; label: string }[] => {
    const result: { id: EntityId; label: string }[] = []
    const walk = (nodes: Folder[], depth: number) => {
      for (const node of nodes) {
        result.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
        if (node.children?.length) walk(node.children, depth + 1)
      }
    }
    walk(folders.value, 0)
    return result
  }

  const findFolder = (id: EntityId): Folder | undefined => {
    const stack = [...folders.value]
    while (stack.length) {
      const node = stack.pop()!
      if (String(node.id) === String(id)) return node
      if (node.children) stack.push(...node.children)
    }
    return undefined
  }

  return { folders, tags, loading, error, reload, flatFolders, findFolder }
})
