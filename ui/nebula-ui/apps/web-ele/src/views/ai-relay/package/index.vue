<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  AiRelayModelApi,
  AiRelayPackageApi,
  AiRelayPackageTypeApi,
  AiRelayProviderApi,
} from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayPackageApi,
  createAiRelayPackageLimitApi,
  createAiRelayPackageModelApi,
  deleteAiRelayPackageApi,
  deleteAiRelayPackageLimitApi,
  deleteAiRelayPackageModelApi,
  getAiRelayModelOptionsApi,
  getAiRelayPackageLimitListApi,
  getAiRelayPackageModelListApi,
  getAiRelayPackagePageApi,
  getAiRelayPackageTypeListApi,
  getAiRelayProviderPageApi,
  updateAiRelayPackageApi,
  updateAiRelayPackageLimitApi,
  updateAiRelayPackageModelApi,
  updateAiRelayPackageStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayPackage' });

const limitTypeOptions = [
  { label: '总额度', value: 1 },
  { label: '每日额度', value: 2 },
  { label: '每周额度', value: 3 },
  { label: '每月额度', value: 4 },
  { label: '单次额度', value: 5 },
] as const;

const resetCycleOptions = [
  { label: '不重置', value: 0 },
  { label: '每日', value: 1 },
  { label: '每周', value: 2 },
  { label: '每月', value: 3 },
  { label: '套餐周期', value: 4 },
] as const;

const overLimitStrategyOptions = [
  { label: '禁止使用', value: 1 },
  { label: '按量计费', value: 2 },
  { label: '限速', value: 3 },
] as const;

function findLabel<T extends { value: number; label: string }>(
  list: readonly T[],
  value?: number,
) {
  return list.find((i) => i.value === value)?.label ?? '-';
}

// -------------------- 主表格 --------------------

const gridOptions: VxeTableGridOptions<AiRelayPackageApi.PackageItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'providerName', title: '服务商', minWidth: 140 },
    {
      field: 'packageTypeName',
      title: '套餐类型',
      width: 110,
      slots: { default: 'packageType' },
    },
    { field: 'name', title: '套餐名称', minWidth: 200 },
    {
      field: 'price',
      title: '价格',
      width: 140,
      slots: { default: 'price' },
    },
    {
      field: 'isRecommended',
      title: '推荐',
      width: 80,
      align: 'center',
      slots: { default: 'recommend' },
    },
    { field: 'recommendScore', title: '推荐分', width: 90, align: 'center' },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
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
      width: 320,
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
        return await getAiRelayPackagePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          providerId: formValues?.providerId || undefined,
          packageTypeId: formValues?.packageTypeId || undefined,
          keyword: formValues?.keyword || undefined,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : Number(formValues.status),
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

// 顶部筛选 / 弹窗共用：服务商、套餐类型选项
const providerOptions = ref<AiRelayProviderApi.ProviderItem[]>([]);
const packageTypeOptions = ref<AiRelayPackageTypeApi.PackageTypeItem[]>([]);
const modelOptions = ref<AiRelayModelApi.ModelItem[]>([]);

async function loadOptions() {
  const [providerPage, types, models] = await Promise.all([
    getAiRelayProviderPageApi({ pageNum: 1, pageSize: 500, status: 1 }),
    getAiRelayPackageTypeListApi(1),
    getAiRelayModelOptionsApi(),
  ]);
  providerOptions.value = providerPage.records ?? [];
  packageTypeOptions.value = types ?? [];
  modelOptions.value = models ?? [];
}

