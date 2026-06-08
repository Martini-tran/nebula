<template>
  <div class="series-shell">
    <div
      class="series-layout"
      :class="{ 'series-layout--collapsed': sidebarCollapsed }"
    >
      <aside class="series-sidebar" aria-label="系列导航">
        <!-- 折叠/展开按钮（仅 ≥ 980px 时可见） -->
        <button
          type="button"
          class="sidebar-toggle"
          :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
          :title="sidebarCollapsed ? '展开导航' : '收起导航'"
          @click="toggleSidebar"
        >
          <Icon
            :icon="
              sidebarCollapsed
                ? 'lucide:panel-left-open'
                : 'lucide:panel-left-close'
            "
          />
        </button>

        <Transition name="sidebar-fade" mode="out-in">
          <!-- 收起态：仅图标条 -->
          <div
            v-if="sidebarCollapsed"
            key="collapsed"
            class="sidebar-collapsed"
          >
            <RouterLink to="/" class="sidebar-mini" title="首页">
              <img
                :src="isDark ? logoDark : logoLight"
                alt="orccode"
                class="sidebar-mini__logo"
              />
            </RouterLink>
            <RouterLink
              to="/series"
              class="sidebar-mini sidebar-mini--icon"
              :class="{ 'sidebar-mini--active': route.path === '/series' }"
              title="全部系列"
            >
              <Icon icon="lucide:layout-grid" />
            </RouterLink>
            <span
              v-if="filteredItems.length"
              class="sidebar-collapsed__divider"
              aria-hidden="true"
            />
            <RouterLink
              v-for="(item, idx) in filteredItems"
              :key="item.slug"
              :to="`/series/${item.slug}`"
              :title="item.name"
              class="sidebar-mini sidebar-mini--num"
              :class="{
                'sidebar-mini--active': route.params.slug === item.slug,
              }"
            >
              <span>{{ String(idx + 1).padStart(2, '0') }}</span>
            </RouterLink>
          </div>

          <!-- 展开态：完整内容 -->
          <div v-else key="expanded" class="sidebar-full">
            <!-- 品牌区 -->
            <RouterLink to="/" class="brand">
              <img
                :src="isDark ? logoDark : logoLight"
                alt="orccode"
                class="brand__logo"
              />
              <div class="brand__text">
                <span class="brand__kicker">orccode Series</span>
                <span class="brand__title">学习路径</span>
              </div>
            </RouterLink>

            <!-- 全部系列入口 -->
            <RouterLink
              to="/series"
              class="overview"
              :class="{ 'overview--active': route.path === '/series' }"
            >
              <span class="overview__icon" aria-hidden="true">
                <Icon icon="lucide:layout-grid" />
              </span>
              <div class="overview__text">
                <span class="overview__title">全部系列</span>
                <span class="overview__sub">{{ items.length }} 个系列 · {{ totalArticles }} 篇</span>
              </div>
              <Icon icon="lucide:arrow-right" class="overview__arrow" />
            </RouterLink>

            <!-- 状态筛选（mini chip） -->
            <div v-if="items.length" class="status-pills" role="tablist">
              <button
                type="button"
                role="tab"
                class="status-pill"
                :class="{ 'status-pill--active': statusFilter === 'all' }"
                @click="statusFilter = 'all'"
              >
                全部
                <span class="status-pill__num">{{ items.length }}</span>
              </button>
              <button
                type="button"
                role="tab"
                class="status-pill"
                :class="{ 'status-pill--active': statusFilter === 'ongoing' }"
                @click="statusFilter = 'ongoing'"
              >
                连载
                <span class="status-pill__num">{{ ongoingCount }}</span>
              </button>
              <button
                type="button"
                role="tab"
                class="status-pill"
                :class="{ 'status-pill--active': statusFilter === 'finished' }"
                @click="statusFilter = 'finished'"
              >
                完结
                <span class="status-pill__num">{{ finishedCount }}</span>
              </button>
            </div>

            <!-- 系列列表 -->
            <nav class="series-nav" aria-label="系列列表">
              <header class="series-nav__header">
                <span>系列</span>
                <span class="series-nav__count">{{ filteredItems.length }}</span>
              </header>
              <ul class="series-nav__list">
                <li
                  v-for="(item, idx) in filteredItems"
                  :key="item.slug"
                  class="series-nav__item"
                  :class="{
                    'series-nav__item--active': route.params.slug === item.slug,
                  }"
                >
                  <RouterLink :to="`/series/${item.slug}`" class="series-nav__btn">
                    <span class="series-nav__index" aria-hidden="true">
                      {{ String(idx + 1).padStart(2, '0') }}
                    </span>
                    <span class="series-nav__main">
                      <span class="series-nav__name">{{ item.name }}</span>
                      <span class="series-nav__meta">
                        {{ item.article_count }} 篇
                        <span v-if="item.is_finished" class="series-nav__pill">完结</span>
                      </span>
                    </span>
                  </RouterLink>
                </li>
              </ul>
              <p
                v-if="!loading && filteredItems.length === 0"
                class="series-nav__empty"
              >
                暂无内容
              </p>
            </nav>
          </div>
        </Transition>
      </aside>

      <main class="series-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchSeriesList, type SeriesListItem } from '../api/series'
