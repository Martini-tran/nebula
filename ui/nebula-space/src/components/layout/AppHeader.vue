<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../BrandMark.vue'
import ThemeToggle from '../ThemeToggle.vue'
import { logout as logoutApi } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'
import { useBadgeStore } from '../../stores/badges'
import { quickCapture } from '../../composables/useQuickCapture'
import { MAIN_MODULES, MODULES, MORE_MODULES, defaultHomePath } from '../../config/modules'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const badges = useBadgeStore()

/** 当前所在模块：按路径前缀匹配 */
const activeKey = computed(() => MODULES.find((m) => route.path.startsWith(m.path))?.key)
const moreActive = computed(() => MORE_MODULES.some((m) => m.key === activeKey.value))

const moreOpen = ref(false)
const userOpen = ref(false)
const mobileOpen = ref(false)
const moreRef = ref<HTMLElement | null>(null)
const userRef = ref<HTMLElement | null>(null)

const closeAll = () => {
  moreOpen.value = false
  userOpen.value = false
  mobileOpen.value = false
}

watch(() => route.fullPath, closeAll)
// 切换页面时顺手刷新角标（任务、随手记的数字在各自页面里会变）
watch(() => route.path, () => authStore.isLoggedIn && badges.refresh(), { immediate: true })

/** 点在菜单外面就收起 */
const onDocClick = (event: MouseEvent) => {
  const target = event.target as Node
  if (moreOpen.value && !moreRef.value?.contains(target)) moreOpen.value = false
  if (userOpen.value && !userRef.value?.contains(target)) userOpen.value = false
}
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeAll()
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('keydown', onKeydown)
})

const initial = computed(() => authStore.displayName.trim().charAt(0).toUpperCase() || '我')

const onLogout = async () => {
  closeAll()
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
      <router-link :to="defaultHomePath()" class="brand">
        <BrandMark :size="30" />
        <span class="brand__name">Space</span>
      </router-link>

      <nav class="nav" aria-label="模块">
        <router-link
          v-for="m in MAIN_MODULES"
          :key="m.key"
          :to="m.path"
          class="nav__link"
          :class="{ 'nav__link--on': activeKey === m.key }"
          :title="m.label"
        >
          <Icon :icon="m.icon" class="nav__icon" />
          <span class="nav__label">{{ m.label }}</span>
          <span v-if="badges.counts[m.key]" class="nav__badge">{{ badges.counts[m.key] }}</span>
        </router-link>

        <div ref="moreRef" class="more">
          <button
            class="nav__link"
            :class="{ 'nav__link--on': moreActive || moreOpen }"
            type="button"
            :aria-expanded="moreOpen"
            aria-haspopup="menu"
            @click="moreOpen = !moreOpen"
          >
            <Icon icon="lucide:layout-grid" class="nav__icon" />
            <span class="nav__label">更多</span>
          </button>
          <transition name="pop">
            <div v-if="moreOpen" class="more__panel surface" role="menu">
              <router-link
                v-for="m in MORE_MODULES"
                :key="m.key"
                :to="m.path"
                class="more__item"
                :class="{ 'more__item--on': activeKey === m.key }"
                role="menuitem"
              >
                <span class="more__ico"><Icon :icon="m.icon" /></span>
                <span class="more__text">
                  <b>{{ m.label }}<span v-if="m.status === 'planned'" class="tag more__soon">规划中</span></b>
                  <small>{{ m.description }}</small>
                </span>
              </router-link>
            </div>
          </transition>
        </div>
      </nav>

      <div class="actions">
        <button class="capture" type="button" title="快速记录（Ctrl+Shift+Space）" aria-label="快速记录" @click="quickCapture.show('note')">
          <Icon icon="lucide:plus" />
        </button>
        <ThemeToggle />
        <div v-if="authStore.isLoggedIn" ref="userRef" class="user">
          <button
            class="avatar"
            type="button"
            :title="authStore.displayName"
            :aria-expanded="userOpen"
            aria-haspopup="menu"
            @click="userOpen = !userOpen"
          >
            {{ initial }}
          </button>
          <transition name="pop">
            <div v-if="userOpen" class="user__panel surface" role="menu">
              <div class="user__who">
                <b>{{ authStore.displayName }}</b>
                <small>Nebula 统一账号</small>
              </div>
              <button class="user__item" type="button" role="menuitem" @click="onLogout">
                <Icon icon="lucide:log-out" />退出登录
              </button>
            </div>
          </transition>
        </div>
        <button class="menu-toggle" type="button" :aria-expanded="mobileOpen" aria-label="打开菜单" @click="mobileOpen = !mobileOpen">
          <Icon :icon="mobileOpen ? 'lucide:x' : 'lucide:menu'" />
        </button>
      </div>
    </div>

    <transition name="sheet">
      <nav v-if="mobileOpen" class="mobile" aria-label="模块">
        <router-link
          v-for="m in MODULES"
          :key="m.key"
          :to="m.path"
          class="mobile__link"
          :class="{ 'mobile__link--on': activeKey === m.key }"
        >
          <Icon :icon="m.icon" />{{ m.label }}
          <span v-if="m.status === 'planned'" class="tag mobile__soon">规划中</span>
        </router-link>
      </nav>
    </transition>
  </header>
</template>

<style scoped lang="scss">
.header {
  position: sticky;
  top: env(safe-area-inset-top, 0px);
  z-index: 50;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg-canvas) 86%, transparent);
  backdrop-filter: blur(10px);
}

