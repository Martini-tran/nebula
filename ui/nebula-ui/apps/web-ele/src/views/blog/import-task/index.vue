<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogImportTaskApi } from '#/api';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import { getBlogImportTaskDetailApi, getBlogImportTaskPageApi } from '#/api';

defineOptions({ name: 'BlogImportTask' });

const statusOptions = [
  { label: '待处理', value: 'pending', tagType: 'info' },
  { label: '处理中', value: 'running', tagType: 'warning' },
  { label: '已完成', value: 'success', tagType: 'success' },
  { label: '失败', value: 'failed', tagType: 'danger' },
] as const;

function getStatusLabel(status?: string) {
  return (
    statusOptions.find((item) => item.value === status)?.label ??
    String(status ?? '-')
  );
}

function getStatusTagType(status?: string) {
  return statusOptions.find((item) => item.value === status)?.tagType ?? 'info';
}

/** 是否进行中（待处理 / 处理中），用于决定是否继续轮询 */
function isActiveStatus(status?: string) {
  return status === 'pending' || status === 'running';
}

const gridOptions: VxeTableGridOptions<BlogImportTaskApi.ImportTaskItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'id', title: '任务ID', width: 120 },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'progress',
      title: '进度',
      width: 110,
      align: 'center',
      slots: { default: 'progress' },
    },
    { field: 'successCount', title: '成功', width: 80, align: 'center' },
    { field: 'failCount', title: '失败', width: 80, align: 'center' },
    { field: 'postStatus', title: '文章状态', width: 100 },
    { field: 'errorMessage', title: '错误信息', minWidth: 200 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'finishedAt',
      title: '完成时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 110,
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
        const result = await getBlogImportTaskPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : formValues.status,
        });
        // 列表中存在进行中的任务时维持轮询，否则停止
        hasActiveTask.value = (result.records ?? []).some((row) =>
          isActiveStatus(row.status),
        );
        return result;
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

// ------------------------------------------------------------------ 轮询进度
const hasActiveTask = ref(false);
let pollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  // 每 4s 刷新一次；仅在存在进行中的任务时真正发起查询，避免无谓请求
  pollTimer = setInterval(() => {
    if (hasActiveTask.value) {
      gridApi.query();
    }
  }, 4000);
});

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});

// ------------------------------------------------------------------ 明细弹窗
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<BlogImportTaskApi.ImportTaskItem | null>(null);

async function openDetail(row: BlogImportTaskApi.ImportTaskItem) {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await getBlogImportTaskDetailApi(row.id);
  } catch {
    ElMessage.error('加载导入明细失败');
  } finally {
    detailLoading.value = false;
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #progress="{ row }">
        {{ row.processedCount ?? 0 }} / {{ row.totalCount ?? 0 }}
      </template>

      <template #action="{ row }">
        <ElButton
          v-access:code="'blog:article:query'"
          link
          type="primary"
          @click="openDetail(row)"
        >
          查看明细
        </ElButton>
      </template>
    </Grid>

    <!-- 导入任务明细 -->
    <ElDialog
      v-model="detailVisible"
      :title="`导入任务明细${detail?.id ? ` #${detail.id}` : ''}`"
      width="900px"
    >
      <div v-loading="detailLoading">
        <ElDescriptions v-if="detail" :column="3" border size="small">
          <ElDescriptionsItem label="状态">
            <ElTag :type="getStatusTagType(detail.status)" size="small">
              {{ getStatusLabel(detail.status) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="进度">
            {{ detail.processedCount ?? 0 }} / {{ detail.totalCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="成功 / 失败">
            {{ detail.successCount ?? 0 }} / {{ detail.failCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="文章状态">
            {{ detail.postStatus ?? '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="可见性">
            {{ detail.visibility ?? '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="类型">
            {{ detail.postType ?? '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="转存图片">
            {{ detail.rehostImages ? '是' : '否' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="开始时间">
            {{ detail.startedAt ?? '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="完成时间">
            {{ detail.finishedAt ?? '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="detail.errorMessage" :span="3" label="错误">
            <span style="color: var(--el-color-danger)">
              {{ detail.errorMessage }}
            </span>
          </ElDescriptionsItem>
        </ElDescriptions>

        <ElTable
          :data="detail?.items ?? []"
          border
          max-height="380"
          size="small"
          style="margin-top: 16px"
        >
          <ElTableColumn label="#" type="index" width="50" />
          <ElTableColumn
            label="文件名"
            min-width="200"
            prop="filename"
            show-overflow-tooltip
          />
          <ElTableColumn label="结果" width="80">
            <template #default="{ row }">
              <ElTag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="文章ID" prop="articleId" width="120" />
          <ElTableColumn
            label="标题"
            min-width="160"
            prop="title"
            show-overflow-tooltip
          />
          <ElTableColumn
            label="slug"
            min-width="140"
            prop="slug"
            show-overflow-tooltip
          />
          <ElTableColumn
            label="错误原因"
            min-width="180"
            prop="error"
            show-overflow-tooltip
          />
        </ElTable>
      </div>
    </ElDialog>
  </Page>
</template>
