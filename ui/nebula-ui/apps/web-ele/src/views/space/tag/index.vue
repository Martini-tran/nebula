<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SpaceTagApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElColorPicker,
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
  createSpaceTagApi,
  deleteSpaceTagApi,
  getSpaceTagListApi,
  updateSpaceTagApi,
} from '#/api';

defineOptions({ name: 'SpaceTag' });

const gridOptions: VxeTableGridOptions<SpaceTagApi.TagItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '标签名称', minWidth: 220, slots: { default: 'name' } },
    { field: 'color', title: '颜色', width: 120, slots: { default: 'color' } },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    { field: 'remark', title: '备注', minWidth: 200 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  data: [],
  height: 'auto',
  pagerConfig: { enabled: false },
  proxyConfig: { enabled: false },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, zoom: true },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });
const tagList = ref<SpaceTagApi.TagItem[]>([]);

async function reloadGrid() {
  const data = await getSpaceTagListApi();
  tagList.value = data ?? [];
  await gridApi.setGridOptions({ data: tagList.value });
}

onMounted(reloadGrid);

type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  name: string;
  color: string;
  sortOrder: number;
  remark: string;
}>({
  name: '',
  color: '',
  sortOrder: 0,
  remark: '',
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.name = '';
  editForm.color = '';
  editForm.sortOrder = 0;
  editForm.remark = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

function openEdit(row: SpaceTagApi.TagItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.name = row.name;
  editForm.color = row.color ?? '';
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.remark = row.remark ?? '';
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = {
      name: editForm.name,
      color: editForm.color || undefined,
      sortOrder: editForm.sortOrder,
      remark: editForm.remark || undefined,
    };

    if (editMode.value === 'create') {
      await createSpaceTagApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateSpaceTagApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }

    editDialogVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: SpaceTagApi.TagItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除标签“${row.name}”？该标签下的书签将解除关联。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  await deleteSpaceTagApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'space:tag:add'"
          type="primary"
          @click="openCreate"
        >
          新增标签
        </ElButton>
      </template>

      <template #name="{ row }">
        <ElTag
          :color="row.color || undefined"
          :style="row.color ? { color: '#fff', borderColor: row.color } : undefined"
        >
          {{ row.name }}
        </ElTag>
      </template>

      <template #color="{ row }">
        <span v-if="row.color" class="space-tag-color">
          <span
            class="space-tag-color__dot"
            :style="{ background: row.color }"
          />
          {{ row.color }}
        </span>
        <span v-else>-</span>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'space:tag:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'space:tag:delete'"
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
      :title="editMode === 'create' ? '新增标签' : '编辑标签'"
      width="480"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="80px"
      >
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="请输入标签名称" />
        </ElFormItem>

        <ElFormItem label="颜色">
          <ElColorPicker v-model="editForm.color" show-alpha />
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber v-model="editForm.sortOrder" :min="0" :max="9999" />
        </ElFormItem>

        <ElFormItem label="备注">
          <ElInput
            v-model="editForm.remark"
            :rows="3"
            maxlength="500"
            placeholder="可选"
            show-word-limit
            type="textarea"
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

<style scoped>
.space-tag-color {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.space-tag-color__dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid var(--el-border-color);
}
</style>
