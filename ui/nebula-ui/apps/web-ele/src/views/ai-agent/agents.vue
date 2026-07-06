<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiAgentApi } from '#/api';

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
  createAgentApi,
  deleteAgentApi,
  getAgentDetailApi,
  getAgentPageApi,
  updateAgentApi,
  updateAgentStatusApi,
} from '#/api';

import ProfileSelector from '../ai-flow/editor/components/selectors/ProfileSelector.vue';

defineOptions({ name: 'AiAgentDefinition' });

const gridOptions: VxeTableGridOptions<AiAgentApi.AgentSummary> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'agentCode', title: 'Agent 编码', minWidth: 160 },
    { field: 'name', title: '名称', minWidth: 140 },
    { field: 'flowCode', title: '引用流程', minWidth: 140 },
    { field: 'version', title: '版本', width: 80, align: 'center' },
    { field: 'status', title: '状态', width: 100, slots: { default: 'status' } },
    { field: 'updateTime', title: '更新时间', width: 180, formatter: 'formatDateTime' },
    { field: 'action', title: '操作', width: 200, fixed: 'right', slots: { default: 'action' } },
  ],
  height: 'auto',
  keepSource: true,
  pagerConfig: { pageSize: 10 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getAgentPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : Number(formValues.status),
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
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
            { label: '启用', value: 1 },
            { label: '停用', value: 0 },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  agentCode: string;
  defaultProfileCode: string;
  description: string;
  flowCode: string;
  flowVersion: null | number;
  inputSchema: string;
  memoryConfig: string;
  name: string;
  outputSchema: string;
  status: number;
  version: null | number;
}>({
  agentCode: '',
  defaultProfileCode: '',
  description: '',
  flowCode: '',
  flowVersion: 1,
  inputSchema: '',
  memoryConfig: '',
  name: '',
  outputSchema: '',
  status: 1,
  version: 1,
});

const editRules: FormRules = {
  agentCode: [
    { required: true, message: '请输入 Agent 编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  flowCode: [{ required: true, message: '请输入引用的流程编码', trigger: 'blur' }],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.agentCode = '';
  editForm.defaultProfileCode = '';
  editForm.description = '';
  editForm.flowCode = '';
  editForm.flowVersion = 1;
  editForm.inputSchema = '';
  editForm.memoryConfig = '';
  editForm.name = '';
  editForm.outputSchema = '';
  editForm.status = 1;
  editForm.version = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

async function openEdit(row: AiAgentApi.AgentSummary) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  // 详情端点返回全字段（含 schema/memoryConfig），编辑回显以它为准
  const d = await getAgentDetailApi(row.id);
  editForm.agentCode = d.agentCode;
  editForm.name = d.name ?? '';
  editForm.description = d.description ?? '';
  editForm.flowCode = d.flowCode ?? '';
  editForm.flowVersion = d.flowVersion ?? 1;
  editForm.inputSchema = d.inputSchema ?? '';
  editForm.outputSchema = d.outputSchema ?? '';
  editForm.memoryConfig = d.memoryConfig ?? '';
  editForm.defaultProfileCode = d.defaultProfileCode ?? '';
  editForm.version = d.version ?? 1;
  editForm.status = d.status ?? 1;
  editVisible.value = true;
}

/** 校验一个可选 JSON 文本字段（空视为合法），非法时提示并返回 false */
function validJson(text: string, label: string): boolean {
  const t = text.trim();
  if (!t) return true;
  try {
    JSON.parse(t);
    return true;
  } catch {
    ElMessage.error(`${label} 不是合法 JSON`);
    return false;
  }
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;
  if (!validJson(editForm.memoryConfig, '记忆配置')) return;
  if (!validJson(editForm.inputSchema, '输入契约')) return;
  if (!validJson(editForm.outputSchema, '输出契约')) return;

  editLoading.value = true;
  try {
    const payload: AiAgentApi.AgentSaveRequest = {
      agentCode: editForm.agentCode,
      name: editForm.name || undefined,
      description: editForm.description || undefined,
      flowCode: editForm.flowCode,
      flowVersion: editForm.flowVersion ?? 1,
      inputSchema: editForm.inputSchema || undefined,
      outputSchema: editForm.outputSchema || undefined,
      memoryConfig: editForm.memoryConfig || undefined,
      defaultProfileCode: editForm.defaultProfileCode || undefined,
      version: editForm.version ?? 1,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAgentApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAgentApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiAgentApi.AgentSummary) {
  const next = row.status === 1 ? 0 : 1;
  await updateAgentStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiAgentApi.AgentSummary) {
  try {
    await ElMessageBox.confirm(
      `确认删除 Agent「${row.name || row.agentCode}」？已创建的运行中/挂起实例已版本锁定，不受影响。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAgentApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-agent:add'"
          type="primary"
          @click="openCreate"
        >
          新增 Agent
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-agent:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-agent:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-agent:delete'"
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
      :title="editMode === 'create' ? '新增 Agent' : '编辑 Agent'"
      width="680"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="Agent 编码" prop="agentCode">
          <ElInput
            v-model="editForm.agentCode"
            :disabled="editMode === 'edit'"
            maxlength="64"
            placeholder="跨版本稳定的逻辑标识（= 记忆隔离键）"
          />
        </ElFormItem>
        <ElFormItem label="名称">
          <ElInput v-model="editForm.name" maxlength="128" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            maxlength="512"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="引用流程" prop="flowCode">
          <ElInput
            v-model="editForm.flowCode"
            placeholder="引用的编排图 flowCode"
          />
        </ElFormItem>
        <ElFormItem label="流程版本">
          <ElInputNumber v-model="editForm.flowVersion" :min="1" />
        </ElFormItem>
        <ElFormItem label="默认档案">
          <ProfileSelector
            v-model="editForm.defaultProfileCode"
            class="w-full"
            placeholder="节点/Flow 未指定档案时使用（可选）"
          />
        </ElFormItem>
        <ElFormItem label="记忆配置">
          <ElInput
            v-model="editForm.memoryConfig"
            :rows="4"
            placeholder='JSON，如 {"enabled":true,"import":["userProfile"],"exportStrategy":"Append"}'
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="输入契约">
          <ElInput
            v-model="editForm.inputSchema"
            :rows="3"
            placeholder="输入 JSON Schema（可选）"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="输出契约">
          <ElInput
            v-model="editForm.outputSchema"
            :rows="3"
            placeholder="输出 JSON Schema（可选）"
            type="textarea"
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
  </Page>
</template>
