<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ForgeDownloadLogApi } from '#/api';

import { ref } from 'vue';

import { Page } from '@nebula/common-ui';

import { ElDatePicker, ElTag } from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import { getForgeDownloadLogPageApi } from '#/api';

defineOptions({ name: 'ForgeDownloadLog' });

/** 时间范围（datetimerange），ISO 格式以匹配后端查询参数绑定 */
const dateRange = ref<[string, string] | null>(null);

const gridOptions: VxeTableGridOptions<ForgeDownloadLogApi.DownloadLogItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'pluginId', title: '插件ID', minWidth: 140 },
    { field: 'versionId', title: '版本ID', minWidth: 140 },
    { field: 'userId', title: '用户ID', minWidth: 140 },
    { field: 'clientVersion', title: '客户端版本', width: 120 },
    { field: 'clientOs', title: '客户端系统', width: 120 },
    { field: 'ip', title: 'IP', width: 140 },
    { field: 'result', title: '结果', width: 90, slots: { default: 'result' } },
    { field: 'errorMsg', title: '错误信息', minWidth: 180 },
    { field: 'createTime', title: '下载时间', width: 180 },
  ],
  height: 'auto',
  keepSource: true,
  pagerConfig: { pageSize: 10 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getForgeDownloadLogPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          pluginId: formValues?.pluginId || undefined,
          versionId: formValues?.versionId || undefined,
          userId: formValues?.userId || undefined,
          result: formValues?.result ?? undefined,
          startTime: dateRange.value?.[0] || undefined,
          endTime: dateRange.value?.[1] || undefined,
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
      { component: 'Input', fieldName: 'pluginId', label: '插件ID' },
      { component: 'Input', fieldName: 'versionId', label: '版本ID' },
      { component: 'Input', fieldName: 'userId', label: '用户ID' },
      {
        component: 'Select',
        fieldName: 'result',
        label: '结果',
        componentProps: {
          clearable: true,
          options: [
            { label: '成功', value: 1 },
            { label: '失败', value: 0 },
          ],
        },
      },
    ],
  },
});

function onDateRangeChange() {
  gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElDatePicker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          @change="onDateRangeChange"
        />
      </template>

      <template #result="{ row }">
        <ElTag :type="row.result === 1 ? 'success' : 'danger'" size="small">
          {{ row.result === 1 ? '成功' : '失败' }}
        </ElTag>
      </template>
    </Grid>
  </Page>
</template>
