<script lang="ts" setup>
import type { AiChatApi } from '#/api';

import { computed, nextTick, ref, watch } from 'vue';
import { BubbleList, useXStream, XSender } from 'vue-element-plus-x';

import { Page } from '@nebula/common-ui';
import { usePreferences } from '@nebula/preferences';

import { ElButton, ElEmpty, ElMessage } from 'element-plus';
// XMarkdown 自 element-plus-x 2.x 起独立为 x-markdown-vue 包（MarkdownRenderer）
import { MarkdownRenderer } from 'x-markdown-vue';

import { chatStreamSipApi } from '#/api';

import 'x-markdown-vue/style';

defineOptions({ name: 'AiChat' });

/** 气泡列表项 */
interface ChatBubble {
  key: number;
  role: 'assistant' | 'user';
  content: string;
  placement: 'end' | 'start';
  loading: boolean;
}

/** SIP 帧解析后向 useXStream 输出的片段（贴合其 SSEOutput={data,event,id,retry} 形状） */
type SipOutput = Partial<Record<'data' | 'event' | 'id' | 'retry', any>>;

/**
 * 构造 SIP 模式的自定义 TransformStream，接管后端 OpenAI 兼容裸流的解析。
 *
 * 后端帧约定（仅 data: 行、无 event: 事件名，\n\n 分帧）：
 *   data: {"choices":[{"delta":{"content":"片段"}}]}
 *   data: {"error":{"message":"原因"}}
 *   data: [DONE]
 *
 * 相比 useXStream 默认 SSE 解析，这里自行按 \n\n 缓冲分帧、剥离 data: 前缀、解出 OpenAI 增量结构，
 * 规避网关缓冲把单帧切碎、或事件名丢失导致的漏帧/错位。产出对齐 useXStream 的 SSEOutput 形状：
 *   { event:'delta', data:'文本片段' } / { event:'error', data:'原因' } / { event:'done' }。
 *
 * 注意：useXStream 在交给本 transformStream 前已解码为字符串（输入是 string，非 Uint8Array），
 * 故此处只需按 \n\n 缓冲分帧，无需自行 TextDecoder。
 */
function createSipTransformStream(): TransformStream<string, SipOutput> {
  // 跨 chunk 的残帧缓冲：网络分片可能在任意位置切断，需拼接后再按 \n\n 切分
  let buffer = '';

  /** 解析单帧（一段以 \n\n 分隔的 data 块），产出 0~1 个 SSEOutput 片段 */
  const parseFrame = (frame: string): null | SipOutput => {
    // 一帧内可能含多行（如 data: 前有空行），逐行取 data: 负载后拼接
    const dataLines = frame
      .split('\n')
      .map((line) => line.trimStart())
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim());
    if (dataLines.length === 0) {
      return null;
    }
    const raw = dataLines.join('');
    if (!raw) {
      return null;
    }
    if (raw === '[DONE]') {
      return { event: 'done' };
    }
    try {
      const payload = JSON.parse(raw);
      const errMsg = payload?.error?.message;
      if (errMsg) {
        return { event: 'error', data: String(errMsg) };
      }
      const content = payload?.choices?.[0]?.delta?.content;
      return content ? { event: 'delta', data: String(content) } : null;
    } catch {
      // 非法 JSON 帧直接丢弃，不中断整条流
      return null;
    }
  };

  return new TransformStream<string, SipOutput>({
    transform(chunk, controller) {
      buffer += chunk;
      // 以 \n\n 为帧边界；最后一段可能是未收全的残帧，留在 buffer 里等下次拼接
      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';
      for (const frame of frames) {
        const parsed = parseFrame(frame);
        if (parsed) {
          controller.enqueue(parsed);
        }
      }
    },
    flush(controller) {
      // 处理最后的残帧（正常收尾走 [DONE]，此处兜底非规范结束）
      const parsed = parseFrame(buffer);
      if (parsed) {
        controller.enqueue(parsed);
      }
    },
  });
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

/**
 * 消费 useXStream 累积的 SIP 片段（已由 createSipTransformStream 解析为 {event,data} 形状），
 * 把 delta 文本拼接到最后一条助手气泡；error 弹出提示，done 仅收尾。
 *
 * data 是「自流开始以来的全部片段」累积数组，故每次全量重算内容，避免重复追加。
 * 注意：useXStream 内部用 data.value.push() 原地追加，ref 本身不重新赋值，
 * 必须 deep 侦听才能在流式增量到达时触发（否则整条流只在开始时触发一次空数组）。
 */
watch(data, (chunks: SipOutput[]) => {
  const last = bubbles.value.at(-1);
  if (!last || last.role !== 'assistant') {
    return;
  }
  let content = '';
  let hasError = false;
  for (const chunk of chunks) {
    if (!chunk) {
      continue;
    }
    switch (chunk.event) {
      case 'delta': {
        content += chunk.data ?? '';
        break;
      }
      case 'error': {
        hasError = true;
        ElMessage.error(chunk.data ?? '对话失败');
        break;
      }
      // done 仅表示流结束，内容已由 delta 拼出，无需额外处理
      default: {
        break;
      }
    }
  }
  if (content) {
    last.content = content;
    last.loading = false;
  } else if (hasError && !last.content) {
    last.loading = false;
  }
}, { deep: true });

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

/**
 * 发送消息：把历史消息与本轮提示词提交给流式端点
 */
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
    const readableStream = await chatStreamSipApi(
      { prompt: text, messages: history },
      abortController.value.signal,
    );
    // SIP 模式：传入自定义 transformStream 接管 OpenAI 兼容裸流的解析
    await startStream({
      readableStream,
      transformStream: createSipTransformStream(),
    });
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
</script>

<template>
  <Page auto-content-height>
    <div class="chat-container">
      <div class="chat-header">
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
        <!--
          content 插槽接管气泡内容（loading 态仍由 Bubble 预置的点点动画渲染）：
          助手正文走 MarkdownRenderer（enable-animate 流式打字动画 + 深浅主题代码高亮），
          用户消息保持纯文本。
        -->
        <BubbleList v-else :list="bubbles" max-height="100%">
          <template #content="{ item }">
            <MarkdownRenderer
              v-if="item.role === 'assistant'"
              :markdown="item.content"
              :is-dark="isDark"
              enable-animate
              class="bubble-markdown"
            />
            <span v-else class="bubble-text">{{ item.content }}</span>
          </template>
        </BubbleList>
      </div>

      <!-- EditorSender updown 变体：输入区在上、操作行在下，预置发送/停止/清空输入按钮 -->
      <div class="chat-footer">
        <XSender
          ref="senderRef"
          :loading="isLoading"
          clearable
          variant="updown"
          placeholder="输入消息，Enter 发送"
          submit-type="enter"
          @cancel="onCancel"
          @submit="onSubmit"
        />
      </div>
    </div>
  </Page>
</template>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: var(--el-bg-color);
  border-radius: 8px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

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

/* updown 变体自带描边卡片，不再叠分隔线 */
.chat-footer {
  padding: 8px 16px 16px;
}

/* 气泡内 markdown：去掉首尾段落外边距，避免气泡上下留白过大 */
.bubble-markdown :deep(> :first-child) {
  margin-top: 0;
}

.bubble-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

/* 用户消息保持纯文本换行行为 */
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
