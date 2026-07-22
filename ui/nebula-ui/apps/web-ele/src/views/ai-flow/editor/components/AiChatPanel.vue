<script lang="ts" setup>
/**
 * AI 对话分栏面板：编辑器最右侧的独立栏位，与节点配置面板并列成"第四栏"。
 *
 * 两种模式：
 * - 普通对话（chat）：照搬独立页 views/ai-flow/chat/index.vue，走 /admin/ai-chat/stream。
 * - 生成流程（copilot）：走 /admin/ai-flow/copilot/stream，由后端流程设计助手在对话中生成流程并落库；
 *   新增 tool_call（工具进度）/ flow（流程产物）/ agent（Agent 产物）事件，flow 事件向上 emit 供画布回显。
 *
 * 折叠 / 拖拽宽度 / tab 头由外层 SplitLayout（EditorSideDock.vue）承载，本组件只做对话内容。
 */
import type { AiChatApi, CopilotApi } from '#/api';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElMessage,
  ElSegmented,
} from 'element-plus';
import { BubbleList, useXStream, XSender } from 'vue-element-plus-x';

import { chatStreamApi, copilotStreamApi } from '#/api';

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

/** 对话模式 */
type ChatMode = 'chat' | 'copilot';

const senderRef = ref<InstanceType<typeof XSender>>();
const bubbles = ref<ChatBubble[]>([]);
const abortController = ref<AbortController>();
const mode = ref<ChatMode>('chat');

const modeOptions = [
  { label: '普通对话', value: 'chat' },
  { label: '生成流程', value: 'copilot' },
];

const { startStream, cancel, data, error, isLoading } = useXStream();

let bubbleKey = 0;

/** 是否已有对话 */
const hasConversation = computed(() => bubbles.value.length > 0);

/** 输入框占位提示随模式变化 */
const placeholder = computed(() =>
  mode.value === 'copilot'
    ? '描述你想要的流程，如「先总结再翻译」，Enter 发送'
    : '输入消息，Enter 发送',
);

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

/**
 * 消费 useXStream 累积的 SSE 事件。
 * 通用事件：delta（{content}）/ error（{message}）/ done。
 * copilot 事件：tool_call / flow / agent（仅生成流程模式出现）。
 * delta 片段追加到“当前这条助手文本气泡”（最后一条非 meta 的 assistant 气泡）。
 */
watch(data, (events) => {
  // eslint-disable-next-line no-console
  console.log('[copilot watch] raw events=', JSON.stringify(events.map((e) => ({ keys: Object.keys(e ?? {}), event: (e as any)?.event, hasData: !!(e as any)?.data }))));
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
  // eslint-disable-next-line no-console
  console.log('[copilot watch] events=', events.length, 'content.len=', content.length, 'bubbles=', JSON.stringify(bubbles.value.map((b) => ({ role: b.role, meta: b.meta, len: b.content.length }))));
  if (content) {
    // 找到当前这轮的助手文本气泡（最后一条非 meta 的 assistant 气泡）
    const target = [...bubbles.value].reverse().find((b) => b.role === 'assistant' && !b.meta);
    // eslint-disable-next-line no-console
    console.log('[copilot watch] target found=', !!target, 'content=', content.slice(0, 30));
    if (target) {
      target.content = content;
      target.loading = false;
    }
  }
});

/** 流式请求本身失败（网络/鉴权等） */
watch(error, (err) => {
  // eslint-disable-next-line no-console
  console.log('[copilot error watch] err=', err, 'message=', (err as any)?.message);
  if (!err) {
    return;
  }
  const target = [...bubbles.value].reverse().find((b) => b.role === 'assistant' && !b.meta);
  if (target && !target.content) {
    target.content = `请求失败：${err.message}`;
    target.loading = false;
  }
});

/** 发送消息：把历史消息与本轮提示词提交给对应模式的流式端点 */
async function onSubmit() {
  const text = senderRef.value?.getModelValue?.()?.text?.trim();
  if (!text) {
    return;
  }
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }

  // 取当前对话作为历史上下文（排除进度/系统气泡，只保留真实对话文本）
  const history: AiChatApi.ChatMessage[] = bubbles.value
    .filter((item) => item.content && !item.meta)
    .map((item) => ({ role: item.role, content: item.content }));

  bubbles.value.push({
    key: bubbleKey++,
    role: 'user',
    content: text,
    placement: 'end',
    loading: false,
  });
  bubbles.value.push({
    key: bubbleKey++,
    role: 'assistant',
    content: '',
    placement: 'start',
    loading: true,
  });
  senderRef.value?.onClear?.();
  await nextTick();

  abortController.value = new AbortController();
  try {
    const readableStream =
      mode.value === 'copilot'
        ? await copilotStreamApi({ prompt: text, messages: history }, abortController.value.signal)
        : await chatStreamApi({ prompt: text, messages: history }, abortController.value.signal);
    // eslint-disable-next-line no-console
    console.log('[copilot submit] mode=', mode.value, 'stream=', readableStream, 'locked=', (readableStream as any)?.locked, 'isReadableStream=', readableStream instanceof ReadableStream);
    await startStream({ readableStream });
    // eslint-disable-next-line no-console
    console.log('[copilot submit] startStream 结束');
  } catch (error_: any) {
    // eslint-disable-next-line no-console
    console.log('[copilot submit] catch 到异常=', error_, 'message=', error_?.message);
    const target = [...bubbles.value].reverse().find((b) => b.role === 'assistant' && !b.meta);
    if (target) {
      target.content = `请求失败：${error_?.message ?? '未知错误'}`;
      target.loading = false;
    }
  }
}

/** 中断当前回复 */
function onCancel() {
  cancel();
  abortController.value?.abort();
  const target = [...bubbles.value].reverse().find((b) => b.role === 'assistant' && !b.meta);
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
  bubbles.value = [];
}
</script>

<template>
  <!--
    折叠/拖宽/tab 头由外层 SplitLayout 承载，本组件只渲染对话内容，撑满所在面板（.dock-pane）。
    头部：模式切换 + 清空对话。
  -->
  <div class="chat-panel">
    <div class="panel-header">
      <ElSegmented
        v-model="mode"
        :options="modeOptions"
        :disabled="isLoading"
        size="small"
        class="mode-switch"
      />
      <ElButton
        :disabled="!hasConversation || isLoading"
        link
        type="primary"
        @click="onClearChat"
      >
        清空对话
      </ElButton>
    </div>

    <div class="chat-body">
      <ElEmpty
        v-if="!hasConversation"
        :description="
          mode === 'copilot'
            ? '描述需求，我来帮你生成流程'
            : '开始你的第一句对话吧'
        "
        class="chat-empty"
      />
      <BubbleList v-else :list="bubbles" max-height="100%" />
    </div>

    <div class="chat-footer">
      <XSender
        ref="senderRef"
        :loading="isLoading"
        :placeholder="placeholder"
        submit-type="enter"
        @cancel="onCancel"
        @submit="onSubmit"
      />
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

/* ---- 头部 ---- */
.panel-header {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.mode-switch {
  flex-shrink: 0;
}

/* ---- 对话内容区 ---- */
.chat-body {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow: hidden;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-footer {
  flex-shrink: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
