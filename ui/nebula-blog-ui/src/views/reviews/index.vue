<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ProviderProductCard from './components/ProviderProductCard.vue'
import {
  fetchRelayPackageTypes,
  fetchRelayProviders,
  fetchRelayVendorOptions,
  type RelayProvider,
} from '../../api/aiRelay'

const router = useRouter()

type SortKey = 'recommend' | 'price' | 'stability'
type SyncPreset = 'all' | 'today' | 'last3' | 'week' | 'month' | 'custom'

const sortKey = ref<SortKey>('recommend')
const vendorFilter = ref<string>('all')
const packageTypeFilter = ref<string>('all')
const syncStart = ref('')
const syncEnd = ref('')
const syncPreset = ref<SyncPreset>('all')

const syncPresetOptions: { key: SyncPreset; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'today', label: '今日' },
  { key: 'last3', label: '近三天' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'custom', label: '自定义' },
]

function formatDate(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function applySyncPreset(preset: SyncPreset) {
  syncPreset.value = preset
  if (preset === 'custom') return
  const today = new Date()
  let start: Date | null = null
  let end: Date | null = null
  if (preset === 'today') {
    start = today
    end = today
  } else if (preset === 'last3') {
    const s = new Date(today)
    s.setDate(today.getDate() - 2)
    start = s
    end = today
  } else if (preset === 'week') {
    // 周一作为本周起点（getDay: 周日=0 → 6, 周一=1 → 0）
    const dow = (today.getDay() + 6) % 7
    const s = new Date(today)
    s.setDate(today.getDate() - dow)
    start = s
    end = today
  } else if (preset === 'month') {
    start = new Date(today.getFullYear(), today.getMonth(), 1)
    end = today
  }
  syncStart.value = start ? formatDate(start) : ''
  syncEnd.value = end ? formatDate(end) : ''
}

const keyword = ref('')
const keywordInput = ref('')

const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const providers = ref<RelayProvider[]>([])
const loading = ref(false)
const errorMsg = ref('')

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'recommend', label: '综合推荐' },
  { key: 'price', label: '价格优先' },
  { key: 'stability', label: '稳定性优先' },
]

const ALL_VENDOR: { key: string; label: string } = { key: 'all', label: '全部厂商' }
const ALL_PACKAGE_TYPE: { key: string; label: string } = { key: 'all', label: '全部类型' }

const vendorOptions = ref<{ key: string; label: string }[]>([ALL_VENDOR])
const packageTypeOptions = ref<{ key: string; label: string }[]>([ALL_PACKAGE_TYPE])

async function loadVendorOptions() {
  try {
    const list = await fetchRelayVendorOptions()
    vendorOptions.value = [
      ALL_VENDOR,
      ...(list ?? []).map((o) => ({ key: o.value, label: o.label })),
    ]
  } catch {
    /* keep default */
  }
}

async function loadPackageTypeOptions() {
  try {
    const list = await fetchRelayPackageTypes()
    packageTypeOptions.value = [
      ALL_PACKAGE_TYPE,
      ...(list ?? []).map((t) => ({ key: t.code, label: t.name })),
    ]
  } catch {
    /* keep default */
  }
}

const totalPages = computed(() =>
  total.value > 0 ? Math.max(1, Math.ceil(total.value / pageSize.value)) : 1,
)

const pageList = computed<(number | '...')[]>(() => {
  const last = totalPages.value
  const cur = pageNum.value
  if (last <= 7) {
    return Array.from({ length: last }, (_, i) => i + 1)
  }
  const result: (number | '...')[] = [1]
  const start = Math.max(2, cur - 1)
  const end = Math.min(last - 1, cur + 1)
  if (start > 2) result.push('...')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < last - 1) result.push('...')
  result.push(last)
  return result
})

async function loadProviders() {
  loading.value = true
  errorMsg.value = ''
  try {
    const params: Parameters<typeof fetchRelayProviders>[0] = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sortBy: sortKey.value,
    }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (vendorFilter.value !== 'all') params.modelVendor = vendorFilter.value
    if (packageTypeFilter.value !== 'all') params.packageTypeCode = packageTypeFilter.value
    if (syncStart.value) params.lastSyncTimeStart = syncStart.value
    if (syncEnd.value) params.lastSyncTimeEnd = syncEnd.value
    const result = await fetchRelayProviders(params)
    providers.value = result?.records ?? []
    total.value = Number(result?.total ?? 0)
  } catch (e) {
    providers.value = []
    total.value = 0
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadVendorOptions()
  loadPackageTypeOptions()
  loadProviders()
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(keywordInput, (v) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (v === keyword.value) return
    keyword.value = v
    pageNum.value = 1
    loadProviders()
  }, 300)
})

