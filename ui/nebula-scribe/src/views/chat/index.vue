<script setup lang="ts">
/**
 * 创作对话页（全屏，不套 DefaultLayout）——《写作Agent设计.md》7.6，批次 C1。
 *
 * 状态约定：
 * - current 为 null 表示「新对话」草稿：不落库，发出首条消息时才创建会话（7.6.4）；
 * - 关联作品只能在首条消息之前选择，之后锁定（7.6.2）；
 * - 同一时刻只有一轮生成，生成中不允许切换会话。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BubbleList, XSender } from 'vue-element-plus-x'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatWelcome from './components/ChatWelcome.vue'
import {
  createDialog,
  deleteDialog,
  fetchDialog,
  fetchDialogs,
  fetchMessages,
  regenerateReply,
  sendMessage,
  setMockWorkTitle,
  updateDialog,
} from '../../api/dialog'
import { fetchWorks } from '../../api/work'
import { useThemeStore } from '../../stores/theme'
import type { DialogDetail, DialogListItem, DialogMessageStatus, DialogStreamResult } from '../../types/dialog'
import type { EntityId, WorkListItem } from '../../types/work'

/** 界面消息：在接口消息之上多两种过渡态 */
interface UiMessage {
  key: string
  id?: EntityId
  role: 'user' | 'assistant'
  content: string
  status: DialogMessageStatus | 'PENDING' | 'STREAMING'
  error?: string
}

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()

const MAX_LENGTH = 2000
const SIDEBAR_KEY = 'nebula-scribe:chat-sidebar-collapsed'

// ── 会话列表 ──
const dialogs = ref<DialogListItem[]>([])
const listLoading = ref(false)
const keyword = ref('')

// ── 当前会话 ──
const current = ref<DialogDetail | null>(null)
const messages = ref<UiMessage[]>([])
/**
 * 会话切换的过渡：加载期间旧内容保持在屏幕上并变淡（switching），
 * 数据到齐后换 viewKey，让新内容淡入；只有加载超过 300ms 才出转圈，快速切换不闪烁。
 * 首条消息创建会话时不换 viewKey，否则正在生成的回复会被整块淡出。
 */
const switching = ref(false)
const showSpinner = ref(false)
const viewKey = ref(0)
/** 首屏数据到齐前不渲染消息区，避免直接打开 /chat/:id 时先闪一下欢迎页 */
const ready = ref(false)
let spinnerTimer: ReturnType<typeof setTimeout> | undefined
const loadError = ref('')
/** 草稿态下选择的关联作品，'' 表示不关联 */
const draftWorkId = ref<string>('')
const works = ref<WorkListItem[]>([])

// ── 生成 ──
const controller = ref<AbortController | null>(null)
const streaming = computed(() => controller.value !== null)
/** 首条消息创建会话后会 replace 路由，这一次路由变化不应重新拉消息，否则会冲掉正在生成的内容 */
let skipLoadFor: string | null = null
/** 首条消息建会话的请求在途：防止连按回车建出两个会话 */
let creating = false
let uiSeq = 0

// ── 布局 ──
const readCollapsed = () => {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === '1'
  } catch {
    return false
  }
}
const collapsed = ref(readCollapsed())
const drawerOpen = ref(false)

const senderRef = ref<InstanceType<typeof XSender> | null>(null)

const locked = computed(() => (current.value?.messageCount ?? 0) > 0 || messages.value.length > 0)
const currentWorkTitle = computed(() => {
  if (current.value) return current.value.workTitle ?? null
  return works.value.find((item) => String(item.id) === draftWorkId.value)?.title ?? null
})
const title = computed(() => current.value?.title ?? '新对话')
const previewTheme = computed<'light' | 'dark'>(() => (themeStore.currentTheme === 'dark' ? 'dark' : 'light'))
const lastAssistantKey = computed(() => [...messages.value].reverse().find((item) => item.role === 'assistant')?.key)

