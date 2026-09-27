<script setup lang="ts">
/**
 * 全局快速记录：任何页面 Ctrl+Shift+Space 呼出，一个输入框记四种东西——
 * 笔记（默认）、任务、书签（粘贴网址自动切换）、会议。Tab 切换。
 * 任务 / 会议模式边打字边识别时间、优先级、清单，识别结果以 chip 显示，点 × 撤销单项识别。
 * Enter 保存并继续（浮层留在原地可以连续记），Ctrl+Enter 保存并打开。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { quickCapture, type CaptureMode } from '../composables/useQuickCapture'
import { createNote } from '../api/notes'
import { createTask, createTaskList } from '../api/tasks'
import { createMeeting } from '../api/meetings'
import { createBookmark, findDuplicate, hostOf } from '../api/space'
import { useTaskStore } from '../stores/tasks'
import { useBadgeStore } from '../stores/badges'
import { errorText, toast } from '../composables/useToast'
import { parseTaskInput, type ParsedKind } from '../utils/taskParser'
import { relativeDay, weekdayLabel } from '../utils/date'
import { nextTagColor } from '../views/bookmarks/tagColors'
import { noteTtl, notesLongByDefault, useSettingsStore } from '../stores/settings'
import { PRIORITY_LABEL, type TaskPriority } from '../types/tasks'

const router = useRouter()
const taskStore = useTaskStore()
const badges = useBadgeStore()

const MODES: { key: CaptureMode; label: string; icon: string; placeholder: string }[] = [
  { key: 'note', label: '笔记', icon: 'lucide:pencil-line', placeholder: notesLongByDefault() ? '记点什么…存为长期笔记' : `记点什么…存为临时笔记，${noteTtl()} 天后自动归档` },
  { key: 'task', label: '任务', icon: 'lucide:square-check-big', placeholder: '下周一上午10点 和运维确认扩容方案 !1 #工作' },
  { key: 'bookmark', label: '书签', icon: 'lucide:bookmark', placeholder: '粘贴网址' },
  { key: 'meeting', label: '会议', icon: 'lucide:users', placeholder: '明天下午3点 书签导入方案评审' },
]

const text = ref('')
const bookmarkTitle = ref('')
const saving = ref(false)
const input = ref<HTMLTextAreaElement | null>(null)
/** 被用户点 × 撤销的识别类型 */
const dropped = ref(new Set<ParsedKind>())

const mode = computed({
  get: () => quickCapture.mode,
  set: (value) => (quickCapture.mode = value),
})
const current = computed(() => MODES.find((m) => m.key === mode.value)!)

watch(
  () => quickCapture.open,
  async (open) => {
    if (!open) return
    await nextTick()
    input.value?.focus()
    if (!taskStore.lists.length) taskStore.reloadLists().catch(() => undefined)
  },
)
watch(mode, () => nextTick(() => input.value?.focus()))
watch(text, () => {
  if (!text.value) dropped.value = new Set()
})

// ── 识别 ──

const parsed = computed(() => parseTaskInput(text.value))
const active = computed(() => parsed.value.spans.filter((s) => !dropped.value.has(s.kind) && (mode.value === 'task' || s.kind === 'date' || s.kind === 'time')))
const has = (kind: ParsedKind) => active.value.some((s) => s.kind === kind)

/** 标题 = 原文去掉仍然生效的识别片段 */
const title = computed(() => {
  let out = ''
  let cursor = 0
  for (const span of active.value) {
    out += `${text.value.slice(cursor, span.start)} `
    cursor = span.end
  }
  return (out + text.value.slice(cursor)).replace(/\s+/g, ' ').trim()
})

const chips = computed(() => {
  const p = parsed.value
  const list: { kind: ParsedKind; label: string; icon: string }[] = []
  if ((has('date') || has('time')) && p.dueDate) {
    list.push({
      kind: has('date') ? 'date' : 'time',
      label: `${relativeDay(p.dueDate)} ${weekdayLabel(p.dueDate)}${has('time') && p.dueTime ? ` ${p.dueTime}` : ''}`,
      icon: 'lucide:calendar',
    })
  }
  if (mode.value === 'task' && has('priority')) list.push({ kind: 'priority', label: `${PRIORITY_LABEL[p.priority as TaskPriority]}优先级`, icon: 'lucide:flag' })
  if (mode.value === 'task' && has('list') && p.listName) {
    const exists = taskStore.findListByName(p.listName)
    list.push({ kind: 'list', label: exists ? `# ${exists.name}` : `# 新清单「${p.listName}」`, icon: 'lucide:hash' })
  }
  return list
})

