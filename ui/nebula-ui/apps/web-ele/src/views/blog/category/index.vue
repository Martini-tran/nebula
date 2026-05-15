<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { BlogCategoryApi } from '#/api';

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
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import {
  createBlogCategoryApi,
  deleteBlogCategoryApi,
  getBlogCategoryTreeApi,
  updateBlogCategoryApi,
} from '#/api';

defineOptions({ name: 'BlogCategory' });

// ===== 树形列表 =====
const loading = ref(false);
const tableData = ref<BlogCategoryApi.CategoryItem[]>([]);

async function loadTree() {
  loading.value = true;
  try {
    tableData.value = await getBlogCategoryTreeApi();
  } finally {
    loading.value = false;
  }
}

loadTree();

// 将树展平为选项（用于父分类下拉，排除自身及其子孙）
function flattenTree(
  nodes: BlogCategoryApi.CategoryItem[],
  excludeId?: number | string,
): { id: number | string; label: string; disabled?: boolean }[] {
  const result: { id: number | string; label: string; disabled?: boolean }[] = [];
  function walk(list: BlogCategoryApi.CategoryItem[], depth = 0) {
    for (const node of list) {
      if (excludeId != null && String(node.id) === String(excludeId)) continue;
      result.push({ id: node.id, label: `${'—'.repeat(depth)} ${node.name}`.trim() });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  }
  walk(nodes);
  return result;
}

// ===== 创建 / 编辑 =====
type EditMode = 'create' | 'edit';
const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  name: string;
  slug: string;
  description: string;
  parentId: number | string | null;
  sortOrder: number;
}>({
  name: '',
  slug: '',
  description: '',
  parentId: null,
  sortOrder: 0,
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '最多 50 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '只允许小写字母、数字和连字符',
      trigger: 'blur',
    },
    { max: 80, message: '最多 80 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.name = '';
  editForm.slug = '';
  editForm.description = '';
  editForm.parentId = null;
  editForm.sortOrder = 0;
  editFormRef.value?.clearValidate();
}

function openCreate(parentId?: number | string) {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  if (parentId != null) editForm.parentId = parentId;
  editDialogVisible.value = true;
}

function openEdit(row: BlogCategoryApi.CategoryItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.name = row.name;
  editForm.slug = row.slug;
  editForm.description = row.description ?? '';
  editForm.parentId = row.parentId ?? null;
  editForm.sortOrder = row.sortOrder ?? 0;
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  const payload = {
    name: editForm.name,
    slug: editForm.slug,
    description: editForm.description || undefined,
    parentId: editForm.parentId ?? undefined,
    sortOrder: editForm.sortOrder,
  };

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createBlogCategoryApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogCategoryApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    loadTree();
  } finally {
    editLoading.value = false;
  }
}

// ===== 删除 =====
async function handleDelete(row: BlogCategoryApi.CategoryItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除分类「${row.name}」？存在子分类时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogCategoryApi(row.id);
  ElMessage.success('删除成功');
  loadTree();
}

// 自动填充 slug：将中文/空格替换为连字符，去掉特殊字符
function autoSlug() {
  if (editMode.value === 'edit') return;
  editForm.slug = editForm.name
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}
</script>

<template>
  <Page auto-content-height>
    <div class="p-4">
      <!-- 工具栏 -->
      <div class="mb-4 flex items-center justify-between">
        <span class="text-base font-medium">博客分类</span>
        <ElButton
          v-access:code="'blog:category:add'"
          type="primary"
          @click="openCreate()"
        >
          新增分类
        </ElButton>
      </div>

      <!-- 树形表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="分类名称" min-width="160" />
        <el-table-column prop="slug" label="Slug" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-2">
              <ElButton
                v-access:code="'blog:category:add'"
                link
                type="primary"
                @click="openCreate(row.id)"
              >
                添加子分类
              </ElButton>
              <ElButton
                v-access:code="'blog:category:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </ElButton>
              <ElButton
                v-access:code="'blog:category:delete'"
                link
                type="danger"
                @click="handleDelete(row)"
              >
                删除
              </ElButton>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建 / 编辑 弹窗 -->
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
        <ElFormItem label="分类名称" prop="name">
          <ElInput
            v-model="editForm.name"
            placeholder="请输入分类名称"
            @blur="autoSlug"
          />
        </ElFormItem>
        <ElFormItem label="Slug" prop="slug">
          <ElInput
            v-model="editForm.slug"
            placeholder="URL 标识，如 tech-news"
          />
        </ElFormItem>
        <ElFormItem label="父分类" prop="parentId">
          <ElSelect
            v-model="editForm.parentId"
            placeholder="顶级分类（可选）"
            clearable
            style="width: 100%"
          >
            <ElOption
              v-for="item in flattenTree(tableData, editingId ?? undefined)"
              :key="item.id"
              :value="item.id"
              :label="item.label"
            />
          </ElSelect>
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
