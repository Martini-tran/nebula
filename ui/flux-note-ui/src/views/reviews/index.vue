<script setup lang="ts">
import { computed, ref } from 'vue'
import ProviderCard from './components/ProviderCard.vue'
import { relayProviders, type RelayProvider } from '../../data/relayProviders'

type SortKey = 'recommend' | 'price' | 'stability'
type VendorFilter = 'all' | 'claude' | 'gpt' | 'gemini'
type BillingFilter = 'all' | 'usage' | 'subscription'

const sortKey = ref<SortKey>('recommend')
const vendorFilter = ref<VendorFilter>('all')
const billingFilter = ref<BillingFilter>('all')

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'recommend', label: '综合推荐' },
  { key: 'price', label: '价格优先' },
  { key: 'stability', label: '稳定性优先' },
]

const vendorOptions: { key: VendorFilter; label: string }[] = [
  { key: 'all', label: '全部厂商' },
  { key: 'claude', label: 'Claude' },
  { key: 'gpt', label: 'GPT' },
  { key: 'gemini', label: 'Gemini' },
]

const billingOptions: { key: BillingFilter; label: string }[] = [
  { key: 'all', label: '全部模式' },
  { key: 'usage', label: '按量计费' },
  { key: 'subscription', label: '月卡 / 周期' },
]

const minPrice = (provider: RelayProvider) => {
  const prices = provider.packages
    .map((p) => p.price)
    .filter((p) => Number.isFinite(p) && p > 0)
  return prices.length === 0 ? Number.POSITIVE_INFINITY : Math.min(...prices)
}

const filteredProviders = computed(() => {
  let list = [...relayProviders]

  if (vendorFilter.value !== 'all') {
    list = list.filter((p) =>
      p.vendorTypes.includes(vendorFilter.value as 'claude' | 'gpt' | 'gemini'),
    )
  }
  if (billingFilter.value !== 'all') {
    list = list.filter((p) =>
      p.billingModes.includes(billingFilter.value as 'usage' | 'subscription'),
    )
  }

  switch (sortKey.value) {
    case 'price':
      list.sort((a, b) => minPrice(a) - minPrice(b))
      break
    case 'stability':
      list.sort((a, b) => b.uptime3d - a.uptime3d)
      break
    case 'recommend':
    default:
      list.sort((a, b) => b.recommendScore - a.recommendScore)
      break
  }

  return list
})

const stats = computed(() => {
  const total = relayProviders.length
  const avgUptime =
    total === 0
      ? 0
      : relayProviders.reduce((acc, p) => acc + p.uptime3d, 0) / total
  const minStartingPrice = relayProviders.reduce<number | null>((acc, p) => {
    const m = minPrice(p)
    if (!Number.isFinite(m)) return acc
    return acc == null ? m : Math.min(acc, m)
  }, null)
  return {
    total,
    avgUptime,
    minStartingPrice,
  }
})

function resetFilters() {
  sortKey.value = 'recommend'
  vendorFilter.value = 'all'
  billingFilter.value = 'all'
}
</script>

<template>
  <section class="review-page">
    <!-- ── Hero ── -->
    <header class="review-hero">
      <div class="hero-text">
        <p class="hero-eyebrow">AI Relay Reviews</p>
        <h1 class="hero-title">中转站测评</h1>
        <p class="hero-desc">
          自费购买、长期使用、不接广告的真实评测。<br />
          稳定性、价格、模型覆盖、支付方式 — 用一张卡看清每家中转。
        </p>
        <div class="hero-actions">
          <button type="button" class="btn-primary">帮我选一家</button>
          <a class="btn-ghost" href="#methodology">评分方法</a>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-card">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">已收录</span>
        </div>
        <div class="stat-card">
          <span class="stat-num">{{ stats.avgUptime.toFixed(2) }}%</span>
          <span class="stat-label">3 日均可用率</span>
        </div>
        <div class="stat-card">
          <span class="stat-num">
            <template v-if="stats.minStartingPrice != null">¥{{ stats.minStartingPrice }}</template>
            <template v-else>—</template>
          </span>
          <span class="stat-label">起价（CNY）</span>
        </div>
      </div>
    </header>

    <!-- ── 风险提示 ── -->
    <aside class="notice">
      <span class="notice-tag">提醒</span>
      <p>
        中转站行业波动较大，定价、模型供应、可用性可能随时变化。下方信息为编辑使用期间的真实记录，
        购买前请以官网为准，避免大额预付。
      </p>
    </aside>

    <!-- ── 筛选条 ── -->
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
        <span class="filter-label">计费</span>
        <div class="chip-row">
          <button
            v-for="opt in billingOptions"
            :key="opt.key"
            type="button"
            class="chip"
            :class="{ active: billingFilter === opt.key }"
            @click="billingFilter = opt.key"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <button type="button" class="reset-btn" @click="resetFilters">重置筛选</button>
    </div>

    <!-- ── 结果统计 ── -->
    <div class="result-meta">
      共 <strong>{{ filteredProviders.length }}</strong> 家中转
      <span class="dot">·</span>
      数据每 30 分钟更新一次
    </div>

    <!-- ── 卡片列表 ── -->
    <div class="provider-list">
      <ProviderCard
        v-for="provider in filteredProviders"
        :key="provider.id"
        :provider="provider"
      />
      <div v-if="filteredProviders.length === 0" class="empty-state">
        没有匹配的中转站，试试重置筛选条件
      </div>
    </div>

    <!-- ── 评分方法 ── -->
    <section id="methodology" class="methodology">
      <p class="block-eyebrow">Methodology</p>
      <h2>评分怎么来的</h2>
      <ul>
        <li><strong>综合推荐分</strong>：编辑站点评分 × 3 日实际可用率，可用率不足 95% 直接降权。</li>
        <li><strong>稳定性</strong>：每 5 分钟探测一次主流模型，统计 24 小时与 3 天的成功率。</li>
        <li><strong>价格优先</strong>：取该中转最便宜的一档套餐起价排序，按量与月卡分开比较。</li>
        <li>所有套餐由编辑自费购买，不接受厂商补贴或带链分成。</li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.review-page {
  display: grid;
  gap: 1.25rem;
}

