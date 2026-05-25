<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogArticleApi, BlogCategoryApi, BlogTagApi } from '#/api';

import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
  ElUpload,
} from 'element-plus';
import { MdEditor } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogArticleApi,
  createBlogTagApi,
  deleteBlogArticleApi,
  getBlogArticleDetailApi,
  getBlogArticlePageApi,
  getBlogCategoryTreeApi,
  getBlogTagListApi,
  updateBlogArticleApi,
  uploadBlogFileApi,
} from '#/api';

defineOptions({ name: 'BlogArticle' });

// ------------------------------------------------------------------ 暗色主题检测
// 框架通过切换 <html class="dark"> 控制全局主题，
// 用 MutationObserver 监听该 class 变化并同步给 MdEditor 的 theme 属性。
const isDark = ref(
  typeof document !== 'undefined' &&
    document.documentElement.classList.contains('dark'),
);
let themeObserver: MutationObserver | null = null;

onMounted(() => {
  isDark.value = document.documentElement.classList.contains('dark');
  themeObserver = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark');
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  });
});

onUnmounted(() => {
  themeObserver?.disconnect();
});

const editorTheme = computed<'dark' | 'light'>(() =>
  isDark.value ? 'dark' : 'light',
);

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
const coverUploading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  title: string;
  slug: string;
  summary: string;
  content: string;
  coverFileId: number | null;
  /** 封面图片预览 URL（不提交给后端，仅用于本地预览） */
  coverPreviewUrl: string;
  status: string;
  visibility: string;
  sourceType: string;
  isOriginal: boolean;
  publishedAt: string;
  categoryIds: Array<number | string>;
  tagIds: Array<number | string>;
  changeNote: string;
}>({
  title: '',
  slug: '',
  summary: '',
  content: '',
  coverFileId: null,
  coverPreviewUrl: '',
  status: 'draft',
  visibility: 'public',
  sourceType: 'manual',
  isOriginal: true,
  publishedAt: '',
  categoryIds: [],
  tagIds: [],
  changeNote: '',
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
  editForm.coverPreviewUrl = '';
  editForm.status = 'draft';
  editForm.visibility = 'public';
  editForm.sourceType = 'manual';
  editForm.isOriginal = true;
  editForm.publishedAt = '';
  editForm.categoryIds = [];
  editForm.tagIds = [];
  editForm.changeNote = '';
  editFormRef.value?.clearValidate();
}

function autoSlug() {
  if (editMode.value === 'edit' || editForm.slug) return;
  const ascii = editForm.title
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  editForm.slug =
    ascii ||
    (editForm.title
      ? `post-${Date.now().toString(36)}`
      : '');
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
  // detail.content 由后端从 OSS 读取后一并返回
  editForm.content = detail.content ?? '';
  editForm.coverFileId =
    detail.coverFileId == null ? null : Number(detail.coverFileId);
  // 设置封面预览 URL
  editForm.coverPreviewUrl = detail.coverUrl ?? '';
  editForm.status = detail.status ?? 'draft';
  editForm.visibility = detail.visibility ?? 'public';
  editForm.sourceType = detail.sourceType ?? 'manual';
  editForm.isOriginal = detail.isOriginal ?? true;
  editForm.publishedAt = detail.publishedAt ?? '';
  editForm.categoryIds = detail.categories?.map((item) => item.id) ?? [];
  editForm.tagIds = detail.tags?.map((item) => item.id) ?? [];
  editForm.changeNote = '';
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
    changeNote: editForm.changeNote || undefined,
  };
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    await materializePendingTags();
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

async function saveAs(targetStatus: 'draft' | 'published') {
  editForm.status = targetStatus;
  if (targetStatus === 'published' && !editForm.publishedAt) {
    editForm.publishedAt = new Date().toISOString().slice(0, 19);
  }
  await submitEdit();
}

function slugifyTagName(name: string) {
  const ascii = name
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  return ascii || `tag-${Date.now().toString(36)}`;
}

async function materializePendingTags() {
  const ids = editForm.tagIds;
  for (let i = 0; i < ids.length; i++) {
    const value = ids[i];
    if (typeof value !== 'string') continue;
    const matched = tagOptions.value.find((tag) => tag.name === value);
    if (matched) {
      ids[i] = matched.id;
      continue;
    }
    const slug = slugifyTagName(value);
    const newId = await createBlogTagApi({ name: value, slug });
    tagOptions.value = [
      ...tagOptions.value,
      { id: newId, name: value, slug },
    ];
    ids[i] = newId;
  }
}

// ------------------------------------------------------------------ 封面上传

/**
 * 封面图片选中后自动上传到 OSS
 */
async function handleCoverChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return;
  coverUploading.value = true;
  try {
    const result = await uploadBlogFileApi(uploadFile.raw, 'cover');
    editForm.coverFileId = result.id as number;
    editForm.coverPreviewUrl = result.url;
    ElMessage.success('封面上传成功');
  } catch {
    ElMessage.error('封面上传失败，请重试');
  } finally {
    coverUploading.value = false;
  }
}

