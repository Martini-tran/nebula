<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelProfileApi } from '#/api';

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
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiModelProfileApi,
  deleteAiModelProfileApi,
  getAiModelProfilePageApi,
  updateAiModelProfileApi,
  updateAiModelProfileStatusApi,
} from '#/api';

defineOptions({ name: 'AiModelProfile' });

const gridOptions: VxeTableGridOptions<AiModelProfileApi.ProfileItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'profileCode', title: '档案编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    { field: 'provider', title: '提供商', width: 120 },
    { field: 'model', title: '模型', minWidth: 160 },
    {
      field: 'apiKeyMasked',
      title: '密钥',
      width: 130,
      slots: { default: 'apiKey' },
    },
    { field: 'temperature', title: '温度', width: 90, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'updateTime',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 200,
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
        return await getAiModelProfilePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          provider: formValues?.provider || undefined,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : Number(formValues.status),
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
      { component: 'Input', fieldName: 'provider', label: '提供商' },
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
/** 编辑态下原有密钥的掩码，用于 placeholder 提示「留空不修改」 */
const apiKeyPlaceholder = ref('');

const editForm = reactive<{
  apiKey: string;
  baseUrl: string;
  maxTokens: null | number;
  model: string;
  name: string;
  optionsText: string;
  profileCode: string;
  provider: string;
  remark: string;
  status: number;
  temperature: null | number;
  timeoutMs: null | number;
  topP: null | number;
}>({
  apiKey: '',
  baseUrl: '',
  maxTokens: null,
  model: '',
  name: '',
  optionsText: '',
  profileCode: '',
  provider: '',
  remark: '',
  status: 1,
  temperature: null,
  timeoutMs: null,
  topP: null,
});

const editRules: FormRules = {
  profileCode: [
    { required: true, message: '请输入档案编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.apiKey = '';
  editForm.baseUrl = '';
  editForm.maxTokens = null;
  editForm.model = '';
  editForm.name = '';
  editForm.optionsText = '';
  editForm.profileCode = '';
  editForm.provider = '';
  editForm.remark = '';
  editForm.status = 1;
  editForm.temperature = null;
  editForm.timeoutMs = null;
  editForm.topP = null;
  apiKeyPlaceholder.value = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  apiKeyPlaceholder.value = '请输入 API 密钥';
  editVisible.value = true;
}

function openEdit(row: AiModelProfileApi.ProfileItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.profileCode = row.profileCode;
  editForm.name = row.name ?? '';
  editForm.provider = row.provider ?? '';
  editForm.baseUrl = row.baseUrl ?? '';
  editForm.model = row.model ?? '';
  editForm.temperature = row.temperature ?? null;
  editForm.maxTokens = row.maxTokens ?? null;
  editForm.topP = row.topP ?? null;
  editForm.timeoutMs = row.timeoutMs ?? null;
  editForm.status = row.status ?? 1;
  editForm.remark = row.remark ?? '';
  editForm.optionsText = row.options
    ? JSON.stringify(row.options, null, 2)
    : '';
  apiKeyPlaceholder.value = row.hasApiKey
    ? `已配置（${row.apiKeyMasked ?? '****'}），留空则不修改`
    : '未配置，请输入 API 密钥';
  editVisible.value = true;
}

/** 解析 options JSON 文本，非法时抛错由调用方提示 */
function parseOptions(): Record<string, any> | undefined {
  const text = editForm.optionsText.trim();
  if (!text) return undefined;
  const parsed = JSON.parse(text);
  if (typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new TypeError('扩展参数必须是 JSON 对象');
  }
  return parsed;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  let options: Record<string, any> | undefined;
  try {
    options = parseOptions();
  } catch (error) {
    ElMessage.error(`扩展参数 JSON 解析失败：${(error as Error).message}`);
    return;
  }

  editLoading.value = true;
  try {
    const payload: AiModelProfileApi.ProfileSaveParams = {
      profileCode: editForm.profileCode,
      name: editForm.name || undefined,
      provider: editForm.provider || undefined,
      baseUrl: editForm.baseUrl || undefined,
      apiKey: editForm.apiKey || undefined,
      model: editForm.model || undefined,
      temperature: editForm.temperature,
      maxTokens: editForm.maxTokens,
      topP: editForm.topP,
      timeoutMs: editForm.timeoutMs,
      options,
      status: editForm.status,
      remark: editForm.remark || undefined,
    };
    if (editMode.value === 'create') {
      await createAiModelProfileApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiModelProfileApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiModelProfileApi.ProfileItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiModelProfileStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiModelProfileApi.ProfileItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除模型档案「${row.name || row.profileCode}」？流程/节点引用该档案时将回退到全局配置。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiModelProfileApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-model-profile:add'"
          type="primary"
          @click="openCreate"
        >
          新增档案
        </ElButton>
      </template>

      <template #apiKey="{ row }">
        <ElTag v-if="row.hasApiKey" size="small" type="success">
          {{ row.apiKeyMasked }}
        </ElTag>
        <ElTag v-else size="small" type="info">未配置</ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-model-profile:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-model-profile:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-model-profile:delete'"
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
      :title="editMode === 'create' ? '新增模型档案' : '编辑模型档案'"
      width="620"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="档案编码" prop="profileCode">
          <ElInput
            v-model="editForm.profileCode"
            :disabled="editMode === 'edit'"
            placeholder="如 deepseek-chat-default"
          />
        </ElFormItem>
        <ElFormItem label="档案名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="便于识别的名称" />
        </ElFormItem>
        <ElFormItem label="提供商">
          <ElInput
            v-model="editForm.provider"
            placeholder="openai / deepseek / anthropic"
          />
        </ElFormItem>
        <ElFormItem label="模型名称">
          <ElInput v-model="editForm.model" placeholder="如 deepseek-chat" />
        </ElFormItem>
        <ElFormItem label="API 地址">
          <ElInput
            v-model="editForm.baseUrl"
            placeholder="如 https://api.deepseek.com/v1"
          />
        </ElFormItem>
        <ElFormItem label="API 密钥">
          <ElInput
            v-model="editForm.apiKey"
            :placeholder="apiKeyPlaceholder"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="温度">
          <ElInputNumber
            v-model="editForm.temperature"
            :max="2"
            :min="0"
            :precision="2"
            :step="0.1"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="最大 Tokens">
          <ElInputNumber
            v-model="editForm.maxTokens"
            :max="1000000"
            :min="1"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="Top P">
          <ElInputNumber
            v-model="editForm.topP"
            :max="1"
            :min="0"
            :precision="2"
            :step="0.05"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="超时(ms)">
          <ElInputNumber
            v-model="editForm.timeoutMs"
            :max="600000"
            :min="0"
            :step="1000"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="扩展参数">
          <ElInput
            v-model="editForm.optionsText"
            :rows="3"
            placeholder='JSON 对象，如 {"frequency_penalty": 0.2}'
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="editForm.remark"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