watch([sortKey, vendorFilter, packageTypeFilter, syncStart, syncEnd], () => {
  pageNum.value = 1
  loadProviders()
})

function changePage(p: number) {
  if (p < 1 || p > totalPages.value || p === pageNum.value) return
  pageNum.value = p
  loadProviders()
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function resetFilters() {
  sortKey.value = 'recommend'
  vendorFilter.value = 'all'
  packageTypeFilter.value = 'all'
  syncStart.value = ''
  syncEnd.value = ''
  syncPreset.value = 'all'
  keyword.value = ''
  keywordInput.value = ''
  pageNum.value = 1
  loadProviders()
}

function submitSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  if (keywordInput.value === keyword.value) return
  keyword.value = keywordInput.value
  pageNum.value = 1
  loadProviders()
}

function openDetail(provider: RelayProvider) {
  router.push({ name: 'reviews-detail', params: { id: String(provider.id) } })
}
</script>

<template>
  <section class="directory-page">
    <!-- 风险提示 -->
    <aside class="notice">
      <span class="notice-tag">提醒</span>
      <p>
        中转站行业波动较大，定价、模型供应、可用性可能随时变化。下方信息为编辑使用期间的真实记录，
        购买前请以官网为准，避免大额预付。
      </p>
    </aside>

    <!-- 搜索 + 筛选 -->
    <div class="toolbar">
      <form class="search-box" @submit.prevent="submitSearch">
        <span class="search-icon" aria-hidden="true">🔍</span>
        <input
          v-model="keywordInput"
          type="search"
          placeholder="搜索中转站名称…"
          class="search-input"
          aria-label="搜索中转站"
        />
        <button v-if="keywordInput" type="button" class="search-clear" @click="keywordInput = ''">
          ×
        </button>
      </form>

      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">排序</span>
          <div class="chip-row">
            <button
              v-for="opt in sortOptions"
              :key="opt.key"
              type="button"
              class="chip"
              :class="{ active: sortKey === opt.key }"
              @click="sortKey = opt.key"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="filter-group">
          <span class="filter-label">厂商</span>
          <div class="chip-row">
            <button
              v-for="opt in vendorOptions"
              :key="opt.key"
              type="button"
              class="chip"
              :class="{ active: vendorFilter === opt.key }"
              @click="vendorFilter = opt.key"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="filter-group">
          <span class="filter-label">类型</span>
          <div class="chip-row">
            <button
              v-for="opt in packageTypeOptions"
              :key="opt.key"
              type="button"
              class="chip"
              :class="{ active: packageTypeFilter === opt.key }"
              @click="packageTypeFilter = opt.key"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="filter-group">
          <span class="filter-label">同步时间</span>
          <div class="chip-row">
            <button
              v-for="opt in syncPresetOptions"
              :key="opt.key"
              type="button"
              class="chip"
              :class="{ active: syncPreset === opt.key }"
              @click="applySyncPreset(opt.key)"
            >
              {{ opt.label }}
            </button>
          </div>
          <div v-if="syncPreset === 'custom'" class="date-range">
            <input
              v-model="syncStart"
              type="date"
              class="date-input"
              :max="syncEnd || undefined"
              aria-label="同步时间起"
            />
            <span class="date-sep" aria-hidden="true">~</span>
            <input
              v-model="syncEnd"
              type="date"
              class="date-input"
              :min="syncStart || undefined"
              aria-label="同步时间止"
            />
          </div>
        </div>

        <button type="button" class="reset-btn" @click="resetFilters">重置筛选</button>
      </div>
    </div>

    <!-- 结果统计 -->
    <div class="result-meta">
      <template v-if="loading">加载中…</template>
      <template v-else-if="errorMsg">{{ errorMsg }}</template>
      <template v-else>
        共 <strong>{{ total }}</strong> 家
        <span class="dot">·</span>
        第 <strong>{{ pageNum }}</strong> / {{ totalPages }} 页
      </template>
    </div>

    <!-- 商品网格 -->
    <div v-if="!loading && providers.length === 0" class="empty-state">
      <template v-if="errorMsg">{{ errorMsg }}</template>
      <template v-else>没有匹配的中转站，试试重置筛选条件</template>
    </div>

    <div v-else class="product-grid" :class="{ 'is-loading': loading }">
      <ProviderProductCard
        v-for="provider in providers"
        :key="provider.id"
        :provider="provider"
        @view-detail="openDetail"
      />
    </div>

    <!-- 分页 -->
    <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
      <button
        type="button"
        class="page-btn"
        :disabled="pageNum <= 1 || loading"
        @click="changePage(pageNum - 1)"
      >
        上一页
      </button>

      <button
        v-for="(p, idx) in pageList"
        :key="`${p}-${idx}`"
        type="button"
        class="page-num"
        :class="{ active: p === pageNum, dots: p === '...' }"
        :disabled="p === '...' || loading"
        @click="typeof p === 'number' && changePage(p)"
      >
        {{ p }}
      </button>

      <button
        type="button"
        class="page-btn"
        :disabled="pageNum >= totalPages || loading"
        @click="changePage(pageNum + 1)"
      >
        下一页
      </button>
    </nav>
  </section>
