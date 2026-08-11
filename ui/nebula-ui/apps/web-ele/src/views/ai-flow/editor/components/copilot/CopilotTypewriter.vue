<script lang="ts" setup>
/**
 * 打字机渲染器：按 element-plus-x Typewriter 的语义实现（content / isMarkdown /
 * typing{step,interval,suffix} / isFog + interrupt/continue/restart/destroy 与
 * renderedContent/isTyping/progress，事件 start/writing/finish）。
 *
 * 为什么自研而不是直接用组件：本仓装的 vue-element-plus-x 为 2.0.3，该版本已移除
 * Typewriter（types/components 下只有 Bubble/BubbleList/Thinking/ThoughtChain/XSender…），
 * Markdown 渲染改由独立包 x-markdown-vue 承担。故此处复刻其 API 与观感，
 * 底层 markdown 走 MarkdownRenderer，避免为一个组件引入第二套 markdown 实现。
 *
 * 与旧实现的关键差异：打字状态收敛在组件内（旧版把 setInterval、缓冲文本、
 * 气泡引用散在面板里），面板只需把「当前全量文本」喂进来，节奏与追赶由本组件负责。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { MarkdownRenderer } from 'x-markdown-vue';

import 'x-markdown-vue/style';

defineOptions({ name: 'CopilotTypewriter' });

const props = withDefaults(
  defineProps<{
    /** 目标全量文本（流式期间持续增长） */
    content?: string;
    /** 深色主题（透传给代码高亮） */
    isDark?: boolean;
    /** 打字中在尾部叠一层渐隐雾化，弱化字符跳变的生硬感 */
    isFog?: boolean;
    /** 是否按 markdown 渲染 */
    isMarkdown?: boolean;
    /** 是否启用打字机；false 时直接全量呈现 */
    typing?: boolean | TypingOptions;
  }>(),
  {
    content: '',
    isMarkdown: false,
    isDark: false,
    typing: false,
    isFog: false,
  },
);

const emit = defineEmits<{
  finish: [];
  start: [];
  writing: [];
}>();

interface TypingOptions {
  /** 每次吐出的字符数 */
  step?: number;
  /** 每次吐字的间隔（毫秒） */
  interval?: number;
  /** 光标字符 */
  suffix?: string;
}

const DEFAULT_STEP = 2;
const DEFAULT_INTERVAL = 30;
/** 落后超过该字符数时进入追赶模式，避免长回复拖几十秒 */
const CATCH_UP_THRESHOLD = 240;
/** 追赶模式下期望在约这么多帧内吐完积压 */
const CATCH_UP_FRAMES = 80;

/** 已渲染出的字符数 */
const shownLength = ref(0);
const isTyping = ref(false);
/** interrupt() 置真，continue() 复位；为真时定时器不推进 */
const paused = ref(false);

let timer: ReturnType<typeof setInterval> | undefined;

const typingConfig = computed(() => {
  if (!props.typing) return undefined;
  const raw = typeof props.typing === 'object' ? props.typing : {};
  return {
    step: raw.step ?? DEFAULT_STEP,
    interval: raw.interval ?? DEFAULT_INTERVAL,
    suffix: raw.suffix ?? '',
  };
});

/** 实际呈现的文本：非打字模式直接全量 */
const renderedContent = computed(() =>
  typingConfig.value ? props.content.slice(0, shownLength.value) : props.content,
);

const progress = computed(() => {
  const total = props.content.length;
  if (total === 0) return 100;
  return Math.min(100, Math.round((shownLength.value / total) * 100));
});

/** 光标：仅在打字进行中显示，收尾即隐藏 */
const showCursor = computed(
  () => isTyping.value && !!typingConfig.value?.suffix,
);

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
}

function settle() {
  stopTimer();
  if (isTyping.value) {
    isTyping.value = false;
    emit('finish');
  }
}

