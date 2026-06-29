<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiToolApi } from '#/api';

import { computed, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElTag } from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import { getAiToolPageApi } from '#/api';

defineOptions({ name: 'AiTool' });

const gridOptions: VxeTableGridOptions<AiToolApi.ToolItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'toolCode', title: '工具编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    { field: 'category', title: '分类', width: 120, slots: { default: 'category' } },
    { field: 'description', title: '描述', minWidth: 220, showOverflow: 'tooltip' },
    { field: 'builtin', title: '来源', width: 100, slots: { default: 'builtin' } },
    { field: 'enabled', title: '状态', width: 100, slots: { default: 'enabled' } },
    { field: 'sortNo', title: '排序', width: 80 },
    {
      field: 'updateTime',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 100,
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
        return await getAiToolPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          category: formValues?.category || undefined,
          enabled:
            formValues?.enabled === '' || formValues?.enabled == null
              ? undefined
              : Number(formValues.enabled),
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

const [Grid] = usenebulaVxeGrid({
  gridOptions,
  showSearchForm: true,
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'keyword', label: '关键词' },
      { component: 'Input', fieldName: 'category', label: '分类' },
      {
        component: 'Select',
        fieldName: 'enabled',
        label: '状态',
        componentProps: {
          clearable: true,
          options: [
            { label: '启用', value: 1 },
            { label: '已下线', value: 0 },
          ],
        },
      },
    ],
  },
});

const detailVisible = ref(false);
const current = ref<AiToolApi.ToolItem | null>(null);

const paramsSchemaText = computed(() =>
  current.value?.paramsSchema
    ? JSON.stringify(current.value.paramsSchema, null, 2)
    : '',
);
const resultSchemaText = computed(() =>
  current.value?.resultSchema
    ? JSON.stringify(current.value.resultSchema, null, 2)
    : '',
);

function openDetail(row: AiToolApi.ToolItem) {
  current.value = row;
  detailVisible.value = true;
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #category="{ row }">
        <ElTag v-if="row.category" size="small" type="primary">
          {{ row.category }}
        </ElTag>
        <span v-else>-</span>
      </template>

      <template #builtin="{ row }">
        <ElTag :type="row.builtin === 1 ? 'primary' : 'warning'" size="small">
          {{ row.builtin === 1 ? '内置' : '外部' }}
        </ElTag>
      </template>

      <template #enabled="{ row }">
        <ElTag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ row.enabled === 1 ? '启用' : '已下线' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
      </template>
    </Grid>

    <ElDialog
      v-model="detailVisible"
      :title="`工具详情 - ${current?.toolCode ?? ''}`"
      width="720"
    >
      <ElDescriptions v-if="current" :column="2" border>
        <ElDescriptionsItem label="工具编码">
          {{ current.toolCode }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="名称">
          {{ current.name || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="分类">
          {{ current.category || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="来源">
          {{ current.builtin === 1 ? '代码内置' : '外部登记' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          {{ current.enabled === 1 ? '启用' : '已下线' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="排序">
          {{ current.sortNo ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :span="2" label="描述">
          {{ current.description || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :span="2" label="备注">
          {{ current.remark || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :span="2" label="入参 Schema">
          <pre class="schema-pre">{{ paramsSchemaText || '（无）' }}</pre>
        </ElDescriptionsItem>
        <ElDescriptionsItem :span="2" label="出参 Schema">
          <pre class="schema-pre">{{ resultSchemaText || '（无）' }}</pre>
        </ElDescriptionsItem>
      </ElDescriptions>

      <template #footer>
        <ElButton type="primary" @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.schema-pre {
  max-height: 280px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
