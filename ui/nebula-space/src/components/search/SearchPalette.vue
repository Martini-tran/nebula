<script setup lang="ts">
/**
 * 全局搜索（Ctrl+K）：一个框搜书签、随手记、任务、会议、周报；输入「>」切成命令模式。
 * - 结果按模块分组，「全部」下每组最多 3 条；顶部 chip 是各模块命中数，Tab 在模块间切换
 * - 空输入时显示最近打开的 5 条
 * - ↑↓ 选择、Enter 打开、Ctrl+Enter 新标签打开（书签直接打开原网址）
 * 另外挂了「G 然后某键」的跳转快捷键（不在输入框里时生效）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { searchPalette } from '../../composables/useSearchPalette'
import { quickCapture } from '../../composables/useQuickCapture'
import { resetSearchCache, searchAll, type SearchHit } from '../../api/search'
import { fetchTask } from '../../api/tasks'
import { useFocusStore } from '../../stores/focus'
import { useThemeStore } from '../../stores/theme'
import { useSettingsStore } from '../../stores/settings'
import { MODULES, type ModuleKey } from '../../config/modules'
import { recentItems, recordRecent } from '../../utils/recent'
import { highlightParts, parseSearch, SEARCH_KINDS, type SearchKind } from '../../utils/searchQuery'

const router = useRouter()
const route = useRoute()
const focus = useFocusStore()
const theme = useThemeStore()
const settings = useSettingsStore()

const input = ref<HTMLInputElement | null>(null)
const list = ref<HTMLElement | null>(null)
const text = ref('')
const scope = ref<SearchKind | 'all'>('all')
const hits = ref<SearchHit[]>([])
const loading = ref(false)
const active = ref(0)
const showSyntax = ref(false)

const commandMode = computed(() => text.value.trimStart().startsWith('>'))
const terms = computed(() => (commandMode.value ? [] : parseSearch(text.value).terms))

// ── 命令 ──

interface Command {
  id: string
  group: '新建' | '跳转' | '其他'
  label: string
  icon: string
  keys?: string
  run: () => void
}

/** G 之后按的键 → 模块或页面 */
const GOTO: { key: string; label: string; path: string; icon: string; module?: ModuleKey }[] = [
  { key: 'T', label: '今天', path: '/today', icon: 'lucide:sun', module: 'today' },
  { key: 'B', label: '书签', path: '/bookmarks', icon: 'lucide:bookmark', module: 'bookmarks' },
  { key: 'N', label: '随手记', path: '/notes', icon: 'lucide:pencil-line', module: 'notes' },
  { key: 'D', label: '任务', path: '/tasks', icon: 'lucide:square-check-big', module: 'tasks' },
  { key: 'M', label: '会议', path: '/meetings', icon: 'lucide:users', module: 'meetings' },
  { key: 'C', label: '日历', path: '/calendar', icon: 'lucide:calendar', module: 'calendar' },
  { key: 'H', label: '习惯', path: '/habits', icon: 'lucide:activity', module: 'habits' },
  { key: 'F', label: '专注统计', path: '/focus', icon: 'lucide:timer', module: 'tasks' },
  { key: 'W', label: '周回顾', path: '/review', icon: 'lucide:calendar-check' },
  { key: 'S', label: '设置', path: '/settings', icon: 'lucide:settings' },
]
const gotoTargets = computed(() => GOTO.filter((g) => !g.module || settings.isEnabled(g.module)))

const startFocus = async () => {
  const taskId = route.path === '/tasks' && typeof route.query.task === 'string' ? route.query.task : null
  if (taskId) {
    try {
      const task = await fetchTask(taskId)
      focus.openSetup(task.id, task.title)
      return
    } catch {
      // 任务取不到就当自由专注
    }
  }
  focus.openSetup(null, '自由专注', '不挂在任务上，只记时长')
}

