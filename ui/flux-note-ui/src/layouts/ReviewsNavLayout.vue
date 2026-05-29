<template>
  <div class="reviews-shell">
    <!-- ── 顶部：品牌 + 顶部主导航（AI 中转 / 模型比较 / 比价选站） ── -->
    <header class="top-header">
      <div class="top-header__glow" aria-hidden="true" />
      <div class="top-header__inner">
        <div class="brand-row">
          <RouterLink to="/" class="brand">
            <span class="brand__logo-wrap">
              <img
                :src="isDark ? logoDark : logoLight"
                alt="FluxLu"
                class="brand__logo"
              />
            </span>
            <span class="brand__text">
              <span class="brand__title">FluxLu</span>
              <span class="brand__kicker">中转工具站</span>
            </span>
          </RouterLink>

          <div class="brand-row__end">
            <button
              class="theme-toggle"
              :class="isDark ? 'theme-toggle--dark' : 'theme-toggle--light'"
              :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
              :title="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
              type="button"
              @click="themeStore.toggle"
            >
              <Icon
                :icon="isDark ? 'lucide:sun' : 'lucide:moon'"
                class="theme-toggle__icon"
              />
            </button>
          </div>
        </div>

        <nav class="product-nav" aria-label="Product">
          <RouterLink
            v-for="item in productNavItems"
            :key="item.key"
            :to="item.to"
            class="product-link"
            :class="{ 'product-link--active': isActiveProduct(item) }"
            :aria-current="isActiveProduct(item) ? 'page' : undefined"
          >
            <Icon :icon="item.icon" class="product-link__icon" />
            <span class="product-link__label">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </div>
    </header>

    <!-- ── 主体：左侧 sidebar + 右侧内容 ── -->
    <div class="reviews-layout">
      <aside class="reviews-sidebar" aria-label="Reviews navigation">
        <p class="sidebar-title">{{ sidebarTitle }}</p>
        <nav class="side-nav" aria-label="Sidebar">
          <RouterLink
            v-for="item in sideNavItems"
            :key="item.key"
            :to="item.to"
            class="side-nav__item"
            :class="{ 'side-nav__item--active': isActiveSide(item.to) }"
          >
            <span class="side-nav__label">{{ item.label }}</span>
            <span class="side-nav__desc">{{ item.description }}</span>
          </RouterLink>
        </nav>
      </aside>

      <main class="reviews-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { reviewsNavItems, type ReviewsNavGroup } from '../data/reviewsNav'
import { useThemeStore } from '../stores/theme'
import logoLight from '../assets/logo-light.png'
import logoDark from '../assets/logo-dark.png'

type ProductNavItem = {
  key: ReviewsNavGroup
  label: string
  icon: string
  to: string
  /** 高亮命中的额外路径前缀 */
  matchPrefixes?: string[]
}

const route = useRoute()
const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

/**
 * 顶部主导航：作为模块入口跳转，并决定左侧侧边栏显示哪一组。
 * AI 中转   → /reviews         relay 组：收录、推荐
 * 所有套餐 → /reviews/guides   guides 组
 */
const productNavItems: ProductNavItem[] = [
  {
    key: 'relay',
    label: 'AI 中转',
    icon: 'lucide:plug-zap',
    to: '/reviews',
    matchPrefixes: ['/reviews/directory', '/reviews/recommend', '/reviews/detail'],
  },
  {
    key: 'guides',
    label: '测评选站',
    icon: 'lucide:scale',
    to: '/reviews/guides',
  },
]

const isActiveProduct = (item: ProductNavItem) => {
  if (item.to === '/reviews') {
    return (
      route.path === '/reviews' ||
      route.path.startsWith('/reviews/directory') ||
      route.path.startsWith('/reviews/recommend') ||
      route.path.startsWith('/reviews/detail')
    )
  }
  if (route.path === item.to) return true
  if (route.path.startsWith(`${item.to}/`)) return true
  return Boolean(
    item.matchPrefixes?.some(
      (p) => route.path === p || route.path.startsWith(`${p}/`),
    ),
  )
}

const activeGroup = computed<ReviewsNavGroup>(() => {
  const hit = productNavItems.find((p) => isActiveProduct(p))
  return hit?.key ?? 'relay'
})

const sideNavItems = computed(() =>
  reviewsNavItems.filter((item) => item.group === activeGroup.value),
)

const sidebarTitle = computed(() => {
  const hit = productNavItems.find((p) => p.key === activeGroup.value)
  return hit?.label ?? '中转测评'
})

const isActiveSide = (to: string) => route.path === to
</script>

<style scoped>
.reviews-shell {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

/* ─────────────── 顶部 ─────────────── */
.top-header {
  position: sticky;
  top: 0;
  z-index: 30;
  isolation: isolate;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 55%, transparent);
  background: color-mix(in srgb, var(--color-bg-surface) 72%, transparent);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  box-shadow:
    0 1px 0 color-mix(in srgb, var(--color-bg-surface) 100%, transparent) inset,
    0 6px 20px -18px color-mix(in srgb, var(--color-text-primary) 35%, transparent);
}

