<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiKnowledgeApi } from '#/api';

import { computed, reactive, ref } from 'vue';

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
  createAiKnowledgeApi,
  deleteAiKnowledgeApi,
  getAiKnowledgePageApi,
  updateAiKnowledgeApi,
  updateAiKnowledgeStatusApi,
} from '#/api';

import DocumentDrawer from './components/DocumentDrawer.vue';

defineOptions({ name: 'AiKnowledge' });

const gridOptions: VxeTableGridOptions<AiKnowledgeApi.KbItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'kbCode', title: '知识库编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    { field: 'embeddingModel', title: '向量模型', minWidth: 160 },
    { field: 'dimension', title: '维度', width: 90, align: 'center' },
    { field: 'metric', title: '度量', width: 100, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'updateTime',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 240,
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
        return await getAiKnowledgePageApi({
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

// —— 编辑弹窗 ——

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();
/** 向量结构字段（提供者/模型/维度/度量）创建后不可变，编辑态锁定 */
const structFieldsLocked = computed(() => editMode.value === 'edit');

const editForm = reactive<{
  description: string;
  dimension: null | number;
  embeddingModel: string;
  embeddingProvider: string;
  kbCode: string;
  metric: string;
  name: string;
  status: number;
}>({
  description: '',
  dimension: null,
  embeddingModel: '',
  embeddingProvider: '',
  kbCode: '',
  metric: '',
  name: '',
  status: 1,
});

const editRules: FormRules = {
  kbCode: [
    { required: true, message: '请输入知识库编码', trigger: 'blur' },
    {
      pattern: /^[\w-]+$/,
      message: '仅支持字母、数字、下划线、连字符',
      trigger: 'blur',
    },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.description = '';
  editForm.dimension = null;
  editForm.embeddingModel = '';
  editForm.embeddingProvider = '';
  editForm.kbCode = '';
  editForm.metric = '';
  editForm.name = '';
  editForm.status = 1;
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiKnowledgeApi.KbItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.kbCode = row.kbCode;
  editForm.name = row.name ?? '';
  editForm.description = row.description ?? '';
  editForm.embeddingProvider = row.embeddingProvider ?? '';
  editForm.embeddingModel = row.embeddingModel ?? '';
  editForm.dimension = row.dimension ?? null;
  editForm.metric = row.metric ?? '';
  editForm.status = row.status ?? 1;
  editVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload: AiKnowledgeApi.KbSaveParams = {
      name: editForm.name || undefined,
      description: editForm.description || undefined,
      embeddingProvider: editForm.embeddingProvider || undefined,
      embeddingModel: editForm.embeddingModel || undefined,
      dimension: editForm.dimension,
      metric: editForm.metric || undefined,
      status: editForm.status,
    };
    if (editMode.value === 'create') {
      // 编码创建后不可变更，仅创建时上行
      payload.kbCode = editForm.kbCode;
      await createAiKnowledgeApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiKnowledgeApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiKnowledgeApi.KbItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiKnowledgeStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiKnowledgeApi.KbItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除知识库「${row.name || row.kbCode}」？其下所有文档、切片与向量将一并清除，且不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiKnowledgeApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

// —— 文档管理抽屉 ——

const docDrawerRef = ref<InstanceType<typeof DocumentDrawer>>();

function openDocuments(row: AiKnowledgeApi.KbItem) {
  docDrawerRef.value?.open(row);
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-knowledge:add'"
          type="primary"
          @click="openCreate"
        >
          新增知识库
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
            v-access:code="'manager:ai-knowledge:query'"
            link
            type="primary"
            @click="openDocuments(row)"
          >
            文档
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-knowledge:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-knowledge:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-knowledge:delete'"
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
      :title="editMode === 'create' ? '新增知识库' : '编辑知识库'"
      width="620"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="知识库编码" prop="kbCode">
          <ElInput
            v-model="editForm.kbCode"
            :disabled="editMode === 'edit'"
            placeholder="如 product-faq（创建后不可变更）"
          />
        </ElFormItem>
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="便于识别的名称" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            placeholder="可选，描述知识库用途"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="向量提供者">
          <ElInput
            v-model="editForm.embeddingProvider"
            :disabled="structFieldsLocked"
            placeholder="留空取当前 embedding 配置"
          />
        </ElFormItem>
        <ElFormItem label="向量模型">
          <ElInput
            v-model="editForm.embeddingModel"
            :disabled="structFieldsLocked"
            placeholder="留空取当前 embedding 配置"
          />
        </ElFormItem>
        <ElFormItem label="向量维度">
          <ElInputNumber
            v-model="editForm.dimension"
            :disabled="structFieldsLocked"
            :max="8192"
            :min="1"
            controls-position="right"
            placeholder="留空取当前维度"
          />
        </ElFormItem>
        <ElFormItem label="相似度度量">
          <ElInput
            v-model="editForm.metric"
            :disabled="structFieldsLocked"
            placeholder="留空默认 COSINE"
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

    <DocumentDrawer ref="docDrawerRef" />
  </Page>
</template>
