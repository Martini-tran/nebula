<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ForgeReviewApi } from '#/api';

import { reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRate,
  ElSelect,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  auditForgeReviewApi,
  deleteForgeReviewApi,
  getForgeReviewPageApi,
  replyForgeReviewApi,
} from '#/api';

defineOptions({ name: 'ForgeReview' });

const statusOptions = [
  { label: '待审核', value: 0, tagType: 'warning' as const },
  { label: '展示', value: 1, tagType: 'success' as const },
  { label: '隐藏', value: 2, tagType: 'info' as const },
  { label: '拒绝', value: 3, tagType: 'danger' as const },
];

function statusLabel(status?: number) {
  return statusOptions.find((s) => s.value === status)?.label ?? '-';
}
function statusTagType(status?: number) {
  return statusOptions.find((s) => s.value === status)?.tagType ?? 'info';
}

const gridOptions: VxeTableGridOptions<ForgeReviewApi.ReviewItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'pluginId', title: '插件ID', minWidth: 140 },
    { field: 'userId', title: '用户ID', minWidth: 140 },
    { field: 'rating', title: '评分', width: 90, slots: { default: 'rating' } },
    { field: 'content', title: '评价内容', minWidth: 220 },
    { field: 'replyContent', title: '官方回复', minWidth: 180 },
    { field: 'status', title: '状态', width: 100, slots: { default: 'status' } },
    { field: 'createTime', title: '创建时间', width: 180 },
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
        return await getForgeReviewPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          pluginId: formValues?.pluginId || undefined,
          userId: formValues?.userId || undefined,
          status: formValues?.status ?? undefined,
          rating: formValues?.rating ?? undefined,
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
      { component: 'Input', fieldName: 'userId', label: '用户ID' },
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
        fieldName: 'rating',
        label: '评分',
        componentProps: {
          clearable: true,
          options: [1, 2, 3, 4, 5].map((v) => ({ label: `${v} 星`, value: v })),
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ------------------------------------------------------------------ 审核
const auditDialogVisible = ref(false);
const auditLoading = ref(false);
const auditingId = ref<number | string | null>(null);
const auditForm = reactive<{ status: number; auditRemark: string }>({
  status: 1,
  auditRemark: '',
});

function openAudit(row: ForgeReviewApi.ReviewItem) {
  auditingId.value = row.id;
  auditForm.status = row.status ?? 1;
  auditForm.auditRemark = row.auditRemark ?? '';
  auditDialogVisible.value = true;
}

async function submitAudit() {
  if (auditingId.value == null) return;
  auditLoading.value = true;
  try {
    await auditForgeReviewApi(auditingId.value, {
      status: auditForm.status,
      auditRemark: auditForm.auditRemark || undefined,
    });
    ElMessage.success('审核完成');
    auditDialogVisible.value = false;
    reloadGrid();
  } finally {
    auditLoading.value = false;
  }
}

// ------------------------------------------------------------------ 回复
const replyDialogVisible = ref(false);
const replyLoading = ref(false);
const replyingId = ref<number | string | null>(null);
const replyContent = ref('');

function openReply(row: ForgeReviewApi.ReviewItem) {
  replyingId.value = row.id;
  replyContent.value = row.replyContent ?? '';
  replyDialogVisible.value = true;
}

async function submitReply() {
  if (replyingId.value == null) return;
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容');
    return;
  }
  replyLoading.value = true;
  try {
    await replyForgeReviewApi(replyingId.value, replyContent.value.trim());
    ElMessage.success('回复成功');
    replyDialogVisible.value = false;
    reloadGrid();
  } finally {
    replyLoading.value = false;
  }
}

// ------------------------------------------------------------------ 删除
async function handleDelete(row: ForgeReviewApi.ReviewItem) {
  try {
    await ElMessageBox.confirm('确认删除该评价？删除后不可恢复。', '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await deleteForgeReviewApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #rating="{ row }">
        <ElRate :model-value="row.rating ?? 0" disabled size="small" />
      </template>

      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'forge:review:edit'"
            link
            type="primary"
            @click="openAudit(row)"
          >
            审核
          </ElButton>
          <ElButton
            v-access:code="'forge:review:edit'"
            link
            type="primary"
            @click="openReply(row)"
          >
            回复
          </ElButton>
          <ElButton
            v-access:code="'forge:review:delete'"
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
      v-model="auditDialogVisible"
      :close-on-click-modal="false"
      title="审核评价"
      width="480"
    >
      <ElForm :model="auditForm" label-width="90px">
        <ElFormItem label="审核状态">
          <ElSelect v-model="auditForm.status" style="width: 100%">
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="审核备注">
          <ElInput
            v-model="auditForm.auditRemark"
            :rows="3"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="auditDialogVisible = false">取消</ElButton>
        <ElButton :loading="auditLoading" type="primary" @click="submitAudit">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="replyDialogVisible"
      :close-on-click-modal="false"
      title="回复评价"
      width="480"
    >
      <ElForm label-width="90px">
        <ElFormItem label="回复内容">
          <ElInput
            v-model="replyContent"
            :rows="4"
            placeholder="请输入回复内容"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="replyDialogVisible = false">取消</ElButton>
        <ElButton :loading="replyLoading" type="primary" @click="submitReply">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
