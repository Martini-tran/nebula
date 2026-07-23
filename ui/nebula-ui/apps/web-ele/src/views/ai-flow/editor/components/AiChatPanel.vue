<script lang="ts" setup>
/**
 * AI 对话分栏面板：编辑器最右侧的独立栏位，与节点配置面板并列成"第四栏"。
 *
 * 统一走 /admin/ai-flow/copilot/stream：纯聊天时后端只回 delta 文本，
 * 需要生成流程时由模型自主调工具，在对话中生成流程并落库；
 * tool_call（工具进度）/ flow（流程产物）/ agent（Agent 产物）事件随流下发，
 * flow 事件向上 emit 供画布回显。
 *
 * 折叠 / 拖拽宽度 / tab 头由外层 SplitLayout（EditorSideDock.vue）承载，本组件只做对话内容。
 */
import type { CopilotApi } from '#/api';

import { computed, nextTick, ref, watch } from 'vue';
import { BubbleList, useXStream, XSender } from 'vue-element-plus-x';

import { Eraser } from '@nebula/icons';
import { usePreferences } from '@nebula/preferences';

import { ElButton, ElEmpty, ElMessage } from 'element-plus';
// XMarkdown 自 element-plus-x 2.x 起独立为 x-markdown-vue 包（MarkdownRenderer）
import { MarkdownRenderer } from 'x-markdown-vue';

import { copilotStreamApi } from '#/api';

import 'x-markdown-vue/style';

defineOptions({ name: 'AiChatPanel' });

/** flow 事件向上抛出，供编辑器主壳把生成的流程回显到画布 */
const emit = defineEmits<{
  flowGenerated: [payload: CopilotApi.FlowEvent];
}>();

/** 气泡列表项（对齐 chat/index.vue 的结构，扩展进度气泡） */
interface ChatBubble {
  key: number;
  role: 'assistant' | 'user';
  content: string;
  placement: 'end' | 'start';
  loading: boolean;
  /** 进度/系统气泡（工具调用、流程产物提示），不参与下一轮 history */
  meta?: boolean;
}

const senderRef = ref<InstanceType<typeof XSender>>();
const bubbles = ref<ChatBubble[]>([]);
const abortController = ref<AbortController>();

/** markdown 渲染跟随全局深浅主题（代码高亮双主题切换） */
const { isDark } = usePreferences();

const { startStream, cancel, data, error, isLoading } = useXStream();

let bubbleKey = 0;

/** 是否已有对话 */
const hasConversation = computed(() => bubbles.value.length > 0);

/** 追加一条进度/系统气泡（工具调用、流程产物提示） */
function pushMetaBubble(content: string) {
  bubbles.value.push({
    key: bubbleKey++,
    role: 'assistant',
    content,
    placement: 'start',
    loading: false,
    meta: true,
  });
}

/** 处理 copilot 专属事件；返回 true 表示已消费 */
function handleCopilotEvent(eventName: string, payload: Record<string, any>): boolean {
  switch (eventName) {
    case 'agent': {
      pushMetaBubble(
        `🤖 已派生 Agent「${payload.name ?? payload.agentCode}」（agentCode：${payload.agentCode}）`,
      );
      return true;
    }
    case 'flow': {
      pushMetaBubble(
        `✅ 已生成流程「${payload.name ?? payload.flowCode}」（flowCode：${payload.flowCode}${
          payload.nodeCount ? `，${payload.nodeCount} 个节点` : ''
        }）`,
      );
      emit('flowGenerated', payload as CopilotApi.FlowEvent);
      return true;
    }
    case 'tool_call': {
      if (payload.status === 'start') {
        pushMetaBubble(`🔧 正在调用工具：${payload.name}…`);
      } else if (payload.status === 'done') {
        const last = bubbles.value.at(-1);
        const tip = `${payload.success === false ? '⚠️' : '✓'} 工具 ${payload.name} 已${
          payload.success === false ? '失败' : '完成'
        }`;
        // 覆盖对应的“正在调用”提示气泡，避免刷屏
        if (last?.meta && last.content.startsWith('🔧')) {
          last.content = tip;
        } else {
          pushMetaBubble(tip);
        }
      }
      return true;
    }
    default: {
      return false;
    }
  }
}

