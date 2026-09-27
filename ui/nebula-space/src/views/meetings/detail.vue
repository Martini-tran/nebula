<script setup lang="ts">
/**
 * 一场会议的三个阶段，同一页面按状态切换：
 * - 准备（planned）：议程与时间预算、参会人
 * - 记录中（live）：左栏议程计时、中间自由书写、右栏实时汇总待办与决议
 *   Ctrl+D 当前行标为决议；行首 [] 或 @某人 识别为待办；Ctrl+↓ 下一个议题
 * - 纪要（done）：概况 → 决议 → 待办表 → 原始记录；我的待办同步进任务，可复制为 Markdown
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { recordRecent } from '../../utils/recent'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { deleteMeeting, fetchMeeting, fetchMeetings, updateMeeting } from '../../api/meetings'
import { fetchTasks } from '../../api/tasks'
import { syncMyActions, useMyNames } from '../../composables/useMeetingSync'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { diffDays, monthDay, nowStamp, relativeDay, todayYmd, weekdayLabel } from '../../utils/date'
import { formatDue, meetingMarkdown, parseMeetingItems } from '../../utils/meetingItems'
import { renderMarkdown } from '../../utils/markdown'
import { endTime } from './meetingInfo'
import type { AgendaItem, Meeting, MeetingSaveRequest } from '../../types/meetings'
import type { Task } from '../../types/tasks'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()

const meeting = ref<Meeting | null>(null)
const content = ref('')
const loading = ref(true)
const loadError = ref('')
const editingSummary = ref(false)
const leftovers = ref<Task[]>([])
const syncedTasks = ref<Task[]>([])
const editor = ref<HTMLTextAreaElement | null>(null)

const mode = computed(() => {
  if (!meeting.value) return 'planned'
  if (meeting.value.status === 'done' && !editingSummary.value) return 'summary'
  if (meeting.value.status === 'planned') return 'planned'
  return 'live'
})

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const m = await fetchMeeting(String(route.params.id))
    meeting.value = m
    content.value = m.content
    recordRecent({ kind: 'meeting', id: String(m.id), title: `${m.title} · ${monthDay(m.date)}`, sub: `${m.startTime} · ${m.durationMin} 分钟`, to: `/meetings/${m.id}` })
    await loadRelated(m)
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 上次遗留：同名会议里分给我、还没做完的待办；以及本场已同步的任务状态 */
const loadRelated = async (m: Meeting) => {
  const [all, tasks] = await Promise.all([fetchMeetings(), fetchTasks({ view: 'all', sourceType: 'meeting' })])
  const previous = all.filter((x) => x.title === m.title && x.id !== m.id && `${x.date} ${x.startTime}` < `${m.date} ${m.startTime}`)
  const prevIds = new Set(previous.map((x) => String(x.id)))
  leftovers.value = tasks.filter((t) => !t.done && prevIds.has(String(t.source?.id)))
  syncedTasks.value = tasks.filter((t) => String(t.source?.id) === String(m.id))
}

// ── 保存 ──

let saveTimer: ReturnType<typeof setTimeout> | undefined
const saving = ref(false)

const save = async (extra: MeetingSaveRequest = {}) => {
  clearTimeout(saveTimer)
  if (!meeting.value) return
  saving.value = true
  try {
    const body: MeetingSaveRequest = { content: content.value, agenda: meeting.value.agenda, ...extra }
    const saved = await updateMeeting(meeting.value.id, body)
    meeting.value = { ...saved, content: content.value }
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

const scheduleSave = () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => save(), 800)
}

watch(content, (value) => {
  if (meeting.value && value !== meeting.value.content) scheduleSave()
})

const patch = (body: MeetingSaveRequest) => {
  if (!meeting.value) return
  Object.assign(meeting.value, body)
  return save(body)
}

// ── 准备 ──

const addAgenda = () => {
  if (!meeting.value) return
  meeting.value.agenda.push({ id: `a${Date.now()}`, title: '', budgetMin: 10, usedSec: 0 })
  nextTick(() => document.querySelector<HTMLInputElement>('.ag-edit:last-child input')?.focus())
}

