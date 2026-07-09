<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BlogTravelApi } from '#/api';

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
  ElTag,
  ElUpload,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createBlogTravelDestinationApi,
  deleteBlogTravelDestinationApi,
  getBlogTravelDestinationTreeApi,
  updateBlogTravelDestinationApi,
  uploadBlogFileApi,
} from '#/api';

defineOptions({ name: 'BlogTravelDestination' });

const typeOptions = [
  { label: '国家', value: 0, tagType: 'primary' as const },
  { label: '省份/州', value: 1, tagType: 'success' as const },
  { label: '城市', value: 2, tagType: 'warning' as const },
  { label: '景点/POI', value: 3, tagType: 'info' as const },
];

const statusOptions = [
  { label: '启用', value: 1, tagType: 'success' as const },
  { label: '禁用', value: 0, tagType: 'info' as const },
];

function getTypeLabel(value?: number) {
  return typeOptions.find((t) => t.value === value)?.label ?? '-';
}

function getTypeTagType(value?: number) {
  return typeOptions.find((t) => t.value === value)?.tagType ?? 'info';
}

function getStatusLabel(value?: number) {
  return statusOptions.find((s) => s.value === value)?.label ?? '-';
}

function getStatusTagType(value?: number) {
  return statusOptions.find((s) => s.value === value)?.tagType ?? 'info';
}

const gridOptions: VxeTableGridOptions<BlogTravelApi.Destination> = {
  columns: [
    {
      align: 'left',
      field: 'name',
      title: '名称',
      treeNode: true,
      minWidth: 220,
    },
    {
      field: 'cover',
      title: '封面',
      width: 90,
      slots: { default: 'cover' },
    },
    { field: 'slug', title: 'Slug', minWidth: 140 },
    {
      field: 'type',
      title: '类型',
      width: 110,
      slots: { default: 'type' },
    },
    { field: 'address', title: '地址', minWidth: 180 },
    {
      field: 'status',
      title: '状态',
      width: 80,
      slots: { default: 'status' },
    },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    {
      field: 'action',
      title: '操作',
      width: 240,
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
  treeConfig: {
    rowField: 'id',
    parentField: 'parentId',
    transform: false,
    expandAll: true,
  },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });
const treeData = ref<BlogTravelApi.Destination[]>([]);

async function reloadGrid() {
  const data = await getBlogTravelDestinationTreeApi();
  treeData.value = data ?? [];
  await gridApi.setGridOptions({ data: treeData.value });
}

onMounted(reloadGrid);

interface FlatNode {
  id: number | string;
  label: string;
  type?: number;
}

function flattenTree(
  nodes: BlogTravelApi.Destination[],
  excludeId?: number | string,
): FlatNode[] {
  const result: FlatNode[] = [];
  function walk(list: BlogTravelApi.Destination[], depth = 0) {
    for (const node of list) {
      if (excludeId != null && String(node.id) === String(excludeId)) continue;
      result.push({
        id: node.id,
        label: `${'--'.repeat(depth)}${depth > 0 ? ' ' : ''}${node.name}`,
        type: node.type,
      });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  }
  walk(nodes);
  return result;
}

// -------------------- 编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<null | number | string>(null);
const editLoading = ref(false);
const coverUploading = ref(false);
const coverExplicitlyRemoved = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  parentId: null | number | string;
  name: string;
  slug: string;
  type: number;
  description: string;
  coverFileId: null | number | string;
  coverPreviewUrl: string;
  longitude: null | number;
  latitude: null | number;
  address: string;
  status: number;
  sortOrder: number;
}>({
  parentId: null,
  name: '',
  slug: '',
  type: 0,
  description: '',
  coverFileId: null,
  coverPreviewUrl: '',
  longitude: null,
  latitude: null,
  address: '',
  status: 1,
  sortOrder: 0,
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '仅允许小写字母、数字和中划线',
      trigger: 'blur',
    },
    { max: 120, message: '最多 120 个字符', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
};

function resetForm() {
  editForm.parentId = null;
  editForm.name = '';
  editForm.slug = '';
  editForm.type = 0;
  editForm.description = '';
  editForm.coverFileId = null;
  editForm.coverPreviewUrl = '';
  editForm.longitude = null;
  editForm.latitude = null;
  editForm.address = '';
  editForm.status = 1;
  editForm.sortOrder = 0;
  coverExplicitlyRemoved.value = false;
  editFormRef.value?.clearValidate();
}

function autoSlug() {
  if (editMode.value === 'edit' || editForm.slug) return;
  const ascii = editForm.name
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  editForm.slug = ascii || `dest-${Date.now().toString(36)}`;
}

function openCreate(parent?: BlogTravelApi.Destination) {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  if (parent) {
    editForm.parentId = parent.id;
    // 子级 type 默认在父级基础上 +1（最高 3）
    editForm.type = Math.min(3, (parent.type ?? 0) + 1);
  }
  editDialogVisible.value = true;
}

function openEdit(row: BlogTravelApi.Destination) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.parentId = row.parentId ?? null;
  editForm.name = row.name;
  editForm.slug = row.slug;
  editForm.type = row.type ?? 0;
  editForm.description = row.description ?? '';
  editForm.coverFileId = row.coverFileId ?? null;
  editForm.coverPreviewUrl = row.coverUrl ?? '';
  editForm.longitude = row.longitude == null ? null : Number(row.longitude);
  editForm.latitude = row.latitude == null ? null : Number(row.latitude);
  editForm.address = row.address ?? '';
  editForm.status = row.status ?? 1;
  editForm.sortOrder = row.sortOrder ?? 0;
  editDialogVisible.value = true;
}

async function handleCoverChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return;
  coverUploading.value = true;
  try {
    const result = await uploadBlogFileApi(uploadFile.raw, 'cover');
    editForm.coverFileId = result.id;
    editForm.coverPreviewUrl = result.url;
    coverExplicitlyRemoved.value = false;
    ElMessage.success('封面上传成功');
  } catch {
    ElMessage.error('封面上传失败，请重试');
  } finally {
    coverUploading.value = false;
  }
}

