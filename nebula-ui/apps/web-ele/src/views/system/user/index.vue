<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemUserApi } from '#/api';

import { reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
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
  createSystemUserApi,
  deleteSystemUserApi,
  getSystemUserPageApi,
  resetSystemUserPasswordApi,
  updateSystemUserApi,
  updateSystemUserStatusApi,
} from '#/api';

defineOptions({ name: 'SystemUser' });

const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;
const MOBILE_PATTERN = /^1[3-9]\d{9}$/;

// ===== 表格 + 搜索（usenebulaVxeGrid 走全局适配） =====
const gridOptions: VxeTableGridOptions<SystemUserApi.UserListItem> = {
  columns: [
    { type: 'seq', title: '#', width: 60 },
    { field: 'username', title: '用户名', minWidth: 140 },
    { field: 'nickname', title: '昵称', minWidth: 140 },
    { field: 'mobile', title: '手机号', minWidth: 130 },
    { field: 'email', title: '邮箱', minWidth: 200 },
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
      width: 280,
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
        const params: SystemUserApi.UserPageQuery = {
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          username: formValues?.username || undefined,
          nickname: formValues?.nickname || undefined,
          mobile: formValues?.mobile || undefined,
          email: formValues?.email || undefined,
          status:
            formValues?.status === undefined || formValues?.status === ''
              ? undefined
              : Number(formValues.status),
        };
        return await getSystemUserPageApi(params);
      },
    },
  },
  rowConfig: {
    keyField: 'id',
  },
  toolbarConfig: {
    refresh: { code: 'query' },
    custom: true,
    zoom: true,
    search: true,
  },
};

const [Grid, gridApi] = usenebulaVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        fieldName: 'username',
        label: '用户名',
        componentProps: { placeholder: '模糊匹配', clearable: true },
      },
      {
        component: 'Input',
        fieldName: 'nickname',
        label: '昵称',
        componentProps: { placeholder: '模糊匹配', clearable: true },
      },
      {
        component: 'Input',
        fieldName: 'mobile',
        label: '手机号',
        componentProps: { placeholder: '精确匹配', clearable: true },
      },
      {
        component: 'Input',
        fieldName: 'email',
        label: '邮箱',
        componentProps: { placeholder: '精确匹配', clearable: true },
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          clearable: true,
          placeholder: '全部',
          options: [
            { label: '正常', value: 1 },
            { label: '禁用', value: 0 },
          ],
        },
      },
    ],
    submitOnChange: false,
  },
  gridOptions,
});

function reloadGrid() {
  gridApi.query();
}

// ===== 创建 / 编辑 =====
type EditMode = 'create' | 'edit';
const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const editForm = reactive<{
  username: string;
  password: string;
  nickname: string;
  mobile: string;
  email: string;
  status: number;
  remark: string;
}>({
  username: '',
  password: '',
  nickname: '',
  mobile: '',
  email: '',
  status: 1,
  remark: '',
});

const editRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度 4-32 位', trigger: 'blur' },
    {
      pattern: USERNAME_PATTERN,
      message: '只能字母/数字/下划线，且以字母开头',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' },
  ],
  mobile: [
    {
      validator: (_rule, value, cb) => {
        if (!value) return cb();
        return MOBILE_PATTERN.test(value)
          ? cb()
          : cb(new Error('手机号格式不正确'));
      },
      trigger: 'blur',
    },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
};

function resetEditForm() {
  editForm.username = '';
  editForm.password = '';
  editForm.nickname = '';
  editForm.mobile = '';
  editForm.email = '';
  editForm.status = 1;
  editForm.remark = '';
  editFormRef.value?.clearValidate();
}

function openCreate() {
  editMode.value = 'create';
  editingId.value = null;
  resetEditForm();
  editDialogVisible.value = true;
}

