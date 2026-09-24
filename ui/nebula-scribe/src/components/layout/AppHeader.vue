<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../BrandMark.vue'
import ThemeToggle from '../ThemeToggle.vue'
import { navItems, product, socialLinks } from '../../data/product'
import type { NavItem } from '../../data/product'
import { logout as logoutApi } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const scrolled = ref(false)
const mobileOpen = ref(false)

const onScroll = () => {
  scrolled.value = window.scrollY > 8
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})

const isActive = (item: NavItem) => {
  if (item.kind !== 'route') return false
  if (item.href === '/') return route.path === '/'
  // 非首页：精确匹配或作为前缀（如 /works 命中 /works/1 详情页）
  return route.path === item.href || route.path.startsWith(`${item.href}/`)
}

const goLogin = () => {
  mobileOpen.value = false
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

const onLogout = async () => {
  mobileOpen.value = false
  try {
    await logoutApi()
  } catch {
    // 服务端会话可能已过期，本地照样清掉
  }
  authStore.logout()
  router.push('/')
}

const onNav = (item: NavItem) => {
  mobileOpen.value = false
  if (item.kind === 'soon') return
  if (item.kind === 'external') {
    window.open(item.href, '_blank', 'noopener')
    return
  }
  router.push(item.href)
}
</script>

<template>
  <header class="app-header" :class="{ 'app-header--scrolled': scrolled }">
    <div class="app-header__inner">
      <button class="brand" type="button" aria-label="回到首页" @click="router.push('/')">
        <BrandMark :size="34" />
        <span class="brand__name">{{ product.name }}</span>
      </button>

      <nav class="nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.label"
          type="button"
          class="nav__link"
          :class="{
            'nav__link--active': isActive(item),
            'nav__link--soon': item.kind === 'soon',
          }"
          :disabled="item.kind === 'soon'"
          :title="item.kind === 'soon' ? '即将上线' : undefined"
          @click="onNav(item)"
        >
          <Icon v-if="item.icon" :icon="item.icon" class="nav__icon" />
          {{ item.label }}
          <span v-if="item.kind === 'soon'" class="nav__badge">即将上线</span>
        </button>
      </nav>

      <div class="actions">
        <a
          v-for="social in socialLinks"
          :key="social.label"
          class="icon-link"
          :href="social.href"
          target="_blank"
          rel="noopener"
          :aria-label="social.label"
          :title="social.label"
        >
          <Icon :icon="social.icon" />
        </a>
        <ThemeToggle />
        <template v-if="authStore.isLoggedIn">
          <span class="user" :title="authStore.user?.username">
            <Icon icon="lucide:circle-user-round" />
            {{ authStore.displayName }}
          </span>
          <button class="btn btn--quiet logout-btn" type="button" @click="onLogout">退出</button>
        </template>
        <button v-else class="btn btn--ghost login-btn" type="button" @click="goLogin">登录</button>
        <button class="btn btn--primary write-btn" type="button" @click="router.push('/works')">
          <Icon icon="lucide:pen-line" />
          开始写
        </button>
        <button
          class="menu-toggle"
          type="button"
          :aria-expanded="mobileOpen"
          aria-label="切换菜单"
          @click="mobileOpen = !mobileOpen"
        >
          <Icon :icon="mobileOpen ? 'lucide:x' : 'lucide:menu'" />
        </button>
      </div>
    </div>

    <transition name="sheet">
      <nav v-if="mobileOpen" class="mobile-nav" aria-label="移动端导航">
        <button
          v-for="item in navItems"
          :key="item.label"
          type="button"
          class="mobile-nav__link"
          :class="{ 'mobile-nav__link--soon': item.kind === 'soon' }"
          :disabled="item.kind === 'soon'"
          @click="onNav(item)"
        >
          <Icon v-if="item.icon" :icon="item.icon" />
          {{ item.label }}
          <span v-if="item.kind === 'soon'" class="nav__badge">即将上线</span>
        </button>

        <button v-if="authStore.isLoggedIn" type="button" class="mobile-nav__link" @click="onLogout">
          <Icon icon="lucide:log-out" />
          退出（{{ authStore.displayName }}）
        </button>
        <button v-else type="button" class="mobile-nav__link" @click="goLogin">
          <Icon icon="lucide:log-in" />
          登录
        </button>

        <div class="mobile-nav__socials">
          <a
            v-for="social in socialLinks"
            :key="social.label"
            class="icon-link"
            :href="social.href"
            target="_blank"
            rel="noopener"
            :aria-label="social.label"
          >
            <Icon :icon="social.icon" />
            <span>{{ social.label }}</span>
          </a>
        </div>
      </nav>
    </transition>
  </header>
</template>

<style scoped lang="scss">
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: color-mix(in srgb, var(--color-bg-canvas) 80%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition:
    border-color 0.25s ease,
    box-shadow 0.25s ease,
    background 0.25s ease;
}

.app-header--scrolled {
  border-bottom-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: 0.75rem var(--space-page-x);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0;
}

.brand__name {
  font-size: 1.2rem;
  font-weight: 800;
  letter-spacing: 0.01em;
  color: var(--color-text-primary);
}

.nav {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-inline: auto;
}

.nav__link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0.45rem 0.7rem;
  border-radius: var(--radius-md);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.nav__link:hover:not(.nav__link--soon),
.nav__link--active {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.nav__link--soon {
  cursor: default;
  opacity: 0.7;
}

.nav__icon {
  width: 1.05rem;
  height: 1.05rem;
}

.nav__badge {
  font-size: 0.6rem;
  font-weight: 700;
  line-height: 1;
  padding: 0.18rem 0.35rem;
  border-radius: 999px;
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
  white-space: nowrap;
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
  max-width: 9rem;
  padding-inline: 0.35rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user svg {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
}

.write-btn svg {
  width: 1.05rem;
  height: 1.05rem;
}

.icon-link {
  display: inline-grid;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.icon-link svg {
  width: 1.2rem;
  height: 1.2rem;
}

.icon-link:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-soft);
}

.menu-toggle {
  display: none;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: 1.2rem;
}

.mobile-nav {
  display: none;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem var(--space-page-x) 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
}

.mobile-nav__link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-align: left;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0.7rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.mobile-nav__link:hover:not(.mobile-nav__link--soon) {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.mobile-nav__link--soon {
  cursor: default;
  opacity: 0.7;
}

.mobile-nav__link svg {
  width: 1.1rem;
  height: 1.1rem;
}

.mobile-nav__socials {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.mobile-nav__socials .icon-link {
  width: auto;
  gap: 0.45rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.sheet-enter-active,
.sheet-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 960px) {
  .nav,
  .actions > .icon-link,
  .user,
  .logout-btn,
  .login-btn,
  .write-btn {
    display: none;
  }

  .menu-toggle {
    display: grid;
  }

  .mobile-nav {
    display: flex;
  }
}
</style>
