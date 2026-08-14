<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiSkillApi, AiToolApi } from '#/api';

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
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createAiSkillApi,
  deleteAiSkillApi,
  getAiSkillPageApi,
  getAiToolPageApi,
  updateAiSkillApi,
  updateAiSkillStatusApi,
} from '#/api';

defineOptions({ name: 'AiSkill' });

/** 触发方式可选项，与后端 SkillAdminServiceImpl.ALLOWED_TRIGGER_TYPES 保持一致 */
const TRIGGER_OPTIONS = [
  { label: 'MANUAL（被引用才装载）', value: 'MANUAL' },
  { label: 'AUTO（范围内始终装载）', value: 'AUTO' },
];

/** 搜索栏用的精简触发方式选项 */
const TRIGGER_FILTER_OPTIONS = [
  { label: 'MANUAL', value: 'MANUAL' },
  { label: 'AUTO', value: 'AUTO' },
];

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
];

/**
 * 指令正文提示。占位符含双花括号，写在模板里会被 Vue 当作插值，故放在脚本里绑定。
 */
const INSTRUCTIONS_PLACEHOLDER =
  'Markdown 正文，命中后作为 system 消息注入；支持 {{变量名}} 占位符，由编排上下文变量池渲染';

/** 可绑定的工具选项（取自 ai_tool 只读表中已启用的工具） */
const toolOptions = ref<AiToolApi.ToolItem[]>([]);

/** 加载可绑定工具清单，失败不阻塞页面（仍可手工输入编码） */
async function loadToolOptions() {
  try {
    const result = await getAiToolPageApi({
      pageNum: 1,
      pageSize: 200,
      enabled: 1,
    });
    toolOptions.value = result.records ?? [];
  } catch {
    toolOptions.value = [];
  }
}

onMounted(loadToolOptions);

const gridOptions: VxeTableGridOptions<AiSkillApi.SkillItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'skillCode', title: '技能编码', minWidth: 180 },
    { field: 'name', title: '名称', minWidth: 140 },
    {
      field: 'description',
      title: '描述',
      minWidth: 220,
      showOverflow: 'tooltip',
    },
    {
      field: 'triggerType',
      title: '触发',
      width: 100,
      slots: { default: 'triggerType' },
    },
    {
      field: 'toolCodes',
      title: '绑定工具',
      minWidth: 180,
      slots: { default: 'toolCodes' },
    },
    { field: 'sortNo', title: '排序', width: 80, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 90,
      align: 'center',
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
        return await getAiSkillPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          triggerType: formValues?.triggerType || undefined,
          status:
            formValues?.status === '' || formValues?.status === undefined
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
        fieldName: 'triggerType',
        label: '触发方式',
        componentProps: {
          clearable: true,
          options: TRIGGER_FILTER_OPTIONS,
        },
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: STATUS_OPTIONS,
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
  description: string;
  instructions: string;
  mcpServerCodesText: string;
  name: string;
  remark: string;
  skillCode: string;
  sortNo: number;
  status: number;
  toolCodes: string[];
  triggerType: string;
}>({
  description: '',
  instructions: '',
  mcpServerCodesText: '',
  name: '',
  remark: '',
  skillCode: '',
  sortNo: 0,
  status: 1,
  toolCodes: [],
  triggerType: 'MANUAL',
});

