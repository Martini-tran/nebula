<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { login } from '../../api/auth'
import SliderCaptcha from '../../components/SliderCaptcha.vue'
import { useAuthStore } from '../../stores/auth'
import type { CaptchaType } from '../../types/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
/** 点登录且表单填好后才弹出验证码；每次打开重新挂载组件，拿到新图 */
const captchaOpen = ref(false)

const submitting = ref(false)
const errorMessage = ref('')
/** 懒校验：提交过一次后才对空字段标红 */
const submitted = ref(false)

const usernameMissing = computed(() => submitted.value && !username.value.trim())
const passwordMissing = computed(() => submitted.value && !password.value)

/** 只接受站内路径，防止 ?redirect= 被拿来跳到外站 */
const redirectTarget = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/works'
})

/** 第一步：校验表单，通过后弹出验证码 */
const submit = () => {
  submitted.value = true
  errorMessage.value = ''
  if (!username.value.trim() || !password.value || submitting.value) {
    return
  }
  captchaOpen.value = true
}

/** 第二步：拼图通过即带着一次性 verifyToken 登录，无需再点一次按钮 */
const onCaptchaPassed = async (payload: { captchaType: CaptchaType; verifyToken: string }) => {
  submitting.value = true
  try {
    const result = await login({
      username: username.value.trim(),
      password: password.value,
      captchaType: payload.captchaType,
      captchaVerifyToken: payload.verifyToken,
    })
    authStore.login(result)
    captchaOpen.value = false
    await router.replace(redirectTarget.value)
  } catch (error) {
    // verifyToken 已被消费，关掉弹窗；再点登录会重新出一张图
    captchaOpen.value = false
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

const closeCaptcha = () => {
  if (!submitting.value) {
    captchaOpen.value = false
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeCaptcha()
}

watch(captchaOpen, (open) => {
  if (open) {
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="login page">
    <div class="login__card surface">
      <header class="login__head">
        <h1 class="login__title">登录 Scribe</h1>
        <p class="page-subtitle">回到你的书桌，接着写下去。</p>
      </header>

      <form class="form" novalidate @submit.prevent="submit">
        <div class="field">
          <label class="field__label" for="login-username">
            用户名
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="login-username"
            v-model="username"
            class="field__input"
            :class="{ 'field__input--invalid': usernameMissing }"
            type="text"
            autocomplete="username"
            :aria-invalid="usernameMissing"
          />
          <p v-if="usernameMissing" class="field__error">请填写用户名</p>
        </div>

        <div class="field">
          <label class="field__label" for="login-password">
            密码
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="login-password"
            v-model="password"
            class="field__input"
            :class="{ 'field__input--invalid': passwordMissing }"
            type="password"
            autocomplete="current-password"
            :aria-invalid="passwordMissing"
          />
          <p v-if="passwordMissing" class="field__error">请填写密码</p>
        </div>

        <p v-if="errorMessage" class="form__error" role="alert">{{ errorMessage }}</p>

        <button class="btn btn--primary login__submit" type="submit" :disabled="submitting">
          <Icon v-if="submitting" icon="lucide:loader-circle" class="spin" />
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>
    </div>

    <transition name="dialog">
      <div v-if="captchaOpen" class="mask" @click.self="closeCaptcha">
        <div class="captcha-dialog surface" role="dialog" aria-modal="true" aria-labelledby="captcha-title">
          <header class="captcha-dialog__head">
            <h2 id="captcha-title" class="captcha-dialog__title">安全验证</h2>
            <button
              class="btn btn--quiet captcha-dialog__close"
              type="button"
              aria-label="关闭"
              :disabled="submitting"
              @click="closeCaptcha"
            >
              <Icon icon="lucide:x" />
            </button>
          </header>
          <div class="captcha-dialog__body">
            <SliderCaptcha @success="onCaptchaPassed" />
            <p v-if="submitting" class="captcha-dialog__status" role="status">
              <Icon icon="lucide:loader-circle" class="spin" />
              登录中…
            </p>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<style scoped lang="scss">
.login {
  display: grid;
  place-items: center;
  padding-block: 3rem 4rem;
}

.login__card {
  width: min(24rem, 100%);
  box-shadow: var(--shadow-lg);
}

.login__head {
  padding: 1.5rem 1.35rem 0;
}

.login__title {
  font-size: 1.35rem;
  font-weight: 800;
  margin-bottom: 0.35rem;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(10, 8, 6, 0.45);
  backdrop-filter: blur(2px);
}

.captcha-dialog {
  box-shadow: var(--shadow-lg);
}

.captcha-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 1.1rem;
  border-bottom: 1px solid var(--color-border);
}

.captcha-dialog__title {
  font-size: 1rem;
  font-weight: 700;
}

.captcha-dialog__close {
  padding: 0.3rem;
}

.captcha-dialog__close svg {
  width: 1.1rem;
  height: 1.1rem;
}

.captcha-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.1rem;
}

.captcha-dialog__status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.18s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.login__submit {
  justify-content: center;
  width: 100%;
  padding-block: 0.7rem;
}
</style>
