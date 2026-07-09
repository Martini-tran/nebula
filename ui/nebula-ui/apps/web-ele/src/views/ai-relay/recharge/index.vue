<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  AiRelayPaymentMethodApi,
  AiRelayProviderApi,
  AiRelayRechargeApi,
} from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';

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
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayRechargeApi,
  deleteAiRelayRechargeApi,
  getAiRelayPaymentMethodListApi,
  getAiRelayProviderPageApi,
  getAiRelayRechargePageApi,
  getAiRelayRechargeStatsApi,
  updateAiRelayRechargeApi,
} from '#/api';

defineOptions({ name: 'AiRelayRecharge' });

// -------------------- 主表格 --------------------

const gridOptions: VxeTableGridOptions<AiRelayRechargeApi.RechargeItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'providerName', title: '服务商', minWidth: 140 },
    { field: 'packageName', title: '套餐', minWidth: 140 },
    {
      field: 'amount',
      title: '金额',
      width: 110,
      align: 'right',
      slots: { default: 'amount' },
    },
    { field: 'currency', title: '币种', width: 80, align: 'center' },
    {
      field: 'cnyAmount',
      title: '折合¥',
      width: 110,
      align: 'right',
      slots: { default: 'cnyAmount' },
    },
    { field: 'paymentMethodName', title: '支付方式', width: 110 },
    {
      field: 'rechargeTime',
      title: '充值时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    { field: 'orderNo', title: '订单号', minWidth: 160, showOverflow: 'tooltip' },
    {
      field: 'status',
      title: '状态',
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 160,
      showOverflow: 'tooltip',
    },
    {
      field: 'action',
      title: '操作',
      width: 160,
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
        return await getAiRelayRechargePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          providerId:
            formValues?.providerId === '' || formValues?.providerId == null
              ? undefined
              : formValues.providerId,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : Number(formValues.status),
          startTime: formValues?.dateRange?.[0] || undefined,
          endTime: formValues?.dateRange?.[1] || undefined,
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
          options: [
            { label: '正常', value: 1 },
            { label: '作废', value: 0 },
          ],
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'dateRange',
        label: '充值时间',
        componentProps: {
          type: 'datetimerange',
          rangeSeparator: '-',
          startPlaceholder: '起始',
          endPlaceholder: '结束',
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// -------------------- 服务商/支付方式下拉 --------------------

const providerOptions = ref<AiRelayProviderApi.ProviderItem[]>([]);
const paymentMethodOptions = ref<
  AiRelayPaymentMethodApi.PaymentMethodItem[]
>([]);

async function loadProviders() {
  const result = await getAiRelayProviderPageApi({
    pageNum: 1,
    pageSize: 200,
    status: 1,
  });
  providerOptions.value = result.records;
}

async function loadPaymentMethods() {
  paymentMethodOptions.value = await getAiRelayPaymentMethodListApi(1);
}

// -------------------- 汇总统计 --------------------

const stats = ref<AiRelayRechargeApi.RechargeStats[]>([]);
const totalCny = computed(() =>
  stats.value.reduce((sum, s) => sum + Number(s.totalCnyAmount ?? 0), 0),
);
const totalCount = computed(() =>
  stats.value.reduce((sum, s) => sum + (s.rechargeCount ?? 0), 0),
);

async function loadStats() {
  stats.value = await getAiRelayRechargeStatsApi();
}

// -------------------- 编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  providerId: null | number | string;
  packageId: null | number | string;
  amount: number | undefined;
  currency: string;
  exchangeRate: number | undefined;
  cnyAmount: number | undefined;
  paymentMethodId: null | number | string;
  rechargeTime: string;
  orderNo: string;
  remark: string;
  status: number;
}>({
  providerId: null,
  packageId: null,
  amount: undefined,
  currency: 'CNY',
  exchangeRate: undefined,
  cnyAmount: undefined,
  paymentMethodId: null,
  rechargeTime: '',
  orderNo: '',
  remark: '',
  status: 1,
});

const editRules: FormRules = {
  providerId: [{ required: true, message: '请选择服务商', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  rechargeTime: [
    { required: true, message: '请选择充值时间', trigger: 'change' },
  ],
};

function resetForm() {
  editForm.providerId = null;
  editForm.packageId = null;
  editForm.amount = undefined;
  editForm.currency = 'CNY';
  editForm.exchangeRate = undefined;
  editForm.cnyAmount = undefined;
  editForm.paymentMethodId = null;
  editForm.rechargeTime = '';
  editForm.orderNo = '';
  editForm.remark = '';
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  editForm.rechargeTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  editVisible.value = true;
}

function openEdit(row: AiRelayRechargeApi.RechargeItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.providerId = row.providerId;
  editForm.packageId = row.packageId ?? null;
  editForm.amount = Number(row.amount);
  editForm.currency = row.currency || 'CNY';
  editForm.exchangeRate =
    row.exchangeRate == null ? undefined : Number(row.exchangeRate);
  editForm.cnyAmount =
    row.cnyAmount == null ? undefined : Number(row.cnyAmount);
  editForm.paymentMethodId = row.paymentMethodId ?? null;
  editForm.rechargeTime = row.rechargeTime ?? '';
  editForm.orderNo = row.orderNo ?? '';
  editForm.remark = row.remark ?? '';
  editForm.status = row.status ?? 1;
  editVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload: AiRelayRechargeApi.RechargeParams = {
      providerId: editForm.providerId as number | string,
      packageId: editForm.packageId ?? undefined,
      amount: editForm.amount as number,
      currency: editForm.currency,
      exchangeRate: editForm.exchangeRate,
      cnyAmount: editForm.cnyAmount,
      paymentMethodId: editForm.paymentMethodId ?? undefined,
      rechargeTime: editForm.rechargeTime,
      orderNo: editForm.orderNo || undefined,
      remark: editForm.remark || undefined,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayRechargeApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayRechargeApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
    loadStats();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: AiRelayRechargeApi.RechargeItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除该充值记录？金额 ${row.amount} ${row.currency}`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayRechargeApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
  loadStats();
}

onMounted(() => {
  loadProviders().catch(() => {});
  loadPaymentMethods().catch(() => {});
  loadStats().catch(() => {});
});
</script>

<template>
  <Page auto-content-height>
    <div class="stats-bar">
      <div class="stat-item">
        <div class="stat-label">累计充值次数</div>
        <div class="stat-value">{{ totalCount }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">累计充值（折合¥）</div>
        <div class="stat-value text-primary">¥{{ totalCny.toFixed(2) }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">服务商数</div>
        <div class="stat-value">{{ stats.length }}</div>
      </div>
    </div>

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:recharge:add'"
          type="primary"
          @click="openCreate"
        >
          新增充值记录
        </ElButton>
      </template>

      <template #amount="{ row }">
        {{ Number(row.amount).toFixed(2) }}
      </template>

      <template #cnyAmount="{ row }">
        <span v-if="row.cnyAmount != null">
          ¥{{ Number(row.cnyAmount).toFixed(2) }}
        </span>
        <span v-else class="text-muted">-</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '作废' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:recharge:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:recharge:delete'"
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
      v-model="editVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增充值记录' : '编辑充值记录'"
      width="640"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <ElFormItem label="服务商" prop="providerId">
          <ElSelect
            v-model="editForm.providerId"
            placeholder="选择服务商"
            filterable
            style="width: 100%"
          >
            <ElOption
              v-for="p in providerOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="金额" prop="amount">
          <div class="amount-row">
            <ElInputNumber
              v-model="editForm.amount"
              :min="0"
              :precision="2"
              :step="10"
              controls-position="right"
              placeholder="充值金额"
              style="flex: 1"
            />
            <ElSelect v-model="editForm.currency" style="width: 100px">
              <ElOption label="CNY" value="CNY" />
              <ElOption label="USD" value="USD" />
              <ElOption label="EUR" value="EUR" />
              <ElOption label="USDT" value="USDT" />
            </ElSelect>
          </div>
        </ElFormItem>
        <ElFormItem v-if="editForm.currency !== 'CNY'" label="汇率">
          <ElInputNumber
            v-model="editForm.exchangeRate"
            :min="0"
            :precision="4"
            :step="0.1"
            controls-position="right"
            placeholder="如 USD->CNY 7.20"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="折合人民币">
          <ElInputNumber
            v-model="editForm.cnyAmount"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
            placeholder="留空则按金额*汇率自动计算"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="支付方式">
          <ElSelect
            v-model="editForm.paymentMethodId"
            placeholder="选择支付方式"
            clearable
            style="width: 100%"
          >
            <ElOption
              v-for="m in paymentMethodOptions"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="充值时间" prop="rechargeTime">
          <ElDatePicker
            v-model="editForm.rechargeTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择充值时间"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="订单号">
          <ElInput
            v-model="editForm.orderNo"
            placeholder="可选，平台订单号或交易流水"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="editForm.remark"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.stats-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.stat-item {
  flex: 1;
  padding: 12px 16px;
  background-color: var(--el-bg-color-page);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.text-primary {
  color: var(--el-color-primary);
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.amount-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