const drop = (kind: ParsedKind) => {
  const next = new Set(dropped.value).add(kind)
  // 日期和时间是一起显示的，撤销时一起撤
  if (kind === 'date' || kind === 'time') {
    next.add('date')
    next.add('time')
  }
  dropped.value = next
  input.value?.focus()
}

// ── 切换 ──

const cycle = (delta: number) => {
  const index = MODES.findIndex((m) => m.key === mode.value)
  mode.value = MODES[(index + delta + MODES.length) % MODES.length]!.key
}

const onPaste = (event: ClipboardEvent) => {
  const pasted = event.clipboardData?.getData('text')?.trim() ?? ''
  if (/^https?:\/\/\S+$/.test(pasted) && !text.value.trim()) mode.value = 'bookmark'
}

// ── 保存 ──

const save = async (openAfter: boolean) => {
  const value = text.value.trim()
  if (!value || saving.value) return
  saving.value = true
  try {
    let target: Parameters<typeof router.push>[0] | null = null
    if (mode.value === 'note') {
      const note = await createNote({ content: value })
      toast.ok('已记下，临时笔记')
      target = { name: 'note-editor', params: { id: String(note.id) } }
    } else if (mode.value === 'task') {
      if (!title.value) throw new Error('还缺任务内容')
      const p = parsed.value
      let listId = null as string | number | null
      if (has('list') && p.listName) {
        const exists = taskStore.findListByName(p.listName)
        listId = exists ? exists.id : (await createTaskList({ name: p.listName, color: nextTagColor(taskStore.lists.length) })).id
        if (!exists) await taskStore.reloadLists()
      }
      const task = await createTask({
        title: title.value,
        dueDate: has('date') || has('time') ? (p.dueDate ?? null) : null,
        dueTime: has('time') ? (p.dueTime ?? null) : null,
        priority: (has('priority') ? p.priority : 0) as TaskPriority,
        listId,
      })
      toast.ok(`已添加到${task.dueDate ? relativeDay(task.dueDate) : '收件箱'}`)
      target = { path: '/tasks', query: { v: 'all', task: String(task.id) } }
    } else if (mode.value === 'bookmark') {
      const url = /^https?:\/\//i.test(value) ? value : `https://${value}`
      const existing = await findDuplicate(url)
      if (existing) {
        toast.info(`已经收藏过：「${existing.title}」`)
      } else {
        await createBookmark({ url, title: bookmarkTitle.value.trim() || hostOf(url).replace(/^www\./, ''), folderId: 0 })
        toast.ok('已存进书签 · 未分类')
      }
      target = { path: '/bookmarks', query: { view: 'uncategorized' } }
    } else {
      if (!title.value) throw new Error('还缺会议主题')
      const p = parsed.value
      const now = new Date(Date.now() + 30 * 60_000)
      const meeting = await createMeeting({
        title: title.value,
        date: has('date') || has('time') ? (p.dueDate ?? relativeToday()) : relativeToday(),
        startTime: has('time') && p.dueTime ? p.dueTime : `${String(now.getHours()).padStart(2, '0')}:${now.getMinutes() < 30 ? '00' : '30'}`,
        durationMin: 30,
        attendees: [{ name: '我', me: true }],
      })
      toast.ok(`会议已创建：${relativeDay(meeting.date)} ${meeting.startTime}`)
      target = { name: 'meeting', params: { id: String(meeting.id) } }
    }
    text.value = ''
    bookmarkTitle.value = ''
    badges.refresh()
    if (openAfter && target) {
      quickCapture.hide()
      router.push(target)
    } else {
      input.value?.focus()
    }
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

const relativeToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Tab') {
    event.preventDefault()
    cycle(event.shiftKey ? -1 : 1)
  } else if (event.key === 'Enter' && !event.isComposing) {
    // 笔记模式 Shift+Enter 换行；其余模式都是单行
    if (event.shiftKey && mode.value === 'note') return
    event.preventDefault()
    save(event.ctrlKey || event.metaKey)
  } else if (event.key === 'Escape') {
    event.preventDefault()
    quickCapture.hide()
  }
}

