<script setup lang="ts">
/**
 * 导入浏览器书签，三步：选择文件 → 解析 → 结果。
 * 后端同步解析，几百条通常 1–3 秒；超过 1 秒才显示进度，避免一闪而过。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { importChromeBookmarks } from '../../../api/space'
import type { ImportTask } from '../../../types/space'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; imported: [task: ImportTask]; viewRecords: [] }>()

/** 后端上限 20 MB */
const MAX_SIZE = 20 * 1024 * 1024

type Step = 'pick' | 'running' | 'done' | 'failed'
const step = ref<Step>('pick')
const file = ref<File | null>(null)
const pickError = ref('')
const failMessage = ref('')
const task = ref<ImportTask | null>(null)
const dragging = ref(false)
const showProgress = ref(false)
const elapsed = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)

let progressTimer: ReturnType<typeof setTimeout> | undefined
let startedAt = 0

watch(
  () => props.open,
  (open) => {
    if (open && step.value !== 'running') reset()
  },
)

const reset = () => {
  step.value = 'pick'
  file.value = null
  pickError.value = ''
  failMessage.value = ''
  task.value = null
}

const sizeText = (size: number) => (size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(size / 1024))} KB`)

const accept = (candidate: File | undefined) => {
  if (!candidate) return
  pickError.value = ''
  if (!/\.html?$/i.test(candidate.name)) {
    pickError.value = '请选择浏览器「导出书签」得到的 .html 文件'
    return
  }
  if (candidate.size > MAX_SIZE) {
    pickError.value = `文件 ${sizeText(candidate.size)}，超过 20 MB 上限`
    return
  }
  file.value = candidate
  run()
}

const onPick = (event: Event) => {
  const input = event.target as HTMLInputElement
  accept(input.files?.[0])
  input.value = ''
}

const onDrop = (event: DragEvent) => {
  dragging.value = false
  accept(event.dataTransfer?.files?.[0])
}

const run = async () => {
  if (!file.value) return
  step.value = 'running'
  showProgress.value = false
  startedAt = performance.now()
  progressTimer = setTimeout(() => (showProgress.value = true), 1000)
  try {
    task.value = await importChromeBookmarks(file.value)
    elapsed.value = (performance.now() - startedAt) / 1000
    step.value = task.value?.status === 3 ? 'failed' : 'done'
    failMessage.value = task.value?.errorMsg ?? ''
    if (task.value) emit('imported', task.value)
  } catch (error) {
    failMessage.value = error instanceof Error ? error.message : '导入失败'
    step.value = 'failed'
  } finally {
    clearTimeout(progressTimer)
  }
}

const stats = computed(() => {
  const t = task.value
  return [
    { label: '新增书签', value: t?.successCount ?? 0, tone: 'ok' },
    { label: '重复，已跳过', value: t?.duplicateCount ?? 0, tone: 'muted' },
    { label: '无法导入', value: t?.failCount ?? 0, tone: (t?.failCount ?? 0) > 0 ? 'warn' : 'muted' },
  ]
})

onBeforeUnmount(() => clearTimeout(progressTimer))
</script>

<template>
  <BaseDialog :open="open" title="导入浏览器书签" width="36rem" :locked="step === 'running'" @close="emit('close')">
    <ol class="steps" aria-label="导入步骤">
      <li :class="{ on: step === 'pick', done: step !== 'pick' }"><span>1</span>选择文件</li>
      <li :class="{ on: step === 'running', done: step === 'done' || step === 'failed' }"><span>2</span>解析</li>
      <li :class="{ on: step === 'done' || step === 'failed' }"><span>3</span>结果</li>
    </ol>

    <div class="body">
      <!-- 第一步 -->
      <template v-if="step === 'pick'">
        <label
          class="drop"
          :class="{ 'drop--over': dragging, 'drop--error': pickError }"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <Icon icon="lucide:file-up" class="drop__icon" />
          <b>把书签 HTML 拖到这里</b>
          <span>或 <u>点击选择文件</u> · 仅 .html，≤ 20 MB</span>
          <input ref="fileInput" type="file" accept=".html,.htm,text/html" hidden @change="onPick" />
        </label>
        <p v-if="pickError" class="err">{{ pickError }}</p>

        <details class="howto">
          <summary>怎么从浏览器导出书签？</summary>
          <ol>
            <li>Chrome / Edge：地址栏输入 <code>chrome://bookmarks</code>（Edge 为 <code>edge://favorites</code>）</li>
            <li>右上角 <b>⋮</b> → <b>导出书签</b></li>
            <li>把得到的 <code>bookmarks_*.html</code> 拖进上面的框</li>
          </ol>
          <p>Firefox：书签 → 管理书签 → 导入和备份 → 导出书签到 HTML。</p>
        </details>
        <p class="note">
          会保留浏览器里的文件夹结构；顶层文件夹与已有顶层目录同名时直接并入（再次导入「书签栏」不会多出一棵树）。
          网址重复的书签会跳过。
        </p>
      </template>

      <!-- 第二步 -->
      <div v-else-if="step === 'running'" class="running">
        <p class="file">
          <Icon icon="lucide:file-code-2" />
          <span>{{ file?.name }}</span>
          <small>{{ file ? sizeText(file.size) : '' }}</small>
        </p>
        <template v-if="showProgress">
          <div class="bar" role="progressbar" aria-label="导入进度"><i /></div>
          <p class="muted">正在解析并写入目录与书签，大文件可能需要几十秒，请不要关闭页面…</p>
        </template>
      </div>

      <!-- 第三步：成功 -->
      <div v-else-if="step === 'done'" class="result">
        <p class="result__head">
          <Icon icon="lucide:circle-check" class="ok" />
          <b>导入完成</b>
          <small>用时 {{ elapsed.toFixed(1) }} 秒 · 共 {{ task?.totalCount ?? 0 }} 条</small>
        </p>
        <div class="stats">
          <div v-for="item in stats" :key="item.label" class="stat" :class="`stat--${item.tone}`">
            <b>{{ item.value }}</b>
            <span>{{ item.label }}</span>
          </div>
        </div>
        <p class="note">
          「重复」按规范化网址判定（忽略协议与域名大小写、#锚点），不是错误。
          <template v-if="(task?.failCount ?? 0) > 0">「无法导入」通常是书签小工具（javascript:）、浏览器内部页或本地文件。</template>
        </p>
      </div>

      <!-- 第三步：失败 -->
      <div v-else class="result">
        <p class="result__head">
          <Icon icon="lucide:circle-x" class="bad" />
          <b>导入失败</b>
        </p>
        <p class="fail">{{ failMessage || '服务端没有返回原因' }}</p>
        <p class="note">
          如果文件是浏览器「另存为网页」得到的，请改用书签管理器里的「导出书签」。
        </p>
      </div>
    </div>

    <template #footer>
      <template v-if="step === 'pick'">
        <button class="btn btn--quiet foot-left" type="button" @click="emit('viewRecords')">导入记录</button>
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="button" @click="fileInput?.click()"><Icon icon="lucide:upload" />选择文件</button>
      </template>
      <template v-else-if="step === 'running'">
        <button class="btn btn--primary" type="button" disabled>
          <Icon icon="lucide:loader-circle" class="spin" />导入中
        </button>
      </template>
      <template v-else>
        <button class="btn btn--ghost" type="button" @click="reset">再导入一个</button>
        <button class="btn btn--primary" type="button" @click="emit('close')">{{ step === 'done' ? '查看书签' : '关闭' }}</button>
      </template>
    </template>
  </BaseDialog>
</template>

<style scoped>
.steps {
  display: flex;
  gap: 1.25rem;
  margin: 0;
  padding: 0.9rem 1.35rem 0;
  list-style: none;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.steps li {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.steps span {
  display: grid;
  place-items: center;
  width: 1.35rem;
  height: 1.35rem;
  border-radius: 50%;
  background: var(--color-bg-soft);
  font-size: 0.75rem;
  font-weight: 700;
}

.steps .on {
  color: var(--color-text-primary);
  font-weight: 700;
}

.steps .on span {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.steps .done span {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem 1.35rem 0.25rem;
}

.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 1.8rem 1rem;
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-canvas);
  text-align: center;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.drop:hover,
.drop--over {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.drop--error {
  border-color: var(--color-danger);
}

.drop__icon {
  width: 2rem;
  height: 2rem;
  color: var(--color-brand);
}

.drop span {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.err {
  font-size: 0.84rem;
  color: var(--color-danger);
}

.howto {
  font-size: 0.86rem;
}

.howto summary {
  color: var(--color-brand);
  font-weight: 600;
  cursor: pointer;
}

.howto ol {
  margin: 0.5rem 0 0.35rem;
  padding-left: 1.3rem;
  line-height: 1.9;
}

.howto p {
  color: var(--color-text-secondary);
}

code {
  padding: 0.05em 0.35em;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
  font-family: var(--font-mono);
  font-size: 0.85em;
}

.note {
  font-size: 0.8rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.running {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0 1rem;
}

.file {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}

.file span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file small {
  font-weight: 400;
  color: var(--color-text-secondary);
}

.bar {
  position: relative;
  height: 0.4rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.bar i {
  position: absolute;
  inset: 0 auto 0 0;
  width: 35%;
  border-radius: inherit;
  background: var(--color-brand);
  animation: slide 1.2s ease-in-out infinite;
}

@keyframes slide {
  from {
    left: -35%;
  }
  to {
    left: 100%;
  }
}

.muted {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.result {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.result__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  font-size: 1.02rem;
}

.result__head small {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.ok {
  width: 1.3rem;
  height: 1.3rem;
  color: var(--color-accent);
}

.bad {
  width: 1.3rem;
  height: 1.3rem;
  color: var(--color-danger);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.7rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.stat b {
  font-size: 1.4rem;
  font-weight: 800;
}

.stat span {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.stat--ok b {
  color: var(--color-accent-text);
}

.stat--warn b {
  color: var(--color-danger);
}

.fail {
  padding: 0.7rem 0.85rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
  color: var(--color-danger);
  font-size: 0.88rem;
  line-height: 1.6;
}

.foot-left {
  margin-right: auto;
}

@media (prefers-reduced-motion: reduce) {
  .bar i {
    animation: none;
    width: 100%;
    opacity: 0.4;
  }
}
</style>
