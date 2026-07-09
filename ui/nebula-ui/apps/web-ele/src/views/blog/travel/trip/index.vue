<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogTravelApi } from '#/api';

import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDatePicker,
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
  ElUpload,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogTravelTripApi,
  deleteBlogTravelTripApi,
  getBlogTravelTripDetailApi,
  getBlogTravelTripPageApi,
  updateBlogTravelTripApi,
  updateBlogTravelTripStatusApi,
  uploadBlogFileApi,
} from '#/api';

defineOptions({ name: 'BlogTravelTrip' });

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

const currencyOptions = [
  { label: 'CNY 人民币', value: 'CNY' },
  { label: 'USD 美元', value: 'USD' },
  { label: 'EUR 欧元', value: 'EUR' },
  { label: 'JPY 日元', value: 'JPY' },
  { label: 'HKD 港币', value: 'HKD' },
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

const gridOptions: VxeTableGridOptions<BlogTravelApi.Trip> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    {
      field: 'cover',
      title: '封面',
      width: 100,
      slots: { default: 'cover' },
    },
    { field: 'title', title: '标题', minWidth: 200 },
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
      field: 'period',
      title: '行程',
      width: 220,
      slots: { default: 'period' },
    },
    { field: 'persons', title: '人数', width: 80, align: 'center' },
    {
      field: 'cost',
      title: '总花费',
      width: 140,
      slots: { default: 'cost' },
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
      width: 280,
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
        const dateRange = formValues?.dateRange as
          | [string, string]
          | null
          | undefined;
        return await getBlogTravelTripPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status || undefined,
          visibility: formValues?.visibility || undefined,
          startDateFrom: dateRange?.[0] || undefined,
          startDateTo: dateRange?.[1] || undefined,
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
        componentProps: { clearable: true, options: visibilityOptions },
      },
      {
        component: 'DatePicker',
        fieldName: 'dateRange',
        label: '出发日期',
        componentProps: {
          type: 'daterange',
          valueFormat: 'YYYY-MM-DD',
          startPlaceholder: '开始',
          endPlaceholder: '结束',
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
  title: string;
  slug: string;
  summary: string;
  coverFileId: null | number | string;
  coverPreviewUrl: string;
  status: string;
  visibility: string;
  startDate: string;
  endDate: string;
  persons: null | number;
  costTotal: null | number;
  costCurrency: string;
}>({
  title: '',
  slug: '',
  summary: '',
  coverFileId: null,
  coverPreviewUrl: '',
  status: 'draft',
  visibility: 'public',
  startDate: '',
  endDate: '',
  persons: null,
  costTotal: null,
  costCurrency: 'CNY',
});

const editRules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '最多 200 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '仅允许小写字母、数字和中划线',
      trigger: 'blur',
    },
    { max: 200, message: '最多 200 个字符', trigger: 'blur' },
  ],
  summary: [{ max: 500, message: '最多 500 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.title = '';
  editForm.slug = '';
  editForm.summary = '';
  editForm.coverFileId = null;
  editForm.coverPreviewUrl = '';
  editForm.status = 'draft';
  editForm.visibility = 'public';
  editForm.startDate = '';
  editForm.endDate = '';
  editForm.persons = null;
  editForm.costTotal = null;
  editForm.costCurrency = 'CNY';
  coverExplicitlyRemoved.value = false;
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
  editForm.slug = ascii || `trip-${Date.now().toString(36)}`;
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

async function openEdit(row: BlogTravelApi.Trip) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editDialogVisible.value = true;

  detailLoading.value = true;
  try {
    const detail = await getBlogTravelTripDetailApi(row.id);
    editForm.title = detail.title ?? '';
    editForm.slug = detail.slug ?? '';
    editForm.summary = detail.summary ?? '';
    editForm.coverFileId = detail.coverFileId ?? null;
    editForm.coverPreviewUrl = detail.coverUrl ?? '';
    editForm.status = detail.status ?? 'draft';
    editForm.visibility = detail.visibility ?? 'public';
    editForm.startDate = detail.startDate ?? '';
    editForm.endDate = detail.endDate ?? '';
    editForm.persons = detail.persons ?? null;
    editForm.costTotal =
      detail.costTotal == null ? null : Number(detail.costTotal);
    editForm.costCurrency = detail.costCurrency ?? 'CNY';
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

  if (editForm.startDate && editForm.endDate && editForm.endDate < editForm.startDate) {
    ElMessage.error('结束日期不能早于开始日期');
    return;
  }

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createBlogTravelTripApi({
        title: editForm.title,
        slug: editForm.slug,
        summary: editForm.summary || undefined,
        coverFileId: editForm.coverFileId ?? undefined,
        status: editForm.status,
        visibility: editForm.visibility,
        startDate: editForm.startDate || undefined,
        endDate: editForm.endDate || undefined,
        persons: editForm.persons ?? undefined,
        costTotal: editForm.costTotal ?? undefined,
        costCurrency: editForm.costCurrency || undefined,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogTravelTripApi(editingId.value, {
        title: editForm.title,
        slug: editForm.slug,
        summary: editForm.summary || undefined,
        coverFileId: editForm.coverFileId ?? undefined,
        clearCoverFileId: coverExplicitlyRemoved.value ? true : undefined,
        status: editForm.status,
        visibility: editForm.visibility,
        startDate: editForm.startDate || undefined,
        endDate: editForm.endDate || undefined,
        persons: editForm.persons ?? undefined,
        costTotal: editForm.costTotal ?? undefined,
        costCurrency: editForm.costCurrency || undefined,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: BlogTravelApi.Trip) {
  try {
    await ElMessageBox.confirm(
      `确认删除游记「${row.title}」？所属的行程日、打卡点与文章关联将被一并清理，删除后不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogTravelTripApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

async function quickPublish(row: BlogTravelApi.Trip) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」发布？`, '提示', {
      type: 'info',
    });
  } catch {
    return;
  }
  await updateBlogTravelTripStatusApi(row.id, { status: 'published' });
  ElMessage.success('已发布');
  reloadGrid();
}

async function quickArchive(row: BlogTravelApi.Trip) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」归档？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await updateBlogTravelTripStatusApi(row.id, { status: 'archived' });
  ElMessage.success('已归档');
  reloadGrid();
}

function openDetail(row: BlogTravelApi.Trip) {
  router
    .push({
      path: `/blog/travel/trip/${row.id}/detail`,
      query: { title: row.title },
    })
    .catch((err) => {
      console.error('Navigate to trip detail failed:', err);
      ElMessage.warning('游记详情页未授权或菜单未配置');
    });
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:travel:add'"
          type="primary"
          @click="openCreate"
        >
          新增游记
        </ElButton>
      </template>

      <template #cover="{ row }">
        <img
          v-if="row.coverUrl"
          :src="row.coverUrl"
          alt="cover"
          class="trip-cover-thumb"
        />
        <span v-else class="text-gray-400">-</span>
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

      <template #period="{ row }">
        <span v-if="row.startDate || row.endDate">
          {{ row.startDate || '?' }} ~ {{ row.endDate || '?' }}
          <span v-if="row.daysCount" class="period-days">
            ({{ row.daysCount }}天)
          </span>
        </span>
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #cost="{ row }">
        <span v-if="row.costTotal != null">
          {{ row.costTotal }} {{ row.costCurrency || 'CNY' }}
        </span>
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:travel:query'"
            link
            type="primary"
            @click="openDetail(row)"
          >
            详情
          </ElButton>
          <ElButton
            v-if="row.status !== 'published'"
            v-access:code="'blog:travel:edit'"
            link
            type="success"
            @click="quickPublish(row)"
          >
            发布
          </ElButton>
          <ElButton
            v-if="row.status === 'published'"
            v-access:code="'blog:travel:edit'"
            link
            type="warning"
            @click="quickArchive(row)"
          >
            归档
          </ElButton>
          <ElButton
            v-access:code="'blog:travel:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:travel:delete'"
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
      :title="editMode === 'create' ? '新增游记' : '编辑游记'"
      width="640"
    >
      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="标题" prop="title">
          <ElInput
            v-model="editForm.title"
            placeholder="如：云南七日自由行"
            @blur="autoSlug"
          />
        </ElFormItem>

        <ElFormItem label="Slug" prop="slug">
          <ElInput v-model="editForm.slug" placeholder="如：yunnan-7-days" />
        </ElFormItem>

        <ElFormItem label="摘要" prop="summary">
          <ElInput
            v-model="editForm.summary"
            :rows="3"
            maxlength="500"
            placeholder="可选"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>

        <ElFormItem label="封面">
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

        <ElFormItem label="开始日期">
          <ElDatePicker
            v-model="editForm.startDate"
            value-format="YYYY-MM-DD"
            type="date"
            placeholder="出发日期"
            style="width: 100%"
          />
        </ElFormItem>

        <ElFormItem label="结束日期">
          <ElDatePicker
            v-model="editForm.endDate"
            value-format="YYYY-MM-DD"
            type="date"
            placeholder="返程日期"
            style="width: 100%"
          />
        </ElFormItem>

        <ElFormItem label="人数">
          <ElInputNumber
            v-model="editForm.persons"
            :min="1"
            :max="999"
            controls-position="right"
            placeholder="可选"
          />
        </ElFormItem>

        <ElFormItem label="总花费">
          <div class="cost-row">
            <ElInputNumber
              v-model="editForm.costTotal"
              :min="0"
              :precision="2"
              controls-position="right"
              placeholder="可选"
              style="width: 200px"
            />
            <ElSelect v-model="editForm.costCurrency" style="width: 160px">
              <ElOption
                v-for="item in currencyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </div>
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
.trip-cover-thumb {
  display: block;
  width: 64px;
  height: 40px;
  margin: 0 auto;
  object-fit: cover;
  border-radius: 4px;
}

.period-days {
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.cover-wrap {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 8px;
}

.cover-preview {
  position: relative;
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
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
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 4px;
  color: #fff !important;
}

.cover-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.cost-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
