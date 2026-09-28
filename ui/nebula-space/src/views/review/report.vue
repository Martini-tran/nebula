<script setup lang="ts">
/**
 * 日报与周报：正文就是 Markdown，不分模板，打开是空白（或上次保存的内容）。
 * 「插入记录」把当天 / 这周的任务、会议、专注按规则拼成一段文字插进去；周报还能「按日报汇总」，把这周每天的日报依次拼进来。
 * 左栏是这一周：周报一行、每天的日报一行，写过的有标记。复制为 Markdown 贴进公司的系统或群聊。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { fetchReport, fetchReports, saveReport } from '../../api/reviews'
import { useMyNames } from '../../composables/useMeetingSync'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { addDays, hmOf, monthDay, relativeDay, startOfWeek, todayYmd, weekdayLabel, ymdOf } from '../../utils/date'
import { renderMarkdown } from '../../utils/markdown'
import { recordRecent } from '../../utils/recent'
import { loadWeek, summarize } from './weekData'
import { dayDraft, loadDay, reportTitle, summarizeDailies, weekDraft } from './reportDraft'
import type { Report, ReportType } from '../../types/reviews'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()
const today = todayYmd()
const thisWeek = startOfWeek(today)

const YMD = /^\d{4}-\d{2}-\d{2}$/
const ymdQuery = (q: unknown) => (typeof q === 'string' && YMD.test(q) ? q : null)

// ?type=day|week&date=；旧链接 ?week= 当周报。不带类型默认写日报
const type = computed<ReportType>(() => (route.query.type === 'week' || (!route.query.type && route.query.week) ? 'week' : 'day'))
const date = computed(() => {
  const q = ymdQuery(route.query.date) ?? ymdQuery(route.query.week)
  if (type.value === 'week') {
    const start = q ? startOfWeek(q) : thisWeek
    return start > thisWeek ? thisWeek : start
  }
  return q && q < today ? q : today
})
const weekStart = computed(() => startOfWeek(date.value))

const saved = ref<Report | null>(null)
/** 这一周的日报和周报，左栏与「按日报汇总」用 */
const weekReports = ref<Report[]>([])
const content = ref('')
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const inserting = ref(false)
const mode = ref<'edit' | 'preview'>('edit')
const textarea = ref<HTMLTextAreaElement | null>(null)

const dirty = computed(() => content.value !== (saved.value?.content ?? ''))

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [current, list] = await Promise.all([
      fetchReport(type.value, date.value),
      fetchReports({ from: weekStart.value, to: addDays(weekStart.value, 6) }),
    ])
    saved.value = current
    weekReports.value = list
    content.value = current?.content ?? ''
    mode.value = 'edit'
    if (current) {
      recordRecent({ kind: 'report', id: `${type.value}:${date.value}`, title: reportTitle(type.value, date.value), sub: relativeDay(ymdOf(current.updateTime)), to: `/review/report?type=${type.value}&date=${date.value}` })
    }
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

watch([type, date], load)

// ── 左栏：这一周 ──

const weekRow = computed(() => ({ written: weekReports.value.some((r) => r.type === 'week' && r.date === weekStart.value) }))
const dayRows = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const day = addDays(weekStart.value, i)
    return { date: day, future: day > today, written: weekReports.value.some((r) => r.type === 'day' && r.date === day) }
  }),
)
const dailies = computed(() => weekReports.value.filter((r) => r.type === 'day'))

const go = (t: ReportType, d: string) => router.push({ query: { type: t, date: d } })
const step = (n: number) => go(type.value, addDays(date.value, type.value === 'week' ? n * 7 : n))
const isLatest = computed(() => (type.value === 'week' ? date.value >= thisWeek : date.value >= today))

// ── 插入 ──

/** 插在光标处；没有光标（预览模式、没点过）就接在末尾，前后空一行 */
const insert = (text: string) => {
  if (!text.trim()) {
    toast.info(type.value === 'day' ? '这天没有可插入的记录' : '这周没有可插入的记录')
    return
  }
  const el = textarea.value
  const cur = content.value
  const at = mode.value === 'edit' && el && document.activeElement === el ? el.selectionStart : cur.length
  const before = cur.slice(0, at).replace(/\s+$/, '')
  const after = cur.slice(at).replace(/^\s+/, '')
  content.value = [before, text, after].filter(Boolean).join('\n\n') + (after ? '' : '\n')
  mode.value = 'edit'
}

