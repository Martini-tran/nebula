<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiIterationApi } from '#/api';

import { reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createIterationChainApi,
  deleteIterationChainApi,
  getIterationChainPageApi,
  pauseIterationChainApi,
  resumeIterationChainApi,
  runNowIterationChainApi,
  updateIterationChainApi,
} from '#/api';

import IterationChainDrawer from './components/IterationChainDrawer.vue';

defineOptions({ name: 'AiIterationChain' });

const STATUS_TAG: Record<string, string> = {
  ACTIVE: 'success',
  PAUSED: 'warning',
  COMPLETED: 'info',
  FAILED: 'danger',
};

const gridOptions: VxeTableGridOptions<AiIterationApi.Chain> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '系列名称', minWidth: 150 },
    { field: 'agentCode', title: 'Agent', minWidth: 150 },
    { field: 'cron', title: '节律', width: 130 },
    { field: 'seq', title: '已写篇数', width: 90, align: 'center' },
    { field: 'nextRunAt', title: '下轮触发', width: 170 },
    { field: 'status', title: '状态', width: 100, slots: { default: 'status' } },
    {
      field: 'action',
      title: '操作',
      width: 320,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  height: 'auto',
  keepSource: true,
  pagerConfig: { pageSize: 10 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getIterationChainPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status || undefined,
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: {
    custom: true,
    refresh: { code: 'query' },
    search: true,
    zoom: true,
  },
};

const [Grid, gridApi] = usenebulaVxeGrid({
  gridOptions,
  showSearchForm: true,
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'keyword', label: '关键词' },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: [
            { label: '运行中', value: 'ACTIVE' },
            { label: '已暂停', value: 'PAUSED' },
            { label: '已完成', value: 'COMPLETED' },
            { label: '已失败', value: 'FAILED' },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ---------------- 新增 / 编辑 ----------------
type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingChainId = ref<null | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  agentCode: string;
  carryOver: string;
  cron: string;
  maxIterations: null | number;
  name: string;
  seedInputs: string;
  untilExpr: string;
  webhookUrl: string;
}>({
  agentCode: '',
  carryOver: '',
  cron: '0 0 9 * * ?',
  maxIterations: 30,
  name: '',
  seedInputs: '',
  untilExpr: '',
  webhookUrl: '',
});

const editRules: FormRules = {
  agentCode: [{ required: true, message: '请输入 Agent 编码', trigger: 'blur' }],
  cron: [{ required: true, message: '请输入 cron 节律', trigger: 'blur' }],
};

function resetForm() {
  editForm.agentCode = '';
  editForm.carryOver = '';
  editForm.cron = '0 0 9 * * ?';
  editForm.maxIterations = 30;
  editForm.name = '';
  editForm.seedInputs = '';
  editForm.untilExpr = '';
  editForm.webhookUrl = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingChainId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiIterationApi.Chain) {
  editMode.value = 'edit';
  editingChainId.value = row.chainId ?? null;
  resetForm();
  editForm.name = row.name ?? '';
  editForm.agentCode = row.agentCode ?? '';
  editForm.cron = row.cron ?? '';
  editForm.maxIterations = row.maxIterations ?? null;
  editForm.untilExpr = row.untilExpr ?? '';
  editForm.webhookUrl = row.webhookUrl ?? '';
  editForm.carryOver =
    row.carryOver && Object.keys(row.carryOver).length > 0
      ? JSON.stringify(row.carryOver, null, 2)
      : '';
  editForm.seedInputs =
    row.seedInputs && Object.keys(row.seedInputs).length > 0
      ? JSON.stringify(row.seedInputs, null, 2)
      : '';
  editVisible.value = true;
}

/** 解析一个可选 JSON 对象字段（空 → undefined），非法则提示并抛错中断提交 */
function parseJsonObj(
  text: string,
  label: string,
): Record<string, any> | undefined {
  const t = text.trim();
  if (!t) return undefined;
  try {
    const parsed = JSON.parse(t);
    if (typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new TypeError('not object');
    }
    return parsed;
  } catch {
    ElMessage.error(`${label} 不是合法的 JSON 对象`);
    throw new Error('invalid json');
  }
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  let carryOver: Record<string, any> | undefined;
  let seedInputs: Record<string, any> | undefined;
  try {
    carryOver = parseJsonObj(editForm.carryOver, 'carryOver 映射');
    seedInputs = parseJsonObj(editForm.seedInputs, 'seedInputs 种子入参');
  } catch {
    return;
  }

  editLoading.value = true;
  try {
    const payload: AiIterationApi.ChainSaveRequest = {
      name: editForm.name || undefined,
      agentCode: editForm.agentCode,
      cron: editForm.cron,
      maxIterations: editForm.maxIterations ?? undefined,
      untilExpr: editForm.untilExpr || undefined,
      carryOver: carryOver as Record<string, string> | undefined,
      seedInputs,
      webhookUrl: editForm.webhookUrl || undefined,
    };
    if (editMode.value === 'create') {
      await createIterationChainApi(payload);
      ElMessage.success('系列已创建，将按节律推进');
    } else if (editingChainId.value) {
      await updateIterationChainApi(editingChainId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

// ---------------- 行操作 ----------------
async function runNow(row: AiIterationApi.Chain) {
  if (!row.chainId) return;
  await runNowIterationChainApi(row.chainId);
  ElMessage.success('已触发，下次扫描即推进一轮');
  reloadGrid();
}

async function togglePause(row: AiIterationApi.Chain) {
  if (!row.chainId) return;
  if (row.status === 'ACTIVE') {
    await pauseIterationChainApi(row.chainId);
    ElMessage.success('已暂停');
  } else if (row.status === 'PAUSED') {
    await resumeIterationChainApi(row.chainId);
    ElMessage.success('已恢复');
  }
  reloadGrid();
}

async function handleDelete(row: AiIterationApi.Chain) {
  try {
    await ElMessageBox.confirm(
      `确认删除系列「${row.name || row.chainId}」？已产出的文章实例不受影响，仅停止后续推进。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  if (!row.chainId) return;
  await deleteIterationChainApi(row.chainId);
  ElMessage.success('删除成功');
  reloadGrid();
}

// ---------------- 详情抽屉 ----------------
const drawerRef = ref<InstanceType<typeof IterationChainDrawer>>();
function openDetail(row: AiIterationApi.Chain) {
  if (row.chainId) drawerRef.value?.open(row.chainId);
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-iteration:add'"
          type="primary"
          @click="openCreate"
        >
          新增系列
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="STATUS_TAG[row.status ?? ''] as any" size="small">
          {{ row.status }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-if="row.status === 'ACTIVE'"
            v-access:code="'manager:ai-iteration:run'"
            link
            type="success"
            @click="runNow(row)"
          >
            立即推进
          </ElButton>
          <ElButton
            v-if="row.status === 'ACTIVE' || row.status === 'PAUSED'"
            v-access:code="'manager:ai-iteration:edit'"
            link
            type="warning"
            @click="togglePause(row)"
          >
            {{ row.status === 'ACTIVE' ? '暂停' : '恢复' }}
          </ElButton>
          <ElButton link type="primary" @click="openDetail(row)">
            详情
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-iteration:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-iteration:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <ElDialog
      v-model="editVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增系列' : '编辑系列'"
      width="680"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="120px"
      >
        <ElFormItem label="系列名称">
          <ElInput v-model="editForm.name" maxlength="128" />
        </ElFormItem>
        <ElFormItem label="Agent 编码" prop="agentCode">
          <ElInput
            v-model="editForm.agentCode"
            :disabled="editMode === 'edit'"
            placeholder="每一轮用哪个 Agent 跑（写一篇 + 更新大纲）"
          />
        </ElFormItem>
        <ElFormItem label="节律 cron" prop="cron">
          <ElInput
            v-model="editForm.cron"
            placeholder="如 0 0 9 * * ?（每天9点推进一篇）"
          />
        </ElFormItem>
        <ElFormItem label="轮次上限">
          <ElInputNumber v-model="editForm.maxIterations" :min="1" />
          <span class="ml-2 text-xs text-gray-400">达上限自动完成</span>
        </ElFormItem>
        <ElFormItem label="出链条件">
          <ElInput
            v-model="editForm.untilExpr"
            placeholder="SpEL，对上一轮产物求值，如 getString('outlineDone') == 'true'"
          />
        </ElFormItem>
        <ElFormItem label="carryOver">
          <ElInput
            v-model="editForm.carryOver"
            :rows="4"
            placeholder='JSON：上一轮产物键 → 下一轮 inputs 键，如 {"outline":"outline","accumulated":"accumulated"}'
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="seedInputs">
          <ElInput
            v-model="editForm.seedInputs"
            :rows="3"
            placeholder='JSON：首轮种子入参，如 {"topic":"30天Java进阶"}'
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="回调 URL">
          <ElInput
            v-model="editForm.webhookUrl"
            placeholder="每轮生成后 POST 产物到此 URL（如 blog 落库接口），空则不回调"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确定
        </ElButton>
      </template>
    </ElDialog>

    <IterationChainDrawer ref="drawerRef" />
  </Page>
</template>
