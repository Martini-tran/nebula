<script lang="ts" setup>
import type { ConfirmationView } from './copilot/CopilotActionCard.vue';
import type { ToolCallView } from './copilot/CopilotToolActivity.vue';

/**
 * AI 对话分栏面板：编辑器最右侧的独立栏位，与节点配置面板并列成"第四栏"。
 *
 * 统一走 /admin/ai-flow/copilot/stream：纯聊天时后端只回 delta 文本，
 * 需要生成流程时由模型自主调工具，在对话中逐步搭建草稿并落库。
 *
 * 展示策略（本轮重点）：一轮里的多次工具调用**合并为一张可折叠的活动卡**，
 * 默认只显示「正在做什么 + 进度」，不再每次调用推一条气泡刷屏——B4 之后
 * 工具是细粒度草稿操作（add_node / connect …），生成一张图常触发十几次调用。
 *
 * 折叠 / 拖拽宽度 / tab 头由外层 SplitLayout（EditorSideDock.vue）承载，本组件只做对话内容。
 */
import type { CopilotApi } from '#/api';

import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { BubbleList, useXStream, XSender } from 'vue-element-plus-x';

import { Eraser, Sparkles } from '@nebula/icons';
import { usePreferences } from '@nebula/preferences';

import { ElButton, ElMessage } from 'element-plus';

import {
  confirmCopilotActionApi,
  copilotStreamApi,
  getCopilotDraftDefinitionApi,
  getCopilotOperationApi,
} from '#/api';

import CopilotActionCard from './copilot/CopilotActionCard.vue';
import CopilotToolActivity from './copilot/CopilotToolActivity.vue';
import CopilotTypewriter from './copilot/CopilotTypewriter.vue';

defineOptions({ name: 'AiChatPanel' });

/** flow 事件向上抛出，供编辑器主壳把生成的流程回显到画布 */
const emit = defineEmits<{
  draftUpdated: [payload: CopilotApi.DraftDefinitionResult];
  flowGenerated: [payload: CopilotApi.FlowEvent];
}>();

/**
 * 气泡列表项。kind 决定渲染方式：
 * - text：用户/助手正文，参与下一轮 history
 * - activity：工具调用活动卡（一轮聚合一张）
 * - action：真实试跑确认 / 执行状态卡
 * - notice：轻量提示（草稿更新、产物落库）
 * 除 text 外均为过程展示，不进 history。
 */
interface ChatBubble {
  key: number;
  role: 'assistant' | 'user';
  kind: 'action' | 'activity' | 'notice' | 'text';
  content: string;
  placement: 'end' | 'start';
  loading: boolean;
  /** kind=activity */
  calls?: ToolCallView[];
  /** kind=action */
  confirmation?: ConfirmationView;
  operation?: CopilotApi.OperationEvent;
  /** kind=notice 的强调色 */
  tone?: 'error' | 'info' | 'success';
}

const senderRef = ref<InstanceType<typeof XSender>>();
const typewriterRef = ref<InstanceType<typeof CopilotTypewriter>>();
const bubbles = ref<ChatBubble[]>([]);
const abortController = ref<AbortController>();

/** markdown 渲染跟随全局深浅主题（代码高亮双主题切换） */
const { isDark } = usePreferences();

const { startStream, cancel, data, error, isLoading } = useXStream();

let bubbleKey = 0;
let processedEventIndex = 0;
/** 本轮累积的全量助手文本，喂给打字机作为目标 */
const streamedText = ref('');
/** 本轮工具活动气泡的 key，用于把后续 tool_call 追加进同一张卡 */
let activityBubbleKey: number | undefined;
let pendingDraftUpdate: CopilotApi.DraftUpdatedEvent | undefined;
let latestDraftId: string | undefined;
let latestDraftRevision = -1;
let draftRefreshTimer: ReturnType<typeof setTimeout> | undefined;
const operationTimers = new Map<string, ReturnType<typeof setTimeout>>();

function newConversationId() {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `copilot-${Date.now()}-${Math.random().toString(16).slice(2)}`
  );
}

