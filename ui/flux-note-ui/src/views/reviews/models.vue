<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  fetchRelayModelStations,
  fetchRelayModels,
  fetchRelayProviders,
  fetchRelayVendorOptions,
  type FetchModelStationsParams,
  type RelayModel,
  type RelayModelStationRow,
  type RelayProvider,
  type RelayOption,
} from '../../api/aiRelay'

type SortKey = NonNullable<FetchModelStationsParams['sortBy']>

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'recommend', label: '推荐优先' },
  { key: 'input_price', label: '输入价升序' },
  { key: 'output_price', label: '输出价升序' },
  { key: 'multiplier', label: '倍率升序' },
  { key: 'context', label: '上下文降序' },
]

// ── 左侧：模型选择 ──
const models = ref<RelayModel[]>([])
const modelTotal = ref(0)
const modelLoading = ref(false)
const modelError = ref('')

const vendorOptions = ref<RelayOption[]>([])
const vendorFilter = ref('')
const modelKeywordInput = ref('')
const modelKeyword = ref('')
const modelPageNum = ref(1)
const modelPageSize = 30

const selectedModelId = ref<number | null>(null)
const selectedModel = computed(
  () => models.value.find((m) => m.id === selectedModelId.value) ?? null,
)

const modelTotalPages = computed(() =>
  modelTotal.value > 0
    ? Math.max(1, Math.ceil(modelTotal.value / modelPageSize))
    : 1,
)

// ── 右侧：站点对比（以 ai_relay_package_model 为主表）──
const rows = ref<RelayModelStationRow[]>([])
const rowsTotal = ref(0)
const compareLoading = ref(false)
const compareError = ref('')

const comparePageNum = ref(1)
const comparePageSize = ref(20)
const sortBy = ref<SortKey>('input_price')

// 主站筛选
const providers = ref<RelayProvider[]>([])
const providerFilter = ref<number | ''>('')