const editRules: FormRules = {
  skillCode: [
    { required: true, message: '请输入技能编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
  description: [{ max: 512, message: '最多 512 个字符', trigger: 'blur' }],
  triggerType: [
    { required: true, message: '请选择触发方式', trigger: 'change' },
  ],
};

function resetForm() {
  editForm.description = '';
  editForm.instructions = '';
  editForm.mcpServerCodesText = '';
  editForm.name = '';
  editForm.remark = '';
  editForm.skillCode = '';
  editForm.sortNo = 0;
  editForm.status = 1;
  editForm.toolCodes = [];
  editForm.triggerType = 'MANUAL';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AiSkillApi.SkillItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.skillCode = row.skillCode;
  editForm.name = row.name ?? '';
  editForm.description = row.description ?? '';
  editForm.instructions = row.instructions ?? '';
  editForm.triggerType = row.triggerType ?? 'MANUAL';
  editForm.toolCodes = [...(row.toolCodes ?? [])];
  editForm.mcpServerCodesText = (row.mcpServerCodes ?? []).join(', ');
  editForm.sortNo = row.sortNo ?? 0;
  editForm.status = row.status ?? 1;
  editForm.remark = row.remark ?? '';
  editVisible.value = true;
}

/** 逗号/换行分隔的编码文本 → 去空去重的数组 */
function parseCodeText(text: string) {
  const codes = text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
  return codes.length > 0 ? [...new Set(codes)] : undefined;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  // 与后端 validate 一致：指令正文与绑定工具不能同时为空，否则技能没有任何效果
  if (!editForm.instructions.trim() && editForm.toolCodes.length === 0) {
    ElMessage.error('指令正文与绑定工具不能同时为空');
    return;
  }

  editLoading.value = true;
  try {
    const payload: AiSkillApi.SkillSaveParams = {
      skillCode: editForm.skillCode,
      name: editForm.name || undefined,
      description: editForm.description || undefined,
      instructions: editForm.instructions || undefined,
      triggerType: editForm.triggerType,
      toolCodes: editForm.toolCodes.length > 0 ? editForm.toolCodes : undefined,
      mcpServerCodes: parseCodeText(editForm.mcpServerCodesText),
      sortNo: editForm.sortNo,
      status: editForm.status,
      remark: editForm.remark || undefined,
    };
    if (editMode.value === 'create') {
      await createAiSkillApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value !== null && editingId.value !== undefined) {
      await updateAiSkillApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleStatusChange(row: AiSkillApi.SkillItem, value: number) {
  try {
    await updateAiSkillStatusApi(row.id, value);
    ElMessage.success(value === 1 ? '已启用' : '已停用');
    reloadGrid();
  } catch {
    // 失败时回滚开关视觉状态，交由下一次刷新对齐
    reloadGrid();
  }
}

async function handleDelete(row: AiSkillApi.SkillItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除技能「${row.name || row.skillCode}」？Agent/节点引用该技能时将不再装载其指令与工具。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiSkillApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'manager:ai-skill:add'"
          type="primary"
          @click="openCreate"
        >
          新增技能
        </ElButton>
      </template>

      <template #triggerType="{ row }">
        <ElTag
          :type="row.triggerType === 'AUTO' ? 'success' : 'info'"
          size="small"
        >
          {{ row.triggerType || 'MANUAL' }}
        </ElTag>
      </template>

      <template #toolCodes="{ row }">
        <div v-if="row.toolCodes?.length" class="flex flex-wrap gap-1">
          <ElTag
            v-for="code in row.toolCodes"
            :key="code"
            size="small"
            type="primary"
          >
            {{ code }}
          </ElTag>
        </div>
        <ElTag v-else size="small" type="info">无</ElTag>
      </template>

      <template #status="{ row }">
        <ElSwitch
          :active-value="1"
          :inactive-value="0"
          :model-value="row.status"
          @update:model-value="handleStatusChange(row, Number($event))"
        />
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'manager:ai-skill:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'manager:ai-skill:delete'"
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
      :title="editMode === 'create' ? '新增技能' : '编辑技能'"
      width="760"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <ElFormItem label="技能编码" prop="skillCode">
          <ElInput
            v-model="editForm.skillCode"
            :disabled="editMode === 'edit'"
            placeholder="如 TECH_BLOG_WRITING"
          />
        </ElFormItem>
        <ElFormItem label="技能名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="便于识别的名称" />
        </ElFormItem>
        <ElFormItem label="技能描述" prop="description">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            placeholder="这个技能解决什么问题，供人工挑选与后续语义匹配"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="指令正文">
          <ElInput
            v-model="editForm.instructions"
            :placeholder="INSTRUCTIONS_PLACEHOLDER"
            :rows="12"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="绑定工具">
          <ElSelect
            v-model="editForm.toolCodes"
            allow-create
            class="w-full"
            filterable
            multiple
            placeholder="命中技能后，这些工具并入该次调用的工具白名单"
          >
            <ElOption
              v-for="item in toolOptions"
              :key="item.toolCode"
              :label="
                item.name ? `${item.toolCode}（${item.name}）` : item.toolCode
              "
              :value="item.toolCode"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="MCP 服务器">
          <ElInput
            v-model="editForm.mcpServerCodesText"
            placeholder="可选，多个编码用逗号分隔"
          />
        </ElFormItem>
        <ElFormItem label="触发方式" prop="triggerType">
          <ElSelect v-model="editForm.triggerType" class="w-full">
            <ElOption
              v-for="item in TRIGGER_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="排序号">
          <ElInputNumber v-model="editForm.sortNo" :min="0" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSwitch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
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