let conversationId = newConversationId();

const hasConversation = computed(() => bubbles.value.length > 0);

/** 当前这轮的助手正文气泡（最后一条 text 且为 assistant） */
function currentTextBubble() {
  return bubbles.value.findLast(
    (bubble) => bubble.role === 'assistant' && bubble.kind === 'text',
  );
}

/**
 * 当前正文气泡的 key。只有它走打字机并绑定 streamedText，
 * 历史轮已定格直接渲染自身 content。抽成 computed 避免模板里反复扫描列表。
 */
const activeTextKey = computed(() => currentTextBubble()?.key);

function pushBubble(bubble: Omit<ChatBubble, 'key'>) {
  const created: ChatBubble = { ...bubble, key: bubbleKey++ };
  bubbles.value.push(created);
  return created;
}

function pushNotice(content: string, tone: ChatBubble['tone'] = 'info') {
  return pushBubble({
    role: 'assistant',
    kind: 'notice',
    content,
    placement: 'start',
    loading: false,
    tone,
  });
}

// ---------------- 工具活动聚合 ----------------
/**
 * 把 tool_call 事件并入当前轮的活动卡。
 * start 追加一条记录，done 按 toolCallId 回填同一条——同一轮可并行调多个工具，
 * 仅靠 name 无法配对，故以后端下发的 toolCallId 为准，缺省时才回退到「最后一条同名未完成」。
 */
function mergeToolCall(payload: CopilotApi.ToolCallEvent) {
  let bubble = bubbles.value.find(
    (item) => item.key === activityBubbleKey && item.kind === 'activity',
  );
  if (!bubble) {
    bubble = pushBubble({
      role: 'assistant',
      kind: 'activity',
      content: '',
      placement: 'start',
      loading: false,
      calls: [],
    });
    activityBubbleKey = bubble.key;
  }
  const calls = bubble.calls!;

  if (payload.status === 'start') {
    calls.push({
      id: payload.toolCallId ?? `${payload.name}-${calls.length}`,
      name: payload.name,
      status: 'start',
    });
    return;
  }

  const target = payload.toolCallId
    ? calls.find((call) => call.id === payload.toolCallId)
    : calls.findLast(
        (call) => call.name === payload.name && call.status === 'start',
      );
  if (target) {
    target.status = 'done';
    target.success = payload.success;
    target.resultBrief = payload.resultBrief;
    target.latencyMs = payload.latencyMs;
    return;
  }
  // 没配上 start（例如断流重连）：补一条已完成记录，避免丢失可观测性
  calls.push({
    id: payload.toolCallId ?? `${payload.name}-${calls.length}`,
    name: payload.name,
    status: 'done',
    success: payload.success,
    resultBrief: payload.resultBrief,
    latencyMs: payload.latencyMs,
  });
}

// ---------------- 真实试跑 operation ----------------
function clearOperationTimer(operationId: string) {
  const timer = operationTimers.get(operationId);
  if (timer) clearTimeout(timer);
  operationTimers.delete(operationId);
}

function upsertOperation(payload: CopilotApi.OperationEvent) {
  let bubble = bubbles.value.find(
    (item) => item.operation?.operationId === payload.operationId,
  );
  if (!bubble) {
    bubble = pushBubble({
      role: 'assistant',
      kind: 'action',
      content: '',
      placement: 'start',
      loading: false,
    });
  }
  bubble.operation = payload;
  if (payload.status === 'PENDING' || payload.status === 'RUNNING') {
    scheduleOperationPoll(payload.operationId);
  } else {
    clearOperationTimer(payload.operationId);
  }
}

function scheduleOperationPoll(operationId: string) {
  clearOperationTimer(operationId);
  operationTimers.set(
    operationId,
    setTimeout(async () => {
      operationTimers.delete(operationId);
      try {
        const operation = await getCopilotOperationApi(operationId);
        if (operation?.operationId) upsertOperation(operation);
      } catch {
        // 短暂网络故障不改变服务端 operation 状态，继续查询持久化结果。
        scheduleOperationPoll(operationId);
      }
    }, 1500),
  );
}

