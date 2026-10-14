<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemApiKeyApi } from '#/api';

import { reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDatePicker,
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
  createSystemApiKeyApi,
  deleteSystemApiKeyApi,
  getSystemApiKeyPageApi,
  updateSystemApiKeyApi,
  updateSystemApiKeyStatusApi,
} from '#/api';

defineOptions({ name: 'SystemApiKey' });

/**
 * 已开放 API Key 访问的授权范围（即后端标了 @SaCheckApiKey 的接口所要求的权限码）。
 * 仅作下拉候选，可手输其它值；后端会校验操作人自己是否拥有该权限。
 */
const SCOPE_OPTIONS = [
  { value: 'blog:article:publish', label: 'blog:article:publish（发布文章）' },
  { value: 'space:me:read', label: 'space:me:read（MCP 读取个人空间）' },
  { value: 'space:report:write', label: 'space:report:write（MCP 写日报周报）' },
  { value: 'space:me:write', label: 'space:me:write（MCP 写任务与笔记）' },
];

// ===== 表格 + 搜索 =====
const gridOptions: VxeTableGridOptions<SystemApiKeyApi.ApiKeyItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '名称', minWidth: 160 },
    {
      field: 'keyMask',
      title: '密钥',
      minWidth: 170,
      slots: { default: 'keyMask' },
    },
    { field: 'username', title: '所属用户', minWidth: 120 },
    {
      field: 'scopes',
      title: '授权范围',
      minWidth: 220,
      slots: { default: 'scopes' },
    },
    {
      field: 'expireTime',
      title: '过期时间',
      width: 190,
      slots: { default: 'expireTime' },
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
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
  height: 'auto',
  keepSource: true,
  pagerConfig: {
    pageSize: 10,
  },
  proxyConfig: {
    autoLoad: true,
    // 后端 PageResult 字段为 records / total，覆盖全局默认 (items / total)
    response: {
      result: 'records',
      total: 'total',
    },
    ajax: {
      query: async ({ page }, formValues) => {
        const params: SystemApiKeyApi.ApiKeyPageQuery = {
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          name: formValues?.name || undefined,
          status:
            formValues?.status === undefined || formValues?.status === ''
              ? undefined
              : Number(formValues.status),
        };
        return await getSystemApiKeyPageApi(params);
      },
    },
  },
  rowConfig: {
    keyField: 'id',
  },
  toolbarConfig: {
    refresh: { code: 'query' },
    custom: true,
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
        component: 'Input',
        fieldName: 'name',
        label: '名称',
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          options: [
            { value: 1, label: '启用' },
            { value: 0, label: '禁用' },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ===== 创建 / 编辑 =====
type EditMode = 'create' | 'edit';
const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  expireTime: null | string;
  name: string;
  remark: string;
  scopes: string[];
}>({
  name: '',
  scopes: [],
  expireTime: null,
  remark: '',
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 100, message: '名称不超过 100 个字符', trigger: 'blur' },
  ],
  scopes: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '请至少添加一个授权范围',
      trigger: 'change',
    },
  ],
};

/** 过期时间只能选今天及以后 */
function isPastDate(date: Date) {
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  return date.getTime() < startOfToday.getTime();
}

function resetEditForm() {
  editForm.name = '';
  editForm.scopes = [];
  editForm.expireTime = null;
  editForm.remark = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetEditForm();
  editDialogVisible.value = true;
}

function openEdit(row: SystemApiKeyApi.ApiKeyItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetEditForm();
  editForm.name = row.name;
  editForm.scopes = [...row.scopes];
  editForm.expireTime = row.expireTime ?? null;
  editForm.remark = row.remark ?? '';
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  const payload: SystemApiKeyApi.ApiKeySaveParams = {
    name: editForm.name.trim(),
    scopes: editForm.scopes,
    expireTime: editForm.expireTime || null,
    remark: editForm.remark || undefined,
  };

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      const created = await createSystemApiKeyApi(payload);
      editDialogVisible.value = false;
      showCreatedKey(created.apiKey);
    } else if (editingId.value !== null) {
      await updateSystemApiKeyApi(editingId.value, payload);
      ElMessage.success('保存成功');
      editDialogVisible.value = false;
    }
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