// ---------------- 打字机缓冲 ----------------
/**
 * 流式全量文本先进缓冲，再按固定节奏吐进气泡（element-plus-x v1 Typewriter 的
 * step/interval 语义）。后端 delta 常大段到达（网关缓冲/工具执行后一次吐出），
 * 直接赋值没有打字观感；缓冲后无论 chunk 节奏如何都逐字平滑输出。
 */
const TYPE_STEP = 2;
const TYPE_INTERVAL = 30;
let typeTimer: ReturnType<typeof setInterval> | undefined;
let typeTarget = '';

function stopTypewriter() {
  if (typeTimer) {
    clearInterval(typeTimer);
    typeTimer = undefined;
  }
}

/** 更新打字目标并确保定时器在跑；bubble 引用整条流内不变 */
function typewriterTo(bubble: ChatBubble, full: string) {
  typeTarget = full;
  if (typeTimer) {
    return;
  }
  typeTimer = setInterval(() => {
    const shown = bubble.content.length;
    if (shown >= typeTarget.length) {
      stopTypewriter();
      return;
    }
    // 落后太多时自适应加速追赶（长回复/delta 洪峰不至于拖几十秒）
    const lag = typeTarget.length - shown;
    const step = lag > 240 ? Math.ceil(lag / 80) : TYPE_STEP;
    bubble.content = typeTarget.slice(0, shown + step);
    bubble.loading = false;
  }, TYPE_INTERVAL);
}

/**
 * 消费 useXStream 累积的 SSE 事件。
 * 通用事件：delta（{content}）/ error（{message}）/ done。
 * copilot 事件：tool_call / flow / agent（模型调工具生成流程时出现）。
 * delta 片段追加到“当前这条助手文本气泡”（最后一条非 meta 的 assistant 气泡）。
 * 注意：useXStream 内部用 data.value.push() 原地追加，ref 本身不重新赋值，
 * 必须 deep 侦听才能在流式增量到达时触发。
 */
watch(data, (events) => {
  let content = '';
  for (const event of events) {
    if (!event?.data) {
      continue;
    }
    let payload: Record<string, any>;
    try {
      payload = JSON.parse(event.data);
    } catch {
      continue;
    }
    switch (event.event) {
      case 'delta': {
        content += payload.content ?? '';
        break;
      }
      case 'error': {
        ElMessage.error(payload.message ?? '对话失败');
        break;
      }
      default: {
        // copilot 专属事件；done 由内容累积覆盖，无需额外处理
        handleCopilotEvent(event.event ?? '', payload);
        break;
      }
    }
  }
  if (content) {
    // 找到当前这轮的助手文本气泡（最后一条非 meta 的 assistant 气泡），走打字机吐字
    const target = bubbles.value.findLast((b) => b.role === 'assistant' && !b.meta);
    if (target) {
      typewriterTo(target, content);
    }
  }
}, { deep: true });

/** 流式请求本身失败（网络/鉴权等） */
watch(error, (err) => {
  if (!err) {
    return;
  }
  stopTypewriter();
  const target = bubbles.value.findLast((b) => b.role === 'assistant' && !b.meta);
  if (target && !target.content) {
    target.content = `请求失败：${err.message}`;
    target.loading = false;
  }
});

/** 发送消息：把历史消息与本轮提示词提交给 copilot 流式端点 */
async function onSubmit() {
  const text = senderRef.value?.getModelValue?.()?.text?.trim();
  if (!text) {
    return;
  }
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }

  // 新一轮开始：终止上一轮可能残留的打字机
  stopTypewriter();
  typeTarget = '';

  // 取当前对话作为历史上下文（排除进度/系统气泡，只保留真实对话文本）
  const history: CopilotApi.ChatMessage[] = bubbles.value
    .filter((item) => item.content && !item.meta)
    .map((item) => ({ role: item.role, content: item.content }));

  bubbles.value.push(
    {
      key: bubbleKey++,
      role: 'user',
      content: text,
      placement: 'end',
      loading: false,
    },
    {
      key: bubbleKey++,
      role: 'assistant',
      content: '',
      placement: 'start',
      loading: true,
    },
  );
  // XSender 暴露的清空方法名为 clear（onClear 是其内部函数名，未导出）
  senderRef.value?.clear?.();
  await nextTick();

  abortController.value = new AbortController();
  try {
    const readableStream = await copilotStreamApi(
      { prompt: text, messages: history },
      abortController.value.signal,
    );
    await startStream({ readableStream });
  } catch (error_: any) {
    const target = bubbles.value.findLast((b) => b.role === 'assistant' && !b.meta);
    if (target) {
      target.content = `请求失败：${error_?.message ?? '未知错误'}`;
      target.loading = false;
    }
  }
}

