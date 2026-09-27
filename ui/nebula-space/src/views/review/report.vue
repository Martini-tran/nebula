<script setup lang="ts">
/**
 * 周报草稿：给别人看。左栏选纳入哪些来源（默认只选「工作」清单、会议决议与待办）和模板；
 * 右侧按模板从数据拼出草稿，每条带来源标签（点了跳回原任务或会议）。
 * 可以直接改，改过的淡黄底；重新生成时保留手改内容。复制为 Markdown 贴进周报系统或群聊；保存后下周回顾里能看到。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import ToggleSwitch from '../../components/base/ToggleSwitch.vue'
import EditableText from '../../components/base/EditableText.vue'
import { fetchWeeklyReport, fetchWeeklyReports, saveWeeklyReport } from '../../api/reviews'
import { useMyNames } from '../../composables/useMeetingSync'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { addDays, monthDay, startOfWeek, todayYmd, weekNumberOf, ymdOf } from '../../utils/date'
import { formatMinutes } from '../../utils/format'
import { recordRecent } from '../../utils/recent'
import { loadWeek, summarize, type WeekSource, type WeekSummary } from './weekData'
import { defaultSources, generate, isEdited, mergeDraft, reportTitle, TEMPLATES, toMarkdown } from './reportDraft'
import type { ReportItem, ReportRef, ReportSection, ReportSources, ReportTemplate, WeeklyReport } from '../../types/reviews'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()
const thisWeek = startOfWeek(todayYmd())

const week = computed(() => {
  const q = route.query.week
  const start = typeof q === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(q) ? startOfWeek(q) : thisWeek
  return start > thisWeek ? thisWeek : start
})

const src = ref<WeekSource | null>(null)
const sum = ref<WeekSummary | null>(null)
const lastSaved = ref<WeeklyReport | null>(null)
const saved = ref<WeeklyReport | null>(null)
const loading = ref(true)
const loadError = ref('')

const template = ref<ReportTemplate>('standard')
const sources = ref<ReportSources>({ lists: [], decisions: true, myActions: true, waiting: true, focus: false })
const sections = ref<ReportSection[]>([])
const removed = ref<string[]>([])
const dirty = ref(false)
const saving = ref(false)

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [data, current, all] = await Promise.all([loadWeek(week.value), fetchWeeklyReport(week.value), fetchWeeklyReports()])
    src.value = data
    sum.value = summarize(data, myNames)
    saved.value = current
    lastSaved.value = all.find((r) => r.week < week.value) ?? null
    if (current) {
      template.value = current.template
      sources.value = { ...defaultSources(data.lists), ...current.sources }
      sections.value = current.sections
      removed.value = current.removed
      recordRecent({ kind: 'report', id: current.week, title: `周报 · 第 ${weekNumberOf(current.week)} 周`, sub: `${monthDay(current.week)} – ${monthDay(addDays(current.week, 6))}`, to: `/review/report?week=${current.week}` })
    } else {
      template.value = 'standard'
      sources.value = defaultSources(data.lists)
      removed.value = []
      sections.value = generate(data, sum.value, sources.value, template.value)
    }
    dirty.value = false
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

watch(week, load)

const regenerate = () => {
  if (!src.value || !sum.value) return
  sections.value = mergeDraft(generate(src.value, sum.value, sources.value, template.value), sections.value, removed.value)
  dirty.value = true
}

// 换来源、换模板立即重新拼
const setTemplate = (key: ReportTemplate) => {
  template.value = key
  regenerate()
}
const setSource = (patch: Partial<ReportSources>) => {
  sources.value = { ...sources.value, ...patch }
  regenerate()
}
const toggleList = (id: string, on: boolean) => {
  const set = new Set(sources.value.lists)
  if (on) set.add(id)
  else set.delete(id)
  setSource({ lists: [...set] })
}

// ── 左栏的计数 ──

const listRows = computed(() => {
  if (!src.value || !sum.value) return []
  const done = sum.value.cur.done
  const rows = src.value.lists.map((l) => ({ id: String(l.id), name: l.name, color: l.color, n: done.filter((t) => String(t.listId) === String(l.id)).length }))
  const none = done.filter((t) => t.listId === null || t.listId === undefined).length
  return none ? [...rows, { id: 'none', name: '未分清单', color: '#94a3b8', n: none }] : rows
})

// ── 编辑 ──

const edit = (item: ReportItem, text: string) => {
  item.text = text
  dirty.value = true
}

const removeItem = (section: ReportSection, item: ReportItem) => {
  section.items = section.items.filter((i) => i !== item)
  if (item.auto !== null) removed.value = [...removed.value, item.key]
  dirty.value = true
}

const editors = ref<Record<string, InstanceType<typeof EditableText> | null>>({})
const addItem = async (section: ReportSection) => {
  const key = `manual:${Date.now().toString(36)}`
  section.items.push({ key, text: '', auto: null, ref: null })
  dirty.value = true
  await nextTick()
  editors.value[key]?.focus()
}

/** 编辑结束时把空的手动条目清掉 */
const tidy = (section: ReportSection, item: ReportItem) => {
  if (item.auto === null && !item.text.trim()) section.items = section.items.filter((i) => i !== item)
}

