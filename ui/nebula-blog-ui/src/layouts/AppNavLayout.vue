<template>
  <div
    class="app-shell"
    :class="{
      'app-shell--atlas-home': isAtlasHome,
      'app-shell--reader-article': isReaderArticle,
    }"
  >
    <header class="app-header">
      <div class="app-header__glow" aria-hidden="true" />
      <div class="app-header__inner">
        <div class="brand-row">
          <div class="brand">
            <span class="brand__logo-wrap">
              <img :src="isDark ? logoDark : logoLight" alt="orccode logo" class="brand__logo" />
            </span>
            <span class="brand__text">
              <span class="brand__title">orccode</span>
              <span class="brand__kicker">orccode.com</span>
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
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { homeNavItems } from '../router/nav'
import { useThemeStore } from '../stores/theme'
import logoLight from '../assets/logo-light.png'
import logoDark from '../assets/logo-dark.png'

const route = useRoute()
const visibleNavItems = homeNavItems
const isAtlasHome = computed(() => route.path === '/' || route.path === '/home')
const isReaderArticle = computed(() => route.path === '/article')
const isActive = (to: string) => {
  if (to === '/articles' && route.path === '/article') {
    return true
  }
  return route.path === to || route.path.startsWith(`${to}/`)
}

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)
</script>

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

/* Atlas owns the home canvas and keeps the shared navigation as a navy masthead. */
.app-shell--atlas-home .app-header {
  border-bottom-color: rgba(255, 255, 255, 0.18);
  background: #102a43;
  box-shadow: none;
}

.app-shell--atlas-home .app-header__glow {
  display: none;
}

.app-shell--atlas-home .app-header__inner {
  max-width: 1472px;
}

.app-shell--atlas-home .app-main {
  max-width: none;
  padding: 0;
}

.app-shell--atlas-home .brand__title {
  color: #fff;
  background: none;
  -webkit-text-fill-color: #fff;
}

.app-shell--atlas-home .brand__kicker,
.app-shell--atlas-home .nav-item {
  color: #cbd9e0;
}

.app-shell--atlas-home .nav-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.app-shell--atlas-home .nav-item--active {
  color: #fff;
  background: rgba(242, 201, 76, 0.14);
}

.app-shell--atlas-home .nav-item::after,
.app-shell--atlas-home .nav-item--active::after {
  background: #f2c94c;
}

.app-shell--atlas-home .theme-toggle {
  border-color: rgba(255, 255, 255, 0.32);
  background: rgba(255, 255, 255, 0.08);
  color: #f2c94c;
}

.app-shell--atlas-home .theme-toggle:hover {
  border-color: #f2c94c;
  background: rgba(242, 201, 76, 0.14);
  color: #fff;
}

/* The standalone article uses the paper reader canvas; embedded articles keep the parent layout. */
.app-shell--reader-article .app-header {
  border-bottom-color: color-mix(in srgb, var(--color-border) 80%, transparent);
  background: color-mix(in srgb, var(--color-bg-canvas) 92%, transparent);
  box-shadow: none;
}

.app-shell--reader-article .app-main {
  max-width: none;
  padding: 0;
}

/* Reader pages use the compact single-line masthead from the Focus Reader concept. */
.app-shell--reader-article {
  --reader-header-paper: #f3f0e8;
  --reader-header-ink: #252621;
  --reader-header-muted: #77766e;
  --reader-header-line: #d5d0c5;
  --reader-header-accent: #c9664d;
}

.app-shell--reader-article .app-header {
  background: color-mix(in srgb, var(--reader-header-paper) 94%, transparent);
  border-bottom-color: var(--reader-header-line);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.app-shell--reader-article .app-header__glow {
  display: none;
}

.app-shell--reader-article .app-header__inner {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 2rem;
  width: min(90rem, calc(100% - 3rem));
  max-width: none;
  min-height: 4.5rem;
  padding: 0;
}

.app-shell--reader-article .brand-row {
  display: contents;
}

.app-shell--reader-article .brand {
  grid-column: 1;
  grid-row: 1;
  gap: 0.55rem;
}

.app-shell--reader-article .brand__logo-wrap {
  width: 2rem;
  height: 2rem;
  border-radius: 0;
  border-color: var(--reader-header-line);
  background: transparent;
  box-shadow: none;
}

.app-shell--reader-article .brand__title {
  color: var(--reader-header-ink);
  background: none;
  -webkit-text-fill-color: currentColor;
  letter-spacing: 0.03em;
}

.app-shell--reader-article .brand__kicker {
  color: var(--reader-header-muted);
}

.app-shell--reader-article .nav-bar {
  grid-column: 2;
  grid-row: 1;
  justify-content: center;
  gap: 0.2rem;
  margin-top: 0;
}

.app-shell--reader-article .nav-item {
  padding: 0.55rem 0.7rem;
  border-radius: 0;
  color: var(--reader-header-muted);
  font-size: 0.8rem;
}

.app-shell--reader-article .nav-item:hover {
  color: var(--reader-header-ink);
  background: transparent;
}

.app-shell--reader-article .nav-item--active {
  color: var(--reader-header-accent);
  background: transparent;
}

.app-shell--reader-article .nav-item::after {
  right: 0.7rem;
  bottom: 0.2rem;
  left: 0.7rem;
  background: var(--reader-header-accent);
}

.app-shell--reader-article .brand-row__end {
  grid-column: 3;
  grid-row: 1;
  margin-left: 0;
}

.app-shell--reader-article .theme-toggle {
  width: 2rem;
  height: 2rem;
  border-radius: 0;
  border-color: var(--reader-header-line);
  background: transparent;
  color: var(--reader-header-muted);
}

.app-shell--reader-article .theme-toggle:hover {
  border-color: var(--reader-header-accent);
  background: transparent;
  color: var(--reader-header-accent);
}

:global(:root[data-theme='dark']) .app-shell--reader-article {
  --reader-header-paper: #171917;
  --reader-header-ink: #e7e1d6;
  --reader-header-muted: #a5a197;
  --reader-header-line: #3c3c37;
  --reader-header-accent: #d9785f;
}

@media (max-width: 720px) {
  .app-shell--reader-article .app-header__inner {
    display: flex;
    flex-wrap: wrap;
    gap: 0;
    width: calc(100% - 2rem);
    min-height: 4rem;
    padding: 0.7rem 0;
  }

  .app-shell--reader-article .brand {
    order: 1;
  }

  .app-shell--reader-article .brand-row__end {
    order: 2;
    margin-left: auto;
  }

  .app-shell--reader-article .nav-bar {
    order: 3;
    width: 100%;
    justify-content: flex-start;
    margin-top: 0.65rem;
    padding-bottom: 0.05rem;
  }
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
