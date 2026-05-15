<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-header__inner">
        <div class="brand-row">
          <div class="brand">
            <img :src="isDark ? logoDark : logoLight" alt="FluxLu logo" class="brand__logo" />
            <div class="brand__text">
              <p class="brand__kicker">fluxLu.com</p>
              <h1 class="brand__title">FluxLu</h1>
            </div>
          </div>

          <div class="brand-row__end">
<!--            <span class="brand-chip">-->
<!--              <span class="brand-chip__dot" aria-hidden="true"></span>-->
<!--              神秘国度-->
<!--            </span>-->

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
      <router-view />
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
const isActive = (to: string) => route.path === to || route.path.startsWith(`${to}/`)

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

.app-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
  background: color-mix(in srgb, var(--color-bg-surface) 72%, transparent);
  backdrop-filter: blur(18px) saturate(1.2);
  -webkit-backdrop-filter: blur(18px) saturate(1.2);
  box-shadow: 0 1px 0 color-mix(in srgb, var(--color-bg-surface) 60%, transparent) inset;
}

.app-main {
  position: relative;
  z-index: 10;
  margin: 0 auto;
  width: 100%;
  max-width: var(--container-max-width);
  padding: var(--space-page-y) var(--space-page-x);
}

.app-header__inner {
  margin: 0 auto;
  width: 100%;
  max-width: var(--container-max-width);
  padding: 1.15rem var(--space-page-x) 0.85rem;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.brand__logo {
  height: 1.9rem;
  width: auto;
  max-width: 2.75rem;
  object-fit: contain;
  flex-shrink: 0;
  display: block;
  border-radius: var(--radius-md);
  box-shadow: 0 4px 12px -4px color-mix(in srgb, var(--color-accent) 32%, transparent);
}

.brand__text {
  min-width: 0;
}

.brand__kicker {
  margin: 0;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.brand__title {
  margin: 0.15rem 0 0;
  font-size: 1.2rem;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}
@media (min-width: 768px) {
  .brand__title {
    font-size: 1.4rem;
  }
}


.nav-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0.35rem;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--color-border) 80%, transparent);
  background: color-mix(in srgb, var(--color-bg-surface) 60%, transparent);
  box-shadow: var(--shadow-sm);
}

.nav-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.95rem;
  border-radius: calc(var(--radius-lg) - 0.25rem);
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
  text-decoration: none;
  -webkit-tap-highlight-color: transparent;
}

.nav-item:hover {
  color: var(--color-text-primary);
  background: color-mix(in srgb, var(--color-bg-surface) 85%, transparent);
  border-color: color-mix(in srgb, var(--color-border) 70%, transparent);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.nav-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 35%, transparent);
}

.nav-item:active {
  transform: translateY(0);
}

.nav-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: currentColor;
  opacity: 0.8;
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.nav-item:hover .nav-item__icon {
  opacity: 1;
}

.nav-item__label {
  line-height: 1;
}

.nav-item--active {
  color: var(--color-bg-surface);
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-hover) 100%);
  border-color: color-mix(in srgb, var(--color-accent) 60%, transparent);
  box-shadow:
    0 10px 22px -12px color-mix(in srgb, var(--color-accent) 75%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}
.nav-item--active:hover {
  color: var(--color-bg-surface);
  background: linear-gradient(135deg, var(--color-accent-hover) 0%, var(--color-accent) 100%);
  border-color: color-mix(in srgb, var(--color-accent) 60%, transparent);
  transform: translateY(-1px);
}
.nav-item--active .nav-item__icon {
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .nav-item,
  .nav-item__icon {
    transition: none;
  }
  .nav-item:hover {
    transform: none;
  }
}

/* ── Brand row right-side group ──────────────────────────── */
.brand-row__end {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* ── Theme toggle button ─────────────────────────────────── */
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
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.theme-toggle:hover {
  color: var(--color-text-primary);
  border-color: var(--color-accent);
  background: color-mix(in srgb, var(--color-accent-soft) 80%, transparent);
  transform: scale(1.08);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 15%, transparent);
}

.theme-toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 35%, transparent);
}

.theme-toggle:active {
  transform: scale(0.95);
}

.theme-toggle__icon {
  display: block;
  transition: transform 0.35s ease, opacity 0.2s ease;
}

/* ── Sun/moon icon rotation ───────────────────────────────── */
/* Sun ray animation when entering light state */
.theme-toggle--light .theme-toggle__icon {
  transform: rotate(0deg);
}
.theme-toggle--dark .theme-toggle__icon {
  transform: rotate(20deg);
}

@media (max-width: 640px) {
  .nav-item {
    padding: 0.5rem 0.75rem;
    font-size: 0.82rem;
  }
  .brand__logo {
    height: 1.6rem;
    max-width: 2.2rem;
  }
}
</style>
