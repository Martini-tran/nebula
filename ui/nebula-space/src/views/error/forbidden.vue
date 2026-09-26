<script setup lang="ts">
/**
 * 已登录但没有空间权限（接口返回 403）。
 * 不能踢回登录页——重新登录还是同一个账号，会死循环；停在这里说明缺什么、找谁开。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../../components/BrandMark.vue'
import { logout as logoutApi } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'
import { defaultHomePath } from '../../config/modules'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/** 回到触发 403 的页面；只接受站内路径 */
const from = computed(() => {
  const raw = route.query.from
  return typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : defaultHomePath()
})

const retry = () => router.replace(from.value)

const switchAccount = async () => {
  try {
    await logoutApi()
  } catch {
    // 会话可能已失效，本地照样清掉
  }
  authStore.logout()
  router.replace({ name: 'login', query: { redirect: from.value } })
}
</script>

<template>
  <div class="fb">
    <header class="fb__bar">
      <BrandMark :size="28" />
      <b>Space</b>
    </header>
    <section class="fb__main">
      <span class="fb__ico"><Icon icon="lucide:lock-keyhole" /></span>
      <h1>这个账号还没有开通个人空间</h1>
      <p>
        你已成功登录，但账号所在角色没有个人空间的权限。请联系管理员在「系统管理 → 角色」中为你的角色勾选「个人空间」相关菜单与按钮权限，开通后点「刷新」即可。
      </p>
      <span class="fb__who">
        <Icon icon="lucide:circle-user-round" />{{ authStore.displayName }}
      </span>
      <p class="fb__perm">缺少的权限形如 <code>space:bookmark:list</code>、<code>space:folder:list</code>、<code>space:tag:list</code></p>
      <div class="fb__acts">
        <button class="btn btn--primary" type="button" @click="retry"><Icon icon="lucide:refresh-cw" />已开通，刷新</button>
        <button class="btn btn--ghost" type="button" @click="switchAccount">换个账号登录</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.fb {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.fb__bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  height: var(--header-height);
  padding-inline: var(--space-page-x);
  border-bottom: 1px solid var(--color-border);
}

.fb__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.8rem;
  max-width: 36rem;
  margin: 0 auto;
  padding: clamp(3rem, 12vh, 6rem) var(--space-page-x) 3rem;
  text-align: center;
}

.fb__ico {
  display: grid;
  place-items: center;
  width: 3.6rem;
  height: 3.6rem;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 1.6rem;
}

.fb__main h1 {
  font-size: 1.35rem;
  font-weight: 800;
}

.fb__main p {
  color: var(--color-text-secondary);
  line-height: 1.8;
  font-size: 0.92rem;
}

.fb__who {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.88rem;
  font-weight: 600;
}

.fb__perm {
  font-size: 0.8rem !important;
}

.fb__perm code {
  font-family: var(--font-mono);
  font-size: 0.78rem;
  padding: 0.1em 0.35em;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
}

.fb__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.6rem;
  margin-top: 0.4rem;
}
</style>
