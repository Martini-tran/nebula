<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  SpaceBookmarkApi,
  SpaceFolderApi,
  SpaceTagApi,
} from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElLink,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  batchDeleteSpaceBookmarksApi,
  bindSpaceBookmarkTagsApi,
  createSpaceBookmarkApi,
  createSpaceTagApi,
  deleteSpaceBookmarkApi,
  getSpaceBookmarkDetailApi,
  getSpaceBookmarkPageApi,
  getSpaceFolderTreeApi,
  getSpaceTagListApi,
  moveSpaceBookmarksApi,
  updateSpaceBookmarkApi,
  updateSpaceBookmarkStatusApi,
} from '#/api';

defineOptions({ name: 'SpaceBookmark' });

const ROOT_FOLDER_ID = 0;

// ------------------------------------------------------------------ 枚举
const statusOptions = [
  { label: '正常', value: 0, tagType: 'success' },
  { label: '归档', value: 1, tagType: 'warning' },
  { label: '失效', value: 2, tagType: 'danger' },
] as const;

const sourceOptions = [
  { label: '手动', value: 'manual' },
  { label: 'Chrome', value: 'chrome' },
  { label: '导入', value: 'import' },
] as const;

function getStatusLabel(status?: number) {
  return (
    statusOptions.find((item) => item.value === status)?.label ??
    String(status ?? '-')
  );
}

function getStatusTagType(status?: number) {
  return statusOptions.find((item) => item.value === status)?.tagType ?? 'info';
}

function getSourceLabel(value?: string) {
  return sourceOptions.find((item) => item.value === value)?.label ?? value ?? '-';
}

// ------------------------------------------------------------------ 关联数据：目录、标签
const folderTree = ref<SpaceFolderApi.FolderItem[]>([]);
const folderOptions = ref<Array<{ id: number | string; label: string }>>([]);
const tagOptions = ref<SpaceTagApi.TagItem[]>([]);

function flattenFolderTree(nodes: SpaceFolderApi.FolderItem[]) {
  const result: Array<{ id: number | string; label: string }> = [];
  function walk(list: SpaceFolderApi.FolderItem[], depth = 0) {
    for (const node of list) {
      result.push({
        id: node.id,
        label: `${'　'.repeat(depth)}${node.name}`,
      });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  }
  walk(nodes);
  return result;
}

const folderSelectOptions = computed(() => [
  { id: ROOT_FOLDER_ID, label: '未分类' },
  ...folderOptions.value,
]);

async function loadRelations() {
  const [folders, tags] = await Promise.all([
    getSpaceFolderTreeApi(),
    getSpaceTagListApi(),
  ]);
  folderTree.value = folders ?? [];
  folderOptions.value = flattenFolderTree(folderTree.value);
  tagOptions.value = tags ?? [];
}

onMounted(loadRelations);

// ------------------------------------------------------------------ 表格
const gridOptions: VxeTableGridOptions<SpaceBookmarkApi.BookmarkItem> = {
  columns: [
    { type: 'checkbox', width: 50, fixed: 'left' },
    { type: 'seq', title: '#', width: 60 },
    {
      field: 'title',
      title: '标题',
      minWidth: 220,
      slots: { default: 'title' },
    },
    {
      field: 'url',
      title: 'URL',
      minWidth: 240,
      slots: { default: 'url' },
    },
    {
      field: 'folderName',
      title: '所属目录',
      width: 160,
      slots: { default: 'folder' },
    },
    {
      field: 'tags',
      title: '标签',
      minWidth: 200,
      slots: { default: 'tags' },
    },
    {
      field: 'status',
      title: '状态',
      width: 80,
      slots: { default: 'status' },
    },
    {
      field: 'source',
      title: '来源',
      width: 90,
      slots: { default: 'source' },
    },
    { field: 'visitCount', title: '访问', width: 80, align: 'center' },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
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
  pagerConfig: { pageSize: 20 },
  proxyConfig: {
    autoLoad: true,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }, formValues) => {
        return await getSpaceBookmarkPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          keyword: formValues?.keyword || undefined,
          folderId:
            formValues?.folderId === '' || formValues?.folderId == null
              ? undefined
              : formValues.folderId,
          tagId: formValues?.tagId || undefined,
          status:
            formValues?.status === '' || formValues?.status == null
              ? undefined
              : formValues.status,
          source: formValues?.source || undefined,
          domain: formValues?.domain || undefined,
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
        fieldName: 'folderId',
        label: '目录',
        componentProps: {
          clearable: true,
          filterable: true,
          options: folderSelectOptions,
        },
      },
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
        fieldName: 'source',
        label: '来源',
        componentProps: {
          clearable: true,
          options: sourceOptions,
        },
      },
      { component: 'Input', fieldName: 'domain', label: '域名' },
    ],
  },
});

