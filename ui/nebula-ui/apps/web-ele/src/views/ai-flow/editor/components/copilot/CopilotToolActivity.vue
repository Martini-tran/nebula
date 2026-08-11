<script lang="ts" setup>
/**
 * 工具调用活动卡：把一轮里连续发生的多次工具调用折叠成 **一张卡**，默认收起。
 *
 * 为什么要这样做：B4 之后 copilot 的工具是细粒度草稿操作（add_node / connect /
 * update_node…），生成一张流程图往往触发十几二十次调用。旧版每次调用推一条气泡，
 * 正文被工具日志淹没。这里只在折叠头展示「正在做什么 + 进度」，明细按需展开。
 *
 * 数据只来自 tool_call 事件：{status, name, toolCallId, success, resultBrief, latencyMs}。
 * 后端从不下发工具入参，故此处不展示参数。
 */
import { computed, ref, watch } from 'vue';

import { Check, ChevronRight, CircleAlert, LoaderCircle } from '@nebula/icons';

defineOptions({ name: 'CopilotToolActivity' });

const props = defineProps<{
  calls: ToolCallView[];
}>();

/** 面板侧维护的单次工具调用视图 */
export interface ToolCallView {
  /** 关联 start/done 两帧；后端缺省时回退为 name+序号 */
  id: string;
  name: string;
  status: 'done' | 'start';
  success?: boolean;
  resultBrief?: string;
  latencyMs?: number;
}

/** 工具编码 → 中文动作名。未收录的工具回退显示原始编码，不阻断。 */
const TOOL_LABELS: Record<string, string> = {
  add_node: '添加节点',
  commit_draft: '提交流程',
  connect: '连接节点',
  create_draft: '创建草稿',
  derive_agent: '派生 Agent',
  disconnect: '断开连接',
  get_harness_operation: '查询试跑状态',
  list_model_profiles: '查询模型档案',
  list_node_types: '查询节点类型',
  list_tools: '查询可用工具',
  read_draft: '读取草稿',
  real_run_draft: '真实试跑',
  remove_node: '删除节点',
  search_tools: '检索工具',
  simulate_draft: '模拟运行',
  update_draft_metadata: '更新草稿信息',
  update_node: '更新节点',
  validate_draft: '校验草稿',
};

function labelOf(name: string) {
  return TOOL_LABELS[name] ?? name;
}

const expanded = ref(false);
/** 用户手动操作过就不再自动收起，尊重其选择 */
const touched = ref(false);

const runningCall = computed(() =>
  props.calls.find((call) => call.status === 'start'),
);
const isRunning = computed(() => !!runningCall.value);
const failedCount = computed(
  () => props.calls.filter((call) => call.success === false).length,
);
const doneCount = computed(
  () => props.calls.filter((call) => call.status === 'done').length,
);

/** 折叠头文案：进行中显示当前动作，结束后显示汇总 */
const summary = computed(() => {
  if (isRunning.value) return labelOf(runningCall.value!.name);
  if (failedCount.value > 0) return `${failedCount.value} 个操作未成功`;
  return `已完成 ${doneCount.value} 个操作`;
});

/** 全部结束后自动收起，避免明细长期占据版面 */
watch(isRunning, (running) => {
  if (!running && !touched.value) expanded.value = false;
});

function toggle() {
  touched.value = true;
  expanded.value = !expanded.value;
}

function formatLatency(ms?: number) {
  if (ms === undefined || ms === null) return '';
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}
</script>

<template>
  <div
    class="tool-activity"
    :class="{ 'is-running': isRunning, 'has-error': failedCount > 0 }"
  >
    <button
      class="activity-head"
      type="button"
      :aria-expanded="expanded"
      @click="toggle"
    >
      <span class="activity-status">
        <LoaderCircle v-if="isRunning" class="icon spin" />
        <CircleAlert v-else-if="failedCount > 0" class="icon icon-error" />
        <Check v-else class="icon icon-ok" />
      </span>
      <span class="activity-summary">{{ summary }}</span>
      <span v-if="calls.length > 1" class="activity-count">
        {{ doneCount }}/{{ calls.length }}
      </span>
      <ChevronRight class="activity-arrow" :class="{ open: expanded }" />
    </button>

    <div v-if="expanded" class="activity-detail">
      <div v-for="call in calls" :key="call.id" class="detail-row">
        <span class="detail-dot" :class="`dot-${call.status === 'start' ? 'running' : call.success === false ? 'error' : 'ok'}`"></span>
        <span class="detail-name">{{ labelOf(call.name) }}</span>
        <span v-if="call.latencyMs" class="detail-latency">
          {{ formatLatency(call.latencyMs) }}
        </span>
        <span v-if="call.resultBrief" class="detail-brief">
          {{ call.resultBrief }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tool-activity {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  transition: border-color 0.2s;
}

.tool-activity.is-running {
  border-color: var(--el-color-primary-light-5);
}

.tool-activity.has-error {
  border-color: var(--el-color-danger-light-5);
}

.activity-head {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
}

.activity-head:hover {
  background: var(--el-fill-color);
}

.activity-status {
  display: flex;
  flex-shrink: 0;
  align-items: center;
}

.icon {
  width: 14px;
  height: 14px;
}

.icon-ok {
  color: var(--el-color-success);
}

.icon-error {
  color: var(--el-color-danger);
}

.spin {
  color: var(--el-color-primary);
  animation: activity-spin 0.9s linear infinite;
}

@keyframes activity-spin {
  to {
    transform: rotate(360deg);
  }
}

.activity-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-count {
  flex-shrink: 0;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.activity-arrow {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  color: var(--el-text-color-secondary);
  transition: transform 0.2s;
}

.activity-arrow.open {
  transform: rotate(90deg);
}

.activity-detail {
  padding: 2px 10px 8px 32px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-row {
  display: flex;
  gap: 6px;
  align-items: baseline;
  padding: 4px 0;
  font-size: 12px;
  line-height: 1.5;
}

.detail-dot {
  flex-shrink: 0;
  align-self: center;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot-ok {
  background: var(--el-color-success);
}

.dot-error {
  background: var(--el-color-danger);
}

.dot-running {
  background: var(--el-color-primary);
  animation: dot-pulse 1s ease-in-out infinite;
}

@keyframes dot-pulse {
  50% {
    opacity: 0.3;
  }
}

.detail-name {
  flex-shrink: 0;
  color: var(--el-text-color-regular);
}

.detail-latency {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.detail-brief {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .spin,
  .dot-running {
    animation: none;
  }
}
</style>