const removeAgenda = (item: AgendaItem) => {
  if (!meeting.value) return
  meeting.value.agenda = meeting.value.agenda.filter((a) => a.id !== item.id)
  save()
}

const moveAgenda = (index: number, delta: number) => {
  const list = meeting.value?.agenda
  if (!list || index + delta < 0 || index + delta >= list.length) return
  const [item] = list.splice(index, 1)
  list.splice(index + delta, 0, item!)
  save()
}

const budgetTotal = computed(() => meeting.value?.agenda.reduce((sum, a) => sum + a.budgetMin, 0) ?? 0)

const newPerson = ref('')
const addPerson = () => {
  const name = newPerson.value.trim()
  newPerson.value = ''
  if (!meeting.value || !name || meeting.value.attendees.some((a) => a.name === name)) return
  patch({ attendees: [...meeting.value.attendees, { name }] })
}
const removePerson = (name: string) => meeting.value && patch({ attendees: meeting.value.attendees.filter((a) => a.name !== name || a.me) })
const toggleAbsent = (name: string) =>
  meeting.value && patch({ attendees: meeting.value.attendees.map((a) => (a.name === name && !a.me ? { ...a, absent: !a.absent } : a)) })

const start = async () => {
  if (!meeting.value) return
  const first = meeting.value.agenda[0]
  if (first && !content.value.trim()) content.value = `## ${first.title} ${nowStamp().slice(11, 16)}\n`
  await patch({ status: 'live', startedAt: nowStamp(), currentAgendaId: first?.id ?? null })
  await nextTick()
  editor.value?.focus()
  editor.value?.setSelectionRange(content.value.length, content.value.length)
}

// ── 记录中：计时 ──

const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | undefined
let tick = 0

const stampMs = (stamp: string | null) => (stamp ? new Date(stamp.replace(' ', 'T')).getTime() : 0)

const elapsedSec = computed(() => {
  const m = meeting.value
  if (!m?.startedAt) return 0
  const end = m.endedAt ? stampMs(m.endedAt) : now.value
  return Math.max(0, Math.floor((end - stampMs(m.startedAt)) / 1000))
})

const clock = (sec: number) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`

watch(
  () => meeting.value?.status,
  (status) => {
    clearInterval(ticker)
    if (status !== 'live') return
    ticker = setInterval(() => {
      now.value = Date.now()
      const current = meeting.value?.agenda.find((a) => a.id === meeting.value?.currentAgendaId)
      if (current) current.usedSec += 1
      // 议题用时每 15 秒存一次，免得频繁写
      if (++tick % 15 === 0) scheduleSave()
    }, 1000)
  },
  { immediate: true },
)

const currentIndex = computed(() => meeting.value?.agenda.findIndex((a) => a.id === meeting.value?.currentAgendaId) ?? -1)

/** 切换议题：在正文末尾插一个带时间的小标题 */
const switchAgenda = (item: AgendaItem) => {
  if (!meeting.value || item.id === meeting.value.currentAgendaId) return
  meeting.value.currentAgendaId = item.id
  const time = nowStamp().slice(11, 16)
  const sep = content.value && !content.value.endsWith('\n') ? '\n\n' : content.value ? '\n' : ''
  content.value += `${sep}## ${item.title} ${time}\n`
  save({ currentAgendaId: item.id })
  nextTick(() => {
    editor.value?.focus()
    editor.value?.setSelectionRange(content.value.length, content.value.length)
    if (editor.value) editor.value.scrollTop = editor.value.scrollHeight
  })
}

const nextAgenda = () => {
  const next = meeting.value?.agenda[currentIndex.value + 1]
  if (next) switchAgenda(next)
}

// ── 记录中：编辑快捷键 ──