.header__inner {
  display: flex;
  align-items: center;
  gap: 1rem;
  height: var(--header-height);
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding-inline: var(--space-page-x);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex: none;
}

.brand__name {
  font-size: 1.08rem;
  font-weight: 800;
}

.nav {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  min-width: 0;
}

.nav__link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.42rem 0.65rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.nav__link:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.nav__link--on,
.nav__link--on:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.nav__icon {
  width: 1rem;
  height: 1rem;
  flex: none;
}

.more {
  position: relative;
}

.more__panel {
  position: absolute;
  top: calc(100% + 0.5rem);
  left: 0;
  z-index: 60;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.25rem;
  width: 34rem;
  padding: 0.5rem;
  box-shadow: var(--shadow-lg);
}

.more__item {
  display: flex;
  gap: 0.65rem;
  padding: 0.65rem;
  border-radius: var(--radius-md);
}

.more__item:hover,
.more__item--on {
  background: var(--color-bg-soft);
}

.more__ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.more__text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.more__text b {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
}

.more__text small {
  font-size: 0.76rem;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.more__soon,
.mobile__soon {
  font-size: 0.66rem;
  padding: 0 0.4rem;
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}

.user {
  position: relative;
}

.avatar {
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  border: 0;
  border-radius: 50%;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-weight: 800;
  cursor: pointer;
}

.user__panel {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  z-index: 60;
  width: 13rem;
  padding: 0.35rem;
  box-shadow: var(--shadow-lg);
}

.user__who {
  display: flex;
  flex-direction: column;
  padding: 0.55rem 0.6rem 0.65rem;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 0.3rem;
}

.user__who b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user__who small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.user__item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.5rem 0.6rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  text-align: left;
  font-size: 0.88rem;
  cursor: pointer;
}

.user__item:hover {
  background: var(--color-bg-soft);
}

.menu-toggle {
  display: none;
  place-items: center;
  width: 2.4rem;
  height: 2.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  font-size: 1.15rem;
  cursor: pointer;
}

.mobile {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.25rem;
  padding: 0.5rem var(--space-page-x) 1rem;
  border-top: 1px solid var(--color-border);
}

.mobile__link {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.65rem 0.7rem;
  border-radius: var(--radius-md);
  font-weight: 600;
}

.mobile__link--on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.pop-enter-active,
.pop-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s var(--ease-soft);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s ease;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

/* 标签随宽度收起：先只留图标，再整条导航收进菜单 */
@media (max-width: 1180px) {
  .nav__label {
    display: none;
  }

  .nav__link {
    padding: 0.5rem 0.6rem;
  }
}

@media (max-width: 760px) {
  .nav {
    display: none;
  }

  .menu-toggle {
    display: grid;
  }
}
.nav__link {
  position: relative;
}

.nav__badge {
  min-width: 1.1rem;
  padding: 0 0.3rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.1rem;
  text-align: center;
}

.nav__link--on .nav__badge {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

/* 标签收起时，角标缩成右上角的小数字 */
@media (max-width: 1180px) {
  .nav__badge {
    position: absolute;
    top: 0.1rem;
    right: 0.05rem;
    min-width: 0.95rem;
    padding: 0 0.2rem;
    font-size: 0.6rem;
    line-height: 0.95rem;
  }
}

.capture {
  display: grid;
  place-items: center;
  width: 2.3rem;
  height: 2.3rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  cursor: pointer;
}

.capture:hover {
  background: var(--color-brand-hover);
}

.capture svg {
  width: 1.15rem;
  height: 1.15rem;
}
</style>
