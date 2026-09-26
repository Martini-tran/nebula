<script setup lang="ts">
import { computed, onMounted, ref, useTemplateRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { login } from '../../api/auth'
import BrandMark from '../../components/BrandMark.vue'
import BaseDialog from '../../components/base/BaseDialog.vue'
import SliderCaptcha from '../../components/SliderCaptcha.vue'
import { useAuthStore } from '../../stores/auth'
import { MODULES, defaultHomePath } from '../../config/modules'
import type { CaptchaType } from '../../types/auth'

const LAST_USERNAME_KEY = 'nebula-space:last-username'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const readLastUsername = () => {
  try {
    return localStorage.getItem(LAST_USERNAME_KEY) ?? ''
  } catch {
    return ''
  }
}

const username = ref(readLastUsername())
const password = ref('')
const showPassword = ref(false)
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
  return typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : defaultHomePath()
})

/** 登录失效跳过来时，说清楚原因和登录后回到哪里 */
const expired = computed(() => route.query.reason === 'expired')
const redirectLabel = computed(() => {
  const mod = MODULES.find((m) => redirectTarget.value.startsWith(m.path))
  return mod ? `「${mod.label}」` : '刚才的页面'
})

const passwordInput = useTemplateRef<HTMLInputElement>('passwordInput')
onMounted(() => {
  // 用户名已预填时直接把光标放到密码框
  if (username.value) passwordInput.value?.focus()
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
    try {
      localStorage.setItem(LAST_USERNAME_KEY, username.value.trim())
    } catch {
      // 存不了就下次重新输入，不影响登录
    }
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

const preview = [
  { letter: 'V', color: '#42b883', title: 'Vue.js 官方文档', host: 'cn.vuejs.org', tag: '文档', dot: '#4f46e5' },
  { letter: 'G', color: '#24292f', title: 'spring-cloud-gateway', host: 'github.com', tag: '开源', dot: '#2563eb' },
  { letter: 'A', color: '#d97757', title: 'Prompt caching', host: 'docs.anthropic.com', tag: '待读', dot: '#65a30d' },
]
</script>

<template>
  <div class="auth">
    <aside class="auth__aside">
      <div class="brand"><BrandMark :size="30" /><b>Space</b><span>个人空间</span></div>
      <div class="lead">
        <h2>书签、随手记、任务、会议，<br />收进一个只属于你的地方。</h2>
        <p>一处整理，哪台电脑都能找回。</p>
      </div>
      <div class="collage" aria-hidden="true">
        <div v-for="(item, i) in preview" :key="item.title" class="mini" :class="`mini--${i}`">
          <span class="mini__fav" :style="{ background: item.color }">{{ item.letter }}</span>
          <span class="mini__text"><b>{{ item.title }}</b><small>{{ item.host }}</small></span>
          <span class="tag"><span class="mini__dot" :style="{ background: item.dot }" />{{ item.tag }}</span>
        </div>
      </div>
      <ul class="points">
        <li><Icon icon="lucide:check" />数据只属于你的账号，别人看不见</li>
        <li><Icon icon="lucide:check" />一键导入 Chrome / Edge / Firefox 书签</li>
        <li><Icon icon="lucide:check" />随时导出，走得干净</li>
      </ul>
    </aside>

    <main class="auth__main">
      <div class="auth__form">
        <p v-if="expired" class="notice" role="status">
          <Icon icon="lucide:clock" />
          <span>登录已过期，请重新登录。登录后将回到 <b>{{ redirectLabel }}</b>。</span>
        </p>
        <span v-else class="tag tag--brand">欢迎回来</span>
        <h1 class="auth__title">登录 Space</h1>
        <p class="auth__sub">使用 Nebula 统一账号登录。</p>

        <form class="form auth__fields" novalidate @submit.prevent="submit">
          <div class="field">
            <label class="field__label" for="login-username">
              用户名 <span class="field__required" aria-hidden="true">*</span>
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
              密码 <span class="field__required" aria-hidden="true">*</span>
            </label>
            <div class="pwd">
              <input
                id="login-password"
                ref="passwordInput"
                v-model="password"
                class="field__input"
                :class="{ 'field__input--invalid': passwordMissing }"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                :aria-invalid="passwordMissing"
              />
              <button
                class="pwd__toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <Icon :icon="showPassword ? 'lucide:eye-off' : 'lucide:eye'" />
              </button>
            </div>
            <p v-if="passwordMissing" class="field__error">请填写密码</p>
          </div>

          <p v-if="errorMessage" class="form__error" role="alert">{{ errorMessage }}</p>

          <button class="btn btn--primary auth__submit" type="submit" :disabled="submitting">
            <Icon v-if="submitting" icon="lucide:loader-circle" class="spin" />
            {{ submitting ? '登录中…' : expired ? '登录并返回' : '登录' }}
          </button>
        </form>

        <p class="auth__foot">账号与 Nebula 管理后台、Scribe 等应用通用。<br />没有账号请联系管理员开通。</p>
      </div>
    </main>

    <BaseDialog :open="captchaOpen" title="安全验证" width="24rem" :locked="submitting" @close="captchaOpen = false">
      <div class="captcha-body">
        <SliderCaptcha @success="onCaptchaPassed" />
        <p v-if="submitting" class="captcha-status" role="status">
          <Icon icon="lucide:loader-circle" class="spin" />登录中…
        </p>
      </div>
    </BaseDialog>
  </div>
</template>

<style scoped lang="scss">
.auth {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(24rem, 30rem);
  min-height: 100vh;
}

.auth__aside {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding: clamp(2rem, 5vw, 3.5rem);
  border-right: 1px solid var(--color-border);
  background:
    radial-gradient(110% 90% at 85% 0%, color-mix(in srgb, var(--color-brand) 16%, transparent), transparent 60%),
    radial-gradient(80% 80% at 0% 100%, color-mix(in srgb, var(--color-accent) 12%, transparent), transparent 55%),
    var(--color-bg-canvas);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.brand b {
  font-size: 1.1rem;
  font-weight: 800;
}

.brand span {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.lead h2 {
  font-size: clamp(1.4rem, 2.4vw, 1.9rem);
  font-weight: 800;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.lead p {
  margin-top: 0.5rem;
  color: var(--color-text-secondary);
}

.collage {
  position: relative;
  flex: 1;
  min-height: 15rem;
  max-width: 30rem;
}

.mini {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 17rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-md);
}

.mini--0 {
  top: 0.5rem;
  left: 0;
  transform: rotate(-2deg);
}

.mini--1 {
  top: 5rem;
  left: 6rem;
  z-index: 1;
}

.mini--2 {
  top: 9.5rem;
  left: 1.5rem;
  transform: rotate(1.5deg);
}

.mini__fav {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.8rem;
  height: 1.8rem;
  border-radius: var(--radius-sm);
  color: #fff;
  font-weight: 800;
  font-size: 0.8rem;
}

.mini__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.mini__text b {
  font-size: 0.86rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini__text small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.mini__dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
}

.points {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0;
  padding: 1.25rem 0 0;
  border-top: 1px solid var(--color-border);
  list-style: none;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.points li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.points svg {
  color: var(--color-accent);
}

.auth__main {
  display: grid;
  place-items: center;
  padding: 2.5rem clamp(1.25rem, 4vw, 2.75rem);
  background: var(--color-bg-surface);
}

.auth__form {
  width: 100%;
  max-width: 24rem;
}

.notice {
  display: flex;
  gap: 0.55rem;
  padding: 0.65rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  font-size: 0.86rem;
  line-height: 1.6;
}

.notice svg {
  flex: none;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.auth__title {
  margin-top: 1rem;
  font-size: 1.45rem;
  font-weight: 800;
}

.auth__sub {
  margin-top: 0.3rem;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.auth__fields {
  padding: 1.5rem 0 0;
}

.pwd {
  position: relative;
}

.pwd .field__input {
  padding-right: 2.6rem;
}

.pwd__toggle {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  transform: translateY(-50%);
  cursor: pointer;
}

.auth__submit {
  width: 100%;
  padding-block: 0.7rem;
}

.auth__foot {
  margin-top: 2rem;
  font-size: 0.8rem;
  line-height: 1.8;
  text-align: center;
  color: var(--color-text-secondary);
}

.captcha-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.1rem;
}

.captcha-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

@media (max-width: 860px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .auth__aside {
    display: none;
  }
}
</style>
