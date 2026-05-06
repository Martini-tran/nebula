<script lang="ts" setup>
import type { nebulaFormSchema } from '@nebula/common-ui';

import type { AuthApi } from '#/api';

import { computed, h, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationRegister, z } from '@nebula/common-ui';
import { LOGIN_PATH } from '@nebula/constants';
import { $t } from '@nebula/locales';

import { ElDialog, ElMessage } from 'element-plus';

import { registerApi } from '#/api';

import BlockPuzzleCaptcha from './components/block-puzzle-captcha.vue';

defineOptions({ name: 'Register' });

const router = useRouter();
const loading = ref(false);

const captchaDialogVisible = ref(false);
const pendingForm = ref<Omit<
  AuthApi.RegisterParams,
  'captchaType' | 'captchaVerifyToken'
> | null>(null);

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
        passwordStrength: true,
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      renderComponentContent() {
        return {
          strengthText: () => $t('authentication.passwordStrength'),
        };
      },
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
    {
      component: 'nebulaInputPassword',
      componentProps: {
        placeholder: $t('authentication.confirmPassword'),
      },
      dependencies: {
        rules(values) {
          const { password } = values;
          return z
            .string({ required_error: $t('authentication.passwordTip') })
            .min(1, { message: $t('authentication.passwordTip') })
            .refine((value) => value === password, {
              message: $t('authentication.confirmPasswordTip'),
            });
        },
        triggerFields: ['password'],
      },
      fieldName: 'confirmPassword',
      label: $t('authentication.confirmPassword'),
    },
    {
      component: 'nebulaInput',
      componentProps: {
        placeholder: '昵称（可选）',
      },
      fieldName: 'nickname',
      label: '昵称',
      rules: z.string().optional(),
    },
    {
      component: 'nebulaInput',
      componentProps: {
        placeholder: '手机号（可选）',
      },
      fieldName: 'mobile',
      label: '手机号',
      rules: z.string().optional(),
    },
    {
      component: 'nebulaInput',
      componentProps: {
        placeholder: '邮箱（可选）',
      },
      fieldName: 'email',
      label: '邮箱',
      rules: z
        .string()
        .email({ message: '邮箱格式不正确' })
        .optional()
        .or(z.literal('')),
    },
    {
      component: 'nebulaCheckbox',
      fieldName: 'agreePolicy',
      renderComponentContent: () => ({
        default: () =>
          h('span', [
            $t('authentication.agree'),
            h(
              'a',
              {
                class: 'nebula-link ml-1 ',
                href: '',
              },
              `${$t('authentication.privacyPolicy')} & ${$t('authentication.terms')}`,
            ),
          ]),
      }),
      rules: z.boolean().refine((value) => !!value, {
        message: $t('authentication.agreeTip'),
      }),
    },
  ];
});

function handleSubmit(values: Record<string, unknown>) {
  pendingForm.value = {
    email: emptyToUndefined(values.email),
    mobile: emptyToUndefined(values.mobile),
    nickname: emptyToUndefined(values.nickname),
    password: String(values.password ?? ''),
    username: String(values.username ?? ''),
  };
  captchaDialogVisible.value = true;
}

async function handleCaptchaSuccess(payload: {
  captchaType: AuthApi.CaptchaType;
  verifyToken: string;
}) {
  if (!pendingForm.value) return;
  captchaDialogVisible.value = false;
  loading.value = true;
  try {
    await registerApi({
      ...pendingForm.value,
      captchaType: payload.captchaType,
      captchaVerifyToken: payload.verifyToken,
    });
    ElMessage.success('注册成功，请登录');
    await router.replace(LOGIN_PATH);
  } finally {
    pendingForm.value = null;
    loading.value = false;
  }
}

function handleDialogClose() {
  captchaDialogVisible.value = false;
  pendingForm.value = null;
}

function emptyToUndefined(value: unknown): string | undefined {
  const v = value == null ? '' : String(value).trim();
  return v ? v : undefined;
}
</script>

<template>
  <AuthenticationRegister
    :form-schema="formSchema"
    :loading="loading"
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