/* ---------------- Hero ---------------- */
.review-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: clamp(1rem, 3vw, 2rem);
  align-items: stretch;
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background:
    radial-gradient(1200px 280px at 100% 0%, color-mix(in srgb, var(--color-accent) 14%, transparent), transparent 60%),
    var(--color-bg-surface);
  padding: clamp(1.4rem, 4vw, 2.5rem);
  box-shadow: var(--shadow-sm);
}

.hero-eyebrow {
  margin: 0 0 0.5rem;
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-title {
  margin: 0;
  font-size: clamp(2.1rem, 5vw, 3.4rem);
  letter-spacing: -0.05em;
  color: var(--color-text-primary);
  line-height: 1.05;
}

.hero-desc {
  margin: 1rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.85;
  max-width: 36rem;
}

.hero-actions {
  display: inline-flex;
  gap: 0.6rem;
  margin-top: 1.4rem;
  flex-wrap: wrap;
}

.btn-primary,
.btn-ghost {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.65rem 1.2rem;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.btn-primary {
  background: var(--color-brand);
  color: #fff;
  border: 1px solid transparent;
}

.btn-primary:hover {
  background: var(--color-brand-hover);
}

.btn-ghost {
  background: transparent;
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.btn-ghost:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  align-content: end;
}

.stat-card {
  display: grid;
  gap: 0.25rem;
  padding: 0.95rem 1rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border);
}

.stat-num {
  font-size: clamp(1.1rem, 2.4vw, 1.45rem);
  font-weight: 800;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  font-weight: 600;
}

/* ---------------- Notice ---------------- */
.notice {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  border-radius: var(--radius-lg);
  border: 1px dashed color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  background: var(--color-accent-soft);
  padding: 0.85rem 1rem;
}

.notice p {
  margin: 0;
  color: var(--color-accent-text);
  font-size: 0.85rem;
  line-height: 1.7;
}

.notice-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 1.5rem;
  padding: 0 0.6rem;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}

/* ---------------- Filter ---------------- */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem 1.5rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 0.85rem 1rem;
  box-shadow: var(--shadow-sm);
}

.filter-group {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.chip-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.chip {
  appearance: none;
  display: inline-flex;
  align-items: center;
  padding: 0.34rem 0.85rem;
  border-radius: 999px;
  font-size: 0.78rem;
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

.reset-btn {
  margin-left: auto;
  appearance: none;
  background: none;
  border: none;
  color: var(--color-accent-text);
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  padding: 0.3rem 0.5rem;
  border-radius: 0.5rem;
}

.reset-btn:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-hover);
}

/* ---------------- Result meta ---------------- */
.result-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.result-meta strong {
  color: var(--color-text-primary);
  font-weight: 800;
}

.result-meta .dot {
  color: var(--color-border);
}

/* ---------------- Provider list ---------------- */
.provider-list {
  display: grid;
  gap: 1rem;
}

.empty-state {
  border-radius: var(--radius-lg);
  border: 1px dashed var(--color-border);
  padding: 3rem 1rem;
  text-align: center;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
}

/* ---------------- Methodology ---------------- */
.methodology {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: clamp(1.2rem, 3vw, 2rem);
  box-shadow: var(--shadow-sm);
}

.methodology .block-eyebrow {
  margin: 0 0 0.45rem;
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.methodology h2 {
  margin: 0;
  font-size: clamp(1.4rem, 3vw, 1.85rem);
  letter-spacing: -0.03em;
  color: var(--color-text-primary);
}

.methodology ul {
  margin: 1rem 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.55rem;
}

.methodology li {
  position: relative;
  padding-left: 1.1rem;
  color: var(--color-text-secondary);
  line-height: 1.75;
  font-size: 0.9rem;
}

.methodology li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.65rem;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 999px;
  background: var(--color-accent);
}

.methodology strong {
  color: var(--color-text-primary);
  font-weight: 700;
}

/* ---------------- Responsive ---------------- */
@media (max-width: 880px) {
  .review-hero {
    grid-template-columns: 1fr;
  }

  .hero-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-content: start;
  }
}

@media (max-width: 560px) {
  .hero-stats {
    grid-template-columns: 1fr;
  }

  .reset-btn {
    margin-left: 0;
  }
}
</style>
