<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiRelayProviderApi, AiRelayRecommendApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

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
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiRelayRecommendApi,
  deleteAiRelayRecommendApi,
  getAiRelayProviderPageApi,
  getAiRelayRecommendPageApi,
  updateAiRelayRecommendApi,
  updateAiRelayRecommendStatusApi,
} from '#/api';

defineOptions({ name: 'AiRelayRecommend' });

// -------------------- 主表格 --------------------

const gridOptions: VxeTableGridOptions<AiRelayRecommendApi.RecommendItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'providerName', title: '服务商', minWidth: 160 },
    {
      field: 'recommendReason',
      title: '推荐原因',
      minWidth: 260,
      showOverflow: 'tooltip',
    },
    {
      field: 'reviewScore',
      title: '评分',
      width: 90,
      align: 'center',
      slots: { default: 'reviewScore' },
    },
    {
      field: 'useScenario',
      title: '使用场景',
      minWidth: 160,
      showOverflow: 'tooltip',
    },
    {
      field: 'recommendTime',
      title: '推荐时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'reviewTime',
      title: '测评时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
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
        return await getAiRelayRecommendPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          providerId:
            formValues?.providerId === '' || formValues?.providerId == null
              ? undefined
              : formValues.providerId,
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

// -------------------- 服务商下拉数据 --------------------

const providerOptions = ref<AiRelayProviderApi.ProviderItem[]>([]);

async function loadProviders() {
  const result = await getAiRelayProviderPageApi({
    pageNum: 1,
    pageSize: 200,
    status: 1,
  });
  providerOptions.value = result.records;
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
  recommendReason: string;
  reviewContent: string;
  reviewScore: number | undefined;
  pros: string;
  cons: string;
  useScenario: string;
  firstUseTime: string;
  reviewTime: string;
  recommendTime: string;
  sortOrder: number;
  status: number;
}>({
  providerId: null,
  recommendReason: '',
  reviewContent: '',
  reviewScore: undefined,
  pros: '',
  cons: '',
  useScenario: '',
  firstUseTime: '',
  reviewTime: '',
  recommendTime: '',
  sortOrder: 0,
  status: 1,
});

const editRules: FormRules = {
  providerId: [
    { required: true, message: '请选择服务商', trigger: 'change' },
  ],
  recommendReason: [
    { required: true, message: '请输入推荐原因', trigger: 'blur' },
    { max: 1000, message: '最多 1000 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  editForm.providerId = null;
  editForm.recommendReason = '';
  editForm.reviewContent = '';
  editForm.reviewScore = undefined;
  editForm.pros = '';
  editForm.cons = '';
  editForm.useScenario = '';
  editForm.firstUseTime = '';
  editForm.reviewTime = '';
  editForm.recommendTime = '';
  editForm.sortOrder = 0;
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  editForm.recommendTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  editVisible.value = true;
}

function openEdit(row: AiRelayRecommendApi.RecommendItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.providerId = row.providerId;
  editForm.recommendReason = row.recommendReason;
  editForm.reviewContent = row.reviewContent ?? '';
  editForm.reviewScore =
    row.reviewScore == null ? undefined : Number(row.reviewScore);
  editForm.pros = row.pros ?? '';
  editForm.cons = row.cons ?? '';
  editForm.useScenario = row.useScenario ?? '';
  editForm.firstUseTime = row.firstUseTime ?? '';
  editForm.reviewTime = row.reviewTime ?? '';
  editForm.recommendTime = row.recommendTime ?? '';
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
    const payload: AiRelayRecommendApi.RecommendParams = {
      provider_id: editForm.providerId as number | string,
      recommend_reason: editForm.recommendReason,
      review_content: editForm.reviewContent || undefined,
      review_score: editForm.reviewScore,
      pros: editForm.pros || undefined,
      cons: editForm.cons || undefined,
      use_scenario: editForm.useScenario || undefined,
      first_use_time: editForm.firstUseTime || undefined,
      review_time: editForm.reviewTime || undefined,
      recommend_time: editForm.recommendTime || undefined,
      sort_order: editForm.sortOrder,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      await createAiRelayRecommendApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiRelayRecommendApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiRelayRecommendApi.RecommendItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiRelayRecommendStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiRelayRecommendApi.RecommendItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除推荐「${row.providerName ?? row.providerId}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiRelayRecommendApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

onMounted(() => {
  loadProviders().catch(() => {});
});
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:ai-relay:recommend:add'"
          type="primary"
          @click="openCreate"
        >
          新增推荐
        </ElButton>
      </template>

      <template #reviewScore="{ row }">
        <ElTag v-if="row.reviewScore != null" type="warning" size="small">
          {{ Number(row.reviewScore).toFixed(1) }}
        </ElTag>
        <span v-else class="text-muted">-</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '下线' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'blog:ai-relay:recommend:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下线' : '上线' }}
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:recommend:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:ai-relay:recommend:delete'"
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
      :title="editMode === 'create' ? '新增推荐' : '编辑推荐'"
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
        <ElFormItem label="推荐原因" prop="recommendReason">
          <ElInput
            v-model="editForm.recommendReason"
            :rows="2"
            placeholder="一句话推荐理由（用于列表展示）"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="完整测评">
          <ElInput
            v-model="editForm.reviewContent"
            :rows="6"
            placeholder="支持 Markdown 的完整测评内容"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="评分">
          <ElInputNumber
            v-model="editForm.reviewScore"
            :min="0"
            :max="10"
            :precision="1"
            :step="0.5"
            controls-position="right"
            placeholder="0-10"
          />
        </ElFormItem>
        <ElFormItem label="优点">
          <ElInput
            v-model="editForm.pros"
            :rows="3"
            placeholder="多个用换行/分号分隔"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="缺点">
          <ElInput
            v-model="editForm.cons"
            :rows="3"
            placeholder="多个用换行/分号分隔"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="使用场景">
          <ElInput
            v-model="editForm.useScenario"
            placeholder="如 日常聊天 / 编码 / 长文写作"
          />
        </ElFormItem>
        <ElFormItem label="首次使用">
          <ElDatePicker
            v-model="editForm.firstUseTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="首次使用时间"
            clearable
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="测评时间">
          <ElDatePicker
            v-model="editForm.reviewTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="完成测评时间"
            clearable
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="推荐时间">
          <ElDatePicker
            v-model="editForm.recommendTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="对外推荐时间"
            clearable
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
.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