const restore = () => {
  removed.value = []
  regenerate()
}

const openRef = (ref: ReportRef) => {
  if (ref.type === 'task' && ref.id !== undefined) router.push({ path: '/tasks', query: { v: 'all', task: String(ref.id) } })
  else if (ref.type === 'meeting' && ref.id !== undefined) router.push(`/meetings/${ref.id}`)
  else if (ref.type === 'focus') router.push('/focus')
}
const refIcon = (ref: ReportRef) =>
  ref.type === 'meeting' ? 'lucide:users' : ref.type === 'focus' ? 'lucide:timer' : ref.label.startsWith('逾期') ? 'lucide:circle-alert' : 'lucide:square-check-big'

// ── 复制与保存 ──

const markdown = computed(() => toMarkdown(week.value, sections.value))
const copied = ref(false)
const copy = async () => {
  try {
    await navigator.clipboard.writeText(markdown.value)
  } catch {
    const area = document.createElement('textarea')
    area.value = markdown.value
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    area.remove()
  }
  copied.value = true
  setTimeout(() => (copied.value = false), 2000)
}

const save = async () => {
  saving.value = true
  try {
    saved.value = await saveWeeklyReport(week.value, {
      template: template.value,
      sources: sources.value,
      sections: sections.value.map((s) => ({ ...s, items: s.items.filter((i) => i.text.trim()) })),
      removed: removed.value,
      content: markdown.value,
    })
    dirty.value = false
    toast.ok('周报已保存，下周回顾里能看到这份计划')
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (!dirty.value) return true
  return confirm({ title: '周报还没保存', message: '离开后这次的修改会丢失。', confirmText: '不保存，离开', danger: true })
})

const editedCount = computed(() => sections.value.reduce((n, s) => n + s.items.filter(isEdited).length, 0))

onMounted(load)
</script>