/** 中断当前回复：终止打字机，保留已吐出的部分 */
function onCancel() {
  stopTypewriter();
  cancel();
  abortController.value?.abort();
  const target = bubbles.value.findLast((b) => b.role === 'assistant' && !b.meta);
  if (target) {
    target.loading = false;
    if (!target.content) {
      target.content = '已取消';
    }
  }
}

/** 清空对话 */
function onClearChat() {
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }
  stopTypewriter();
  bubbles.value = [];
}
</script>

<template>
  <!--
    折叠/拖宽/tab 头由外层 SplitLayout 承载（标题也在 tab 头），本组件只渲染对话内容，
    撑满所在面板（.dock-pane）。「清空对话」收进输入框操作行（prefix 插槽），不再单占一行头部。
  -->
  <div class="chat-panel">
    <div class="chat-body">
      <ElEmpty
        v-if="!hasConversation"
        description="聊聊天，或描述需求让我生成流程"
        class="chat-empty"
      />
      <!--
        content 插槽接管气泡内容（loading 态仍由 Bubble 预置的点点动画渲染）：
        助手正文走 MarkdownRenderer（enable-animate 流式打字动画 + 深浅主题代码高亮），
        用户消息与 meta 进度提示保持纯文本。
      -->
      <BubbleList v-else :list="bubbles" max-height="100%">
        <template #content="{ item }">
          <MarkdownRenderer
            v-if="item.role === 'assistant' && !item.meta"
            :markdown="item.content"
            :is-dark="isDark"
            enable-animate
            class="bubble-markdown"
          />
          <span v-else class="bubble-text">{{ item.content }}</span>
        </template>
      </BubbleList>
    </div>

    <!--
      EditorSender（XSender）updown 变体：输入区在上、操作行在下，
      预置按钮随状态切换（发送 / loading 时停止 / clearable 时清空输入），
      prefix 插槽放「清空对话」入口，对齐官方文档推荐布局。
    -->
    <div class="chat-footer">
      <XSender
        ref="senderRef"
        :loading="isLoading"
        clearable
        variant="updown"
        placeholder="输入消息或描述想要的流程，如「先总结再翻译」，Enter 发送"
        submit-type="enter"
        @cancel="onCancel"
        @submit="onSubmit"
      >
        <template #prefix>
          <ElButton
            :disabled="!hasConversation || isLoading"
            link
            size="small"
            @click="onClearChat"
          >
            <Eraser class="prefix-icon" />
            清空对话
          </ElButton>
        </template>
      </XSender>
    </div>
  </div>
</template>

<style scoped>
/**
 * 撑满外层 SplitLayout 面板（.dock-pane）：不写 height:100%，靠 flex item 的
 * align-self:stretch 拿高度、min-height:0 允许压缩、overflow:hidden 兜底。
 */
.chat-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  align-self: stretch;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
}

/* ---- 对话内容区 ---- */
.chat-body {
  flex: 1;
  min-height: 0;
  padding: 12px 12px 4px;
  overflow: hidden;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

/* updown 变体自带描边卡片，不再叠一条分隔线，让输入卡片"浮"在内容区下方 */
.chat-footer {
  flex-shrink: 0;
  padding: 8px 12px 12px;
}

.prefix-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
}

/* 气泡内 markdown：去掉首尾段落外边距，避免气泡上下留白过大 */
.bubble-markdown :deep(> :first-child) {
  margin-top: 0;
}

.bubble-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

/* 用户消息/进度提示保持纯文本换行行为 */
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
