<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ForgeVersionApi } from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

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
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  bindForgeVersionPermissionsApi,
  createForgeVersionApi,
  deleteForgeVersionApi,
  getForgePluginDetailApi,
  getForgeVersionDetailApi,
  getForgeVersionPageApi,
  getForgeVersionPermissionsApi,
  reviewForgeVersionApi,
  updateForgeVersionApi,
  updateForgeVersionStatusApi,
} from '#/api';

defineOptions({ name: 'ForgePluginVersion' });

const route = useRoute();
const router = useRouter();

const pluginId = computed<number | string>(() => {
  const id = route.params.id;
  return Array.isArray(id) ? (id[0] ?? '') : (id ?? '');
});
const pluginName = ref('');

const channelOptions = [
  { label: '稳定（stable）', value: 'stable' },
  { label: '测试（beta）', value: 'beta' },
  { label: '开发（dev）', value: 'dev' },
];
const statusOptions = [
  { label: '草稿', value: 0, tagType: 'info' as const },
  { label: '已发布', value: 1, tagType: 'success' as const },
  { label: '已下架', value: 2, tagType: 'warning' as const },
  { label: '已废弃', value: 3, tagType: 'danger' as const },
];
const reviewOptions = [
  { label: '待审核', value: 0, tagType: 'warning' as const },
  { label: '通过', value: 1, tagType: 'success' as const },
  { label: '拒绝', value: 2, tagType: 'danger' as const },
];
const riskOptions = [
  { label: '低', value: 1 },
  { label: '中', value: 2 },
  { label: '高', value: 3 },
];

function statusLabel(v?: number) {
  return statusOptions.find((o) => o.value === v)?.label ?? '-';
}
function statusTagType(v?: number) {
  return statusOptions.find((o) => o.value === v)?.tagType ?? 'info';
}
function reviewLabel(v?: number) {
  return reviewOptions.find((o) => o.value === v)?.label ?? '-';
}
function reviewTagType(v?: number) {
  return reviewOptions.find((o) => o.value === v)?.tagType ?? 'info';
}

async function loadPluginName() {
  if (!pluginId.value) return;
  try {
    const detail = await getForgePluginDetailApi(pluginId.value);
    pluginName.value = detail.name ?? '';
  } catch {
    // 忽略
  }
}
onMounted(loadPluginName);

