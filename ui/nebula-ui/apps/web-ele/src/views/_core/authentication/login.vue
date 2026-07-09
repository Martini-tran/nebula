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
const pendingCredentials = ref<null | { password: string; username: string }>(
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
  <div class="login-modern">
    <AuthenticationLogin
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-qrcode-login="false"
      :show-third-party-login="false"
      @submit="handleSubmit"
    />
  </div>

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

<style scoped>
/**
 * 登录表单现代化重构：与 3D 场景、主题主色统一的科技感玻璃卡片。
 * 仅作用于本登录页（scoped + :deep），不影响全局表单组件。
 */
.login-modern {
  width: 100%;
  max-width: 26rem;
  margin-inline: auto;
  padding: 1.75rem;
  border: 1px solid hsl(var(--border) / 60%);
  border-radius: 1.25rem;
  background-color: hsl(var(--card) / 70%);
  box-shadow:
    inset 0 1px 0 0 hsl(var(--primary) / 8%),
    0 20px 50px -24px hsl(var(--primary) / 45%),
    0 8px 24px -16px rgb(0 0 0 / 25%);
  backdrop-filter: blur(14px);
}

@media (width >= 640px) {
  .login-modern {
    padding: 2.25rem;
  }
}

/* 标题：前景色到主色的渐变，呼应 3D 主色 */
.login-modern :deep(h2) {
  width: fit-content;
  background: linear-gradient(
    90deg,
    hsl(var(--foreground)),
    hsl(var(--primary))
  );
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* 输入框：更舒展的高度、圆角、聚焦时主色发光 */
.login-modern :deep(input) {
  height: 2.875rem;
  border-radius: 0.75rem;
  background-color: hsl(var(--background) / 60%);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.login-modern :deep(input:focus),
.login-modern :deep(input:focus-visible) {
  border-color: hsl(var(--primary));
  background-color: hsl(var(--background));
  box-shadow: 0 0 0 3px hsl(var(--primary) / 18%);
  outline: none;
}

/* 主登录按钮：主色渐变 + 投影 + 悬浮微抬升 */
.login-modern :deep(button[aria-label='login']) {
  height: 2.875rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-radius: 0.75rem;
  background-image: linear-gradient(
    135deg,
    hsl(var(--primary)),
    hsl(var(--primary) / 82%)
  );
  box-shadow: 0 10px 26px -10px hsl(var(--primary) / 60%);
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    filter 0.2s ease;
}

.login-modern :deep(button[aria-label='login']:hover) {
  filter: brightness(1.03);
  box-shadow: 0 14px 32px -10px hsl(var(--primary) / 70%);
  transform: translateY(-1px);
}

.login-modern :deep(button[aria-label='login']:active) {
  transform: translateY(0);
}
</style>