<template>
  <div class="report page">
    <header class="rhead">
      <router-link class="btn btn--quiet back" :to="{ path: '/review', query: week === thisWeek ? {} : { week } }"><Icon icon="lucide:arrow-left" />周回顾</router-link>
      <h1 class="page-title">周报草稿</h1>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <div v-else-if="sum" class="wr surface">
      <aside class="src">
        <div>
          <h2>纳入</h2>
          <label v-for="l in listRows" :key="l.id" class="tg">
            <span><i class="dot" :style="{ background: l.color }" />{{ l.name }}清单<small>{{ l.n }}</small></span>
            <ToggleSwitch :model-value="sources.lists.includes(l.id)" :label="`纳入${l.name}清单`" @update:model-value="(on) => toggleList(l.id, on)" />
          </label>
          <label class="tg">
            <span>会议决议<small>{{ sum.decisions.length }}</small></span>
            <ToggleSwitch :model-value="sources.decisions" label="纳入会议决议" @update:model-value="(on) => setSource({ decisions: on })" />
          </label>
          <label class="tg">
            <span>我的会议待办<small>{{ sum.myActions.length }}</small></span>
            <ToggleSwitch :model-value="sources.myActions" label="纳入我的会议待办" @update:model-value="(on) => setSource({ myActions: on })" />
          </label>
          <label class="tg">
            <span>在等别人的<small>{{ sum.waiting.length }}</small></span>
            <ToggleSwitch :model-value="sources.waiting" label="纳入在等别人的待办" @update:model-value="(on) => setSource({ waiting: on })" />
          </label>
          <label class="tg">
            <span>专注时长<small>{{ formatMinutes(sum.cur.focusMin) }}</small></span>
            <ToggleSwitch :model-value="sources.focus" label="纳入专注时长" @update:model-value="(on) => setSource({ focus: on })" />
          </label>
        </div>
        <div>
          <h2>模板</h2>
          <div class="tpl" role="radiogroup" aria-label="模板">
            <button v-for="t in TEMPLATES" :key="t.key" type="button" role="radio" :aria-checked="template === t.key" :class="{ on: template === t.key }" @click="setTemplate(t.key)">
              {{ t.label }}<small>{{ t.desc }}</small>
            </button>
          </div>
        </div>
        <p class="src__meta">
          <template v-if="saved">本周已保存 · {{ monthDay(ymdOf(saved.updateTime)) }} {{ saved.updateTime.slice(11, 16) }}</template>
          <template v-else-if="lastSaved">
            上次保存：<router-link :to="{ query: { week: lastSaved.week } }">第 {{ weekNumberOf(lastSaved.week) }} 周</router-link> · {{ monthDay(ymdOf(lastSaved.updateTime)) }}
          </template>
          <template v-else>还没有保存过周报</template>
        </p>
      </aside>

      <article class="doc">
        <div class="doc__bar">
          <h2>{{ reportTitle(week) }}</h2>
          <transition name="fade"><span v-if="copied" class="copied"><Icon icon="lucide:check" />已复制</span></transition>
          <span v-if="dirty && !copied" class="unsaved">未保存</span>
          <button class="btn btn--ghost btn--sm" type="button" title="按当前来源与模板重新拼；你改过的内容会保留" @click="regenerate"><Icon icon="lucide:refresh-cw" />重新生成</button>
          <button class="btn btn--ghost btn--sm" type="button" @click="copy"><Icon icon="lucide:copy" />复制 Markdown</button>
          <button class="btn btn--primary btn--sm" type="button" :disabled="saving" @click="save">
            <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />保存
          </button>
        </div>

        <section v-for="s in sections" :key="s.key" class="sec">
          <h3>{{ s.title }}</h3>
          <component :is="s.ordered ? 'ol' : 'ul'" v-if="s.items.length">
            <li v-for="i in s.items" :key="i.key" :class="{ edited: isEdited(i) }">
              <EditableText
                :ref="(c) => (editors[i.key] = c as InstanceType<typeof EditableText> | null)"
                :model-value="i.text"
                :label="`${s.title}：一条`"
                placeholder="写点什么…"
                @update:model-value="(v) => edit(i, v)"
                @done="tidy(s, i)"
                @focusout="tidy(s, i)"
              />
              <button v-if="i.ref" type="button" class="ref" :title="`打开：${i.ref.label}`" @click="openRef(i.ref)">
                <Icon :icon="refIcon(i.ref)" />{{ i.ref.label }}
              </button>
              <button type="button" class="rm" :aria-label="`删掉这一条：${i.text}`" @click="removeItem(s, i)"><Icon icon="lucide:x" /></button>
            </li>
          </component>
          <p v-else class="none">（这一段没有内容）</p>
          <button type="button" class="add" @click="addItem(s)"><Icon icon="lucide:plus" />添加一条</button>
        </section>

        <div class="hint2">
          <span><i class="swatch" />淡黄底 = 你改过或加的（{{ editedCount }} 处），重新生成时保留</span>
          <span>点来源标签跳回原记录</span>
          <button v-if="removed.length" type="button" @click="restore">找回删掉的 {{ removed.length }} 条</button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.rhead {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1rem;
}