const insertRecords = async () => {
  inserting.value = true
  try {
    if (type.value === 'day') {
      insert(dayDraft(await loadDay(date.value), myNames))
    } else {
      const src = await loadWeek(date.value)
      insert(weekDraft(src, summarize(src, myNames)))
    }
  } catch (error) {
    toast.error(errorText(error, '读取记录失败'))
  } finally {
    inserting.value = false
  }
}

const insertDailies = () => {
  if (!dailies.value.length) {
    toast.info('这周还没有写过日报')
    return
  }
  insert(summarizeDailies(dailies.value))
}

// ── 复制与保存 ──

const copied = ref(false)
const copy = async () => {
  const text = `## ${reportTitle(type.value, date.value)}\n\n${content.value.trim()}`
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const area = document.createElement('textarea')
    area.value = text
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    area.remove()
  }
  copied.value = true
  setTimeout(() => (copied.value = false), 2000)
}

const save = async () => {
  if (saving.value || !dirty.value) return
  saving.value = true
  try {
    const result = await saveReport(type.value, date.value, content.value)
    saved.value = result
    content.value = result?.content ?? ''
    weekReports.value = [...weekReports.value.filter((r) => !(r.type === type.value && r.date === date.value)), ...(result ? [result] : [])]
    toast.ok(result ? `${type.value === 'day' ? '日报' : '周报'}已保存` : '内容为空，已删除这一份')
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

const onKey = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    save()
  }
}

const guard = async () => {
  if (!dirty.value) return true
  return confirm({ title: '还没保存', message: '离开后这次的修改会丢失。', confirmText: '不保存，离开', danger: true })
}
onBeforeRouteLeave(guard)
// 同一页换日期、换日报周报也先问一句
onBeforeRouteUpdate(guard)

const previewHtml = computed(() => renderMarkdown(content.value))

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="report page">
    <header class="rhead">
      <router-link class="btn btn--quiet back" :to="{ path: '/review', query: weekStart === thisWeek ? {} : { week: weekStart } }"><Icon icon="lucide:arrow-left" />周回顾</router-link>
      <h1 class="page-title">日报与周报</h1>
    </header>

    <div class="wr surface">
      <aside class="side">
        <div class="seg" role="radiogroup" aria-label="类型">
          <button type="button" role="radio" :aria-checked="type === 'day'" :class="{ on: type === 'day' }" @click="go('day', type === 'day' ? date : weekStart === thisWeek ? today : addDays(weekStart, 6))">日报</button>
          <button type="button" role="radio" :aria-checked="type === 'week'" :class="{ on: type === 'week' }" @click="go('week', weekStart)">周报</button>
        </div>

        <nav class="days" aria-label="这一周">
          <button type="button" class="row" :class="{ on: type === 'week' }" @click="go('week', weekStart)">
            <Icon icon="lucide:calendar-range" /><span>本周周报</span>
            <i v-if="weekRow.written" class="mark" title="已写" />
          </button>
          <button
            v-for="d in dayRows"
            :key="d.date"
            type="button"
            class="row"
            :class="{ on: type === 'day' && date === d.date }"
            :disabled="d.future"
            @click="go('day', d.date)"
          >
            <span class="row__wd">{{ weekdayLabel(d.date) }}</span><span>{{ monthDay(d.date) }}</span>
            <small v-if="d.date === today">今天</small>
            <i v-if="d.written" class="mark" title="已写" />
          </button>
        </nav>

        <p class="side__meta">
          本周日报 {{ dailies.length }} 份<template v-if="saved"> · 这份保存于 {{ relativeDay(ymdOf(saved.updateTime)) }} {{ hmOf(saved.updateTime) }}</template>
        </p>
      </aside>

      <article class="doc">
        <div class="doc__bar">
          <span class="doc__nav">
            <button type="button" class="btn btn--ghost btn--icon" :aria-label="type === 'week' ? '上一周' : '前一天'" @click="step(-1)"><Icon icon="lucide:chevron-left" /></button>
            <button type="button" class="btn btn--ghost btn--icon" :aria-label="type === 'week' ? '下一周' : '后一天'" :disabled="isLatest" @click="step(1)"><Icon icon="lucide:chevron-right" /></button>
          </span>
          <h2>{{ reportTitle(type, date) }}</h2>
          <transition name="fade"><span v-if="copied" class="copied"><Icon icon="lucide:check" />已复制</span></transition>
          <span v-if="dirty && !copied" class="unsaved">未保存</span>
          <div class="seg" role="radiogroup" aria-label="模式">
            <button type="button" role="radio" :aria-checked="mode === 'edit'" :class="{ on: mode === 'edit' }" @click="mode = 'edit'">编辑</button>
            <button type="button" role="radio" :aria-checked="mode === 'preview'" :class="{ on: mode === 'preview' }" @click="mode = 'preview'">预览</button>
          </div>
        </div>

        <div class="doc__tools">
          <button class="btn btn--ghost btn--sm" type="button" :disabled="loading || inserting" :title="type === 'day' ? '当天完成的任务、会议决议、明天到期的、没做完的' : '这周完成的任务、会议决议、下周计划、风险'" @click="insertRecords">
            <Icon :icon="inserting ? 'lucide:loader-circle' : 'lucide:list-plus'" :class="{ spin: inserting }" />{{ type === 'day' ? '插入今天的记录' : '插入本周的记录' }}
          </button>
          <button v-if="type === 'week'" class="btn btn--ghost btn--sm" type="button" :disabled="loading" title="把这周写过的日报按日期依次拼进来" @click="insertDailies">
            <Icon icon="lucide:layers" />按日报汇总<small v-if="dailies.length">{{ dailies.length }}</small>
          </button>
          <span class="doc__spacer" />
          <button class="btn btn--ghost btn--sm" type="button" :disabled="!content.trim()" @click="copy"><Icon icon="lucide:copy" />复制 Markdown</button>
          <button class="btn btn--primary btn--sm" type="button" :disabled="saving || !dirty" title="Ctrl+S" @click="save">
            <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />保存
          </button>
        </div>

        <StateBlock v-if="loading" state="loading" />
        <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
        <textarea
          v-else-if="mode === 'edit'"
          ref="textarea"
          v-model="content"
          class="doc__text"
          :aria-label="reportTitle(type, date)"
          :placeholder="type === 'day' ? '今天做了什么、明天打算做什么……支持 Markdown。\n也可以点上面的「插入今天的记录」从任务和会议里带出来。' : '这周的总结……支持 Markdown。\n可以点上面的「按日报汇总」把这周的日报拼进来再改。'"
        />
        <div v-else-if="content.trim()" class="doc__preview md" v-html="previewHtml" />
        <p v-else class="doc__empty">还没有内容。</p>
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
  grid-template-columns: 15rem minmax(0, 1fr);
  min-height: 36rem;
  overflow: hidden;
  border-radius: var(--radius-xl);
}

