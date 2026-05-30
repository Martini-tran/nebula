<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-header__glow" aria-hidden="true" />
      <div class="app-header__inner">
        <div class="brand-row">
          <div class="brand">
            <span class="brand__logo-wrap">
              <img :src="isDark ? logoDark : logoLight" alt="nebula logo" class="brand__logo" />
            </span>
            <span class="brand__text">
              <span class="brand__title">nebula</span>
              <span class="brand__kicker">nebula.com</span>
            </span>
          </div>

          <div class="brand-row__end">
            <button
              class="theme-toggle"
              :class="isDark ? 'theme-toggle--dark' : 'theme-toggle--light'"
              @click="themeStore.toggle"
              :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
              :title="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
              type="button"
            >
              <!-- Sun — shown in dark mode, clicking → light -->
              <svg v-if="isDark" class="theme-toggle__icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="4"/>
                <line x1="12" y1="2" x2="12" y2="5"/>
                <line x1="12" y1="19" x2="12" y2="22"/>
                <line x1="4.22" y1="4.22" x2="6.34" y2="6.34"/>
                <line x1="17.66" y1="17.66" x2="19.78" y2="19.78"/>
                <line x1="2" y1="12" x2="5" y2="12"/>
                <line x1="19" y1="12" x2="22" y2="12"/>
                <line x1="4.22" y1="19.78" x2="6.34" y2="17.66"/>
                <line x1="17.66" y1="6.34" x2="19.78" y2="4.22"/>
              </svg>
              <!-- Moon — shown in light mode, clicking → dark -->
              <svg v-else class="theme-toggle__icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              </svg>
            </button>
          </div>
        </div>

        <nav class="nav-bar" aria-label="Primary">
          <RouterLink
            v-for="item in visibleNavItems"
            :key="item.key"
            :to="item.to"
            class="nav-item"
            :class="{ 'nav-item--active': isActive(item.to) }"
            :aria-current="isActive(item.to) ? 'page' : undefined"
          >
            <span class="nav-item__icon" aria-hidden="true">
              <Icon :icon="item.icon" width="16" height="16" />
            </span>
            <span class="nav-item__label">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </div>
    </header>

    <main class="app-main">
      <router-view v-slot="{ Component, route: childRoute }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" :key="childRoute.fullPath" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { homeNavItems } from '../router/nav'
import { useThemeStore } from '../stores/theme'
import logoLight from '../assets/logo-light.png'
import logoDark from '../assets/logo-dark.png'

const route = useRoute()
const visibleNavItems = homeNavItems
const isActive = (to: string) => {
  if (to === '/articles' && route.path === '/article') {
    return true
  }
  return route.path === to || route.path.startsWith(`${to}/`)
}

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)</script>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

/* ─────────────── 顶部 ─────────────── */
.app-header {
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
.app-header__glow {
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

.app-header__inner {
  margin: 0 auto;
  width: 100%;
  max-width: 1600px;
  padding: 0.85rem var(--space-page-x) 0.6rem;
}

.app-main {
  position: relative;
  z-index: 10;
  margin: 0 auto;
  width: 100%;
  max-width: 1600px;
  padding: var(--space-page-y) var(--space-page-x);
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

@media (prefers-reduced-motion: reduce) {
  .page-fade-enter-active,
  .page-fade-leave-active {
    transition: none;
  }
  .page-fade-enter-from,
  .page-fade-leave-to {
    transform: none;
  }
}

/* ─────────────── 品牌行 ─────────────── */
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
  min-width: 0;
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
  flex-shrink: 0;
}

.brand__logo {
  height: 1.4rem;
  width: auto;
  max-width: 1.6rem;
  object-fit: contain;
  display: block;
}

.brand__text {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.1;
  min-width: 0;
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

/* ─────────────── 主导航：下划线风 ─────────────── */
.nav-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.15rem;
  margin-top: 0.5rem;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.nav-item {
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
  background: transparent;
  border: none;
  text-decoration: none;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.nav-item::after {
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

.nav-item:hover {
  color: var(--color-text-primary);
  background: color-mix(in srgb, var(--color-bg-soft) 60%, transparent);
}

.nav-item:hover::after {
  transform: scaleX(0.5);
  background: color-mix(in srgb, var(--color-accent) 60%, transparent);
}

.nav-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 35%, transparent);
}

.nav-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: currentColor;
  opacity: 0.75;
  transition: opacity 0.18s ease;
}

.nav-item:hover .nav-item__icon,
.nav-item--active .nav-item__icon {
  opacity: 1;
}

.nav-item__label {
  line-height: 1;
}

.nav-item--active {
  color: var(--color-accent-text);
  background: color-mix(in srgb, var(--color-accent) 10%, transparent);
}

.nav-item--active::after {
  transform: scaleX(1);
  background: var(--color-accent);
}

@media (prefers-reduced-motion: reduce) {
  .nav-item,
  .nav-item__icon,
  .nav-item::after {
    transition: none;
  }
}

/* ─────────────── 主题按钮 ─────────────── */
.brand-row__end {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
  margin-left: auto;
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

.theme-toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 35%, transparent);
}

.theme-toggle:active {
  transform: rotate(-12deg) scale(0.95);
}

.theme-toggle__icon {
  display: block;
  width: 1.05rem;
  height: 1.05rem;
  transition: transform 0.35s ease, opacity 0.2s ease;
}

@media (max-width: 720px) {
  .nav-bar {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
    margin-top: 0.35rem;
    padding-bottom: 0.15rem;
  }

  .nav-bar::-webkit-scrollbar {
    display: none;
  }

  .nav-item {
    flex-shrink: 0;
    padding: 0.5rem 0.7rem;
    font-size: 0.82rem;
  }

  .nav-item::after {
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