const commands = computed<Command[]>(() => {
  const capture = (mode: 'task' | 'note' | 'bookmark' | 'meeting' | 'ledger') => () => quickCapture.show(mode)
  const create: Command[] = [
    { id: 'new-task', group: '新建', label: '新建任务', icon: 'lucide:square-check-big', run: capture('task') },
    { id: 'new-note', group: '新建', label: '新建随手记', icon: 'lucide:pencil-line', run: capture('note') },
    { id: 'new-bookmark', group: '新建', label: '新建书签', icon: 'lucide:bookmark', run: capture('bookmark') },
    { id: 'new-meeting', group: '新建', label: '新建会议', icon: 'lucide:users', run: capture('meeting') },
    { id: 'new-ledger', group: '新建', label: '记一笔账', icon: 'lucide:wallet', run: capture('ledger') },
    { id: 'focus', group: '新建', label: route.query.task && route.path === '/tasks' ? '开始专注（当前任务）' : '开始专注', icon: 'lucide:timer', run: startFocus },
  ].filter((c) => {
    const needs: Record<string, ModuleKey> = { 'new-task': 'tasks', 'new-note': 'notes', 'new-bookmark': 'bookmarks', 'new-meeting': 'meetings', 'new-ledger': 'ledger', focus: 'tasks' }
    return settings.isEnabled(needs[c.id]!)
  }) as Command[]
  const go: Command[] = gotoTargets.value.map((g) => ({
    id: `go-${g.key}`,
    group: '跳转',
    label: `去「${g.label}」`,
    icon: g.icon,
    keys: `G ${g.key}`,
    run: () => router.push(g.path),
  }))
  const more: Command[] = [
    { id: 'report', group: '其他', label: '写本周周报', icon: 'lucide:file-text', run: () => router.push('/review/report') },
    { id: 'theme', group: '其他', label: theme.isDark ? '切换到浅色' : '切换到深色', icon: theme.isDark ? 'lucide:sun' : 'lucide:moon', run: theme.toggle },
    ...MODULES.filter((m) => m.group === 'more' && settings.isEnabled(m.key)).map((m) => ({
      id: `go-${m.key}`,
      group: '跳转' as const,
      label: `去「${m.label}」`,
      icon: m.icon,
      run: () => router.push(m.path),
    })),
  ]
  const all = [...create, ...go, ...more]
  const q = text.value.trimStart().slice(1).trim().toLowerCase()
  return q ? all.filter((c) => c.label.toLowerCase().includes(q)) : all
})

// ── 搜索 ──

let seq = 0
let timer: ReturnType<typeof setTimeout> | undefined
const run = async () => {
  const q = text.value.trim()
  const mine = ++seq
  if (!q || commandMode.value) {
    hits.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await searchAll(q)
    if (mine === seq) hits.value = result.filter((h) => !KIND_MODULE[h.kind] || settings.isEnabled(KIND_MODULE[h.kind]!))
  } catch {
    if (mine === seq) hits.value = []
  } finally {
    if (mine === seq) loading.value = false
  }
}

watch(text, () => {
  active.value = 0
  clearTimeout(timer)
  timer = setTimeout(run, 160)
})

/** 前缀 b: 之类直接定位到那个模块；删掉前缀时，由前缀切过去的范围回到「全部」 */
watch(
  () => (commandMode.value ? null : parseSearch(text.value).kind),
  (kind, prev) => {
    if (kind) scope.value = kind
    else if (prev && scope.value === prev) scope.value = 'all'
  },
)

/** 模块被关掉时，它的结果分组也不显示 */
const KIND_MODULE: Partial<Record<SearchKind, ModuleKey>> = { task: 'tasks', note: 'notes', bookmark: 'bookmarks', meeting: 'meetings', reading: 'reading', person: 'people' }

const counts = computed(() => {
  const map: Record<string, number> = {}
  hits.value.forEach((h) => (map[h.kind] = (map[h.kind] ?? 0) + 1))
  return map
})

const scopes = computed(() => [
  { key: 'all' as const, label: '全部', n: hits.value.length },
  ...SEARCH_KINDS.filter((k) => !KIND_MODULE[k.key] || settings.isEnabled(KIND_MODULE[k.key]!)).map((k) => ({ key: k.key, label: k.label, n: counts.value[k.key] ?? 0 })),
])

