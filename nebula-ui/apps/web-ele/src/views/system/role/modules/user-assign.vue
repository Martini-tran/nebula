<script lang="ts" setup>
import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getSystemUserPageApi,
  type SystemUserApi,
} from '#/api';
import {
  assignSystemRoleUsersApi,
  getSystemRoleUserIdsApi,
  type SystemRoleApi,
} from '#/api/system/role';

defineOptions({ name: 'SystemRoleUserAssign' });

const props = defineProps<{
  modelValue: boolean;
  role?: null | SystemRoleApi.RoleListItem;
}>();

const emit = defineEmits<{
  'update:modelValue': [boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

// 列表数据
const tableRef = ref<InstanceType<typeof ElTable>>();
const rows = ref<SystemUserApi.UserListItem[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const loading = ref(false);

// 检索
const searchUsername = ref('');
const searchNickname = ref('');
const searchStatus = ref<'' | number>('');

/**
 * 跨分页保留勾选 ID。Element Plus 的 ElTable 本身只针对当前页，
 * 翻页 / 检索时通过 selectedIds 重新还原勾选态。
 */
const selectedIds = ref<Set<number | string>>(new Set());
const submitting = ref(false);

async function load() {
  loading.value = true;
  try {
    const res = await getSystemUserPageApi({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      username: searchUsername.value || undefined,
      nickname: searchNickname.value || undefined,
      status:
        searchStatus.value === '' ? undefined : Number(searchStatus.value),
    });
    rows.value = res.records;
    total.value = res.total;
    // 翻页后还原勾选
    syncSelection();
  } finally {
    loading.value = false;
  }
}

async function syncSelection() {
  await nextTick();
  const tbl = tableRef.value;
  if (!tbl) return;
  rows.value.forEach((row) => {
    tbl.toggleRowSelection(row, selectedIds.value.has(row.id));
  });
}

function onSelectionChange(selection: SystemUserApi.UserListItem[]) {
  // 当前页：补上选中 / 去掉未选中
  const currentPageIds = new Set(rows.value.map((r) => r.id));
  const selectedNow = new Set(selection.map((r) => r.id));
  for (const id of currentPageIds) {
    if (selectedNow.has(id)) selectedIds.value.add(id);
    else selectedIds.value.delete(id);
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  load();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
  load();
}

function search() {
  currentPage.value = 1;
  load();
}

function resetSearch() {
  searchUsername.value = '';
  searchNickname.value = '';
  searchStatus.value = '';
  currentPage.value = 1;
  load();
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || !props.role) return;
    selectedIds.value = new Set();
    searchUsername.value = '';
    searchNickname.value = '';
    searchStatus.value = '';
    currentPage.value = 1;
    const ids = (await getSystemRoleUserIdsApi(props.role.id)) ?? [];
    selectedIds.value = new Set(ids);
    await load();
  },
);

async function submit() {
  if (!props.role) return;
  submitting.value = true;
  try {
    await assignSystemRoleUsersApi(
      props.role.id,
      Array.from(selectedIds.value),
    );
    ElMessage.success('用户分配已保存');
    visible.value = false;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :close-on-click-modal="false"
    :title="`分配用户：${role?.roleName ?? ''}`"
    width="780"
    top="6vh"
  >
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <ElInput
        v-model="searchUsername"
        clearable
        placeholder="用户名"
        style="width: 160px"
        @keyup.enter="search"
      />
      <ElInput
        v-model="searchNickname"
        clearable
        placeholder="昵称"
        style="width: 160px"
        @keyup.enter="search"
      />
      <ElSelect
        v-model="searchStatus"
        clearable
        placeholder="状态"
        style="width: 120px"
      >
        <ElOption :value="1" label="正常" />
        <ElOption :value="0" label="禁用" />
      </ElSelect>
      <ElButton type="primary" @click="search">查询</ElButton>
      <ElButton @click="resetSearch">重置</ElButton>
      <span class="ml-auto text-sm text-gray-500">
        已选 {{ selectedIds.size }} 人
      </span>
    </div>

    <ElTable
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      :max-height="420"
      border
      row-key="id"
      stripe
      @selection-change="onSelectionChange"
    >
      <ElTableColumn :reserve-selection="true" type="selection" width="48" />
      <ElTableColumn label="用户名" min-width="120" prop="username" />
      <ElTableColumn label="昵称" min-width="120" prop="nickname" />
      <ElTableColumn label="手机号" min-width="120" prop="mobile" />
      <ElTableColumn label="邮箱" min-width="180" prop="email" />
      <ElTableColumn label="状态" prop="status" width="80">
        <template #default="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </ElTag>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="mt-3 flex justify-end">
      <ElPagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        background
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="submitting" type="primary" @click="submit">
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>