import { useThemeStore } from '../stores/theme'
import logoLight from '../assets/logo-light.png'
import logoDark from '../assets/logo-dark.png'

type StatusFilter = 'all' | 'ongoing' | 'finished'

const STORAGE_KEY = 'series-nav:sidebar-collapsed'

const route = useRoute()
const items = ref<SeriesListItem[]>([])
const loading = ref(false)
const statusFilter = ref<StatusFilter>('all')

const sidebarCollapsed = ref<boolean>(
  typeof window !== 'undefined' &&
    window.localStorage?.getItem(STORAGE_KEY) === '1',
)

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
  if (typeof window !== 'undefined') {
    window.localStorage?.setItem(
      STORAGE_KEY,
      sidebarCollapsed.value ? '1' : '0',
    )
  }
}

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

const totalArticles = computed(() =>
  items.value.reduce((sum, s) => sum + (s.article_count ?? 0), 0),
)

const finishedCount = computed(
  () => items.value.filter((s) => s.is_finished).length,
)

const ongoingCount = computed(
  () => items.value.filter((s) => !s.is_finished).length,
)

const filteredItems = computed(() => {
  if (statusFilter.value === 'all') return items.value
  if (statusFilter.value === 'ongoing')
    return items.value.filter((s) => !s.is_finished)
  return items.value.filter((s) => s.is_finished)
})

onMounted(async () => {
  loading.value = true
  try {
    const result = await fetchSeriesList({ limit: 50 })
    items.value = result.items ?? []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.series-shell {
  min-height: 100vh;
  color: var(--color-text-primary);
  background: var(--color-bg-canvas);
  font-family: var(--font-sans), serif;
}

.series-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
  padding: var(--space-page-y) var(--space-page-x);
}

@media (min-width: 980px) {
  .series-layout {
    grid-template-columns: 260px minmax(0, 1fr);
    align-items: start;
    transition: grid-template-columns 0.25s ease;
  }

  .series-layout--collapsed {
    grid-template-columns: 56px minmax(0, 1fr);
  }
}

.series-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 980px) {
  .series-sidebar {
    position: sticky;
    top: var(--space-page-y);
    max-height: calc(100vh - (var(--space-page-y) * 2));
    overflow-y: auto;
    padding-right: 4px;
    scrollbar-width: thin;
  }
}

.series-layout--collapsed .series-sidebar {
  gap: 0.5rem;
  align-items: center;
  padding-right: 0;
}

/* ── 折叠按钮 ── */
.sidebar-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 1.85rem;
  height: 1.85rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  border-radius: 0.5rem;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.sidebar-toggle:hover {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  background: var(--color-bg-soft);
}

.sidebar-toggle :deep(svg) {
  width: 1rem;
  height: 1rem;
}

@media (min-width: 980px) {
  .sidebar-toggle {
    display: inline-flex;
    align-self: flex-end;
    flex-shrink: 0;
  }
  .series-layout--collapsed .sidebar-toggle {
    align-self: center;
  }
}

/* ── 内容容器（用于切换动画） ── */
.sidebar-full,
.sidebar-collapsed {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 100%;
}

.sidebar-collapsed {
  align-items: center;
  gap: 0.4rem;
}

.sidebar-collapsed__divider {
  width: 1.6rem;
  height: 1px;
  background: var(--color-border);
  margin: 0.25rem 0;
}

/* 收起态条目 */
.sidebar-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.6rem;
  text-decoration: none;
  color: inherit;
  transition: background 0.15s, color 0.15s, border-color 0.15s, transform 0.15s;
}

.sidebar-mini:hover {
  background: var(--color-bg-soft);
}

.sidebar-mini__logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 0.55rem;
  box-shadow:
    0 4px 12px -4px color-mix(in srgb, var(--color-accent) 35%, transparent),
    0 0 0 1px color-mix(in srgb, var(--color-border) 80%, transparent);
}

.sidebar-mini--icon,
.sidebar-mini--num {
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-muted);
}

.sidebar-mini--icon:hover,
.sidebar-mini--num:hover {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
}

.sidebar-mini--icon :deep(svg) {
  width: 1.05rem;
  height: 1.05rem;
}

