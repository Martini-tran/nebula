<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ForgeCategoryApi } from '#/api';

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
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createForgeCategoryApi,
  deleteForgeCategoryApi,
  getForgeCategoryPageApi,
  updateForgeCategoryApi,
  updateForgeCategoryStatusApi,
} from '#/api';

defineOptions({ name: 'ForgeCategory' });

const gridOptions: VxeTableGridOptions<ForgeCategoryApi.CategoryItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'code', title: '分类编码', minWidth: 160 },
    { field: 'name', title: '分类名称', minWidth: 160 },
    { field: 'description', title: '描述', minWidth: 200 },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
    { field: 'createTime', title: '创建时间', width: 180 },
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
        return await getForgeCategoryPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status ?? undefined,
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
            { label: '禁用', value: 0 },
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

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  code: string;
  name: string;
  description: string;
  sortOrder: number;
  status: number;
}>({
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
  status: 1,
});

const editRules: FormRules = {
  code: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.code = '';
  editForm.name = '';
  editForm.description = '';
  editForm.sortOrder = 0;
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

function openEdit(row: ForgeCategoryApi.CategoryItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.code = row.code;
  editForm.name = row.name;
  editForm.description = row.description ?? '';
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.status = row.status ?? 1;
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  const payload = {
    code: editForm.code,
    name: editForm.name,
    description: editForm.description || undefined,
    sortOrder: editForm.sortOrder,
    status: editForm.status,
  };

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createForgeCategoryApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateForgeCategoryApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: ForgeCategoryApi.CategoryItem) {
  const next = row.status === 1 ? 0 : 1;
  try {
    await updateForgeCategoryStatusApi(row.id, next);
    row.status = next;
    ElMessage.success('状态已更新');
  } catch {
    // 失败时保持原值
  }
}

async function handleDelete(row: ForgeCategoryApi.CategoryItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除分类「${row.name}」？被插件引用时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteForgeCategoryApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'forge:category:add'"
          type="primary"
          @click="openCreate"
        >
          新增分类
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElSwitch
          :model-value="row.status === 1"
          v-access:code="'forge:category:edit'"
          @click="toggleStatus(row)"
        />
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'forge:category:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'forge:category:delete'"
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
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增分类' : '编辑分类'"
      width="480"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="分类编码" prop="code">
          <ElInput v-model="editForm.code" placeholder="唯一编码，如 productivity" />
        </ElFormItem>
        <ElFormItem label="分类名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="请输入分类名称" />
        </ElFormItem>
        <ElFormItem label="描述" prop="description">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="排序" prop="sortOrder">
          <ElInputNumber
            v-model="editForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSwitch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="editDialogVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
