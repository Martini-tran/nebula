<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SpaceFolderApi } from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';

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
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createSpaceFolderApi,
  deleteSpaceFolderApi,
  getSpaceFolderTreeApi,
  moveSpaceFolderApi,
  updateSpaceFolderApi,
} from '#/api';

defineOptions({ name: 'SpaceFolder' });

/** 后端约定：parentId === 0 表示根目录 */
const ROOT_PARENT_ID = 0;

const gridOptions: VxeTableGridOptions<SpaceFolderApi.FolderItem> = {
  columns: [
    {
      align: 'left',
      field: 'name',
      title: '目录名称',
      treeNode: true,
      minWidth: 240,
    },
    { field: 'level', title: '层级', width: 80, align: 'center' },
    { field: 'sortOrder', title: '排序', width: 80, align: 'center' },
    { field: 'source', title: '来源', width: 100, slots: { default: 'source' } },
    { field: 'remark', title: '备注', minWidth: 200 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 280,
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
const treeData = ref<SpaceFolderApi.FolderItem[]>([]);

async function reloadGrid() {
  treeData.value = await getSpaceFolderTreeApi();
  await gridApi.setGridOptions({ data: treeData.value });
}

onMounted(reloadGrid);

/**
 * 把树拍平为下拉选项；编辑/移动时排除节点自身和其后代，避免环路
 */
function flattenTree(
  nodes: SpaceFolderApi.FolderItem[],
  excludeId?: number | string,
): Array<{ id: number | string; label: string }> {
  const result: Array<{ id: number | string; label: string }> = [];

  function walk(list: SpaceFolderApi.FolderItem[], depth = 0) {
    for (const node of list) {
      if (excludeId != null && String(node.id) === String(excludeId)) {
        continue;
      }
      result.push({
        id: node.id,
        label: `${'　'.repeat(depth)}${node.name}`,
      });
      if (node.children?.length) {
        walk(node.children, depth + 1);
      }
    }
  }

  walk(nodes);
  return result;
}

const sourceLabel: Record<string, string> = {
  manual: '手动',
  chrome: 'Chrome',
  import: '导入',
};

function getSourceLabel(value?: string) {
  if (!value) return '-';
  return sourceLabel[value] ?? value;
}

// ==================================================================== 编辑
type EditMode = 'create' | 'edit';

const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  name: string;
  parentId: number | string;
  sortOrder: number;
  remark: string;
}>({
  name: '',
  parentId: ROOT_PARENT_ID,
  sortOrder: 0,
  remark: '',
});

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入目录名称', trigger: 'blur' },
    { max: 100, message: '最多 100 个字符', trigger: 'blur' },
  ],
};

const parentOptions = computed(() => {
  const baseList = flattenTree(treeData.value, editingId.value ?? undefined);
  return [{ id: ROOT_PARENT_ID, label: '根目录' }, ...baseList];
});

function resetForm() {
  editForm.name = '';
  editForm.parentId = ROOT_PARENT_ID;
  editForm.sortOrder = 0;
  editForm.remark = '';
  editFormRef.value?.clearValidate();
}

function openCreate(parent?: SpaceFolderApi.FolderItem) {
  editMode.value = 'create';
  editingId.value = null;
  resetForm();
  if (parent) {
    editForm.parentId = parent.id;
  }
  editDialogVisible.value = true;
}

