<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SpaceFolderApi, SpaceTagApi, SpaceTaskApi } from '#/api';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  cancelSpaceExportTaskApi,
  exportChromeBookmarksApi,
  getSpaceExportTaskPageApi,
  getSpaceFolderTreeApi,
  getSpaceTagListApi,
} from '#/api';

defineOptions({ name: 'SpaceExportTask' });

const statusOptions = [
  { label: '待处理', value: 0, tagType: 'info' },
  { label: '处理中', value: 1, tagType: 'warning' },
  { label: '成功', value: 2, tagType: 'success' },
  { label: '失败', value: 3, tagType: 'danger' },
] as const;

const exportTypeLabel: Record<string, string> = {
  chrome_html: 'Chrome HTML',
  json: 'JSON',
};

const scopeTypeLabel: Record<string, string> = {
  all: '全部',
  folder: '目录',
  tag: '标签',
};

function getStatusLabel(status?: number) {
  return (
    statusOptions.find((item) => item.value === status)?.label ??
    String(status ?? '-')
  );
}

function getStatusTagType(status?: number) {
  return statusOptions.find((item) => item.value === status)?.tagType ?? 'info';
}

function getExportTypeLabel(value?: string) {
  return value ? (exportTypeLabel[value] ?? value) : '-';
}

function getScopeTypeLabel(value?: string) {
  return value ? (scopeTypeLabel[value] ?? value) : '-';
}

const gridOptions: VxeTableGridOptions<SpaceTaskApi.ExportTaskItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'id', title: '任务ID', width: 120 },
    {
      field: 'exportType',
      title: '导出类型',
      width: 130,
      slots: { default: 'exportType' },
    },
    {
      field: 'scopeType',
      title: '范围',
      width: 100,
      slots: { default: 'scopeType' },
    },
    { field: 'scopeId', title: '范围ID', width: 120 },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    { field: 'totalCount', title: '导出数量', width: 100, align: 'center' },
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
        return await getSpaceExportTaskPageApi({
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

async function handleCancel(row: SpaceTaskApi.ExportTaskItem) {
  try {
    await ElMessageBox.confirm(`确认取消任务 #${row.id}？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await cancelSpaceExportTaskApi(row.id);
  ElMessage.success('已取消');
  reloadGrid();
}

// ------------------------------------------------------------------ 导出
const exportDialogVisible = ref(false);
const exporting = ref(false);
const exportScope = ref<'all' | 'folder' | 'tag'>('all');
const exportFolderId = ref<number | string | undefined>();
const exportTagId = ref<number | string | undefined>();

const folderTree = ref<SpaceFolderApi.FolderItem[]>([]);
const tagList = ref<SpaceTagApi.TagItem[]>([]);

const folderOptions = computed(() => {
  const result: Array<{ id: number | string; label: string }> = [];
  function walk(list: SpaceFolderApi.FolderItem[], depth = 0) {
    for (const node of list) {
      result.push({
        id: node.id,
        label: `${'　'.repeat(depth)}${node.name}`,
      });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  }
  walk(folderTree.value);
  return result;
});

async function loadScopeOptions() {
  if (folderTree.value.length === 0 && tagList.value.length === 0) {
    const [folders, tags] = await Promise.all([
      getSpaceFolderTreeApi(),
      getSpaceTagListApi(),
    ]);
    folderTree.value = folders ?? [];
    tagList.value = tags ?? [];
  }
}

onMounted(loadScopeOptions);

function openExportDialog() {
  exportScope.value = 'all';
  exportFolderId.value = undefined;
  exportTagId.value = undefined;
  exportDialogVisible.value = true;
}

/**
 * 触发浏览器下载：把 Blob 转成对象 URL 后用一个临时 <a> 触发点击
 * 文件名优先用响应里的 Content-Disposition，但 download() 默认只返回 body，
 * 所以这里给一个本地时间戳兜底
 */
function triggerDownload(blob: Blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  a.download = `bookmarks_${ts}.html`;
  document.body.append(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

async function submitExport() {
  if (exportScope.value === 'folder' && exportFolderId.value == null) {
    ElMessage.warning('请选择要导出的目录');
    return;
  }
  if (exportScope.value === 'tag' && exportTagId.value == null) {
    ElMessage.warning('请选择要导出的标签');
    return;
  }
  exporting.value = true;
  try {
    const scopeId =
      exportScope.value === 'folder'
        ? exportFolderId.value
        : exportScope.value === 'tag'
          ? exportTagId.value
          : undefined;
    const blob = await exportChromeBookmarksApi(exportScope.value, scopeId);
    triggerDownload(blob);
    ElMessage.success('已生成 Chrome 书签文件');
    exportDialogVisible.value = false;
    reloadGrid();
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'space:bookmark-export:edit'"
          type="primary"
          @click="openExportDialog"
        >
          导出 Chrome 书签
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #exportType="{ row }">
        {{ getExportTypeLabel(row.exportType) }}
      </template>

      <template #scopeType="{ row }">
        {{ getScopeTypeLabel(row.scopeType) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-if="row.status === 0"
            v-access:code="'space:bookmark-export:edit'"
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

    <ElDialog
      v-model="exportDialogVisible"
      :close-on-click-modal="false"
      title="导出 Chrome 书签"
      width="520"
    >
      <ElForm label-width="80px">
        <ElFormItem label="范围">
          <ElRadioGroup v-model="exportScope">
            <ElRadioButton value="all">全部</ElRadioButton>
            <ElRadioButton value="folder">指定目录</ElRadioButton>
            <ElRadioButton value="tag">指定标签</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>

        <ElFormItem v-if="exportScope === 'folder'" label="目录">
          <ElSelect
            v-model="exportFolderId"
            filterable
            placeholder="请选择目录"
            style="width: 100%"
          >
            <ElOption
              v-for="item in folderOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem v-if="exportScope === 'tag'" label="标签">
          <ElSelect
            v-model="exportTagId"
            filterable
            placeholder="请选择标签"
            style="width: 100%"
          >
            <ElOption
              v-for="item in tagList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="exportDialogVisible = false">取消</ElButton>
        <ElButton :loading="exporting" type="primary" @click="submitExport">
          导出
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
