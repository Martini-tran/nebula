<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../BrandMark.vue'
import ThemeToggle from '../ThemeToggle.vue'
import { logout as logoutApi } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const onLogout = async () => {
  try {
    await logoutApi()
  } catch {
    // 服务端会话可能已过期，本地照样清掉
  }
  authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="header">
    <div class="header__inner">
      <router-link to="/" class="brand">
        <BrandMark :size="30" />
        <span class="brand__name">Space</span>
        <span class="brand__sub">个人空间</span>
      </router-link>

      <div class="actions">
        <ThemeToggle />
        <template v-if="authStore.isLoggedIn">
          <span class="user" :title="authStore.displayName">
            <Icon icon="lucide:circle-user-round" />
            {{ authStore.displayName }}
          </span>
          <button class="btn btn--quiet" type="button" @click="onLogout">退出</button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.header {
  position: sticky;
  top: 0;
  z-index: 50;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg-canvas) 86%, transparent);
  backdrop-filter: blur(10px);
}

.header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  height: var(--header-height);
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding-inline: var(--space-page-x);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}

.brand__name {
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.brand__sub {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 10rem;
  padding-inline: 0.35rem;
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user svg {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
}

@media (max-width: 640px) {
  .brand__sub,
  .user {
    display: none;
  }
}
</style>
