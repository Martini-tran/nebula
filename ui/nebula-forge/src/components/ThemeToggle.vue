<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useThemeStore } from '../stores/theme'
import type { ThemeMode } from '../utils/theme'

const themeStore = useThemeStore()

const order: ThemeMode[] = ['light', 'dark', 'ocean']

const meta: Record<ThemeMode, { icon: string; label: string }> = {
  light: { icon: 'lucide:sun', label: '浅色' },
  dark: { icon: 'lucide:moon', label: '深色' },
  ocean: { icon: 'lucide:waves', label: '海洋' },
}

const current = computed(() => meta[themeStore.currentTheme])

const cycle = () => {
  const index = order.indexOf(themeStore.currentTheme)
  const next = order[(index + 1) % order.length]
  themeStore.apply(next)
}
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :title="`当前：${current.label}主题，点击切换`"
    :aria-label="`切换主题，当前为${current.label}`"
    @click="cycle"
  >
    <Icon :icon="current.icon" class="theme-toggle__icon" />
  </button>
</template>

<style scoped lang="scss">
.theme-toggle {
  display: inline-grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.theme-toggle:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
  transform: translateY(-1px);
}

.theme-toggle__icon {
  width: 1.2rem;
  height: 1.2rem;
}
</style>