const [Grid, gridApi] = usenebulaVxeGrid({
  gridOptions,
  showSearchForm: true,
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'keyword', label: '关键词' },
      {
        component: 'Select',
        fieldName: 'providerId',
        label: '服务商',
        componentProps: () => ({
          clearable: true,
          filterable: true,
          options: providerOptions.value.map((p) => ({
            label: p.name,
            value: p.id,
          })),
        }),
      },
      {
        component: 'Select',
        fieldName: 'packageTypeId',
        label: '套餐类型',
        componentProps: () => ({
          clearable: true,
          options: packageTypeOptions.value.map((t) => ({
            label: t.name,
            value: t.id,
          })),
        }),
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: [
            { label: '正常', value: 1 },
            { label: '下线', value: 0 },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

onMounted(async () => {
  await loadOptions();
});

// -------------------- 编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  providerId: null | number | string;
  packageTypeId: null | number | string;
  name: string;
  price: number;
  originalPrice: null | number;
  currency: string;
  isRecommended: number;
  recommendScore: number;
  description: string;
  sortOrder: number;
  status: number;
}>({
  providerId: null,
  packageTypeId: null,
  name: '',
  price: 0,
  originalPrice: null,
  currency: 'CNY',
  isRecommended: 0,
  recommendScore: 0,
  description: '',
  sortOrder: 0,
  status: 1,
});

const editRules: FormRules = {
  providerId: [
    { required: true, message: '请选择服务商', trigger: 'change' },
  ],
  packageTypeId: [
    { required: true, message: '请选择套餐类型', trigger: 'change' },
  ],
  name: [
    { required: true, message: '请输入套餐名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  price: [{ required: true, message: '请输入套餐价格', trigger: 'blur' }],
};

function resetForm() {
  editForm.providerId = null;
  editForm.packageTypeId = null;
  editForm.name = '';
  editForm.price = 0;
  editForm.originalPrice = null;
  editForm.currency = 'CNY';
  editForm.isRecommended = 0;
  editForm.recommendScore = 0;
  editForm.description = '';
  editForm.sortOrder = 0;
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiRelayPackageApi.PackageItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.providerId = row.providerId;
  editForm.packageTypeId = row.packageTypeId;
  editForm.name = row.name;
  editForm.price = Number(row.price ?? 0);
  editForm.originalPrice =
    row.originalPrice == null ? null : Number(row.originalPrice);
  editForm.currency = row.currency ?? 'CNY';
  editForm.isRecommended = row.isRecommended ?? 0;
  editForm.recommendScore = Number(row.recommendScore ?? 0);
  editForm.description = row.description ?? '';
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.status = row.status ?? 1;
  editVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const basePayload = {
      package_type_id: editForm.packageTypeId,
      name: editForm.name,
      price: editForm.price,
      original_price: editForm.originalPrice ?? undefined,
      currency: editForm.currency || undefined,
      is_recommended: editForm.isRecommended,
      recommend_score: editForm.recommendScore,
      description: editForm.description || undefined,
      sort_order: editForm.sortOrder,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayPackageApi({
        ...basePayload,
        provider_id: editForm.providerId!,
        package_type_id: editForm.packageTypeId!,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayPackageApi(editingId.value, basePayload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayPackageApi.PackageItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayPackageStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiRelayPackageApi.PackageItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除套餐「${row.name}」？将级联清理限额与模型绑定。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayPackageApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

// -------------------- 限额抽屉 --------------------

const limitDrawer = ref(false);
const limitPackage = ref<AiRelayPackageApi.PackageItem | null>(null);
const limitList = ref<AiRelayPackageApi.PackageLimitItem[]>([]);
const limitLoading = ref(false);

async function reloadLimits() {
  if (!limitPackage.value) return;
  limitLoading.value = true;
  try {
    limitList.value = await getAiRelayPackageLimitListApi(limitPackage.value.id);
  } finally {
    limitLoading.value = false;
  }
}

function openLimitDrawer(row: AiRelayPackageApi.PackageItem) {
  limitPackage.value = row;
  limitDrawer.value = true;
  reloadLimits();
}

const limitDialog = ref(false);
const limitMode = ref<EditMode>('create');
const limitEditingId = ref<null | number | string>(null);
const limitLoadingSubmit = ref(false);
const limitFormRef = ref<FormInstance>();

const limitForm = reactive<{
  limitType: number;
  quotaAmount: number;
  quotaUnit: string;
  resetCycle: number;
  overLimitStrategy: number;
  description: string;
  status: number;
}>({
  limitType: 1,
  quotaAmount: 0,
  quotaUnit: 'token',
  resetCycle: 0,
  overLimitStrategy: 1,
  description: '',
  status: 1,
});

const limitRules: FormRules = {
  limitType: [{ required: true, message: '请选择限制类型', trigger: 'change' }],
  quotaAmount: [{ required: true, message: '请输入额度数量', trigger: 'blur' }],
  quotaUnit: [{ required: true, message: '请输入额度单位', trigger: 'blur' }],
};

function resetLimitForm() {
  limitForm.limitType = 1;
  limitForm.quotaAmount = 0;
  limitForm.quotaUnit = 'token';
  limitForm.resetCycle = 0;
  limitForm.overLimitStrategy = 1;
  limitForm.description = '';
  limitForm.status = 1;
  limitFormRef.value?.clearValidate();
}

function openLimitCreate() {
  limitMode.value = 'create';
  limitEditingId.value = null;
  resetLimitForm();
  limitDialog.value = true;
}

function openLimitEdit(row: AiRelayPackageApi.PackageLimitItem) {
  limitMode.value = 'edit';
  limitEditingId.value = row.id;
  resetLimitForm();
  limitForm.limitType = row.limitType ?? 1;
  limitForm.quotaAmount = Number(row.quotaAmount ?? 0);
  limitForm.quotaUnit = row.quotaUnit ?? 'token';
  limitForm.resetCycle = row.resetCycle ?? 0;
  limitForm.overLimitStrategy = row.overLimitStrategy ?? 1;
  limitForm.description = row.description ?? '';
  limitForm.status = row.status ?? 1;
  limitDialog.value = true;
}

async function submitLimit() {
  if (!limitFormRef.value || !limitPackage.value) return;
  const valid = await limitFormRef.value.validate().catch(() => false);
  if (!valid) return;

  limitLoadingSubmit.value = true;
  try {
    const payload = {
      limit_type: limitForm.limitType,
      quota_amount: limitForm.quotaAmount,
      quota_unit: limitForm.quotaUnit,
      reset_cycle: limitForm.resetCycle,
      over_limit_strategy: limitForm.overLimitStrategy,
      description: limitForm.description || undefined,
      status: limitForm.status,
    };
    if (limitMode.value === 'create') {
      await createAiRelayPackageLimitApi(limitPackage.value.id, payload);
      ElMessage.success('已新增');
    } else if (limitEditingId.value != null) {
      await updateAiRelayPackageLimitApi(
        limitPackage.value.id,
        limitEditingId.value,
        payload,
      );
      ElMessage.success('已保存');
    }
    limitDialog.value = false;
    reloadLimits();
  } finally {
    limitLoadingSubmit.value = false;
  }
}

async function deleteLimit(row: AiRelayPackageApi.PackageLimitItem) {
  if (!limitPackage.value) return;
  try {
    await ElMessageBox.confirm('确认删除该限额？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await deleteAiRelayPackageLimitApi(limitPackage.value.id, row.id);
  ElMessage.success('已删除');
  reloadLimits();
}

// -------------------- 模型抽屉 --------------------

const modelDrawer = ref(false);
const modelPackage = ref<AiRelayPackageApi.PackageItem | null>(null);
const packageModelList = ref<AiRelayPackageApi.PackageModelItem[]>([]);
const packageModelLoading = ref(false);

async function reloadPackageModels() {
  if (!modelPackage.value) return;
  packageModelLoading.value = true;
  try {
    packageModelList.value = await getAiRelayPackageModelListApi(
      modelPackage.value.id,
    );
  } finally {
    packageModelLoading.value = false;
  }
}

function openModelDrawer(row: AiRelayPackageApi.PackageItem) {
  modelPackage.value = row;
  modelDrawer.value = true;
  reloadPackageModels();
}

const modelDialog = ref(false);
const modelMode = ref<EditMode>('create');
const modelEditingId = ref<null | number | string>(null);
const modelLoadingSubmit = ref(false);
const modelFormRef = ref<FormInstance>();

const modelForm = reactive<{
  modelId: null | number | string;
  providerModelCode: string;
  consumeMultiplier: number;
  minChargeAmount: null | number;
  maxContextTokens: null | number;
  inputPricePerMillionTokens: null | number;
  outputPricePerMillionTokens: null | number;
  isDefault: number;
  sortOrder: number;
  status: number;
}>({
  modelId: null,
  providerModelCode: '',
  consumeMultiplier: 1,
  minChargeAmount: null,
  maxContextTokens: null,
  inputPricePerMillionTokens: null,
  outputPricePerMillionTokens: null,
  isDefault: 0,
  sortOrder: 0,
  status: 1,
});

const modelRules: FormRules = {
  modelId: [{ required: true, message: '请选择模型', trigger: 'change' }],
};

function resetModelForm() {
  modelForm.modelId = null;
  modelForm.providerModelCode = '';
  modelForm.consumeMultiplier = 1;
  modelForm.minChargeAmount = null;
  modelForm.maxContextTokens = null;
  modelForm.inputPricePerMillionTokens = null;
  modelForm.outputPricePerMillionTokens = null;
  modelForm.isDefault = 0;
  modelForm.sortOrder = 0;
  modelForm.status = 1;
  modelFormRef.value?.clearValidate();
}

function openModelCreate() {
  modelMode.value = 'create';
  modelEditingId.value = null;
  resetModelForm();
  modelDialog.value = true;
}

function openModelEdit(row: AiRelayPackageApi.PackageModelItem) {
  modelMode.value = 'edit';
  modelEditingId.value = row.id;
  resetModelForm();
  modelForm.modelId = row.modelId;
  modelForm.providerModelCode = row.providerModelCode ?? '';
  modelForm.consumeMultiplier = Number(row.consumeMultiplier ?? 1);
  modelForm.minChargeAmount =
    row.minChargeAmount == null ? null : Number(row.minChargeAmount);
  modelForm.maxContextTokens = row.maxContextTokens ?? null;
  modelForm.inputPricePerMillionTokens =
    row.inputPricePerMillionTokens == null
      ? null
      : Number(row.inputPricePerMillionTokens);
  modelForm.outputPricePerMillionTokens =
    row.outputPricePerMillionTokens == null
      ? null
      : Number(row.outputPricePerMillionTokens);
  modelForm.isDefault = row.isDefault ?? 0;
  modelForm.sortOrder = row.sortOrder ?? 0;
  modelForm.status = row.status ?? 1;
  modelDialog.value = true;
}

async function submitPackageModel() {
  if (!modelFormRef.value || !modelPackage.value) return;
  const valid = await modelFormRef.value.validate().catch(() => false);
  if (!valid) return;

  modelLoadingSubmit.value = true;
  try {
    const payload = {
      model_id: modelForm.modelId!,
      provider_model_code: modelForm.providerModelCode || undefined,
      consume_multiplier: modelForm.consumeMultiplier,
      min_charge_amount: modelForm.minChargeAmount ?? undefined,
      max_context_tokens: modelForm.maxContextTokens ?? undefined,
      input_price_per_million_tokens:
        modelForm.inputPricePerMillionTokens ?? undefined,
      output_price_per_million_tokens:
        modelForm.outputPricePerMillionTokens ?? undefined,
      is_default: modelForm.isDefault,
      sort_order: modelForm.sortOrder,
      status: modelForm.status,
    };
    if (modelMode.value === 'create') {
      await createAiRelayPackageModelApi(modelPackage.value.id, payload);
      ElMessage.success('已绑定');
    } else if (modelEditingId.value != null) {
      await updateAiRelayPackageModelApi(
        modelPackage.value.id,
        modelEditingId.value,
        payload,
      );
      ElMessage.success('已保存');
    }
    modelDialog.value = false;
    reloadPackageModels();
  } finally {
    modelLoadingSubmit.value = false;
  }
}

async function deletePackageModel(row: AiRelayPackageApi.PackageModelItem) {
  if (!modelPackage.value) return;
  try {
    await ElMessageBox.confirm(
      `确认移除模型「${row.modelName ?? row.modelCode ?? ''}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayPackageModelApi(modelPackage.value.id, row.id);
  ElMessage.success('已移除');
  reloadPackageModels();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:package:add'"
          type="primary"
          @click="openCreate"
        >
          新增套餐
        </ElButton>
      </template>

      <template #packageType="{ row }">
        <ElTag size="small">
          {{ row.packageTypeName ?? row.packageTypeCode ?? '-' }}
        </ElTag>
      </template>

      <template #price="{ row }">
        <span class="price-now">{{ row.currency ?? 'CNY' }} {{ row.price }}</span>
        <span v-if="row.originalPrice != null" class="price-origin">
          {{ row.originalPrice }}
        </span>
      </template>

      <template #recommend="{ row }">
        <ElTag v-if="row.isRecommended === 1" type="warning" size="small">
          推荐
        </ElTag>
        <span v-else>-</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '下线' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:package:query'"
            link
            type="primary"
            @click="openLimitDrawer(row)"
          >
            限额
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package:query'"
            link
            type="primary"
            @click="openModelDrawer(row)"
          >
            模型
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下线' : '上线' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 套餐编辑弹窗 -->
    <ElDialog
      v-model="editVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增套餐' : '编辑套餐'"
      width="720"
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
            :disabled="editMode === 'edit'"
            filterable
            placeholder="请选择服务商"
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
        <ElFormItem label="套餐类型" prop="packageTypeId">
          <ElSelect
            v-model="editForm.packageTypeId"
            placeholder="请选择套餐类型"
            style="width: 100%"
          >
            <ElOption
              v-for="t in packageTypeOptions"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="套餐名称" prop="name">
          <ElInput
            v-model="editForm.name"
            placeholder="如 月卡 / 学生月卡"
            maxlength="100"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="价格" prop="price">
          <ElInputNumber
            v-model="editForm.price"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <ElInput
            v-model="editForm.currency"
            placeholder="币种"
            style="width: 100px; margin-left: 8px"
          />
        </ElFormItem>
        <ElFormItem label="原价">
          <ElInputNumber
            v-model="editForm.originalPrice"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
        </ElFormItem>
        <ElFormItem label="是否推荐">
          <ElSwitch
            v-model="editForm.isRecommended"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
          />
        </ElFormItem>
        <ElFormItem label="推荐分">
          <ElInputNumber
            v-model="editForm.recommendScore"
            :min="0"
            :max="100"
            :precision="2"
            :step="0.5"
            controls-position="right"
            style="width: 220px"
          />
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput
            v-model="editForm.description"
            :rows="3"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="editForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="正常"
            inactive-text="下线"
            inline-prompt
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

    <!-- 限额抽屉 -->
    <ElDrawer
      v-model="limitDrawer"
      :title="`套餐限额 - ${limitPackage?.name ?? ''}`"
      size="720px"
      destroy-on-close
    >
      <div class="drawer-toolbar">
        <ElButton
          v-access:code="'blog:ai-relay:package:edit'"
          type="primary"
          @click="openLimitCreate"
        >
          新增限额
        </ElButton>
      </div>
      <ElTable
        v-loading="limitLoading"
        :data="limitList"
        border
        stripe
        size="small"
      >
        <ElTableColumn label="限制类型" width="120">
          <template #default="{ row }">
            {{ findLabel(limitTypeOptions, row.limitType) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="额度" min-width="160">
          <template #default="{ row }">
            {{ row.quotaAmount }} {{ row.quotaUnit }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="重置周期" width="110">
          <template #default="{ row }">
            {{ findLabel(resetCycleOptions, row.resetCycle) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="超限策略" width="110">
          <template #default="{ row }">
            {{ findLabel(overLimitStrategyOptions, row.overLimitStrategy) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-access:code="'blog:ai-relay:package:edit'"
              link
              type="primary"
              @click="openLimitEdit(row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'blog:ai-relay:package:edit'"
              link
              type="danger"
              @click="deleteLimit(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>

    <!-- 限额编辑弹窗 -->
    <ElDialog
      v-model="limitDialog"
      :close-on-click-modal="false"
      :title="limitMode === 'create' ? '新增限额' : '编辑限额'"
      width="560"
      append-to-body
    >
      <ElForm
        ref="limitFormRef"
        :model="limitForm"
        :rules="limitRules"
        label-width="100px"
      >
        <ElFormItem label="限制类型" prop="limitType">
          <ElSelect v-model="limitForm.limitType" style="width: 100%">
            <ElOption
              v-for="o in limitTypeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="额度数量" prop="quotaAmount">
          <ElInputNumber
            v-model="limitForm.quotaAmount"
            :min="0"
            :precision="6"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="额度单位" prop="quotaUnit">
          <ElInput v-model="limitForm.quotaUnit" placeholder="token / request / credit" />
        </ElFormItem>
        <ElFormItem label="重置周期">
          <ElSelect v-model="limitForm.resetCycle" style="width: 100%">
            <ElOption
              v-for="o in resetCycleOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="超限策略">
          <ElSelect v-model="limitForm.overLimitStrategy" style="width: 100%">
            <ElOption
              v-for="o in overLimitStrategyOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput
            v-model="limitForm.description"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="limitForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="limitDialog = false">取消</ElButton>
        <ElButton
          :loading="limitLoadingSubmit"
          type="primary"
          @click="submitLimit"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 模型抽屉 -->
    <ElDrawer
      v-model="modelDrawer"
      :title="`套餐模型 - ${modelPackage?.name ?? ''}`"
      size="780px"
      destroy-on-close
    >
      <div class="drawer-toolbar">
        <ElButton
          v-access:code="'blog:ai-relay:package:edit'"
          type="primary"
          @click="openModelCreate"
        >
          绑定模型
        </ElButton>
      </div>
      <ElTable
        v-loading="packageModelLoading"
        :data="packageModelList"
        border
        stripe
        size="small"
      >
        <ElTableColumn label="模型" min-width="220">
          <template #default="{ row }">
            <div class="model-cell">
              <strong>{{ row.modelName ?? row.modelCode }}</strong>
              <span class="model-meta">{{ row.modelCode }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="厂商" prop="modelVendor" width="120" />
        <ElTableColumn label="服务商侧编码" prop="providerModelCode" min-width="160" />
        <ElTableColumn
          label="输入价 / 1M"
          prop="inputPricePerMillionTokens"
          width="130"
          align="right"
          sortable
        >
          <template #default="{ row }">
            <span v-if="row.inputPricePerMillionTokens != null">
              {{ Number(row.inputPricePerMillionTokens).toFixed(4) }}
            </span>
            <span v-else>-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="输出价 / 1M"
          prop="outputPricePerMillionTokens"
          width="130"
          align="right"
          sortable
        >
          <template #default="{ row }">
            <span v-if="row.outputPricePerMillionTokens != null">
              {{ Number(row.outputPricePerMillionTokens).toFixed(4) }}
            </span>
            <span v-else>-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="倍率" prop="consumeMultiplier" width="90" align="center" />
        <ElTableColumn label="默认" width="80" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.isDefault === 1" type="warning" size="small">默认</ElTag>
            <span v-else>-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-access:code="'blog:ai-relay:package:edit'"
              link
              type="primary"
              @click="openModelEdit(row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'blog:ai-relay:package:edit'"
              link
              type="danger"
              @click="deletePackageModel(row)"
            >
              移除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>

    <!-- 模型绑定弹窗 -->
    <ElDialog
      v-model="modelDialog"
      :close-on-click-modal="false"
      :title="modelMode === 'create' ? '绑定模型' : '编辑模型'"
      width="600"
      append-to-body
    >
      <ElForm
        ref="modelFormRef"
        :model="modelForm"
        :rules="modelRules"
        label-width="120px"
      >
        <ElFormItem label="模型" prop="modelId">
          <ElSelect
            v-model="modelForm.modelId"
            :disabled="modelMode === 'edit'"
            filterable
            placeholder="请选择模型"
            style="width: 100%"
          >
            <ElOption
              v-for="m in modelOptions"
              :key="m.id"
              :label="`${m.name} (${m.code})`"
              :value="m.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="服务商侧编码">
          <ElInput
            v-model="modelForm.providerModelCode"
            placeholder="不填则默认使用模型编码"
          />
        </ElFormItem>
        <ElFormItem label="消耗倍率">
          <ElInputNumber
            v-model="modelForm.consumeMultiplier"
            :min="0"
            :precision="4"
            :step="0.1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="最低扣费">
          <ElInputNumber
            v-model="modelForm.minChargeAmount"
            :min="0"
            :precision="6"
            :step="0.01"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="最大上下文">
          <ElInputNumber
            v-model="modelForm.maxContextTokens"
            :min="0"
            :step="1024"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="输入价/百万Token">
          <ElInputNumber
            v-model="modelForm.inputPricePerMillionTokens"
            :min="0"
            :precision="4"
            :step="0.5"
            controls-position="right"
            style="width: 100%"
            placeholder="如 3.0000，币种沿用套餐"
          />
        </ElFormItem>
        <ElFormItem label="输出价/百万Token">
          <ElInputNumber
            v-model="modelForm.outputPricePerMillionTokens"
            :min="0"
            :precision="4"
            :step="0.5"
            controls-position="right"
            style="width: 100%"
            placeholder="如 15.0000，币种沿用套餐"
          />
        </ElFormItem>
        <ElFormItem label="是否默认模型">
          <ElSwitch
            v-model="modelForm.isDefault"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
          />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="modelForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="modelForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="modelDialog = false">取消</ElButton>
        <ElButton
          :loading="modelLoadingSubmit"
          type="primary"
          @click="submitPackageModel"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.price-now {
  font-weight: 600;
  color: var(--el-color-danger);
}

.price-origin {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
  font-size: 12px;
}

.model-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.model-meta {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
