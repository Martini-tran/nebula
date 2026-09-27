import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { followSystemTheme, getThemePreference, setTheme, watchSystemTheme, type ThemeMode, type ThemePreference } from '../utils/theme'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeMode>(
    (document.documentElement.dataset.theme as ThemeMode) ?? 'light',
  )
  const preference = ref<ThemePreference>(getThemePreference())

  const isDark = computed(() => currentTheme.value === 'dark')

  watchSystemTheme((theme) => {
    currentTheme.value = theme
  })

  const transition = (run: () => void) => {
    document.documentElement.classList.add('theme-transitioning')
    run()
    setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), 400)
  }

  function apply(theme: ThemeMode) {
    transition(() => {
      currentTheme.value = theme
      preference.value = theme
      setTheme(theme)
    })
  }

  function prefer(value: ThemePreference) {
    if (value !== 'system') return apply(value)
    transition(() => {
      preference.value = 'system'
      currentTheme.value = followSystemTheme()
    })
  }

  function toggle() {
    apply(isDark.value ? 'light' : 'dark')
  }

  return { currentTheme, preference, isDark, apply, prefer, toggle }
})