// ---------------- 草稿回显 ----------------
function scheduleDraftRefresh(payload: CopilotApi.DraftUpdatedEvent) {
  if (latestDraftId === payload.draftId) {
    latestDraftRevision = Math.max(latestDraftRevision, payload.revision);
  } else {
    latestDraftId = payload.draftId;
    latestDraftRevision = payload.revision;
  }
  if (!pendingDraftUpdate || payload.revision >= pendingDraftUpdate.revision) {
    pendingDraftUpdate = payload;
  }
  if (draftRefreshTimer) clearTimeout(draftRefreshTimer);
  // 连续的草稿变更合并成一次拉取，避免每个 add_node 都打一次接口
  draftRefreshTimer = setTimeout(async () => {
    const expected = pendingDraftUpdate;
    pendingDraftUpdate = undefined;
    draftRefreshTimer = undefined;
    if (!expected) return;
    try {
      const result = await getCopilotDraftDefinitionApi(
        expected.draftId,
        conversationId,
      );
      if (
        result.ok &&
        result.definition &&
        result.draftId === latestDraftId &&
        result.revision >= latestDraftRevision
      ) {
        emit('draftUpdated', result);
      }
    } catch {
      pushNotice(`草稿 ${expected.draftId} 的画布刷新失败`, 'error');
    }
  }, 120);
}

/** 处理 copilot 专属事件；返回 true 表示已消费 */
function handleCopilotEvent(
  eventName: string,
  payload: Record<string, any>,
): boolean {
  switch (eventName) {
    case 'agent': {
      pushNotice(
        `已派生 Agent「${payload.name ?? payload.agentCode}」`,
        'success',
      );
      return true;
    }
    case 'confirm_required': {
      const bubble = pushBubble({
        role: 'assistant',
        kind: 'action',
        content: '',
        placement: 'start',
        loading: false,
      });
      bubble.confirmation = {
        payload: payload as CopilotApi.ConfirmationRequiredEvent,
        status: 'pending',
      };
      return true;
    }
    case 'draft_updated': {
      scheduleDraftRefresh(payload as CopilotApi.DraftUpdatedEvent);
      return true;
    }
    case 'flow': {
      pushNotice(
        `已生成流程「${payload.name ?? payload.flowCode}」${
          payload.nodeCount ? `，共 ${payload.nodeCount} 个节点` : ''
        }`,
        'success',
      );
      emit('flowGenerated', payload as CopilotApi.FlowEvent);
      return true;
    }
    case 'operation_updated': {
      upsertOperation(payload as CopilotApi.OperationEvent);
      return true;
    }
    case 'tool_call': {
      mergeToolCall(payload as CopilotApi.ToolCallEvent);
      return true;
    }
    default: {
      return false;
    }
  }
}

/**
 * 消费 useXStream 累积的 SSE 事件。
 * 注意：useXStream 内部用 data.value.push() 原地追加，ref 本身不重新赋值，
 * 必须 deep 侦听才能在流式增量到达时触发。
 */
watch(
  data,
  (events) => {
    if (events.length < processedEventIndex) processedEventIndex = 0;
    for (let index = processedEventIndex; index < events.length; index += 1) {
      const event = events[index];
      if (!event?.data) continue;
      let payload: Record<string, any>;
      try {
        payload = JSON.parse(event.data);
      } catch {
        continue;
      }
      switch (event.event) {
        case 'delta': {
          streamedText.value += payload.content ?? '';
          break;
        }
        case 'done': {
          const target = currentTextBubble();
          if (target) target.loading = false;
          break;
        }
        case 'error': {
          ElMessage.error(payload.message ?? '对话失败');
          break;
        }
        default: {
          handleCopilotEvent(event.event ?? '', payload);
          break;
        }
      }
    }
    processedEventIndex = events.length;
    // 有文本到达即结束 loading 骨架，交给打字机逐字呈现
    if (streamedText.value) {
      const target = currentTextBubble();
      if (target) target.loading = false;
    }
  },
  { deep: true },
);

