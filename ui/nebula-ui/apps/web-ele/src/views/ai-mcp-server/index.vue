<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiMcpServerApi } from '#/api';

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
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiMcpServerApi,
  deleteAiMcpServerApi,
  getAiMcpServerPageApi,
  updateAiMcpServerApi,
  updateAiMcpServerStatusApi,
} from '#/api';

defineOptions({ name: 'AiMcpServer' });

const TRANSPORT_OPTIONS = [
  { label: 'stdio（本地子进程）', value: 'stdio' },
  { label: 'sse（远程 SSE）', value: 'sse' },
  { label: 'streamable-http（远程 HTTP）', value: 'streamable-http' },
];

function transportLabel(value?: string) {
  return TRANSPORT_OPTIONS.find((o) => o.value === value)?.label ?? value ?? '-';
}

const gridOptions: VxeTableGridOptions<AiMcpServerApi.ServerItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'serverCode', title: '服务器编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    {
      field: 'transport',
      title: '传输',
      width: 180,
      slots: { default: 'transport' },
    },
    { field: 'endpoint', title: '连接', minWidth: 200, slots: { default: 'endpoint' } },
    {
      field: 'authToken',
      title: '凭证',
      width: 120,
      slots: { default: 'authToken' },
    },
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
      width: 200,
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
        return await getAiMcpServerPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          transport: formValues?.transport || undefined,
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
        fieldName: 'transport',
        label: '传输',
        componentProps: {
          clearable: true,
          options: TRANSPORT_OPTIONS,
        },
      },
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

type EditMode = 'create' | 'edit';

const editVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();
/** 编辑态下原有凭证的掩码，用于 placeholder 提示「留空不修改」 */
const authTokenPlaceholder = ref('');

const editForm = reactive<{
  argsText: string;
  authToken: string;
  command: string;
  envText: string;
  headersText: string;
  name: string;
  optionsText: string;
  remark: string;
  serverCode: string;
  status: number;
  timeoutMs: null | number;
  transport: string;
  url: string;
}>({
  argsText: '',
  authToken: '',
  command: '',
  envText: '',
  headersText: '',
  name: '',
  optionsText: '',
  remark: '',
  serverCode: '',
  status: 1,
  timeoutMs: null,
  transport: 'stdio',
  url: '',
});

/** stdio 传输显示 command/args/env；远程传输显示 url/headers/authToken */
const isStdio = computed(() => editForm.transport === 'stdio');