function removeCover() {
  editForm.coverFileId = null;
  editForm.coverPreviewUrl = '';
}

// ------------------------------------------------------------------ 编辑器图片上传

/**
 * MdEditor 图片上传回调：接收文件列表，上传后通过 callback 返回 URL 列表。
 * md-editor-v3 会将返回的 URL 自动插入到编辑器内容中。
 */
async function handleUploadImg(
  files: File[],
  callback: (urls: string[]) => void,
) {
  const urls: string[] = [];
  for (const file of files) {
    try {
      const result = await uploadBlogFileApi(file, 'image');
      if (result?.url) urls.push(result.url);
    } catch {
      ElMessage.error(`图片「${file.name}」上传失败`);
    }
  }
  callback(urls);
}

// ------------------------------------------------------------------ 计算属性

const editorTitle = computed(() =>
  editMode.value === 'create' ? '新建文章' : '编辑文章',
);

const wordCount = computed(() => {
  const text = editForm.content || '';
  const cn = (text.match(/[一-龥]/g) || []).length;
  const en = (text.match(/[A-Za-z0-9]+/g) || []).length;
  return cn + en;
});

const selectedCategoryNames = computed(() => {
  const map = new Map(categoryOptions.value.map((c) => [c.id, c.label]));
  return editForm.categoryIds
    .map((id) => map.get(id))
    .filter(Boolean) as string[];
});

// ------------------------------------------------------------------ 删除

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
        <div class="ae-header">
          <div class="ae-header__left">
            <ElButton circle text @click="editDialogVisible = false">
              <span class="ae-back">←</span>
            </ElButton>
            <span class="ae-header__title">{{ editorTitle }}</span>
            <span class="ae-header__status">
              <ElTag
                :type="getStatusTagType(editForm.status)"
                effect="plain"
                round
                size="small"
              >
                {{ getStatusLabel(editForm.status) }}
              </ElTag>
            </span>
          </div>
          <div class="ae-header__center ae-meta">
            <span>共 {{ wordCount }} 字</span>
          </div>
          <div class="ae-header__right">
            <ElButton text @click="settingsDrawerVisible = true">
              文章设置
            </ElButton>
            <ElButton :loading="editLoading" @click="saveAs('draft')">
              保存草稿
            </ElButton>
            <ElButton
              :loading="editLoading"
              type="primary"
              @click="saveAs('published')"
            >
              {{ editForm.status === 'published' ? '更新发布' : '立即发布' }}
            </ElButton>
          </div>
        </div>
      </template>

      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        class="ae-form"
      >
        <div class="ae-canvas">
          <div class="ae-paper">
            <ElFormItem prop="title" class="ae-title-item">
              <ElInput
                v-model="editForm.title"
                class="ae-title-input"
                maxlength="200"
                placeholder="输入文章标题..."
                @blur="autoSlug"
              />
            </ElFormItem>

            <div class="ae-chips">
              <ElSelect
                v-model="editForm.categoryIds"
                class="ae-chip-select"
                clearable
                collapse-tags
                collapse-tags-tooltip
                filterable
                multiple
                placeholder="+ 添加分类"
              >
                <ElOption
                  v-for="item in categoryOptions"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </ElSelect>

              <ElSelect
                v-model="editForm.tagIds"
                allow-create
                class="ae-chip-select"
                clearable
                collapse-tags
                collapse-tags-tooltip
                default-first-option
                filterable
                multiple
                placeholder="+ 添加标签 (回车新建)"
              >
                <ElOption
                  v-for="item in tagOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </ElSelect>

              <span v-if="!editForm.categoryIds.length && !editForm.tagIds.length" class="ae-chip-hint">
                选择已有分类、标签，或直接输入新标签后回车
              </span>
            </div>

            <ElFormItem class="ae-content-item" prop="content">
              <MdEditor
                v-model="editForm.content"
                class="ae-md-editor"
                :preview-theme="'github'"
                :theme="editorTheme"
                :toolbars-exclude="['github', 'save']"
                :on-upload-img="handleUploadImg"
                language="zh-CN"
              />
            </ElFormItem>
          </div>
        </div>
      </ElForm>

      <ElDrawer
        v-model="settingsDrawerVisible"
        :append-to-body="true"
        size="420px"
        title="文章设置"
      >
        <ElForm :model="editForm" label-position="top" class="ae-drawer-form">
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

          <!-- 封面图片上传 -->
          <ElFormItem label="封面图片">
            <div class="ae-cover-wrap">
              <div v-if="editForm.coverPreviewUrl" class="ae-cover-preview">
                <img :src="editForm.coverPreviewUrl" alt="封面预览" class="ae-cover-img" />
                <ElButton
                  class="ae-cover-remove"
                  size="small"
                  type="danger"
                  link
                  @click="removeCover"
                >
                  移除
                </ElButton>
              </div>
              <ElUpload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                @change="handleCoverChange"
              >
                <ElButton :loading="coverUploading" size="small" type="primary" plain>
                  {{ editForm.coverPreviewUrl ? '重新上传' : '上传封面' }}
                </ElButton>
              </ElUpload>
              <span class="ae-cover-hint">支持 JPG、PNG、WebP、GIF，最大 10MB</span>
            </div>
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

          <!-- 变更说明：仅编辑模式显示，写入快照 changeNote -->
          <ElFormItem v-if="editMode === 'edit'" label="变更说明（快照备注）">
            <ElInput
              v-model="editForm.changeNote"
              :rows="2"
              maxlength="500"
              placeholder="记录本次修改内容，可选"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>

          <div v-if="selectedCategoryNames.length" class="ae-drawer-summary">
            已选分类：
            <ElTag
              v-for="name in selectedCategoryNames"
              :key="name"
              class="mr-1"
              size="small"
            >
              {{ name }}
            </ElTag>
          </div>
        </ElForm>
      </ElDrawer>
    </ElDialog>
  </Page>
