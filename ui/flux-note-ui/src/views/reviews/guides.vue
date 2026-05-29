<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  fetchRelayCompare,
  fetchRelayModels,
  fetchRelayPackages,
  fetchRelayPackageTypes,
  type FetchCompareParams,
  type RelayCompareRow,
  type RelayModel,
  type RelayPackageType,
  type RelayProviderPackage,
} from '../../api/aiRelay'

type SortKey = NonNullable<FetchCompareParams['sortBy']>

const limitTypeOptions: { value: number; label: string }[] = [
  { value: 1, label: '总额度' },
  { value: 2, label: '每日' },
  { value: 3, label: '每周' },
  { value: 4, label: '每月' },
  { value: 5, label: '单次' },
]

const sortOptions: { key: SortKey; label: string; needModel?: boolean }[] = [
  { key: 'recommend', label: '推荐优先' },
  { key: 'input_price', label: '输入单价升序', needModel: true },
  { key: 'output_price', label: '输出单价升序', needModel: true },
  { key: 'quota', label: '额度大者优先' },
]

const packageTypeFilter = ref<string>('')
const limitTypeFilter = ref<number | ''>('')
const modelId = ref<number | ''>('')
const sortBy = ref<SortKey>('recommend')
const keyword = ref('')
const keywordInput = ref('')

const pageNum = ref(1)
const pageSize = ref(15)
const total = ref(0)

const list = ref<RelayCompareRow[]>([])
const loading = ref(false)
const errorMsg = ref('')

const models = ref<RelayModel[]>([])
const packageTypes = ref<RelayPackageType[]>([])

// ===== 左侧：套餐列表 =====
const allPackages = ref<RelayProviderPackage[]>([])
const packagesLoading = ref(false)
const packageSearch = ref('')
const expandedPackageIds = ref<Set<number>>(new Set())
const filterByPackage = ref<RelayProviderPackage | null>(null)

const totalPages = computed(() =>
  total.value > 0 ? Math.max(1, Math.ceil(total.value / pageSize.value)) : 1,
)

const hasModel = computed(() => modelId.value !== '' && modelId.value !== null)

const filteredPackages = computed(() => {
  const kw = packageSearch.value.trim().toLowerCase()
  let arr = allPackages.value
  if (packageTypeFilter.value) {
    arr = arr.filter((p) => p.package_type_code === packageTypeFilter.value)
  }
  if (kw) {
    arr = arr.filter((p) =>
      [p.name, p.provider_name, p.description]
        .filter(Boolean)
        .some((s) => String(s).toLowerCase().includes(kw)),
    )
  }
  return arr
})

function isExpanded(id: number) {
  return expandedPackageIds.value.has(id)
}