function openEdit(row: SystemUserApi.UserListItem) {
  editMode.value = 'edit';
  editingId.value = row.id;
  resetEditForm();
  editForm.username = row.username;
  editForm.nickname = row.nickname ?? '';
  editForm.mobile = row.mobile ?? '';
  editForm.email = row.email ?? '';
  editForm.status = row.status ?? 1;
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
      await createSystemUserApi({
        username: editForm.username,
        password: editForm.password,
        nickname: editForm.nickname || undefined,
        mobile: editForm.mobile || undefined,
        email: editForm.email || undefined,
        status: editForm.status,
        remark: editForm.remark || undefined,
      });
      ElMessage.success('创建成功');
    } else if (editingId.value != null) {
      await updateSystemUserApi(editingId.value, {
        nickname: editForm.nickname || undefined,
        mobile: editForm.mobile || undefined,
        email: editForm.email || undefined,
        status: editForm.status,
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

// ===== 状态切换 =====
async function toggleStatus(row: SystemUserApi.UserListItem) {
  const next = row.status === 1 ? 0 : 1;
  const action = next === 1 ? '启用' : '禁用';
  try {
    await ElMessageBox.confirm(
      `确认${action}用户「${row.username}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await updateSystemUserStatusApi(row.id, next);
  ElMessage.success(`${action}成功`);
  reloadGrid();
}

// ===== 删除 =====
async function handleDelete(row: SystemUserApi.UserListItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.username}」？删除后不可在列表中恢复。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteSystemUserApi(row.id);
  ElMessage.success('删除成功');
  reloadGrid();
}

// ===== 重置密码 =====
const passwordDialogVisible = ref(false);
const passwordLoading = ref(false);
const passwordTargetUser = ref<SystemUserApi.UserListItem | null>(null);
const passwordFormRef = ref<FormInstance>();
const passwordForm = reactive<{ newPassword: string; confirmPassword: string }>(
  {
    newPassword: '',
    confirmPassword: '',
  },
);

const passwordRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule, value, cb) => {
        if (!value) return cb(new Error('请再次输入新密码'));
        return value === passwordForm.newPassword
          ? cb()
          : cb(new Error('两次密码不一致'));
      },
      trigger: 'blur',
    },
  ],
};

function openResetPassword(row: SystemUserApi.UserListItem) {
  passwordTargetUser.value = row;
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
  passwordFormRef.value?.clearValidate();
  passwordDialogVisible.value = true;
}

async function submitResetPassword() {
  if (!passwordFormRef.value || !passwordTargetUser.value) return;
  const valid = await passwordFormRef.value.validate().catch(() => false);
  if (!valid) return;

  passwordLoading.value = true;
  try {
    await resetSystemUserPasswordApi(
      passwordTargetUser.value.id,
      passwordForm.newPassword,
    );
    ElMessage.success('密码已重置');
    passwordDialogVisible.value = false;
  } finally {
    passwordLoading.value = false;
  }
}
</script>

<template>
  <Page description="系统用户的增删改查与启用 / 禁用、重置密码" title="用户管理">
    <Grid table-title="用户列表">
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新增用户</ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '正常' : '禁用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton
          link
          :type="row.status === 1 ? 'warning' : 'success'"
          @click="toggleStatus(row)"
        >
          {{ row.status === 1 ? '禁用' : '启用' }}
        </ElButton>
        <ElButton link type="primary" @click="openResetPassword(row)">
          重置密码
        </ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">
          删除
        </ElButton>
      </template>
    </Grid>

    <!-- 创建 / 编辑 -->
    <ElDialog
      v-model="editDialogVisible"
      :close-on-click-modal="false"
      :title="editMode === 'create' ? '新增用户' : '编辑用户'"
      width="520"
    >
      <ElForm
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="90px"
      >
        <ElFormItem label="用户名" prop="username">
          <ElInput
            v-model="editForm.username"
            :disabled="editMode === 'edit'"
            placeholder="字母开头，4-32 位"
          />
        </ElFormItem>
        <ElFormItem v-if="editMode === 'create'" label="密码" prop="password">
          <ElInput
            v-model="editForm.password"
            placeholder="6-64 位"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="昵称" prop="nickname">
          <ElInput
            v-model="editForm.nickname"
            placeholder="可选，缺省同用户名"
          />
        </ElFormItem>
        <ElFormItem label="手机号" prop="mobile">
          <ElInput v-model="editForm.mobile" placeholder="可选" />
        </ElFormItem>
        <ElFormItem label="邮箱" prop="email">
          <ElInput v-model="editForm.email" placeholder="可选" />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="editForm.status" style="width: 160px">
            <ElOption :value="1" label="正常" />
            <ElOption :value="0" label="禁用" />
          </ElSelect>
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

    <!-- 重置密码 -->
    <ElDialog
      v-model="passwordDialogVisible"
      :close-on-click-modal="false"
      title="重置密码"
      width="420"
    >
      <ElForm
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <ElFormItem label="目标用户">
          <span>{{ passwordTargetUser?.username }}</span>
        </ElFormItem>
        <ElFormItem label="新密码" prop="newPassword">
          <ElInput
            v-model="passwordForm.newPassword"
            placeholder="6-64 位"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="确认密码" prop="confirmPassword">
          <ElInput
            v-model="passwordForm.confirmPassword"
            placeholder="再次输入"
            show-password
            type="password"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="passwordDialogVisible = false">取消</ElButton>
        <ElButton
          :loading="passwordLoading"
          type="primary"
          @click="submitResetPassword"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
