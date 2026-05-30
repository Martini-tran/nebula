<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogTagApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogTagApi,
  deleteBlogTagApi,
  getBlogTagListApi,
  updateBlogTagApi,
} from '#/api';

defineOptions({ name: 'BlogTag' });

const gridOptions: VxeTableGridOptions<BlogTagApi.TagItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '标签名称', minWidth: 220 },
    { field: 'slug', title: 'Slug', minWidth: 180 },
    { field: 'useCount', title: '使用次数', width: 120, align: 'center' },
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
const tagList = ref<BlogTagApi.TagItem[]>([]);

async function reloadGrid() {
  const data = await getBlogTagListApi();
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
  slug: string;
}>({
  name: '',
  slug: '',
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 50, message: '最长 50 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '仅允许小写字母、数字和中划线',
      trigger: 'blur',
    },
    { max: 80, message: '最长 80 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.name = '';
  editForm.slug = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

function openEdit(row: BlogTagApi.TagItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.name = row.name;
  editForm.slug = row.slug;
  editDialogVisible.value = true;
}

function autoSlug() {
  if (editMode.value === 'edit' || editForm.slug) return;
  editForm.slug = editForm.name
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = {
      name: editForm.name,
      slug: editForm.slug,
    };

    if (editMode.value === 'create') {
      await createBlogTagApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogTagApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }

    editDialogVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: BlogTagApi.TagItem) {
  try {
    await ElMessageBox.confirm(`确认删除标签“${row.name}”吗？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }

  await deleteBlogTagApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:tag:add'"
          type="primary"
          @click="openCreate"
        >
          新增标签
        </ElButton>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:tag:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:tag:delete'"
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
        label-width="90px"
      >
        <ElFormItem label="标签名称" prop="name">
          <ElInput
            v-model="editForm.name"
            placeholder="请输入标签名称"
            @blur="autoSlug"
          />
        </ElFormItem>

        <ElFormItem label="Slug" prop="slug">
          <ElInput v-model="editForm.slug" placeholder="URL 标识，例如 frontend" />
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
