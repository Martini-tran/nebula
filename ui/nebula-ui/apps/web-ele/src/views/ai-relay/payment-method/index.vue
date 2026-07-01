<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiRelayPaymentMethodApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

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
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayPaymentMethodApi,
  deleteAiRelayPaymentMethodApi,
  getAiRelayPaymentMethodListApi,
  updateAiRelayPaymentMethodApi,
  updateAiRelayPaymentMethodStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayPaymentMethod' });

const gridOptions: VxeTableGridOptions<AiRelayPaymentMethodApi.PaymentMethodItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'code', title: '编码', minWidth: 140 },
    { field: 'name', title: '名称', minWidth: 160 },
    {
      field: 'description',
      title: '说明',
      minWidth: 240,
      showOverflow: 'tooltip',
    },
    { field: 'sortOrder', title: '排序', width: 90, align: 'center' },
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
const list = ref<AiRelayPaymentMethodApi.PaymentMethodItem[]>([]);

async function reloadGrid() {
  const data = await getAiRelayPaymentMethodListApi();
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
  description: string;
  sortOrder: number;
  status: number;
}>({
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
  status: 1,
});

const editRules: FormRules = {
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { max: 50, message: '最多 50 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 50, message: '最多 50 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.code = '';
  editForm.name = '';
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

function openEdit(row: AiRelayPaymentMethodApi.PaymentMethodItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.code = row.code;
  editForm.name = row.name;
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
      description: editForm.description || undefined,
      sortOrder: editForm.sortOrder,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayPaymentMethodApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayPaymentMethodApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayPaymentMethodApi.PaymentMethodItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayPaymentMethodStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  await reloadGrid();
}

async function handleDelete(row: AiRelayPaymentMethodApi.PaymentMethodItem) {
  try {
    await ElMessageBox.confirm(`确认删除支付方式「${row.name}」？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await deleteAiRelayPaymentMethodApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:payment-method:add'"
          type="primary"
          @click="openCreate"
        >
          新增支付方式
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
            v-access:code="'blog:ai-relay:payment-method:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:payment-method:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:payment-method:delete'"
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
      :title="editMode === 'create' ? '新增支付方式' : '编辑支付方式'"
      width="520"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="编码" prop="code">
          <ElInput
            v-model="editForm.code"
            :disabled="editMode === 'edit'"
            placeholder="如 alipay / wechat / paypal"
          />
        </ElFormItem>
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="如 支付宝" />
        </ElFormItem>
        <ElFormItem label="说明" prop="description">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="排序" prop="sortOrder">
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
