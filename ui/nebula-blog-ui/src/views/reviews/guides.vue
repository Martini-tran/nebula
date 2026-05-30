<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  fetchRelayPackages,
  fetchRelayPackageTypes,
  fetchRelayProviders,
  type FetchPackagesParams,
  type RelayPackageType,
  type RelayProvider,
  type RelayProviderPackage,
} from '../../api/aiRelay'

type SortKey = NonNullable<FetchPackagesParams['sortBy']>

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'recommend', label: '推荐优先' },
  { key: 'price_asc', label: '价格升序' },
  { key: 'price_desc', label: '价格降序' },
  { key: 'latest', label: '最新创建' },
]

const list = ref<RelayProviderPackage[]>([])
const total = ref(0)
const loading = ref(false)
const errorMsg = ref('')

const packageTypes = ref<RelayPackageType[]>([])
const providers = ref<RelayProvider[]>([])

const pageNum = ref(1)
const pageSize = ref(15)
const sortBy = ref<SortKey>('recommend')

const providerFilter = ref<number | ''>('')
const packageTypeFilter = ref<string>('')
const keywordInput = ref('')
const keyword = ref('')

const selectedId = ref<number | null>(null)

const totalPages = computed(() =>
  total.value > 0 ? Math.max(1, Math.ceil(total.value / pageSize.value)) : 1,
)

const selectedPackage = computed(() =>
  list.value.find((p) => p.id === selectedId.value) ?? null,
)

function formatNumber(input?: number | null, fractionDigits = 4) {
  if (input == null) return '-'
  const n = Number(input)
  if (Number.isNaN(n)) return '-'
  return n.toFixed(fractionDigits)
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

function formatQuotaSummary(pkg: RelayProviderPackage): string {
  if (pkg.quota_summary) return pkg.quota_summary
  const first = pkg.limits?.[0]
  if (!first) return '—'
  const num = Number(first.quota_amount ?? 0)
  let display: string
  if (num >= 1_000_000) display = `${(num / 1_000_000).toFixed(1)}M`
  else if (num >= 1_000) display = `${(num / 1_000).toFixed(1)}K`
  else display = String(first.quota_amount ?? '-')
  return first.quota_unit ? `${display} ${first.quota_unit}` : display
}

async function loadOptions() {
  try {
    const [providerRes, typeList] = await Promise.all([
      fetchRelayProviders({ pageNum: 1, pageSize: 200 }),
      fetchRelayPackageTypes(),
    ])
    providers.value = providerRes.records ?? []
    packageTypes.value = typeList ?? []
  } catch (e) {
    console.warn('load options failed', e)
  }
}

async function loadList() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchRelayPackages({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      providerId:
        providerFilter.value === '' ? undefined : Number(providerFilter.value),
      packageTypeCode: packageTypeFilter.value || undefined,
      keyword: keyword.value || undefined,
      sortBy: sortBy.value,
    })
    list.value = res.records ?? []
    total.value = res.total ?? 0
    if (list.value.length === 0) {
      selectedId.value = null
    } else if (
      selectedId.value == null ||
      !list.value.some((p) => p.id === selectedId.value)
    ) {
      selectedId.value = list.value[0]?.id ?? null
    }
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
    selectedId.value = null
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
}

function resetFilters() {
  providerFilter.value = ''
  packageTypeFilter.value = ''
  keyword.value = ''
  keywordInput.value = ''
  sortBy.value = 'recommend'
  pageNum.value = 1
  loadList()
}

function selectPackage(id: number) {
  selectedId.value = id
}

watch([providerFilter, packageTypeFilter, pageSize], () => {
  pageNum.value = 1
  loadList()
})

onMounted(async () => {
  await loadOptions()
  loadList()
})
</script>

