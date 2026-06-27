<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiFlowApi } from '#/api';

import { useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import { deleteFlowApi, getFlowPageApi } from '#/api';

defineOptions({ name: 'AiFlowList' });

const router = useRouter();

const gridOptions: VxeTableGridOptions<AiFlowApi.FlowSummaryRaw> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'flow_code', title: '流程编码', minWidth: 200 },
    { field: 'name', title: '名称', minWidth: 160 },
    {
      field: 'description',
      title: '说明',
      minWidth: 220,
      showOverflow: 'tooltip',
    },
    { field: 'version', title: '版本', width: 80, align: 'center' },
    {
      field: 'default_profile_code',
      title: '默认档案码',
      width: 140,
    },
    {
      field: 'node_count',
      title: '节点数',
      width: 90,
      align: 'center',
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'update_time',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 180,
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
        return await getFlowPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : Number(formValues.status),
        });
      },
    },
  },
  rowConfig: { keyField: 'flow_code' },
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
          options: [
            { label: '启用', value: 1 },
            { label: '停用', value: 0 },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

function openCreate() {
  router.push({ name: 'AiFlowEditor', query: {} });
}

function openEdit(row: AiFlowApi.FlowSummaryRaw) {
  router.push({ name: 'AiFlowEditor', query: { flowCode: row.flow_code } });
}

async function handleDelete(row: AiFlowApi.FlowSummaryRaw) {
  try {
    await ElMessageBox.confirm(`确认删除流程「${row.name || row.flow_code}」？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await deleteFlowApi(row.flow_code);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-flow:save'"
          type="primary"
          @click="openCreate"
        >
          新建流程
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-flow:query'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-flow:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>
  </Page>
</template>