const autoGrow = () => {
  const el = input.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 200)}px`
}
watch(text, () => nextTick(autoGrow))

/** 全局快捷键：Ctrl+Shift+Space（部分输入法占用了它时，也可以点页头的「+」） */
const onGlobalKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.shiftKey && (event.code === 'Space' || event.key === ' ')) {
    event.preventDefault()
    if (quickCapture.open) quickCapture.hide()
    else quickCapture.show(useSettingsStore().data.captureMode)
  }
}
onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>

<template>
  <Teleport to="body">
    <transition name="qc">
      <div v-if="quickCapture.open" class="qc-mask" @click.self="quickCapture.hide()">
        <div class="qc surface" role="dialog" aria-modal="true" aria-label="快速记录">
          <div class="qc__modes" role="tablist" aria-label="记录类型">
            <button
              v-for="m in MODES"
              :key="m.key"
              type="button"
              role="tab"
              :aria-selected="mode === m.key"
              :class="{ on: mode === m.key }"
              @click="mode = m.key"
            >
              <Icon :icon="m.icon" />{{ m.label }}
            </button>
            <span class="qc__hint"><kbd>Tab</kbd> 切换</span>
          </div>

          <textarea
            ref="input"
            v-model="text"
            class="qc__input"
            rows="1"
            :placeholder="current.placeholder"
            :aria-label="current.label"
            @keydown="onKeydown"
            @paste="onPaste"
          />
          <input
            v-if="mode === 'bookmark' && text.trim()"
            v-model="bookmarkTitle"
            class="qc__sub"
            :placeholder="`标题（留空用 ${hostOf(/^https?:/.test(text.trim()) ? text.trim() : `https://${text.trim()}`) || '域名'}）`"
            aria-label="书签标题"
            @keydown="onKeydown"
          />

          <div v-if="chips.length" class="qc__chips">
            <span v-for="c in chips" :key="c.kind" class="qc__chip">
              <Icon :icon="c.icon" />{{ c.label }}
              <button type="button" :aria-label="`撤销识别：${c.label}`" @click="drop(c.kind)"><Icon icon="lucide:x" /></button>
            </span>
          </div>

          <footer class="qc__foot">
            <span><kbd>Enter</kbd> 保存并继续 · <kbd>Ctrl</kbd> <kbd>Enter</kbd> 保存并打开<template v-if="mode === 'note'"> · <kbd>Shift</kbd> <kbd>Enter</kbd> 换行</template></span>
            <button class="btn btn--primary" type="button" :disabled="!text.trim() || saving" @click="save(false)">
              <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />添加{{ current.label }}
            </button>
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.qc-mask {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 14vh 1rem 1rem;
  background: rgba(8, 10, 16, 0.4);
  backdrop-filter: blur(2px);
}

.qc {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: min(40rem, 100%);
  padding: 0.9rem 1.1rem 0.85rem;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.qc__modes {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.qc__modes button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.75rem;
  border: 0;
  border-radius: 999px;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
}

.qc__modes button.on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.qc__hint {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.qc__input {
  width: 100%;
  min-height: 2.6rem;
  padding: 0.3rem 0.2rem;
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 1.1rem;
  line-height: 1.6;
}

.qc__sub {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  outline: none;
}

.qc__sub:focus {
  border-color: var(--color-brand);
}

.qc__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.qc__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.3rem 0.2rem 0.6rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.82rem;
  font-weight: 600;
}

.qc__chip button {
  display: inline-grid;
  place-items: center;
  width: 1.2rem;
  height: 1.2rem;
  border: 0;
  border-radius: 50%;
  background: none;
  color: inherit;
  cursor: pointer;
}

.qc__chip button:hover {
  background: color-mix(in srgb, var(--color-brand) 18%, transparent);
}

.qc__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.qc__foot .btn {
  padding: 0.4rem 0.9rem;
  font-size: 0.86rem;
}

.qc-enter-active,
.qc-leave-active {
  transition: opacity 0.16s ease;
}

.qc-enter-active .qc {
  transition: transform 0.2s var(--ease-soft);
}

.qc-enter-from,
.qc-leave-to {
  opacity: 0;
}

.qc-enter-from .qc {
  transform: translateY(-8px) scale(0.98);
}
</style>
