<script setup lang="ts">
/**
 * 阅读模式：去掉原站导航与广告的存档正文，衬线字、行宽约 38 字，顶部细进度条；离开时记住位置。
 * 选中文字弹出工具条：两种划线（黄 = 观点，蓝 = 要查证 / 待办）、批注、存为随手记、转为任务。
 * 右栏按出现顺序列出本篇划线；读完点「读完了」可写一句读后感。?hl=<划线 id> 打开时滚到那条划线。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import BaseDialog from '../../components/base/BaseDialog.vue'
import { createHighlight, deleteHighlight, fetchHighlights, fetchReadingItem, updateHighlight, updateReadingItem } from '../../api/reading'
import { createNote } from '../../api/notes'
import { createTask } from '../../api/tasks'
import { favColor } from '../../api/search'
import { errorText, toast } from '../../composables/useToast'
import { monthDay, nowStamp, ymdOf } from '../../utils/date'
import { recordRecent } from '../../utils/recent'
import { HIGHLIGHT_COLORS, type Highlight, type HighlightColor, type ReadingItem } from '../../types/reading'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id)

const item = ref<ReadingItem | null>(null)
const highlights = ref<Highlight[]>([])
const loading = ref(true)
const loadError = ref('')
const articleEl = ref<HTMLElement | null>(null)

const sorted = computed(() => [...highlights.value].sort((a, b) => a.para - b.para || a.start - b.start))

/** 每段拆成「普通文字 / 划线」片段 */
const segments = (para: number, text: string) => {
  const marks = sorted.value.filter((h) => h.para === para)
  const out: { text: string; hl?: Highlight }[] = []
  let at = 0
  for (const h of marks) {
    if (h.start < at) continue
    if (h.start > at) out.push({ text: text.slice(at, h.start) })
    out.push({ text: text.slice(h.start, h.end), hl: h })
    at = h.end
  }
  if (at < text.length) out.push({ text: text.slice(at) })
  return out
}

// ── 进度 ──

const ratio = ref(0)
let saveTimer: ReturnType<typeof setTimeout> | undefined
let dirty = false

const measure = () => {
  const el = articleEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const total = rect.height
  // 看到的最下沿走到了文章的哪里
  const seen = window.innerHeight - rect.top
  ratio.value = Math.max(0, Math.min(1, seen / total))
}

const saveProgress = async () => {
  clearTimeout(saveTimer)
  if (!item.value || !dirty || !articleEl.value) return
  dirty = false
  const rect = articleEl.value.getBoundingClientRect()
  const position = Math.max(0, Math.min(1, -rect.top / rect.height))
  const progress = Math.max(item.value.progress, ratio.value > 0.97 ? 1 : ratio.value)
  const patch = {
    position,
    progress,
    lastReadTime: nowStamp(),
    ...(item.value.status === 'unread' && progress > 0.02 ? { status: 'reading' as const } : {}),
  }
  item.value = { ...item.value, ...patch }
  try {
    await updateReadingItem(item.value.id, patch)
  } catch {
    // 进度存不上不打扰阅读
  }
}

const onScroll = () => {
  measure()
  dirty = true
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveProgress, 1500)
  hideBar()
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [it, hls] = await Promise.all([fetchReadingItem(id), fetchHighlights({ itemId: id })])
    item.value = it
    highlights.value = hls
    recordRecent({ kind: 'reading', id, title: it.title, sub: it.domain, to: `/reading/${id}`, url: it.url })
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
  await nextTick()
  const target = typeof route.query.hl === 'string' ? route.query.hl : null
  if (target) {
    flash(target)
  } else if (item.value && articleEl.value && item.value.position > 0.01 && item.value.status !== 'done') {
    const rect = articleEl.value.getBoundingClientRect()
    window.scrollTo({ top: window.scrollY + rect.top + rect.height * item.value.position, behavior: 'instant' as ScrollBehavior })
  }
  measure()
}

// ── 选中与工具条 ──

