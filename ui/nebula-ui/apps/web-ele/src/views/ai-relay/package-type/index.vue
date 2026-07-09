<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiRelayPackageTypeApi } from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayPackageTypeApi,
  deleteAiRelayPackageTypeApi,
  getAiRelayPackageTypeListApi,
  updateAiRelayPackageTypeApi,
  updateAiRelayPackageTypeStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayPackageType' });

const billingModeOptions = [
  { label: '固定周期', value: 1 },
  { label: '按量计费', value: 2 },
] as const;

const durationUnitOptions = [
  { label: '天', value: 1 },
  { label: '周', value: 2 },
  { label: '月', value: 3 },
  { label: '年', value: 4 },
] as const;

function getBillingModeLabel(value?: number) {
  return billingModeOptions.find((i) => i.value === value)?.label ?? '-';
}

function getDurationUnitLabel(value?: number) {
  return durationUnitOptions.find((i) => i.value === value)?.label;
}

function formatDuration(row: AiRelayPackageTypeApi.PackageTypeItem) {
  if (row.billingMode !== 1) return '-';
  if (row.durationValue == null || row.durationUnit == null) return '-';
  return `${row.durationValue} ${getDurationUnitLabel(row.durationUnit) ?? ''}`;
}

const gridOptions: VxeTableGridOptions<AiRelayPackageTypeApi.PackageTypeItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'code', title: '编码', minWidth: 140 },
    { field: 'name', title: '名称', minWidth: 140 },
    {
      field: 'billingMode',
      title: '计费模式',
      width: 120,
      slots: { default: 'billingMode' },
    },
    {
      field: 'duration',
      title: '周期',
      width: 120,
      slots: { default: 'duration' },
    },
    {
      field: 'description',
      title: '说明',
      minWidth: 220,
      showOverflow: 'tooltip',
    },
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
      width: 200,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  data: [],
  height: 'auto',
  pagerConfig: { enabled: false },
  proxyConfig: { enabled: false },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, zoom: true },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });
const list = ref<AiRelayPackageTypeApi.PackageTypeItem[]>([]);

async function reloadGrid() {
  const data = await getAiRelayPackageTypeListApi();
  list.value = data ?? [];
  await gridApi.setGridOptions({ data: list.value });
}

onMounted(reloadGrid);

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  code: string;
  name: string;
  billingMode: number;
  durationValue: null | number;
  durationUnit: null | number;
  description: string;
  sortOrder: number;
  status: number;
}>({
  code: '',
  name: '',
  billingMode: 1,
  durationValue: 1,
  durationUnit: 1,
  description: '',
  sortOrder: 0,
  status: 1,
});

const isFixedCycle = computed(() => editForm.billingMode === 1);

const editRules = computed<FormRules>(() => {
  const baseRequired = (msg: string) => ({
    required: true,
    message: msg,
    trigger: 'blur',
  });
  return {
    code: [baseRequired('请输入编码'), { max: 50, message: '最多 50 个字符' }],
    name: [baseRequired('请输入名称'), { max: 50, message: '最多 50 个字符' }],
    billingMode: [
      { required: true, message: '请选择计费模式', trigger: 'change' },
    ],
    durationValue: isFixedCycle.value
      ? [{ required: true, message: '固定周期需填写周期数值', trigger: 'blur' }]
      : [],
    durationUnit: isFixedCycle.value
      ? [{ required: true, message: '固定周期需选择周期单位', trigger: 'change' }]
      : [],
  };
});

function resetForm() {
  editForm.code = '';
  editForm.name = '';
  editForm.billingMode = 1;
  editForm.durationValue = 1;
  editForm.durationUnit = 1;
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

function openEdit(row: AiRelayPackageTypeApi.PackageTypeItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.code = row.code;
  editForm.name = row.name;
  editForm.billingMode = row.billingMode ?? 1;
  editForm.durationValue = row.durationValue ?? null;
  editForm.durationUnit = row.durationUnit ?? null;
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
    const payload = {
      code: editForm.code,
      name: editForm.name,
      billingMode: editForm.billingMode,
      durationValue: isFixedCycle.value ? editForm.durationValue ?? undefined : undefined,
      durationUnit: isFixedCycle.value ? editForm.durationUnit ?? undefined : undefined,
      description: editForm.description || undefined,
      sortOrder: editForm.sortOrder,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayPackageTypeApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayPackageTypeApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayPackageTypeApi.PackageTypeItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayPackageTypeStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  await reloadGrid();
}

async function handleDelete(row: AiRelayPackageTypeApi.PackageTypeItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除套餐类型「${row.name}」？存在套餐引用时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayPackageTypeApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:package-type:add'"
          type="primary"
          @click="openCreate"
        >
          新增套餐类型
        </ElButton>
      </template>

      <template #billingMode="{ row }">
        <ElTag :type="row.billingMode === 1 ? 'primary' : 'warning'" size="small">
          {{ getBillingModeLabel(row.billingMode) }}
        </ElTag>
      </template>

      <template #duration="{ row }">
        {{ formatDuration(row) }}
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:package-type:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package-type:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:package-type:delete'"
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
      :title="editMode === 'create' ? '新增套餐类型' : '编辑套餐类型'"
      width="560"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <ElFormItem label="编码" prop="code">
          <ElInput
            v-model="editForm.code"
            :disabled="editMode === 'edit'"
            placeholder="如 day / week / month / usage"
          />
        </ElFormItem>
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="如 月卡" />
        </ElFormItem>
        <ElFormItem label="计费模式" prop="billingMode">
          <ElSelect v-model="editForm.billingMode" style="width: 100%">
            <ElOption
              v-for="o in billingModeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="isFixedCycle" label="周期数值" prop="durationValue">
          <ElInputNumber
            v-model="editForm.durationValue"
            :min="1"
            :max="9999"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem v-if="isFixedCycle" label="周期单位" prop="durationUnit">
          <ElSelect v-model="editForm.durationUnit" style="width: 100%">
            <ElOption
              v-for="o in durationUnitOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput
            v-model="editForm.description"
            :rows="2"
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
            active-text="启用"
            inactive-text="停用"
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
  </Page>
</template>
