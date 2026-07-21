<script lang="ts" setup>
/**
 * AI 对话分栏面板：编辑器最右侧的独立栏位，与节点配置面板并列成"第四栏"。
 *
 * 对话逻辑照搬独立页 views/ai-flow/chat/index.vue（BubbleList + XSender +
 * useXStream 流式），去掉页面级 <Page> 外壳，换成与 NodeConfigPanel 同构的
 * 折叠条 + 可拖拽宽度 + 头部三段式布局，作为普通 flex 兄弟节点占位而非遮挡。
 *
 * 与配置面板各用独立的 localStorage key 记宽度/折叠态，互不干扰；两栏可同时展开。
 */
import type { AiChatApi } from '#/api';

import { computed, nextTick, ref, watch } from 'vue';

import { ElButton, ElEmpty, ElMessage } from 'element-plus';
import { BubbleList, useXStream, XSender } from 'vue-element-plus-x';

import { chatStreamApi } from '#/api';

defineOptions({ name: 'AiChatPanel' });

/** 气泡列表项（对齐 chat/index.vue 的结构） */
interface ChatBubble {
  key: number;
  role: 'assistant' | 'user';
  content: string;
  placement: 'end' | 'start';
  loading: boolean;
}

const senderRef = ref<InstanceType<typeof XSender>>();
const bubbles = ref<ChatBubble[]>([]);
const abortController = ref<AbortController>();

const { startStream, cancel, data, error, isLoading } = useXStream();

let bubbleKey = 0;

/** 是否已有对话 */
const hasConversation = computed(() => bubbles.value.length > 0);

/**
 * 消费 useXStream 累积的 SSE 事件，把 delta 片段追加到最后一条助手气泡。
 * 后端事件约定：delta（{content}）/ done（完整响应）/ error（{message}）。
 */
watch(data, (events) => {
  const last = bubbles.value.at(-1);
  if (!last || last.role !== 'assistant') {
    return;
  }
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
      // done 事件携带聚合结果，内容已由 delta 拼出，此处无需重复追加
      default: {
        break;
      }
    }
  }
  if (content) {
    last.content = content;
    last.loading = false;
  }
});

/** 流式请求本身失败（网络/鉴权等） */
watch(error, (err) => {
  if (!err) {
    return;
  }
  const last = bubbles.value.at(-1);
  if (last && last.role === 'assistant' && !last.content) {
    last.content = `请求失败：${err.message}`;
    last.loading = false;
  }
});

/** 发送消息：把历史消息与本轮提示词提交给流式端点 */
async function onSubmit() {
  const text = senderRef.value?.getModelValue?.()?.text?.trim();
  if (!text) {
    return;
  }
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }

  // 取当前对话作为历史上下文（不含本轮提示词，后端会追加）
  const history: AiChatApi.ChatMessage[] = bubbles.value
    .filter((item) => item.content)
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
    const readableStream = await chatStreamApi(
      { prompt: text, messages: history },
      abortController.value.signal,
    );
    await startStream({ readableStream });
  } catch (error_: any) {
    const last = bubbles.value.at(-1);
    if (last && last.role === 'assistant') {
      last.content = `请求失败：${error_?.message ?? '未知错误'}`;
      last.loading = false;
    }
  }
}

/** 中断当前回复 */
function onCancel() {
  cancel();
  abortController.value?.abort();
  const last = bubbles.value.at(-1);
  if (last && last.role === 'assistant') {
    last.loading = false;
    if (!last.content) {
      last.content = '已取消';
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

// 折叠 / 拖拽宽度 / 显隐已上交外层 SplitLayout（见 EditorSideDock.vue），
// 本组件只做纯对话内容。
</script>

<template>
  <!--
    折叠/拖宽/tab 头由外层 SplitLayout 承载，本组件只渲染对话内容，
    撑满所在面板（.dock-pane）。头部仅留「清空对话」操作。
  -->
  <div class="chat-panel">
    <div class="panel-header">
      <span class="chat-title">AI 对话</span>
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
        description="开始你的第一句对话吧"
        class="chat-empty"
      />
      <BubbleList v-else :list="bubbles" max-height="100%" />
    </div>

    <div class="chat-footer">
      <XSender
        ref="senderRef"
        :loading="isLoading"
        placeholder="输入消息，Enter 发送"
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
 * 折叠/拖宽/左边框/tab 头都归 SplitLayout 与 EditorSideDock，这里不再自绘。
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
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.chat-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
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