const onKeydown = (event: KeyboardEvent) => {
  const el = editor.value
  if (!el) return
  const ctrl = event.ctrlKey || event.metaKey
  if (ctrl && event.key.toLowerCase() === 'd') {
    event.preventDefault()
    const pos = el.selectionStart
    const lineStart = content.value.lastIndexOf('\n', pos - 1) + 1
    const lineEnd = content.value.indexOf('\n', pos) === -1 ? content.value.length : content.value.indexOf('\n', pos)
    const line = content.value.slice(lineStart, lineEnd)
    const next = /^决议[:：]\s*/.test(line) ? line.replace(/^决议[:：]\s*/, '') : `决议：${line.replace(/^\s*/, '')}`
    content.value = content.value.slice(0, lineStart) + next + content.value.slice(lineEnd)
    const caret = lineStart + next.length
    nextTick(() => el.setSelectionRange(caret, caret))
  } else if (ctrl && event.key === 'ArrowDown' && mode.value === 'live' && meeting.value?.status === 'live') {
    event.preventDefault()
    nextAgenda()
  } else if (ctrl && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save()
  }
}

const items = computed(() =>
  meeting.value ? parseMeetingItems(content.value, myNames, meeting.value.date) : { decisions: [], actions: [] },
)

/** 点右栏条目，光标跳到正文里那一行 */
const gotoLine = (line: number) => {
  const el = editor.value
  if (!el) return
  const lines = content.value.split('\n')
  const pos = lines.slice(0, line).reduce((sum, l) => sum + l.length + 1, 0)
  el.focus()
  el.setSelectionRange(pos, pos + (lines[line]?.length ?? 0))
}

// ── 结束 / 纪要 ──

const finish = async () => {
  if (!meeting.value) return
  const wasDone = meeting.value.status === 'done'
  await save(wasDone ? {} : { status: 'done', endedAt: nowStamp(), currentAgendaId: null })
  try {
    const { meeting: synced, created } = await syncMyActions({ ...meeting.value, content: content.value }, myNames)
    meeting.value = { ...synced, content: content.value }
    editingSummary.value = false
    await loadRelated(meeting.value)
    toast.ok(created ? `纪要已生成，${created} 项我的待办已进入任务` : '纪要已生成')
  } catch (error) {
    toast.error(errorText(error, '同步待办失败'))
  }
}

const overMin = computed(() => {
  const m = meeting.value
  if (!m?.endedAt) return 0
  return Math.round(elapsedSec.value / 60) - m.durationMin
})

const agendaDone = computed(() => meeting.value?.agenda.filter((a) => a.usedSec > 0).length ?? 0)

const syncedOf = (text: string) => {
  const id = meeting.value?.syncedTasks[text]
  return id === undefined ? undefined : syncedTasks.value.find((t) => String(t.id) === String(id))
}

const copyMarkdown = async () => {
  if (!meeting.value) return
  try {
    await navigator.clipboard.writeText(meetingMarkdown({ ...meeting.value, content: content.value }, myNames))
    toast.ok('已复制为 Markdown，可以直接贴进群聊或文档')
  } catch {
    toast.error('复制失败')
  }
}

