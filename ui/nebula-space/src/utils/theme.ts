export type ThemeMode = 'light' | 'dark'
/** 设置里的选项：没存过（或选了跟随系统）就跟着系统走 */
export type ThemePreference = ThemeMode | 'system'

const THEME_KEY = 'nebula-space-theme'
const THEMES: ThemeMode[] = ['light', 'dark']

const isThemeMode = (value: string | null): value is ThemeMode => {
  return value !== null && THEMES.includes(value as ThemeMode)
}

export const getStoredTheme = (): ThemeMode | null => {
  const saved = window.localStorage.getItem(THEME_KEY)
  return isThemeMode(saved) ? saved : null
}

export const getThemePreference = (): ThemePreference => getStoredTheme() ?? 'system'

const media = () => window.matchMedia('(prefers-color-scheme: dark)')

export const getSystemTheme = (): ThemeMode => {
  return media().matches ? 'dark' : 'light'
}

export const applyTheme = (theme: ThemeMode): void => {
  document.documentElement.dataset.theme = theme
}

export const setTheme = (theme: ThemeMode): void => {
  applyTheme(theme)
  window.localStorage.setItem(THEME_KEY, theme)
}

/** 回到跟随系统：清掉本机记住的主题 */
export const followSystemTheme = (): ThemeMode => {
  window.localStorage.removeItem(THEME_KEY)
  const theme = getSystemTheme()
  applyTheme(theme)
  return theme
}

/** 跟随系统时，系统切换深浅色也跟着切 */
export const watchSystemTheme = (onChange: (theme: ThemeMode) => void) => {
  media().addEventListener('change', () => {
    if (getStoredTheme()) return
    const theme = getSystemTheme()
    applyTheme(theme)
    onChange(theme)
  })
}

export const initTheme = (): ThemeMode => {
  const theme = getStoredTheme() ?? getSystemTheme()
  applyTheme(theme)
  return theme
}
