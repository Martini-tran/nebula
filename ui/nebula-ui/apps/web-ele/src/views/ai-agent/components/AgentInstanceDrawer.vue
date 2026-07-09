<script lang="ts" setup>
import type { AiAgentApi } from '#/api';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton, ElDrawer, ElEmpty, ElTag } from 'element-plus';

import { getAgentInstanceDetailApi } from '#/api';

import TransitionTimeline from './TransitionTimeline.vue';

defineOptions({ name: 'AgentInstanceDrawer' });

const router = useRouter();

/** 实例状态 → tag 类型 */
const STATUS_TAG: Record<string, string> = {
  RUNNING: 'primary',
  SUSPENDED: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
};

const visible = ref(false);
const loading = ref(false);
const detail = ref<AiAgentApi.AgentInstance>();

const contextText = computed(() =>
  detail.value?.contextSnapshot
    ? JSON.stringify(detail.value.contextSnapshot, null, 2)
    : '',
);

/** 跳到画布回放页（按实例 graph_snapshot 建图逐步回放） */
function goReplay() {
  const id = detail.value?.instanceId;
  if (!id) return;
  visible.value = false;
  router.push({ name: 'AiAgentReplay', query: { instanceId: id } });
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
      <div class="mb-1 flex items-center justify-between">
        <span class="text-sm font-medium">转移历史（回放）</span>
        <ElButton
          v-if="detail.instanceId"
          link
          size="small"
          type="primary"
          @click="goReplay"
        >
          画布回放
        </ElButton>
      </div>
      <TransitionTimeline :transitions="detail.transitions" />

      <!-- context 快照 -->
      <div class="mb-1 mt-4 text-sm font-medium">上下文快照</div>
      <pre
        class="max-h-[30vh] overflow-auto rounded bg-[#f5f5f5] p-3 text-xs dark:bg-[#2a2a2a]"
        >{{ contextText }}</pre>
    </template>

    <ElEmpty v-else-if="!loading" description="未加载实例" />
  </ElDrawer>
</template>
