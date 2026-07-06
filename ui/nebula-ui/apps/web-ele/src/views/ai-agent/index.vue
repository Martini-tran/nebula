<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiAgentApi } from '#/api';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  getAgentInstancePageApi,
  resumeAgentInstanceApi,
  signalAgentInstanceApi,
} from '#/api';

import AgentInstanceDrawer from './components/AgentInstanceDrawer.vue';
import AgentRunDialog from './components/AgentRunDialog.vue';

defineOptions({ name: 'AiAgentInstance' });

/** 实例状态 → element tag 类型 */
const STATUS_TAG: Record<string, string> = {
  RUNNING: 'primary',
  SUSPENDED: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
};

const gridOptions: VxeTableGridOptions<AiAgentApi.AgentInstance> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'instanceId', title: '实例ID', minWidth: 240 },
    { field: 'agentCode', title: 'Agent', minWidth: 120 },
    { field: 'status', title: '状态', width: 110, slots: { default: 'status' } },
    { field: 'currentState', title: '当前状态', minWidth: 120 },
    { field: 'transitionCount', title: '转移数', width: 90, align: 'center' },
    { field: 'action', title: '操作', width: 240, fixed: 'right', slots: { default: 'action' } },
  ],
  height: 'auto',
  keepSource: true,
  pagerConfig: { pageSize: 10 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getAgentInstancePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          agentCode: formValues?.agentCode || undefined,
          status: formValues?.status || undefined,
        });
      },
    },
  },
  rowConfig: { keyField: 'instanceId' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
};

const [Grid, gridApi] = usenebulaVxeGrid({
  gridOptions,
  showSearchForm: true,
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'agentCode', label: 'Agent 编码' },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: [
            { label: 'RUNNING', value: 'RUNNING' },
            { label: 'SUSPENDED', value: 'SUSPENDED' },
            { label: 'SUCCESS', value: 'SUCCESS' },
            { label: 'FAILED', value: 'FAILED' },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ---------------- 运行 Agent（按 inputSchema 动态表单） ----------------
const runDialogRef = ref<InstanceType<typeof AgentRunDialog>>();

function openRun() {
  runDialogRef.value?.open();
}

// ---------------- 唤醒（signal） ----------------
const signalVisible = ref(false);
const signalLoading = ref(false);
const signalTarget = ref<AiAgentApi.AgentInstance>();
const signalForm = reactive<{ event: string; payloadText: string }>({
  event: '',
  payloadText: '{\n  \n}',
});
const signalEvents = computed(() => signalTarget.value?.awaitingEvents ?? []);

function openSignal(row: AiAgentApi.AgentInstance) {
  signalTarget.value = row;
  signalForm.event = row.awaitingEvents?.[0] ?? '';
  signalForm.payloadText = '{\n  \n}';
  signalVisible.value = true;
}

async function submitSignal() {
  const id = signalTarget.value?.instanceId;
  if (!id) return;
  let payload: Record<string, any> = {};
  const text = signalForm.payloadText.trim();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      ElMessage.error('payload 不是合法 JSON');
      return;
    }
  }
  signalLoading.value = true;
  try {
    const res = await signalAgentInstanceApi(id, {
      event: signalForm.event || undefined,
      payload,
    });
    ElMessage.success(`已唤醒：${res.status ?? ''}`);
    signalVisible.value = false;
    reloadGrid();
  } finally {
    signalLoading.value = false;
  }
}

// ---------------- 续跑（resume） ----------------
async function handleResume(row: AiAgentApi.AgentInstance) {
  if (!row.instanceId) return;
  try {
    const res = await resumeAgentInstanceApi(row.instanceId);
    ElMessage.success(`已续跑：${res.status ?? ''}`);
    reloadGrid();
  } catch {
    // 错误已由拦截器提示
  }
}

// ---------------- 详情抽屉 ----------------
const drawerRef = ref<InstanceType<typeof AgentInstanceDrawer>>();
function openDetail(row: AiAgentApi.AgentInstance) {
  if (row.instanceId) drawerRef.value?.open(row.instanceId);
}

// ---------------- 画布回放 ----------------
const router = useRouter();
function openReplay(row: AiAgentApi.AgentInstance) {
  if (!row.instanceId) return;
  router.push({ name: 'AiAgentReplay', query: { instanceId: row.instanceId } });
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-agent:run'"
          type="primary"
          @click="openRun"
        >
          运行 Agent
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="(STATUS_TAG[row.status ?? ''] as any) || 'info'" size="small">
          {{ row.status }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-agent:query'"
            link
            type="primary"
            @click="openDetail(row)"
          >
            详情
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-agent:query'"
            link
            type="primary"
            @click="openReplay(row)"
          >
            回放
          </ElButton>
          <ElButton
            v-if="row.status === 'SUSPENDED'"
            v-access:code="'manager:ai-agent:run'"
            link
            type="warning"
            @click="openSignal(row)"
          >
            唤醒
          </ElButton>
          <ElButton
            v-if="row.status === 'RUNNING'"
            v-access:code="'manager:ai-agent:run'"
            link
            type="primary"
            @click="handleResume(row)"
          >
            续跑
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 运行 Agent -->
    <AgentRunDialog ref="runDialogRef" @finished="reloadGrid" />

    <!-- 唤醒 signal -->
    <ElDialog
      v-model="signalVisible"
      :close-on-click-modal="false"
      title="唤醒挂起实例"
      width="520"
    >
      <ElForm label-width="96px">
        <ElFormItem label="事件">
          <ElSelect
            v-model="signalForm.event"
            allow-create
            class="w-full"
            filterable
            placeholder="选择或输入事件名"
          >
            <ElOption
              v-for="e in signalEvents"
              :key="e"
              :label="e"
              :value="e"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="负载">
          <ElInput
            v-model="signalForm.payloadText"
            :rows="6"
            placeholder="JSON 对象，写入 context 供出边 guard 裁决"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="signalVisible = false">取消</ElButton>
        <ElButton :loading="signalLoading" type="primary" @click="submitSignal">
          唤醒
        </ElButton>
      </template>
    </ElDialog>

    <AgentInstanceDrawer ref="drawerRef" />
  </Page>
</template>