interface Bar {
  x: number
  y: number
  /** 新划线的位置，或者点中的已有划线 */
  draft?: { para: number; start: number; end: number; text: string }
  hl?: Highlight
}
const bar = ref<Bar | null>(null)
const hideBar = () => (bar.value = null)

const offsetIn = (p: HTMLElement, node: Node, offset: number) => {
  const r = document.createRange()
  r.selectNodeContents(p)
  r.setEnd(node, offset)
  return r.toString().length
}

const placeBar = (rect: DOMRect) => {
  const box = articleEl.value!.getBoundingClientRect()
  return { x: Math.max(8, Math.min(box.width - 8, rect.left + rect.width / 2 - box.left)), y: rect.top - box.top - 8 }
}

const onMouseUp = () => {
  // 等浏览器把选区定下来
  setTimeout(() => {
    const sel = window.getSelection()
    if (!sel || sel.isCollapsed || !sel.rangeCount) return
    const range = sel.getRangeAt(0)
    const startP = (range.startContainer.parentElement ?? (range.startContainer as HTMLElement)).closest<HTMLElement>('[data-para]')
    if (!startP || !articleEl.value?.contains(startP)) return
    const para = Number(startP.dataset.para)
    const full = startP.textContent ?? ''
    let start = offsetIn(startP, range.startContainer, range.startOffset)
    let end = startP.contains(range.endContainer) ? offsetIn(startP, range.endContainer, range.endOffset) : full.length
    // 去掉两头的空白
    while (start < end && /\s/.test(full[start]!)) start += 1
    while (end > start && /\s/.test(full[end - 1]!)) end -= 1
    if (end - start < 2) return
    if (highlights.value.some((h) => h.para === para && h.start < end && start < h.end)) {
      toast.info('和已有划线重叠了：点那条划线可以改颜色或删掉')
      return
    }
    bar.value = { ...placeBar(range.getBoundingClientRect()), draft: { para, start, end, text: full.slice(start, end) } }
  })
}

const openHl = (h: Highlight, event: MouseEvent) => {
  event.stopPropagation()
  bar.value = { ...placeBar((event.currentTarget as HTMLElement).getBoundingClientRect()), hl: h }
}

const replace = (h: Highlight) => {
  highlights.value = highlights.value.map((x) => (x.id === h.id ? h : x))
}

/** 选区变成划线（点已有划线时直接返回那条） */
const commit = async (color: HighlightColor): Promise<Highlight | null> => {
  const b = bar.value
  if (!b || !item.value) return null
  if (b.hl) {
    if (b.hl.color !== color) {
      const h = await updateHighlight(b.hl.id, { color })
      replace(h)
      return h
    }
    return b.hl
  }
  try {
    const h = await createHighlight({ itemId: item.value.id, para: b.draft!.para, start: b.draft!.start, end: b.draft!.end, text: b.draft!.text, color, note: '' })
    highlights.value = [...highlights.value, h]
    window.getSelection()?.removeAllRanges()
    return h
  } catch (error) {
    toast.error(errorText(error, '划线失败'))
    return null
  }
}

const mark = async (color: HighlightColor) => {
  await commit(color)
  hideBar()
}

// ── 批注 ──

const editing = ref<string | null>(null)
const noteDraft = ref('')
const annotate = async () => {
  const h = await commit(bar.value?.hl?.color ?? 'yellow')
  hideBar()
  if (!h) return
  editing.value = String(h.id)
  noteDraft.value = h.note
  await nextTick()
  document.querySelector<HTMLTextAreaElement>(`[data-note="${h.id}"]`)?.focus()
}
const startEdit = (h: Highlight) => {
  editing.value = String(h.id)
  noteDraft.value = h.note
}
const saveNote = async (h: Highlight) => {
  if (editing.value !== String(h.id)) return
  editing.value = null
  if (noteDraft.value.trim() === h.note) return
  try {
    replace(await updateHighlight(h.id, { note: noteDraft.value.trim() }))
  } catch (error) {
    toast.error(errorText(error, '批注没存上'))
  }
}