function openEdit(row: SpaceFolderApi.FolderItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetForm();
  editForm.name = row.name;
  editForm.parentId = row.parentId ?? ROOT_PARENT_ID;
  editForm.sortOrder = row.sortOrder ?? 0;
  editForm.remark = row.remark ?? '';
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editFormRef.value) return;
  const valid = await editFormRef.value.validate().catch(() => false);
  if (!valid) return;

  editLoading.value = true;
  try {
    if (editMode.value === 'create') {
      await createSpaceFolderApi({
        name: editForm.name,
        parentId: editForm.parentId,
        sortOrder: editForm.sortOrder,
        remark: editForm.remark || undefined,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      // 创建模式才能改 parent；编辑只走 update（不改父）；要改父请用"移动"
      await updateSpaceFolderApi(editingId.value, {
        name: editForm.name,
        sortOrder: editForm.sortOrder,
        remark: editForm.remark,
      });
      ElMessage.success('保存成功');
    }
    editDialogVisible.value = false;
    await reloadGrid();
  } finally {
    editLoading.value = false;
  }
}

// ==================================================================== 移动
const moveDialogVisible = ref(false);
const movingId = ref<number | string | null>(null);
const moveLoading = ref(false);
const moveForm = reactive<{
  targetParentId: number | string;
  sortOrder: number | null;
}>({
  targetParentId: ROOT_PARENT_ID,
  sortOrder: null,
});

const moveParentOptions = computed(() => {
  const baseList = flattenTree(treeData.value, movingId.value ?? undefined);
  return [{ id: ROOT_PARENT_ID, label: '根目录' }, ...baseList];
});

function openMove(row: SpaceFolderApi.FolderItem) {
  movingId.value = row.id;
  moveForm.targetParentId = row.parentId ?? ROOT_PARENT_ID;
  moveForm.sortOrder = row.sortOrder ?? 0;
  moveDialogVisible.value = true;
}

async function submitMove() {
  if (movingId.value == null) return;
  moveLoading.value = true;
  try {
    await moveSpaceFolderApi(movingId.value, {
      targetParentId: moveForm.targetParentId,
      sortOrder: moveForm.sortOrder ?? undefined,
    });
    ElMessage.success('移动成功');
    moveDialogVisible.value = false;
    await reloadGrid();
  } finally {
    moveLoading.value = false;
  }
}

// ==================================================================== 删除
async function handleDelete(row: SpaceFolderApi.FolderItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除目录“${row.name}”？目录下存在子目录或书签时不允许删除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteSpaceFolderApi(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'space:folder:add'"
          type="primary"
          @click="openCreate()"
        >
          新增目录
        </ElButton>
      </template>

      <template #source="{ row }">
        {{ getSourceLabel(row.source) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton
            v-access:code="'space:folder:add'"
            link
            type="primary"
            @click="openCreate(row)"
          >
            新增子目录
          </ElButton>
          <ElButton
            v-access:code="'space:folder:edit'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </ElButton>
          <ElButton
            v-access:code="'space:folder:edit'"
            link
            type="primary"
            @click="openMove(row)"
          >
            移动
          </ElButton>
          <ElButton
            v-access:code="'space:folder:delete'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <!-- 新建/编辑 -->
    <ElDialog
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增目录' : '编辑目录'"
      width="520"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="80px"
      >
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="editForm.name" placeholder="请输入目录名称" />
        </ElFormItem>

        <ElFormItem v-if="editMode === 'create'" label="父目录">
          <ElSelect v-model="editForm.parentId" filterable style="width: 100%">
            <ElOption
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber v-model="editForm.sortOrder" :min="0" :max="9999" />
        </ElFormItem>

        <ElFormItem label="备注">
          <ElInput
            v-model="editForm.remark"
            :rows="3"
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

    <!-- 移动 -->
    <ElDialog
      v-model="moveDialogVisible"
      :close-on-click-modal="false"
      title="移动目录"
      width="480"
    >
      <ElForm :model="moveForm" label-width="100px">
        <ElFormItem label="目标父目录">
          <ElSelect v-model="moveForm.targetParentId" filterable style="width: 100%">
            <ElOption
              v-for="item in moveParentOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="排序">
          <ElInputNumber v-model="moveForm.sortOrder" :min="0" :max="9999" />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="moveDialogVisible = false">取消</ElButton>
        <ElButton :loading="moveLoading" type="primary" @click="submitMove">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
