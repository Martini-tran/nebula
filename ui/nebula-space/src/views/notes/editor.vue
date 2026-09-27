<script setup lang="ts">
/**
 * 笔记编辑：左栏笔记列表、中间正文、右栏属性。
 * 正文是轻量 Markdown，行首输入 / 插入块；选中一段文字出现操作条（转为任务、存为书签、加粗、链接）。
 * 自动保存，没有保存按钮（Ctrl+S 立即保存）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { recordRecent } from '../../utils/recent'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { deleteNote, fetchNote, fetchNotes, updateNote } from '../../api/notes'
import { fetchTasks } from '../../api/tasks'
import { noteToTask } from '../../composables/useNoteTask'
import { saveLinkAsBookmark } from '../../composables/useSaveLink'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { caretCoords } from '../../utils/caret'
import { monthDay, hmOf, shortStamp, ymdOf } from '../../utils/date'
import { extractUrls, firstLine, plainText, renderMarkdown, shortUrl, toggleTodoLine } from '../../utils/markdown'
import { NOTE_COLORS, expireText } from './noteLife'
import type { Note, NoteColor } from '../../types/notes'
import type { Task } from '../../types/tasks'

const route = useRoute()
const router = useRouter()

const note = ref<Note | null>(null)
const content = ref('')
const loading = ref(true)
const loadError = ref('')
const saveState = ref<'saved' | 'dirty' | 'saving' | 'error'>('saved')
const mode = ref<'edit' | 'preview'>('edit')
const editor = ref<HTMLTextAreaElement | null>(null)

const others = ref<Note[]>([])
const related = ref<Task[]>([])

const load = async (id: string) => {
  loading.value = true
  loadError.value = ''
  try {
    const [current, list, tasks] = await Promise.all([
      fetchNote(id),
      fetchNotes({ view: 'all' }),
      fetchTasks({ view: 'all', sourceType: 'note', sourceId: id }),
    ])
    note.value = current
    content.value = current.content
    if (current.content.trim()) {
      recordRecent({ kind: 'note', id: String(current.id), title: firstLine(current.content), sub: current.archived ? '已归档' : current.pinned ? '长期' : '临时', to: `/notes/${current.id}` })
    }
    others.value = list
    related.value = tasks
    saveState.value = 'saved'
    mode.value = current.archived ? 'preview' : 'edit'
    await nextTick()
    if (!current.archived && !current.content) editor.value?.focus()
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

// ── 自动保存 ──

let saveTimer: ReturnType<typeof setTimeout> | undefined

const save = async () => {
  clearTimeout(saveTimer)
  const current = note.value
  if (!current || content.value === current.content) {
    if (saveState.value === 'dirty') saveState.value = 'saved'
    return
  }
  saveState.value = 'saving'
  const text = content.value
  try {
    const saved = await updateNote(current.id, { content: text })
    // 保存期间又改了的话，保留新内容，只更新元数据
    note.value = { ...saved, content: text }
    const index = others.value.findIndex((n) => n.id === saved.id)
    if (index >= 0) others.value[index] = saved
    saveState.value = content.value === text ? 'saved' : 'dirty'
    if (saveState.value === 'dirty') scheduleSave()
  } catch (error) {
    saveState.value = 'error'
    toast.error(errorText(error, '保存失败'))
  }
}

const scheduleSave = () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(save, 700)
}

watch(content, (value) => {
  if (!note.value || value === note.value.content) return
  saveState.value = 'dirty'
  scheduleSave()
})

const saveText = computed(() => {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'dirty') return '未保存'
  if (saveState.value === 'error') return '保存失败，稍后自动重试'
  return '已保存'
})

// ── 属性 ──

const patch = async (body: Parameters<typeof updateNote>[1], message?: string) => {
  if (!note.value) return
  await save()
  try {
    note.value = { ...(await updateNote(note.value.id, body)), content: content.value }
    if (message) toast.ok(message)
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const setColor = (color: NoteColor) => patch({ color })
const togglePin = () => patch({ pinned: !note.value?.pinned }, note.value?.pinned ? '已变回临时笔记' : '已转为长期笔记')

const tagDraft = ref('')
const addTag = () => {
  const tag = tagDraft.value.trim().replace(/^#/, '')
  tagDraft.value = ''
  if (!tag || !note.value || note.value.tags.includes(tag)) return
  patch({ tags: [...note.value.tags, tag] }, note.value.tags.length ? undefined : '加了标签，已自动转为长期笔记')
}
const removeTag = (tag: string) => note.value && patch({ tags: note.value.tags.filter((t) => t !== tag) })

const archive = async () => {
  if (!note.value) return
  const archived = note.value.archived
  await patch({ archived: !archived }, archived ? '已恢复为临时笔记' : '已归档')
  if (!archived) router.push({ name: 'notes' })
}

const remove = async () => {
  if (!note.value) return
  const ok = await confirm({
    title: '删除这条笔记？',
    message: `「${firstLine(content.value) || '空笔记'}」会被永久删除。只是不想看到的话，可以归档。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  clearTimeout(saveTimer)
  try {
    await deleteNote(note.value.id)
    note.value = null
    toast.ok('笔记已删除')
    router.push({ name: 'notes' })
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const links = computed(() => extractUrls(content.value))

const toTask = async (text?: string) => {
  if (!note.value) return
  await save()
  try {
    const task = await noteToTask(note.value, text)
    related.value = [task, ...related.value]
    if (!note.value.pinned) note.value = { ...note.value, pinned: true, expireDate: null }
    toast.ok(`已加入任务收件箱：「${task.title}」`)
  } catch (error) {
    toast.error(errorText(error, '转任务失败'))
  }
}

// ── 选中文字操作条 ──

const selection = ref({ start: 0, end: 0 })
const selectedText = computed(() => content.value.slice(selection.value.start, selection.value.end))
const selectedUrl = computed(() => (/^https?:\/\/\S+$/.test(selectedText.value.trim()) ? selectedText.value.trim() : ''))

const syncSelection = () => {
  const el = editor.value
  if (el) selection.value = { start: el.selectionStart, end: el.selectionEnd }
}

/** 用新文本替换选区，并把选区放到 [selStart, selEnd]（相对替换起点） */
const replaceSelection = async (text: string, selStart = text.length, selEnd = selStart) => {
  const { start, end } = selection.value
  content.value = content.value.slice(0, start) + text + content.value.slice(end)
  await nextTick()
  editor.value?.focus()
  editor.value?.setSelectionRange(start + selStart, start + selEnd)
  syncSelection()
}