/** BubbleList 需要的气泡字段；消息本体仍通过插槽渲染 */
const bubbles = computed(() =>
  messages.value.map((item) => ({
    ...item,
    placement: item.role === 'user' ? ('end' as const) : ('start' as const),
    loading: item.status === 'PENDING',
    variant: item.role === 'user' ? ('filled' as const) : ('borderless' as const),
    shape: 'corner' as const,
    maxWidth: item.role === 'user' ? '80%' : '100%',
  })),
)

// ───────────────────────────── 数据加载 ─────────────────────────────

const loadDialogs = async () => {
  listLoading.value = true
  try {
    dialogs.value = await fetchDialogs(keyword.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话列表加载失败')
  } finally {
    listLoading.value = false
  }
}

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDialogs, 300)
})

const loadWorks = async () => {
  try {
    const page = await fetchWorks({ pageNum: 1, pageSize: 100, sort: 'recent' })
    works.value = page.records
  } catch {
    // 作品列表只用于「关联作品」下拉，失败时只保留「不关联」选项
    works.value = []
  }
}

const toUi = (item: { id: EntityId; role: 'user' | 'assistant'; content: string; status: DialogMessageStatus }): UiMessage => ({
  key: `m-${item.id}`,
  id: item.id,
  role: item.role,
  content: item.content,
  status: item.status,
})

const openDialog = async (id: string) => {
  switching.value = true
  clearTimeout(spinnerTimer)
  spinnerTimer = setTimeout(() => (showSpinner.value = true), 300)
  try {
    const [detail, list] = await Promise.all([fetchDialog(id), fetchMessages(id)])
    current.value = detail
    messages.value = list.map(toUi)
    loadError.value = ''
  } catch (error) {
    current.value = null
    messages.value = []
    loadError.value = error instanceof Error ? error.message : '对话加载失败'
  } finally {
    clearTimeout(spinnerTimer)
    showSpinner.value = false
    switching.value = false
    viewKey.value++
    ready.value = true
  }
}

const resetDraft = () => {
  current.value = null
  messages.value = []
  loadError.value = ''
  senderRef.value?.clear()
  viewKey.value++
  ready.value = true
}

// 路由是唯一的真相源：/chat 为草稿，/chat/:dialogId 为已有会话
watch(
  () => route.params.dialogId,
  (value) => {
    const id = typeof value === 'string' && value ? value : null
    if (id && id === skipLoadFor) {
      skipLoadFor = null
      return
    }
    if (id) {
      openDialog(id)
    } else {
      resetDraft()
    }
  },
  { immediate: true },
)

// ───────────────────────────── 会话操作 ─────────────────────────────

const guardStreaming = () => {
  if (streaming.value) {
    ElMessage.warning('正在生成，先停止或等它写完')
    return true
  }
  return false
}

const selectDialog = (id: EntityId) => {
  drawerOpen.value = false
  if (String(id) === String(current.value?.id) || guardStreaming()) return
  router.push({ name: 'chat', params: { dialogId: String(id) } })
}

const newChat = () => {
  drawerOpen.value = false
  if (guardStreaming()) return
  draftWorkId.value = ''
  if (route.params.dialogId) {
    router.push({ name: 'chat' })
  } else {
    resetDraft()
  }
}

const renameDialog = async (item: Pick<DialogListItem, 'id' | 'title'>) => {
  try {
    const { value } = await ElMessageBox.prompt('', '重命名对话', {
      inputValue: item.title,
      inputPlaceholder: '对话标题',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (text: string) => (text.trim() ? (text.trim().length <= 60 ? true : '最多 60 个字') : '标题不能为空'),
    })
    const updated = await updateDialog(item.id, { title: value.trim() })
    const row = dialogs.value.find((dialog) => String(dialog.id) === String(item.id))
    if (row) row.title = updated.title
    if (current.value && String(current.value.id) === String(item.id)) current.value.title = updated.title
  } catch (error) {
    // 取消弹窗会以 'cancel' / 'close' 拒绝，不算错误
    if (error instanceof Error) ElMessage.error(error.message)
  }
}

