<script lang="ts" setup>
/**
 * 高风险动作卡：真实试跑的「待确认」与「执行状态」两态共用一张卡。
 *
 * 真实试跑会真实调用模型/工具/子 Agent（产生费用与副作用），所以确认按钮走
 * danger 语义并显式展示后端下发的 warning，不做静默执行。
 */
import type { CopilotApi } from '#/api';

import { computed } from 'vue';

import { ElButton, ElTag } from 'element-plus';

defineOptions({ name: 'CopilotActionCard' });

const props = defineProps<{
  /** 有流式进行中时禁用确认，避免并发提交 */
  busy?: boolean;
  confirmation?: ConfirmationView;
  operation?: CopilotApi.OperationEvent;
}>();

const emit = defineEmits<{ confirm: [confirmation: ConfirmationView] }>();

export type ConfirmationStatus =
  | 'confirmed'
  | 'confirming'
  | 'failed'
  | 'pending';

export interface ConfirmationView {
  payload: CopilotApi.ConfirmationRequiredEvent;
  status: ConfirmationStatus;
  confirmationToken?: string;
}

const OPERATION_LABELS: Record<CopilotApi.OperationStatus, string> = {
  FAILED: '失败',
  PENDING: '等待执行',
  RUNNING: '执行中',
  SUCCEEDED: '成功',
  UNKNOWN: '状态未知',
};

function operationTagType(status: CopilotApi.OperationStatus) {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED' || status === 'UNKNOWN') return 'danger';
  if (status === 'RUNNING') return 'warning';
  return 'info';
}

const isPendingConfirm = computed(
  () => props.confirmation && props.confirmation.status !== 'confirmed',
);
</script>

<template>
  <div v-if="confirmation" class="action-card" :class="{ pending: isPendingConfirm }">
    <div class="action-head">
      <span class="action-title">真实试跑确认</span>
      <ElTag v-if="confirmation.status === 'confirmed'" type="success" size="small">
        已确认
      </ElTag>
      <ElTag v-else-if="confirmation.status === 'failed'" type="danger" size="small">
        确认失败
      </ElTag>
    </div>

    <p class="action-message">{{ confirmation.payload.warning }}</p>
    <div class="action-meta">revision {{ confirmation.payload.revision }}</div>

    <ElButton
      v-if="isPendingConfirm"
      :loading="confirmation.status === 'confirming'"
      :disabled="busy"
      size="small"
      type="danger"
      class="action-btn"
      @click="emit('confirm', confirmation)"
    >
      确认真实试跑
    </ElButton>
  </div>

  <div v-else-if="operation" class="action-card">
    <div class="action-head">
      <span class="action-title">真实试跑</span>
      <ElTag :type="operationTagType(operation.status)" size="small">
        {{ OPERATION_LABELS[operation.status] }}
      </ElTag>
    </div>
    <div class="action-meta">revision {{ operation.revision }}</div>
    <p v-if="operation.errorMessage" class="action-error">
      {{ operation.errorMessage }}
    </p>
  </div>
</template>

<style scoped>
.action-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

/* 待确认是阻塞态，用左侧危险色条把它从常规进度里区分出来 */
.action-card.pending {
  border-color: var(--el-color-danger-light-5);
  border-left: 3px solid var(--el-color-danger);
}

.action-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.action-message {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.action-meta {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.action-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}

.action-btn {
  align-self: flex-start;
  margin-top: 2px;
}
</style>