// ── 转出 ──

const toNote = async () => {
  const h = await commit(bar.value?.hl?.color ?? 'yellow')
  hideBar()
  if (!h || !item.value) return
  if (h.noteId) {
    router.push(`/notes/${h.noteId}`)
    return
  }
  try {
    const note = await createNote({
      content: `> ${h.text}\n\n${h.note ? `${h.note}\n\n` : ''}—— [${item.value.title}](${item.value.url})`,
      tags: ['摘录'],
    })
    replace(await updateHighlight(h.id, { noteId: note.id }))
    toast.ok('已存为随手记（#摘录）', { action: { label: '打开', run: () => router.push(`/notes/${note.id}`) } })
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const taskFor = ref<Highlight | null>(null)
const taskTitle = ref('')
const toTask = async () => {
  const h = await commit(bar.value?.hl?.color ?? 'blue')
  hideBar()
  if (!h) return
  taskFor.value = h
  taskTitle.value = h.taskTitle ?? `查证：${h.text.length > 30 ? `${h.text.slice(0, 30)}…` : h.text}`
}
const saveTask = async () => {
  const h = taskFor.value
  if (!h || !item.value || !taskTitle.value.trim()) return
  try {
    const task = await createTask({ title: taskTitle.value.trim(), note: `「${h.text}」`, source: { type: 'reading', id: item.value.id, label: item.value.title } })
    replace(await updateHighlight(h.id, { taskId: task.id, taskTitle: task.title }))
    taskFor.value = null
    toast.ok(`已转为任务「${task.title}」，在收件箱里`)
  } catch (error) {
    toast.error(errorText(error, '创建任务失败'))
  }
}

const removeHl = async (h: Highlight) => {
  hideBar()
  highlights.value = highlights.value.filter((x) => x.id !== h.id)
  try {
    await deleteHighlight(h.id)
    toast.ok('已删除划线', {
      action: {
        label: '撤销',
        run: async () => {
          const { id: _id, createTime: _t, noteId: _n, taskId: _k, taskTitle: _tt, ...rest } = h
          highlights.value = [...highlights.value, await createHighlight(rest)]
        },
      },
    })
  } catch (error) {
    highlights.value = [...highlights.value, h]
    toast.error(errorText(error, '删除失败'))
  }
}

/** 右栏点一条：滚到正文里那处并闪一下 */
const flashing = ref<string | null>(null)
const flash = (hid: string | number) => {
  const el = articleEl.value?.querySelector<HTMLElement>(`[data-hl="${hid}"]`)
  if (!el) return
  el.scrollIntoView({ block: 'center', behavior: 'smooth' })
  flashing.value = String(hid)
  setTimeout(() => (flashing.value = null), 1600)
}

// ── 读完 ──

const doneOpen = ref(false)
const thought = ref('')
const finish = async () => {
  if (!item.value) return
  try {
    await updateReadingItem(item.value.id, { status: 'done', progress: 1, doneTime: nowStamp(), thought: thought.value.trim() })
    doneOpen.value = false
    dirty = false
    toast.ok(`读完「${item.value.title}」`)
    router.push({ path: '/reading', query: { tab: 'done' } })
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const later = async () => {
  dirty = true
  await saveProgress()
  toast.ok('记住位置了，下次从这里接着读')
  router.push('/reading')
}

const markReadWithoutArchive = async () => {
  thought.value = ''
  doneOpen.value = true
}

const onDocClick = (event: MouseEvent) => {
  if (bar.value && !(event.target as HTMLElement).closest('.pop, mark')) {
    if (!window.getSelection()?.isCollapsed) return
    hideBar()
  }
}
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && bar.value) hideBar()
}

onBeforeRouteLeave(() => {
  saveProgress()
})

onMounted(() => {
  load()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('mousedown', onDocClick)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('pagehide', saveProgress)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('mousedown', onDocClick)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pagehide', saveProgress)
})
</script>

<template>
  <div class="reader-page">
    <div class="rtop" aria-hidden="true"><i :style="{ width: `${Math.round(ratio * 100)}%` }" /></div>

    <div class="page">
      <router-link class="btn btn--quiet back" to="/reading"><Icon icon="lucide:arrow-left" />稍后读</router-link>

      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

      <div v-else-if="item" class="reader">
        <article class="doc">
          <div class="doc__src">
            <span class="fav" :style="{ background: favColor(item.domain) }">{{ item.domain.charAt(0).toUpperCase() }}</span>
            {{ item.domain }}<template v-if="item.content"> · 已存档 {{ monthDay(ymdOf(item.addTime)) }}</template>
            <a :href="item.url" target="_blank" rel="noopener noreferrer">打开原文 ↗</a>
          </div>
          <h1>{{ item.title }}</h1>
          <div class="doc__meta">
            <span v-if="item.minutes">约 {{ item.minutes }} 分钟</span>
            <span>已读 {{ Math.round(Math.max(item.progress, ratio) * 100) }}%</span>
            <span>{{ highlights.length }} 处划线</span>
            <span v-if="item.status === 'done'" class="ok"><Icon icon="lucide:check" />读完</span>
          </div>

          <div v-if="!item.content" class="noarc">
            <Icon icon="lucide:file-question" />
            <div>
              <b>还没有阅读版</b>
              <p>抓取并清洗正文需要后端（Readability 类算法，存档到 MinIO），接通前只能打开原文阅读；读完回来标记一下。</p>
              <div class="noarc__acts">
                <a class="btn btn--primary" :href="item.url" target="_blank" rel="noopener noreferrer"><Icon icon="lucide:external-link" />打开原文</a>
                <button v-if="item.status !== 'done'" class="btn btn--ghost" type="button" @click="markReadWithoutArchive">标记读完</button>
              </div>
            </div>
          </div>

          <div v-else ref="articleEl" class="doc__body" @mouseup="onMouseUp">
            <p v-for="(para, i) in item.content" :key="i" :data-para="i">
              <template v-for="(seg, j) in segments(i, para)" :key="j">
                <mark
                  v-if="seg.hl"
                  :data-hl="seg.hl.id"
                  :class="[`hl--${seg.hl.color}`, { flash: flashing === String(seg.hl.id), noted: seg.hl.note }]"
                  :title="seg.hl.note || HIGHLIGHT_COLORS[seg.hl.color].label"
                  @mouseup.stop
                  @click="openHl(seg.hl, $event)"
                >{{ seg.text }}</mark>
                <template v-else>{{ seg.text }}</template>
              </template>
            </p>

            <div v-if="bar" class="pop surface" role="toolbar" aria-label="划线" :style="{ left: `${bar.x}px`, top: `${bar.y}px` }" @mousedown.prevent>
              <button v-for="(c, key) in HIGHLIGHT_COLORS" :key="key" type="button" :class="{ on: bar.hl?.color === key }" :title="c.hint" @click="mark(key)">
                <i :class="`sw sw--${key}`" />{{ c.label }}
              </button>
              <span class="sep" />
              <button type="button" @click="annotate">批注</button>
              <button type="button" @click="toNote">{{ bar.hl?.noteId ? '打开随手记' : '存为随手记' }}</button>
              <button type="button" :disabled="Boolean(bar.hl?.taskId)" @click="toTask">{{ bar.hl?.taskId ? '已转任务' : '转任务' }}</button>
              <template v-if="bar.hl">
                <span class="sep" />
                <button type="button" class="danger" aria-label="删除划线" @click="removeHl(bar.hl)"><Icon icon="lucide:trash-2" /></button>
              </template>
            </div>
          </div>

          <p v-if="item.content" class="doc__end">— 全文完 —</p>
        </article>

        <aside class="rside">
          <h2>本篇划线 <span>{{ highlights.length }}</span></h2>
          <p v-if="!highlights.length" class="rside__empty">选中正文里的文字就能划线。黄色记观点，蓝色记要查证或要做的事。</p>
          <div v-for="h in sorted" :key="h.id" class="hcard" :class="`hcard--${h.color}`">
            <button type="button" class="hcard__text" @click="flash(h.id)">{{ h.text }}</button>
            <textarea
              v-if="editing === String(h.id)"
              v-model="noteDraft"
              :data-note="h.id"
              rows="2"
              placeholder="写批注…（Ctrl+Enter 保存）"
              aria-label="批注"
              @blur="saveNote(h)"
              @keydown.ctrl.enter="saveNote(h)"
              @keydown.meta.enter="saveNote(h)"
            />
            <button v-else-if="h.note" type="button" class="hcard__note" @click="startEdit(h)"><b>批注：</b>{{ h.note }}</button>
            <button v-else type="button" class="hcard__add" @click="startEdit(h)">+ 批注</button>
            <p v-if="h.taskTitle" class="hcard__link"><Icon icon="lucide:square-check-big" />已转为任务「{{ h.taskTitle }}」</p>
            <router-link v-if="h.noteId" class="hcard__link" :to="`/notes/${h.noteId}`"><Icon icon="lucide:pencil-line" />已存为随手记 →</router-link>
          </div>

          <div class="rdone">
            <button v-if="item.status !== 'done'" class="btn btn--primary" type="button" @click="doneOpen = true">读完了</button>
            <p v-else class="rdone__ok"><Icon icon="lucide:check" />{{ item.thought ? `“${item.thought}”` : '已读完' }}</p>
            <button v-if="item.status !== 'done' && item.content" class="btn btn--quiet" type="button" @click="later">稍后接着读（记住位置）</button>
          </div>
        </aside>
      </div>
    </div>

    <BaseDialog :open="doneOpen" title="读完了" width="28rem" @close="doneOpen = false">
      <div class="form">
        <label class="field">
          <span class="field__label">一句读后感（可以不写）</span>
          <textarea v-model="thought" class="field__input field__input--area" rows="3" placeholder="这篇最有用的一点是…" @keydown.ctrl.enter="finish" />
        </label>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="doneOpen = false">取消</button>
        <button class="btn btn--primary" type="button" @click="finish">移到「读完」</button>
      </template>
    </BaseDialog>

    <BaseDialog :open="Boolean(taskFor)" title="转为任务" width="28rem" @close="taskFor = null">
      <div class="form">
        <label class="field">
          <span class="field__label">任务</span>
          <input v-model="taskTitle" class="field__input" type="text" @keydown.enter="saveTask" />
        </label>
        <p class="hint">划线原文会写进任务备注，任务来源记为这篇文章，先放进收件箱。</p>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="taskFor = null">取消</button>
        <button class="btn btn--primary" type="button" :disabled="!taskTitle.trim()" @click="saveTask">创建任务</button>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.rtop {
  position: fixed;
  top: var(--header-height);
  left: 0;
  right: 0;
  z-index: 40;
  height: 3px;
  background: transparent;
}

.rtop i {
  display: block;
  height: 100%;
  background: var(--color-brand);
  transition: width 0.15s linear;
}

.back {
  margin-bottom: 0.6rem;
  padding: 0.35rem 0.6rem;
  font-size: 0.86rem;
}

.reader {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 19rem;
  gap: 2rem;
  align-items: start;
}

.doc {
  justify-self: center;
  width: 100%;
  max-width: 42rem;
  padding: 0.5rem 0 3rem;
}

.doc__src {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.doc__src a {
  margin-left: auto;
  color: var(--color-brand);
}

.fav {
  display: grid;
  place-items: center;
  width: 1.3rem;
  height: 1.3rem;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.66rem;
  font-weight: 800;
}

.doc h1 {
  margin: 0.8rem 0 0.4rem;
  font-family: Georgia, 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 1.75rem;
  font-weight: 800;
  line-height: 1.35;
}

.doc__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 1rem;
  margin-bottom: 1.6rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.doc__meta .ok {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  color: var(--color-accent-text);
}

.doc__body {
  position: relative;
  font-family: Georgia, 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 1.08rem;
  line-height: 2;
}

/* 行宽约 38 字 */
.doc__body p {
  max-width: 38em;
  margin: 0 0 1.2em;
}

mark {
  padding: 0.05em 0;
  border-radius: 0.15em;
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}

.hl--yellow {
  background: color-mix(in srgb, #fde047 55%, transparent);
}

.hl--blue {
  background: color-mix(in srgb, #93c5fd 55%, transparent);
}

mark.noted {
  text-decoration: underline dotted color-mix(in srgb, var(--color-text-primary) 45%, transparent);
  text-underline-offset: 0.3em;
}

mark.flash {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-brand) 45%, transparent);
}

.doc__end {
  margin-top: 2rem;
  text-align: center;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.pop {
  position: absolute;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 0.1rem;
  padding: 0.25rem;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  font-family: var(--font-sans);
  font-size: 0.8rem;
  line-height: 1.4;
  white-space: nowrap;
  transform: translate(-50%, -100%);
}

.pop button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  cursor: pointer;
}

.pop button:hover,
.pop button.on {
  background: var(--color-bg-soft);
}

.pop button:disabled {
  opacity: 0.5;
  cursor: default;
}

.pop .danger:hover {
  color: var(--color-danger);
}

.pop .sep {
  width: 1px;
  height: 1rem;
  margin: 0 0.15rem;
  background: var(--color-border);
}

.sw {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 50%;
}

.sw--yellow {
  background: #fde047;
}

.sw--blue {
  background: #93c5fd;
}

.noarc {
  display: flex;
  gap: 0.9rem;
  padding: 1.1rem 1.2rem;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.noarc > svg {
  flex: none;
  width: 1.6rem;
  height: 1.6rem;
  color: var(--color-text-secondary);
}

.noarc p {
  margin: 0.3rem 0 0.8rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.noarc__acts {
  display: flex;
  gap: 0.5rem;
}

.rside {
  position: sticky;
  top: calc(var(--header-height) + 1rem);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-height: calc(100vh - var(--header-height) - 2rem);
  overflow-y: auto;
  padding: 0.2rem 0.2rem 1rem;
}

.rside h2 {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.rside h2 span {
  margin-left: 0.2rem;
}

.rside__empty {
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.hcard {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.6rem 0.7rem;
  border-left: 3px solid #eab308;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-bg-surface);
  font-size: 0.84rem;
  line-height: 1.6;
}

.hcard--blue {
  border-left-color: #3b82f6;
}

.hcard button {
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.hcard__text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.hcard__note {
  padding: 0.35rem 0.5rem !important;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft) !important;
  font-size: 0.8rem;
}

.hcard__add {
  font-size: 0.76rem;
  color: var(--color-text-secondary) !important;
}

.hcard__add:hover {
  color: var(--color-brand) !important;
}

.hcard textarea {
  width: 100%;
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  font-size: 0.8rem;
  resize: vertical;
}

.hcard__link {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

a.hcard__link:hover {
  color: var(--color-brand);
}

.rdone {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-top: 0.6rem;
  padding-top: 0.8rem;
  border-top: 1px solid var(--color-border);
}

.rdone .btn--quiet {
  font-size: 0.82rem;
}

.rdone__ok {
  display: flex;
  gap: 0.35rem;
  font-size: 0.84rem;
  color: var(--color-accent-text);
}

.hint {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

@media (max-width: 1000px) {
  .reader {
    grid-template-columns: 1fr;
  }

  .rside {
    position: static;
    max-height: none;
  }
}

@media (max-width: 560px) {
  .doc h1 {
    font-size: 1.4rem;
  }

  .doc__body {
    font-size: 1.02rem;
  }
}
</style>