function reloadGrid() {
  gridApi.query();
}

const folderNameMap = computed(() => {
  const map = new Map<string, string>();
  map.set(String(ROOT_FOLDER_ID), '未分类');
  for (const o of folderOptions.value) {
    map.set(String(o.id), o.label.trim());
  }
  return map;
});

function getFolderName(id?: number | string) {
  if (id == null) return '-';
  return folderNameMap.value.get(String(id)) ?? `#${id}`;
}

// ------------------------------------------------------------------ 新增/编辑
type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const detailLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  title: string;
  url: string;
  folderId: number | string;
  description: string;
  faviconUrl: string;
  source: string;
  sortOrder: number;
  remark: string;
  tagIds: Array<number | string>;
}>({
  title: '',
  url: '',
  folderId: ROOT_FOLDER_ID,
  description: '',
  faviconUrl: '',
  source: 'manual',
  sortOrder: 0,
  remark: '',
  tagIds: [],
});

const editRules: FormRules = {
  title: [
    { required: true, message: '请输入书签标题', trigger: 'blur' },
    { max: 500, message: '最多 500 个字符', trigger: 'blur' },
  ],
  url: [
    { required: true, message: '请输入 URL', trigger: 'blur' },
    {
      pattern: /^https?:\/\//i,
      message: 'URL 应以 http:// 或 https:// 开头',
      trigger: 'blur',
    },
  ],
};

function resetForm() {
  editForm.title = '';
  editForm.url = '';
  editForm.folderId = ROOT_FOLDER_ID;
  editForm.description = '';
  editForm.faviconUrl = '';
  editForm.source = 'manual';
  editForm.sortOrder = 0;
  editForm.remark = '';
  editForm.tagIds = [];
  editFormRef.value?.clearValidate();
}

async function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  editDialogVisible.value = true;
  if (folderOptions.value.length === 0 && tagOptions.value.length === 0) {
    await loadRelations();
  }
}

async function openEdit(row: SpaceBookmarkApi.BookmarkItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editDialogVisible.value = true;
  if (folderOptions.value.length === 0 && tagOptions.value.length === 0) {
    await loadRelations();
  }
  detailLoading.value = true;
  try {
    const detail = await getSpaceBookmarkDetailApi(row.id);
    editForm.title = detail.title;
    editForm.url = detail.url;
    editForm.folderId = detail.folderId ?? ROOT_FOLDER_ID;
    editForm.description = detail.description ?? '';
    editForm.faviconUrl = detail.faviconUrl ?? '';
    editForm.source = detail.source ?? 'manual';
    editForm.sortOrder = detail.sortOrder ?? 0;
    editForm.remark = detail.remark ?? '';
    editForm.tagIds = (detail.tags ?? []).map((t) => t.id);
  } finally {
    detailLoading.value = false;
  }
}

/**
 * 标签选择器开启了 allow-create，新输入的字符串需要先调用创建接口换 ID
 */