const gridOptions: VxeTableGridOptions<ForgeVersionApi.VersionItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'version', title: '版本号', minWidth: 120 },
    { field: 'channel', title: '通道', width: 100 },
    {
      field: 'reviewStatus',
      title: '审核',
      width: 100,
      slots: { default: 'review' },
    },
    { field: 'status', title: '状态', width: 100, slots: { default: 'status' } },
    { field: 'downloadCount', title: '下载', width: 90, align: 'center' },
    { field: 'publishedTime', title: '发布时间', width: 170 },
    { field: 'createTime', title: '创建时间', width: 170 },
    {
      field: 'action',
      title: '操作',
      width: 300,
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
        if (!pluginId.value) {
          return { records: [], total: 0, current: 1, size: page.pageSize };
        }
        return await getForgeVersionPageApi(pluginId.value, {
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          status: formValues?.status ?? undefined,
          reviewStatus: formValues?.reviewStatus ?? undefined,
          channel: formValues?.channel || undefined,
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
};

const [Grid, gridApi] = usenebulaVxeGrid({
  gridOptions,
  showSearchForm: true,
  formOptions: {
    schema: [
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          options: statusOptions.map(({ label, value }) => ({ label, value })),
        },
      },
      {
        component: 'Select',
        fieldName: 'reviewStatus',
        label: '审核',
        componentProps: {
          clearable: true,
          options: reviewOptions.map(({ label, value }) => ({ label, value })),
        },
      },
      {
        component: 'Select',
        fieldName: 'channel',
        label: '通道',
        componentProps: { clearable: true, options: channelOptions },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ------------------------------------------------------------------ 新增 / 编辑
type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const detailLoading = ref(false);
const editFormRef = ref<FormInstance>();

function defaultForm() {
  return {
    version: '',
    channel: 'stable',
    manifestJson: '',
    packageUrl: '',
    packageFileId: '' as number | string,
    packageSha256: '',
    packageSize: undefined as number | undefined,
    signature: '',
    minAppVersion: '',
    maxAppVersion: '',
    changelog: '',
    status: 0,
    remark: '',
  };
}
const editForm = reactive(defaultForm());

const editRules: FormRules = {
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  manifestJson: [{ required: true, message: '请输入 manifest JSON', trigger: 'blur' }],
  packageSha256: [{ required: true, message: '请输入安装包 SHA256', trigger: 'blur' }],
};

function resetForm() {
  Object.assign(editForm, defaultForm());
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
}

async function openEdit(row: ForgeVersionApi.VersionItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editDialogVisible.value = true;
  detailLoading.value = true;
  try {
    const detail = await getForgeVersionDetailApi(pluginId.value, row.id);
    editForm.version = detail.version ?? '';
    editForm.channel = detail.channel ?? 'stable';
    editForm.manifestJson = detail.manifestJson ?? '';
    editForm.packageUrl = detail.packageUrl ?? '';
    editForm.packageFileId = detail.packageFileId ?? '';
    editForm.packageSha256 = detail.packageSha256 ?? '';
    editForm.packageSize =
      detail.packageSize == null ? undefined : Number(detail.packageSize);
    editForm.signature = detail.signature ?? '';
    editForm.minAppVersion = detail.minAppVersion ?? '';
    editForm.maxAppVersion = detail.maxAppVersion ?? '';
    editForm.changelog = detail.changelog ?? '';
    editForm.status = detail.status ?? 0;
    editForm.remark = detail.remark ?? '';
  } finally {
    detailLoading.value = false;
  }
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createForgeVersionApi(pluginId.value, {
        version: editForm.version,
        channel: editForm.channel || undefined,
        manifestJson: editForm.manifestJson,
        packageUrl: editForm.packageUrl || undefined,
        packageFileId: editForm.packageFileId === '' ? null : editForm.packageFileId,
        packageSha256: editForm.packageSha256,
        packageSize: editForm.packageSize ?? null,
        signature: editForm.signature || undefined,
        minAppVersion: editForm.minAppVersion || undefined,
        maxAppVersion: editForm.maxAppVersion || undefined,
        changelog: editForm.changelog || undefined,
        status: editForm.status,
        remark: editForm.remark || undefined,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateForgeVersionApi(pluginId.value, editingId.value, {
        version: editForm.version,
        channel: editForm.channel || undefined,
        manifestJson: editForm.manifestJson,
        packageUrl: editForm.packageUrl || undefined,
        packageFileId: editForm.packageFileId === '' ? null : editForm.packageFileId,
        packageSha256: editForm.packageSha256,
        packageSize: editForm.packageSize ?? null,
        signature: editForm.signature || undefined,
        minAppVersion: editForm.minAppVersion || undefined,
        maxAppVersion: editForm.maxAppVersion || undefined,
        changelog: editForm.changelog || undefined,
        remark: editForm.remark || undefined,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

// ------------------------------------------------------------------ 审核
const reviewDialogVisible = ref(false);
const reviewLoading = ref(false);
const reviewingId = ref<number | string | null>(null);
const reviewForm = reactive<{ reviewStatus: number; reviewRemark: string }>({
  reviewStatus: 1,
  reviewRemark: '',
});

function openReview(row: ForgeVersionApi.VersionItem) {
  reviewingId.value = row.id;
  reviewForm.reviewStatus = row.reviewStatus === 2 ? 2 : 1;
  reviewForm.reviewRemark = row.reviewRemark ?? '';
  reviewDialogVisible.value = true;
}

async function submitReview() {
  if (reviewingId.value == null) return;
  reviewLoading.value = true;
  try {
    await reviewForgeVersionApi(pluginId.value, reviewingId.value, {
      reviewStatus: reviewForm.reviewStatus,
      reviewRemark: reviewForm.reviewRemark || undefined,
    });
    ElMessage.success('审核完成');
    reviewDialogVisible.value = false;
    reloadGrid();
  } finally {
    reviewLoading.value = false;
  }
}

// ------------------------------------------------------------------ 状态
const statusDialogVisible = ref(false);
const statusLoading = ref(false);
const statusTargetId = ref<number | string | null>(null);
const targetStatus = ref(1);

function openStatus(row: ForgeVersionApi.VersionItem) {
  statusTargetId.value = row.id;
  targetStatus.value = row.status ?? 0;
  statusDialogVisible.value = true;
}

async function submitStatus() {
  if (statusTargetId.value == null) return;
  statusLoading.value = true;
  try {
    await updateForgeVersionStatusApi(
      pluginId.value,
      statusTargetId.value,
      targetStatus.value,
    );
    ElMessage.success('状态已更新');
    statusDialogVisible.value = false;
    reloadGrid();
  } finally {
    statusLoading.value = false;
  }
}

// ------------------------------------------------------------------ 权限声明
const permDialogVisible = ref(false);
const permLoading = ref(false);
const permVersionId = ref<number | string | null>(null);
const permRows = ref<ForgeVersionApi.PermissionItem[]>([]);

async function openPermissions(row: ForgeVersionApi.VersionItem) {
  permVersionId.value = row.id;
  permDialogVisible.value = true;
  permLoading.value = true;
  try {
    permRows.value = await getForgeVersionPermissionsApi(pluginId.value, row.id);
  } finally {
    permLoading.value = false;
  }
}

function addPermRow() {
  permRows.value.push({
    permissionCode: '',
    permissionName: '',
    description: '',
    riskLevel: 1,
    required: 1,
  });
}

function removePermRow(index: number) {
  permRows.value.splice(index, 1);
}

async function submitPermissions() {
  if (permVersionId.value == null) return;
  for (const row of permRows.value) {
    if (!row.permissionCode?.trim() || !row.permissionName?.trim()) {
      ElMessage.warning('权限编码与名称不能为空');
      return;
    }
  }
  permLoading.value = true;
  try {
    await bindForgeVersionPermissionsApi(
      pluginId.value,
      permVersionId.value,
      permRows.value,
    );
    ElMessage.success('权限声明已保存');
    permDialogVisible.value = false;
  } finally {
    permLoading.value = false;
  }
}

// ------------------------------------------------------------------ 删除
async function handleDelete(row: ForgeVersionApi.VersionItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除版本「${row.version}」？将一并删除其权限声明。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteForgeVersionApi(pluginId.value, row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton @click="router.push('/forge/plugin')">返回插件列表</ElButton>
        <ElButton
          v-access:code="'forge:version:add'"
          type="primary"
          @click="openCreate"
        >
          新增版本
        </ElButton>
      </template>

      <template #review="{ row }">
        <ElTag :type="reviewTagType(row.reviewStatus)" size="small">
          {{ reviewLabel(row.reviewStatus) }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'forge:version:review'"
            link
            type="primary"
            @click="openReview(row)"
          >
            审核
          </ElButton>
          <ElButton
            v-access:code="'forge:version:edit'"
            link
            type="primary"
            @click="openStatus(row)"
          >
            状态
          </ElButton>
          <ElButton
            v-access:code="'forge:version:query'"
            link
            type="primary"
            @click="openPermissions(row)"
          >
            权限
          </ElButton>
          <ElButton
            v-access:code="'forge:version:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'forge:version:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 新增 / 编辑版本 -->
    <ElDialog
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="`${editMode === 'create' ? '新增' : '编辑'}版本${pluginName ? ` · ${pluginName}` : ''}`"
      top="6vh"
      width="720"
    >
      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
      >
        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="版本号" prop="version">
            <ElInput v-model="editForm.version" placeholder="如 1.0.0" />
          </ElFormItem>
          <ElFormItem label="发布通道" prop="channel">
            <ElSelect v-model="editForm.channel" style="width: 100%">
              <ElOption
                v-for="item in channelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </div>

        <ElFormItem label="manifest" prop="manifestJson">
          <ElInput
            v-model="editForm.manifestJson"
            :rows="4"
            placeholder="plugin.json 内容（JSON 字符串）"
            type="textarea"
          />
        </ElFormItem>

        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="安装包URL" prop="packageUrl">
            <ElInput v-model="editForm.packageUrl" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="安装包文件ID" prop="packageFileId">
            <ElInput v-model="editForm.packageFileId" placeholder="sys_file ID，可选" />
          </ElFormItem>
          <ElFormItem label="SHA256" prop="packageSha256">
            <ElInput v-model="editForm.packageSha256" placeholder="安装包摘要" />
          </ElFormItem>
          <ElFormItem label="包大小(字节)" prop="packageSize">
            <ElInputNumber
              v-model="editForm.packageSize"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="签名" prop="signature">
            <ElInput v-model="editForm.signature" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="最低应用版本" prop="minAppVersion">
            <ElInput v-model="editForm.minAppVersion" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="最高应用版本" prop="maxAppVersion">
            <ElInput v-model="editForm.maxAppVersion" placeholder="可选" />
          </ElFormItem>
          <ElFormItem v-if="editMode === 'create'" label="状态" prop="status">
            <ElSelect v-model="editForm.status" style="width: 100%">
              <ElOption
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </div>

        <ElFormItem label="更新日志" prop="changelog">
          <ElInput
            v-model="editForm.changelog"
            :rows="3"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="备注" prop="remark">
          <ElInput
            v-model="editForm.remark"
            :rows="2"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editDialogVisible = false">取消</ElButton>
        <ElButton :loading="editLoading" type="primary" @click="submitEdit">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 审核 -->
    <ElDialog
      v-model="reviewDialogVisible"
      :close-on-click-modal="false"
      title="审核版本"
      width="480"
    >
      <ElForm :model="reviewForm" label-width="90px">
        <ElFormItem label="审核结果">
          <ElSelect v-model="reviewForm.reviewStatus" style="width: 100%">
            <ElOption
              v-for="item in reviewOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="审核备注">
          <ElInput
            v-model="reviewForm.reviewRemark"
            :rows="3"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="reviewDialogVisible = false">取消</ElButton>
        <ElButton :loading="reviewLoading" type="primary" @click="submitReview">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 状态 -->
    <ElDialog
      v-model="statusDialogVisible"
      :close-on-click-modal="false"
      title="变更版本状态"
      width="420"
    >
      <ElForm label-width="90px">
        <ElFormItem label="目标状态">
          <ElSelect v-model="targetStatus" style="width: 100%">
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <div class="text-xs text-gray-400">
        发布为「已发布」要求该版本审核已通过。
      </div>
      <template #footer>
        <ElButton @click="statusDialogVisible = false">取消</ElButton>
        <ElButton :loading="statusLoading" type="primary" @click="submitStatus">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 权限声明 -->
    <ElDialog
      v-model="permDialogVisible"
      :close-on-click-modal="false"
      title="权限声明"
      top="6vh"
      width="860"
    >
      <div v-loading="permLoading">
        <div class="mb-2 flex justify-end">
          <ElButton
            v-access:code="'forge:version:edit'"
            size="small"
            type="primary"
            @click="addPermRow"
          >
            添加权限
          </ElButton>
        </div>
        <div
          v-for="(row, index) in permRows"
          :key="index"
          class="mb-2 grid grid-cols-12 items-center gap-2"
        >
          <ElInput
            v-model="row.permissionCode"
            class="col-span-3"
            placeholder="权限编码"
          />
          <ElInput
            v-model="row.permissionName"
            class="col-span-2"
            placeholder="权限名称"
          />
          <ElInput
            v-model="row.description"
            class="col-span-3"
            placeholder="用途说明"
          />
          <ElSelect v-model="row.riskLevel" class="col-span-2" placeholder="风险">
            <ElOption
              v-for="item in riskOptions"
              :key="item.value"
              :label="`风险:${item.label}`"
              :value="item.value"
            />
          </ElSelect>
          <ElSelect v-model="row.required" class="col-span-1" placeholder="必需">
            <ElOption :value="1" label="必需" />
            <ElOption :value="0" label="可选" />
          </ElSelect>
          <ElButton
            class="col-span-1"
            link
            type="danger"
            @click="removePermRow(index)"
          >
            删除
          </ElButton>
        </div>
        <div v-if="permRows.length === 0" class="py-6 text-center text-gray-400">
          暂无权限声明，点击「添加权限」新增
        </div>
      </div>
      <template #footer>
        <ElButton @click="permDialogVisible = false">取消</ElButton>
        <ElButton
          v-access:code="'forge:version:edit'"
          :loading="permLoading"
          type="primary"
          @click="submitPermissions"
        >
          保存
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
