<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  AiRelayPaymentMethodApi,
  AiRelayProviderApi,
} from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCheckbox,
  ElDatePicker,
  ElDialog,
  ElDivider,
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
  bindAiRelayProviderPaymentMethodsApi,
  createAiRelayProviderAdvantageApi,
  createAiRelayProviderApi,
  deleteAiRelayProviderAdvantageApi,
  deleteAiRelayProviderApi,
  getAiRelayPaymentMethodListApi,
  getAiRelayProviderAdvantageListApi,
  getAiRelayProviderPageApi,
  getAiRelayProviderPaymentMethodListApi,
  updateAiRelayProviderAdvantageApi,
  updateAiRelayProviderApi,
  updateAiRelayProviderStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayProvider' });

const advantageTypeOptions = [
  { label: '普通优势', value: 1, tagType: 'primary' as const },
  { label: '核心优势', value: 2, tagType: 'success' as const },
  { label: '风险提示', value: 3, tagType: 'danger' as const },
] as const;

function getAdvantageTypeLabel(value?: number) {
  return advantageTypeOptions.find((i) => i.value === value)?.label ?? '-';
}

function getAdvantageTypeTagType(value?: number) {
  return advantageTypeOptions.find((i) => i.value === value)?.tagType ?? 'info';
}

// -------------------- 主表格 --------------------

