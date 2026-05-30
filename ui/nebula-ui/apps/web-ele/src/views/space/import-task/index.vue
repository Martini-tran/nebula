<script lang="ts" setup>
import type { UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SpaceTaskApi } from '#/api';

import { ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTag,
  ElUpload,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  cancelSpaceImportTaskApi,
  getSpaceImportTaskPageApi,
  importChromeBookmarksApi,
} from '#/api';

defineOptions({ name: 'SpaceImportTask' });

const statusOptions = [
  { label: '待处理', value: 0, tagType: 'info' },
  { label: '处理中', value: 1, tagType: 'warning' },
  { label: '成功', value: 2, tagType: 'success' },
  { label: '失败', value: 3, tagType: 'danger' },
] as const;

function getStatusLabel(status?: number) {
  return (
    statusOptions.find((item) => item.value === status)?.label ??
    String(status ?? '-')
  );
}

function getStatusTagType(status?: number) {
  return statusOptions.find((item) => item.value === status)?.tagType ?? 'info';
}

const gridOptions: VxeTableGridOptions<SpaceTaskApi.ImportTaskItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'id', title: '任务ID', width: 120 },
    { field: 'source', title: '来源', width: 120 },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    { field: 'totalCount', title: '总数', width: 80, align: 'center' },
    { field: 'successCount', title: '成功', width: 80, align: 'center' },
    { field: 'duplicateCount', title: '重复', width: 80, align: 'center' },
    { field: 'failCount', title: '失败', width: 80, align: 'center' },
    { field: 'errorMsg', title: '错误信息', minWidth: 220 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'updateTime',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  height: 'auto',
  pagerConfig: { pageSize: 20 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getSpaceImportTaskPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : formValues.status,
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
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: statusOptions.map(({ label, value }) => ({ label, value })),
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

async function handleCancel(row: SpaceTaskApi.ImportTaskItem) {
  try {
    await ElMessageBox.confirm(`确认取消任务 #${row.id}？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await cancelSpaceImportTaskApi(row.id);
  ElMessage.success('已取消');
  reloadGrid();
}

// ------------------------------------------------------------------ 上传导入
const importing = ref(false);

/**
 * Chrome 书签 HTML 上传
 * Element Plus 的 ElUpload 在 :auto-upload="false" 模式下选中文件即触发 change，
 * 这里直接拿原始文件调用接口，避免走它默认的 PUT/POST 逻辑
 */
async function handleImportChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return;
  importing.value = true;
  try {
    const result = await importChromeBookmarksApi(uploadFile.raw);
    ElMessage.success(
      `导入完成：成功 ${result.successCount ?? 0} / 重复 ${
        result.duplicateCount ?? 0
      } / 失败 ${result.failCount ?? 0}`,
    );
    reloadGrid();
  } finally {
    importing.value = false;
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElUpload
          :auto-upload="false"
          :show-file-list="false"
          accept=".html,.htm"
          @change="handleImportChange"
        >
          <ElButton
            v-access:code="'space:bookmark-import:edit'"
            :loading="importing"
            type="primary"
          >
            导入 Chrome 书签
          </ElButton>
        </ElUpload>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-if="row.status === 0"
            v-access:code="'space:bookmark-import:edit'"
            link
            type="danger"
            @click="handleCancel(row)"
          >
            取消
          </ElButton>
          <span v-else>-</span>
        </div>
      </template>
    </Grid>
  </Page>
</template>
