import { onBeforeUnmount, ref } from 'vue'
import { errorText, toast } from './useToast'
import type { EntityId } from '../types/space'

/**
 * 「删除后 5 秒内可撤销」：先从界面隐藏，到时才真正调接口；离开页面时立即提交。
 * 页面用 isHidden 过滤列表即可。
 */
export const useDeferredDelete = (options: {
  remove: (id: EntityId) => Promise<unknown>
  /** 真正删掉之后（刷新列表、统计等） */
  onCommitted?: () => void
  delay?: number
}) => {
  const hidden = ref(new Set<string>())
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  const unhide = (id: string) => {
    const next = new Set(hidden.value)
    next.delete(id)
    hidden.value = next
  }

  const commit = async (id: string) => {
    timers.delete(id)
    try {
      await options.remove(id)
      options.onCommitted?.()
    } catch (error) {
      unhide(id)
      toast.error(errorText(error, '删除失败'))
    }
  }

  const schedule = (id: EntityId, message: string) => {
    const key = String(id)
    if (timers.has(key)) return
    hidden.value = new Set(hidden.value).add(key)
    const ms = options.delay ?? 5000
    timers.set(
      key,
      setTimeout(() => commit(key), ms),
    )
    toast.ok(message, {
      duration: ms,
      action: {
        label: '撤销',
        run: () => {
          clearTimeout(timers.get(key))
          timers.delete(key)
          unhide(key)
        },
      },
    })
  }

  const flush = () => {
    for (const [id, timer] of timers) {
      clearTimeout(timer)
      options.remove(id).catch(() => undefined)
    }
    timers.clear()
  }

  const isHidden = (id: EntityId) => hidden.value.has(String(id))

  window.addEventListener('pagehide', flush)
  onBeforeUnmount(() => {
    flush()
    window.removeEventListener('pagehide', flush)
  })

  return { hidden, isHidden, schedule, flush }
}
