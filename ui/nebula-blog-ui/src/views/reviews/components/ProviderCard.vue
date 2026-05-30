<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { RelayPackageModel, RelayProvider } from '../../../api/aiRelay'

const props = defineProps<{ provider: RelayProvider; rank?: number }>()

const PACKAGE_MODEL_PREVIEW = 6
const expandedPackages = reactive<Record<number | string, boolean>>({})

function isExpanded(pkgId: number | string) {
  return !!expandedPackages[pkgId]
}

function toggleExpand(pkgId: number | string) {
  expandedPackages[pkgId] = !expandedPackages[pkgId]
}

function visibleModels(pkg: { id: number | string; models?: RelayPackageModel[] }) {
  const list = pkg.models ?? []
  return isExpanded(pkg.id) ? list : list.slice(0, PACKAGE_MODEL_PREVIEW)
}

function hiddenCount(pkg: { id: number | string; models?: RelayPackageModel[] }) {
  const list = pkg.models ?? []
  return Math.max(0, list.length - PACKAGE_MODEL_PREVIEW)
}

const formattedScore = computed(() => {
  const score = props.provider.recommend_score
  if (score == null) return '—'
  return Number(score).toFixed(1)
})

const advantageKindClass = (type: number | null | undefined) => ({
  'is-core': type === 2,
  'is-risk': type === 3,
})

function priceLabel(price: number | null | undefined, currency: string | null | undefined) {
  if (price == null || price === 0) return '按量计费'
  const sym = currency === 'CNY' ? '¥' : currency === 'USD' ? '$' : currency ? `${currency} ` : ''
  return `${sym}${price}`
}

function logoText(provider: RelayProvider) {
  if (provider.logo_text) return provider.logo_text
  if (!provider.name) return ''
  return provider.name
    .replace(/[^A-Za-z一-龥]/g, '')
    .slice(0, 2)
    .toUpperCase()
}

function formatTokens(tokens: number) {
  if (!Number.isFinite(tokens) || tokens <= 0) return ''
  if (tokens >= 1000) {
    const k = tokens / 1000
    const text = Number.isInteger(k) ? `${k}` : k.toFixed(1)
    return `${text}K ctx`
  }
  return `${tokens} ctx`
}

type ModelMeta = {
  kind: 'vendor' | 'multiplier' | 'context' | 'charge'
  text: string
}

function modelMeta(m: RelayPackageModel): ModelMeta[] {
  const out: ModelMeta[] = []
  if (m.model_vendor) out.push({ kind: 'vendor', text: m.model_vendor })
  if (m.consume_multiplier != null && Number(m.consume_multiplier) !== 1) {
    out.push({ kind: 'multiplier', text: `×${Number(m.consume_multiplier)}` })
  }
  if (m.max_context_tokens) {
    const ctx = formatTokens(Number(m.max_context_tokens))
    if (ctx) out.push({ kind: 'context', text: ctx })
  }
  if (m.min_charge_amount != null && Number(m.min_charge_amount) > 0) {
    out.push({ kind: 'charge', text: `最低 ${m.min_charge_amount}` })
  }
  return out
}
</script>