function prepareStream() {
  data.value.splice(0);
  processedEventIndex = 0;
  streamedText.value = '';
}

async function runStream(request: CopilotApi.CopilotStreamRequest) {
  prepareStream();
  abortController.value = new AbortController();
  const readableStream = await copilotStreamApi(
    request,
    abortController.value.signal,
  );
  await startStream({ readableStream });
}

async function onConfirm(confirmation: ConfirmationView) {
  if (isLoading.value || confirmation.status === 'confirming') {
    ElMessage.warning('请等待当前操作结束');
    return;
  }
  confirmation.status = 'confirming';
  try {
    if (!confirmation.confirmationToken) {
      const confirmed = await confirmCopilotActionApi(
        confirmation.payload.confirmationId,
        conversationId,
      );
      if (!confirmed.ok || !confirmed.confirmationToken) {
        throw new Error(confirmed.message ?? '确认授权签发失败');
      }
      confirmation.confirmationToken = confirmed.confirmationToken;
    }
    confirmation.status = 'confirmed';
    // 续跑是新一轮流：重置活动聚合，避免并入上一轮的卡片
    activityBubbleKey = undefined;
    await runStream({
      prompt: '',
      conversationId,
      confirmationToken: confirmation.confirmationToken,
      resumeAction: {
        toolCode: 'real_run_draft',
        arguments: confirmation.payload.resumeArguments,
      },
    });
  } catch (error_: any) {
    confirmation.status = 'failed';
    ElMessage.error(error_?.message ?? '确认真实试跑失败');
  }
}

/** 流式请求本身失败（网络/鉴权等） */
watch(error, (err) => {
  if (!err) return;
  const target = currentTextBubble();
  if (target && !streamedText.value) {
    streamedText.value = `请求失败：${err.message}`;
    target.loading = false;
  }
});

/** 发送消息：把历史消息与本轮提示词提交给 copilot 流式端点 */
async function onSubmit() {
  const text = senderRef.value?.getModelValue?.()?.text?.trim();
  if (!text) return;
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }

  // 上一轮的助手正文定格：把打字机内容落回气泡，供下一轮作为 history
  const previous = currentTextBubble();
  if (previous && streamedText.value) previous.content = streamedText.value;

  // 取当前对话作为历史上下文（只保留真实对话文本，过程卡片不进 history）
  const history: CopilotApi.ChatMessage[] = bubbles.value
    .filter((item) => item.kind === 'text' && item.content)
    .map((item) => ({ role: item.role, content: item.content }));

  // 新一轮开始：活动卡重新聚合
  activityBubbleKey = undefined;

  pushBubble({
    role: 'user',
    kind: 'text',
    content: text,
    placement: 'end',
    loading: false,
  });
  pushBubble({
    role: 'assistant',
    kind: 'text',
    content: '',
    placement: 'start',
    loading: true,
  });

  // XSender 暴露的清空方法名为 clear（onClear 是其内部函数名，未导出）
  senderRef.value?.clear?.();
  await nextTick();

  try {
    await runStream({
      prompt: text,
      messages: history,
      conversationId,
      activeDraftId: latestDraftId,
      activeDraftRevision:
        latestDraftRevision >= 0 ? latestDraftRevision : undefined,
    });
  } catch (error_: any) {
    const target = currentTextBubble();
    if (target) {
      streamedText.value = `请求失败：${error_?.message ?? '未知错误'}`;
      target.loading = false;
    }
  }
}

/** 中断当前回复：让打字机立即落地已收到的文本 */
function onCancel() {
  cancel();
  abortController.value?.abort();
  typewriterRef.value?.finish();
  const target = currentTextBubble();
  if (target) {
    target.loading = false;
    if (!streamedText.value) streamedText.value = '已取消';
  }
}

