<script setup lang="ts">
import { computed } from 'vue'
import type { RelayProvider } from '../../../data/relayProviders'

const props = defineProps<{ provider: RelayProvider }>()

const formattedScore = computed(() => props.provider.recommendScore.toFixed(1))

const formattedUptime24 = computed(() =>
  props.provider.uptime24h.toFixed(2),
)

const formattedUptime3d = computed(() =>
  props.provider.uptime3d.toFixed(2),
)

const recommendedPackage = computed(() =>
  props.provider.packages.find((pkg) => pkg.recommended) ?? props.provider.packages[0],
)

function priceLabel(price: number, currency: string) {
  if (price === 0) return '按量计费'
  return `${currency === 'CNY' ? '¥' : currency === 'USD' ? '$' : `${currency} `}${price}`
}
</script>

<template>
  <article class="provider-card">
    <!-- 头部：名次 + Logo + 名称 + 推荐分 -->
    <header class="card-head">
      <div class="rank-block">
        <span class="rank-num">#{{ provider.rank }}</span>
      </div>
      <div class="logo" :title="provider.name">{{ provider.logoText }}</div>
      <div class="head-info">
        <div class="head-title">
          <h3>{{ provider.name }}</h3>
          <a class="site-link" :href="provider.websiteUrl" target="_blank" rel="noopener">
            官网
            <span aria-hidden="true">↗</span>
          </a>
        </div>
        <p class="head-desc">{{ provider.description }}</p>
        <div class="tag-row">
          <span v-for="tag in provider.tags" :key="tag" class="badge-tag">{{ tag }}</span>
        </div>
      </div>
      <div class="score-block">
        <div class="score-num">{{ formattedScore }}</div>
        <div class="score-label">综合推荐分</div>
        <div class="uptime-row">
          <span>24h {{ formattedUptime24 }}%</span>
          <span>·</span>
          <span>3d {{ formattedUptime3d }}%</span>
        </div>
      </div>
    </header>

    <!-- 套餐 -->
    <section class="block">
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
            <span class="package-type">{{ pkg.typeName }}</span>
            <span v-if="pkg.recommended" class="package-tag-recommend">推荐</span>
          </div>
          <div class="package-name">{{ pkg.name }}</div>
          <div class="package-price">
            <span class="price-now">{{ priceLabel(pkg.price, pkg.currency) }}</span>
            <span v-if="pkg.originalPrice" class="price-origin">
              {{ priceLabel(pkg.originalPrice, pkg.currency) }}
            </span>
          </div>
          <div v-if="pkg.quotaSummary" class="package-quota">{{ pkg.quotaSummary }}</div>
          <div v-if="pkg.description" class="package-desc">{{ pkg.description }}</div>
        </div>
      </div>
    </section>

    <!-- 模型 -->
    <section class="block">
      <div class="block-header">
        <span class="block-title">支持模型</span>
        <span class="block-meta">3 日可用率 · 缓存命中</span>
      </div>
      <ul class="model-list">
        <li v-for="model in provider.models" :key="model.code" class="model-item">
          <div class="model-info">
            <strong>{{ model.name }}</strong>
            <span class="model-code">{{ model.code }}</span>
          </div>
          <div class="model-stats">
            <span v-if="model.availability != null" class="stat-pill stat-uptime">
              {{ model.availability.toFixed(1) }}%
            </span>
            <span v-if="model.cacheHit != null" class="stat-pill stat-cache">
              缓存 {{ model.cacheHit }}%
            </span>
          </div>
        </li>
      </ul>
    </section>

    <!-- 优势 / 不足 / 支付 / 更新 -->
    <div class="meta-grid">
      <section class="meta-block">
        <div class="block-header">
          <span class="block-title">优势</span>
        </div>
        <ul class="meta-list meta-list--good">
          <li
            v-for="(item, idx) in provider.advantages"
            :key="`adv-${idx}`"
            :class="{
              'is-core': item.kind === 'core',
              'is-risk': item.kind === 'risk',
            }"
          >
            <span class="meta-dot" aria-hidden="true">●</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p v-if="item.content">{{ item.content }}</p>
            </div>
          </li>
        </ul>
      </section>

      <section v-if="provider.improvements?.length" class="meta-block">
        <div class="block-header">
          <span class="block-title">待改进</span>
        </div>
        <ul class="meta-list meta-list--warn">
          <li v-for="(item, idx) in provider.improvements" :key="`imp-${idx}`">
            <span class="meta-dot" aria-hidden="true">●</span>
            <span>{{ item }}</span>
          </li>
        </ul>
      </section>

      <section class="meta-block">
        <div class="block-header">
          <span class="block-title">支付方式</span>
        </div>
        <div class="payment-row">
          <span v-for="m in provider.paymentMethods" :key="m" class="badge-soft">{{ m }}</span>
        </div>
      </section>

      <section v-if="provider.recentUpdates?.length" class="meta-block">
        <div class="block-header">
          <span class="block-title">近期更新</span>
        </div>
        <ul class="update-list">
          <li v-for="(item, idx) in provider.recentUpdates" :key="`upd-${idx}`">
            <span class="update-time">{{ item.time }}</span>
            <span>{{ item.text }}</span>
          </li>
        </ul>
      </section>
    </div>

    <!-- 底部 CTA -->
    <footer class="card-footer">
      <div class="footer-hint">
        推荐套餐：
        <strong v-if="recommendedPackage">{{ recommendedPackage.name }}</strong>
      </div>
      <div class="footer-actions">
        <a class="btn-ghost" :href="provider.websiteUrl" target="_blank" rel="noopener">
          访问官网
        </a>
        <button class="btn-primary" type="button">查看详情</button>
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

/* 套餐 */
.package-grid {
  display: grid;
  gap: 0.7rem;
  grid-template-columns: repeat(auto-fill, minmax(15.5rem, 1fr));
}

.package-card {
  display: grid;
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
  gap: 0.4rem;
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
