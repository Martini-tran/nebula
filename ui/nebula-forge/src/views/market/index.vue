<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import PluginCard from './components/PluginCard.vue'
import { fetchCategories, fetchPlugins } from '../../api/plugins'
import {
  PLUGIN_TYPE_LABEL,
  PRICING_TYPE_LABEL,
  SORT_OPTIONS,
} from '../../types/plugin'
import type { PluginCategory, PluginListItem } from '../../types/plugin'

const router = useRouter()

const PAGE_SIZE = 12

const plugins = ref<PluginListItem[]>([])
const categories = ref<PluginCategory[]>([])
const loading = ref(true)
const error = ref(false)
const total = ref(0)

const filters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  type: '' as string,
  pricingType: undefined as number | undefined,
  sort: 'featured',
  pageNum: 1,
})

// 关键词输入与实际查询解耦，做防抖
const keywordInput = ref('')
let keywordTimer: ReturnType<typeof setTimeout> | undefined

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / PAGE_SIZE)),
)

const pricingOptions = Object.entries(PRICING_TYPE_LABEL).map(([value, label]) => ({
  value: Number(value),
  label,
}))

const typeOptions = Object.entries(PLUGIN_TYPE_LABEL).map(([value, label]) => ({
  value,
  label,
}))

const load = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await fetchPlugins({
      pageNum: filters.pageNum,
      pageSize: PAGE_SIZE,
      keyword: filters.keyword || undefined,
      categoryId: filters.categoryId,
      type: filters.type || undefined,
      pricingType: filters.pricingType,
      sort: filters.sort,
    })
    plugins.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch {
    error.value = true
    plugins.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 筛选条件变化（关键词/分类/类型/定价/排序）重置到第 1 页
watch(
  () => [filters.keyword, filters.categoryId, filters.type, filters.pricingType, filters.sort],
  () => {
    filters.pageNum = 1
    load()
  },
)

// 翻页单独触发
watch(
  () => filters.pageNum,
  () => load(),
)

watch(keywordInput, (value) => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    filters.keyword = value.trim()
  }, 350)
})

const submitKeyword = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  filters.keyword = keywordInput.value.trim()
}

const selectCategory = (id?: number) => {
  filters.categoryId = id
}

const goPage = (page: number) => {
  if (page < 1 || page > totalPages.value || page === filters.pageNum) return
  filters.pageNum = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const openDetail = (plugin: PluginListItem) => {
  router.push(`/market/${plugin.id}`)
}

const resetFilters = () => {
  keywordInput.value = ''
  filters.keyword = ''
  filters.categoryId = undefined
  filters.type = ''
  filters.pricingType = undefined
  filters.sort = 'featured'
}

onMounted(async () => {
  try {
    categories.value = await fetchCategories()
  } catch {
    categories.value = []
  }
  await load()
})

onBeforeUnmount(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})
</script>