.side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 0.95rem;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
  font-size: 0.86rem;
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.days {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.55rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-primary);
  font-size: 0.86rem;
  text-align: left;
  cursor: pointer;
}

.row:first-child {
  margin-bottom: 0.35rem;
}

.row:hover:not(:disabled) {
  background: var(--color-bg-soft);
}

.row:disabled {
  color: var(--color-text-secondary);
  opacity: 0.5;
  cursor: default;
}

.row.on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.row__wd {
  width: 2.2rem;
}

.row small {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.mark {
  width: 0.45rem;
  height: 0.45rem;
  margin-left: auto;
  border-radius: 50%;
  background: var(--color-brand);
}

.side__meta {
  margin-top: auto;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.doc {
  display: flex;
  flex-direction: column;
  padding: 1.2rem 1.6rem 1.3rem;
  min-width: 0;
}

.doc__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.doc__bar h2 {
  margin-right: auto;
  font-size: 1.15rem;
  font-weight: 800;
}

.doc__nav {
  display: inline-flex;
  gap: 0.2rem;
}

.btn--icon {
  padding: 0.3rem 0.4rem;
}

.doc__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 0.8rem 0 0.6rem;
  padding-bottom: 0.8rem;
  border-bottom: 1px solid var(--color-border);
}

.doc__tools small {
  margin-left: 0.15rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.doc__spacer {
  flex: 1;
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
}

.copied {
  font-weight: 600;
  color: var(--color-accent-text);
}

.unsaved {
  color: var(--color-text-secondary);
}

.doc__text {
  flex: 1;
  width: 100%;
  min-height: 28rem;
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.95rem;
  line-height: 1.8;
}

.doc__preview {
  flex: 1;
  min-height: 28rem;
  font-size: 0.95rem;
  line-height: 1.8;
}

.doc__empty {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
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

  .side {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .days {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .row:first-child {
    margin-bottom: 0;
  }

  .doc {
    padding: 1.1rem;
  }
}
</style>
