<script lang="ts" setup>
/**
 * 迭代链详情抽屉：链头（写到第几篇/下轮何时跑/状态/连续失败）+ carryOver/seedInputs 配置只读展示
 * + 已产出实例时间线（阶段 I 仅展示最后一篇 = lastInstanceId 指针；完整系列回放需阶段 II 串 parent_instance_id）。
 *
 * 语义对齐 docs/跨实例迭代层设计.md：系列递进走"上一轮实例产物 → 下一轮入参"（carryOver），不走 ai_memory。
 */
import type { AiIterationApi } from '#/api';

import { ref } from 'vue';

import {
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getIterationChainDetailApi } from '#/api';

defineOptions({ name: 'IterationChainDrawer' });

const visible = ref(false);
const loading = ref(false);
const chain = ref<AiIterationApi.Chain>();

const STATUS_TAG: Record<string, string> = {
  ACTIVE: 'success',
  PAUSED: 'warning',
  COMPLETED: 'info',
  FAILED: 'danger',
};

async function open(chainId: string) {
  visible.value = true;
  loading.value = true;
  chain.value = undefined;
  try {
    chain.value = await getIterationChainDetailApi(chainId);
  } finally {
    loading.value = false;
  }
}

function pretty(obj?: Record<string, any>): string {
  if (!obj || Object.keys(obj).length === 0) return '（未配置）';
  return JSON.stringify(obj, null, 2);
}

defineExpose({ open });
</script>

<template>
  <ElDrawer
    v-model="visible"
    :size="620"
    :title="`迭代链详情 · ${chain?.name || chain?.chainId || ''}`"
    direction="rtl"
  >
    <div v-loading="loading">
      <ElDescriptions v-if="chain" :column="2" border size="small">
        <ElDescriptionsItem label="链标识">
          {{ chain.chainId }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="Agent">
          {{ chain.agentCode }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="STATUS_TAG[chain.status ?? ''] as any" size="small">
            {{ chain.status }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="已写篇数">
          {{ chain.seq ?? 0 }}
          <span v-if="chain.maxIterations" class="text-xs text-gray-400">
            / {{ chain.maxIterations }}
          </span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="节律 cron">
          {{ chain.cron }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="下轮触发">
          {{ chain.nextRunAt || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="连续失败">
          {{ chain.consecutiveFails ?? 0 }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="出链条件">
          <span class="break-all">{{ chain.untilExpr || '—' }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem v-if="chain.errorMsg" :span="2" label="最近错误">
          <span class="break-all text-red-500">{{ chain.errorMsg }}</span>
        </ElDescriptionsItem>
      </ElDescriptions>

      <template v-if="chain">
        <div class="mb-1 mt-4 text-xs text-gray-400">
          carryOver（上一轮产物键 → 下一轮 inputs 键）
        </div>
        <pre class="detail-pre">{{ pretty(chain.carryOver) }}</pre>

        <div class="mb-1 mt-3 text-xs text-gray-400">
          seedInputs（首轮种子入参）
        </div>
        <pre class="detail-pre">{{ pretty(chain.seedInputs) }}</pre>

        <div class="mb-1 mt-4 text-sm font-medium">
          已产出实例时间线
          <span class="ml-2 text-xs font-normal text-gray-400">
            阶段 I 展示最后一篇（lastInstanceId）
          </span>
        </div>
        <ElTable
          v-if="chain.runs && chain.runs.length > 0"
          :data="chain.runs"
          size="small"
        >
          <ElTableColumn label="实例 ID" min-width="220" prop="instanceId" />
          <ElTableColumn label="状态" width="110">
            <template #default="{ row }">
              <ElTag size="small">{{ row.status }}</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="创建时间" prop="createTime" width="170" />
        </ElTable>
        <ElEmpty v-else description="尚无产出实例" :image-size="60" />
      </template>
    </div>
  </ElDrawer>
</template>

<style scoped>
.detail-pre {
  max-height: 20vh;
  padding: 8px;
  overflow: auto;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
