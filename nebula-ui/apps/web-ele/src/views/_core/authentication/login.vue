<script lang="ts" setup>
import type { nebulaFormSchema } from '@nebula/common-ui';

import type { AuthApi } from '#/api';

import { computed, markRaw, ref } from 'vue';

import { AuthenticationLogin, z } from '@nebula/common-ui';
import { $t } from '@nebula/locales';

import { useAuthStore } from '#/store';

import BlockPuzzleCaptcha from './components/block-puzzle-captcha.vue';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

/** 验证码组件回填的一次性 token；登录成功后由后端消费 */
const captchaPayload = ref<{
  captchaType: AuthApi.CaptchaType;
  verifyToken: string;
} | null>(null);

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
    {
      component: markRaw(BlockPuzzleCaptcha),
      componentProps: {
        onSuccess: (
          payload: { captchaType: AuthApi.CaptchaType; verifyToken: string },
        ) => {
          captchaPayload.value = payload;
        },
      },
      fieldName: 'captcha',
      rules: z.boolean().refine((value) => value, {
        message: $t('authentication.verifyRequiredTip'),
      }),
    },
  ];
});

async function handleSubmit(values: Record<string, unknown>) {
  if (!captchaPayload.value) return;
  const params: AuthApi.LoginParams = {
    captchaType: captchaPayload.value.captchaType,
    captchaVerifyToken: captchaPayload.value.verifyToken,
    password: String(values.password ?? ''),
    username: String(values.username ?? ''),
  };
  await authStore.authLogin(params);
}
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    @submit="handleSubmit"
  />
</template>