<template>
  <div class="market">
    <header class="market__hero">
      <div class="market__hero-inner">
        <p class="market__eyebrow">插件市场</p>
        <h1 class="market__title">发现并安装插件</h1>
        <p class="market__sub">
          浏览社区与官方插件，扩展启动器的能力。搜索、按分类筛选，一键下载安装。
        </p>

        <div class="market__search">
          <Icon icon="lucide:search" class="market__search-icon" />
          <input
            v-model="keywordInput"
            class="market__search-input"
            type="search"
            placeholder="搜索插件名称、标识或简介…"
            @keyup.enter="submitKeyword"
          />
        </div>
      </div>
    </header>

    <section class="market__body">
      <!-- 分类 chips -->
      <div class="market__chips">
        <button
          type="button"
          class="chip"
          :class="{ 'chip--active': filters.categoryId === undefined }"
          @click="selectCategory(undefined)"
        >
          全部
        </button>
        <button
          v-for="cat in categories"
          :key="cat.id"
          type="button"
          class="chip"
          :class="{ 'chip--active': filters.categoryId === cat.id }"
          @click="selectCategory(cat.id)"
        >
          {{ cat.name }}
        </button>
      </div>

      <!-- 工具栏 -->
      <div class="market__toolbar">
        <div class="market__selects">
          <label class="select">
            <span class="select__label">类型</span>
            <select v-model="filters.type">
              <option value="">全部</option>
              <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </label>

          <label class="select">
            <span class="select__label">定价</span>
            <select v-model="filters.pricingType">
              <option :value="undefined">全部</option>
              <option v-for="opt in pricingOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </label>
        </div>

        <div class="market__sort">
          <button
            v-for="opt in SORT_OPTIONS"
            :key="opt.value"
            type="button"
            class="sort-btn"
            :class="{ 'sort-btn--active': filters.sort === opt.value }"
            @click="filters.sort = opt.value"
          >
            <Icon :icon="opt.icon" />
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- 列表 -->
      <p v-if="loading" class="market__state">
        <Icon icon="lucide:loader-circle" class="spin" />
        正在加载插件…
      </p>

      <div v-else-if="error" class="market__state market__state--error">
        <Icon icon="lucide:alert-triangle" />
        加载失败，请稍后重试。
        <button type="button" class="market__retry" @click="load">重试</button>
      </div>

      <div v-else-if="!plugins.length" class="market__state">
        <Icon icon="lucide:inbox" />
        没有符合条件的插件。
        <button type="button" class="market__retry" @click="resetFilters">重置筛选</button>
      </div>

      <template v-else>
        <div class="market__grid">
          <PluginCard
            v-for="plugin in plugins"
            :key="plugin.id"
            :plugin="plugin"
            @click="openDetail(plugin)"
          />
        </div>

        <!-- 分页 -->
        <nav v-if="totalPages > 1" class="pager" aria-label="分页">
          <button
            type="button"
            class="pager__btn"
            :disabled="filters.pageNum <= 1"
            @click="goPage(filters.pageNum - 1)"
          >
            <Icon icon="lucide:chevron-left" />
            上一页
          </button>
          <span class="pager__info">
            第 {{ filters.pageNum }} / {{ totalPages }} 页 · 共 {{ total }} 个
          </span>
          <button
            type="button"
            class="pager__btn"
            :disabled="filters.pageNum >= totalPages"
            @click="goPage(filters.pageNum + 1)"
          >
            下一页
            <Icon icon="lucide:chevron-right" />
          </button>
        </nav>
      </template>
    </section>
  </div>
</template>

<style scoped lang="scss">
.market__hero {
  background: var(--color-bg-soft);
  border-bottom: 1px solid var(--color-border);
}

.market__hero-inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(2.5rem, 6vw, 4.5rem) var(--space-page-x) clamp(2rem, 4vw, 3rem);
}

.market__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.market__title {
  margin-top: 0.6rem;
  font-size: clamp(2rem, 4.5vw, 3rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.market__sub {
  margin-top: 0.8rem;
  max-width: 38rem;
  color: var(--color-text-secondary);
  font-size: 1.05rem;
}

.market__search {
  position: relative;
  margin-top: 1.6rem;
  max-width: 34rem;
}

.market__search-icon {
  position: absolute;
  left: 0.9rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1.15rem;
  height: 1.15rem;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.market__search-input {
  width: 100%;
  padding: 0.8rem 1rem 0.8rem 2.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 1rem;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.market__search-input:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.market__body {
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(1.75rem, 4vw, 2.75rem) var(--space-page-x) clamp(3rem, 6vw, 5rem);
}

.market__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.chip {
  padding: 0.4rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.chip:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.chip--active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.market__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.25rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--color-border);
}

.market__selects {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.select {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.select select {
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.9rem;
  cursor: pointer;
}

.select select:focus {
  outline: none;
  border-color: var(--color-brand);
}

.market__sort {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.75rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.sort-btn:hover {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.sort-btn svg {
  width: 1rem;
  height: 1rem;
}

.sort-btn--active {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.market__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr));
  gap: 1.1rem;
  margin-top: 1.5rem;
}

.market__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 3rem;
  color: var(--color-text-secondary);
  font-size: 1rem;
}

.market__state svg {
  width: 1.3rem;
  height: 1.3rem;
}

.market__state--error {
  color: #e05252;
}

.market__retry {
  margin-left: 0.5rem;
  padding: 0.3rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-brand);
  font-weight: 600;
  cursor: pointer;
}

.market__retry:hover {
  border-color: var(--color-brand);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 2.5rem;
}

.pager__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.5rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.pager__btn:hover:not(:disabled) {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.pager__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pager__btn svg {
  width: 1.05rem;
  height: 1.05rem;
}

.pager__info {
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .market__toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
