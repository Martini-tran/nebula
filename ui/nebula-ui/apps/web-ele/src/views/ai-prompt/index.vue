<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiPromptApi } from '#/api';

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
  ElSelect,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiPromptApi,
  deleteAiPromptApi,
  getAiPromptPageApi,
  updateAiPromptApi,
} from '#/api';

defineOptions({ name: 'AiPrompt' });

/** 消息角色可选项，与后端 PromptAdminServiceImpl.ALLOWED_ROLES 保持一致 */
const ROLE_OPTIONS = [
  { label: 'system', value: 'system' },
  { label: 'user', value: 'user' },
  { label: 'assistant', value: 'assistant' },
];

/** 角色 → 标签配色 */
const ROLE_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning'> = {
  assistant: 'success',
  system: 'primary',
  user: 'warning',
};

/** 取角色标签配色，未知或缺省角色回退为 info */
function roleTagType(role?: string) {
  return role ? (ROLE_TAG_TYPE[role] ?? 'info') : 'info';
}

/** 正文输入框提示。占位符含双花括号，写在模板里会被 Vue 当作插值，故放在脚本里绑定 */
const CONTENT_PLACEHOLDER =
  '支持 {{变量名}} 占位符，渲染时由下方声明的变量替换';

const gridOptions: VxeTableGridOptions<AiPromptApi.PromptItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'promptCode', title: '提示词编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    { field: 'role', title: '角色', width: 110, slots: { default: 'role' } },
    {
      field: 'content',
      title: '正文',
      minWidth: 260,
      showOverflow: 'tooltip',
    },
    {
      field: 'variables',
      title: '变量',
      width: 100,
      align: 'center',
      slots: { default: 'variables' },
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
      width: 140,
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
        return await getAiPromptPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          role: formValues?.role || undefined,
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
        fieldName: 'role',
        label: '角色',
        componentProps: {
          clearable: true,
          options: ROLE_OPTIONS,
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  content: string;
  name: string;
  promptCode: string;
  remark: string;
  role: string;
  variablesText: string;
}>({
  content: '',
  name: '',
  promptCode: '',
  remark: '',
  role: 'system',
  variablesText: '',
});

const editRules: FormRules = {
  promptCode: [
    { required: true, message: '请输入提示词编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
  content: [{ required: true, message: '请输入提示词正文', trigger: 'blur' }],
  role: [{ required: true, message: '请选择消息角色', trigger: 'change' }],
};

function resetForm() {
  editForm.content = '';
  editForm.name = '';
  editForm.promptCode = '';
  editForm.remark = '';
  editForm.role = 'system';
  editForm.variablesText = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiPromptApi.PromptItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.promptCode = row.promptCode;
  editForm.name = row.name ?? '';
  editForm.role = row.role ?? 'system';
  editForm.content = row.content ?? '';
  editForm.remark = row.remark ?? '';
  editForm.variablesText = row.variables?.length
    ? JSON.stringify(row.variables, null, 2)
    : '';
  editVisible.value = true;
}

/** 解析变量声明 JSON 文本，非法时抛错由调用方提示 */
function parseVariables(): AiPromptApi.PromptVariable[] | undefined {
  const text = editForm.variablesText.trim();
  if (!text) return undefined;
  const parsed = JSON.parse(text);
  if (!Array.isArray(parsed)) {
    throw new TypeError('变量声明必须是 JSON 数组');
  }
  for (const item of parsed) {
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      throw new TypeError('变量声明的每个元素必须是 JSON 对象');
    }
    if (!item.name) {
      throw new TypeError('变量声明的每个元素必须包含 name 字段');
    }
  }
  return parsed as AiPromptApi.PromptVariable[];
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  let variables: AiPromptApi.PromptVariable[] | undefined;
  try {
    variables = parseVariables();
  } catch (error) {
    ElMessage.error(`变量声明解析失败：${(error as Error).message}`);
    return;
  }

  editLoading.value = true;
  try {
    const payload: AiPromptApi.PromptSaveParams = {
      promptCode: editForm.promptCode,
      name: editForm.name || undefined,
      role: editForm.role,
      content: editForm.content,
      variables,
      remark: editForm.remark || undefined,
    };
    if (editMode.value === 'create') {
      await createAiPromptApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiPromptApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: AiPromptApi.PromptItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除提示词「${row.name || row.promptCode}」？流程/节点引用该提示词时将无法解析。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiPromptApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-prompt:add'"
          type="primary"
          @click="openCreate"
        >
          新增提示词
        </ElButton>
      </template>

      <template #role="{ row }">
        <ElTag :type="roleTagType(row.role)" size="small">
          {{ row.role }}
        </ElTag>
      </template>

      <template #variables="{ row }">
        <ElTag v-if="row.variables?.length" size="small" type="success">
          {{ row.variables.length }} 个
        </ElTag>
        <ElTag v-else size="small" type="info">无</ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-prompt:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-prompt:delete'"
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
      :title="editMode === 'create' ? '新增提示词' : '编辑提示词'"
      width="720"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="提示词编码" prop="promptCode">
          <ElInput
            v-model="editForm.promptCode"
            :disabled="editMode === 'edit'"
            placeholder="如 BLOG_SERIES_OUTLINE"
          />
        </ElFormItem>
        <ElFormItem label="提示词名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="便于识别的名称" />
        </ElFormItem>
        <ElFormItem label="消息角色" prop="role">
          <ElSelect v-model="editForm.role" class="w-full">
            <ElOption
              v-for="item in ROLE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="提示词正文" prop="content">
          <ElInput
            v-model="editForm.content"
            :placeholder="CONTENT_PLACEHOLDER"
            :rows="10"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="变量声明">
          <ElInput
            v-model="editForm.variablesText"
            :rows="6"
            placeholder='JSON 数组，如 [{"name":"topic","type":"string","required":true,"description":"主题"}]'
            type="textarea"
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