function toggleExpand(id: number) {
  const next = new Set(expandedPackageIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedPackageIds.value = next
}

function applyPackageFilter(pkg: RelayProviderPackage) {
  filterByPackage.value = pkg
  pageNum.value = 1
  loadList()
}

function clearPackageFilter() {
  filterByPackage.value = null
  pageNum.value = 1
  loadList()
}

function isFilteringBy(id: number) {
  return filterByPackage.value?.id === id
}

function formatNumber(input?: number | null, fractionDigits = 4) {
  if (input == null) return '-'
  const n = Number(input)
  if (Number.isNaN(n)) return '-'
  return n.toFixed(fractionDigits)
}

function formatQuota(amount?: number | null, unit?: string | null) {
  if (amount == null) return '-'
  const num = Number(amount)
  if (Number.isNaN(num)) return '-'
  let display: string
  if (num >= 1_000_000) display = `${(num / 1_000_000).toFixed(2)}M`
  else if (num >= 1_000) display = `${(num / 1_000).toFixed(1)}K`
  else display = num.toString()
  return unit ? `${display} ${unit}` : display
}

function formatMoney(amount?: number | null, currency?: string | null) {
  if (amount == null) return '-'
  const symbol = currency === 'USD' ? '$' : '¥'
  return `${symbol}${Number(amount).toFixed(2)}`
}

function effectivePrice(price?: number | null, multiplier?: number | null) {
  if (price == null) return null
  const mult = multiplier == null ? 1 : Number(multiplier)
  return Number(price) * (Number.isNaN(mult) ? 1 : mult)
}

async function loadPackages() {
  packagesLoading.value = true
  try {
    const res = await fetchRelayPackages({ pageNum: 1, pageSize: 200 })
    allPackages.value = res.records ?? []
  } catch (e) {
    console.warn('load packages failed', e)
  } finally {
    packagesLoading.value = false
  }
}

async function loadOptions() {
  try {
    const [modelPage, types] = await Promise.all([
      fetchRelayModels({ pageNum: 1, pageSize: 100 }),
      fetchRelayPackageTypes(),
    ])
    models.value = modelPage.records ?? []
    packageTypes.value = types ?? []
  } catch (e) {
    console.warn('load compare options failed', e)
  }
}

async function loadList() {
  loading.value = true
  errorMsg.value = ''
  try {
    const pkg = filterByPackage.value
    const res = await fetchRelayCompare({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      modelId: modelId.value === '' ? undefined : modelId.value,
      providerId: pkg?.provider_id,
      packageTypeCode:
        pkg?.package_type_code || packageTypeFilter.value || undefined,
      limitType:
        limitTypeFilter.value === '' ? undefined : Number(limitTypeFilter.value),
      sortBy: sortBy.value,
      keyword: pkg?.name || keyword.value || undefined,
    })
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function applySearch() {
  keyword.value = keywordInput.value.trim()
  pageNum.value = 1
  loadList()
}

function changeSort(key: SortKey) {
  if (sortBy.value === key) return
  sortBy.value = key
  pageNum.value = 1
  loadList()
}

function changePage(next: number) {
  if (next < 1 || next > totalPages.value || next === pageNum.value) return
  pageNum.value = next
  loadList()
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function resetFilters() {
  packageTypeFilter.value = ''
  limitTypeFilter.value = ''
  modelId.value = ''
  sortBy.value = 'recommend'
  keyword.value = ''
  keywordInput.value = ''
  filterByPackage.value = null
  packageSearch.value = ''
  pageNum.value = 1
  loadList()
}

watch([modelId, packageTypeFilter, limitTypeFilter], () => {
  pageNum.value = 1
  if (
    !hasModel.value &&
    (sortBy.value === 'input_price' || sortBy.value === 'output_price')
  ) {
    sortBy.value = 'recommend'
  }
  loadList()
})

onMounted(async () => {
  await Promise.all([loadOptions(), loadPackages()])
  loadList()
})
</script>

<template>
  <section class="compare-page">
    <header class="hero">
      <p class="eyebrow">Buyer&apos;s Guide</p>
      <h1>比价选站</h1>
      <p class="lead">
        左侧浏览所有套餐，点击展开可见该套餐支持的模型、消耗倍率与每百万 token 单价。
        右侧表格按套餐限额维度横向比价，可与左侧任意套餐联动过滤。
      </p>
    </header>

    <div class="layout">
      <!-- 左侧：套餐列表 -->
      <aside class="package-panel">
        <div class="panel-head">
          <span class="panel-title">所有套餐</span>
          <span class="panel-meta">{{ filteredPackages.length }} 个</span>
        </div>

        <input
          v-model="packageSearch"
          class="panel-search"
          placeholder="搜索套餐 / 中转站"
          type="search"
        />

        <div v-if="filterByPackage" class="active-filter">
          已按套餐过滤：
          <strong>{{ filterByPackage.name }}</strong>
          <button class="link-btn" type="button" @click="clearPackageFilter">
            清除
          </button>
        </div>

        <ul class="package-list">
          <li v-if="packagesLoading" class="package-empty">加载中…</li>
          <li v-else-if="filteredPackages.length === 0" class="package-empty">
            暂无匹配
          </li>
          <li
            v-for="pkg in filteredPackages"
            v-else
            :key="pkg.id"
            class="package-item"
            :class="{ active: isFilteringBy(pkg.id), expanded: isExpanded(pkg.id) }"
          >
            <button
              class="package-row"
              type="button"
              :aria-expanded="isExpanded(pkg.id)"
              @click="toggleExpand(pkg.id)"
            >
              <span class="package-arrow" :class="{ open: isExpanded(pkg.id) }">▸</span>
              <div class="package-row-main">
                <div class="package-row-head">
                  <span class="pkg-name">{{ pkg.name }}</span>
                  <span class="pkg-price">
                    {{ formatMoney(pkg.price, pkg.currency) }}
                  </span>
                </div>
                <div class="package-row-sub">
                  <span class="pkg-provider">{{ pkg.provider_name || '—' }}</span>
                  <span v-if="pkg.package_type_name" class="pkg-type">
                    {{ pkg.package_type_name }}
                  </span>
                  <span v-if="pkg.recommended" class="pkg-tag">推荐</span>
                </div>
              </div>
            </button>

            <div v-show="isExpanded(pkg.id)" class="package-detail">
              <div v-if="pkg.description" class="pkg-desc">{{ pkg.description }}</div>

              <div v-if="!pkg.models || pkg.models.length === 0" class="pkg-empty">
                该套餐暂未配置模型
              </div>
              <table v-else class="model-mini-table">
                <thead>
                  <tr>
                    <th>模型</th>
                    <th class="num">倍率</th>
                    <th class="num">输入 /1M</th>
                    <th class="num">输出 /1M</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in pkg.models" :key="m.id">
                    <td>
                      <div class="mm-name">{{ m.model_name || m.model_code }}</div>
                      <div v-if="m.model_vendor" class="mm-vendor">
                        {{ m.model_vendor }}
                      </div>
                    </td>
                    <td class="num">
                      {{ m.consume_multiplier != null ? Number(m.consume_multiplier).toFixed(2) : '-' }}
                    </td>
                    <td class="num">
                      <div class="mm-price">
                        {{ formatNumber(effectivePrice(m.input_price_per_million_tokens, m.consume_multiplier)) }}
                      </div>
                      <div
                        v-if="
                          m.input_price_per_million_tokens != null &&
                          Number(m.consume_multiplier ?? 1) !== 1
                        "
                        class="mm-raw"
                      >
                        挂牌 {{ formatNumber(m.input_price_per_million_tokens) }}
                      </div>
                    </td>
                    <td class="num">
                      <div class="mm-price">
                        {{ formatNumber(effectivePrice(m.output_price_per_million_tokens, m.consume_multiplier)) }}
                      </div>
                      <div
                        v-if="
                          m.output_price_per_million_tokens != null &&
                          Number(m.consume_multiplier ?? 1) !== 1
                        "
                        class="mm-raw"
                      >
                        挂牌 {{ formatNumber(m.output_price_per_million_tokens) }}
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>

              <div class="pkg-actions">
                <button
                  v-if="!isFilteringBy(pkg.id)"
                  class="ghost-btn small"
                  type="button"
                  @click="applyPackageFilter(pkg)"
                >
                  按此套餐筛选右侧表
                </button>
                <button
                  v-else
                  class="ghost-btn small"
                  type="button"
                  @click="clearPackageFilter"
                >
                  清除筛选
                </button>
              </div>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 右侧：比价主体 -->
      <div class="compare-main">
        <div class="toolbar">
          <div class="filter-group">
            <label class="field">
              <span class="field-label">模型</span>
              <select v-model="modelId" class="select">
                <option value="">不限（仅看额度）</option>
                <option v-for="m in models" :key="m.id" :value="m.id">
                  {{ m.name || m.code }}<span v-if="m.model_vendor">（{{ m.model_vendor }}）</span>
                </option>
              </select>
            </label>

            <label class="field">
              <span class="field-label">套餐类型</span>
              <select v-model="packageTypeFilter" class="select">
                <option value="">不限</option>
                <option v-for="t in packageTypes" :key="t.id" :value="t.code">
                  {{ t.name }}
                </option>
              </select>
            </label>

            <label class="field">
              <span class="field-label">限额类型</span>
              <select v-model="limitTypeFilter" class="select">
                <option value="">不限</option>
                <option v-for="o in limitTypeOptions" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </label>

            <button class="ghost-btn" type="button" @click="resetFilters">重置</button>
          </div>

          <div class="search-bar">
            <input
              v-model="keywordInput"
              class="search-input"
              placeholder="搜索套餐 / 中转站"
              type="search"
              @keyup.enter="applySearch"
            />
            <button class="search-btn" type="button" @click="applySearch">搜索</button>
          </div>
        </div>

        <div class="sort-row">
          <span class="sort-label">排序</span>
          <button
            v-for="opt in sortOptions"
            :key="opt.key"
            :class="['chip', { active: sortBy === opt.key, disabled: opt.needModel && !hasModel }]"
            :disabled="opt.needModel && !hasModel"
            type="button"
            @click="changeSort(opt.key)"
          >
            {{ opt.label }}
          </button>
          <span v-if="!hasModel" class="hint">价格排序需先选定模型</span>
        </div>

        <div v-if="errorMsg" class="state state-error">{{ errorMsg }}</div>
        <div v-else-if="loading && list.length === 0" class="state">加载中…</div>
        <div v-else-if="list.length === 0" class="state">没有匹配的限额数据，可调整筛选条件试试。</div>

        <div v-else class="table-wrap">
          <table class="compare-table">
            <thead>
              <tr>
                <th class="col-provider">中转站</th>
                <th class="col-package">套餐</th>
                <th class="col-quota">额度</th>
                <th class="col-cycle">重置周期</th>
                <th class="col-strategy">超限</th>
                <th class="col-pkgprice">套餐价</th>
                <th v-if="hasModel" class="col-price">输入价 /1M</th>
                <th v-if="hasModel" class="col-price">输出价 /1M</th>
                <th v-if="hasModel" class="col-multi">倍率</th>
                <th v-if="hasModel" class="col-ctx">上下文</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in list" :key="row.limit_id">
                <td class="col-provider">
                  <div class="provider-cell">
                    <span class="provider-logo-cell">{{ row.provider_logo_text || 'AI' }}</span>
                    <div class="provider-info">
                      <a
                        v-if="row.provider_website_url"
                        :href="row.provider_website_url"
                        target="_blank"
                        rel="noopener"
                        class="provider-cell-name"
                      >
                        {{ row.provider_name }}
                      </a>
                      <span v-else class="provider-cell-name">{{ row.provider_name }}</span>
                      <router-link
                        :to="`/reviews/detail/${row.provider_id}`"
                        class="provider-link"
                      >
                        详情 →
                      </router-link>
                    </div>
                  </div>
                </td>

                <td class="col-package">
                  <div class="package-cell">
                    <span class="package-name">{{ row.package_name }}</span>
                    <span v-if="row.package_type_name" class="package-type">
                      {{ row.package_type_name }}
                    </span>
                    <span v-if="row.package_description" class="package-desc">
                      {{ row.package_description }}
                    </span>
                  </div>
                </td>

                <td class="col-quota">
                  <span class="quota-num">{{ formatQuota(row.quota_amount, row.quota_unit) }}</span>
                  <span v-if="row.limit_type_text" class="quota-type">
                    {{ row.limit_type_text }}
                  </span>
                </td>

                <td class="col-cycle">{{ row.reset_cycle_text || '-' }}</td>
                <td class="col-strategy">{{ row.over_limit_strategy_text || '-' }}</td>

                <td class="col-pkgprice">
                  <span class="money">{{ formatMoney(row.package_price, row.package_currency) }}</span>
                  <span v-if="row.package_original_price" class="money-origin">
                    {{ formatMoney(row.package_original_price, row.package_currency) }}
                  </span>
                </td>

                <template v-if="hasModel">
                  <td class="col-price">
                    <div class="price-cell">
                      <span class="price-eff">
                        {{ formatNumber(row.effective_input_price_per_million_tokens) }}
                      </span>
                      <span
                        v-if="
                          row.input_price_per_million_tokens != null &&
                          Number(row.consume_multiplier ?? 1) !== 1
                        "
                        class="price-raw"
                      >
                        挂牌 {{ formatNumber(row.input_price_per_million_tokens) }}
                      </span>
                    </div>
                  </td>
                  <td class="col-price">
                    <div class="price-cell">
                      <span class="price-eff">
                        {{ formatNumber(row.effective_output_price_per_million_tokens) }}
                      </span>
                      <span
                        v-if="
                          row.output_price_per_million_tokens != null &&
                          Number(row.consume_multiplier ?? 1) !== 1
                        "
                        class="price-raw"
                      >
                        挂牌 {{ formatNumber(row.output_price_per_million_tokens) }}
                      </span>
                    </div>
                  </td>
                  <td class="col-multi">
                    {{ row.consume_multiplier != null ? Number(row.consume_multiplier).toFixed(2) : '-' }}
                  </td>
                  <td class="col-ctx">
                    {{ row.max_context_tokens ? `${(row.max_context_tokens / 1024).toFixed(0)}K` : '-' }}
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="totalPages > 1" class="pager">
          <button
            :disabled="pageNum <= 1"
            class="page-btn"
            type="button"
            @click="changePage(pageNum - 1)"
          >
            上一页
          </button>
          <span class="page-info">{{ pageNum }} / {{ totalPages }}（共 {{ total }} 条）</span>
          <button
            :disabled="pageNum >= totalPages"
            class="page-btn"
            type="button"
            @click="changePage(pageNum + 1)"
          >
            下一页
          </button>
        </div>

        <p class="footnote">
          价格列单位「每百万 token」，币种沿用所属套餐 currency；跨币种比较请自行换算。
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.compare-page {
  display: grid;
  gap: 1.25rem;
}

.hero {
  position: relative;
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  padding: clamp(1.4rem, 4vw, 2.4rem);
  background:
    radial-gradient(900px 240px at 100% 0%, color-mix(in srgb, var(--color-accent) 16%, transparent), transparent 60%),
    var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3rem);
  letter-spacing: -0.05em;
  color: var(--color-text-primary);
  line-height: 1.05;
}

.lead {
  margin: 1rem 0 0;
  max-width: 38rem;
  color: var(--color-text-secondary);
  line-height: 1.85;
}

/* ── 双栏布局 ── */
.layout {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
  align-items: start;
}

@media (min-width: 1100px) {
  .layout {
    grid-template-columns: 22rem minmax(0, 1fr);
  }
}

.compare-main {
  display: grid;
  gap: 1rem;
  min-width: 0;
}

/* ── 左侧 package 面板 ── */
.package-panel {
  position: sticky;
  top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  max-height: calc(100vh - 2rem);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.4rem;
}

.panel-title {
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.panel-meta {
  font-size: 0.72rem;
  color: var(--color-accent-text);
  font-weight: 600;
}

.panel-search {
  width: 100%;
  padding: 0.4rem 0.7rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  outline: none;
}

.panel-search:focus {
  border-color: var(--color-accent);
}

.active-filter {
  font-size: 0.74rem;
  padding: 0.45rem 0.55rem;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--color-accent) 10%, transparent);
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.active-filter strong {
  color: var(--color-accent-text);
  font-weight: 700;
}

.link-btn {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--color-text-secondary);
  font-size: 0.74rem;
  cursor: pointer;
  letter-spacing: 0.04em;
}

.link-btn:hover {
  color: var(--color-accent-text);
  text-decoration: underline;
}

.package-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  overflow-y: auto;
  scrollbar-width: thin;
}

.package-empty {
  padding: 1rem 0.5rem;
  text-align: center;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.package-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  overflow: hidden;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.package-item.active {
  border-color: color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 30%, transparent) inset;
}

.package-item.expanded {
  background: var(--color-bg-surface);
}

.package-row {
  appearance: none;
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.55rem 0.65rem;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.package-row:hover {
  background: var(--color-bg-soft);
}

.package-arrow {
  flex-shrink: 0;
  margin-top: 0.15rem;
  color: var(--color-text-secondary);
  transition: transform 0.15s ease;
}

.package-arrow.open {
  transform: rotate(90deg);
  color: var(--color-accent-text);
}

.package-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.package-row-head {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: baseline;
}

.pkg-name {
  font-weight: 700;
  font-size: 0.88rem;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pkg-price {
  flex-shrink: 0;
  font-weight: 700;
  font-size: 0.85rem;
  color: var(--color-accent-text);
  font-variant-numeric: tabular-nums;
}

.package-row-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.pkg-type {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.pkg-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 18%, transparent);
  color: var(--color-accent-text);
  font-weight: 700;
}

.package-detail {
  padding: 0.5rem 0.65rem 0.7rem;
  border-top: 1px dashed var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.pkg-desc {
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.pkg-empty {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  padding: 0.4rem 0;
}

.model-mini-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.78rem;
}

.model-mini-table th {
  text-align: left;
  font-weight: 700;
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  letter-spacing: 0.04em;
  padding: 0.3rem 0.4rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.model-mini-table th.num,
.model-mini-table td.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.model-mini-table td {
  padding: 0.4rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 50%, transparent);
  vertical-align: top;
}

.model-mini-table tr:last-child td {
  border-bottom: none;
}

.mm-name {
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 9rem;
}

.mm-vendor {
  margin-top: 0.15rem;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.mm-price {
  font-weight: 700;
  color: var(--color-text-primary);
}

.mm-raw {
  font-size: 0.68rem;
  color: var(--color-text-secondary);
  margin-top: 0.1rem;
}

.pkg-actions {
  display: flex;
  justify-content: flex-end;
}

/* ── 右侧 toolbar 与排序 ── */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: flex-end;
}

.field {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
}

.field-label {
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.select {
  appearance: none;
  padding: 0.4rem 1.8rem 0.4rem 0.7rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg) url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='gray' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>") no-repeat right 0.55rem center / 0.85rem 0.85rem;
  color: var(--color-text-primary);
  font-size: 0.85rem;
  min-width: 9rem;
  outline: none;
  cursor: pointer;
}

.select:focus {
  border-color: var(--color-accent);
}

.ghost-btn {
  appearance: none;
  padding: 0.4rem 0.85rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
}

.ghost-btn.small {
  padding: 0.3rem 0.65rem;
  font-size: 0.74rem;
}

.ghost-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.search-bar {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.search-input {
  width: clamp(11rem, 24vw, 18rem);
  padding: 0.4rem 0.8rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  outline: none;
}

.search-input:focus {
  border-color: var(--color-accent);
}

.search-btn {
  padding: 0.4rem 0.95rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-accent);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}

.search-btn:hover {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
}

.sort-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: center;
}

.sort-label {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
}

.chip {
  padding: 0.32rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip:hover:not(.disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.chip.active {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
  color: var(--color-accent-text);
  font-weight: 600;
}

.chip.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.hint {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  margin-left: 0.4rem;
}

.state {
  padding: 2.5rem 1rem;
  text-align: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.state-error {
  color: var(--color-danger, #d33);
  border-color: color-mix(in srgb, var(--color-danger, #d33) 35%, transparent);
}

.table-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  overflow-x: auto;
  box-shadow: var(--shadow-sm);
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 56rem;
}

.compare-table thead th {
  position: sticky;
  top: 0;
  background: var(--color-bg-soft);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  text-align: left;
  padding: 0.65rem 0.85rem;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.compare-table tbody td {
  padding: 0.85rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 60%, transparent);
  vertical-align: top;
  font-size: 0.85rem;
  color: var(--color-text-primary);
}

.compare-table tbody tr:hover td {
  background: color-mix(in srgb, var(--color-accent) 4%, transparent);
}

.col-provider { min-width: 12rem; }
.col-package { min-width: 14rem; }
.col-quota { min-width: 8rem; }
.col-cycle, .col-strategy, .col-multi, .col-ctx { white-space: nowrap; }
.col-pkgprice, .col-price { text-align: right; white-space: nowrap; }

.provider-cell {
  display: flex;
  gap: 0.6rem;
  align-items: flex-start;
}

.provider-logo-cell {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-sm);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 0.78rem;
}

.provider-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.provider-cell-name {
  font-weight: 700;
  color: var(--color-text-primary);
  text-decoration: none;
}

.provider-cell-name:hover {
  color: var(--color-accent-text);
  text-decoration: underline;
}

.provider-link {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  text-decoration: none;
}

.provider-link:hover {
  color: var(--color-accent-text);
}

.package-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.package-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.package-type {
  display: inline-block;
  width: fit-content;
  font-size: 0.7rem;
  padding: 0.05rem 0.45rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.package-desc {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.quota-num {
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--color-text-primary);
}

.quota-type {
  display: block;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  margin-top: 0.15rem;
}

.money {
  font-weight: 700;
  color: var(--color-accent-text);
}

.money-origin {
  display: block;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  text-decoration: line-through;
  margin-top: 0.15rem;
}

.price-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.15rem;
}

.price-eff {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
}

.price-raw {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  padding: 0.75rem;
}

.page-btn {
  padding: 0.4rem 0.9rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn:not(:disabled):hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.page-info {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.05em;
}

.footnote {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-align: center;
  padding: 0.5rem;
}
</style>
