<script lang="ts" setup>
import type { UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElImage,
  ElImageViewer,
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
  bindSystemFileApi,
  deleteSystemFileApi,
  getSystemFilePageApi,
  getSystemFilePresignedUrlApi,
  type SystemFileApi,
  uploadSystemFileApi,
} from '#/api/system/file';

defineOptions({ name: 'SystemFile' });

// ===== 静态选项 =====
const FILE_TYPE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '图片', value: 'image' },
  { label: '封面', value: 'cover' },
  { label: '头像', value: 'avatar' },
  { label: 'Logo', value: 'logo' },
  { label: '附件', value: 'attachment' },
  { label: '视频', value: 'video' },
  { label: '音频', value: 'audio' },
  { label: '其他', value: 'other' },
];

const STATUS_OPTIONS: Array<{
  label: string;
  tagType: 'danger' | 'info' | 'primary' | 'success' | 'warning';
  value: number;
}> = [
  { label: '正常', tagType: 'success', value: 1 },
  { label: '已删除', tagType: 'info', value: 0 },
  { label: '上传中', tagType: 'warning', value: 2 },
  { label: '上传失败', tagType: 'danger', value: 3 },
  { label: '禁用', tagType: 'info', value: 4 },
];

function statusMeta(status: number | undefined) {
  return (
    STATUS_OPTIONS.find((s) => s.value === status) ?? {
      label: '未知',
      tagType: 'info' as const,
      value: -1,
    }
  );
}

function isImage(row: SystemFileApi.FileInfo) {
  return row.mimeType?.startsWith('image/');
}