const gridOptions: VxeTableGridOptions<AiRelayProviderApi.ProviderItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '服务商名称', minWidth: 180 },
    {
      field: 'websiteUrl',
      title: '官网',
      minWidth: 220,
      slots: { default: 'websiteUrl' },
    },
    { field: 'recommendScore', title: '推荐分', width: 100, align: 'center' },
    { field: 'sortOrder', title: '排序', width: 90, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'lastSyncTime',
      title: '同步时间',
      width: 170,
      slots: { default: 'lastSyncTime' },
    },
    {
      field: 'createTime',
      title: '收录时间',
      width: 170,
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
        return await getAiRelayProviderPageApi({
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
      { component: 'Input', fieldName: 'keyword', label: '关键词' },
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

// -------------------- 编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  name: string;
  websiteUrl: string;
  description: string;
  recommendScore: number;
  sortOrder: number;
  status: number;
  lastSyncTime: string;
}>({
  name: '',
  websiteUrl: '',
  description: '',
  recommendScore: 0,
  sortOrder: 0,
  status: 1,
  lastSyncTime: '',
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入服务商名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.name = '';
  editForm.websiteUrl = '';
  editForm.description = '';
  editForm.recommendScore = 0;
  editForm.sortOrder = 0;
  editForm.status = 1;
  editForm.lastSyncTime = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiRelayProviderApi.ProviderItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.name = row.name;
  editForm.websiteUrl = row.websiteUrl ?? '';
  editForm.description = row.description ?? '';
  editForm.recommendScore = Number(row.recommendScore ?? 0);
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.status = row.status ?? 1;
  editForm.lastSyncTime = row.lastSyncTime ?? '';
  editVisible.value = true;
}

function markSyncedNow() {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  editForm.lastSyncTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = {
      name: editForm.name,
      website_url: editForm.websiteUrl || undefined,
      description: editForm.description || undefined,
      recommend_score: editForm.recommendScore,
      sort_order: editForm.sortOrder,
      status: editForm.status,
      last_sync_time: editForm.lastSyncTime || undefined,
    };
    if (editMode.value === 'create') {
      await createAiRelayProviderApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayProviderApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayProviderApi.ProviderItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayProviderStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiRelayProviderApi.ProviderItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除服务商「${row.name}」？需先清空其下套餐，删除后将级联清理优势/支付方式绑定。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayProviderApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

// -------------------- 优势抽屉 --------------------

const advantageDrawer = ref(false);
const advantageProvider = ref<AiRelayProviderApi.ProviderItem | null>(null);
const advantageList = ref<AiRelayProviderApi.AdvantageItem[]>([]);
const advantageLoading = ref(false);

async function reloadAdvantages() {
  if (!advantageProvider.value) return;
  advantageLoading.value = true;
  try {
    advantageList.value = await getAiRelayProviderAdvantageListApi(
      advantageProvider.value.id,
    );
  } finally {
    advantageLoading.value = false;
  }
}

function openAdvantageDrawer(row: AiRelayProviderApi.ProviderItem) {
  advantageProvider.value = row;
  advantageDrawer.value = true;
  reloadAdvantages();
}

const advantageDialog = ref(false);
const advantageMode = ref<EditMode>('create');
const advantageEditingId = ref<null | number | string>(null);
const advantageLoadingSubmit = ref(false);
const advantageFormRef = ref<FormInstance>();

const advantageForm = reactive<{
  title: string;
  content: string;
  advantageType: number;
  sortOrder: number;
  status: number;
}>({
  title: '',
  content: '',
  advantageType: 1,
  sortOrder: 0,
  status: 1,
});

const advantageRules: FormRules = {
  title: [
    { required: true, message: '请输入优势标题', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
};

function resetAdvantageForm() {
  advantageForm.title = '';
  advantageForm.content = '';
  advantageForm.advantageType = 1;
  advantageForm.sortOrder = 0;
  advantageForm.status = 1;
  advantageFormRef.value?.clearValidate();
}

function openAdvantageCreate() {
  advantageMode.value = 'create';
  advantageEditingId.value = null;
  resetAdvantageForm();
  advantageDialog.value = true;
}

function openAdvantageEdit(row: AiRelayProviderApi.AdvantageItem) {
  advantageMode.value = 'edit';
  advantageEditingId.value = row.id;
  resetAdvantageForm();
  advantageForm.title = row.title;
  advantageForm.content = row.content ?? '';
  advantageForm.advantageType = row.advantageType ?? 1;
  advantageForm.sortOrder = row.sortOrder ?? 0;
  advantageForm.status = row.status ?? 1;
  advantageDialog.value = true;
}

async function submitAdvantage() {
  if (!advantageFormRef.value || !advantageProvider.value) return;
  const valid = await advantageFormRef.value.validate().catch(() => false);
  if (!valid) return;

  advantageLoadingSubmit.value = true;
  try {
    const payload = {
      title: advantageForm.title,
      content: advantageForm.content || undefined,
      advantage_type: advantageForm.advantageType,
      sort_order: advantageForm.sortOrder,
      status: advantageForm.status,
    };
    if (advantageMode.value === 'create') {
      await createAiRelayProviderAdvantageApi(
        advantageProvider.value.id,
        payload,
      );
      ElMessage.success('已新增优势');
    } else if (advantageEditingId.value != null) {
      await updateAiRelayProviderAdvantageApi(
        advantageProvider.value.id,
        advantageEditingId.value,
        payload,
      );
      ElMessage.success('已保存');
    }
    advantageDialog.value = false;
    reloadAdvantages();
  } finally {
    advantageLoadingSubmit.value = false;
  }
}

async function deleteAdvantage(row: AiRelayProviderApi.AdvantageItem) {
  if (!advantageProvider.value) return;
  try {
    await ElMessageBox.confirm(`确认删除优势「${row.title}」？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await deleteAiRelayProviderAdvantageApi(advantageProvider.value.id, row.id);
  ElMessage.success('已删除');
  reloadAdvantages();
}

// -------------------- 支付方式绑定抽屉 --------------------

const paymentDrawer = ref(false);
const paymentProvider = ref<AiRelayProviderApi.ProviderItem | null>(null);
const paymentLoading = ref(false);
const paymentSubmitting = ref(false);
const allPaymentMethods = ref<AiRelayPaymentMethodApi.PaymentMethodItem[]>([]);
const selectedPaymentMethodIds = ref<Array<number | string>>([]);

async function ensureAllPaymentMethods() {
  if (allPaymentMethods.value.length > 0) return;
  allPaymentMethods.value = await getAiRelayPaymentMethodListApi(1);
}

async function openPaymentDrawer(row: AiRelayProviderApi.ProviderItem) {
  paymentProvider.value = row;
  paymentDrawer.value = true;
  paymentLoading.value = true;
  try {
    await ensureAllPaymentMethods();
    const bound = await getAiRelayProviderPaymentMethodListApi(row.id);
    selectedPaymentMethodIds.value = bound.map((b) => b.paymentMethodId);
  } finally {
    paymentLoading.value = false;
  }
}

function isSelected(id: number | string) {
  return selectedPaymentMethodIds.value.some((v) => String(v) === String(id));
}

function togglePaymentMethod(id: number | string, checked: boolean) {
  const set = new Set(selectedPaymentMethodIds.value.map(String));
  if (checked) {
    set.add(String(id));
  } else {
    set.delete(String(id));
  }
  selectedPaymentMethodIds.value = [...set];
}

async function submitPaymentBinding() {
  if (!paymentProvider.value) return;
  paymentSubmitting.value = true;
  try {
    await bindAiRelayProviderPaymentMethodsApi(paymentProvider.value.id, {
      payment_method_ids: selectedPaymentMethodIds.value,
    });
    ElMessage.success('支付方式已更新');
    paymentDrawer.value = false;
  } finally {
    paymentSubmitting.value = false;
  }
}

onMounted(() => {
  // 预加载支付方式，便于 drawer 打开时即时展示
  ensureAllPaymentMethods().catch(() => {});
});
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:provider:add'"
          type="primary"
          @click="openCreate"
        >
          新增服务商
        </ElButton>
      </template>

      <template #websiteUrl="{ row }">
        <a v-if="row.websiteUrl" :href="row.websiteUrl" target="_blank" rel="noopener">
          {{ row.websiteUrl }}
        </a>
        <span v-else>-</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '下线' }}
        </ElTag>
      </template>

      <template #lastSyncTime="{ row }">
        <span v-if="row.lastSyncTime">{{ row.lastSyncTime }}</span>
        <span v-else class="text-muted">未同步</span>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:provider:query'"
            link
            type="primary"
            @click="openAdvantageDrawer(row)"
          >
            优势
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:provider:query'"
            link
            type="primary"
            @click="openPaymentDrawer(row)"
          >
            支付方式
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:provider:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下线' : '上线' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:provider:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:provider:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 服务商编辑弹窗 -->
    <ElDialog
      v-model="editVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增服务商' : '编辑服务商'"
      width="640"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <ElFormItem label="名称" prop="name">
          <ElInput
            v-model="editForm.name"
            placeholder="如 OpenRouter / DeepBricks"
          />
        </ElFormItem>
        <ElFormItem label="官网">
          <ElInput v-model="editForm.websiteUrl" placeholder="https://..." />
        </ElFormItem>
        <ElFormItem label="简介">
          <ElInput
            v-model="editForm.description"
            :rows="3"
            placeholder="可选，1000 字以内"
            type="textarea"
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
            style="width: 100%"
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
        <ElFormItem label="同步时间">
          <div class="sync-time-row">
            <ElDatePicker
              v-model="editForm.lastSyncTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择最近一次同步价格/模型的时间"
              clearable
              style="flex: 1"
            />
            <ElButton link type="primary" @click="markSyncedNow">
              标记为现在
            </ElButton>
          </div>
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 优势抽屉 -->
    <ElDrawer
      v-model="advantageDrawer"
      :title="`服务商优势 - ${advantageProvider?.name ?? ''}`"
      size="640px"
      destroy-on-close
    >
      <div class="drawer-toolbar">
        <ElButton
          v-access:code="'blog:ai-relay:provider:edit'"
          type="primary"
          @click="openAdvantageCreate"
        >
          新增优势
        </ElButton>
      </div>
      <ElTable
        v-loading="advantageLoading"
        :data="advantageList"
        border
        stripe
        size="small"
      >
        <ElTableColumn label="标题" prop="title" min-width="160" />
        <ElTableColumn label="类型" width="110">
          <template #default="{ row }">
            <ElTag :type="getAdvantageTypeTagType(row.advantageType)" size="small">
              {{ getAdvantageTypeLabel(row.advantageType) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="说明"
          prop="content"
          min-width="200"
          show-overflow-tooltip
        />
        <ElTableColumn label="排序" prop="sortOrder" width="80" align="center" />
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
              v-access:code="'blog:ai-relay:provider:edit'"
              link
              type="primary"
              @click="openAdvantageEdit(row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'blog:ai-relay:provider:edit'"
              link
              type="danger"
              @click="deleteAdvantage(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>

    <!-- 优势编辑弹窗 -->
    <ElDialog
      v-model="advantageDialog"
      :close-on-click-modal="false"
      :title="advantageMode === 'create' ? '新增优势' : '编辑优势'"
      width="520"
      append-to-body
    >
      <ElForm
        ref="advantageFormRef"
        :model="advantageForm"
        :rules="advantageRules"
        label-width="90px"
      >
        <ElFormItem label="标题" prop="title">
          <ElInput v-model="advantageForm.title" placeholder="如 高性价比" />
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput
            v-model="advantageForm.content"
            :rows="3"
            placeholder="可选，500 字以内"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="类型">
          <ElSelect v-model="advantageForm.advantageType" style="width: 100%">
            <ElOption
              v-for="o in advantageTypeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="advantageForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="advantageForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="advantageDialog = false">取消</ElButton>
        <ElButton
          :loading="advantageLoadingSubmit"
          type="primary"
          @click="submitAdvantage"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 支付方式绑定抽屉 -->
    <ElDrawer
      v-model="paymentDrawer"
      :title="`支付方式绑定 - ${paymentProvider?.name ?? ''}`"
      size="480px"
      destroy-on-close
    >
      <div v-loading="paymentLoading" class="payment-list">
        <p class="payment-hint">勾选服务商支持的支付方式，保存后全量覆盖。</p>
        <ElDivider />
        <div class="payment-grid">
          <ElCheckbox
            v-for="item in allPaymentMethods"
            :key="item.id"
            :model-value="isSelected(item.id)"
            @change="(v: boolean) => togglePaymentMethod(item.id, v)"
          >
            {{ item.name }}
            <span class="payment-code">({{ item.code }})</span>
          </ElCheckbox>
        </div>
      </div>
      <template #footer>
        <ElButton @click="paymentDrawer = false">取消</ElButton>
        <ElButton
          :loading="paymentSubmitting"
          type="primary"
          @click="submitPaymentBinding"
        >
          保存
        </ElButton>
      </template>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.payment-list {
  padding: 0 4px;
}

.payment-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.payment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  row-gap: 12px;
}

.payment-code {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-left: 4px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.sync-time-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
</style>
