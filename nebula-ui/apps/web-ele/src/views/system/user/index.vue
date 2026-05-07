<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { SystemUserApi } from '#/api';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createSystemUserApi,
  deleteSystemUserApi,
  getSystemUserPageApi,
  resetSystemUserPasswordApi,
  updateSystemUserApi,
  updateSystemUserStatusApi,
} from '#/api';

defineOptions({ name: 'SystemUser' });

// ===== 列表 =====
const filterForm = reactive<{
  username: string;
  nickname: string;
  mobile: string;
  email: string;
  status: number | undefined;
}>({
  username: '',
  nickname: '',
  mobile: '',
  email: '',
  status: undefined,
});

const tableData = ref<SystemUserApi.UserListItem[]>([]);
const tableLoading = ref(false);
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});

async function fetchPage() {
  tableLoading.value = true;
  try {
    const res = await getSystemUserPageApi({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: filterForm.username || undefined,
      nickname: filterForm.nickname || undefined,
      mobile: filterForm.mobile || undefined,
      email: filterForm.email || undefined,
      status: filterForm.status,
    });
    tableData.value = res?.records ?? [];
    pagination.total = res?.total ?? 0;
  } finally {
    tableLoading.value = false;
  }
}

function handleSearch() {
  pagination.pageNum = 1;
  fetchPage();
}

function handleResetFilter() {
  filterForm.username = '';
  filterForm.nickname = '';
  filterForm.mobile = '';
  filterForm.email = '';
  filterForm.status = undefined;
  handleSearch();
}

function handlePageChange(page: number) {
  pagination.pageNum = page;
  fetchPage();
}

function handleSizeChange(size: number) {
  pagination.pageSize = size;
  pagination.pageNum = 1;
  fetchPage();
}

// ===== 创建 / 编辑 =====
type EditMode = 'create' | 'edit';
const editDialogVisible = ref(false);
const editMode = ref<EditMode>('create');
const editingId = ref<number | string | null>(null);
const editLoading = ref(false);
const editFormRef = ref<FormInstance>();

const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;
const MOBILE_PATTERN = /^1[3-9]\d{9}$/;

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
    fetchPage();
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
  fetchPage();
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
  // 删除最后一行回退一页
  if (tableData.value.length === 1 && pagination.pageNum > 1) {
    pagination.pageNum -= 1;
  }
  fetchPage();
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

onMounted(fetchPage);
</script>

<template>
  <Page description="系统用户的增删改查与启用 / 禁用、重置密码" title="用户管理">
    <ElCard class="mb-4" shadow="never">
      <ElForm :inline="true" :model="filterForm" @submit.prevent="handleSearch">
        <ElFormItem label="用户名">
          <ElInput
            v-model="filterForm.username"
            clearable
            placeholder="模糊匹配"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="昵称">
          <ElInput
            v-model="filterForm.nickname"
            clearable
            placeholder="模糊匹配"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="手机号">
          <ElInput
            v-model="filterForm.mobile"
            clearable
            placeholder="精确匹配"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="邮箱">
          <ElInput
            v-model="filterForm.email"
            clearable
            placeholder="精确匹配"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect
            v-model="filterForm.status"
            clearable
            placeholder="全部"
            style="width: 120px"
          >
            <ElOption :value="1" label="正常" />
            <ElOption :value="0" label="禁用" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="handleSearch">查询</ElButton>
          <ElButton @click="handleResetFilter">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard shadow="never">
      <div class="mb-3 flex items-center justify-between">
        <span class="font-medium">用户列表</span>
        <ElButton type="primary" @click="openCreate">新增用户</ElButton>
      </div>

      <ElTable
        v-loading="tableLoading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
      >
        <ElTableColumn label="ID" prop="id" width="200" />
        <ElTableColumn label="用户名" prop="username" min-width="120" />
        <ElTableColumn label="昵称" prop="nickname" min-width="120" />
        <ElTableColumn label="手机号" prop="mobile" min-width="130" />
        <ElTableColumn label="邮箱" prop="email" min-width="180" />
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" prop="createTime" width="180" />
        <ElTableColumn fixed="right" label="操作" width="280">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">
              编辑
            </ElButton>
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
        </ElTableColumn>
      </ElTable>

      <div class="mt-4 flex justify-end">
        <ElPagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </ElCard>

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
