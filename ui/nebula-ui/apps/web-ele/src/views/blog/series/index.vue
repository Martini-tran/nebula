<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogSeriesApi } from '#/api';

import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElDivider,
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
  ElTooltip,
  ElUpload,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogSeriesApi,
  deleteBlogSeriesApi,
  getBlogSeriesDetailApi,
  getBlogSeriesPageApi,
  updateBlogSeriesApi,
  uploadBlogFileApi,
} from '#/api';

defineOptions({ name: 'BlogSeries' });

const router = useRouter();

const statusOptions = [
  { label: '草稿', value: 'draft', tagType: 'info' as const },
  { label: '已发布', value: 'published', tagType: 'success' as const },
  { label: '已归档', value: 'archived', tagType: 'warning' as const },
];

const visibilityOptions = [
  { label: '公开', value: 'public' },
  { label: '私有', value: 'private' },
];

function getStatusLabel(status?: string) {
  return statusOptions.find((s) => s.value === status)?.label ?? status ?? '-';
}

function getStatusTagType(status?: string) {
  return statusOptions.find((s) => s.value === status)?.tagType ?? 'info';
}

function getVisibilityLabel(value?: string) {
  return visibilityOptions.find((v) => v.value === value)?.label ?? value ?? '-';
}

const gridOptions: VxeTableGridOptions<BlogSeriesApi.SeriesItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    {
      field: 'cover',
      title: '封面',
      width: 100,
      slots: { default: 'cover' },
    },
    { field: 'name', title: '系列名称', minWidth: 200 },
    { field: 'slug', title: 'Slug', minWidth: 160 },
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
      field: 'isFinished',
      title: '完结',
      width: 80,
      slots: { default: 'isFinished' },
    },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 220,
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
        return await getBlogSeriesPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status || undefined,
          visibility: formValues?.visibility || undefined,
          isFinished:
            formValues?.isFinished === '' || formValues?.isFinished == null
              ? undefined
              : formValues.isFinished,
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
        fieldName: 'isFinished',
        label: '是否完结',
        componentProps: {
          clearable: true,
          options: [
            { label: '已完结', value: true },
            { label: '连载中', value: false },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// -------------------- 编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const detailLoading = ref(false);
const coverUploading = ref(false);
const coverExplicitlyRemoved = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  name: string;
  slug: string;
  description: string;
  coverFileId: null | number | string;
  coverPreviewUrl: string;
  status: string;
  visibility: string;
  isFinished: boolean;
  sortOrder: number;
}>({
  name: '',
  slug: '',
  description: '',
  coverFileId: null,
  coverPreviewUrl: '',
  status: 'draft',
  visibility: 'public',
  isFinished: false,
  sortOrder: 0,
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入系列名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '仅允许小写字母、数字和中划线',
      trigger: 'blur',
    },
    { max: 120, message: '最多 120 个字符', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '最多 500 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.name = '';
  editForm.slug = '';
  editForm.description = '';
  editForm.coverFileId = null;
  editForm.coverPreviewUrl = '';
  editForm.status = 'draft';
  editForm.visibility = 'public';
  editForm.isFinished = false;
  editForm.sortOrder = 0;
  coverExplicitlyRemoved.value = false;
  editFormRef.value?.clearValidate();
}

function autoSlug() {
  if (editMode.value === 'edit' || editForm.slug) return;
  generateSlug();
}

function generateSlug() {
  if (!editForm.name) {
    ElMessage.warning('请先填写系列名称');
    return;
  }
  const ascii = editForm.name
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  editForm.slug = ascii || `series-${Date.now().toString(36)}`;
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

async function openEdit(row: BlogSeriesApi.SeriesItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editDialogVisible.value = true;

  detailLoading.value = true;
  try {
    const detail = await getBlogSeriesDetailApi(row.id);
    editForm.name = detail.name ?? '';
    editForm.slug = detail.slug ?? '';
    editForm.description = detail.description ?? '';
    editForm.coverFileId = detail.coverFileId ?? null;
    editForm.coverPreviewUrl = detail.coverUrl ?? '';
    editForm.status = detail.status ?? 'draft';
    editForm.visibility = detail.visibility ?? 'public';
    editForm.isFinished = detail.isFinished ?? false;
    editForm.sortOrder = detail.sortOrder ?? 0;
  } finally {
    detailLoading.value = false;
  }
}

async function handleCoverChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return;
  coverUploading.value = true;
  try {
    const result = await uploadBlogFileApi(uploadFile.raw, 'cover');
    editForm.coverFileId = result.id;
    editForm.coverPreviewUrl = result.url;
    coverExplicitlyRemoved.value = false;
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
  coverExplicitlyRemoved.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createBlogSeriesApi({
        name: editForm.name,
        slug: editForm.slug,
        description: editForm.description || undefined,
        cover_file_id: editForm.coverFileId ?? undefined,
        status: editForm.status,
        visibility: editForm.visibility,
        is_finished: editForm.isFinished,
        sort_order: editForm.sortOrder,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogSeriesApi(editingId.value, {
        name: editForm.name,
        slug: editForm.slug,
        description: editForm.description || undefined,
        cover_file_id: editForm.coverFileId ?? undefined,
        clear_cover_file_id: coverExplicitlyRemoved.value ? true : undefined,
        status: editForm.status,
        visibility: editForm.visibility,
        is_finished: editForm.isFinished,
        sort_order: editForm.sortOrder,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: BlogSeriesApi.SeriesItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除系列「${row.name}」？目录与文章关联将被一并清理，删除后不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogSeriesApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

function openCatalog(row: BlogSeriesApi.SeriesItem) {
  router
    .push({
      path: `/blog/series/${row.id}/catalog`,
      query: { name: row.name },
    })
    .catch((err) => {
      // 后端动态路由未下发（菜单未配置或未授权）时 push 会静默失败，这里显式提示
      console.error('Navigate to series catalog failed:', err);
      ElMessage.warning('系列目录页未授权或菜单未配置');
    });
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:series:add'"
          type="primary"
          @click="openCreate"
        >
          新增系列
        </ElButton>
      </template>

      <template #cover="{ row }">
        <img
          v-if="row.coverUrl"
          :src="row.coverUrl"
          alt="cover"
          class="series-cover-thumb"
        />
        <span v-else class="series-empty">-</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #visibility="{ row }">
        <ElTag
          :type="row.visibility === 'public' ? 'success' : 'info'"
          size="small"
        >
          {{ getVisibilityLabel(row.visibility) }}
        </ElTag>
      </template>

      <template #isFinished="{ row }">
        <ElTag :type="row.isFinished ? 'success' : 'warning'" size="small">
          {{ row.isFinished ? '已完结' : '连载中' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:series:query'"
            link
            type="primary"
            @click="openCatalog(row)"
          >
            目录
          </ElButton>
          <ElButton
            v-access:code="'blog:series:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:series:delete'"
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
      :title="editMode === 'create' ? '新增系列' : '编辑系列'"
      width="720"
      append-to-body
      destroy-on-close
    >
      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <div class="form-section-title">基本信息</div>
        <div class="form-grid">
          <ElFormItem label="系列名称" prop="name" class="form-grid__full">
            <ElInput
              v-model="editForm.name"
              placeholder="请输入系列名称，例如「Vue3 源码精讲」"
              maxlength="100"
              show-word-limit
              @blur="autoSlug"
            />
          </ElFormItem>

          <ElFormItem label="Slug" prop="slug" class="form-grid__full">
            <div class="slug-row">
              <ElInput
                v-model="editForm.slug"
                placeholder="URL 标识，仅小写字母、数字、中划线"
              />
              <ElTooltip content="基于系列名称自动生成" placement="top">
                <ElButton :disabled="!editForm.name" plain @click="generateSlug">
                  自动生成
                </ElButton>
              </ElTooltip>
            </div>
          </ElFormItem>

          <ElFormItem label="简介" prop="description" class="form-grid__full">
            <ElInput
              v-model="editForm.description"
              :rows="3"
              maxlength="500"
              placeholder="可选，用于系列详情页展示"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>

          <ElFormItem label="封面" class="form-grid__full">
            <div class="cover-wrap">
              <div v-if="editForm.coverPreviewUrl" class="cover-preview">
                <img
                  :src="editForm.coverPreviewUrl"
                  alt="封面预览"
                  class="cover-img"
                />
                <ElButton
                  class="cover-remove"
                  size="small"
                  type="danger"
                  link
                  @click="removeCover"
                >
                  移除
                </ElButton>
              </div>
              <div class="cover-actions">
                <ElUpload
                  :auto-upload="false"
                  :show-file-list="false"
                  accept="image/*"
                  @change="handleCoverChange"
                >
                  <ElButton
                    :loading="coverUploading"
                    size="small"
                    type="primary"
                    plain
                  >
                    {{ editForm.coverPreviewUrl ? '重新上传' : '上传封面' }}
                  </ElButton>
                </ElUpload>
                <span class="cover-hint">支持 JPG、PNG、WebP、GIF，最大 10MB</span>
              </div>
            </div>
          </ElFormItem>
        </div>

        <ElDivider class="form-divider" />
        <div class="form-section-title">发布设置</div>
        <div class="form-grid">
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

          <ElFormItem label="是否完结">
            <ElSwitch
              v-model="editForm.isFinished"
              active-text="已完结"
              inactive-text="连载中"
              inline-prompt
            />
          </ElFormItem>

          <ElFormItem label="排序">
            <ElInputNumber
              v-model="editForm.sortOrder"
              :min="0"
              :max="9999"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
        </div>
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
.series-cover-thumb {
  width: 64px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  display: block;
  margin: 0 auto;
}

.series-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.form-section-title {
  margin-bottom: 12px;
  padding-left: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
}

.form-divider {
  margin: 12px 0 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.form-grid__full {
  grid-column: 1 / -1;
}

.slug-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.slug-row :deep(.el-input) {
  flex: 1;
}

.cover-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.cover-preview {
  position: relative;
  display: inline-block;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  max-width: 200px;
}

.cover-img {
  display: block;
  width: 100%;
  max-height: 120px;
  object-fit: cover;
}

.cover-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff !important;
  border-radius: 4px;
  padding: 2px 6px;
}

.cover-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