/* 顶部柔光：使强调色在亚克力背景上"透"出一点品牌氛围 */
.top-header__glow {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  opacity: 0.55;
  background:
    radial-gradient(
      60rem 14rem at 12% -20%,
      color-mix(in srgb, var(--color-accent) 18%, transparent),
      transparent 70%
    ),
    radial-gradient(
      40rem 10rem at 90% -40%,
      color-mix(in srgb, var(--color-accent) 12%, transparent),
      transparent 70%
    );
}

.top-header__inner {
  margin: 0 auto;
  width: 100%;
  max-width: 1600px;
  padding: 0.85rem var(--space-page-x) 0.6rem;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 3rem;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.7rem;
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.brand:hover {
  opacity: 0.92;
}

.brand:active {
  transform: scale(0.985);
}

.brand__logo-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: var(--radius-md);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-accent) 18%, var(--color-bg-surface)) 0%,
    color-mix(in srgb, var(--color-accent) 4%, var(--color-bg-surface)) 100%
  );
  border: 1px solid color-mix(in srgb, var(--color-accent) 32%, transparent);
  box-shadow:
    0 6px 16px -10px color-mix(in srgb, var(--color-accent) 50%, transparent),
    inset 0 1px 0 color-mix(in srgb, #ffffff 30%, transparent);
}

.brand__logo {
  height: 1.4rem;
  width: auto;
  max-width: 1.6rem;
  object-fit: contain;
}

.brand__text {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand__title {
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  background: linear-gradient(
    135deg,
    var(--color-text-primary) 0%,
    color-mix(in srgb, var(--color-accent) 80%, var(--color-text-primary)) 100%
  );
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand__kicker {
  margin-top: 0.15rem;
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

@media (min-width: 768px) {
  .brand__title {
    font-size: 1.15rem;
  }
}

/* 顶部主导航 —— 简洁下划线风格，告别厚重胶囊 */
.product-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.15rem;
  margin-top: 0.5rem;
  padding: 0;
}

.product-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 0.9rem;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: 0.005em;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.product-link::after {
  content: "";
  position: absolute;
  left: 0.9rem;
  right: 0.9rem;
  bottom: 0.25rem;
  height: 2px;
  border-radius: 2px;
  background: var(--color-accent);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.product-link:hover {
  color: var(--color-text-primary);
  background: color-mix(in srgb, var(--color-bg-soft) 60%, transparent);
}

.product-link:hover::after {
  transform: scaleX(0.5);
  background: color-mix(in srgb, var(--color-accent) 60%, transparent);
}

.product-link__icon {
  width: 1rem;
  height: 1rem;
  opacity: 0.75;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.product-link:hover .product-link__icon,
.product-link--active .product-link__icon {
  opacity: 1;
}

.product-link--active {
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 10%, transparent);
}

.product-link--active::after {
  transform: scaleX(1);
  background: var(--color-accent);
}

.brand-row__end {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 9999px;
  border: 1px solid color-mix(in srgb, var(--color-border) 75%, transparent);
  background: color-mix(in srgb, var(--color-bg-surface) 70%, transparent);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.theme-toggle:hover {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 55%, transparent);
  background: color-mix(in srgb, var(--color-accent-soft) 70%, transparent);
  transform: rotate(-12deg);
}

.theme-toggle:active {
  transform: rotate(-12deg) scale(0.95);
}

.theme-toggle__icon {
  width: 1.05rem;
  height: 1.05rem;
}

/* ─────────────── 主体布局 ─────────────── */
.reviews-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}

@media (min-width: 980px) {
  .reviews-layout {
    grid-template-columns: 240px minmax(0, 1fr);
    align-items: start;
  }
}

.reviews-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

@media (min-width: 980px) {
  .reviews-sidebar {
    position: sticky;
    top: calc(3.6rem + var(--space-page-y));
    max-height: calc(100vh - 3.6rem - (var(--space-page-y) * 2));
    overflow-y: auto;
    padding-right: 0.25rem;
    scrollbar-width: thin;
  }
}

.sidebar-title {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  padding: 0 0.35rem;
}

.side-nav {
  display: grid;
  gap: 0.3rem;
}

.side-nav__item {
  display: grid;
  gap: 0.15rem;
  padding: 0.65rem 0.85rem;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  background: transparent;
  text-decoration: none;
  color: var(--color-text-secondary);
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.side-nav__item:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-border) 60%, transparent);
}

.side-nav__label {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.side-nav__desc {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.side-nav__item--active {
  background: color-mix(in srgb, var(--color-accent) 10%, var(--color-bg-surface));
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
}

.side-nav__item--active .side-nav__label {
  color: var(--color-accent-text);
}

.reviews-main {
  min-width: 0;
}

@media (max-width: 720px) {
  .product-nav {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
    margin-top: 0.35rem;
    padding-bottom: 0.15rem;
  }

  .product-nav::-webkit-scrollbar {
    display: none;
  }

  .product-link {
    flex-shrink: 0;
    padding: 0.5rem 0.7rem;
    font-size: 0.82rem;
  }

  .product-link::after {
    left: 0.7rem;
    right: 0.7rem;
  }

  .brand__logo-wrap {
    width: 2rem;
    height: 2rem;
  }

  .brand__logo {
    height: 1.25rem;
    max-width: 1.4rem;
  }
}
</style>