/** 清空对话 */
function onClearChat() {
  if (isLoading.value) {
    ElMessage.warning('请等待当前回复结束');
    return;
  }
  bubbles.value = [];
  conversationId = newConversationId();
  activityBubbleKey = undefined;
  pendingDraftUpdate = undefined;
  latestDraftId = undefined;
  latestDraftRevision = -1;
  if (draftRefreshTimer) clearTimeout(draftRefreshTimer);
  draftRefreshTimer = undefined;
  operationTimers.forEach((timer) => clearTimeout(timer));
  operationTimers.clear();
  prepareStream();
}

/** 空态引导词：点一下直接填进输入框 */
const SUGGESTIONS = [
  '生成一个「读取文档 → 总结 → 翻译」的流程',
  '帮我在当前草稿里加一个条件分支',
  '校验当前草稿有没有结构问题',
];

function useSuggestion(text: string) {
  senderRef.value?.clear?.();
  const instance = senderRef.value as any;
  // XSender 无公开 setValue，走 v-model 底层字段兜底；失败则仅聚焦让用户自行输入
  if (instance?.setModelValue) instance.setModelValue({ text });
  instance?.focus?.();
}

onBeforeUnmount(() => {
  abortController.value?.abort();
  if (draftRefreshTimer) clearTimeout(draftRefreshTimer);
  operationTimers.forEach((timer) => clearTimeout(timer));
  operationTimers.clear();
});
</script>

<template>
  <div class="chat-panel">
    <div class="chat-body">
      <!-- 空态：一句引导 + 可点击的示例，降低「不知道能说什么」的冷启动成本 -->
      <div v-if="!hasConversation" class="chat-empty">
        <div class="empty-icon">
          <Sparkles />
        </div>
        <p class="empty-title">描述你想要的流程</p>
        <p class="empty-desc">
          我会在对话中逐步搭建草稿，并实时回显到左侧画布
        </p>
        <div class="empty-suggestions">
          <button
            v-for="item in SUGGESTIONS"
            :key="item"
            class="suggestion"
            type="button"
            @click="useSuggestion(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <BubbleList v-else :list="bubbles" max-height="100%">
        <template #content="{ item }">
          <CopilotToolActivity
            v-if="item.kind === 'activity'"
            :calls="item.calls ?? []"
          />
          <CopilotActionCard
            v-else-if="item.kind === 'action'"
            :confirmation="item.confirmation"
            :operation="item.operation"
            :busy="isLoading"
            @confirm="onConfirm"
          />
          <div v-else-if="item.kind === 'notice'" class="notice" :class="`tone-${item.tone}`">
            {{ item.content }}
          </div>
          <!--
            当前轮的助手正文走打字机（目标文本是流式累积的 streamedText），
            历史轮已定格，直接渲染其 content。
          -->
          <CopilotTypewriter
            v-else-if="item.role === 'assistant' && item.key === activeTextKey"
            ref="typewriterRef"
            :content="streamedText"
            :is-dark="isDark"
            is-markdown
            is-fog
            :typing="{ step: 2, interval: 30 }"
          />
          <CopilotTypewriter
            v-else-if="item.role === 'assistant'"
            :content="item.content"
            :is-dark="isDark"
            is-markdown
          />
          <span v-else class="bubble-text">{{ item.content }}</span>
        </template>
      </BubbleList>
    </div>

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

.chat-body {
  flex: 1;
  min-height: 0;
  padding: 12px 12px 4px;
  overflow: hidden;
}

/* ---- 空态 ---- */
.chat-empty {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0 16px;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 12px;
}

.empty-icon svg {
  width: 22px;
  height: 22px;
}

.empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.empty-desc {
  max-width: 260px;
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.empty-suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 300px;
  margin-top: 14px;
}

.suggestion {
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    border-color 0.2s,
    color 0.2s;
}

.suggestion:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

/* updown 变体自带描边卡片，不再叠一条分隔线 */
.chat-footer {
  flex-shrink: 0;
  padding: 8px 12px 12px;
}

.prefix-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
}

/* 用户消息保持纯文本换行行为 */
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---- 轻量提示（草稿更新、产物落库）---- */
.notice {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.notice.tone-success {
  color: var(--el-color-success);
}

.notice.tone-error {
  color: var(--el-color-danger);
}
</style>