</template>

<style scoped>
.article-editor-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 10px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.article-editor-dialog :deep(.el-dialog__body) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  padding: 0;
  overflow: hidden;
  background: var(--el-fill-color-light);
}

.ae-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
}

.ae-header__left,
.ae-header__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ae-header__right {
  justify-content: flex-end;
}

.ae-header__title {
  font-size: 15px;
  font-weight: 600;
}

.ae-header__center {
  display: flex;
  justify-content: center;
}

.ae-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ae-back {
  font-size: 18px;
  line-height: 1;
}

.ae-form {
  display: flex;
  flex: 1;
  min-height: 0;
}

.ae-canvas {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px 16px 32px;
  display: flex;
  justify-content: center;
}

.ae-paper {
  width: 100%;
  max-width: 980px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ae-title-item {
  margin-bottom: 0;
}

.ae-title-item :deep(.el-form-item__content) {
  line-height: 1.2;
}

.ae-title-input :deep(.el-input__wrapper) {
  padding: 0;
  background: transparent;
  box-shadow: none !important;
}

.ae-title-input :deep(.el-input__inner) {
  height: 56px;
  font-size: 32px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.ae-title-input :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
  font-weight: 600;
}

.ae-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ae-chip-select {
  flex: 1 1 240px;
  min-width: 200px;
}

.ae-chip-select :deep(.el-select__wrapper) {
  background: transparent;
  box-shadow: none !important;
  padding-left: 0;
}

.ae-chip-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.ae-content-item {
  flex: 1;
  min-height: 520px;
  margin-bottom: 0;
  display: flex;
}

.ae-content-item :deep(.el-form-item__content) {
  flex: 1;
  display: flex;
  min-height: 0;
}

.ae-md-editor {
  flex: 1;
  height: auto !important;
  min-height: 520px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.ae-drawer-form {
  padding: 4px 4px 24px;
}

.ae-drawer-summary {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 封面上传区 */
.ae-cover-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.ae-cover-preview {
  position: relative;
  display: inline-block;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

.ae-cover-img {
  display: block;
  width: 100%;
  max-height: 160px;
  object-fit: cover;
}

.ae-cover-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff !important;
  border-radius: 4px;
  padding: 2px 6px;
}

.ae-cover-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
