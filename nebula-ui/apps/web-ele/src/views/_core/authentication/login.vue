<script lang="ts" setup>
import type { nebulaFormSchema } from '@nebula/common-ui';

import type { AuthApi } from '#/api';

import { computed, ref } from 'vue';

import { AuthenticationLogin, z } from '@nebula/common-ui';
import { $t } from '@nebula/locales';

import { ElDialog } from 'element-plus';

import { useAuthStore } from '#/store';

import BlockPuzzleCaptcha from './components/block-puzzle-captcha.vue';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

/** 弹窗可见性 */
const captchaDialogVisible = ref(false);
/** 用户名/密码先校验通过后暂存，等验证码通过再调登录 */
const pendingCredentials = ref<{ password: string; username: string } | null>(
  null,
);

const formSchema = computed((): nebulaFormSchema[] => {
  return [
    {
      component: 'nebulaInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'nebulaInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

/** 点击登录按钮：表单校验通过后弹出验证码 */
function handleSubmit(values: Record<string, unknown>) {
  pendingCredentials.value = {
    password: String(values.password ?? ''),
    username: String(values.username ?? ''),
  };
  captchaDialogVisible.value = true;
}

/** 验证码通过：合并凭据 + verifyToken 调登录 */
async function handleCaptchaSuccess(payload: {
  captchaType: AuthApi.CaptchaType;
  verifyToken: string;
}) {
  if (!pendingCredentials.value) return;
  const params: AuthApi.LoginParams = {
    captchaType: payload.captchaType,
    captchaVerifyToken: payload.verifyToken,
    password: pendingCredentials.value.password,
    username: pendingCredentials.value.username,
  };
  captchaDialogVisible.value = false;
  try {
    await authStore.authLogin(params);
  } finally {
    pendingCredentials.value = null;
  }
}

function handleDialogClose() {
  captchaDialogVisible.value = false;
  pendingCredentials.value = null;
}
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    @submit="handleSubmit"
  />

  <ElDialog
    v-model="captchaDialogVisible"
    :before-close="handleDialogClose"
    :close-on-click-modal="false"
    align-center
    title="安全验证"
    width="360"
  >
    <BlockPuzzleCaptcha
      v-if="captchaDialogVisible"
      @success="handleCaptchaSuccess"
    />
  </ElDialog>
</template>