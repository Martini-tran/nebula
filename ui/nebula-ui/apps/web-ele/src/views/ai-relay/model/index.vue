<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiRelayModelApi } from '#/api';

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
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayModelApi,
  deleteAiRelayModelApi,
  getAiRelayModelPageApi,
  updateAiRelayModelApi,
  updateAiRelayModelStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayModel' });

const modelTypeOptions = [
  { label: '文本', value: 1, tagType: 'primary' as const },
  { label: '图像', value: 2, tagType: 'success' as const },
  { label: '音频', value: 3, tagType: 'warning' as const },
  { label: '多模态', value: 4, tagType: 'info' as const },
  { label: 'Embedding', value: 5, tagType: 'danger' as const },
] as const;

function getModelTypeLabel(value?: number) {
  return modelTypeOptions.find((i) => i.value === value)?.label ?? '-';
}

function getModelTypeTagType(value?: number) {
  return modelTypeOptions.find((i) => i.value === value)?.tagType ?? 'info';
}

const gridOptions: VxeTableGridOptions<AiRelayModelApi.ModelItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'code', title: '模型编码', minWidth: 200 },
    { field: 'name', title: '名称', minWidth: 160 },
    { field: 'modelVendor', title: '厂商', width: 140 },
    {
      field: 'modelType',
      title: '类型',
      width: 110,
      slots: { default: 'modelType' },
    },
    {
      field: 'description',
      title: '说明',
      minWidth: 240,
      showOverflow: 'tooltip',
    },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'createTime',
      title: '创建时间',
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
        return await getAiRelayModelPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          modelVendor: formValues?.modelVendor || undefined,
          modelType:
            formValues?.modelType === '' || formValues?.modelType == null
              ? undefined
              : Number(formValues.modelType),
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
      { component: 'Input', fieldName: 'modelVendor', label: '厂商' },
      {
        component: 'Select',
        fieldName: 'modelType',
        label: '类型',
        componentProps: {
          clearable: true,
          options: modelTypeOptions.map(({ label, value }) => ({ label, value })),
        },
      },
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
  code: string;
  name: string;
  modelVendor: string;
  modelType: number;
  description: string;
  sortOrder: number;
  status: number;
}>({
  code: '',
  name: '',
  modelVendor: '',
  modelType: 1,
  description: '',
  sortOrder: 0,
  status: 1,
});

const editRules: FormRules = {
  code: [
    { required: true, message: '请输入模型编码', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入模型名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
};

function resetForm() {
  editForm.code = '';
  editForm.name = '';
  editForm.modelVendor = '';
  editForm.modelType = 1;
  editForm.description = '';
  editForm.sortOrder = 0;
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiRelayModelApi.ModelItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.code = row.code;
  editForm.name = row.name;
  editForm.modelVendor = row.modelVendor ?? '';
  editForm.modelType = row.modelType ?? 1;
  editForm.description = row.description ?? '';
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.status = row.status ?? 1;
  editVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = {
      code: editForm.code,
      name: editForm.name,
      modelVendor: editForm.modelVendor || undefined,
      modelType: editForm.modelType,
      description: editForm.description || undefined,
      sortOrder: editForm.sortOrder,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayModelApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayModelApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayModelApi.ModelItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayModelStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiRelayModelApi.ModelItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除模型「${row.name}」？存在套餐引用时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayModelApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:model:add'"
          type="primary"
          @click="openCreate"
        >
          新增模型
        </ElButton>
      </template>

      <template #modelType="{ row }">
        <ElTag :type="getModelTypeTagType(row.modelType)" size="small">
          {{ getModelTypeLabel(row.modelType) }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:model:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:model:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:model:delete'"
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
      :title="editMode === 'create' ? '新增模型' : '编辑模型'"
      width="560"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <ElFormItem label="模型编码" prop="code">
          <ElInput
            v-model="editForm.code"
            :disabled="editMode === 'edit'"
            placeholder="如 gpt-4o-mini / claude-3-5-sonnet"
          />
        </ElFormItem>
        <ElFormItem label="模型名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="对外展示名称" />
        </ElFormItem>
        <ElFormItem label="厂商" prop="modelVendor">
          <ElInput
            v-model="editForm.modelVendor"
            placeholder="OpenAI / Anthropic / Google"
          />
        </ElFormItem>
        <ElFormItem label="模型类型" prop="modelType">
          <ElSelect v-model="editForm.modelType" style="width: 100%">
            <ElOption
              v-for="o in modelTypeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="editForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
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