const removeDialog = async (item: Pick<DialogListItem, 'id' | 'title'>) => {
  try {
    await ElMessageBox.confirm(`「${item.title}」及其全部消息将被永久删除，无法恢复。`, '删除这段对话？', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  try {
    await deleteDialog(item.id)
    dialogs.value = dialogs.value.filter((dialog) => String(dialog.id) !== String(item.id))
    if (current.value && String(current.value.id) === String(item.id)) {
      await router.replace({ name: 'chat' })
    }
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

/** 有新消息的会话挪到列表顶部，与「按最后活动时间倒序」一致 */
const touchDialog = (detail: DialogDetail) => {
  const rest = dialogs.value.filter((dialog) => String(dialog.id) !== String(detail.id))
  dialogs.value = [{ ...detail, updateTime: new Date().toISOString() }, ...rest]
}

// ───────────────────────────── 发送与生成 ─────────────────────────────

/** 流式生成的公共部分：占位气泡 → 逐段追加 → 按结果落定状态 */
const runStream = async (start: (handlers: Parameters<typeof sendMessage>[2], signal: AbortSignal) => Promise<DialogStreamResult>) => {
  const reply: UiMessage = { key: `ui-${++uiSeq}`, role: 'assistant', content: '', status: 'PENDING' }
  messages.value.push(reply)
  // 之后都通过响应式代理修改，保证界面更新
  const target = messages.value[messages.value.length - 1]!
  const abort = new AbortController()
  controller.value = abort

  try {
    const result = await start(
      {
        onMeta: (meta) => {
          target.id = meta.assistantMessageId
        },
        onDelta: (text) => {
          target.status = 'STREAMING'
          target.content += text
        },
      },
      abort.signal,
    )
    target.status = result.status
    target.error = result.error
  } catch (error) {
    target.status = 'FAILED'
    target.error = error instanceof Error ? error.message : '回复失败，请稍后重试'
  } finally {
    controller.value = null
    if (current.value) {
      current.value.messageCount = messages.value.length
      touchDialog(current.value)
    }
  }
}

const send = async (text: string) => {
  const content = text.trim()
  if (!content || streaming.value || creating) return
  if (content.length > MAX_LENGTH) {
    ElMessage.warning(`最多 ${MAX_LENGTH} 字`)
    return
  }

  // 先把问题放上屏幕，再去建会话：不让用户盯着欢迎页等一个网络往返
  const question: UiMessage = { key: `ui-${++uiSeq}`, role: 'user', content, status: 'DONE' }
  messages.value.push(question)

  // 草稿态：此刻才创建会话，标题取首问前 20 字，关联作品随之锁定
  if (!current.value) {
    creating = true
    try {
      const workId = draftWorkId.value || null
      const created = await createDialog({ workId, title: content.slice(0, 20) })
      const workTitle = currentWorkTitle.value
      setMockWorkTitle(created.id, workTitle)
      current.value = { ...created, workTitle: created.workTitle ?? workTitle }
      skipLoadFor = String(created.id)
      router.replace({ name: 'chat', params: { dialogId: String(created.id) } })
    } catch (error) {
      messages.value = messages.value.filter((item) => item.key !== question.key)
      ElMessage.error(error instanceof Error ? error.message : '创建对话失败')
      return
    } finally {
      creating = false
    }
  }

  const dialogId = current.value?.id
  if (dialogId == null) return
  await runStream((handlers, signal) => sendMessage(dialogId, content, handlers, signal))
}

const readInput = () => senderRef.value?.getModelValue()?.text ?? ''

const onSubmit = async () => {
  if (streaming.value || creating) return
  let text = readInput()
  // XSender 的内部模型在输入事件处理完后才同步：粘贴或输入法上屏后立即回车会读到空值，稍等再读一次
  if (!text.trim()) {
    await new Promise((resolve) => setTimeout(resolve, 60))
    text = readInput()
  }
  if (!text.trim()) return
  senderRef.value?.clear()
  send(text)
}

const stop = () => {
  controller.value?.abort()
}

/** 重试失败的回复与「重新生成」是同一件事：替换最后一条 AI 回复 */
const regenerate = async () => {
  if (!current.value || streaming.value) return
  if (messages.value.at(-1)?.role === 'assistant') {
    messages.value.pop()
  }
  const dialogId = current.value.id
  await runStream((handlers, signal) => regenerateReply(dialogId, handlers, signal))
}

const copy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

// ───────────────────────────── 布局 ─────────────────────────────

const toggleCollapsed = () => {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem(SIDEBAR_KEY, collapsed.value ? '1' : '0')
  } catch {
    // 存储不可用时只影响「记住收起状态」
  }
}

/** 侧栏里的「收起」按钮：窄屏下侧栏是抽屉，收起即关闭抽屉 */
const onCollapse = () => {
  if (window.matchMedia('(max-width: 768px)').matches) {
    drawerOpen.value = false
  } else {
    toggleCollapsed()
  }
}

const openSidebar = () => {
  if (window.matchMedia('(max-width: 768px)').matches) {
    drawerOpen.value = true
  } else if (collapsed.value) {
    toggleCollapsed()
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') drawerOpen.value = false
}

// 首页入口带 ?q= / ?workId= 进来：预选作品、自动发出首问，然后清掉参数，刷新不重复提问
onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  const { q, workId } = route.query
  loadDialogs()
  await loadWorks()
  if (typeof workId === 'string' && workId) {
    draftWorkId.value = workId
  }
  if (typeof q === 'string' || typeof workId === 'string') {
    await router.replace({ name: 'chat' })
    await nextTick()
    if (typeof q === 'string' && q.trim()) send(q)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(searchTimer)
  clearTimeout(spinnerTimer)
  controller.value?.abort()
})
</script>

<template>
  <div class="chat" :class="{ 'chat--collapsed': collapsed, 'chat--drawer': drawerOpen }">
    <ChatSidebar
      v-model:keyword="keyword"
      class="chat__side"
      :dialogs="dialogs"
      :active-id="current?.id ?? null"
      :loading="listLoading"
      @select="selectDialog"
      @create="newChat"
      @rename="renameDialog"
      @remove="removeDialog"
      @collapse="onCollapse"
    />
    <div class="chat__scrim" aria-hidden="true" @click="drawerOpen = false" />

    <main class="chat__main">
      <header class="bar">
        <button class="icon-btn bar__open" type="button" aria-label="展开会话列表" title="展开会话列表" @click="openSidebar">
          <Icon icon="lucide:panel-left-open" />
        </button>

        <label v-if="!locked" class="work-select">
          <Icon icon="lucide:book-open" />
          <span class="sr-only">关联作品</span>
          <select v-model="draftWorkId" :disabled="!!current">
            <option value="">不关联作品</option>
            <option v-for="work in works" :key="String(work.id)" :value="String(work.id)">《{{ work.title }}》</option>
          </select>
        </label>
        <span
          v-else
          class="work-tag"
          :class="{ 'work-tag--none': !currentWorkTitle }"
          title="关联作品在首条消息后锁定"
        >
          <Icon v-if="currentWorkTitle" icon="lucide:book-open" />
          {{ currentWorkTitle ? `《${currentWorkTitle}》` : '不关联作品' }}
          <Icon icon="lucide:lock" />
        </span>

        <span class="bar__sep" aria-hidden="true">·</span>
        <div class="bar__title">
          <h1>{{ title }}</h1>
          <button
            v-if="current"
            class="icon-btn"
            type="button"
            aria-label="重命名"
            title="重命名"
            @click="renameDialog(current)"
          >
            <Icon icon="lucide:pencil" />
          </button>
          <span v-if="!locked" class="bar__hint">发出第一条消息后，关联作品将锁定</span>
        </div>
      </header>

      <section class="thread" :class="{ 'thread--switching': switching }" :aria-busy="switching">
        <Transition name="thread-fade" mode="out-in">
          <div v-if="ready" :key="viewKey" class="thread__view">
            <!-- 欢迎页 → 第一条消息也走同一个淡入，不硬切 -->
            <Transition name="thread-fade" mode="out-in">
              <div v-if="loadError" class="thread__state" role="alert">
                <p>{{ loadError }}</p>
                <button class="btn btn--ghost" type="button" @click="router.replace({ name: 'chat' })">开始新对话</button>
              </div>
              <div v-else-if="messages.length === 0" class="thread__scroll">
                <div class="thread__column">
                  <ChatWelcome :work-title="currentWorkTitle" @ask="send" />
                </div>
              </div>
              <BubbleList v-else :list="bubbles" max-height="100%" class="thread__list">
                <template #content="{ item }">
                  <span v-if="item.role === 'user'" class="text-user">{{ item.content }}</span>
                  <div v-else-if="item.status === 'FAILED'" class="failed" role="alert">
                    <span>回复失败：{{ item.error || '请稍后重试' }}</span>
                    <button class="btn btn--ghost" type="button" :disabled="streaming" @click="regenerate">重试</button>
                  </div>
                  <template v-else>
                    <MdPreview :model-value="item.content" :theme="previewTheme" class="md" />
                    <span v-if="item.status === 'STOPPED'" class="stopped">
                      <Icon icon="lucide:circle-stop" />
                      已停止生成
                    </span>
                  </template>
                </template>
                <template #footer="{ item }">
                  <div v-if="item.role === 'assistant' && (item.status === 'DONE' || item.status === 'STOPPED')" class="actions">
                    <button type="button" @click="copy(item.content)">
                      <Icon icon="lucide:copy" />
                      复制
                    </button>
                    <button v-if="item.key === lastAssistantKey" type="button" :disabled="streaming" @click="regenerate">
                      <Icon icon="lucide:refresh-cw" />
                      重新生成
                    </button>
                  </div>
                </template>
              </BubbleList>
            </Transition>
          </div>
        </Transition>
        <Transition name="spinner-fade">
          <div v-if="showSpinner" class="thread__spinner" role="status">
            <Icon icon="lucide:loader-circle" class="spin" />
            加载中…
          </div>
        </Transition>
      </section>

      <footer class="composer">
        <XSender
          ref="senderRef"
          class="composer__sender"
          placeholder="继续问……  Enter 发送，Shift + Enter 换行"
          :loading="streaming"
          :max-length="MAX_LENGTH"
          @submit="onSubmit"
          @cancel="stop"
        />
      </footer>
    </main>
  </div>
</template>

<style scoped lang="scss">
.chat {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg-canvas);
}

.chat__side {
  flex-shrink: 0;
  transition: margin-left var(--duration-panel) var(--ease-soft);
}

.chat--collapsed .chat__side {
  margin-left: -16rem;
}

.chat__scrim {
  display: none;
}

.chat__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

/* ── 顶栏 ── */
.bar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 3.4rem;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.icon-btn {
  display: inline-grid;
  flex-shrink: 0;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.icon-btn svg {
  width: 1.05rem;
  height: 1.05rem;
}

.bar__open {
  display: none;
}

.chat--collapsed .bar__open {
  display: inline-grid;
}

.work-select {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--color-text-secondary);
}

.work-select svg {
  width: 1rem;
  height: 1rem;
}

.work-select select {
  max-width: 12rem;
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-primary);
}

.work-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
}