.sidebar-mini--num {
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
  font-family: var(--font-family-mono, ui-monospace, "SF Mono", Menlo, monospace);
}

.sidebar-mini--active {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 60%, var(--color-border));
  background: color-mix(in srgb, var(--color-accent) 10%, var(--color-bg-surface));
}

/* ── 切换动画 ── */
.sidebar-fade-enter-active,
.sidebar-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.22s ease;
}

.sidebar-fade-enter-from {
  opacity: 0;
  transform: translateX(-6px);
}

.sidebar-fade-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}

/* ── 品牌 ── */
.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.5rem 0.25rem;
  text-decoration: none;
  color: inherit;
  transition: opacity 0.15s;
}

.brand:hover {
  opacity: 0.85;
}

.brand__logo {
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.6rem;
  object-fit: contain;
  flex-shrink: 0;
  box-shadow:
    0 4px 12px -4px color-mix(in srgb, var(--color-accent) 35%, transparent),
    0 0 0 1px color-mix(in srgb, var(--color-border) 80%, transparent);
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand__kicker {
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.brand__title {
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.2;
}

/* ── 全部系列入口 ── */
.overview {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--color-accent) 14%, transparent), transparent 60%),
    var(--color-bg-surface);
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.overview:hover {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  box-shadow: 0 6px 18px color-mix(in srgb, var(--color-accent) 12%, transparent);
  transform: translateY(-1px);
}

.overview--active {
  border-color: color-mix(in srgb, var(--color-accent) 60%, var(--color-border));
  background:
    linear-gradient(135deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 70%, #6366f1));
  color: #fff;
}

.overview--active:hover {
  transform: none;
  box-shadow: 0 6px 18px color-mix(in srgb, var(--color-accent) 30%, transparent);
}

.overview__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  color: var(--color-accent);
  flex-shrink: 0;
}

.overview--active .overview__icon {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.overview__icon :deep(svg) {
  width: 1.05rem;
  height: 1.05rem;
}

.overview__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.overview__title {
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.overview--active .overview__title {
  color: #fff;
}

.overview__sub {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.overview--active .overview__sub {
  color: rgba(255, 255, 255, 0.85);
}

.overview__arrow {
  width: 1rem;
  height: 1rem;
  color: var(--color-text-muted);
  transition: transform 0.15s, color 0.15s;
  flex-shrink: 0;
}

.overview:hover .overview__arrow {
  transform: translateX(3px);
  color: var(--color-accent);
}

.overview--active .overview__arrow,
.overview--active:hover .overview__arrow {
  color: rgba(255, 255, 255, 0.85);
}

/* ── 状态筛选 ── */
.status-pills {
  display: flex;
  gap: 0.3rem;
  flex-wrap: wrap;
  padding: 0 0.15rem;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 999px;
  padding: 0.3rem 0.65rem;
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.status-pill:hover {
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
}

.status-pill--active,
.status-pill--active:hover {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
  border-color: var(--color-text-primary);
}

.status-pill__num {
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.status-pill--active .status-pill__num {
  color: color-mix(in srgb, var(--color-bg-surface) 80%, transparent);
}

/* ── 系列列表 ── */
.series-nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.series-nav__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0 0.15rem 0.35rem;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.series-nav__count {
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  text-transform: none;
}

.series-nav__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.series-nav__btn {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.55rem 0.55rem 0.85rem;
  border-left: 2px solid transparent;
  border-radius: 0 0.4rem 0.4rem 0;
  text-decoration: none;
  color: var(--color-text-secondary);
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.series-nav__btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-left-color: color-mix(in srgb, var(--color-accent) 40%, transparent);
}

.series-nav__item--active .series-nav__btn {
  background: color-mix(in srgb, var(--color-accent) 8%, transparent);
  color: var(--color-text-primary);
  border-left-color: var(--color-accent);
}

.series-nav__index {
  flex-shrink: 0;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-family-mono, ui-monospace, "SF Mono", Menlo, monospace);
}

.series-nav__item--active .series-nav__index {
  color: var(--color-accent);
}

.series-nav__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.series-nav__name {
  font-size: 0.83rem;
  font-weight: 600;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-nav__meta {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.68rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.series-nav__pill {
  font-size: 0.58rem;
  font-weight: 700;
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  background: rgba(22, 163, 74, 0.12);
  color: #16a34a;
  letter-spacing: 0.02em;
}

.series-nav__empty {
  margin: 0;
  padding: 0.5rem 0.85rem;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.series-main {
  min-width: 0;
}

@media (prefers-reduced-motion: reduce) {
  .overview,
  .overview__arrow,
  .series-nav__btn {
    transition: none;
  }
  .overview:hover {
    transform: none;
  }
}
</style>