const compareTotalPages = computed(() =>
  rowsTotal.value > 0
    ? Math.max(1, Math.ceil(rowsTotal.value / comparePageSize.value))
    : 1,
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

async function loadOptions() {
  try {
    const [vendors, providerRes] = await Promise.all([
      fetchRelayVendorOptions(),
      fetchRelayProviders({ pageNum: 1, pageSize: 200 }),
    ])
    vendorOptions.value = vendors ?? []
    providers.value = providerRes.records ?? []
  } catch (e) {
    console.warn('load options failed', e)
  }
}

async function loadModels() {
  modelLoading.value = true
  modelError.value = ''
  try {
    const res = await fetchRelayModels({
      pageNum: modelPageNum.value,
      pageSize: modelPageSize,
      keyword: modelKeyword.value || undefined,
      modelVendor: vendorFilter.value || undefined,
    })
    models.value = res.records ?? []
    modelTotal.value = res.total ?? 0
    // 自动选中第一个，若当前选中项已不在列表中
    if (
      models.value.length > 0 &&
      (selectedModelId.value == null ||
        !models.value.some((m) => m.id === selectedModelId.value))
    ) {
      selectModel(models.value[0]!.id)
    } else if (models.value.length === 0) {
      selectedModelId.value = null
      rows.value = []
      rowsTotal.value = 0
    }
  } catch (e) {
    modelError.value = e instanceof Error ? e.message : '加载失败'
    models.value = []
    modelTotal.value = 0
  } finally {
    modelLoading.value = false
  }
}

async function loadStations() {
  if (selectedModelId.value == null) {
    rows.value = []
    rowsTotal.value = 0
    return
  }
  compareLoading.value = true
  compareError.value = ''
  try {
    const res = await fetchRelayModelStations({
      pageNum: comparePageNum.value,
      pageSize: comparePageSize.value,
      modelId: selectedModelId.value,
      providerId: providerFilter.value === '' ? undefined : Number(providerFilter.value),
      sortBy: sortBy.value,
    })
    rows.value = res.records ?? []
    rowsTotal.value = res.total ?? 0
  } catch (e) {
    compareError.value = e instanceof Error ? e.message : '加载失败'
    rows.value = []
    rowsTotal.value = 0
  } finally {
    compareLoading.value = false
  }
}

function applyModelSearch() {
  modelKeyword.value = modelKeywordInput.value.trim()
  modelPageNum.value = 1
  loadModels()
}

function resetModelFilters() {
  vendorFilter.value = ''
  modelKeyword.value = ''
  modelKeywordInput.value = ''
  modelPageNum.value = 1
  loadModels()
}

function changeModelPage(next: number) {
  if (next < 1 || next > modelTotalPages.value || next === modelPageNum.value) return
  modelPageNum.value = next
  loadModels()
}

function selectModel(id: number) {
  if (selectedModelId.value === id) return
  selectedModelId.value = id
  comparePageNum.value = 1
  loadStations()
}

function changeSort(key: SortKey) {
  if (sortBy.value === key) return
  sortBy.value = key
  comparePageNum.value = 1
  loadStations()
}

function changeComparePage(next: number) {
  if (next < 1 || next > compareTotalPages.value || next === comparePageNum.value) return
  comparePageNum.value = next
  loadStations()
}

watch(vendorFilter, () => {
  modelPageNum.value = 1
  loadModels()
})

watch([providerFilter, comparePageSize], () => {
  comparePageNum.value = 1
  loadStations()
})

onMounted(async () => {
  await loadOptions()
  await loadModels()
})
</script>

<template>
  <section class="models-page">
    <header class="hero">
      <p class="eyebrow">Model First</p>
      <h1>模型选择站点</h1>
      <p class="lead">
        先在左侧选定你想用的模型，右侧即按该模型横向对比所有支持它的主站套餐：消耗倍率、实付每百万 token 单价一目了然，还能指定主站单独计算价格，帮你为目标模型挑出最划算的站点。
      </p>
    </header>

    <div class="layout">
      <!-- 左侧：模型选择 -->
      <aside class="model-pane">
        <div class="model-toolbar">
          <label class="field">
            <span class="field-label">厂商</span>
            <select v-model="vendorFilter" class="select">
              <option value="">全部</option>
              <option v-for="v in vendorOptions" :key="v.value" :value="v.value">
                {{ v.label }}
              </option>
            </select>
          </label>

          <div class="search-bar">
            <input
              v-model="modelKeywordInput"
              class="search-input"
              placeholder="搜索模型名 / 编码"
              type="search"
              @keyup.enter="applyModelSearch"
            />
            <button class="search-btn" type="button" @click="applyModelSearch">
              搜索
            </button>
          </div>

          <button class="ghost-btn" type="button" @click="resetModelFilters">
            重置
          </button>
        </div>

        <div v-if="modelError" class="state state-error">{{ modelError }}</div>
        <div v-else-if="modelLoading && models.length === 0" class="state">加载中…</div>
        <div v-else-if="models.length === 0" class="state">暂无匹配模型</div>

        <ul v-else class="model-list">
          <li v-for="m in models" :key="m.id">
            <button
              type="button"
              class="model-item"
              :class="{ active: m.id === selectedModelId }"
              @click="selectModel(m.id)"
            >
              <span class="model-item-name">{{ m.name || m.code }}</span>
              <span v-if="m.model_vendor" class="model-item-vendor">
                {{ m.model_vendor }}
              </span>
              <span v-if="m.code && m.name" class="model-item-code">{{ m.code }}</span>
            </button>
          </li>
        </ul>

        <div v-if="modelTotalPages > 1" class="pager mini">
          <button
            :disabled="modelPageNum <= 1"
            class="page-btn"
            type="button"
            @click="changeModelPage(modelPageNum - 1)"
          >
            上一页
          </button>
          <span class="page-info">{{ modelPageNum }} / {{ modelTotalPages }}</span>
          <button
            :disabled="modelPageNum >= modelTotalPages"
            class="page-btn"
            type="button"
            @click="changeModelPage(modelPageNum + 1)"
          >
            下一页
          </button>
        </div>
      </aside>

      <!-- 右侧：站点对比 -->
      <section class="compare-pane">
        <div v-if="!selectedModel" class="state">请从左侧选择一个模型</div>

        <template v-else>
          <header class="compare-head">
            <div class="compare-title-row">
              <h2 class="compare-title">{{ selectedModel.name || selectedModel.code }}</h2>
              <span v-if="selectedModel.model_vendor" class="vendor-chip">
                {{ selectedModel.model_vendor }}
              </span>
            </div>
            <p class="compare-sub">
              共 {{ rowsTotal }} 个套餐支持该模型 · 实付 = 挂牌价 × 消耗倍率
            </p>
          </header>

          <div class="filter-row">
            <label class="field">
              <span class="field-label">主站</span>
              <select v-model="providerFilter" class="select">
                <option value="">全部主站</option>
                <option v-for="p in providers" :key="p.id" :value="p.id">
                  {{ p.name }}
                </option>
              </select>
            </label>

            <div class="sort-group">
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
          </div>

          <div v-if="compareError" class="state state-error">{{ compareError }}</div>
          <div v-else-if="compareLoading && rows.length === 0" class="state">加载中…</div>
          <div v-else-if="rows.length === 0" class="state">暂无支持该模型的站点</div>

          <div v-else class="table-wrap">
            <table class="cmp-table">
              <thead>
                <tr>
                  <th class="col-provider">主站</th>
                  <th class="col-pkg">套餐</th>
                  <th class="col-mult">倍率</th>
                  <th class="col-price">输入 /1M</th>
                  <th class="col-price">输出 /1M</th>
                  <th class="col-ctx">上下文</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td class="col-provider">
                    <div class="provider-cell">
                      <a
                        v-if="row.provider_website_url"
                        :href="row.provider_website_url"
                        target="_blank"
                        rel="noopener"
                        class="provider-name"
                      >
                        {{ row.provider_name || '—' }}
                        <span class="site-link" aria-hidden="true">↗</span>
                      </a>
                      <span v-else class="provider-name">
                        {{ row.provider_name || '—' }}
                      </span>
                      <span v-if="row.package_recommended" class="rec-tag">推荐</span>
                    </div>
                  </td>
                  <td class="col-pkg">
                    <div class="pkg-cell">
                      <span class="pkg-name">{{ row.package_name || '—' }}</span>
                      <span v-if="row.package_type_name" class="type-chip">
                        {{ row.package_type_name }}
                      </span>
                      <span v-if="row.is_default" class="default-tag">默认</span>
                    </div>
                    <div v-if="row.package_price != null" class="pkg-price">
                      套餐 {{ formatMoney(row.package_price, row.package_currency) }}
                    </div>
                  </td>
                  <td class="col-mult">
                    {{ row.consume_multiplier != null ? Number(row.consume_multiplier).toFixed(2) : '-' }}
                  </td>
                  <td class="col-price">
                    <span class="eff-price">
                      {{ formatNumber(row.effective_input_price_per_million_tokens) }}
                    </span>
                    <span
                      v-if="
                        row.input_price_per_million_tokens != null &&
                        Number(row.consume_multiplier ?? 1) !== 1
                      "
                      class="raw-price"
                    >
                      挂牌 {{ formatNumber(row.input_price_per_million_tokens) }}
                    </span>
                  </td>
                  <td class="col-price">
                    <span class="eff-price">
                      {{ formatNumber(row.effective_output_price_per_million_tokens) }}
                    </span>
                    <span
                      v-if="
                        row.output_price_per_million_tokens != null &&
                        Number(row.consume_multiplier ?? 1) !== 1
                      "
                      class="raw-price"
                    >
                      挂牌 {{ formatNumber(row.output_price_per_million_tokens) }}
                    </span>
                  </td>
                  <td class="col-ctx">
                    {{ row.max_context_tokens ? `${(row.max_context_tokens / 1024).toFixed(0)}K` : '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="compareTotalPages > 1" class="pager">
            <button
              :disabled="comparePageNum <= 1"
              class="page-btn"
              type="button"
              @click="changeComparePage(comparePageNum - 1)"
            >
              上一页
            </button>
            <span class="page-info">{{ comparePageNum }} / {{ compareTotalPages }}</span>
            <button
              :disabled="comparePageNum >= compareTotalPages"
              class="page-btn"
              type="button"
              @click="changeComparePage(comparePageNum + 1)"
            >
              下一页
            </button>
            <select v-model.number="comparePageSize" class="page-size">
              <option :value="10">10/页</option>
              <option :value="20">20/页</option>
              <option :value="50">50/页</option>
            </select>
          </div>
        </template>
      </section>
    </div>

    <p class="footnote">
      价格列单位「每百万 token」，币种沿用所属套餐 currency；实付 = 挂牌价 × 消耗倍率。数据以 ai_relay_package_model 为准。
    </p>
  </section>
</template>

<style scoped>
.models-page {
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
  max-width: 40rem;
  color: var(--color-text-secondary);
  line-height: 1.85;
}

/* ── 30/70 双栏：左选模型，右对比站点 ── */
.layout {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
  align-items: start;
}

@media (min-width: 1080px) {
  .layout {
    grid-template-columns: minmax(0, 3fr) minmax(0, 7fr);
  }
}

/* ── 左侧：模型选择 ── */
.model-pane {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

@media (min-width: 1080px) {
  .model-pane {
    position: sticky;
    top: 1rem;
  }
}

.model-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: flex-end;
  padding: 0.75rem 0.85rem;
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
  min-width: 8rem;
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
  flex: 1;
  min-width: 10rem;
}

.search-input {
  flex: 1;
  min-width: 0;
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
  white-space: nowrap;
}

.search-btn:hover {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
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

.model-list {
  margin: 0;
  padding: 0.55rem;
  list-style: none;
  display: grid;
  gap: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  max-height: calc(100vh - 16rem);
  overflow-y: auto;
}

.model-item {
  appearance: none;
  width: 100%;
  display: grid;
  gap: 0.2rem;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease;
}

.model-item:hover {
  background: color-mix(in srgb, var(--color-accent) 6%, transparent);
}

.model-item.active {
  background: color-mix(in srgb, var(--color-accent) 12%, var(--color-bg-surface));
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
}

.model-item-name {
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--color-text-primary);
}

.model-item.active .model-item-name {
  color: var(--color-accent-text);
}

.model-item-vendor {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.model-item-code {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  opacity: 0.85;
}

/* ── 右侧：站点对比 ── */
.compare-pane {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}

.compare-head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.compare-title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.compare-title {
  margin: 0;
  font-size: 1.5rem;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.vendor-chip {
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.74rem;
  font-weight: 700;
}

.compare-sub {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
  align-items: flex-end;
}

.sort-group {
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

.cmp-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 44rem;
}

.cmp-table thead th {
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

.cmp-table tbody td {
  padding: 0.8rem 0.85rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 60%, transparent);
  vertical-align: top;
  font-size: 0.85rem;
  color: var(--color-text-primary);
}

.cmp-table tbody tr:hover td {
  background: color-mix(in srgb, var(--color-accent) 5%, transparent);
}

.col-provider { min-width: 9rem; }
.col-pkg { min-width: 11rem; }
.col-mult { white-space: nowrap; font-variant-numeric: tabular-nums; }
.col-price { white-space: nowrap; font-variant-numeric: tabular-nums; }
.col-ctx { white-space: nowrap; text-align: center; }

.provider-cell {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.provider-name {
  font-weight: 700;
  color: var(--color-text-primary);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

a.provider-name:hover {
  color: var(--color-accent-text);
  text-decoration: underline;
}

.site-link {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.rec-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 18%, transparent);
  color: var(--color-accent-text);
  font-size: 0.68rem;
  font-weight: 700;
}

.pkg-cell {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.pkg-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.type-chip {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.72rem;
}

.default-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  color: var(--color-accent-text);
  font-size: 0.66rem;
  font-weight: 700;
}

.pkg-price {
  margin-top: 0.25rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.eff-price {
  font-weight: 700;
  color: var(--color-accent-text);
  display: block;
}

.raw-price {
  display: block;
  margin-top: 0.15rem;
  font-size: 0.68rem;
  color: var(--color-text-secondary);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  padding: 0.55rem;
}

.pager.mini {
  padding: 0.25rem;
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

.footnote {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-align: center;
  padding: 0.5rem;
}
</style>
