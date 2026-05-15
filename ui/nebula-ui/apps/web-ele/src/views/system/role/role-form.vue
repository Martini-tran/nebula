<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  createSystemRoleApi,
  type SystemRoleApi,
  updateSystemRoleApi,
} from '#/api/system/role';

defineOptions({ name: 'SystemRoleForm' });

const props = defineProps<{
  /** 编辑模式时传入；新增传 null */
  current?: null | SystemRoleApi.RoleListItem;
  modelValue: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [boolean];
  saved: [];
}>();

const ROLE_CODE_PATTERN = /^[\w-]{2,50}$/;

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

const isEdit = computed(() => props.current?.id != null);
const title = computed(() => (isEdit.value ? '编辑角色' : '新增角色'));

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive({
  roleCode: '',
  roleName: '',
  status: 1,
  remark: '',
});

const rules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: ROLE_CODE_PATTERN,
      message: '仅字母 / 数字 / 下划线 / 短横线，长度 2-50',
      trigger: 'blur',
    },
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 100, message: '最长 100 字符', trigger: 'blur' },
  ],
};

function resetForm() {
  form.roleCode = '';
  form.roleName = '';
  form.status = 1;
  form.remark = '';
  formRef.value?.clearValidate();
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    resetForm();
    if (props.current) {
      form.roleCode = props.current.roleCode;
      form.roleName = props.current.roleName;
      form.status = props.current.status ?? 1;
      form.remark = props.current.remark ?? '';
    }
  },
);

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (isEdit.value && props.current) {
      await updateSystemRoleApi(props.current.id, {
        roleName: form.roleName,
        status: form.status,
        remark: form.remark || undefined,
      });
      ElMessage.success('保存成功');
    } else {
      await createSystemRoleApi({
        roleCode: form.roleCode,
        roleName: form.roleName,
        status: form.status,
        remark: form.remark || undefined,
      });
      ElMessage.success('创建成功');
    }
    emit('saved');
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
    :title="title"
    width="520"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="90px"
    >
      <ElFormItem label="角色编码" prop="roleCode">
        <ElInput
          v-model="form.roleCode"
          :disabled="isEdit"
          placeholder="如 admin、user_manager"
        />
      </ElFormItem>
      <ElFormItem label="角色名称" prop="roleName">
        <ElInput v-model="form.roleName" placeholder="展示名称" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElSelect v-model="form.status" style="width: 160px">
          <ElOption :value="1" label="正常" />
          <ElOption :value="0" label="禁用" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="备注" prop="remark">
        <ElInput
          v-model="form.remark"
          :rows="2"
          placeholder="可选"
          type="textarea"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="submitting" type="primary" @click="submit">
        确认
      </ElButton>
    </template>
  </ElDialog>
</template>