<template>
  <article class="provider-card">
    <!-- 头部：名次 + Logo + 名称 + 推荐分 -->
    <header class="card-head">
      <div v-if="rank != null" class="rank-block">
        <span class="rank-num">#{{ rank }}</span>
      </div>
      <div class="logo" :title="provider.name">
        <img v-if="provider.logo_url" :src="provider.logo_url" :alt="provider.name" />
        <span v-else>{{ logoText(provider) }}</span>
      </div>
      <div class="head-info">
        <div class="head-title">
          <h3>{{ provider.name }}</h3>
          <a
            v-if="provider.website_url"
            class="site-link"
            :href="provider.website_url"
            target="_blank"
            rel="noopener"
          >
            官网
            <span aria-hidden="true">↗</span>
          </a>
        </div>
        <p v-if="provider.description" class="head-desc">{{ provider.description }}</p>
      </div>
      <div class="score-block">
        <div class="score-num">{{ formattedScore }}</div>
        <div class="score-label">综合推荐分</div>
      </div>
    </header>

    <!-- 套餐 -->
    <section v-if="provider.packages?.length" class="block">
      <div class="block-header">
        <span class="block-title">套餐</span>
        <span class="block-meta">{{ provider.packages.length }} 个方案</span>
      </div>
      <div class="package-grid">
        <div
          v-for="pkg in provider.packages"
          :key="pkg.id"
          class="package-card"
          :class="{ 'package-card--recommended': pkg.recommended }"
        >
          <div class="package-head">
            <div class="package-head-left">
              <span class="package-type">{{ pkg.package_type_name ?? pkg.package_type_code }}</span>
              <span class="package-name">{{ pkg.name }}</span>
              <span v-if="pkg.recommended" class="package-tag-recommend">推荐</span>
            </div>
            <div class="package-price">
              <span class="price-now">{{ priceLabel(pkg.price, pkg.currency) }}</span>
              <span v-if="pkg.original_price" class="price-origin">
                {{ priceLabel(pkg.original_price, pkg.currency) }}
              </span>
            </div>
          </div>

          <div v-if="pkg.quota_summary" class="package-quota">{{ pkg.quota_summary }}</div>
          <div v-if="pkg.description" class="package-desc">{{ pkg.description }}</div>

          <div v-if="pkg.models?.length" class="package-models-wrap">
            <div class="package-models-head">
              <span class="package-models-label">绑定模型 · {{ pkg.models.length }}</span>
              <button
                v-if="hiddenCount(pkg) > 0"
                type="button"
                class="package-models-toggle"
                @click="toggleExpand(pkg.id)"
              >
                {{ isExpanded(pkg.id) ? '收起' : `展开剩余 ${hiddenCount(pkg)}` }}
              </button>
            </div>
            <ul class="package-models">
              <li
                v-for="m in visibleModels(pkg)"
                :key="m.id"
                class="package-model"
                :class="{ 'package-model--default': m.is_default }"
              >
                <div class="package-model__head">
                  <span class="package-model__name">
                    {{ m.model_name ?? m.model_code ?? m.provider_model_code }}
                  </span>
                  <span v-if="m.is_default" class="package-model__badge">默认</span>
                </div>
                <div v-if="modelMeta(m).length" class="package-model__meta">
                  <span
                    v-for="meta in modelMeta(m)"
                    :key="`${meta.kind}-${meta.text}`"
                    class="package-model__meta-item"
                    :class="`package-model__meta-item--${meta.kind}`"
                  >
                    {{ meta.text }}
                  </span>
                </div>
                <div
                  v-if="m.provider_model_code && m.provider_model_code !== m.model_code"
                  class="package-model__alias"
                  :title="`服务商映射：${m.provider_model_code}`"
                >
                  ↳ {{ m.provider_model_code }}
                </div>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 模型 -->
    <section v-if="provider.models?.length" class="block">
      <div class="block-header">
        <span class="block-title">支持模型</span>
        <span class="block-meta">来自上线套餐的去重清单</span>
      </div>
      <ul class="model-list">
        <li v-for="model in provider.models" :key="model.id" class="model-item">
          <div class="model-info">
            <strong>{{ model.name ?? model.code }}</strong>
            <span v-if="model.code" class="model-code">{{ model.code }}</span>
          </div>
          <div v-if="model.model_vendor" class="model-stats">
            <span class="stat-pill stat-cache">{{ model.model_vendor }}</span>
          </div>
        </li>
      </ul>
    </section>

    <!-- 优势 / 支付 -->
    <div class="meta-grid">
      <section v-if="provider.advantages?.length" class="meta-block">
        <div class="block-header">
          <span class="block-title">优势</span>
        </div>
        <ul class="meta-list meta-list--good">
          <li
            v-for="item in provider.advantages"
            :key="item.id"
            :class="advantageKindClass(item.advantage_type)"
          >
            <span class="meta-dot" aria-hidden="true">●</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p v-if="item.content">{{ item.content }}</p>
            </div>
          </li>
        </ul>
      </section>

      <section v-if="provider.payment_methods?.length" class="meta-block">
        <div class="block-header">
          <span class="block-title">支付方式</span>
        </div>
        <div class="payment-row">
          <span v-for="m in provider.payment_methods" :key="m.id" class="badge-soft">{{ m.name }}</span>
        </div>
      </section>
    </div>

    <!-- 底部 CTA -->
    <footer class="card-footer">
      <div class="footer-hint">
        <span v-if="provider.packages?.length">共 {{ provider.packages.length }} 个套餐方案</span>
        <span v-else>暂无套餐</span>
      </div>
      <div class="footer-actions">
        <a
          v-if="provider.website_url"
          class="btn-ghost"
          :href="provider.website_url"
          target="_blank"
          rel="noopener"
        >
          访问官网
        </a>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.provider-card {
  display: grid;
  gap: 1.25rem;
  padding: clamp(1.1rem, 2.5vw, 1.6rem);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.provider-card:hover {
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

/* ---------------- Head ---------------- */
.card-head {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: 1rem;
  align-items: flex-start;
}

.rank-block {
  align-self: stretch;
  display: flex;
  align-items: flex-start;
}

.rank-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2.5rem;
  padding: 0.3rem 0.55rem;
  border-radius: 0.6rem;
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  font-weight: 800;
  font-size: 0.95rem;
  letter-spacing: -0.02em;
}

.logo {
  width: 3rem;
  height: 3rem;
  border-radius: 0.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  font-weight: 800;
  font-size: 0.95rem;
  letter-spacing: 0.02em;
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.head-info {
  min-width: 0;
}

.head-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.head-title h3 {
  margin: 0;
  font-size: 1.2rem;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.site-link {
  font-size: 0.78rem;
  color: var(--color-accent-text);
  text-decoration: none;
  font-weight: 600;
}

.site-link:hover {
  color: var(--color-accent-hover);
}

.head-desc {
  margin: 0.4rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
  font-size: 0.9rem;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.65rem;
}

.badge-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.22rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

/* 评分 */
.score-block {
  text-align: right;
  min-width: 7.5rem;
}

.score-num {
  font-size: 1.8rem;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: var(--color-accent);
  line-height: 1;
}

.score-label {
  margin-top: 0.25rem;
  font-size: 0.7rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.uptime-row {
  margin-top: 0.55rem;
  display: inline-flex;
  gap: 0.35rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  background: var(--color-bg-soft);
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
}

/* ---------------- Block ---------------- */
.block,
.meta-block {
  display: grid;
  gap: 0.65rem;
}

.block-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.block-title {
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--color-text-primary);
}

.block-meta {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

/* 套餐：每行一条，避免多卡同步等高 */
.package-grid {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.package-card {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
  padding: 0.85rem 1rem;
}

.package-card--recommended {
  border-color: color-mix(in srgb, var(--color-accent) 55%, var(--color-border));
  background: var(--color-accent-soft);
}

.package-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.package-head-left {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.package-type {
  font-size: 0.7rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  font-weight: 700;
}

.package-tag-recommend {
  font-size: 0.7rem;
  background: var(--color-accent);
  color: #fff;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  font-weight: 700;
}

.package-name {
  font-weight: 700;
  color: var(--color-text-primary);
}

.package-price {
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
}

.price-now {
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--color-accent-text);
}

.price-origin {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-decoration: line-through;
}

.package-quota,
.package-desc {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

/* 套餐内绑定的模型列表 */
.package-models-wrap {
  margin-top: 0.55rem;
  padding-top: 0.55rem;
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 60%, transparent);
  display: grid;
  gap: 0.45rem;
}

.package-models-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.package-models-label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.package-models-toggle {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--color-accent-text);
  font-size: 0.74rem;
  font-weight: 700;
  cursor: pointer;
  padding: 0.18rem 0.5rem;
  border-radius: 0.4rem;
  transition: background 0.15s ease, color 0.15s ease;
}

.package-models-toggle:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-hover);
}

.package-models {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.45rem;
}

.package-model {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.22rem 0.6rem 0.22rem 0.4rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-bg-canvas) 70%, var(--color-bg-soft));
  border: 1px solid color-mix(in srgb, var(--color-border) 80%, transparent);
  font-size: 0.74rem;
  line-height: 1.4;
  max-width: 100%;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.package-model:hover {
  background: var(--color-bg-canvas);
  border-color: color-mix(in srgb, var(--color-accent) 28%, var(--color-border));
}

.package-model--default {
  border-color: color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  background: color-mix(in srgb, var(--color-accent) 10%, var(--color-bg-canvas));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 12%, transparent);
}

