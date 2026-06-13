<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../BrandMark.vue'
import ThemeToggle from '../ThemeToggle.vue'
import { navItems, product } from '../../data/product'

const route = useRoute()
const router = useRouter()

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

const scrollToAnchor = (hash: string) => {
  const id = hash.replace('#', '')
  if (id === 'top') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  const el = document.getElementById(id)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 锚点导航：当前在首页则平滑滚动，否则先回首页再滚动。 */
const goAnchor = async (hash: string) => {
  mobileOpen.value = false
  if (route.path !== '/') {
    await router.push('/')
    // 等待首页挂载后再滚动
    requestAnimationFrame(() => requestAnimationFrame(() => scrollToAnchor(hash)))
    return
  }
  scrollToAnchor(hash)
}

const goRoute = (path: string) => {
  mobileOpen.value = false
  router.push(path)
}

const onNavClick = (href: string) => {
  if (href.startsWith('#')) {
    goAnchor(href)
  } else {
    goRoute(href)
  }
}

const onDownload = () => goAnchor('#download')
</script>

<template>
  <header class="app-header" :class="{ 'app-header--scrolled': scrolled }">
    <div class="app-header__inner">
      <button class="brand" type="button" aria-label="回到首页" @click="goAnchor('#top')">
        <BrandMark :size="34" />
        <span class="brand__name">{{ product.name }}</span>
      </button>

      <nav class="nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.href"
          type="button"
          class="nav__link"
          :class="{ 'nav__link--active': item.href === '/versions' && route.path === '/versions' }"
          @click="onNavClick(item.href)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="actions">
        <ThemeToggle />
        <button class="btn-download" type="button" @click="onDownload">
          <Icon icon="lucide:download" class="btn-download__icon" />
          <span>免费下载</span>
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
          :key="item.href"
          type="button"
          class="mobile-nav__link"
          @click="onNavClick(item.href)"
        >
          {{ item.label }}
        </button>
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

.nav__link:hover,
.nav__link--active {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.btn-download {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-weight: 700;
  padding: 0.55rem 1rem;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}

.btn-download:hover {
  background: var(--color-brand-hover);
  transform: translateY(-1px);
}

.btn-download__icon {
  width: 1.1rem;
  height: 1.1rem;
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

.mobile-nav__link:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
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

@media (max-width: 860px) {
  .nav,
  .btn-download span {
    display: none;
  }

  .menu-toggle {
    display: grid;
  }

  .mobile-nav {
    display: flex;
  }

  .btn-download {
    padding: 0.55rem 0.7rem;
  }
}
</style>