function formatSize(bytes: number | undefined) {
  if (!bytes && bytes !== 0) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

// ===== 表格 + 搜索 =====
const gridOptions: VxeTableGridOptions<SystemFileApi.FileInfo> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    {
      field: 'preview',
      title: '预览',
      width: 80,
      slots: { default: 'preview' },
    },
    { field: 'originalFilename', title: '文件名', minWidth: 220 },
    {
      field: 'fileType',
      title: '用途',
      width: 100,
      slots: { default: 'fileType' },
    },
    { field: 'mimeType', title: 'MIME', minWidth: 160 },
    {
      field: 'sizeBytes',
      title: '大小',
      width: 110,
      slots: { default: 'size' },
    },
    { field: 'targetType', title: '业务类型', width: 140 },
    { field: 'targetId', title: '业务ID', width: 110 },
    {
      field: 'isPublic',
      title: '公开',
      width: 80,
      slots: { default: 'isPublic' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    { field: 'storageType', title: '存储', width: 90 },
    {
      field: 'createTime',
      title: '上传时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 170,
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
        const params: SystemFileApi.FilePageQuery = {
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          originalFilename: formValues?.originalFilename || undefined,
          targetType: formValues?.targetType || undefined,
          targetId:
            formValues?.targetId === '' || formValues?.targetId == null
              ? undefined
              : formValues.targetId,
          fileType: formValues?.fileType || undefined,
          status:
            formValues?.status === undefined || formValues?.status === ''
              ? undefined
              : Number(formValues.status),
        };
        return await getSystemFilePageApi(params);
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: {
    refresh: { code: 'query' },
    custom: true,
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
        fieldName: 'originalFilename',
        label: '文件名',
      },
      {
        component: 'Input',
        fieldName: 'targetType',
        label: '业务类型',
      },
      {
        component: 'Input',
        fieldName: 'targetId',
        label: '业务ID',
      },
      {
        component: 'Select',
        fieldName: 'fileType',
        label: '用途',
        componentProps: { options: FILE_TYPE_OPTIONS, clearable: true },
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          options: STATUS_OPTIONS.map(({ label, value }) => ({ label, value })),
          clearable: true,
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ===== 上传 =====
const uploadDialogVisible = ref(false);
const uploadLoading = ref(false);
const uploadForm = reactive<{
  fileType: string;
  targetType: string;
  targetId: string;
  bucket: string;
  isPublic: boolean;
  sortOrder: number;
  prefix: string;
  pendingFile: File | null;
}>({
  fileType: 'image',
  targetType: '',
  targetId: '',
  bucket: '',
  isPublic: true,
  sortOrder: 0,
  prefix: '',
  pendingFile: null,
});

const uploadAccept = computed(() => {
  switch (uploadForm.fileType) {
    case 'avatar':
    case 'cover':
    case 'image':
    case 'logo': {
      return 'image/*';
    }
    case 'audio': {
      return 'audio/*';
    }
    case 'video': {
      return 'video/*';
    }
    default: {
      return '';
    }
  }
});

function openUpload() {
  uploadForm.fileType = 'image';
  uploadForm.targetType = '';
  uploadForm.targetId = '';
  uploadForm.bucket = '';
  uploadForm.isPublic = true;
  uploadForm.sortOrder = 0;
  uploadForm.prefix = '';
  uploadForm.pendingFile = null;
  uploadDialogVisible.value = true;
}

function handlePickFile(uploadFile: UploadFile) {
  uploadForm.pendingFile = uploadFile.raw ?? null;
}

async function submitUpload() {
  if (!uploadForm.pendingFile) {
    ElMessage.warning('请先选择文件');
    return;
  }
  uploadLoading.value = true;
  try {
    const targetIdRaw = uploadForm.targetId.trim();
    await uploadSystemFileApi(uploadForm.pendingFile, {
      fileType: uploadForm.fileType,
      targetType: uploadForm.targetType.trim() || undefined,
      targetId: targetIdRaw === '' ? undefined : targetIdRaw,
      bucket: uploadForm.bucket.trim() || undefined,
      isPublic: uploadForm.isPublic ? 1 : 0,
      sortOrder: uploadForm.sortOrder,
      prefix: uploadForm.prefix.trim() || undefined,
    });
    ElMessage.success('上传成功');
    uploadDialogVisible.value = false;
    reloadGrid();
  } finally {
    uploadLoading.value = false;
  }
}

// ===== 详情 =====
const detailVisible = ref(false);
const detailRow = ref<null | SystemFileApi.FileInfo>(null);

function openDetail(row: SystemFileApi.FileInfo) {
  detailRow.value = row;
  detailVisible.value = true;
}

// ===== 复制临时URL =====
async function copyPresignedUrl(row: SystemFileApi.FileInfo) {
  try {
    const url = await getSystemFilePresignedUrlApi(row.id, 3600);
    await navigator.clipboard.writeText(url);
    ElMessage.success('临时URL已复制（有效期 1 小时）');
  } catch {
    ElMessage.error('复制失败');
  }
}

// ===== 图片预览（使用临时签名URL，避免私有文件直链失败） =====
const previewVisible = ref(false);
const previewUrls = ref<string[]>([]);

async function openPreview(row: SystemFileApi.FileInfo) {
  if (!isImage(row)) return;
  try {
    const url = await getSystemFilePresignedUrlApi(row.id, 600);
    previewUrls.value = [url];
    previewVisible.value = true;
  } catch {
    ElMessage.error('图片预览失败');
  }
}

function closePreview() {
  previewVisible.value = false;
  previewUrls.value = [];
}

// ===== 下载 =====
// 通过临时签名URL下载，避免 <a href> 发请求时不携带 sa-token Authorization
async function downloadFile(row: SystemFileApi.FileInfo) {
  try {
    const url = await getSystemFilePresignedUrlApi(row.id, 600);
    const a = document.createElement('a');
    a.href = url;
    a.download = row.originalFilename ?? `file-${row.id}`;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    document.body.append(a);
    a.click();
    a.remove();
  } catch {
    ElMessage.error('生成下载链接失败');
  }
}

// ===== 绑定业务实体 =====
const bindDialogVisible = ref(false);
const bindLoading = ref(false);
const bindRow = ref<null | SystemFileApi.FileInfo>(null);
const bindForm = reactive({
  targetType: '',
  targetId: '',
});

function openBind(row: SystemFileApi.FileInfo) {
  bindRow.value = row;
  bindForm.targetType = row.targetType ?? '';
  bindForm.targetId = row.targetId == null ? '' : String(row.targetId);
  bindDialogVisible.value = true;
}

async function submitBind() {
  if (!bindRow.value) return;
  if (!bindForm.targetType.trim() || !bindForm.targetId.trim()) {
    ElMessage.warning('业务类型和业务ID都必须填写');
    return;
  }
  bindLoading.value = true;
  try {
    await bindSystemFileApi(bindRow.value.id, {
      targetType: bindForm.targetType.trim(),
      targetId: bindForm.targetId.trim(),
    });
    ElMessage.success('绑定成功');
    bindDialogVisible.value = false;
    reloadGrid();
  } finally {
    bindLoading.value = false;
  }
}

// ===== 删除 =====
async function handleDelete(row: SystemFileApi.FileInfo) {
  let removeStorage = false;
  try {
    await ElMessageBox.confirm(
      `确认删除文件「${row.originalFilename ?? row.id}」？`,
      '删除确认',
      {
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonText: '仅软删除',
        cancelButtonText: '同时删除存储',
        showClose: true,
      },
    );
    removeStorage = false;
  } catch (action) {
    // ElMessageBox 取消时返回 'cancel'，关闭时返回 'close'
    if (action === 'cancel') {
      removeStorage = true;
    } else {
      return;
    }
  }
  await deleteSystemFileApi(row.id, removeStorage);
  ElMessage.success(removeStorage ? '已删除（含存储对象）' : '已删除');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton v-access:code="'system:file:upload'" type="primary" @click="openUpload">
          上传文件
        </ElButton>
      </template>

      <template #preview="{ row }">
        <ElImage
          v-if="isImage(row) && row.url"
          :src="row.url"
          fit="cover"
          class="sf-thumb"
          @click="openPreview(row)"
        >
          <template #error>
            <div class="sf-thumb sf-thumb-fallback" @click.stop="openPreview(row)">
              预览
            </div>
          </template>
        </ElImage>
        <span v-else class="sf-empty">-</span>
      </template>

      <template #fileType="{ row }">
        <ElTag size="small" type="info">{{ row.fileType ?? '-' }}</ElTag>
      </template>

      <template #size="{ row }">
        <span>{{ formatSize(row.sizeBytes) }}</span>
      </template>

      <template #isPublic="{ row }">
        <ElTag :type="row.isPublic === 1 ? 'success' : 'warning'" size="small">
          {{ row.isPublic === 1 ? '公开' : '私有' }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="statusMeta(row.status).tagType" size="small">
          {{ statusMeta(row.status).label }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
          <ElButton link type="primary" @click="downloadFile(row)">
            下载
          </ElButton>
          <ElDropdown trigger="click">
            <ElButton link type="primary">
              更多<span class="ml-0.5">▾</span>
            </ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="copyPresignedUrl(row)">
                  复制临时URL
                </ElDropdownItem>
                <ElDropdownItem
                  v-access:code="'system:file:edit'"
                  @click="openBind(row)"
                >
                  绑定业务
                </ElDropdownItem>
                <ElDropdownItem
                  v-access:code="'system:file:delete'"
                  divided
                  @click="handleDelete(row)"
                >
                  <span style="color: var(--el-color-danger)">删除</span>
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
      </template>
    </Grid>

    <!-- 上传 -->
    <ElDialog
      v-model="uploadDialogVisible"
      :close-on-click-modal="false"
      title="上传文件"
      width="520"
    >
      <ElForm label-width="90px">
        <ElFormItem label="选择文件" required>
          <ElUpload
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :accept="uploadAccept"
            @change="handlePickFile"
          >
            <ElButton size="small" type="primary" plain>选择文件</ElButton>
            <template #tip>
              <span class="text-xs text-gray-400">
                单文件最大 50MB；公开文件可直接通过永久URL访问，私有文件每次访问需要签名URL
              </span>
            </template>
          </ElUpload>
        </ElFormItem>

        <ElFormItem label="用途">
          <ElSelect v-model="uploadForm.fileType" style="width: 100%">
            <ElOption
              v-for="item in FILE_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="业务类型">
          <ElInput
            v-model="uploadForm.targetType"
            placeholder="可选，如 blog_post / user_avatar"
          />
        </ElFormItem>

        <ElFormItem label="业务ID">
          <ElInput
            v-model="uploadForm.targetId"
            placeholder="可选，未确定时可后续绑定"
          />
        </ElFormItem>

        <ElFormItem label="存储桶">
          <ElInput v-model="uploadForm.bucket" placeholder="留空则使用默认桶" />
        </ElFormItem>

        <ElFormItem label="路径前缀">
          <ElInput
            v-model="uploadForm.prefix"
            placeholder="可选，未填则使用文件用途作为前缀"
          />
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber v-model="uploadForm.sortOrder" :min="0" />
        </ElFormItem>

        <ElFormItem label="是否公开">
          <ElCheckbox v-model="uploadForm.isPublic">公开（永久URL可访问）</ElCheckbox>
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="uploadDialogVisible = false">取消</ElButton>
        <ElButton :loading="uploadLoading" type="primary" @click="submitUpload">
          上传
        </ElButton>
      </template>
    </ElDialog>

    <!-- 绑定 -->
    <ElDialog
      v-model="bindDialogVisible"
      :close-on-click-modal="false"
      title="绑定业务实体"
      width="420"
    >
      <ElForm label-width="90px">
        <ElFormItem label="文件">
          <span>{{ bindRow?.originalFilename ?? bindRow?.id }}</span>
        </ElFormItem>
        <ElFormItem label="业务类型" required>
          <ElInput
            v-model="bindForm.targetType"
            placeholder="如 blog_post / user_avatar"
          />
        </ElFormItem>
        <ElFormItem label="业务ID" required>
          <ElInput v-model="bindForm.targetId" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="bindDialogVisible = false">取消</ElButton>
        <ElButton :loading="bindLoading" type="primary" @click="submitBind">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 详情 -->
    <ElDialog
      v-model="detailVisible"
      :close-on-click-modal="true"
      title="文件详情"
      width="640"
    >
      <div v-if="detailRow" class="sf-detail">
        <div v-if="isImage(detailRow) && detailRow.url" class="sf-detail-preview">
          <ElImage
            :src="detailRow.url"
            fit="contain"
            class="sf-detail-img"
            @click="openPreview(detailRow)"
          >
            <template #error>
              <div class="sf-detail-fallback" @click.stop="openPreview(detailRow)">
                点击预览
              </div>
            </template>
          </ElImage>
        </div>
        <table class="sf-detail-table">
          <tbody>
            <tr>
              <td>ID</td>
              <td>{{ detailRow.id }}</td>
            </tr>
            <tr>
              <td>原始文件名</td>
              <td>{{ detailRow.originalFilename }}</td>
            </tr>
            <tr>
              <td>存储文件名</td>
              <td>{{ detailRow.storedFilename }}</td>
            </tr>
            <tr>
              <td>用途</td>
              <td>{{ detailRow.fileType }}</td>
            </tr>
            <tr>
              <td>MIME</td>
              <td>{{ detailRow.mimeType }}</td>
            </tr>
            <tr>
              <td>大小</td>
              <td>{{ formatSize(detailRow.sizeBytes) }}</td>
            </tr>
            <tr>
              <td>存储</td>
              <td>{{ detailRow.storageType }} / {{ detailRow.bucket }}</td>
            </tr>
            <tr>
              <td>对象Key</td>
              <td class="sf-detail-mono">{{ detailRow.objectKey }}</td>
            </tr>
            <tr>
              <td>访问URL</td>
              <td class="sf-detail-mono">
                <a v-if="detailRow.url" :href="detailRow.url" target="_blank">
                  {{ detailRow.url }}
                </a>
                <span v-else>-</span>
              </td>
            </tr>
            <tr>
              <td>SHA256</td>
              <td class="sf-detail-mono">{{ detailRow.hashSha256 }}</td>
            </tr>
            <tr>
              <td>业务关联</td>
              <td>{{ detailRow.targetType || '-' }} / {{ detailRow.targetId || '-' }}</td>
            </tr>
            <tr>
              <td>是否公开</td>
              <td>{{ detailRow.isPublic === 1 ? '公开' : '私有' }}</td>
            </tr>
            <tr>
              <td>状态</td>
              <td>{{ statusMeta(detailRow.status).label }}</td>
            </tr>
            <tr>
              <td>上传人</td>
              <td>{{ detailRow.createBy ?? '-' }}</td>
            </tr>
            <tr>
              <td>创建时间</td>
              <td>{{ detailRow.createTime }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <!-- 图片预览（使用临时签名URL） -->
    <ElImageViewer
      v-if="previewVisible"
      :url-list="previewUrls"
      teleported
      @close="closePreview"
    />
  </Page>
</template>

<style scoped>
.sf-thumb {
  width: 40px;
  height: 40px;
  cursor: pointer;
  border-radius: 4px;
}

.sf-thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  background-color: var(--el-fill-color-light);
}

.sf-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.sf-detail-img {
  max-width: 100%;
  max-height: 280px;
  cursor: pointer;
}

.sf-detail-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 240px;
  height: 160px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  cursor: pointer;
  background-color: var(--el-fill-color);
  border-radius: 6px;
}

.sf-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sf-detail-preview {
  display: flex;
  justify-content: center;
  padding: 8px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.sf-detail-table {
  width: 100%;
  border-collapse: collapse;
}

.sf-detail-table td {
  padding: 6px 8px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  vertical-align: top;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sf-detail-table td:first-child {
  width: 110px;
  color: var(--el-text-color-secondary);
}

.sf-detail-mono {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  word-break: break-all;
}
</style>