interface Group {
  kind: SearchKind
  label: string
  total: number
  items: SearchHit[]
}

const groups = computed<Group[]>(() =>
  SEARCH_KINDS.filter((k) => scope.value === 'all' || scope.value === k.key)
    .map((k) => {
      const all = hits.value.filter((h) => h.kind === k.key)
      return { kind: k.key, label: k.label, total: all.length, items: scope.value === 'all' ? all.slice(0, 3) : all.slice(0, 30) }
    })
    .filter((g) => g.items.length),
)

const recent = ref(recentItems().slice(0, 5))

/** 当前能用方向键走的所有行 */
type Row = { type: 'hit'; hit: SearchHit } | { type: 'recent'; hit: SearchHit } | { type: 'cmd'; cmd: Command }
const rows = computed<Row[]>(() => {
  if (commandMode.value) return commands.value.map((cmd) => ({ type: 'cmd' as const, cmd }))
  if (!text.value.trim())
    return recent.value.map((r) => ({ type: 'recent' as const, hit: { ...r, meta: '', score: 0 } }))
  return groups.value.flatMap((g) => g.items.map((hit) => ({ type: 'hit' as const, hit })))
})

const indexOfHit = (hit: SearchHit) => rows.value.findIndex((r) => r.type !== 'cmd' && r.hit === hit)
const indexOfCmd = (cmd: Command) => rows.value.findIndex((r) => r.type === 'cmd' && r.cmd === cmd)

const cmdGroups = computed(() => {
  const order: Command['group'][] = ['新建', '跳转', '其他']
  return order.map((g) => ({ label: g, items: commands.value.filter((c) => c.group === g) })).filter((g) => g.items.length)
})

watch(active, () =>
  nextTick(() => list.value?.querySelector('.on')?.scrollIntoView({ block: 'nearest' })),
)

// ── 打开 ──

const close = () => searchPalette.hide()

const openHit = (hit: SearchHit, newTab = false) => {
  recordRecent({ kind: hit.kind, id: hit.id, title: hit.title, sub: hit.sub, to: hit.to, url: hit.url })
  close()
  if (newTab) {
    window.open(hit.url ?? router.resolve(hit.to).href, '_blank', 'noopener')
    return
  }
  router.push(hit.to)
}

const runRow = (row: Row | undefined, newTab = false) => {
  if (!row) return
  if (row.type === 'cmd') {
    close()
    row.cmd.run()
  } else {
    openHit(row.hit, newTab)
  }
}

const cycleScope = (dir: 1 | -1) => {
  const keys = scopes.value.map((s) => s.key)
  const at = keys.indexOf(scope.value)
  scope.value = keys[(at + dir + keys.length) % keys.length]!
  active.value = 0
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.isComposing) return
  const max = rows.value.length
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close()
  } else if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (max) active.value = (active.value + 1) % max
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (max) active.value = (active.value - 1 + max) % max
  } else if (event.key === 'Enter') {
    event.preventDefault()
    runRow(rows.value[active.value], event.ctrlKey || event.metaKey)
  } else if (event.key === 'Tab') {
    event.preventDefault()
    if (!commandMode.value && text.value.trim()) cycleScope(event.shiftKey ? -1 : 1)
  }
}

watch(
  () => searchPalette.open,
  (open) => {
    if (!open) return
    text.value = searchPalette.initial
    scope.value = 'all'
    active.value = 0
    showSyntax.value = false
    recent.value = recentItems().slice(0, 5)
    resetSearchCache()
    nextTick(() => input.value?.focus())
  },
)

// ── 全局快捷键：Ctrl+K 与 G 跳转 ──

let gPending = false
let gTimer: ReturnType<typeof setTimeout> | undefined

const typing = (target: EventTarget | null) => {
  const el = target as HTMLElement | null
  return Boolean(el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)))
}

const onGlobalKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && !event.shiftKey && !event.altKey && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (searchPalette.open) close()
    else searchPalette.show()
    return
  }
  if (searchPalette.open || event.ctrlKey || event.metaKey || event.altKey || typing(event.target)) return
  if (document.querySelector('.dialog-mask, .qc-mask, .fx')) return
  const key = event.key.toUpperCase()
  if (gPending) {
    gPending = false
    clearTimeout(gTimer)
    const target = gotoTargets.value.find((g) => g.key === key)
    if (target) {
      event.preventDefault()
      router.push(target.path)
    }
    return
  }
  if (key === 'G' && !event.shiftKey) {
    gPending = true
    gTimer = setTimeout(() => (gPending = false), 1200)
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))

const kindIcon = (hit: SearchHit) => SEARCH_KINDS.find((k) => k.key === hit.kind)!.icon

const SYNTAX = [
  ['b: vue', '只搜书签（n: 随手记、t: 任务、m: 会议、l: 稍后读、r: 周报、p: 人物）'],
  ['#工作', '按标签或清单过滤'],
  ['@张工', '张工的人物卡，和会议待办中负责人是他的'],
  ['is:open', '未完成的任务 / 待办（is:done、is:overdue）'],
  ['after:9-20', '该日期之后创建或发生（before: 同理）'],
  ['"同名目录"', '精确短语'],
]
</script>

