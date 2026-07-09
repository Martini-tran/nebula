<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ForgeCategoryApi, ForgePluginApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

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
  createForgePluginApi,
  deleteForgePluginApi,
  getAllForgeCategoriesApi,
  getForgePluginDetailApi,
  getForgePluginPageApi,
  updateForgePluginApi,
  updateForgePluginFeaturedApi,
} from '#/api';

defineOptions({ name: 'ForgePlugin' });

const router = useRouter();

const typeOptions = [
  { label: '内联（inline）', value: 'inline' },
  { label: '视图（view）', value: 'view' },
];
const pricingOptions = [
  { label: '免费', value: 1, tagType: 'success' as const },
  { label: '付费', value: 2, tagType: 'warning' as const },
  { label: '订阅', value: 3, tagType: 'primary' as const },
  { label: '外部购买', value: 4, tagType: 'info' as const },
];
const statusOptions = [
  { label: '草稿', value: 0, tagType: 'info' as const },
  { label: '上架', value: 1, tagType: 'success' as const },
  { label: '下架', value: 2, tagType: 'warning' as const },
  { label: '封禁', value: 3, tagType: 'danger' as const },
];

function pricingLabel(v?: number) {
  return pricingOptions.find((o) => o.value === v)?.label ?? '-';
}
function pricingTagType(v?: number) {
  return pricingOptions.find((o) => o.value === v)?.tagType ?? 'info';
}
function statusLabel(v?: number) {
  return statusOptions.find((o) => o.value === v)?.label ?? '-';
}
function statusTagType(v?: number) {
  return statusOptions.find((o) => o.value === v)?.tagType ?? 'info';
}

const categoryOptions = ref<ForgeCategoryApi.CategoryItem[]>([]);
async function loadCategories() {
  categoryOptions.value = await getAllForgeCategoriesApi();
}
onMounted(loadCategories);

const gridOptions: VxeTableGridOptions<ForgePluginApi.PluginItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'name', title: '插件名称', minWidth: 180 },
    { field: 'pluginKey', title: '插件标识', minWidth: 180 },
    { field: 'type', title: '类型', width: 90 },
    {
      field: 'pricingType',
      title: '定价',
      width: 100,
      slots: { default: 'pricing' },
    },
    {
      field: 'categoryNames',
      title: '分类',
      minWidth: 160,
      slots: { default: 'categories' },
    },
    { field: 'latestVersion', title: '最新版本', width: 110 },
    { field: 'downloadCount', title: '下载', width: 90, align: 'center' },
    { field: 'ratingScore', title: '评分', width: 90, align: 'center' },
    {
      field: 'isFeatured',
      title: '推荐',
      width: 80,
      slots: { default: 'featured' },
    },
    { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
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
        return await getForgePluginPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          status: formValues?.status ?? undefined,
          type: formValues?.type || undefined,
          pricingType: formValues?.pricingType ?? undefined,
          isFeatured: formValues?.isFeatured ?? undefined,
          categoryId: formValues?.categoryId || undefined,
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
      { component: 'Input', fieldName: 'keyword', label: '关键词' },
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
        fieldName: 'type',
        label: '类型',
        componentProps: { clearable: true, options: typeOptions },
      },
      {
        component: 'Select',
        fieldName: 'pricingType',
        label: '定价',
        componentProps: {
          clearable: true,
          options: pricingOptions.map(({ label, value }) => ({ label, value })),
        },
      },
      {
        component: 'Select',
        fieldName: 'isFeatured',
        label: '推荐',
        componentProps: {
          clearable: true,
          options: [
            { label: '是', value: 1 },
            { label: '否', value: 0 },
          ],
        },
      },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

// ------------------------------------------------------------------ 推荐切换
async function toggleFeatured(row: ForgePluginApi.PluginItem) {
  const next = row.isFeatured === 1 ? 0 : 1;
  try {
    await updateForgePluginFeaturedApi(row.id, next);
    row.isFeatured = next;
    ElMessage.success('已更新推荐状态');
  } catch {
    // 保持原值
  }
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
    pluginKey: '',
    name: '',
    type: 'inline',
    summary: '',
    description: '',
    keywords: '',
    iconFileId: '' as number | string,
    coverFileId: '' as number | string,
    authorUserId: '' as number | string,
    authorName: '',
    homepageUrl: '',
    repoUrl: '',
    license: '',
    pricingType: 1,
    price: undefined as number | undefined,
    originalPrice: undefined as number | undefined,
    currency: 'CNY',
    priceText: '',
    purchaseUrl: '',
    isFeatured: 0,
    sortOrder: 0,
    status: 0,
    remark: '',
    categoryIds: [] as Array<number | string>,
  };
}

