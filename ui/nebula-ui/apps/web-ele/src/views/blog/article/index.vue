<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogArticleApi, BlogCategoryApi, BlogTagApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElDrawer,
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
import { MdEditor } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogArticleApi,
  deleteBlogArticleApi,
  getBlogArticleDetailApi,
  getBlogArticlePageApi,
  getBlogCategoryTreeApi,
  getBlogTagListApi,
  updateBlogArticleApi,
} from '#/api';

defineOptions({ name: 'BlogArticle' });

const statusOptions = [
  { label: '草稿', value: 'draft', tagType: 'info' },
  { label: '已发布', value: 'published', tagType: 'success' },
  { label: '已归档', value: 'archived', tagType: 'warning' },
] as const;

const visibilityOptions = [
  { label: '公开', value: 'public' },
  { label: '私有', value: 'private' },
] as const;

const sourceTypeOptions = [
  { label: '手动', value: 'manual' },
  { label: 'AI', value: 'ai' },
  { label: '导入', value: 'import' },
] as const;

const gridOptions: VxeTableGridOptions<BlogArticleApi.ArticleListItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'title', title: '文章标题', minWidth: 220 },
    { field: 'slug', title: 'Slug', minWidth: 180 },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'visibility',
      title: '可见性',
      width: 100,
      slots: { default: 'visibility' },
    },
    {
      field: 'categories',
      title: '分类',
      minWidth: 180,
      slots: { default: 'categories' },
    },
    {
      field: 'tags',
      title: '标签',
      minWidth: 180,
      slots: { default: 'tags' },
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
      width: 160,
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
        return await getBlogArticlePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status || undefined,
          visibility: formValues?.visibility || undefined,
          sourceType: formValues?.sourceType || undefined,
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
      {
        component: 'Input',
        fieldName: 'keyword',
        label: '关键词',
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: statusOptions.map(({ label, value }) => ({ label, value })),
        },
      },
      {
        component: 'Select',
        fieldName: 'visibility',
        label: '可见性',
        componentProps: {
          clearable: true,
          options: visibilityOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'sourceType',
        label: '来源',
        componentProps: {
          clearable: true,
          options: sourceTypeOptions,
        },
      },
    ],
  },
});

const categoryTree = ref<BlogCategoryApi.CategoryItem[]>([]);
const categoryOptions = ref<Array<{ id: number | string; label: string }>>([]);
const tagOptions = ref<BlogTagApi.TagItem[]>([]);

async function loadRelationOptions() {
  const [categories, tags] = await Promise.all([
    getBlogCategoryTreeApi(),
    getBlogTagListApi(),
  ]);
  categoryTree.value = categories ?? [];
  categoryOptions.value = flattenCategoryTree(categoryTree.value);
  tagOptions.value = tags ?? [];
}

onMounted(loadRelationOptions);

function flattenCategoryTree(nodes: BlogCategoryApi.CategoryItem[]) {
  const result: Array<{ id: number | string; label: string }> = [];

  function walk(list: BlogCategoryApi.CategoryItem[], depth = 0) {
    for (const node of list) {
      result.push({
        id: node.id,
        label: `${'--'.repeat(depth)}${depth > 0 ? ' ' : ''}${node.name}`,
      });
      if (node.children?.length) {
        walk(node.children, depth + 1);
      }
    }
  }

  walk(nodes);
  return result;
}

function getStatusLabel(status?: string) {
  return statusOptions.find((item) => item.value === status)?.label ?? status ?? '-';
}

function getStatusTagType(status?: string) {
  return statusOptions.find((item) => item.value === status)?.tagType ?? 'info';
}

function getVisibilityLabel(visibility?: string) {
  return (
    visibilityOptions.find((item) => item.value === visibility)?.label ??
    visibility ??
    '-'
  );
}

function reloadGrid() {
  gridApi.query();
}

type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const settingsDrawerVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const detailLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  title: string;
  slug: string;
  summary: string;
  content: string;
  coverFileId: number | null;
  status: string;
  visibility: string;
  sourceType: string;
  isOriginal: boolean;
  publishedAt: string;
  categoryIds: Array<number | string>;
  tagIds: Array<number | string>;
}>({
  title: '',
  slug: '',
  summary: '',
  content: '',
  coverFileId: null,
  status: 'draft',
  visibility: 'public',
  sourceType: 'manual',
  isOriginal: true,
  publishedAt: '',
  categoryIds: [],
  tagIds: [],
});