<template>
  <Teleport to="body">
    <transition name="sp">
      <div v-if="searchPalette.open" class="sp-mask" @click.self="close">
        <div class="sp surface" role="dialog" aria-modal="true" aria-label="搜索全部">
          <label class="sp__in">
            <Icon :icon="commandMode ? 'lucide:chevron-right' : 'lucide:search'" />
            <input
              ref="input"
              v-model="text"
              type="text"
              :placeholder="'搜索书签、随手记、任务、会议…  输入 > 执行命令'"
              aria-label="搜索全部"
              role="combobox"
              aria-expanded="true"
              aria-controls="sp-results"
              autocomplete="off"
              spellcheck="false"
              @keydown="onKeydown"
            />
            <Icon v-if="loading" icon="lucide:loader-circle" class="spin sp__spin" />
            <kbd>Esc</kbd>
          </label>

          <div v-if="!commandMode && text.trim()" class="sp__scope" role="tablist" aria-label="按模块过滤">
            <button
              v-for="s in scopes"
              :key="s.key"
              type="button"
              role="tab"
              :aria-selected="scope === s.key"
              :class="{ on: scope === s.key }"
              :disabled="s.key !== 'all' && !s.n"
              @click="scope = s.key; active = 0; input?.focus()"
            >
              {{ s.label }}<span class="n">{{ s.n }}</span>
            </button>
          </div>

          <div id="sp-results" ref="list" class="sp__res" role="listbox">
            <!-- 命令模式 -->
            <template v-if="commandMode">
              <template v-for="g in cmdGroups" :key="g.label">
                <div class="sp__g"><span>{{ g.label }}</span></div>
                <div
                  v-for="c in g.items"
                  :key="c.id"
                  class="sp__cmd"
                  :class="{ on: indexOfCmd(c) === active }"
                  role="option"
                  :aria-selected="indexOfCmd(c) === active"
                  @mousemove="active = indexOfCmd(c)"
                  @click="runRow({ type: 'cmd', cmd: c })"
                >
                  <span class="sp__ico"><Icon :icon="c.icon" /></span>
                  <span class="sp__label">{{ c.label }}</span>
                  <kbd v-if="c.keys">{{ c.keys }}</kbd>
                </div>
              </template>
              <p v-if="!commands.length" class="sp__empty">没有这个命令</p>
            </template>

            <!-- 空输入：最近打开 -->
            <template v-else-if="!text.trim()">
              <template v-if="recent.length && !showSyntax">
                <div class="sp__g"><span>最近打开</span></div>
                <div
                  v-for="(r, i) in rows"
                  :key="i"
                  class="sp__r"
                  :class="{ on: i === active }"
                  role="option"
                  :aria-selected="i === active"
                  @mousemove="active = i"
                  @click="runRow(r)"
                >
                  <template v-if="r.type !== 'cmd'">
                    <span class="sp__ico"><Icon :icon="kindIcon(r.hit)" /></span>
                    <span class="sp__main"><b>{{ r.hit.title }}</b><small>{{ r.hit.sub }}</small></span>
                    <span class="sp__meta">{{ SEARCH_KINDS.find((k) => k.key === r.hit.kind)?.label }}</span>
                  </template>
                </div>
              </template>
              <div v-else class="sp__syntax">
                <p v-if="!recent.length">还没有打开过的记录。试试这些写法：</p>
                <table>
                  <tr v-for="[code, desc] in SYNTAX" :key="code">
                    <td><button type="button" @click="text = code.replace(/\s.*$/, ' '); input?.focus()"><code>{{ code }}</code></button></td>
                    <td>{{ desc }}</td>
                  </tr>
                </table>
              </div>
            </template>

            <!-- 搜索结果 -->
            <template v-else>
              <template v-for="g in groups" :key="g.kind">
                <div class="sp__g">
                  <span>{{ g.label }}</span>
                  <button v-if="scope === 'all' && g.total > g.items.length" type="button" @click="scope = g.kind; active = 0; input?.focus()">
                    全部 {{ g.total }} 条 →
                  </button>
                  <span v-else>{{ g.total }}</span>
                </div>
                <div
                  v-for="hit in g.items"
                  :key="`${hit.kind}:${hit.id}`"
                  class="sp__r"
                  :class="{ on: indexOfHit(hit) === active }"
                  role="option"
                  :aria-selected="indexOfHit(hit) === active"
                  @mousemove="active = indexOfHit(hit)"
                  @click="openHit(hit, $event.ctrlKey || $event.metaKey)"
                >
                  <span v-if="hit.kind === 'bookmark' || hit.kind === 'person'" class="sp__fav" :style="{ background: hit.color }">{{ hit.title.charAt(0).toUpperCase() }}</span>
                  <span v-else-if="hit.kind === 'task'" class="sp__ico">
                    <i class="ck" :class="`ck--${hit.state}`" :style="hit.state === 'open' && hit.color ? { borderColor: hit.color } : undefined"><Icon v-if="hit.state === 'done'" icon="lucide:check" /></i>
                  </span>
                  <span v-else class="sp__ico"><Icon :icon="kindIcon(hit)" /></span>
                  <span class="sp__main">
                    <b :class="{ done: hit.state === 'done' }"><template v-for="(p, i) in highlightParts(hit.title, terms)" :key="i"><mark v-if="p.hit">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></b>
                    <small v-if="hit.snippet" class="sp__snip"><template v-for="(p, i) in highlightParts(hit.snippet, terms)" :key="i"><mark v-if="p.hit">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></small>
                    <small :class="{ over: hit.state === 'overdue' }">{{ hit.sub }}</small>
                  </span>
                  <span class="sp__meta">
                    <template v-if="indexOfHit(hit) === active">{{ hit.url ? 'Ctrl Enter 打开网址' : 'Enter 打开' }}</template>
                    <template v-else>{{ hit.meta }}</template>
                  </span>
                </div>
              </template>
              <div v-if="!loading && !groups.length" class="sp__empty">
                <p>没有找到「{{ text.trim() }}」</p>
                <small>换个词，或者用 <code>is:done</code>、<code>#清单</code> 缩小范围</small>
              </div>
            </template>
          </div>

          <footer class="sp__foot">
            <span><kbd>↑↓</kbd> 选择</span>
            <span><kbd>Enter</kbd> 打开</span>
            <span v-if="!commandMode"><kbd>Tab</kbd> 切换模块</span>
            <span><kbd>&gt;</kbd> 命令</span>
            <button v-if="!commandMode && !text.trim() && recent.length" type="button" class="sp__link" @click="showSyntax = !showSyntax">
              {{ showSyntax ? '最近打开' : '搜索语法' }}
            </button>
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.sp-mask {
  position: fixed;
  inset: 0;
  z-index: 115;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12vh 1rem 1rem;
  background: rgba(8, 10, 16, 0.4);
  backdrop-filter: blur(2px);
}