// ===== 创建成功：明文只展示这一次 =====
const createdDialogVisible = ref(false);
const createdKey = ref('');

function showCreatedKey(apiKey: string) {
  createdKey.value = apiKey;
  createdDialogVisible.value = true;
}

async function copyCreatedKey() {
  try {
    await navigator.clipboard.writeText(createdKey.value);
    ElMessage.success('密钥已复制');
  } catch {
    ElMessage.error('复制失败，请手动选中复制');
  }
}

function closeCreatedDialog() {
  createdDialogVisible.value = false;
  createdKey.value = '';
}

// ===== 状态切换 =====
async function toggleStatus(row: SystemApiKeyApi.ApiKeyItem) {
  const next = row.status === 1 ? 0 : 1;
  const action = next === 1 ? '启用' : '禁用';
  try {
    await ElMessageBox.confirm(`确认${action}密钥「${row.name}」？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await updateSystemApiKeyStatusApi(row.id, next);
  ElMessage.success(`${action}成功`);
  reloadGrid();
}

// ===== 删除 =====
async function handleDelete(row: SystemApiKeyApi.ApiKeyItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除密钥「${row.name}」？删除后使用它的调用方会立即失效，且无法恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteSystemApiKeyApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'system:api-key:add'"
          type="primary"
          @click="openCreate"
        >
          新增密钥
        </ElButton>
      </template>

      <template #keyMask="{ row }">
        <span class="font-mono">{{ row.keyMask }}</span>
      </template>

      <template #scopes="{ row }">
        <div class="flex flex-wrap justify-center gap-1 py-1">
          <ElTag v-for="scope in row.scopes" :key="scope" type="info">
            {{ scope }}
          </ElTag>
        </div>
      </template>

      <template #expireTime="{ row }">
        <ElTag v-if="row.expired" type="danger">已过期</ElTag>
        <span v-else>{{ row.expireTime || '永不过期' }}</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '启用' : '禁用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-3">
          <ElButton
            v-access:code="'system:api-key:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'system:api-key:edit'"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'system:api-key:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 创建 / 编辑 -->
    <ElDialog
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增密钥' : '编辑密钥'"
      width="560"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="名称" prop="name">
          <ElInput
            v-model="editForm.name"
            maxlength="100"
            placeholder="说明这把密钥给谁、做什么用"
          />
        </ElFormItem>
        <ElFormItem label="授权范围" prop="scopes">
          <ElSelect
            v-model="editForm.scopes"
            allow-create
            class="w-full"
            default-first-option
            filterable
            multiple
            placeholder="选择或输入权限码后回车"
          >
            <ElOption
              v-for="option in SCOPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="过期时间" prop="expireTime">
          <ElDatePicker
            v-model="editForm.expireTime"
            :disabled-date="isPastDate"
            placeholder="不选则永不过期"
            style="width: 100%"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="备注" prop="remark">
          <ElInput
            v-model="editForm.remark"
            :rows="2"
            maxlength="500"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="editDialogVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          {{ editMode === 'create' ? '生成密钥' : '保存' }}
        </ElButton>
      </template>
    </ElDialog>

    <!-- 创建成功：展示明文 -->
    <ElDialog
      v-model="createdDialogVisible"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      title="密钥已生成"
      width="560"
    >
      <div class="flex items-center gap-2">
        <ElInput :model-value="createdKey" class="font-mono" readonly />
        <ElButton type="primary" @click="copyCreatedKey">复制</ElButton>
      </div>
      <p class="mt-3 text-sm text-[hsl(var(--muted-foreground))]">
        密钥明文只显示这一次，关闭后只能看到掩码，请现在就保存好。调用接口时放在请求头
        <span class="font-mono">apikey</span> 里。
      </p>

      <template #footer>
        <ElButton type="primary" @click="closeCreatedDialog">
          我已保存
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
