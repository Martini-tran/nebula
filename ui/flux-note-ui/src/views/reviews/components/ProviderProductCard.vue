<script setup lang="ts">
import { computed } from 'vue'
import type { RelayProvider, RelayProviderPackage } from '../../../api/aiRelay'

const props = defineProps<{ provider: RelayProvider }>()

const formattedScore = computed(() => {
  const score = props.provider.recommend_score
  if (score == null) return '—'
  return Number(score).toFixed(1)
})

const minPrice = computed<{ price: number; currency: string | null } | null>(() => {
  const list = props.provider.packages ?? []
  let best: { price: number; currency: string | null } | null = null
  for (const pkg of list) {
    const p = Number(pkg.price ?? 0)
    if (!Number.isFinite(p) || p <= 0) continue
    if (best == null || p < best.price) {
      best = { price: p, currency: pkg.currency ?? null }
    }
  }
  return best
})

const recommendedPackage = computed<RelayProviderPackage | undefined>(() => {
  const list = props.provider.packages ?? []
  return list.find((pkg) => pkg.recommended) ?? list[0]
})

const vendorTags = computed<string[]>(() => {
  const set = new Set<string>()
  ;(props.provider.vendor_types ?? []).forEach((v) => v && set.add(v))
  ;(props.provider.models ?? []).forEach((m) => m.model_vendor && set.add(m.model_vendor))
  return Array.from(set).slice(0, 4)
})

const billingLabels = computed<string[]>(() => {
  const map: Record<string, string> = {
    usage: '按量',
    subscription: '月卡',
  }
  return (props.provider.billing_modes ?? [])
    .map((m) => map[m] ?? m)
    .filter(Boolean)
})

function priceText(price: number, currency: string | null) {
  const sym = currency === 'CNY' ? '¥' : currency === 'USD' ? '$' : currency ? `${currency} ` : '¥'
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
</script>

<template>
  <article class="product-card">
    <header class="product-head">
      <div class="logo" :title="provider.name">
        <img v-if="provider.logo_url" :src="provider.logo_url" :alt="provider.name" />
        <span v-else>{{ logoText(provider) }}</span>
      </div>
      <div class="score">
        <span class="score-num">{{ formattedScore }}</span>
        <span class="score-label">推荐分</span>
      </div>
    </header>

    <div class="product-body">
      <h3 class="product-name" :title="provider.name">{{ provider.name }}</h3>
      <p v-if="provider.description" class="product-desc">{{ provider.description }}</p>

      <div v-if="vendorTags.length || billingLabels.length" class="tag-row">
        <span v-for="tag in vendorTags" :key="`v-${tag}`" class="tag tag-vendor">{{ tag }}</span>
        <span v-for="tag in billingLabels" :key="`b-${tag}`" class="tag tag-billing">{{ tag }}</span>
      </div>
    </div>

    <footer class="product-footer">
      <div class="price">
        <template v-if="minPrice">
          <span class="price-label">起</span>
          <span class="price-value">{{ priceText(minPrice.price, minPrice.currency) }}</span>
        </template>
        <template v-else>
          <span class="price-pay-as-you-go">按量计费</span>
        </template>
        <span v-if="recommendedPackage" class="price-pkg">{{ recommendedPackage.name }}</span>
      </div>

      <div class="actions">
        <a
          v-if="provider.website_url"
          class="btn-ghost"
          :href="provider.website_url"
          target="_blank"
          rel="noopener"
        >
          官网
          <span aria-hidden="true">↗</span>
        </a>
        <button class="btn-primary" type="button">详情</button>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.product-card {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 0.75rem;
  padding: 1rem 1.1rem 0.9rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  min-height: 16rem;
}

.product-card:hover {
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

/* head */
.product-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem;
}

.logo {
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 0.65rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  font-weight: 800;
  font-size: 0.85rem;
  border: 1px solid var(--color-border);
  overflow: hidden;
  flex-shrink: 0;
}

.logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.score {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1;
}

.score-num {
  font-size: 1.25rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--color-accent);
}

.score-label {
  margin-top: 0.25rem;
  font-size: 0.65rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  font-weight: 600;
}

/* body */
.product-body {
  display: grid;
  gap: 0.5rem;
  align-content: start;
  min-height: 0;
}

.product-name {
  margin: 0;
  font-size: 1.05rem;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-desc {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.1rem;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 0.16rem 0.5rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 600;
  border: 1px solid var(--color-border);
}

.tag-vendor {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 25%, var(--color-border));
}

.tag-billing {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

/* footer */
.product-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
  padding-top: 0.7rem;
  border-top: 1px dashed var(--color-border);
}

.price {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  min-width: 0;
}

.price-label {
  font-size: 0.68rem;
  color: var(--color-text-secondary);
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.price-value {
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--color-accent-text);
  letter-spacing: -0.02em;
}

.price-pay-as-you-go {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.price-pkg {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  margin-left: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 6rem;
}

.actions {
  display: inline-flex;
  gap: 0.4rem;
  flex-shrink: 0;
}

.btn-ghost,
.btn-primary {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
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
</style>