const remove = async () => {
  if (!meeting.value) return
  const ok = await confirm({
    title: `删除会议「${meeting.value.title}」？`,
    message: '会议记录会被删除；已经进入任务的待办保留。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    clearTimeout(saveTimer)
    await deleteMeeting(meeting.value.id)
    meeting.value = null
    router.push({ name: 'meetings' })
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const dayText = computed(() => {
  const m = meeting.value
  if (!m) return ''
  const rel = relativeDay(m.date)
  return `${rel === monthDay(m.date) ? '' : `${rel} · `}${monthDay(m.date)} ${weekdayLabel(m.date)}`
})

const renderedContent = computed(() => renderMarkdown(content.value))
const lateText = (task: Task) => {
  if (!task.dueDate) return ''
  const d = diffDays(todayYmd(), task.dueDate)
  return d < 0 ? `逾期 ${-d} 天` : relativeDay(task.dueDate)
}

onMounted(load)
onBeforeRouteLeave(() => {
  if (meeting.value && (content.value !== meeting.value.content || meeting.value.status === 'live')) save()
})
onBeforeUnmount(() => {
  clearInterval(ticker)
  clearTimeout(saveTimer)
})
</script>

<template>
  <div class="md-page page">
    <StateBlock v-if="loading && !meeting" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="回到会议" @action="router.push({ name: 'meetings' })" />

    <template v-else-if="meeting">
      <!-- 顶栏 -->
      <header class="bar">
        <router-link :to="{ name: 'meetings' }" class="bar__back" aria-label="返回会议列表"><Icon icon="lucide:arrow-left" /></router-link>
        <span v-if="mode === 'live'" class="bar__live"><i />记录中</span>
        <div class="bar__title">
          <input
            v-if="mode === 'planned'"
            :value="meeting.title"
            class="bar__input"
            aria-label="会议主题"
            @change="patch({ title: ($event.target as HTMLInputElement).value.trim() || meeting.title })"
          />
          <h1 v-else>{{ meeting.title }}<template v-if="mode === 'summary'"> · 纪要</template></h1>
          <p>
            {{ dayText }} · {{ meeting.startTime }}–{{ endTime(meeting) }}
            <template v-if="mode === 'live'"> · <b class="bar__clock" :class="{ over: elapsedSec > meeting.durationMin * 60 }">{{ clock(elapsedSec) }}</b> / {{ meeting.durationMin }} 分钟</template>
            <template v-if="saving"> · 保存中…</template>
          </p>
        </div>
        <div class="bar__acts">
          <template v-if="mode === 'planned'">
            <button class="btn btn--quiet" type="button" @click="remove"><Icon icon="lucide:trash-2" />删除</button>
            <button class="btn btn--primary" type="button" @click="start"><Icon icon="lucide:play" />开始记录</button>
          </template>
          <template v-else-if="mode === 'live'">
            <button v-if="editingSummary" class="btn btn--ghost" type="button" @click="editingSummary = false; content = meeting.content">取消编辑</button>
            <button class="btn btn--primary" type="button" @click="finish">
              <Icon icon="lucide:file-check-2" />{{ meeting.status === 'done' ? '保存并更新纪要' : '结束并生成纪要' }}
            </button>
          </template>
          <template v-else>
            <button class="btn btn--quiet" type="button" @click="remove"><Icon icon="lucide:trash-2" />删除</button>
            <button class="btn btn--ghost" type="button" @click="editingSummary = true"><Icon icon="lucide:pencil" />编辑</button>
            <button class="btn btn--primary" type="button" @click="copyMarkdown"><Icon icon="lucide:copy" />复制为 Markdown</button>
          </template>
        </div>
      </header>

      <!-- 准备 -->
      <div v-if="mode === 'planned'" class="prep">
        <section class="card surface">
          <h2>议程 <small>共 {{ budgetTotal }} 分钟<template v-if="budgetTotal > meeting.durationMin"> · 超出会议时长 {{ budgetTotal - meeting.durationMin }} 分钟</template></small></h2>
          <ol class="ag-list">
            <li v-for="(a, i) in meeting.agenda" :key="a.id" class="ag-edit">
              <span class="ag-edit__n">{{ i + 1 }}</span>
              <input v-model="a.title" placeholder="议题" aria-label="议题" @change="save()" />
              <label class="ag-edit__budget">
                <input v-model.number="a.budgetMin" type="number" min="1" max="240" aria-label="时间预算（分钟）" @change="save()" />分钟
              </label>
              <button type="button" title="上移" @click="moveAgenda(i, -1)"><Icon icon="lucide:arrow-up" /></button>
              <button type="button" title="下移" @click="moveAgenda(i, 1)"><Icon icon="lucide:arrow-down" /></button>
              <button type="button" title="删除" @click="removeAgenda(a)"><Icon icon="lucide:x" /></button>
            </li>
          </ol>
          <button class="btn btn--ghost add" type="button" @click="addAgenda"><Icon icon="lucide:plus" />添加议题</button>
        </section>

        <aside class="prep__side">
          <section class="card surface">
            <h2>时间</h2>
            <div class="when">
              <input type="date" class="field__input" :value="meeting.date" aria-label="日期" @change="patch({ date: ($event.target as HTMLInputElement).value })" />
              <input type="time" class="field__input" :value="meeting.startTime" aria-label="开始时间" @change="patch({ startTime: ($event.target as HTMLInputElement).value })" />
              <select class="field__input" :value="meeting.durationMin" aria-label="时长" @change="patch({ durationMin: Number(($event.target as HTMLSelectElement).value) })">
                <option v-for="m in [15, 30, 45, 60, 90, 120]" :key="m" :value="m">{{ m }} 分钟</option>
              </select>
            </div>
          </section>
          <section class="card surface">
            <h2>参会人</h2>
            <ul class="people">
              <li v-for="a in meeting.attendees" :key="a.name">
                <i :class="{ me: a.me }">{{ a.name.charAt(0) }}</i>{{ a.name }}<small v-if="a.me">（我）</small>
                <button v-if="!a.me" type="button" :aria-label="`移除 ${a.name}`" @click="removePerson(a.name)"><Icon icon="lucide:x" /></button>
              </li>
            </ul>
            <input v-model="newPerson" class="field__input" placeholder="添加参会人，回车" @keydown.enter.prevent="addPerson" />
          </section>
          <section v-if="leftovers.length" class="card surface">
            <h2>上次遗留 <small>{{ leftovers.length }}</small></h2>
            <ul class="mini">
              <li v-for="t in leftovers" :key="t.id"><b>{{ t.title }}</b><small class="late">{{ lateText(t) }}</small></li>
            </ul>
          </section>
        </aside>
      </div>

      <!-- 记录中 / 编辑纪要 -->
      <div v-else-if="mode === 'live'" class="live">
        <aside class="live__left">
          <section class="card surface">
            <h2>议程 <small v-if="meeting.agenda.length">{{ Math.max(0, currentIndex + 1) }} / {{ meeting.agenda.length }}</small></h2>
            <p v-if="!meeting.agenda.length" class="muted">没有议程，直接记吧。</p>
            <button
              v-for="a in meeting.agenda"
              :key="a.id"
              type="button"
              class="ag"
              :class="{ 'ag--on': a.id === meeting.currentAgendaId, 'ag--over': a.usedSec > a.budgetMin * 60 }"
              :disabled="meeting.status !== 'live'"
              @click="switchAgenda(a)"
            >
              <b>{{ a.title }}</b>
              <small>{{ a.budgetMin }} 分钟 · {{ a.usedSec ? `用时 ${Math.ceil(a.usedSec / 60)}` : '未开始' }}</small>
              <span class="ag__bar"><i :style="{ width: `${Math.min(100, (a.usedSec / (a.budgetMin * 60)) * 100)}%` }" /></span>
            </button>
          </section>
          <section class="card surface">
            <h2>参会</h2>
            <label v-for="a in meeting.attendees" :key="a.name" class="att">
              <input type="checkbox" :checked="!a.absent" :disabled="a.me" @change="toggleAbsent(a.name)" />
              {{ a.name }}<small v-if="a.me">（我）</small><small v-else-if="a.absent">· 缺席</small>
            </label>
          </section>
        </aside>

        <section class="live__main surface">
          <textarea
            ref="editor"
            v-model="content"
            class="live__text"
            placeholder="边听边记。Ctrl+D 把当前行标为决议；行首写 [] 或 @某人 就是待办，写上「周三前」会识别成截止日。"
            aria-label="会议记录"
            spellcheck="false"
            @keydown="onKeydown"
          />
          <p class="live__tip"><kbd>Ctrl</kbd> <kbd>D</kbd> 标为决议 · <kbd>@</kbd> 指派待办 · <kbd>Ctrl</kbd> <kbd>↓</kbd> 下一个议题</p>
        </section>

        <aside class="live__right">
          <section v-if="leftovers.length" class="card surface">
            <h2>上次遗留 <small>{{ leftovers.length }}</small></h2>
            <ul class="mini">
              <li v-for="t in leftovers" :key="t.id"><b>{{ t.title }}</b><small class="late">我 · {{ lateText(t) }}</small></li>
            </ul>
          </section>
          <section class="card surface">
            <h2>本次待办 <small>{{ items.actions.length }}</small></h2>
            <p v-if="!items.actions.length" class="muted">行首写 [] 或 @某人 就会出现在这里。</p>
            <ul class="mini">
              <li v-for="a in items.actions" :key="a.line" @click="gotoLine(a.line)">
                <b>{{ a.text }}</b>
                <small>
                  {{ a.owner }} · {{ formatDue(a.due) }}
                  <em v-if="a.mine" class="mine">{{ meeting.syncedTasks[a.text] !== undefined ? '已在任务里' : '将进入我的任务' }}</em>
                </small>
              </li>
            </ul>
          </section>
          <section class="card surface">
            <h2>决议 <small>{{ items.decisions.length }}</small></h2>
            <p v-if="!items.decisions.length" class="muted">Ctrl+D 把当前行标为决议。</p>
            <ul class="mini">
              <li v-for="d in items.decisions" :key="d.line" class="decision" @click="gotoLine(d.line)">{{ d.text }}</li>
            </ul>
          </section>
        </aside>
      </div>

      <!-- 纪要 -->
      <div v-else class="sum">
        <section class="card surface sum__head">
          <p>
            {{ dayText }} {{ meeting.startedAt?.slice(11, 16) ?? meeting.startTime }}–{{ meeting.endedAt?.slice(11, 16) ?? endTime(meeting) }}
            <em v-if="overMin > 0" class="late">（超时 {{ overMin }} 分钟）</em>
          </p>
          <p>
            参会：{{ meeting.attendees.filter((a) => !a.absent).map((a) => a.name).join('、') }}
            <template v-if="meeting.attendees.some((a) => a.absent)"> · 缺席：{{ meeting.attendees.filter((a) => a.absent).map((a) => a.name).join('、') }}</template>
          </p>
          <div class="stats">
            <span v-if="meeting.agenda.length"><b>{{ agendaDone }} / {{ meeting.agenda.length }}</b>议程完成</span>
            <span><b>{{ items.decisions.length }}</b>条决议</span>
            <span><b>{{ items.actions.length }}</b>项待办 · 我的 {{ items.actions.filter((a) => a.mine).length }} 项已进任务</span>
          </div>
        </section>

        <section class="card surface">
          <h2>决议</h2>
          <p v-if="!items.decisions.length" class="muted">没有记录决议。</p>
          <ol class="decisions">
            <li v-for="d in items.decisions" :key="d.line">{{ d.text }}</li>
          </ol>
        </section>

        <section class="card surface">
          <h2>待办</h2>
          <p v-if="!items.actions.length" class="muted">没有记录待办。</p>
          <div v-else class="tbl-wrap">
            <table class="tbl">
              <thead><tr><th>事项</th><th>负责人</th><th>截止</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="a in items.actions" :key="a.line">
                  <td>{{ a.text }}</td>
                  <td>{{ a.owner }}</td>
                  <td class="nowrap">{{ formatDue(a.due) }}</td>
                  <td class="nowrap">
                    <router-link v-if="syncedOf(a.text)" :to="{ path: '/tasks', query: { v: 'all', task: String(syncedOf(a.text)!.id) } }" class="st" :class="{ 'st--done': syncedOf(a.text)!.done }">
                      {{ syncedOf(a.text)!.done ? '已完成' : '已进入我的任务' }}
                    </router-link>
                    <span v-else class="muted">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <details class="card surface raw">
          <summary>原始记录</summary>
          <div class="md" v-html="renderedContent" />
        </details>
      </div>
    </template>
  </div>
</template>

<style scoped>
.md-page {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
}

.card h2 {
  font-size: 0.86rem;
  font-weight: 700;
}

.card h2 small {
  margin-left: 0.3rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.muted {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.late {
  color: var(--color-danger) !important;
  font-style: normal;
}

.bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
}

.bar__back {
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
}

.bar__back:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.bar__live {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
  font-size: 0.8rem;
  font-weight: 700;
}

.bar__live i {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: currentColor;
  animation: blink 1.4s ease infinite;
}

@keyframes blink {
  50% {
    opacity: 0.3;
  }
}

.bar__title {
  flex: 1;
  min-width: 12rem;
}

.bar__title h1,
.bar__input {
  font-size: 1.35rem;
  font-weight: 800;
}

.bar__input {
  width: 100%;
  padding: 0.1rem 0.3rem;
  margin-left: -0.3rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-primary);
  outline: none;
}

.bar__input:hover,
.bar__input:focus {
  border-color: var(--color-border);
}

.bar__title p {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.bar__clock {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
}

.bar__clock.over {
  color: var(--color-danger);
}

.bar__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

/* 准备 */
.prep {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 19rem;
  gap: 1rem;
  align-items: start;
}

.prep__side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ag-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ag-edit {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0;
  border-bottom: 1px solid var(--color-border);
}

.ag-edit__n {
  width: 1.4rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.ag-edit > input {
  flex: 1;
  min-width: 0;
  padding: 0.35rem 0.4rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-primary);
  outline: none;
}

.ag-edit > input:focus {
  border-color: var(--color-brand);
}

.ag-edit__budget {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.ag-edit__budget input {
  width: 3.2rem;
  padding: 0.2rem 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
  text-align: right;
}

.ag-edit button {
  display: inline-grid;
  place-items: center;
  width: 1.6rem;
  height: 1.6rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.ag-edit button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.add {
  align-self: flex-start;
  padding: 0.35rem 0.8rem;
  font-size: 0.85rem;
}

.when {
  display: grid;
  gap: 0.4rem;
}

.people {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.88rem;
}

.people li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.people i {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  background: var(--color-bg-soft);
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 700;
}

.people i.me {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.people small {
  color: var(--color-text-secondary);
}

.people button {
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

/* 记录中 */
.live {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr) 17rem;
  gap: 1rem;
  align-items: start;
}

.live__left,
.live__right {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ag {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.ag:hover:not(:disabled) {
  background: var(--color-bg-soft);
}

.ag:disabled {
  cursor: default;
}

.ag--on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.ag b {
  font-size: 0.86rem;
  font-weight: 600;
}

.ag small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.ag__bar {
  height: 0.25rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.ag__bar i {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.ag--over .ag__bar i {
  background: var(--color-danger);
}

.ag--over small {
  color: var(--color-danger);
}

.att {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.86rem;
}

.att small {
  color: var(--color-text-secondary);
}

.live__main {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem 1.2rem 0.75rem;
  border-radius: var(--radius-lg);
}

.live__text {
  min-height: calc(100vh - var(--header-height) - 12rem);
  border: 0;
  outline: none;
  resize: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.96rem;
  line-height: 1.85;
}

.live__tip {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.mini li {
  display: flex;
  flex-direction: column;
  padding: 0.4rem 0.5rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  cursor: pointer;
}

.mini li:hover {
  background: var(--color-bg-soft);
}

.mini b {
  font-weight: 600;
}

.mini small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.mine {
  margin-left: 0.3rem;
  color: var(--color-brand);
  font-style: normal;
  font-weight: 600;
}

.decision {
  border-left: 3px solid var(--color-accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0 !important;
  background: var(--color-accent-soft);
}

/* 纪要 */
.sum {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 56rem;
}

.sum__head p {
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.3rem;
}

.stats span {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  padding: 0.45rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.stats b {
  font-size: 1.05rem;
  color: var(--color-text-primary);
}

.decisions {
  margin: 0;
  padding-left: 1.3rem;
  line-height: 1.9;
}

.tbl-wrap {
  overflow-x: auto;
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.tbl th {
  padding: 0.45rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  text-align: left;
}

.tbl td {
  padding: 0.55rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
}

.nowrap {
  white-space: nowrap;
}

.st {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.76rem;
  font-weight: 600;
}

.st--done {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.raw summary {
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 1100px) {
  .live {
    grid-template-columns: minmax(0, 1fr) 16rem;
  }

  .live__left {
    display: none;
  }
}

@media (max-width: 860px) {
  .prep,
  .live {
    grid-template-columns: 1fr;
  }

  .live__right,
  .live__left {
    position: static;
  }

  .live__text {
    min-height: 50vh;
  }
}
</style>