const editRules: FormRules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { max: 200, message: '最多 200 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '仅允许小写字母、数字和中划线',
      trigger: 'blur',
    },
    { max: 220, message: '最多 220 个字符', trigger: 'blur' },
  ],
  summary: [{ max: 500, message: '最多 500 个字符', trigger: 'blur' }],
  content: [
    { required: true, message: '请输入文章正文', trigger: 'blur' },
  ],
};

function resetEditForm() {
  editForm.title = '';
  editForm.slug = '';
  editForm.summary = '';
  editForm.content = '';
  editForm.coverFileId = null;
  editForm.status = 'draft';
  editForm.visibility = 'public';
  editForm.sourceType = 'manual';
  editForm.isOriginal = true;
  editForm.publishedAt = '';
  editForm.categoryIds = [];
  editForm.tagIds = [];
  editFormRef.value?.clearValidate();
}

function autoSlug() {
  if (editMode.value === 'edit' || editForm.slug) return;
  editForm.slug = editForm.title
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

async function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetEditForm();
  editDialogVisible.value = true;
  await ensureRelationOptions();
}

async function openEdit(row: BlogArticleApi.ArticleListItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetEditForm();
  editDialogVisible.value = true;
  await ensureRelationOptions();

  detailLoading.value = true;
  try {
    const detail = await getBlogArticleDetailApi(row.id);
    fillEditForm(detail);
  } finally {
    detailLoading.value = false;
  }
}

async function ensureRelationOptions() {
  if (categoryOptions.value.length === 0 && tagOptions.value.length === 0) {
    await loadRelationOptions();
  }
}

function fillEditForm(detail: BlogArticleApi.ArticleDetail) {
  editForm.title = detail.title ?? '';
  editForm.slug = detail.slug ?? '';
  editForm.summary = detail.summary ?? '';
  editForm.content = detail.content ?? '';
  editForm.coverFileId =
    detail.coverFileId == null ? null : Number(detail.coverFileId);
  editForm.status = detail.status ?? 'draft';
  editForm.visibility = detail.visibility ?? 'public';
  editForm.sourceType = detail.sourceType ?? 'manual';
  editForm.isOriginal = detail.isOriginal ?? true;
  editForm.publishedAt = detail.publishedAt ?? '';
  editForm.categoryIds = detail.categories?.map((item) => item.id) ?? [];
  editForm.tagIds = detail.tags?.map((item) => item.id) ?? [];
}