const bold = () => replaceSelection(`**${selectedText.value}**`, 2, 2 + selectedText.value.length)
const linkify = () => {
  const text = selectedText.value
  replaceSelection(`[${text}](https://)`, text.length + 3, text.length + 11)
}

// ── 斜杠菜单 ──

const SLASH_ITEMS = [
  { key: 'h', label: '标题', icon: 'lucide:heading', insert: '## ' },
  { key: 'todo', label: '待办', icon: 'lucide:square-check', insert: '- [ ] ' },
  { key: 'ul', label: '列表', icon: 'lucide:list', insert: '- ' },
  { key: 'ol', label: '编号列表', icon: 'lucide:list-ordered', insert: '1. ' },
  { key: 'quote', label: '引用', icon: 'lucide:quote', insert: '> ' },
  { key: 'code', label: '代码块', icon: 'lucide:code', insert: '```\n\n```', caret: 4 },
  { key: 'hr', label: '分割线', icon: 'lucide:minus', insert: '---\n' },
]

const slash = ref<{ at: number; top: number; left: number; index: number; filter: string } | null>(null)
const slashItems = computed(() => {
  const filter = slash.value?.filter ?? ''
  return SLASH_ITEMS.filter((item) => !filter || item.label.includes(filter) || item.key.startsWith(filter))
})