.package-model--default:hover {
  border-color: color-mix(in srgb, var(--color-accent) 65%, var(--color-border));
}

.package-model__head {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.package-model__name {
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 14rem;
}

.package-model--default .package-model__name {
  color: var(--color-accent-text);
}

.package-model__badge {
  font-size: 0.6rem;
  font-weight: 800;
  padding: 0.04rem 0.42rem;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  flex-shrink: 0;
  letter-spacing: 0.06em;
  box-shadow: 0 1px 0 color-mix(in srgb, var(--color-accent) 35%, transparent);
}

.package-model__meta {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 0.3rem;
  align-items: center;
}

.package-model__meta-item {
  display: inline-flex;
  align-items: center;
  padding: 0.04rem 0.42rem;
  border-radius: 0.34rem;
  font-size: 0.66rem;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

/* 厂商：中性 + 微强调 */
.package-model__meta-item--vendor {
  background: color-mix(in srgb, var(--color-text-primary) 6%, transparent);
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-text-primary) 12%, transparent);
}

/* 倍率：紫色，提醒计费倍数 */
.package-model__meta-item--multiplier {
  background: color-mix(in srgb, #8b5cf6 14%, var(--color-bg-canvas));
  color: #6d28d9;
  border-color: color-mix(in srgb, #8b5cf6 30%, transparent);
}

:global(.dark) .package-model__meta-item--multiplier {
  color: #c4b5fd;
}

/* 上下文：青色，技术规格感 */
.package-model__meta-item--context {
  background: color-mix(in srgb, #0891b2 14%, var(--color-bg-canvas));
  color: #0e7490;
  border-color: color-mix(in srgb, #0891b2 30%, transparent);
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
}

:global(.dark) .package-model__meta-item--context {
  color: #67e8f9;
}

/* 最低扣费：琥珀，金钱相关 */
.package-model__meta-item--charge {
  background: color-mix(in srgb, #d97706 14%, var(--color-bg-canvas));
  color: #b45309;
  border-color: color-mix(in srgb, #d97706 30%, transparent);
}

:global(.dark) .package-model__meta-item--charge {
  color: #fcd34d;
}

.package-model__alias {
  font-size: 0.66rem;
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 10rem;
  opacity: 0.75;
}

/* 模型 */
.model-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.5rem;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0.8rem;
  border-radius: 0.7rem;
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border);
}

.model-info {
  display: flex;
  align-items: baseline;
  gap: 0.55rem;
  min-width: 0;
}

.model-info strong {
  color: var(--color-text-primary);
}

.model-code {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
}

.model-stats {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
}

.stat-pill {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.18rem 0.5rem;
  border-radius: 999px;
}

.stat-uptime {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.stat-cache {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

/* 优势 / 不足 / 支付 / 近期更新 */
.meta-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
}

.meta-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.5rem;
}

.meta-list li {
  display: flex;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.meta-list strong {
  color: var(--color-text-primary);
  font-weight: 700;
}

.meta-list p {
  margin: 0.15rem 0 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.meta-dot {
  margin-top: 0.45rem;
  font-size: 0.45rem;
  color: var(--color-accent);
  flex-shrink: 0;
}

.meta-list--good .is-core .meta-dot {
  color: var(--color-accent);
}

.meta-list--good .is-risk .meta-dot,
.meta-list--warn .meta-dot {
  color: #d97706;
}

.payment-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.badge-soft {
  display: inline-flex;
  align-items: center;
  padding: 0.22rem 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
  font-weight: 600;
  border: 1px solid var(--color-border);
}

.update-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.5rem;
}

.update-list li {
  display: grid;
  grid-template-columns: 5rem 1fr;
  gap: 0.55rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.update-time {
  color: var(--color-accent-text);
  font-weight: 600;
}

/* 底部 CTA */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.65rem;
  padding-top: 1rem;
  border-top: 1px dashed var(--color-border);
}

.footer-hint {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.footer-hint strong {
  color: var(--color-text-primary);
}

.footer-actions {
  display: inline-flex;
  gap: 0.55rem;
}

.btn-ghost,
.btn-primary {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  padding: 0.55rem 1rem;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.btn-ghost {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
}

.btn-ghost:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.btn-primary {
  background: var(--color-brand);
  color: #fff;
  border: 1px solid transparent;
}

.btn-primary:hover {
  background: var(--color-brand-hover);
}

/* 移动端适配 */
@media (max-width: 760px) {
  .card-head {
    grid-template-columns: auto 1fr;
    grid-template-areas:
      'rank score'
      'logo info';
  }

  .rank-block {
    grid-area: rank;
  }

  .logo {
    grid-area: logo;
  }

  .head-info {
    grid-area: info;
  }

  .score-block {
    grid-area: score;
    text-align: right;
  }
}
</style>
