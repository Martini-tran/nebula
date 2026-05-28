<template>
  <div class="reviews-shell">
    <!-- ── 顶部：品牌 + 顶部主导航（AI 中转 / 模型比较 / 比价选站） ── -->
    <header class="top-header">
      <div class="top-header__inner">
        <div class="brand-row">
          <RouterLink to="/" class="brand">
            <img
              :src="isDark ? logoDark : logoLight"
              alt="FluxLu"
              class="brand__logo"
            />
            <div class="brand__text">
              <p class="brand__kicker">fluxLu.com</p>
              <h1 class="brand__title">中转工具站</h1>
            </div>
          </RouterLink>

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
              <span>{{ item.label }}</span>
            </RouterLink>
          </nav>

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
      </div>
    </header>

    <!-- ── 主体：左侧 sidebar + 右侧内容 ── -->
    <div class="reviews-layout">
      <aside class="reviews-sidebar" aria-label="Reviews navigation">
        <p class="sidebar-title">中转测评</p>
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
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { reviewsNavItems } from '../data/reviewsNav'
import { useThemeStore } from '../stores/theme'
import logoLight from '../assets/logo-light.png'
import logoDark from '../assets/logo-dark.png'

type ProductNavItem = {
  key: string
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
 * 顶部主导航：仅作为模块入口跳转，不控制主体内容。
 * AI 中转   → /reviews         中转站综合测评
 * 模型比较 → /reviews/compare  跨厂商模型横向对比
 * 比价选站 → /reviews/guides   按场景给出选购建议
 */
const productNavItems: ProductNavItem[] = [
  {
    key: 'relay',
    label: 'AI 中转',
    icon: 'lucide:plug-zap',
    to: '/reviews',
    matchPrefixes: ['/reviews/directory'],
  },
  {
    key: 'compare',
    label: '模型比较',
    icon: 'lucide:bar-chart-3',
    to: '/reviews/compare',
  },
  {
    key: 'guides',
    label: '比价选站',
    icon: 'lucide:scale',
    to: '/reviews/guides',
  },
]

const sideNavItems = reviewsNavItems

const isActiveSide = (to: string) => route.path === to

const isActiveProduct = (item: ProductNavItem) => {
  if (item.to === '/reviews') {
    // 「AI 中转」对应 /reviews 主页与 /reviews/directory 子路由
    return (
      route.path === '/reviews' ||
      route.path.startsWith('/reviews/directory')
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
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
  background: color-mix(in srgb, var(--color-bg-surface) 78%, transparent);
  backdrop-filter: blur(18px) saturate(1.2);
  -webkit-backdrop-filter: blur(18px) saturate(1.2);
}

.top-header__inner {
  margin: 0 auto;
  width: 100%;
  max-width: 1600px;
  padding: 0.75rem var(--space-page-x);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  flex-wrap: wrap;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
}

.brand__logo {
  height: 1.9rem;
  width: auto;
  max-width: 2.6rem;
  object-fit: contain;
  border-radius: var(--radius-md);
  box-shadow: 0 4px 12px -4px color-mix(in srgb, var(--color-accent) 32%, transparent);
}

.brand__kicker {
  margin: 0;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.brand__title {
  margin: 0.1rem 0 0;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.15;
}

@media (min-width: 768px) {
  .brand__title {
    font-size: 1.18rem;
  }
}

/* 顶部主导航 */
.product-nav {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-bg-soft) 70%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
}

.product-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.95rem;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-text-secondary);
  text-decoration: none;
  border: 1px solid transparent;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.product-link:hover {
  color: var(--color-text-primary);
  background: color-mix(in srgb, var(--color-bg-surface) 70%, transparent);
}

.product-link__icon {
  width: 1rem;
  height: 1rem;
  opacity: 0.85;
}

.product-link--active {
  color: var(--color-bg-surface);
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-hover) 100%);
  border-color: color-mix(in srgb, var(--color-accent) 60%, transparent);
  box-shadow:
    0 8px 18px -10px color-mix(in srgb, var(--color-accent) 70%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.product-link--active:hover {
  color: var(--color-bg-surface);
  background: linear-gradient(135deg, var(--color-accent-hover) 0%, var(--color-accent) 100%);
}

.product-link--active .product-link__icon {
  opacity: 1;
}

.brand-row__end {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 9999px;
  border: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg-surface) 80%, transparent);
  color: var(--color-text-secondary);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.theme-toggle:hover {
  color: var(--color-text-primary);
  border-color: var(--color-accent);
  background: color-mix(in srgb, var(--color-accent-soft) 80%, transparent);
}

.theme-toggle__icon {
  width: 1rem;
  height: 1rem;
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
  .brand-row {
    flex-wrap: wrap;
  }

  .product-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    flex-wrap: nowrap;
    justify-content: flex-start;
  }

  .product-link {
    flex-shrink: 0;
  }

  .brand-row__end {
    margin-left: auto;
  }
}
</style>