.work-tag--none {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.work-tag svg {
  width: 0.9rem;
  height: 0.9rem;
}

.bar__sep {
  color: var(--color-text-secondary);
}

.bar__title {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.3rem;
  min-width: 0;
}

.bar__title h1 {
  overflow: hidden;
  font-size: 1rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar__hint {
  margin-left: 0.4rem;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

/* ── 消息区 ── */
.thread {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.thread__view {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  transition: opacity 0.2s ease-out;
}

/* 等新会话数据时，旧内容先淡下去，而不是被「加载中」替换掉 */
.thread--switching .thread__view {
  opacity: 0.45;
}

.thread__spinner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--color-text-secondary);
  pointer-events: none;
}

/* 会话切换、欢迎页 → 消息列表：淡出快、淡入慢，并带一点点上浮 */
.thread-fade-enter-active {
  transition:
    opacity var(--duration-enter) var(--ease-soft),
    transform var(--duration-enter) var(--ease-soft);
}

.thread-fade-leave-active {
  transition: opacity var(--duration-leave) ease-out;
}

.thread-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.thread-fade-leave-to {
  opacity: 0;
}

.spinner-fade-enter-active,
.spinner-fade-leave-active {
  transition: opacity 0.2s ease-out;
}

.spinner-fade-enter-from,
.spinner-fade-leave-to {
  opacity: 0;
}

.thread__scroll {
  flex: 1;
  overflow-y: auto;
}

.thread__column {
  max-width: 48rem;
  margin: 0 auto;
  padding: 1.5rem 1.25rem;
}

.thread__state {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: var(--color-text-secondary);
}

.thread__list {
  flex: 1;
  min-height: 0;
}

/* 消息列居中、限宽，和原型一致 */
.thread__list :deep(.elx-bubble-list__list) {
  padding: 1.5rem max(1.25rem, calc((100% - 48rem) / 2));
}

.text-user {
  white-space: pre-wrap;
  line-height: 1.7;
}

.md {
  background: transparent;
}

.md :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.md :deep(.md-editor-preview) {
  font-size: 0.95rem;
  line-height: 1.8;
  color: var(--color-text-primary);
}

/* Tailwind 的 preflight 会清掉列表符号，回复里的步骤列表要恢复序号/圆点 */
.md :deep(ol) {
  list-style: decimal;
}

.md :deep(ul) {
  list-style: disc;
}

.failed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.8rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, #dc2626 12%, transparent);
  color: #dc2626;
  font-size: 0.9rem;
}

.failed .btn {
  padding: 0.3rem 0.7rem;
  font-size: 0.82rem;
}

.stopped {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.2rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.stopped svg {
  width: 0.9rem;
  height: 0.9rem;
}

.actions {
  display: flex;
  gap: 0.15rem;
}

.actions button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.45rem;
  border: 0;
  border-radius: 0.35rem;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
}

.actions button:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.actions svg {
  width: 0.9rem;
  height: 0.9rem;
}

/* ── 输入区 ── */
.composer {
  padding: 0.5rem 1.25rem 1.1rem;
}

.composer__sender {
  max-width: 48rem;
  margin: 0 auto;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* ── 窄屏：侧栏变抽屉 ── */
@media (max-width: 768px) {
  .chat__side {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    margin-left: 0 !important;
    box-shadow: var(--shadow-lg);
    transform: translateX(-100%);
    transition: transform var(--duration-panel) var(--ease-soft);
  }

  .chat--drawer .chat__side {
    transform: none;
  }

  /* 遮罩常驻、靠透明度渐显渐隐，不再「啪」地出现 */
  .chat__scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 25;
    background: rgba(10, 8, 6, 0.35);
    opacity: 0;
    visibility: hidden;
    transition:
      opacity var(--duration-panel) var(--ease-soft),
      visibility 0s linear var(--duration-panel);
  }

  .chat--drawer .chat__scrim {
    opacity: 1;
    visibility: visible;
    transition: opacity var(--duration-panel) var(--ease-soft);
  }

  .bar__open {
    display: inline-grid;
  }

  .bar__hint {
    display: none;
  }

  .composer {
    padding-inline: 0.75rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat__side,
  .chat__scrim,
  .thread__view,
  .thread-fade-enter-active,
  .thread-fade-leave-active {
    transition: none !important;
  }

  .thread-fade-enter-from {
    transform: none;
  }
}
</style>
