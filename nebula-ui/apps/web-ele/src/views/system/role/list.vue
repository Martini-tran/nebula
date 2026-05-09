<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@nebula/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSystemRoleApi,
  getSystemRolePageApi,
  type SystemRoleApi,
  updateSystemRoleStatusApi,
} from '#/api/system/role';

const SUPER_ADMIN_CODE = 'SUPER_ADMIN';

import RoleForm from './modules/form.vue';
import MenuAssign from './modules/menu-assign.vue';
import UserAssign from './modules/user-assign.vue';

defineOptions({ name: 'SystemRole' });

// ===== 表格 =====
const gridOptions: VxeTableGridOptions<SystemRoleApi.RoleListItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'roleCode', title: '角色编码', minWidth: 160 },
    { field: 'roleName', title: '角色名称', minWidth: 160 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      slots: { default: 'status' },
    },
    { field: 'remark', title: '备注', minWidth: 160 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 360,
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
        return await getSystemRolePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          roleCode: formValues?.roleCode || undefined,
          roleName: formValues?.roleName || undefined,
          status:
            formValues?.status === undefined || formValues?.status === ''
              ? undefined
              : Number(formValues.status),
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, zoom: true },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });

function reloadGrid() {
  gridApi.query();
}

// ===== 弹窗状态 =====
const formVisible = ref(false);
const formCurrent = ref<null | SystemRoleApi.RoleListItem>(null);

const menuAssignVisible = ref(false);
const menuAssignTarget = ref<null | SystemRoleApi.RoleListItem>(null);

const userAssignVisible = ref(false);
const userAssignTarget = ref<null | SystemRoleApi.RoleListItem>(null);

function openCreate() {
  formCurrent.value = null;
  formVisible.value = true;
}

function openEdit(row: SystemRoleApi.RoleListItem) {
  formCurrent.value = row;
  formVisible.value = true;
}

function openAssignMenus(row: SystemRoleApi.RoleListItem) {
  menuAssignTarget.value = row;
  menuAssignVisible.value = true;
}

function openAssignUsers(row: SystemRoleApi.RoleListItem) {
  userAssignTarget.value = row;
  userAssignVisible.value = true;
}

async function toggleStatus(row: SystemRoleApi.RoleListItem) {
  const next = row.status === 1 ? 0 : 1;
  const action = next === 1 ? '启用' : '禁用';
  try {
    await ElMessageBox.confirm(
      `确认${action}角色「${row.roleName}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await updateSystemRoleStatusApi(row.id, next);
  ElMessage.success(`${action}成功`);
  reloadGrid();
}

async function handleDelete(row: SystemRoleApi.RoleListItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.roleName}」？删除前请确保没有用户仍绑定该角色。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteSystemRoleApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton type="primary" @click="openCreate">新增角色</ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '禁用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
          <ElButton link type="primary" @click="openAssignMenus(row)">
            分配菜单
          </ElButton>
          <ElButton
            v-if="row.roleCode !== SUPER_ADMIN_CODE"
            link
            type="primary"
            @click="openAssignUsers(row)"
          >
            分配用户
          </ElButton>
          <ElButton
            v-if="row.roleCode !== SUPER_ADMIN_CODE"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </ElButton>
          <ElButton
            v-if="row.roleCode !== SUPER_ADMIN_CODE"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <RoleForm
      v-model="formVisible"
      :current="formCurrent"
      @saved="reloadGrid"
    />

    <MenuAssign v-model="menuAssignVisible" :role="menuAssignTarget" />

    <UserAssign v-model="userAssignVisible" :role="userAssignTarget" />
  </Page>
</template>