.sp {
  display: flex;
  flex-direction: column;
  width: min(40rem, 100%);
  max-height: 76vh;
  overflow: hidden;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.sp__in {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.sp__in > svg {
  width: 1.15rem;
  height: 1.15rem;
  flex: none;
}

.sp__in input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 1.02rem;
}

.sp__spin {
  width: 1rem !important;
  height: 1rem !important;
}

kbd {
  padding: 0.02rem 0.35rem;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-family: var(--font-mono, monospace);
  font-size: 0.68rem;
  white-space: nowrap;
}

.sp__scope {
  display: flex;
  gap: 0.3rem;
  padding: 0.55rem 1rem;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
}

.sp__scope button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.8rem;
  white-space: nowrap;
  cursor: pointer;
}

.sp__scope button:disabled {
  opacity: 0.45;
  cursor: default;
}

.sp__scope button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.sp__scope .n {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.sp__scope .on .n {
  color: inherit;
}

.sp__res {
  flex: 1;
  min-height: 6rem;
  padding: 0.3rem 0.5rem 0.6rem;
  overflow-y: auto;
}

.sp__g {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0.55rem 0.25rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.sp__g button {
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
}

.sp__r,
.sp__cmd {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.5rem 0.55rem;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.sp__r.on,
.sp__cmd.on {
  background: var(--color-brand-soft);
}

.sp__ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.6rem;
  height: 1.6rem;
  color: var(--color-text-secondary);
}

.ck {
  display: inline-grid;
  place-items: center;
  width: 1rem;
  height: 1rem;
  border: 1.8px solid var(--color-border);
  border-radius: 50%;
}

.ck--overdue {
  border-color: var(--color-danger);
}

.ck--done {
  border-color: var(--color-accent);
  background: var(--color-accent);
  color: #fff;
}

.ck svg {
  width: 0.65rem;
  height: 0.65rem;
}

.sp__fav {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 800;
}

.sp__main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.sp__main b {
  overflow: hidden;
  font-size: 0.9rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp__main b.done {
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.sp__main small {
  overflow: hidden;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp__main small.over {
  color: var(--color-danger);
}

.sp__snip {
  color: var(--color-text-primary) !important;
  opacity: 0.8;
}

mark {
  padding: 0 0.05rem;
  border-radius: 0.2rem;
  background: color-mix(in srgb, #facc15 45%, transparent);
  color: inherit;
}

.sp__meta {
  flex: none;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.sp__label {
  flex: 1;
  font-size: 0.9rem;
}

.sp__empty {
  padding: 1.6rem 1rem;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
}

.sp__empty small {
  display: block;
  margin-top: 0.3rem;
  font-size: 0.78rem;
}

code {
  padding: 0.05rem 0.3rem;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
  font-family: var(--font-mono, monospace);
  font-size: 0.8em;
}

.sp__syntax {
  padding: 0.5rem 0.55rem;
  font-size: 0.84rem;
}

.sp__syntax p {
  margin-bottom: 0.5rem;
  color: var(--color-text-secondary);
}

.sp__syntax table {
  width: 100%;
  border-collapse: collapse;
}

.sp__syntax td {
  padding: 0.35rem 0.3rem;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.sp__syntax td:first-child {
  width: 8.5rem;
}

.sp__syntax button {
  border: 0;
  background: none;
  padding: 0;
  cursor: pointer;
}

.sp__syntax code {
  color: var(--color-text-primary);
}

.sp__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 1rem;
  padding: 0.55rem 1rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.sp__link {
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
}

.sp-enter-active,
.sp-leave-active {
  transition: opacity 0.15s ease;
}

.sp-enter-active .sp,
.sp-leave-active .sp {
  transition: transform 0.15s var(--ease-soft);
}

.sp-enter-from,
.sp-leave-to {
  opacity: 0;
}

.sp-enter-from .sp {
  transform: translateY(-6px) scale(0.99);
}

@media (max-width: 560px) {
  .sp-mask {
    padding-top: 1rem;
  }

  .sp__foot span:nth-child(3),
  .sp__foot span:nth-child(4) {
    display: none;
  }
}
</style>