function removeCover() {
  editForm.coverFileId = null;
  editForm.coverPreviewUrl = '';
  coverExplicitlyRemoved.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createBlogTravelDestinationApi({
        parentId: editForm.parentId ?? null,
        name: editForm.name,
        slug: editForm.slug,
        type: editForm.type,
        description: editForm.description || undefined,
        coverFileId: editForm.coverFileId ?? undefined,
        longitude: editForm.longitude ?? undefined,
        latitude: editForm.latitude ?? undefined,
        address: editForm.address || undefined,
        status: editForm.status,
        sortOrder: editForm.sortOrder,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateBlogTravelDestinationApi(editingId.value, {
        // 顶层 → 传 0 作为提升信号；其余传具体ID
        parentId: editForm.parentId ?? 0,
        name: editForm.name,
        slug: editForm.slug,
        type: editForm.type,
        description: editForm.description || undefined,
        coverFileId: editForm.coverFileId ?? undefined,
        clearCoverFileId: coverExplicitlyRemoved.value ? true : undefined,
        longitude: editForm.longitude ?? undefined,
        latitude: editForm.latitude ?? undefined,
        address: editForm.address || undefined,
        status: editForm.status,
        sortOrder: editForm.sortOrder,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

async function handleDelete(row: BlogTravelApi.Destination) {
  try {
    await ElMessageBox.confirm(
      `确认删除目的地「${row.name}」？存在子节点时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogTravelDestinationApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'blog:travel:add'"
          type="primary"
          @click="openCreate()"
        >
          新增目的地
        </ElButton>
      </template>

      <template #cover="{ row }">
        <img
          v-if="row.coverUrl"
          :src="row.coverUrl"
          alt="cover"
          class="dest-cover-thumb"
        />
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #type="{ row }">
        <ElTag :type="getTypeTagType(row.type)" size="small">
          {{ getTypeLabel(row.type) }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-if="(row.type ?? 0) < 3"
            v-access:code="'blog:travel:add'"
            link
            type="primary"
            @click="openCreate(row)"
          >
            新增下级
          </ElButton>
          <ElButton
            v-access:code="'blog:travel:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'blog:travel:delete'"
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
      :title="editMode === 'create' ? '新增目的地' : '编辑目的地'"
      width="560"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="父级">
          <ElSelect
            v-model="editForm.parentId"
            placeholder="顶层（不选父级）"
            clearable
            style="width: 100%"
          >
            <ElOption
              v-for="item in flattenTree(treeData, editingId ?? undefined)"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="名称" prop="name">
          <ElInput
            v-model="editForm.name"
            placeholder="如：大理古城"
            @blur="autoSlug"
          />
        </ElFormItem>

        <ElFormItem label="Slug" prop="slug">
          <ElInput v-model="editForm.slug" placeholder="如：dali-ancient-city" />
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

        <ElFormItem label="描述">
          <ElInput
            v-model="editForm.description"
            :rows="3"
            maxlength="500"
            placeholder="可选"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>

        <ElFormItem label="封面">
          <div class="cover-wrap">
            <div v-if="editForm.coverPreviewUrl" class="cover-preview">
              <img
                :src="editForm.coverPreviewUrl"
                alt="封面预览"
                class="cover-img"
              />
              <ElButton
                class="cover-remove"
                size="small"
                type="danger"
                link
                @click="removeCover"
              >
                移除
              </ElButton>
            </div>
            <ElUpload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              @change="handleCoverChange"
            >
              <ElButton
                :loading="coverUploading"
                size="small"
                type="primary"
                plain
              >
                {{ editForm.coverPreviewUrl ? '重新上传' : '上传封面' }}
              </ElButton>
            </ElUpload>
            <span class="cover-hint">支持 JPG、PNG、WebP、GIF，最大 10MB</span>
          </div>
        </ElFormItem>

        <ElFormItem label="经度">
          <ElInputNumber
            v-model="editForm.longitude"
            :min="-180"
            :max="180"
            :precision="6"
            controls-position="right"
            placeholder="-180 ~ 180"
            style="width: 100%"
          />
        </ElFormItem>

        <ElFormItem label="纬度">
          <ElInputNumber
            v-model="editForm.latitude"
            :min="-90"
            :max="90"
            :precision="6"
            controls-position="right"
            placeholder="-90 ~ 90"
            style="width: 100%"
          />
        </ElFormItem>

        <ElFormItem label="详细地址">
          <ElInput
            v-model="editForm.address"
            maxlength="200"
            placeholder="可选"
          />
        </ElFormItem>

        <ElFormItem label="状态">
          <ElSelect v-model="editForm.status" style="width: 100%">
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber
            v-model="editForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
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

<style scoped>
.dest-cover-thumb {
  display: block;
  width: 56px;
  height: 36px;
  margin: 0 auto;
  object-fit: cover;
  border-radius: 4px;
}

.cover-wrap {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 8px;
}

.cover-preview {
  position: relative;
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.cover-img {
  display: block;
  width: 100%;
  max-height: 120px;
  object-fit: cover;
}

.cover-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 4px;
  color: #fff !important;
}

.cover-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
