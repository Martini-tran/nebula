import type { Component } from 'vue'

const viewModules = import.meta.glob('../views/*/index.vue') as Record<string, () => Promise<Component>>

type NavMeta = {
  label: string
  order: number
  isHomeNav: boolean
  requiresLogin: boolean
  icon: string
}

const navMetaMap: Record<string, NavMeta> = {
  home:       { label: '首页',       order: 0, isHomeNav: true, requiresLogin: false, icon: 'lucide:house' },
  articles:   { label: '文章',       order: 1, isHomeNav: true, requiresLogin: false, icon: 'lucide:newspaper' },
  essays:     { label: '随笔',       order: 2, isHomeNav: true, requiresLogin: false, icon: 'lucide:pen-line' },
  travel:     { label: '旅行',       order: 3, isHomeNav: true, requiresLogin: false, icon: 'lucide:map' },
  reviews:    { label: '中转站测评', order: 4, isHomeNav: true, requiresLogin: false, icon: 'lucide:route' },
  handbook:   { label: '宝典',       order: 5, isHomeNav: true, requiresLogin: false, icon: 'lucide:book-open-check' },
  series:     { label: '系列',       order: 6, isHomeNav: true, requiresLogin: false, icon: 'lucide:layers' },
}

const hiddenViewKeys = new Set(['reviews', 'series', 'series-detail'])

const toTitle = (value: string) => value.charAt(0).toUpperCase() + value.slice(1)

export type NavLinkItem = {
  key: string
  label: string
  icon: string
  to: string
  folder: string
  isHomeNav: boolean
  requiresLogin: boolean
  order: number
}

export type NavItem = NavLinkItem & {
  component: () => Promise<Component>
}

const parseKeyFromPath = (path: string) => {
  const match = path.match(/\.\.\/views\/([^/]+)\/index\.vue$/)
  return match?.[1] ?? null
}

export const navItems: NavItem[] = Object.entries(viewModules)
  .map(([path, component]) => {
    const key = parseKeyFromPath(path)

    if (!key || hiddenViewKeys.has(key)) {
      return null
    }

    const meta = navMetaMap[key] ?? {
      label: toTitle(key),
      order: 999,
      isHomeNav: true,
      requiresLogin: false,
      icon: 'lucide:circle',
    }

    return {
      key,
      label: meta.label,
      icon: meta.icon,
      to: `/${key}`,
      folder: `views/${key}`,
      isHomeNav: meta.isHomeNav,
      requiresLogin: meta.requiresLogin,
      order: meta.order,
      component,
    }
  })
  .filter((item): item is NavItem => item !== null)
  .sort((a, b) => a.order - b.order)

const standaloneNavItems: NavLinkItem[] = [
  {
    key: 'reviews',
    label: navMetaMap.reviews.label,
    icon: navMetaMap.reviews.icon,
    to: '/reviews',
    folder: 'views/reviews',
    isHomeNav: navMetaMap.reviews.isHomeNav,
    requiresLogin: navMetaMap.reviews.requiresLogin,
    order: navMetaMap.reviews.order,
  },
  {
    key: 'series',
    label: navMetaMap.series.label,
    icon: navMetaMap.series.icon,
    to: '/series',
    folder: 'views/series',
    isHomeNav: navMetaMap.series.isHomeNav,
    requiresLogin: navMetaMap.series.requiresLogin,
    order: navMetaMap.series.order,
  },
]

export const homeNavItems: NavLinkItem[] = [
  ...navItems.filter((item) => item.isHomeNav),
  ...standaloneNavItems.filter((item) => item.isHomeNav),
].sort((a, b) => a.order - b.order)