const onInput = () => {
  const el = editor.value
  if (!el) return
  syncSelection()
  const pos = el.selectionStart
  const lineStart = content.value.lastIndexOf('\n', pos - 1) + 1
  const typed = content.value.slice(lineStart, pos)
  // 行首的「/xxx」触发菜单
  if (/^\/[\p{L}\w]*$/u.test(typed)) {
    const { top, left, height } = caretCoords(el, lineStart)
    slash.value = { at: lineStart, top: top + height + 4, left, index: 0, filter: typed.slice(1) }
  } else {
    slash.value = null
  }
}

const applySlash = async (item: (typeof SLASH_ITEMS)[number]) => {
  const state = slash.value
  const el = editor.value
  if (!state || !el) return
  const end = el.selectionStart
  content.value = content.value.slice(0, state.at) + item.insert + content.value.slice(end)
  slash.value = null
  await nextTick()
  const caret = state.at + (item.caret ?? item.insert.length)
  el.focus()
  el.setSelectionRange(caret, caret)
}

/** 列表 / 待办里回车自动续上前缀；空项回车结束列表 */
const continueList = (event: KeyboardEvent) => {
  const el = editor.value
  if (!el || el.selectionStart !== el.selectionEnd) return false
  const pos = el.selectionStart
  const lineStart = content.value.lastIndexOf('\n', pos - 1) + 1
  const line = content.value.slice(lineStart, pos)
  const m = /^(\s*)([-*] \[[ xX]\] |[-*] |(\d+)[.)] )(.*)$/.exec(line)
  if (!m) return false
  event.preventDefault()
  if (!m[4]!.trim()) {
    content.value = content.value.slice(0, lineStart) + content.value.slice(pos)
    nextTick(() => el.setSelectionRange(lineStart, lineStart))
    return true
  }
  const prefix = m[3] ? `${Number(m[3]) + 1}. ` : m[2]!.replace(/\[[xX]\]/, '[ ]')
  const insert = `\n${m[1]}${prefix}`
  content.value = content.value.slice(0, pos) + insert + content.value.slice(pos)
  nextTick(() => el.setSelectionRange(pos + insert.length, pos + insert.length))
  return true
}

const onKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save()
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'b' && selectedText.value) {
    event.preventDefault()
    bold()
    return
  }
  if (slash.value && slashItems.value.length) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const n = slashItems.value.length
      slash.value.index = (slash.value.index + (event.key === 'ArrowDown' ? 1 : n - 1)) % n
      return
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      applySlash(slashItems.value[slash.value.index]!)
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      slash.value = null
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) continueList(event)
}

// ── 预览里勾待办 ──

const previewHtml = computed(() => renderMarkdown(content.value))
const onPreviewClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (target instanceof HTMLInputElement && target.dataset.line && !note.value?.archived) {
    content.value = toggleTodoLine(content.value, Number(target.dataset.line))
  }
}

// ── 左栏 ──

const listKeyword = ref('')
const listed = computed(() => {
  const kw = listKeyword.value.trim().toLowerCase()
  return others.value.filter((n) => !kw || n.content.toLowerCase().includes(kw))
})

// ── 路由 ──

onMounted(() => load(String(route.params.id)))
onBeforeRouteUpdate(async (to) => {
  await save()
  await load(String(to.params.id))
})
onBeforeRouteLeave(() => save())
const flush = () => {
  if (note.value && content.value !== note.value.content) updateNote(note.value.id, { content: content.value })
}
window.addEventListener('pagehide', flush)
onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  window.removeEventListener('pagehide', flush)
})
</script>