.back {
  padding: 0.35rem 0.6rem;
  font-size: 0.86rem;
}

.wr {
  display: grid;
  grid-template-columns: 17rem minmax(0, 1fr);
  min-height: 36rem;
  overflow: hidden;
  border-radius: var(--radius-xl);
}

.src {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.1rem 0.95rem;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
  font-size: 0.84rem;
}

.src h2 {
  margin-bottom: 0.45rem;
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.tg {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.35rem 0;
  cursor: pointer;
}

.tg > span {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.tg small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.tpl {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.tpl button {
  display: flex;
  flex-direction: column;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  text-align: left;
  font-size: 0.84rem;
  cursor: pointer;
}

.tpl button small {
  font-size: 0.7rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.tpl button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.src__meta {
  margin-top: auto;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.src__meta a {
  color: var(--color-brand);
}

.doc {
  padding: 1.5rem 2rem 1.3rem;
  font-size: 0.92rem;
  line-height: 1.85;
}

.doc__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
  padding-bottom: 0.8rem;
  border-bottom: 1px solid var(--color-border);
}

.doc__bar h2 {
  margin-right: auto;
  font-size: 1.15rem;
  font-weight: 800;
}

.btn--sm {
  padding: 0.3rem 0.65rem;
  font-size: 0.82rem;
}

.copied,
.unsaved {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  font-weight: 600;
}

.copied {
  color: var(--color-accent-text);
}

.unsaved {
  color: var(--color-text-secondary);
  font-weight: 400;
}

.sec h3 {
  margin: 1rem 0 0.3rem;
  font-size: 1rem;
  font-weight: 800;
}

.sec ol,
.sec ul {
  margin: 0;
  padding-left: 1.3rem;
}

.sec ol {
  list-style: decimal;
}

.sec ul {
  list-style: disc;
}

.sec li {
  position: relative;
  padding-right: 1.6rem;
}

.sec li.edited :deep(.et) {
  background: color-mix(in srgb, var(--color-warn, #b45309) 20%, transparent);
}

.ref {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  margin-left: 0.4rem;
  padding: 0 0.4rem;
  border: 0;
  border-radius: 0.3rem;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  line-height: 1.6;
  vertical-align: 0.1rem;
  cursor: pointer;
}

.ref:hover {
  color: var(--color-brand);
}

.ref svg {
  width: 0.7rem;
  height: 0.7rem;
}

.rm {
  position: absolute;
  top: 0.35rem;
  right: 0;
  display: grid;
  place-items: center;
  width: 1.3rem;
  height: 1.3rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  opacity: 0;
  cursor: pointer;
}

.sec li:hover .rm,
.rm:focus-visible {
  opacity: 1;
}

.rm:hover {
  background: var(--color-bg-soft);
  color: var(--color-danger);
}

.none {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.add {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.15rem;
  padding: 0 0.3rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  cursor: pointer;
  opacity: 0.7;
}

.add:hover {
  color: var(--color-brand);
  opacity: 1;
}

.hint2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 1rem;
  margin-top: 1.2rem;
  padding-top: 0.7rem;
  border-top: 1px dashed var(--color-border);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.hint2 span {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.hint2 button {
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.76rem;
  cursor: pointer;
}

.swatch {
  width: 0.8rem;
  height: 0.8rem;
  border-radius: 0.2rem;
  background: color-mix(in srgb, var(--color-warn, #b45309) 20%, transparent);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1000px) {
  .wr {
    grid-template-columns: 1fr;
  }

  .src {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .doc {
    padding: 1.1rem;
  }
}
</style>