<template>
  <section class="guides-page">
    <header class="hero">
      <p class="eyebrow">All Packages</p>
      <h1>所有套餐</h1>
      <p class="lead">
        左侧浏览全部上线套餐，可按中转站、套餐类型筛选并排序；点击任意一行可在右侧查看该套餐支持的模型、消耗倍率以及每百万 token 单价。
      </p>
    </header>

    <div class="layout">
      <!-- 左侧：套餐表格 -->
      <section class="list-pane">
        <div class="toolbar">
          <label class="field">
            <span class="field-label">中转站</span>
            <select v-model="providerFilter" class="select">
              <option value="">全部</option>
              <option v-for="p in providers" :key="p.id" :value="p.id">
                {{ p.name }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field-label">套餐类型</span>
            <select v-model="packageTypeFilter" class="select">
              <option value="">全部</option>
              <option v-for="t in packageTypes" :key="t.id" :value="t.code">
                {{ t.name }}
              </option>
            </select>
          </label>

          <div class="search-bar">
            <input
              v-model="keywordInput"
              class="search-input"
              placeholder="搜索套餐名"
              type="search"
              @keyup.enter="applySearch"
            />
            <button class="search-btn" type="button" @click="applySearch">搜索</button>
          </div>

          <button class="ghost-btn" type="button" @click="resetFilters">重置</button>

          <span class="count-tip">共 {{ total }} 个</span>
        </div>

        <div class="sort-row">
          <span class="sort-label">排序</span>
          <button
            v-for="opt in sortOptions"
            :key="opt.key"
            :class="['chip', { active: sortBy === opt.key }]"
            type="button"
            @click="changeSort(opt.key)"
          >
            {{ opt.label }}
          </button>
        </div>

        <div v-if="errorMsg" class="state state-error">{{ errorMsg }}</div>
        <div v-else-if="loading && list.length === 0" class="state">加载中…</div>
        <div v-else-if="list.length === 0" class="state">暂无匹配套餐</div>

        <div v-else class="table-wrap">
          <table class="pkg-table">
            <thead>
              <tr>
                <th class="col-name">套餐</th>
                <th class="col-provider">中转站</th>
                <th class="col-type">类型</th>
                <th class="col-quota">额度</th>
                <th class="col-price">价格</th>
                <th class="col-models">模型数</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="pkg in list"
                :key="pkg.id"
                :class="{ active: pkg.id === selectedId }"
                @click="selectPackage(pkg.id)"
              >
                <td class="col-name">
                  <div class="cell-name">
                    <span class="pkg-name">{{ pkg.name }}</span>
                    <span v-if="pkg.recommended" class="pkg-tag">推荐</span>
                  </div>
                  <div v-if="pkg.description" class="pkg-desc">
                    {{ pkg.description }}
                  </div>
                </td>
                <td class="col-provider">{{ pkg.provider_name || '—' }}</td>
                <td class="col-type">
                  <span class="type-chip">
                    {{ pkg.package_type_name || pkg.package_type_code || '—' }}
                  </span>
                </td>
                <td class="col-quota">{{ formatQuotaSummary(pkg) }}</td>
                <td class="col-price">
                  <span class="money">{{ formatMoney(pkg.price, pkg.currency) }}</span>
                  <span v-if="pkg.original_price" class="money-origin">
                    {{ formatMoney(pkg.original_price, pkg.currency) }}
                  </span>
                </td>
                <td class="col-models">
                  <span class="model-count">{{ pkg.models?.length ?? 0 }}</span>
                </td>
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
          <span class="page-info">
            {{ pageNum }} / {{ totalPages }}
          </span>
          <button
            :disabled="pageNum >= totalPages"
            class="page-btn"
            type="button"
            @click="changePage(pageNum + 1)"
          >
            下一页
          </button>
          <select v-model.number="pageSize" class="page-size">
            <option :value="10">10/页</option>
            <option :value="15">15/页</option>
            <option :value="30">30/页</option>
            <option :value="50">50/页</option>
          </select>
        </div>
      </section>

      <!-- 右侧：选中套餐详情 -->
      <aside class="detail-pane">
        <div v-if="!selectedPackage" class="detail-empty">
          请从左侧选择一个套餐
        </div>

        <template v-else>
          <header class="detail-head">
            <p class="detail-eyebrow">{{ selectedPackage.provider_name || '—' }}</p>
            <h2 class="detail-title">{{ selectedPackage.name }}</h2>
            <div class="detail-meta">
              <span class="meta-price">
                {{ formatMoney(selectedPackage.price, selectedPackage.currency) }}
              </span>
              <span
                v-if="selectedPackage.original_price"
                class="meta-origin"
              >
                {{ formatMoney(selectedPackage.original_price, selectedPackage.currency) }}
              </span>
              <span
                v-if="selectedPackage.package_type_name"
                class="meta-chip"
              >
                {{ selectedPackage.package_type_name }}
              </span>
              <span v-if="selectedPackage.recommended" class="meta-tag">推荐</span>
            </div>
            <p v-if="selectedPackage.description" class="detail-desc">
              {{ selectedPackage.description }}
            </p>
          </header>

          <section
            v-if="selectedPackage.limits && selectedPackage.limits.length > 0"
            class="detail-section"
          >
            <h3 class="section-title">额度限制</h3>
            <ul class="limit-list">
              <li v-for="l in selectedPackage.limits" :key="l.id">
                <span class="limit-amount">
                  {{ l.quota_amount }} {{ l.quota_unit }}
                </span>
                <span v-if="l.description" class="limit-desc">{{ l.description }}</span>
              </li>
            </ul>
          </section>

          <section class="detail-section">
            <h3 class="section-title">
              支持模型
              <span class="section-meta">
                共 {{ selectedPackage.models?.length ?? 0 }} 个
              </span>
            </h3>
            <div
              v-if="!selectedPackage.models || selectedPackage.models.length === 0"
              class="state mini"
            >
              该套餐暂未配置模型
            </div>
            <ul v-else class="model-list">
              <li v-for="m in selectedPackage.models" :key="m.id" class="model-card">
                <div class="model-head">
                  <div class="model-name">
                    {{ m.model_name || m.model_code }}
                    <span v-if="m.is_default" class="model-default">默认</span>
                  </div>
                  <span v-if="m.model_vendor" class="model-vendor">
                    {{ m.model_vendor }}
                  </span>
                </div>

                <div class="model-stats">
                  <div class="stat">
                    <span class="stat-label">倍率</span>
                    <span class="stat-value">
                      {{ m.consume_multiplier != null ? Number(m.consume_multiplier).toFixed(2) : '-' }}
                    </span>
                  </div>
                  <div class="stat">
                    <span class="stat-label">输入 /1M</span>
                    <span class="stat-value">
                      {{ formatNumber(effectivePrice(m.input_price_per_million_tokens, m.consume_multiplier)) }}
                    </span>
                    <span
                      v-if="
                        m.input_price_per_million_tokens != null &&
                        Number(m.consume_multiplier ?? 1) !== 1
                      "
                      class="stat-raw"
                    >
                      挂牌 {{ formatNumber(m.input_price_per_million_tokens) }}
                    </span>
                  </div>
                  <div class="stat">
                    <span class="stat-label">输出 /1M</span>
                    <span class="stat-value">
                      {{ formatNumber(effectivePrice(m.output_price_per_million_tokens, m.consume_multiplier)) }}
                    </span>
                    <span
                      v-if="
                        m.output_price_per_million_tokens != null &&
                        Number(m.consume_multiplier ?? 1) !== 1
                      "
                      class="stat-raw"
                    >
                      挂牌 {{ formatNumber(m.output_price_per_million_tokens) }}
                    </span>
                  </div>
                  <div v-if="m.max_context_tokens" class="stat">
                    <span class="stat-label">上下文</span>
                    <span class="stat-value">
                      {{ (m.max_context_tokens / 1024).toFixed(0) }}K
                    </span>
                  </div>
                </div>

                <div v-if="m.provider_model_code" class="model-code">
                  <span class="code-label">服务商编码</span>
                  <code>{{ m.provider_model_code }}</code>
                </div>
              </li>
            </ul>
          </section>
        </template>
      </aside>
    </div>

    <p class="footnote">
      价格列单位「每百万 token」，币种沿用所属套餐 currency；实付 = 挂牌价 × 消耗倍率。
    </p>
  </section>
</template>

<style scoped>
.guides-page {
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

/* ── 70/30 双栏 ── */
.layout {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
  align-items: start;
}

@media (min-width: 1080px) {
  .layout {
    grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  }
}

/* ── 左侧 ── */
.list-pane {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: flex-end;
  padding: 0.85rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
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

.search-bar {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.search-input {
  width: clamp(11rem, 22vw, 18rem);
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

.count-tip {
  margin-left: auto;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
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

.state.mini {
  padding: 1rem 0.8rem;
  font-size: 0.78rem;
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

.pkg-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 48rem;
}

.pkg-table thead th {
  position: sticky;
  top: 0;
  background: var(--color-bg-soft);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  text-align: left;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.pkg-table tbody td {
  padding: 0.85rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 60%, transparent);
  vertical-align: top;
  font-size: 0.85rem;
  color: var(--color-text-primary);
}

.pkg-table tbody tr {
  cursor: pointer;
  transition: background 0.12s ease;
}

.pkg-table tbody tr:hover td {
  background: color-mix(in srgb, var(--color-accent) 5%, transparent);
}

.pkg-table tbody tr.active td {
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
}

.pkg-table tbody tr.active td:first-child {
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.col-name { min-width: 14rem; }
.col-provider { min-width: 8rem; white-space: nowrap; }
.col-type, .col-quota { white-space: nowrap; }
.col-price { white-space: nowrap; text-align: right; }
.col-models { text-align: center; white-space: nowrap; }

.cell-name {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.pkg-name {
  font-weight: 700;
  color: var(--color-text-primary);
}

.pkg-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 18%, transparent);
  color: var(--color-accent-text);
  font-size: 0.7rem;
  font-weight: 700;
}

.pkg-desc {
  margin-top: 0.25rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.type-chip {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
}

.money {
  font-weight: 700;
  color: var(--color-accent-text);
  font-variant-numeric: tabular-nums;
}

.money-origin {
  display: block;
  margin-top: 0.15rem;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.model-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2rem;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* ── 右侧详情 ── */
.detail-pane {
  position: sticky;
  top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem 1.05rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  max-height: calc(100vh - 2rem);
  overflow-y: auto;
}

.detail-empty {
  color: var(--color-text-secondary);
  font-size: 0.9rem;
  text-align: center;
  padding: 3rem 0;
}

.detail-eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  font-weight: 700;
}

.detail-title {
  margin: 0.3rem 0 0.4rem;
  font-size: 1.4rem;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
}

.meta-price {
  font-weight: 800;
  font-size: 1.05rem;
  color: var(--color-accent-text);
}

.meta-origin {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.meta-chip {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
}

.meta-tag {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 18%, transparent);
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 700;
}

.detail-desc {
  margin: 0.65rem 0 0;
  font-size: 0.85rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.section-title {
  margin: 0;
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--color-text-secondary);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.section-meta {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  font-weight: 500;
  letter-spacing: 0.04em;
  text-transform: none;
}

.limit-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.4rem;
}

.limit-list li {
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  font-size: 0.82rem;
}

.limit-amount {
  font-weight: 700;
  color: var(--color-text-primary);
}

.limit-desc {
  display: block;
  margin-top: 0.2rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.model-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.65rem;
}

.model-card {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.7rem 0.8rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg);
}

.model-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.model-name {
  font-weight: 700;
  color: var(--color-text-primary);
  font-size: 0.92rem;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.model-default {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 18%, transparent);
  color: var(--color-accent-text);
  font-size: 0.66rem;
  font-weight: 700;
}

.model-vendor {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.model-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.45rem 0.55rem;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
}

.stat-label {
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.stat-value {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.stat-raw {
  font-size: 0.68rem;
  color: var(--color-text-secondary);
}

.model-code {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.model-code code {
  padding: 0.05rem 0.45rem;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  color: var(--color-text-primary);
}

.code-label {
  letter-spacing: 0.04em;
}

.footnote {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-align: center;
  padding: 0.5rem;
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

.chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.chip.active {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
  color: var(--color-accent-text);
  font-weight: 600;
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

.ghost-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  padding: 0.55rem;
}

.page-btn {
  padding: 0.35rem 0.85rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.82rem;
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
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
}

.page-size {
  appearance: none;
  padding: 0.32rem 0.55rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text-primary);
  font-size: 0.78rem;
  outline: none;
  cursor: pointer;
}
</style>
