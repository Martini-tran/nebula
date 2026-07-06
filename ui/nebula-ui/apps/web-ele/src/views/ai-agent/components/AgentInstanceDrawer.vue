<script lang="ts" setup>
import type { AiAgentApi } from '#/api';

import { computed, ref } from 'vue';

import {
  ElDrawer,
  ElEmpty,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import { getAgentInstanceDetailApi } from '#/api';

defineOptions({ name: 'AgentInstanceDrawer' });

/** 实例状态 → tag 类型 */
const STATUS_TAG: Record<string, string> = {
  RUNNING: 'primary',
  SUSPENDED: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
};

/** 转移结果 → 时间线节点颜色 */
const OUTCOME_COLOR: Record<string, string> = {
  SUCCESS: '#67c23a',
  RETRY: '#e6a23c',
  FAILED: '#f56c6c',
  COMPENSATED: '#909399',
};

const visible = ref(false);
const loading = ref(false);
const detail = ref<AiAgentApi.AgentInstance>();

const contextText = computed(() =>
  detail.value?.contextSnapshot
    ? JSON.stringify(detail.value.contextSnapshot, null, 2)
    : '',
);

/** 按 seq 升序的转移时间线 */
const transitions = computed(() =>
  [...(detail.value?.transitions ?? [])].sort(
    (a, b) => (a.seq ?? 0) - (b.seq ?? 0),
  ),
);

function nodeResultText(t: AiAgentApi.AgentTransition) {
  return t.nodeResult && Object.keys(t.nodeResult).length > 0
    ? JSON.stringify(t.nodeResult, null, 2)
    : '';
}

async function load(instanceId: string) {
  loading.value = true;
  try {
    detail.value = await getAgentInstanceDetailApi(instanceId);
  } finally {
    loading.value = false;
  }
}

async function open(instanceId: string) {
  visible.value = true;
  await load(instanceId);
}

defineExpose({ open });
</script>

<template>
  <ElDrawer v-model="visible" :size="620" direction="rtl" title="实例详情">
    <template v-if="detail">
      <!-- 头部信息 -->
      <div class="mb-4 space-y-1 text-sm">
        <div>
          <span class="text-gray-400">实例ID：</span>{{ detail.instanceId }}
        </div>
        <div class="flex items-center gap-2">
          <span class="text-gray-400">状态：</span>
          <ElTag
            :type="(STATUS_TAG[detail.status ?? ''] as any) || 'info'"
            size="small"
          >
            {{ detail.status }}
          </ElTag>
          <span class="text-gray-400">当前：</span>{{ detail.currentState }}
        </div>
        <div>
          <span class="text-gray-400">Agent / Flow：</span>{{ detail.agentCode }}
          / {{ detail.flowCode }}
        </div>
        <div v-if="detail.awaitingEvents?.length">
          <span class="text-gray-400">等待事件：</span>
          <ElTag
            v-for="e in detail.awaitingEvents"
            :key="e"
            class="mr-1"
            size="small"
            type="warning"
          >
            {{ e }}
          </ElTag>
        </div>
      </div>

      <!-- 转移时间线 -->
      <div class="mb-1 text-sm font-medium">转移历史（回放）</div>
      <ElEmpty
        v-if="transitions.length === 0"
        description="暂无转移记录"
        :image-size="60"
      />
      <ElTimeline v-else class="mt-2">
        <ElTimelineItem
          v-for="t in transitions"
          :key="`${t.seq}-${t.attempt}-${t.outcome}`"
          :color="OUTCOME_COLOR[t.outcome ?? ''] || '#909399'"
          :timestamp="`seq ${t.seq} · attempt ${t.attempt}`"
        >
          <div class="flex items-center gap-2 text-sm">
            <ElTag
              :type="t.outcome === 'SUCCESS' ? 'success' : t.outcome === 'RETRY' ? 'warning' : 'danger'"
              size="small"
            >
              {{ t.outcome }}
            </ElTag>
            <span class="font-medium">
              {{ t.fromState ? `${t.fromState} → ` : '' }}{{ t.toState }}
            </span>
            <span v-if="t.eventName" class="text-xs text-gray-400">
              事件 {{ t.eventName }}
            </span>
          </div>
          <pre
            v-if="nodeResultText(t)"
            class="mt-1 max-h-[24vh] overflow-auto rounded bg-[#f5f5f5] p-2 text-xs dark:bg-[#2a2a2a]"
            >{{ nodeResultText(t) }}</pre>
        </ElTimelineItem>
      </ElTimeline>

      <!-- context 快照 -->
      <div class="mb-1 mt-4 text-sm font-medium">上下文快照</div>
      <pre
        class="max-h-[30vh] overflow-auto rounded bg-[#f5f5f5] p-3 text-xs dark:bg-[#2a2a2a]"
        >{{ contextText }}</pre>
    </template>

    <ElEmpty v-else-if="!loading" description="未加载实例" />
  </ElDrawer>
</template>