</template>

<style scoped>
.directory-page {
  display: grid;
  gap: 1.1rem;
}

/* notice */
.notice {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  border-radius: var(--radius-lg);
  border: 1px dashed color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  background: var(--color-accent-soft);
  padding: 0.7rem 0.95rem;
}

.notice p {
  margin: 0;
  color: var(--color-accent-text);
  font-size: 0.82rem;
  line-height: 1.65;
}

.notice-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 1.4rem;
  padding: 0 0.55rem;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}

/* toolbar */
.toolbar {
  display: grid;
  gap: 0.7rem;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 0 0.95rem;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.search-box:focus-within {
  border-color: color-mix(in srgb, var(--color-accent) 60%, var(--color-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 18%, transparent);
}

.search-icon {
  flex-shrink: 0;
  margin-right: 0.55rem;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.search-input {
  flex: 1;
  appearance: none;
  border: none;
  outline: none;
  background: transparent;
  padding: 0.7rem 0;
  font-size: 0.92rem;
  color: var(--color-text-primary);
  font-family: inherit;
}

.search-input::placeholder {
  color: var(--color-text-secondary);
}

.search-clear {
  appearance: none;
  border: none;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 999px;
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.search-clear:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

/* filter */
.filter-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.6rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 0.85rem 0.95rem;
  box-shadow: var(--shadow-sm);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.filter-group .filter-label {
  flex-shrink: 0;
  width: 4.5rem;
}

.filter-label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.chip-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.chip {
  appearance: none;
  display: inline-flex;
  align-items: center;
  padding: 0.3rem 0.8rem;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 600;
  background: var(--color-bg-soft);
  border: 1px solid transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.chip:hover {
  color: var(--color-text-primary);
}

.chip.active {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
  border-color: var(--color-text-primary);
}

.date-range {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.date-input {
  appearance: none;
  border: 1px solid var(--color-border);
  border-radius: 0.55rem;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.78rem;
  font-family: inherit;
  padding: 0.32rem 0.55rem;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.date-input:hover {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
}

.date-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--color-accent) 60%, var(--color-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 18%, transparent);
}

.date-sep {
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  user-select: none;
}

.reset-btn {
  align-self: flex-end;
  appearance: none;
  background: none;
  border: none;
  color: var(--color-accent-text);
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  padding: 0.3rem 0.5rem;
  border-radius: 0.5rem;
}

.reset-btn:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-hover);
}

/* result meta */
.result-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.result-meta strong {
  color: var(--color-text-primary);
  font-weight: 800;
}

.result-meta .dot {
  color: var(--color-border);
}

/* grid */
.product-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
  grid-auto-rows: 1fr;
  align-items: stretch;
  transition: opacity 0.15s ease;
}

@media (min-width: 640px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1440px) {
  .product-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.product-grid.is-loading {
  opacity: 0.55;
  pointer-events: none;
}

.empty-state {
  border-radius: var(--radius-lg);
  border: 1px dashed var(--color-border);
  padding: 3rem 1rem;
  text-align: center;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
}

/* pagination */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.5rem;
}

.page-btn,
.page-num {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2.25rem;
  height: 2.25rem;
  padding: 0 0.7rem;
  border-radius: 0.55rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.page-btn:hover:not(:disabled),
.page-num:hover:not(:disabled):not(.dots):not(.active) {
  border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
  color: var(--color-accent-text);
}

.page-num.active {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff;
  cursor: default;
}

.page-num.dots {
  border: none;
  background: transparent;
  cursor: default;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .filter-group .filter-label {
    width: auto;
  }
}
</style>
