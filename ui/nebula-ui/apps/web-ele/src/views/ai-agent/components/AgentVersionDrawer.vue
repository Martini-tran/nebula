<script lang="ts" setup>
/**
 * Agent 版本管理抽屉：同 agentCode 的版本历史（version 降序，最新标「当前」），
 * 行操作：发布新版本（复制该版定义 version+1）、启用/停用（停用旧版=灰度回退）、
 * 查看（只读展示 IO 契约 / 记忆配置）。
 *
 * 语义对齐设计文档：版本发布后不可变（历史版本后端拒绝 PUT）；运行入口自动命中
 * 同 code 最大启用版本；运行中/挂起实例已被 graph_snapshot 锁定，不受发布影响。
 */
import type { AiAgentApi } from '#/api';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElDrawer,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getAgentDetailApi,
  getAgentVersionsApi,
  publishAgentVersionApi,
  updateAgentStatusApi,
} from '#/api';

defineOptions({ name: 'AgentVersionDrawer' });

const emit = defineEmits<{
  /** 版本集合发生变化（发布/启停），父页可刷新列表 */
  changed: [];
}>();

const visible = ref(false);
const loading = ref(false);
const agentCode = ref('');
const rows = ref<AiAgentApi.AgentSummary[]>([]);

const latestVersion = computed(() =>
  rows.value.reduce((max, r) => Math.max(max, r.version ?? 0), 0),
);

async function open(code: string) {
  agentCode.value = code;
  rows.value = [];
  detail.value = undefined;
  visible.value = true;
  await load();
}

async function load() {
  loading.value = true;
  try {
    rows.value = await getAgentVersionsApi(agentCode.value);
  } finally {
    loading.value = false;
  }
}

async function publish(row: AiAgentApi.AgentSummary) {
  try {
    await ElMessageBox.confirm(
      `基于 v${row.version} 复制发布新版本 v${latestVersion.value + 1}？发布后运行入口自动切到最新启用版本；运行中/挂起实例已版本锁定，不受影响。`,
      '发布新版本',
      { type: 'info' },
    );
  } catch {
    return;
  }
  await publishAgentVersionApi(row.id);
  ElMessage.success('新版本已发布');
  await load();
  emit('changed');
}

async function toggleStatus(row: AiAgentApi.AgentSummary) {
  const next = row.status === 1 ? 0 : 1;
  await updateAgentStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  await load();
  emit('changed');
}

// ---------------- 只读查看（IO 契约 / 记忆配置） ----------------
const detail = ref<AiAgentApi.AgentDetail>();
const detailLoading = ref(false);

async function view(row: AiAgentApi.AgentSummary) {
  detailLoading.value = true;
  try {
    detail.value = await getAgentDetailApi(row.id);
  } finally {
    detailLoading.value = false;
  }
}

function pretty(text?: string): string {
  const t = (text ?? '').trim();
  if (!t) return '（未配置）';
  try {
    return JSON.stringify(JSON.parse(t), null, 2);
  } catch {
    return t;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDrawer
    v-model="visible"
    :size="640"
    :title="`版本管理 · ${agentCode}`"
    direction="rtl"
  >
    <ElTable v-loading="loading" :data="rows" size="small">
      <ElTableColumn label="版本" width="90">
        <template #default="{ row }">
          <span>v{{ row.version }}</span>
          <ElTag
            v-if="row.version === latestVersion"
            class="ml-1"
            size="small"
            type="primary"
          >
            当前
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="引用流程" min-width="130">
        <template #default="{ row }">
          {{ row.flowCode }}<span class="text-xs text-gray-400">@v{{ row.flowVersion }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="80">
        <template #default="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="更新时间" prop="updateTime" width="160" />
      <ElTableColumn fixed="right" label="操作" width="190">
        <template #default="{ row }">
          <ElButton
            v-access:code="'manager:ai-agent:version'"
            link
            size="small"
            type="primary"
            @click="publish(row)"
          >
            发布新版本
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-agent:edit'"
            link
            size="small"
            type="warning"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton link size="small" type="primary" @click="view(row)">
            查看
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 只读详情：IO 契约 / 记忆配置 -->
    <div v-if="detail" v-loading="detailLoading" class="mt-4">
      <div class="mb-2 text-sm font-medium">
        v{{ detail.version }} 定义详情
        <span class="ml-2 text-xs font-normal text-gray-400">
          {{ detail.name || detail.agentCode }}
        </span>
      </div>
      <div class="mb-1 text-xs text-gray-400">输入契约 input_schema</div>
      <pre class="detail-pre">{{ pretty(detail.inputSchema) }}</pre>
      <div class="mb-1 mt-3 text-xs text-gray-400">输出契约 output_schema</div>
      <pre class="detail-pre">{{ pretty(detail.outputSchema) }}</pre>
      <div class="mb-1 mt-3 text-xs text-gray-400">记忆配置 memory_config</div>
      <pre class="detail-pre">{{ pretty(detail.memoryConfig) }}</pre>
    </div>
  </ElDrawer>
</template>

<style scoped>
.detail-pre {
  max-height: 26vh;
  padding: 8px;
  overflow: auto;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
