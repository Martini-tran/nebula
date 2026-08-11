import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setTheme, type ThemeMode } from '../utils/theme'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeMode>(
    (document.documentElement.dataset.theme as ThemeMode) ?? 'light',
  )

  const isDark = computed(() => currentTheme.value === 'dark')

  function apply(theme: ThemeMode) {
    document.documentElement.classList.add('theme-transitioning')
    currentTheme.value = theme
    setTheme(theme)
    setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), 400)
  }

  function toggle() {
    apply(isDark.value ? 'light' : 'dark')
  }

  return { currentTheme, isDark, apply, toggle }
})