const editForm = reactive(defaultForm());

const editRules: FormRules = {
  pluginKey: [
    { required: true, message: '请输入插件标识', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入插件名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
};

function resetForm() {
  Object.assign(editForm, defaultForm());
  editFormRef.value?.clearValidate();
}

async function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
  if (categoryOptions.value.length === 0) await loadCategories();
}

async function openEdit(row: ForgePluginApi.PluginItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editDialogVisible.value = true;
  if (categoryOptions.value.length === 0) await loadCategories();

  detailLoading.value = true;
  try {
    const detail = await getForgePluginDetailApi(row.id);
    editForm.pluginKey = detail.pluginKey ?? '';
    editForm.name = detail.name ?? '';
    editForm.type = detail.type ?? 'inline';
    editForm.summary = detail.summary ?? '';
    editForm.description = detail.description ?? '';
    editForm.keywords = detail.keywords ?? '';
    editForm.iconFileId = detail.iconFileId ?? '';
    editForm.coverFileId = detail.coverFileId ?? '';
    editForm.authorUserId = detail.authorUserId ?? '';
    editForm.authorName = detail.authorName ?? '';
    editForm.homepageUrl = detail.homepageUrl ?? '';
    editForm.repoUrl = detail.repoUrl ?? '';
    editForm.license = detail.license ?? '';
    editForm.pricingType = detail.pricingType ?? 1;
    editForm.price = detail.price == null ? undefined : Number(detail.price);
    editForm.originalPrice =
      detail.originalPrice == null ? undefined : Number(detail.originalPrice);
    editForm.currency = detail.currency ?? 'CNY';
    editForm.priceText = detail.priceText ?? '';
    editForm.purchaseUrl = detail.purchaseUrl ?? '';
    editForm.isFeatured = detail.isFeatured ?? 0;
    editForm.sortOrder = detail.sortOrder ?? 0;
    editForm.status = detail.status ?? 0;
    editForm.remark = detail.remark ?? '';
    editForm.categoryIds = detail.categoryIds ?? [];
  } finally {
    detailLoading.value = false;
  }
}

function buildPayload(): ForgePluginApi.PluginCreateParams {
  return {
    pluginKey: editForm.pluginKey,
    name: editForm.name,
    type: editForm.type || undefined,
    summary: editForm.summary || undefined,
    description: editForm.description || undefined,
    keywords: editForm.keywords || undefined,
    iconFileId: editForm.iconFileId === '' ? null : editForm.iconFileId,
    coverFileId: editForm.coverFileId === '' ? null : editForm.coverFileId,
    authorUserId: editForm.authorUserId === '' ? null : editForm.authorUserId,
    authorName: editForm.authorName || undefined,
    homepageUrl: editForm.homepageUrl || undefined,
    repoUrl: editForm.repoUrl || undefined,
    license: editForm.license || undefined,
    pricingType: editForm.pricingType,
    price: editForm.price ?? null,
    originalPrice: editForm.originalPrice ?? null,
    currency: editForm.currency || undefined,
    priceText: editForm.priceText || undefined,
    purchaseUrl: editForm.purchaseUrl || undefined,
    isFeatured: editForm.isFeatured,
    sortOrder: editForm.sortOrder,
    status: editForm.status,
    remark: editForm.remark || undefined,
    categoryIds: editForm.categoryIds,
  };
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    const payload = buildPayload();
    if (editMode.value === 'create') {
      await createForgePluginApi(payload);
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateForgePluginApi(editingId.value, payload);
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

function goVersions(row: ForgePluginApi.PluginItem) {
  router.push(`/forge/plugin/${row.id}/versions`);
}

async function handleDelete(row: ForgePluginApi.PluginItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除插件「${row.name}」？存在版本时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteForgePluginApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'forge:plugin:add'"
          type="primary"
          @click="openCreate"
        >
          新增插件
        </ElButton>
      </template>

      <template #pricing="{ row }">
        <ElTag :type="pricingTagType(row.pricingType)" size="small">
          {{ pricingLabel(row.pricingType) }}
        </ElTag>
      </template>

      <template #categories="{ row }">
        <div class="flex flex-wrap justify-center gap-1">
          <ElTag
            v-for="name in row.categoryNames ?? []"
            :key="name"
            size="small"
          >
            {{ name }}
          </ElTag>
          <span v-if="!row.categoryNames?.length">-</span>
        </div>
      </template>

      <template #featured="{ row }">
        <ElSwitch
          :model-value="row.isFeatured === 1"
          v-access:code="'forge:plugin:edit'"
          @click="toggleFeatured(row)"
        />
      </template>

      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'forge:version:list'"
            link
            type="primary"
            @click="goVersions(row)"
          >
            版本
          </ElButton>
          <ElButton
            v-access:code="'forge:plugin:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'forge:plugin:delete'"
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
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增插件' : '编辑插件'"
      top="6vh"
      width="760"
    >
      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="插件标识" prop="pluginKey">
            <ElInput v-model="editForm.pluginKey" placeholder="plugin.json 中的 id" />
          </ElFormItem>
          <ElFormItem label="插件名称" prop="name">
            <ElInput v-model="editForm.name" placeholder="请输入插件名称" />
          </ElFormItem>
          <ElFormItem label="类型" prop="type">
            <ElSelect v-model="editForm.type" style="width: 100%">
              <ElOption
                v-for="item in typeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="分类" prop="categoryIds">
            <ElSelect
              v-model="editForm.categoryIds"
              multiple
              clearable
              collapse-tags
              collapse-tags-tooltip
              filterable
              placeholder="选择分类"
              style="width: 100%"
            >
              <ElOption
                v-for="item in categoryOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
        </div>

        <ElFormItem label="简介" prop="summary">
          <ElInput v-model="editForm.summary" placeholder="一句话简介" />
        </ElFormItem>
        <ElFormItem label="详情" prop="description">
          <ElInput
            v-model="editForm.description"
            :rows="3"
            placeholder="Markdown 或 HTML"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="关键词" prop="keywords">
          <ElInput v-model="editForm.keywords" placeholder="多个用逗号分隔" />
        </ElFormItem>

        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="作者名" prop="authorName">
            <ElInput v-model="editForm.authorName" placeholder="作者展示名" />
          </ElFormItem>
          <ElFormItem label="作者用户ID" prop="authorUserId">
            <ElInput v-model="editForm.authorUserId" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="主页地址" prop="homepageUrl">
            <ElInput v-model="editForm.homepageUrl" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="仓库地址" prop="repoUrl">
            <ElInput v-model="editForm.repoUrl" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="许可证" prop="license">
            <ElInput v-model="editForm.license" placeholder="如 MIT" />
          </ElFormItem>
          <ElFormItem label="图标文件ID" prop="iconFileId">
            <ElInput v-model="editForm.iconFileId" placeholder="sys_file ID" />
          </ElFormItem>
          <ElFormItem label="封面文件ID" prop="coverFileId">
            <ElInput v-model="editForm.coverFileId" placeholder="sys_file ID" />
          </ElFormItem>
        </div>

        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="定价类型" prop="pricingType">
            <ElSelect v-model="editForm.pricingType" style="width: 100%">
              <ElOption
                v-for="item in pricingOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="币种" prop="currency">
            <ElInput v-model="editForm.currency" placeholder="CNY" />
          </ElFormItem>
          <ElFormItem label="价格" prop="price">
            <ElInputNumber
              v-model="editForm.price"
              :min="0"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="原价" prop="originalPrice">
            <ElInputNumber
              v-model="editForm.originalPrice"
              :min="0"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="价格文案" prop="priceText">
            <ElInput v-model="editForm.priceText" placeholder="如 ¥9/月" />
          </ElFormItem>
          <ElFormItem label="购买地址" prop="purchaseUrl">
            <ElInput v-model="editForm.purchaseUrl" placeholder="外部购买地址" />
          </ElFormItem>
        </div>

        <div class="grid grid-cols-2 gap-x-4">
          <ElFormItem label="状态" prop="status">
            <ElSelect v-model="editForm.status" style="width: 100%">
              <ElOption
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="排序" prop="sortOrder">
            <ElInputNumber
              v-model="editForm.sortOrder"
              :min="0"
              :max="9999"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="推荐" prop="isFeatured">
            <ElSwitch
              v-model="editForm.isFeatured"
              :active-value="1"
              :inactive-value="0"
            />
          </ElFormItem>
        </div>

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
  </Page>
</template>