<template>
  <div class="ed page">
    <aside class="ed__list">
      <router-link :to="{ name: 'notes' }" class="ed__back"><Icon icon="lucide:arrow-left" />随手记</router-link>
      <label class="ed__search">
        <Icon icon="lucide:search" />
        <input v-model="listKeyword" type="search" placeholder="筛选笔记" aria-label="筛选笔记" />
      </label>
      <ul>
        <li v-for="n in listed" :key="n.id">
          <router-link :to="{ name: 'note-editor', params: { id: String(n.id) } }" class="ed__item" :class="{ 'ed__item--on': n.id === note?.id }">
            <b>{{ firstLine(n.id === note?.id ? content : n.content) || '（空笔记）' }}</b>
            <small>{{ plainText(n.id === note?.id ? content : n.content).slice(firstLine(n.content).length, firstLine(n.content).length + 40) || shortStamp(n.updateTime) }}</small>
          </router-link>
        </li>
      </ul>
    </aside>

    <StateBlock v-if="loading && !note" class="ed__state" state="loading" />
    <StateBlock v-else-if="loadError" class="ed__state" state="error" :description="loadError" action-label="回到随手记" @action="router.push({ name: 'notes' })" />

    <template v-else-if="note">
      <section class="ed__main surface" :class="`ed__main--${note.color}`">
        <header class="ed__bar">
          <router-link :to="{ name: 'notes' }" class="ed__back ed__back--inline" aria-label="返回随手记"><Icon icon="lucide:arrow-left" /></router-link>
          <span class="ed__meta">
            {{ monthDay(ymdOf(note.createTime)) }} {{ hmOf(note.createTime) }} 创建
            <span class="ed__save" :class="`ed__save--${saveState}`">
              <Icon :icon="saveState === 'saving' ? 'lucide:loader-circle' : saveState === 'saved' ? 'lucide:check' : 'lucide:circle-dot'" :class="{ spin: saveState === 'saving' }" />
              {{ saveText }}
            </span>
          </span>
          <div class="seg" role="radiogroup" aria-label="模式">
            <button type="button" role="radio" :aria-checked="mode === 'edit'" :class="{ on: mode === 'edit' }" :disabled="note.archived" @click="mode = 'edit'">编辑</button>
            <button type="button" role="radio" :aria-checked="mode === 'preview'" :class="{ on: mode === 'preview' }" @click="mode = 'preview'">预览</button>
          </div>
        </header>

        <div v-if="mode === 'edit' && selectedText.trim()" class="selbar" role="toolbar" aria-label="选中文字">
          <span>已选 {{ selectedText.trim().length }} 字</span>
          <button type="button" @mousedown.prevent @click="toTask(selectedText.trim())"><Icon icon="lucide:square-check-big" />转为任务</button>
          <button v-if="selectedUrl" type="button" @mousedown.prevent @click="saveLinkAsBookmark(selectedUrl)"><Icon icon="lucide:bookmark-plus" />存为书签</button>
          <button type="button" title="Ctrl+B" @mousedown.prevent @click="bold"><Icon icon="lucide:bold" />加粗</button>
          <button type="button" @mousedown.prevent @click="linkify"><Icon icon="lucide:link" />链接</button>
        </div>

        <div v-if="mode === 'edit'" class="ed__edit">
          <textarea
            ref="editor"
            v-model="content"
            class="ed__text"
            placeholder="第一行是标题。行首输入 / 插入标题、待办、代码块、分割线…"
            aria-label="笔记正文"
            spellcheck="false"
            @input="onInput"
            @keydown="onKeydown"
            @select="syncSelection"
            @click="syncSelection(); slash = null"
            @keyup="syncSelection"
            @blur="save(); slash = null"
          />
          <ul v-if="slash && slashItems.length" class="slash surface" role="listbox" :style="{ top: `${slash.top}px`, left: `${slash.left}px` }">
            <li
              v-for="(item, i) in slashItems"
              :key="item.key"
              role="option"
              :aria-selected="i === slash.index"
              :class="{ on: i === slash.index }"
              @mousedown.prevent="applySlash(item)"
            >
              <Icon :icon="item.icon" />{{ item.label }}
            </li>
          </ul>
        </div>
        <div v-else class="ed__preview md" @click="onPreviewClick" v-html="previewHtml || '<p class=&quot;md-gap&quot;></p>'" />
        <p v-if="mode === 'edit'" class="ed__tip"><kbd>/</kbd> 插入块 · 选中文字可转任务 · <kbd>Ctrl</kbd> <kbd>S</kbd> 立即保存</p>
      </section>

      <aside class="ed__props">
        <section>
          <h3>保留</h3>
          <p class="ed__life">
            <Icon :icon="note.archived ? 'lucide:archive' : note.pinned ? 'lucide:pin' : 'lucide:hourglass'" />
            {{ note.archived ? '已归档' : expireText(note) }}
          </p>
          <button v-if="!note.archived" class="btn btn--ghost" type="button" @click="togglePin">
            {{ note.pinned ? '变回临时笔记' : '转为长期笔记' }}
          </button>
        </section>

        <section>
          <h3>颜色</h3>
          <div class="swatches">
            <button
              v-for="c in NOTE_COLORS"
              :key="c.key"
              type="button"
              :class="[`sw--${c.key}`, { on: note.color === c.key }]"
              :aria-label="c.label"
              :aria-pressed="note.color === c.key"
              @click="setColor(c.key)"
            />
          </div>
        </section>

        <section>
          <h3>标签</h3>
          <div class="tags">
            <span v-for="tag in note.tags" :key="tag" class="tag">
              #{{ tag }}<button type="button" :aria-label="`去掉标签 ${tag}`" @click="removeTag(tag)"><Icon icon="lucide:x" /></button>
            </span>
            <input v-model="tagDraft" placeholder="+ 标签" aria-label="添加标签" maxlength="20" @keydown.enter.prevent="addTag" @blur="addTag" />
          </div>
        </section>

        <section>
          <h3>关联</h3>
          <ul class="rel">
            <li v-for="url in links" :key="url">
              <Icon icon="lucide:link" />
              <a :href="url" target="_blank" rel="noopener noreferrer">{{ shortUrl(url, 30) }}</a>
              <button type="button" title="存进书签" @click="saveLinkAsBookmark(url)"><Icon icon="lucide:bookmark-plus" /></button>
            </li>
            <li v-for="task in related" :key="task.id">
              <Icon :icon="task.done ? 'lucide:circle-check' : 'lucide:circle'" :class="{ done: task.done }" />
              <router-link :to="{ path: '/tasks', query: { v: 'all', task: String(task.id) } }">{{ task.title }}</router-link>
            </li>
            <li v-if="!links.length && !related.length" class="rel__empty">正文里的网址、由这条笔记生成的任务会出现在这里</li>
          </ul>
          <button v-if="!note.archived" class="btn btn--ghost" type="button" @click="toTask()"><Icon icon="lucide:square-check-big" />整条转为任务</button>
        </section>

        <section class="ed__danger">
          <button class="btn btn--quiet" type="button" @click="archive">
            <Icon :icon="note.archived ? 'lucide:archive-restore' : 'lucide:archive'" />{{ note.archived ? '恢复' : '归档' }}
          </button>
          <button class="btn btn--quiet danger" type="button" @click="remove"><Icon icon="lucide:trash-2" />删除</button>
        </section>
      </aside>
    </template>
  </div>