async function materializePendingTags() {
  const ids = editForm.tagIds;
  for (let i = 0; i < ids.length; i++) {
    const value = ids[i];
    if (typeof value !== 'string') continue;
    const matched = tagOptions.value.find((t) => t.name === value);
    if (matched) {
      ids[i] = matched.id;
      continue;
    }
    const newId = await createSpaceTagApi({ name: value });
    tagOptions.value = [
      ...tagOptions.value,
      { id: newId, name: value },
    ];
    ids[i] = newId;
  }
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    await materializePendingTags();
    if (editMode.value === 'create') {
      await createSpaceBookmarkApi({
        title: editForm.title,
        url: editForm.url,
        folderId: editForm.folderId,
        description: editForm.description || undefined,
        faviconUrl: editForm.faviconUrl || undefined,
        source: editForm.source || undefined,
        sortOrder: editForm.sortOrder,
        remark: editForm.remark || undefined,
        tagIds: editForm.tagIds,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateSpaceBookmarkApi(editingId.value, {
        title: editForm.title,
        url: editForm.url,
        folderId: editForm.folderId,
        description: editForm.description,
        faviconUrl: editForm.faviconUrl,
        sortOrder: editForm.sortOrder,
        remark: editForm.remark,
        tagIds: editForm.tagIds,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

// ------------------------------------------------------------------ 状态变更
async function changeStatus(row: SpaceBookmarkApi.BookmarkItem, status: number) {
  await updateSpaceBookmarkStatusApi(row.id, status);
  ElMessage.success('状态已更新');
  reloadGrid();
}

// ------------------------------------------------------------------ 标签快速绑定
const bindTagsDialogVisible = ref(false);
const bindTagsTargetId = ref<number | string | null>(null);
const bindTagsLoading = ref(false);
const bindingTagIds = ref<Array<number | string>>([]);

function openBindTags(row: SpaceBookmarkApi.BookmarkItem) {
  bindTagsTargetId.value = row.id;
  bindingTagIds.value = (row.tags ?? []).map((t) => t.id);
  bindTagsDialogVisible.value = true;
}

async function submitBindTags() {
  if (bindTagsTargetId.value == null) return;
  bindTagsLoading.value = true;
  try {
    // 复用 materialize 逻辑，但作用在 bindingTagIds 上
    for (let i = 0; i < bindingTagIds.value.length; i++) {
      const value = bindingTagIds.value[i];
      if (typeof value !== 'string') continue;
      const matched = tagOptions.value.find((t) => t.name === value);
      if (matched) {
        bindingTagIds.value[i] = matched.id;
        continue;
      }
      const newId = await createSpaceTagApi({ name: value });
      tagOptions.value = [
        ...tagOptions.value,
        { id: newId, name: value },
      ];
      bindingTagIds.value[i] = newId;
    }
    await bindSpaceBookmarkTagsApi(
      bindTagsTargetId.value,
      bindingTagIds.value,
    );
    ElMessage.success('标签已更新');
    bindTagsDialogVisible.value = false;
    reloadGrid();
  } finally {
    bindTagsLoading.value = false;
  }
}

// ------------------------------------------------------------------ 批量操作
function getSelectedRows(): SpaceBookmarkApi.BookmarkItem[] {
  return (gridApi.grid?.getCheckboxRecords?.() ??
    []) as SpaceBookmarkApi.BookmarkItem[];
}

function getSelectedIds(): Array<number | string> {
  return getSelectedRows().map((r) => r.id);
}

const moveDialogVisible = ref(false);
const moveLoading = ref(false);
const moveTargetFolderId = ref<number | string>(ROOT_FOLDER_ID);

function openBatchMove() {
  if (getSelectedIds().length === 0) {
    ElMessage.warning('请先选择要移动的书签');
    return;
  }
  moveTargetFolderId.value = ROOT_FOLDER_ID;
  moveDialogVisible.value = true;
}

async function submitBatchMove() {
  const ids = getSelectedIds();
  if (ids.length === 0) return;
  moveLoading.value = true;
  try {
    const moved = await moveSpaceBookmarksApi(ids, moveTargetFolderId.value);
    ElMessage.success(`已移动 ${moved} 条`);
    moveDialogVisible.value = false;
    reloadGrid();
  } finally {
    moveLoading.value = false;
  }
}

async function batchDelete() {
  const ids = getSelectedIds();
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的书签');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除已选 ${ids.length} 条书签？删除后不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  const removed = await batchDeleteSpaceBookmarksApi(ids);
  ElMessage.success(`已删除 ${removed} 条`);
  reloadGrid();
}

// ------------------------------------------------------------------ 删除
async function handleDelete(row: SpaceBookmarkApi.BookmarkItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除书签“${row.title}”？删除后不可恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteSpaceBookmarkApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'space:bookmark:add'"
          type="primary"
          @click="openCreate"
        >
          新增书签
        </ElButton>
        <ElButton
          v-access:code="'space:bookmark:edit'"
          @click="openBatchMove"
        >
          批量移动
        </ElButton>
        <ElButton
          v-access:code="'space:bookmark:delete'"
          type="danger"
          plain
          @click="batchDelete"
        >
          批量删除
        </ElButton>
      </template>

      <template #title="{ row }">
        <div class="space-bookmark-title">
          <img
            v-if="row.faviconUrl"
            :src="row.faviconUrl"
            class="space-bookmark-favicon"
            alt=""
          />
          <span class="space-bookmark-title__text">{{ row.title }}</span>
        </div>
      </template>

      <template #url="{ row }">
        <ElLink :href="row.url" target="_blank" :underline="false" type="primary">
          {{ row.url }}
        </ElLink>
      </template>

      <template #folder="{ row }">
        {{ getFolderName(row.folderId) }}
      </template>

      <template #tags="{ row }">
        <div class="flex flex-wrap gap-1">
          <ElTag
            v-for="tag in row.tags ?? []"
            :key="tag.id"
            :color="tag.color || undefined"
            :style="tag.color ? { color: '#fff', borderColor: tag.color } : undefined"
            size="small"
          >
            {{ tag.name }}
          </ElTag>
          <span v-if="!row.tags?.length">-</span>
        </div>
      </template>

      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row.status)" size="small">
          {{ getStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #source="{ row }">
        {{ getSourceLabel(row.source) }}
      </template>

      <template #action="{ row }">
        <div class="flex flex-wrap items-center justify-center gap-2">
          <ElButton
            v-access:code="'space:bookmark:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'space:bookmark:edit'"
            link
            type="primary"
            @click="openBindTags(row)"
          >
            标签
          </ElButton>
          <ElButton
            v-if="row.status === 0"
            v-access:code="'space:bookmark:edit'"
            link
            type="warning"
            @click="changeStatus(row, 1)"
          >
            归档
          </ElButton>
          <ElButton
            v-else
            v-access:code="'space:bookmark:edit'"
            link
            type="success"
            @click="changeStatus(row, 0)"
          >
            恢复
          </ElButton>
          <ElButton
            v-access:code="'space:bookmark:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 新增/编辑书签 -->
    <ElDialog
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增书签' : '编辑书签'"
      width="640"
    >
      <ElForm
        ref="editFormRef"
        v-loading="detailLoading"
        :model="editForm"
        :rules="editRules"
        label-width="80px"
      >
        <ElFormItem label="标题" prop="title">
          <ElInput v-model="editForm.title" placeholder="请输入书签标题" />
        </ElFormItem>

        <ElFormItem label="URL" prop="url">
          <ElInput v-model="editForm.url" placeholder="https://..." />
        </ElFormItem>

        <ElFormItem label="目录">
          <ElSelect v-model="editForm.folderId" filterable style="width: 100%">
            <ElOption
              v-for="item in folderSelectOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="标签">
          <ElSelect
            v-model="editForm.tagIds"
            allow-create
            collapse-tags
            collapse-tags-tooltip
            default-first-option
            filterable
            multiple
            placeholder="选择已有标签或回车新建"
            style="width: 100%"
          >
            <ElOption
              v-for="item in tagOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="描述">
          <ElInput
            v-model="editForm.description"
            :rows="2"
            maxlength="1000"
            placeholder="可选"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>

        <ElFormItem label="图标 URL">
          <ElInput v-model="editForm.faviconUrl" placeholder="可选，自动获取站点图标" />
        </ElFormItem>

        <ElFormItem v-if="editMode === 'create'" label="来源">
          <ElSelect v-model="editForm.source" style="width: 100%">
            <ElOption
              v-for="item in sourceOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber v-model="editForm.sortOrder" :min="0" :max="9999" />
        </ElFormItem>

        <ElFormItem label="备注">
          <ElInput
            v-model="editForm.remark"
            :rows="2"
            maxlength="500"
            placeholder="可选"
            show-word-limit
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

    <!-- 标签快速绑定 -->
    <ElDialog
      v-model="bindTagsDialogVisible"
      :close-on-click-modal="false"
      title="编辑标签"
      width="480"
    >
      <ElSelect
        v-model="bindingTagIds"
        allow-create
        collapse-tags
        collapse-tags-tooltip
        default-first-option
        filterable
        multiple
        placeholder="选择已有标签或回车新建"
        style="width: 100%"
      >
        <ElOption
          v-for="item in tagOptions"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </ElSelect>

      <template #footer>
        <ElButton @click="bindTagsDialogVisible = false">取消</ElButton>
        <ElButton :loading="bindTagsLoading" type="primary" @click="submitBindTags">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 批量移动 -->
    <ElDialog
      v-model="moveDialogVisible"
      :close-on-click-modal="false"
      title="批量移动"
      width="480"
    >
      <ElForm label-width="100px">
        <ElFormItem label="目标目录">
          <ElSelect v-model="moveTargetFolderId" filterable style="width: 100%">
            <ElOption
              v-for="item in folderSelectOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="moveDialogVisible = false">取消</ElButton>
        <ElButton :loading="moveLoading" type="primary" @click="submitBatchMove">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.space-bookmark-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.space-bookmark-favicon {
  width: 18px;
  height: 18px;
  border-radius: 2px;
  flex: none;
  object-fit: contain;
}

.space-bookmark-title__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
