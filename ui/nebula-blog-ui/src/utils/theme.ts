export type ThemeMode = 'light' | 'dark' | 'ocean'

const THEME_KEY = 'stilldoing-theme'
const THEMES: ThemeMode[] = ['light', 'dark', 'ocean']

const isThemeMode = (value: string | null): value is ThemeMode => {
  return value !== null && THEMES.includes(value as ThemeMode)
}

export const getStoredTheme = (): ThemeMode | null => {
  const saved = window.localStorage.getItem(THEME_KEY)
  return isThemeMode(saved) ? saved : null
}

export const getSystemTheme = (): ThemeMode => {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export const applyTheme = (theme: ThemeMode): void => {
  document.documentElement.dataset.theme = theme
}

export const setTheme = (theme: ThemeMode): void => {
  applyTheme(theme)
  window.localStorage.setItem(THEME_KEY, theme)
}

export const initTheme = (): ThemeMode => {
  const theme = getStoredTheme() ?? getSystemTheme()
  applyTheme(theme)
  return theme
}