const editRules: FormRules = {
  serverCode: [
    { required: true, message: '请输入服务器编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  transport: [{ required: true, message: '请选择传输类型', trigger: 'change' }],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function resetForm() {
  editForm.argsText = '';
  editForm.authToken = '';
  editForm.command = '';
  editForm.envText = '';
  editForm.headersText = '';
  editForm.name = '';
  editForm.optionsText = '';
  editForm.remark = '';
  editForm.serverCode = '';
  editForm.status = 1;
  editForm.timeoutMs = null;
  editForm.transport = 'stdio';
  editForm.url = '';
  authTokenPlaceholder.value = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  authTokenPlaceholder.value = '请输入鉴权凭证（Bearer / apiKey）';
  editVisible.value = true;
}

function openEdit(row: AiMcpServerApi.ServerItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.serverCode = row.serverCode;
  editForm.name = row.name ?? '';
  editForm.transport = row.transport ?? 'stdio';
  editForm.command = row.command ?? '';
  editForm.argsText = (row.args ?? []).join('\n');
  editForm.envText = row.env ? JSON.stringify(row.env, null, 2) : '';
  editForm.url = row.url ?? '';
  editForm.headersText = row.headers
    ? JSON.stringify(row.headers, null, 2)
    : '';
  editForm.timeoutMs = row.timeoutMs ?? null;
  editForm.optionsText = row.options
    ? JSON.stringify(row.options, null, 2)
    : '';
  editForm.status = row.status ?? 1;
  editForm.remark = row.remark ?? '';
  authTokenPlaceholder.value = row.hasAuthToken
    ? `已配置（${row.authTokenMasked ?? '****'}），留空则不修改`
    : '未配置，请输入鉴权凭证';
  editVisible.value = true;
}

/** 解析 args 文本：按行拆分，去空行 */
function parseArgs(): string[] | undefined {
  const lines = editForm.argsText
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
  return lines.length > 0 ? lines : undefined;
}

/** 解析 JSON 对象文本（env/headers/options 共用），非法时抛错由调用方提示 */
function parseJsonObject(
  text: string,
  label: string,
): Record<string, any> | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const parsed = JSON.parse(trimmed);
  if (typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new TypeError(`${label}必须是 JSON 对象`);
  }
  return parsed;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  // 按传输类型校验必填
  if (isStdio.value && !editForm.command.trim()) {
    ElMessage.error('stdio 传输需填写启动命令');
    return;
  }
  if (!isStdio.value && !editForm.url.trim()) {
    ElMessage.error('远程传输需填写端点地址');
    return;
  }

  let env: Record<string, any> | undefined;
  let headers: Record<string, any> | undefined;
  let options: Record<string, any> | undefined;
  try {
    env = parseJsonObject(editForm.envText, '环境变量');
    headers = parseJsonObject(editForm.headersText, '请求头');
    options = parseJsonObject(editForm.optionsText, '扩展参数');
  } catch (error) {
    ElMessage.error(`JSON 解析失败：${(error as Error).message}`);
    return;
  }

  editLoading.value = true;
  try {
    const payload: AiMcpServerApi.ServerSaveParams = {
      serverCode: editForm.serverCode,
      name: editForm.name || undefined,
      transport: editForm.transport,
      // stdio 组
      command: isStdio.value ? editForm.command || undefined : undefined,
      args: isStdio.value ? parseArgs() : undefined,
      env: isStdio.value ? env : undefined,
      // 远程组
      url: isStdio.value ? undefined : editForm.url || undefined,
      headers: isStdio.value ? undefined : headers,
      authToken: isStdio.value ? undefined : editForm.authToken || undefined,
      timeoutMs: editForm.timeoutMs,
      options,
      status: editForm.status,
      remark: editForm.remark || undefined,
    };
    if (editMode.value === 'create') {
      await createAiMcpServerApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateAiMcpServerApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function toggleStatus(row: AiMcpServerApi.ServerItem) {
  const next = row.status === 1 ? 0 : 1;
  await updateAiMcpServerStatusApi(row.id, next);
  ElMessage.success('状态已更新');
  reloadGrid();
}

async function handleDelete(row: AiMcpServerApi.ServerItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除 MCP 服务器「${row.name || row.serverCode}」？引用该服务器的流程/节点将失去该工具源。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiMcpServerApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-mcp-server:add'"
          type="primary"
          @click="openCreate"
        >
          新增服务器
        </ElButton>
      </template>

      <template #transport="{ row }">
        <ElTag size="small" type="primary">
          {{ transportLabel(row.transport) }}
        </ElTag>
      </template>

      <template #endpoint="{ row }">
        <span v-if="row.transport === 'stdio'">{{ row.command || '-' }}</span>
        <span v-else>{{ row.url || '-' }}</span>
      </template>

      <template #authToken="{ row }">
        <ElTag v-if="row.hasAuthToken" size="small" type="success">
          {{ row.authTokenMasked }}
        </ElTag>
        <ElTag v-else size="small" type="info">无</ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-mcp-server:edit'"
            link
            type="primary"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-mcp-server:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-mcp-server:delete'"
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
      :title="editMode === 'create' ? '新增 MCP 服务器' : '编辑 MCP 服务器'"
      width="640"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="服务器编码" prop="serverCode">
          <ElInput
            v-model="editForm.serverCode"
            :disabled="editMode === 'edit'"
            placeholder="如 filesystem-local"
          />
        </ElFormItem>
        <ElFormItem label="服务器名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="便于识别的名称" />
        </ElFormItem>
        <ElFormItem label="传输类型" prop="transport">
          <ElSelect v-model="editForm.transport" class="w-full">
            <ElOption
              v-for="opt in TRANSPORT_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>

        <!-- stdio 组 -->
        <template v-if="isStdio">
          <ElFormItem label="启动命令">
            <ElInput
              v-model="editForm.command"
              placeholder="如 npx / uvx / python"
            />
          </ElFormItem>
          <ElFormItem label="命令参数">
            <ElInput
              v-model="editForm.argsText"
              :rows="3"
              placeholder="每行一个参数，如&#10;-y&#10;@modelcontextprotocol/server-filesystem&#10;/data"
              type="textarea"
            />
          </ElFormItem>
          <ElFormItem label="环境变量">
            <ElInput
              v-model="editForm.envText"
              :rows="3"
              placeholder='JSON 对象，如 {"LOG_LEVEL": "info"}（敏感凭证请放远程的鉴权凭证字段）'
              type="textarea"
            />
          </ElFormItem>
        </template>

        <!-- 远程组 -->
        <template v-else>
          <ElFormItem label="端点地址">
            <ElInput
              v-model="editForm.url"
              placeholder="如 https://mcp.example.com/sse"
            />
          </ElFormItem>
          <ElFormItem label="请求头">
            <ElInput
              v-model="editForm.headersText"
              :rows="3"
              placeholder='JSON 对象（非敏感），如 {"X-Trace": "on"}'
              type="textarea"
            />
          </ElFormItem>
          <ElFormItem label="鉴权凭证">
            <ElInput
              v-model="editForm.authToken"
              :placeholder="authTokenPlaceholder"
              show-password
              type="password"
            />
          </ElFormItem>
        </template>

        <ElFormItem label="超时(ms)">
          <ElInputNumber
            v-model="editForm.timeoutMs"
            :max="600000"
            :min="0"
            :step="1000"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="扩展参数">
          <ElInput
            v-model="editForm.optionsText"
            :rows="2"
            placeholder='JSON 对象，透传未来新增配置'
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