</template>

<style scoped>
.ed {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr) 15rem;
  gap: 1.25rem;
  align-items: start;
}

.ed__state {
  grid-column: 2 / 4;
}

.ed__list {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-height: calc(100vh - var(--header-height) - 2.5rem);
}

.ed__back {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.ed__back:hover {
  color: var(--color-brand);
}

.ed__back--inline {
  display: none;
}

.ed__search {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
}

.ed__search input {
  flex: 1;
  min-width: 0;
  padding: 0.4rem 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.86rem;
}

.ed__list ul {
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.ed__item {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.55rem 0.65rem;
  border-radius: var(--radius-md);
}

.ed__item:hover {
  background: var(--color-bg-soft);
}

.ed__item--on,
.ed__item--on:hover {
  background: var(--color-brand-soft);
}

.ed__item b,
.ed__item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ed__item b {
  font-size: 0.88rem;
  font-weight: 600;
}

.ed__item--on b {
  color: var(--color-brand);
}

.ed__item small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.ed__main {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: calc(100vh - var(--header-height) - 5rem);
  padding: 1rem 1.25rem 0.75rem;
  border-radius: var(--radius-lg);
}

.ed__main--yellow {
  background: var(--note-yellow);
}

.ed__main--green {
  background: var(--note-green);
}

.ed__main--blue {
  background: var(--note-blue);
}

.ed__main--pink {
  background: var(--note-pink);
}

.ed__main--purple {
  background: var(--note-purple);
}

.ed__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.ed__meta {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.ed__save {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.ed__save svg {
  width: 0.85rem;
  height: 0.85rem;
}

.ed__save--saved {
  color: var(--color-accent-text);
}

.ed__save--error {
  color: var(--color-danger);
}

.seg {
  display: inline-flex;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-text-primary) 6%, transparent);
}

.seg button {
  padding: 0.2rem 0.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.seg button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.selbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.2rem;
  align-self: flex-start;
  padding: 0.25rem 0.35rem 0.25rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  color: #f3f4f6;
  font-size: 0.8rem;
  box-shadow: var(--shadow-md);
}

.selbar span {
  margin-right: 0.35rem;
  color: #9ca3af;
}

.selbar button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.55rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: inherit;
  font-weight: 600;
  cursor: pointer;
}

.selbar button:hover {
  background: rgba(255, 255, 255, 0.12);
}

.ed__edit {
  position: relative;
  flex: 1;
  display: flex;
}

.ed__text {
  flex: 1;
  width: 100%;
  min-height: 24rem;
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.98rem;
  line-height: 1.8;
}

.ed__preview {
  flex: 1;
  min-height: 24rem;
  font-size: 0.98rem;
  line-height: 1.8;
}

.slash {
  position: absolute;
  z-index: 10;
  min-width: 11rem;
  margin: 0;
  padding: 0.3rem;
  list-style: none;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.slash li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  cursor: pointer;
}

.slash li.on,
.slash li:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.ed__tip {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.ed__props {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.ed__props h3 {
  margin-bottom: 0.45rem;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.ed__props .btn {
  padding: 0.35rem 0.75rem;
  font-size: 0.84rem;
}

.ed__life {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.5rem;
  font-size: 0.86rem;
}

.swatches {
  display: flex;
  gap: 0.4rem;
}

.swatches button {
  width: 1.6rem;
  height: 1.6rem;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  cursor: pointer;
}

.swatches button.on {
  box-shadow:
    0 0 0 2px var(--color-bg-canvas),
    0 0 0 4px var(--color-brand);
}

.sw--plain {
  background: var(--note-plain);
}

.sw--yellow {
  background: var(--note-yellow);
}

.sw--green {
  background: var(--note-green);
}

.sw--blue {
  background: var(--note-blue);
}

.sw--pink {
  background: var(--note-pink);
}

.sw--purple {
  background: var(--note-purple);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.tags .tag button {
  display: inline-grid;
  place-items: center;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}

.tags input {
  width: 5rem;
  padding: 0.15rem 0.4rem;
  border: 1px dashed var(--color-border);
  border-radius: 999px;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.8rem;
  outline: none;
}

.tags input:focus {
  border-color: var(--color-brand);
}

.rel {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0 0 0.6rem;
  padding: 0;
  list-style: none;
  font-size: 0.84rem;
}

.rel li {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.rel li > svg {
  flex: none;
  color: var(--color-text-secondary);
}

.rel li > svg.done {
  color: var(--color-accent);
}

.rel a {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rel a:hover {
  color: var(--color-brand);
}

.rel button {
  display: inline-grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.rel button:hover {
  color: var(--color-brand);
}

.rel__empty {
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.ed__danger {
  display: flex;
  gap: 0.3rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.ed__danger .danger:hover {
  color: var(--color-danger);
}

@media (max-width: 1180px) {
  .ed {
    grid-template-columns: minmax(0, 1fr) 14rem;
  }

  .ed__list {
    display: none;
  }

  .ed__back--inline {
    display: inline-flex;
  }

  .ed__state {
    grid-column: 1 / 3;
  }
}

@media (max-width: 760px) {
  .ed {
    grid-template-columns: 1fr;
  }

  .ed__props {
    position: static;
  }

  .ed__state {
    grid-column: auto;
  }
}
</style>