function tick() {
  if (paused.value) return;
  const config = typingConfig.value;
  if (!config) return settle();

  const target = props.content.length;
  if (shownLength.value >= target) return settle();

  const lag = target - shownLength.value;
  // 积压过多时按比例加速，保证长文本也能在有限时间内追平
  const step =
    lag > CATCH_UP_THRESHOLD ? Math.ceil(lag / CATCH_UP_FRAMES) : config.step;
  shownLength.value = Math.min(target, shownLength.value + step);
  emit('writing');

  if (shownLength.value >= target) settle();
}

function ensureTimer() {
  const config = typingConfig.value;
  if (!config || timer) return;
  if (!isTyping.value) {
    isTyping.value = true;
    emit('start');
  }
  timer = setInterval(tick, config.interval);
}

watch(
  () => props.content,
  (next, prev) => {
    if (!typingConfig.value) {
      shownLength.value = next.length;
      return;
    }
    // 内容被换掉（新一轮对话复用同一实例）而非增量追加：从头打字
    if (prev && !next.startsWith(prev)) shownLength.value = 0;
    if (shownLength.value > next.length) shownLength.value = next.length;
    if (shownLength.value < next.length) ensureTimer();
  },
  { immediate: true },
);

// 运行中切换 typing 开关：关掉时立即全量落地，避免半截文本卡住
watch(typingConfig, (config) => {
  if (config) return;
  stopTimer();
  shownLength.value = props.content.length;
  if (isTyping.value) {
    isTyping.value = false;
    emit('finish');
  }
});

onBeforeUnmount(stopTimer);

/** 暂停打字（已吐出的保留） */
function interrupt() {
  paused.value = true;
}

/** 从暂停处继续 */
function resume() {
  if (!paused.value) return;
  paused.value = false;
  if (shownLength.value < props.content.length) ensureTimer();
}

/** 从头重放 */
function restart() {
  stopTimer();
  paused.value = false;
  shownLength.value = 0;
  isTyping.value = false;
  if (props.content) ensureTimer();
}

/** 立即停止并把全部文本落地（用于「取消回复」保留已收内容） */
function finish() {
  stopTimer();
  paused.value = false;
  shownLength.value = props.content.length;
  if (isTyping.value) {
    isTyping.value = false;
    emit('finish');
  }
}

function destroy() {
  stopTimer();
  paused.value = false;
  isTyping.value = false;
}

defineExpose({
  interrupt,
  // `continue` 是保留字，无法作为方法名声明，别名导出保持语义一致
  continue: resume,
  restart,
  finish,
  destroy,
  renderedContent,
  isTyping,
  progress,
});
</script>

<template>
  <div class="copilot-typewriter" :class="{ 'is-typing': isTyping }">
    <MarkdownRenderer
      v-if="isMarkdown"
      :markdown="renderedContent"
      :is-dark="isDark"
      class="typewriter-markdown"
    />
    <span v-else class="typewriter-plain">{{ renderedContent }}</span>
    <span v-if="showCursor" class="typewriter-cursor" aria-hidden="true">
      {{ typingConfig?.suffix }}
    </span>
    <span
      v-if="isFog && isTyping"
      class="typewriter-fog"
      aria-hidden="true"
    ></span>
  </div>
</template>

<style scoped>
.copilot-typewriter {
  position: relative;
  min-width: 0;
}

/* 打字期间尾部渐隐，缓和逐字跳变 */
.typewriter-fog {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 5em;
  height: 1.6em;
  pointer-events: none;
  background: linear-gradient(
    90deg,
    transparent,
    var(--el-bg-color-overlay, var(--el-bg-color))
  );
  opacity: 0.85;
}

.typewriter-plain {
  white-space: pre-wrap;
  word-break: break-word;
}

.typewriter-cursor {
  display: inline-block;
  margin-left: 1px;
  color: var(--el-color-primary);
  animation: typewriter-blink 1s step-end infinite;
}

@keyframes typewriter-blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

/* 气泡内 markdown：去掉首尾外边距，避免上下留白过大 */
.typewriter-markdown :deep(> :first-child) {
  margin-top: 0;
}

.typewriter-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

@media (prefers-reduced-motion: reduce) {
  .typewriter-cursor {
    animation: none;
  }
}
</style>