function buildPayload(): BlogArticleApi.ArticleUpdateParams {
  return {
    title: editForm.title,
    slug: editForm.slug,
    summary: editForm.summary || undefined,
    content: editForm.content,
    coverFileId: editForm.coverFileId ?? undefined,
    status: editForm.status,
    visibility: editForm.visibility,
    sourceType: editForm.sourceType,
    isOriginal: editForm.isOriginal,
    publishedAt: editForm.publishedAt || undefined,
    categoryIds: editForm.categoryIds,
    tagIds: editForm.tagIds,
  };
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = buildPayload();
    if (editMode.value === 'create') {
      await createBlogArticleApi(payload as BlogArticleApi.ArticleCreateParams);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogArticleApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: BlogArticleApi.ArticleListItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除文章「${row.title}」？删除后不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  await deleteBlogArticleApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:article:add'"
          type="primary"
          @click="openCreate"
        >
          新增文章
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #visibility="{ row }">
        <ElTag :type="row.visibility === 'public' ? 'success' : 'info'" size="small">
          {{ getVisibilityLabel(row.visibility) }}
        </ElTag>
      </template>

      <template #categories="{ row }">
        <div class="flex flex-wrap justify-center gap-1">
          <ElTag
            v-for="category in row.categories ?? []"
            :key="category.id"
            size="small"
          >
            {{ category.name }}
          </ElTag>
          <span v-if="!row.categories?.length">-</span>
        </div>
      </template>

      <template #tags="{ row }">
        <div class="flex flex-wrap justify-center gap-1">
          <ElTag v-for="tag in row.tags ?? []" :key="tag.id" size="small" type="info">
            {{ tag.name }}
          </ElTag>
          <span v-if="!row.tags?.length">-</span>
        </div>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:article:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:article:delete'"
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
      :show-close="false"
      class="article-editor-dialog"
      fullscreen
    >
      <template #header>
        <div class="article-editor-header">
          <span class="article-editor-title">
            {{ editMode === 'create' ? '新增文章' : '编辑文章' }}
          </span>
          <div class="article-editor-header-actions">
            <ElButton @click="settingsDrawerVisible = true">
              更多设置
            </ElButton>
            <ElButton @click="editDialogVisible = false">取消</ElButton>
            <ElButton
              :loading="editLoading"
              type="primary"
              @click="submitEdit"
            >
              保存
            </ElButton>
          </div>
        </div>
      </template>

      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        class="article-editor-form"
        label-position="top"
      >
        <ElFormItem prop="title" class="article-title-item">
          <ElInput
            v-model="editForm.title"
            class="article-title-input"
            maxlength="200"
            placeholder="请输入文章标题"
            @blur="autoSlug"
          />
        </ElFormItem>

        <div class="article-meta-row">
          <ElFormItem
            class="article-meta-item"
            label="分类"
            prop="categoryIds"
          >
            <ElSelect
              v-model="editForm.categoryIds"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="请选择分类"
              style="width: 100%"
            >
              <ElOption
                v-for="item in categoryOptions"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem
            class="article-meta-item"
            label="标签"
            prop="tagIds"
          >
            <ElSelect
              v-model="editForm.tagIds"
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="请选择标签"
              style="width: 100%"
            >
              <ElOption
                v-for="item in tagOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
        </div>

        <ElFormItem class="article-content-item" prop="content">
          <MdEditor
            v-model="editForm.content"
            class="article-md-editor"
            :preview-theme="'github'"
            language="zh-CN"
          />
        </ElFormItem>
      </ElForm>

      <ElDrawer
        v-model="settingsDrawerVisible"
        :append-to-body="true"
        size="420px"
        title="文章设置"
      >
        <ElForm :model="editForm" label-position="top">
          <ElFormItem label="Slug">
            <ElInput
              v-model="editForm.slug"
              placeholder="URL 标识，如 product-update"
            />
          </ElFormItem>

          <ElFormItem label="摘要">
            <ElInput
              v-model="editForm.summary"
              :rows="3"
              maxlength="500"
              placeholder="可选"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>

          <ElFormItem label="封面文件ID">
            <ElInputNumber
              v-model="editForm.coverFileId"
              :min="1"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>

          <ElFormItem label="状态">
            <ElSelect v-model="editForm.status" style="width: 100%">
              <ElOption
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="可见性">
            <ElSelect v-model="editForm.visibility" style="width: 100%">
              <ElOption
                v-for="item in visibilityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="来源">
            <ElSelect v-model="editForm.sourceType" style="width: 100%">
              <ElOption
                v-for="item in sourceTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="发布时间">
            <ElDatePicker
              v-model="editForm.publishedAt"
              clearable
              placeholder="可选"
              style="width: 100%"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </ElFormItem>

          <ElFormItem label="原创">
            <ElSwitch v-model="editForm.isOriginal" />
          </ElFormItem>
        </ElForm>
      </ElDrawer>
    </ElDialog>
  </Page>
</template>

<style scoped>
.article-editor-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 12px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.article-editor-dialog :deep(.el-dialog__body) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  padding: 0;
  overflow: hidden;
}

.article-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.article-editor-title {
  font-size: 16px;
  font-weight: 600;
}

.article-editor-header-actions {
  display: flex;
  gap: 8px;
}

.article-editor-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 16px 24px 0;
}

.article-title-item :deep(.el-form-item__content) {
  line-height: 1.2;
}

.article-title-input :deep(.el-input__wrapper) {
  padding-left: 0;
  padding-right: 0;
  background-color: transparent;
  box-shadow: none !important;
}

.article-title-input :deep(.el-input__inner) {
  height: 48px;
  font-size: 26px;
  font-weight: 600;
}

.article-meta-row {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}

.article-meta-item {
  flex: 1;
  margin-bottom: 12px;
}

.article-content-item {
  display: flex;
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
}

.article-content-item :deep(.el-form-item__content) {
  display: flex;
  flex: 1;
  min-height: 0;
}

.article-md-editor {
  flex: 1;
  min-height: 0;
  height: 100% !important;
}
</style>
