<script setup lang="ts">
/**
 * 设置：左侧分组、右侧逐项开关，改动即时保存（右上角「已保存」闪一下）。
 * 模块开关关掉的从导航与「今天」里消失，数据保留；主题只存本机。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import SideNav, { type SideNavGroup } from '../../components/layout/SideNav.vue'
import ToggleSwitch from '../../components/base/ToggleSwitch.vue'
import { useSettingsStore } from '../../stores/settings'
import { useThemeStore } from '../../stores/theme'
import { useFocusStore } from '../../stores/focus'
import { MODULES, type ModuleKey } from '../../config/modules'
import { NOTE_COLORS } from '../notes/noteLife'
import { notificationState } from '../../composables/useReminders'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { exportAll } from './exportAll'
import { exportChromeBookmarks } from '../../api/space'
import { USE_MOCK } from '../../api/mock'
import type { SpaceSettings } from '../../types/settings'
import type { CaptureMode } from '../../composables/useQuickCapture'
import type { ThemePreference } from '../../utils/theme'

const route = useRoute()
const router = useRouter()
const settings = useSettingsStore()
const theme = useThemeStore()
const focus = useFocusStore()

type Section = 'general' | 'notes' | 'tasks' | 'reading' | 'notify' | 'data'
const SECTIONS: { key: Section; label: string; icon: string; desc: string }[] = [
  { key: 'general', label: '通用', icon: 'lucide:settings', desc: '对整个个人空间生效。' },
  { key: 'notes', label: '随手记', icon: 'lucide:pencil-line', desc: '新笔记的默认样子，以及快速记录。' },
  { key: 'tasks', label: '任务与专注', icon: 'lucide:square-check-big', desc: '番茄钟的默认时长与休息。' },
  { key: 'reading', label: '稍后读', icon: 'lucide:book-open', desc: '阅读目标与摘录回顾。' },
  { key: 'notify', label: '通知', icon: 'lucide:bell', desc: '任务提醒、专注结束、习惯提醒。' },
  { key: 'data', label: '数据', icon: 'lucide:database', desc: '把你的数据完整带走。' },
]

const active = computed<Section>(() => {
  const s = route.query.s
  return SECTIONS.some((x) => x.key === s) ? (s as Section) : 'general'
})
const current = computed(() => SECTIONS.find((s) => s.key === active.value)!)
const groups = computed<SideNavGroup[]>(() => [
  {
    key: 's',
    items: SECTIONS.filter((s) => s.key !== 'reading' || settings.isEnabled('reading')).map((s) => ({ key: s.key, label: s.label, icon: s.icon })),
  },
])
const select = (key: string) => router.replace({ query: key === 'general' ? {} : { s: key } })

const data = computed(() => settings.data)
const set = (patch: Partial<SpaceSettings>) => settings.update(patch)

// ── 「已保存」一闪 ──

const flash = ref(false)
let flashTimer: ReturnType<typeof setTimeout> | undefined
const blink = () => {
  flash.value = true
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => (flash.value = false), 1600)
}
watch(() => settings.savedAt, blink)
// 专注设置与主题只存本机，改了也闪一下
watch(() => ({ ...focus.settings }), blink, { deep: true })
watch(() => settings.saveError, (msg) => msg && toast.error(`设置没保存上：${msg}`))

// ── 通用 ──

const TOGGLABLE = MODULES.filter((m) => m.key !== 'today')
const EMOJI: Partial<Record<ModuleKey, string>> = {
  bookmarks: '🔖', notes: '📝', tasks: '✅', meetings: '👥', calendar: '📅', habits: '🌱',
  reading: '📖', files: '🗂️', ledger: '💰', goals: '🎯', people: '👤', share: '🌐',
}
const homeOptions = computed(() => MODULES.filter((m) => m.group === 'main' && m.status === 'ready' && settings.isEnabled(m.key)))

const THEMES: { key: ThemePreference; label: string }[] = [
  { key: 'light', label: '浅色' },
  { key: 'dark', label: '深色' },
  { key: 'system', label: '跟随系统' },
]
const setTheme = (value: ThemePreference) => {
  theme.prefer(value)
  blink()
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const TIMES = Array.from({ length: 30 }, (_, i) => `${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`)

// ── 随手记 ──

const TTLS = [
  { days: 1, label: '1 天' },
  { days: 3, label: '3 天' },
  { days: 7, label: '7 天' },
  { days: 30, label: '30 天' },
  { days: 0, label: '长期' },
]
const CAPTURE: { key: CaptureMode; label: string }[] = [
  { key: 'note', label: '笔记' },
  { key: 'task', label: '任务' },
  { key: 'bookmark', label: '书签' },
  { key: 'meeting', label: '会议' },
  { key: 'ledger', label: '记账' },
]

// ── 任务与专注 ──

const FOCUS_MIN = [25, 45, 60]
const BREAK_MIN = [5, 10, 15]

// ── 通知 ──

const permission = ref(notificationState())
const requestPermission = async () => {
  if (permission.value === 'unsupported') return
  try {
    permission.value = await Notification.requestPermission()
    if (permission.value === 'granted') toast.ok('已授权，到点会弹出系统通知')
    else if (permission.value === 'denied') toast.error('浏览器拒绝了通知，需要在地址栏左侧的站点设置里重新允许')
  } catch {
    permission.value = notificationState()
  }
}

// ── 数据 ──

const exporting = ref<'' | 'json' | 'html'>('')
const doExport = async () => {
  exporting.value = 'json'
  try {
    const count = await exportAll()
    toast.ok(`已导出 ${count} 条记录`)
  } catch (error) {
    toast.error(errorText(error, '导出失败'))
  } finally {
    exporting.value = ''
  }
}
const doExportBookmarks = async () => {
  exporting.value = 'html'
  try {
    await exportChromeBookmarks()
  } catch (error) {
    toast.error(errorText(error, '书签导出失败'))
  } finally {
    exporting.value = ''
  }
}

const resetDemo = async () => {
  const ok = await confirm({
    title: '重置演示数据？',
    message: '还没接后端的稍后读、文件柜、记账、人物卡、公开主页会清空并换回示例数据。书签、随手记、任务、会议、习惯、专注、日报周报、目标与纪念日和偏好设置在后端，不受影响。',
    confirmText: '清空并重置',
    danger: true,
  })
  if (!ok) return
  Object.keys(localStorage)
    .filter((k) => k.startsWith('nebula-space:mock:') || k.startsWith('nebula-space:focus') || k.startsWith('nebula-space:review') || k === 'nebula-space:recent-open')
    .forEach((k) => localStorage.removeItem(k))
  // 文件柜的文件内容存在 IndexedDB
  try {
    indexedDB.deleteDatabase('nebula-space-files')
  } catch {
    // 删不掉也不影响重置
  }
  location.reload()
}
</script>

<template>
  <div class="settings page">
    <aside class="settings__side">
      <h1 class="page-title">设置</h1>
      <SideNav :groups="groups" :active="active" @select="select" />
    </aside>

    <section class="settings__main surface">
      <header class="head">
        <div>
          <h2>{{ current.label }}</h2>
          <p>{{ current.desc }}</p>
        </div>
        <transition name="fade">
          <span v-if="flash" class="saved" role="status"><Icon icon="lucide:check" />已保存</span>
        </transition>
      </header>

      <!-- 通用 -->
      <template v-if="active === 'general'">
        <div class="sg">
          <h3>模块</h3>
          <p class="sg__hint">关掉用不上的模块：它会从顶部导航和「今天」里消失，数据保留，随时可以再打开。</p>
          <div class="modules">
            <label v-for="m in TOGGLABLE" :key="m.key" class="mod" :class="{ off: !settings.isEnabled(m.key) }">
              <span class="mod__name">{{ EMOJI[m.key] }} {{ m.label }}<small v-if="m.status === 'planned'" class="tag">规划中</small></span>
              <ToggleSwitch :model-value="settings.isEnabled(m.key)" :label="`${m.label}模块`" @update:model-value="(on) => settings.toggleModule(m.key, on)" />
            </label>
          </div>
        </div>

        <div class="sg">
          <h3>显示</h3>
          <div class="item">
            <div><b>登录后首页</b><small>打开 Space 时先看到哪一页</small></div>
            <select class="sel" :value="data.home" aria-label="登录后首页" @change="set({ home: ($event.target as HTMLSelectElement).value as ModuleKey })">
              <option v-for="m in homeOptions" :key="m.key" :value="m.key">{{ m.label }}</option>
            </select>
          </div>
          <div class="item">
            <div><b>主题</b><small>只对这台设备生效</small></div>
            <div class="segs" role="radiogroup" aria-label="主题">
              <button v-for="t in THEMES" :key="t.key" type="button" role="radio" :aria-checked="theme.preference === t.key" :class="{ on: theme.preference === t.key }" @click="setTheme(t.key)">{{ t.label }}</button>
            </div>
          </div>
          <div class="item">
            <div><b>一周从哪天开始</b><small>影响日历、习惯、周回顾的范围</small></div>
            <div class="segs" role="radiogroup" aria-label="一周从哪天开始">
              <button type="button" role="radio" :aria-checked="data.weekStart === 1" :class="{ on: data.weekStart === 1 }" @click="set({ weekStart: 1 })">周一</button>
              <button type="button" role="radio" :aria-checked="data.weekStart === 0" :class="{ on: data.weekStart === 0 }" @click="set({ weekStart: 0 })">周日</button>
            </div>
          </div>
        </div>

        <div class="sg">
          <h3>节奏</h3>
          <div class="item">
            <div><b>晚间回顾</b><small>到点后「今天」面板顶部换成回顾卡</small></div>
            <div class="row">
              <select class="sel" :value="data.eveningReview.time" :disabled="!data.eveningReview.enabled" aria-label="晚间回顾时间"
                @change="set({ eveningReview: { ...data.eveningReview, time: ($event.target as HTMLSelectElement).value } })">
                <option v-for="t in TIMES.filter((x) => x >= '16:00')" :key="t" :value="t">{{ t }}</option>
              </select>
              <ToggleSwitch :model-value="data.eveningReview.enabled" label="晚间回顾" @update:model-value="(on) => set({ eveningReview: { ...data.eveningReview, enabled: on } })" />
            </div>
          </div>
          <div class="item">
            <div><b>周回顾提醒</b><small>在「今天」显示生成周报的提示卡</small></div>
            <div class="row">
              <select class="sel" :value="data.weeklyReview.weekday" :disabled="!data.weeklyReview.enabled" aria-label="周回顾提醒：星期"
                @change="set({ weeklyReview: { ...data.weeklyReview, weekday: Number(($event.target as HTMLSelectElement).value) } })">
                <option v-for="(w, i) in WEEKDAYS" :key="i" :value="i">{{ w }}</option>
              </select>
              <select class="sel" :value="data.weeklyReview.time" :disabled="!data.weeklyReview.enabled" aria-label="周回顾提醒：时间"
                @change="set({ weeklyReview: { ...data.weeklyReview, time: ($event.target as HTMLSelectElement).value } })">
                <option v-for="t in TIMES" :key="t" :value="t">{{ t }}</option>
              </select>
              <ToggleSwitch :model-value="data.weeklyReview.enabled" label="周回顾提醒" @update:model-value="(on) => set({ weeklyReview: { ...data.weeklyReview, enabled: on } })" />
            </div>
          </div>
        </div>
      </template>

      <!-- 随手记 -->
      <template v-else-if="active === 'notes'">
        <div class="sg">
          <div class="item">
            <div><b>临时笔记默认保留</b><small>新笔记多少天后自动归档；选「长期」则默认不过期</small></div>
            <div class="segs" role="radiogroup" aria-label="临时笔记默认保留">
              <button v-for="t in TTLS" :key="t.days" type="button" role="radio" :aria-checked="data.noteTtlDays === t.days" :class="{ on: data.noteTtlDays === t.days }" @click="set({ noteTtlDays: t.days })">{{ t.label }}</button>
            </div>
          </div>
          <div class="item">
            <div><b>新笔记的底色</b><small>便签墙上的默认颜色，写完还能改</small></div>
            <div class="colors" role="radiogroup" aria-label="新笔记的底色">
              <button v-for="c in NOTE_COLORS" :key="c.key" type="button" role="radio" :aria-checked="data.noteColor === c.key" :title="c.label"
                :class="[`note--${c.key}`, { on: data.noteColor === c.key }]" @click="set({ noteColor: c.key })" />
            </div>
          </div>
          <div class="item">
            <div><b>快速记录默认类型</b><small><kbd>Ctrl</kbd> <kbd>Shift</kbd> <kbd>Space</kbd> 或页头「+」打开时先选中哪一种</small></div>
            <div class="segs" role="radiogroup" aria-label="快速记录默认类型">
              <button v-for="c in CAPTURE" :key="c.key" type="button" role="radio" :aria-checked="data.captureMode === c.key" :class="{ on: data.captureMode === c.key }" @click="set({ captureMode: c.key })">{{ c.label }}</button>
            </div>
          </div>
        </div>
      </template>

      <!-- 任务与专注 -->
      <template v-else-if="active === 'tasks'">
        <div class="sg">
          <h3>专注</h3>
          <div class="item">
            <div><b>默认专注时长</b><small>开始专注时预选的时长，当次还能改</small></div>
            <div class="segs" role="radiogroup" aria-label="默认专注时长">
              <button v-for="m in FOCUS_MIN" :key="m" type="button" role="radio" :aria-checked="focus.settings.minutes === m" :class="{ on: focus.settings.minutes === m }" @click="focus.settings.minutes = m">{{ m }} 分钟</button>
            </div>
          </div>
          <div class="item">
            <div><b>结束后自动休息</b><small>一轮结束直接进入休息倒计时</small></div>
            <div class="row">
              <div class="segs" role="radiogroup" aria-label="休息时长">
                <button v-for="m in BREAK_MIN" :key="m" type="button" role="radio" :disabled="!focus.settings.autoBreak" :aria-checked="focus.settings.breakMinutes === m" :class="{ on: focus.settings.breakMinutes === m }" @click="focus.settings.breakMinutes = m">{{ m }} 分钟</button>
              </div>
              <ToggleSwitch v-model="focus.settings.autoBreak" label="结束后自动休息" />
            </div>
          </div>
          <div class="item">
            <div><b>标签页标题显示倒计时</b><small>切到别的标签页也能看到还剩多久</small></div>
            <ToggleSwitch v-model="focus.settings.titleCountdown" label="标签页标题显示倒计时" />
          </div>
          <p class="sg__hint">专注设置只存在这台设备上。</p>
        </div>
      </template>

      <!-- 稍后读 -->
      <template v-else-if="active === 'reading'">
        <div class="sg">
          <div class="item">
            <div><b>每月阅读目标</b><small>稍后读右栏显示本月读完了几篇</small></div>
            <div class="segs" role="radiogroup" aria-label="每月阅读目标">
              <button v-for="n in [4, 6, 10, 15, 20]" :key="n" type="button" role="radio" :aria-checked="data.readingGoal === n" :class="{ on: data.readingGoal === n }" @click="set({ readingGoal: n })">{{ n }} 篇</button>
            </div>
          </div>
          <div class="item">
            <div><b>今天回顾一条</b><small>摘录库顶部随机翻出一条旧划线，让它被重新看见</small></div>
            <ToggleSwitch :model-value="data.dailyQuote" label="今天回顾一条" @update:model-value="(on) => set({ dailyQuote: on })" />
          </div>
        </div>
      </template>

      <!-- 通知 -->
      <template v-else-if="active === 'notify'">
        <div class="sg">
          <div class="item">
            <div>
              <b>浏览器通知</b>
              <small>页面开着时，到点弹出系统通知；没授权时只在页面里提示</small>
              <span v-if="permission === 'granted'" class="perm perm--ok"><Icon icon="lucide:check" />已授权</span>
              <span v-else-if="permission === 'denied'" class="perm"><Icon icon="lucide:circle-alert" />浏览器拒绝了通知，需在站点设置里重新允许</span>
              <span v-else-if="permission === 'unsupported'" class="perm"><Icon icon="lucide:circle-alert" />这个浏览器不支持通知</span>
              <span v-else class="perm"><Icon icon="lucide:circle-alert" />浏览器尚未授权</span>
            </div>
            <button v-if="permission === 'default'" class="btn btn--ghost" type="button" @click="requestPermission">授权通知</button>
          </div>
          <div class="item">
            <div><b>任务提醒</b><small>按任务上设的「提前 N 分钟提醒」</small></div>
            <ToggleSwitch :model-value="data.remind.tasks" label="任务提醒" @update:model-value="(on) => set({ remind: { ...data.remind, tasks: on } })" />
          </div>
          <div class="item">
            <div><b>习惯提醒</b><small>按习惯里设的提醒时间，已打卡就不提醒</small></div>
            <ToggleSwitch :model-value="data.remind.habits" label="习惯提醒" @update:model-value="(on) => set({ remind: { ...data.remind, habits: on } })" />
          </div>
          <div class="item">
            <div><b>专注结束</b><small>一轮结束、休息结束时通知</small></div>
            <ToggleSwitch v-model="focus.settings.notify" label="专注结束通知" :disabled="permission !== 'granted'" />
          </div>
          <p class="sg__hint">提醒只在 Space 页面开着时生效（可以在后台标签页里）。关掉页面后的推送需要后端支持，之后再做。</p>
        </div>
      </template>

      <!-- 数据 -->
      <template v-else>
        <div class="sg">
          <div class="item">
            <div><b>导出全部数据</b><small>随手记、任务、会议、习惯、专注、周报、稍后读、记账、目标与纪念日、文件信息、偏好，一个 JSON 文件；人物卡单独一节</small></div>
            <button class="btn btn--ghost" type="button" :disabled="Boolean(exporting)" @click="doExport">
              <Icon :icon="exporting === 'json' ? 'lucide:loader-circle' : 'lucide:download'" :class="{ spin: exporting === 'json' }" />导出 JSON
            </button>
          </div>
          <div v-if="settings.isEnabled('bookmarks')" class="item">
            <div><b>导出书签</b><small>Chrome 兼容的书签 HTML，能直接导入浏览器</small></div>
            <button class="btn btn--ghost" type="button" :disabled="Boolean(exporting)" @click="doExportBookmarks">
              <Icon :icon="exporting === 'html' ? 'lucide:loader-circle' : 'lucide:bookmark'" :class="{ spin: exporting === 'html' }" />导出 HTML
            </button>
          </div>
          <div v-if="USE_MOCK" class="item item--danger">
            <div><b>重置演示数据</b><small>后端还没接通的模块数据存在这个浏览器里；清空后换回示例数据</small></div>
            <button class="btn btn--ghost danger" type="button" @click="resetDemo"><Icon icon="lucide:rotate-ccw" />重置</button>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.settings {
  display: grid;
  grid-template-columns: 13rem minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

.settings__side {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
}

.settings__main {
  max-width: 50rem;
  padding: 1.4rem 1.6rem 1.8rem;
  border-radius: var(--radius-xl);
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.4rem;
}

.head h2 {
  font-size: 1.2rem;
  font-weight: 800;
}

.head p {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.saved {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--color-accent-text);
  font-size: 0.8rem;
  font-weight: 700;
}

.sg {
  margin-top: 1.4rem;
}

.sg h3 {
  margin-bottom: 0.3rem;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.sg__hint {
  margin: 0.25rem 0 0.6rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.modules {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.5rem;
}

.mod {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 0.88rem;
  cursor: pointer;
}

.mod.off .mod__name {
  color: var(--color-text-secondary);
}

.mod__name {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  white-space: nowrap;
}

.mod__name .tag {
  padding: 0 0.35rem;
  font-size: 0.62rem;
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--color-border);
}

.item > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.item b {
  font-size: 0.92rem;
}

.item small {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: none;
}

.sel {
  padding: 0.35rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  font-size: 0.86rem;
}

.sel:disabled {
  opacity: 0.5;
}

.segs {
  display: inline-flex;
  flex: none;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.segs button {
  padding: 0.3rem 0.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  white-space: nowrap;
  cursor: pointer;
}

.segs button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.segs button:disabled {
  opacity: 0.5;
  cursor: default;
}

.colors {
  display: flex;
  gap: 0.45rem;
}

.colors button {
  width: 1.6rem;
  height: 1.6rem;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  cursor: pointer;
}

.colors button.on {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.note--plain {
  background: var(--color-bg-surface);
}

.note--yellow {
  background: var(--note-yellow);
}

.note--green {
  background: var(--note-green);
}

.note--blue {
  background: var(--note-blue);
}

.note--pink {
  background: var(--note-pink);
}

.note--purple {
  background: var(--note-purple);
}

.perm {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.2rem;
  color: var(--color-warn, #b45309);
  font-size: 0.76rem;
}

.perm--ok {
  color: var(--color-accent-text);
}

kbd {
  padding: 0 0.3rem;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono, monospace);
  font-size: 0.66rem;
}

.item .btn {
  flex: none;
  padding: 0.4rem 0.8rem;
  font-size: 0.84rem;
}

.danger:hover {
  border-color: var(--color-danger) !important;
  color: var(--color-danger) !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 820px) {
  .settings {
    grid-template-columns: minmax(0, 1fr);
  }

  .settings__side {
    position: static;
    min-width: 0;
  }

  .settings__side :deep(.sn__group) {
    display: flex;
    flex-direction: row;
    gap: 0.25rem;
    overflow-x: auto;
  }

  .settings__side :deep(.sn__item) {
    flex: none;
    width: auto;
  }

  .settings__main {
    padding: 1.1rem;
  }

  .item {
    flex-wrap: wrap;
  }
}
</style>
