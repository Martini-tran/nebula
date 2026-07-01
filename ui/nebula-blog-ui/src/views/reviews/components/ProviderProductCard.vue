<script setup lang="ts">
import { computed } from 'vue'
import type { RelayProvider, RelayProviderPackage } from '../../../api/aiRelay'

const props = defineProps<{ provider: RelayProvider }>()

const emit = defineEmits<{
  (e: 'view-detail', provider: RelayProvider): void
}>()

const formattedScore = computed(() => {
  const score = props.provider.recommendScore
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

const VENDOR_LABELS: Record<string, string> = {
  gpt: 'GPT',
  claude: 'Claude',
  gemini: 'Gemini',
}

function vendorLabel(value: string) {
  const key = value.trim().toLowerCase()
  if (VENDOR_LABELS[key]) return VENDOR_LABELS[key]
  return key.charAt(0).toUpperCase() + key.slice(1)
}

const vendorTags = computed<string[]>(() => {
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of props.provider.vendorTypes ?? []) {
    if (!raw) continue
    const key = raw.trim().toLowerCase()
    if (!key || seen.has(key)) continue
    seen.add(key)
    result.push(vendorLabel(key))
  }
  return result.slice(0, 4)
})

const billingLabels = computed<string[]>(() => {
  const map: Record<string, string> = {
    usage: '按量',
    subscription: '月卡',
  }
  return (props.provider.billingModes ?? [])
    .map((m) => map[m] ?? m)
    .filter(Boolean)
})

const MODEL_PREVIEW = 6

const modelCodes = computed<string[]>(() => {
  const set = new Set<string>()
  for (const m of props.provider.models ?? []) {
    const code = m.code?.trim()
    if (code) set.add(code)
  }
  return Array.from(set)
})

const visibleModelCodes = computed(() => modelCodes.value.slice(0, MODEL_PREVIEW))
const hiddenModelCount = computed(() =>
  Math.max(0, modelCodes.value.length - visibleModelCodes.value.length),
)

function priceText(price: number, currency: string | null) {
  const sym = currency === 'CNY' ? '¥' : currency === 'USD' ? '$' : currency ? `${currency} ` : '¥'
  return `${sym}${price}`
}

function formatDate(value?: string | null) {
  if (!value) return ''
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const listedDate = computed(() => formatDate(props.provider.createTime))
const syncedDate = computed(() => formatDate(props.provider.lastSyncTime))

function logoText(provider: RelayProvider) {
  if (provider.logoText) return provider.logoText
  if (!provider.name) return ''
  return provider.name
    .replace(/[^A-Za-z一-龥]/g, '')
    .slice(0, 2)
    .toUpperCase()
}
</script>

<template>
  <article class="product-card">
    <header class="card-head">
      <div class="logo" :title="provider.name">
        <img v-if="provider.logoUrl" :src="provider.logoUrl" :alt="provider.name" />
        <span v-else>{{ logoText(provider) }}</span>
      </div>
      <div class="head-text">
        <h3 class="provider-name" :title="provider.name">{{ provider.name }}</h3>
        <div v-if="vendorTags.length || billingLabels.length" class="tag-row">
          <span v-for="tag in vendorTags" :key="`v-${tag}`" class="tag tag-vendor">{{ tag }}</span>
          <span v-for="tag in billingLabels" :key="`b-${tag}`" class="tag tag-billing">{{ tag }}</span>
        </div>
      </div>
      <div class="score-pill" :title="`推荐分 ${formattedScore}`">
        <span class="score-num">{{ formattedScore }}</span>
        <span class="score-label">分</span>
      </div>
    </header>

    <p v-if="provider.description" class="provider-desc">{{ provider.description }}</p>

    <dl v-if="listedDate || syncedDate" class="time-meta">
      <div v-if="listedDate" class="time-meta__item">
        <dt>收录</dt>
        <dd>{{ listedDate }}</dd>
      </div>
      <div v-if="syncedDate" class="time-meta__item">
        <dt>同步</dt>
        <dd>{{ syncedDate }}</dd>
      </div>
    </dl>

    <section v-if="visibleModelCodes.length" class="models">
      <span class="section-label">支持模型</span>
      <ul class="model-codes" :title="modelCodes.join('\n')">
        <li v-for="code in visibleModelCodes" :key="code" class="model-code">{{ code }}</li>
        <li v-if="hiddenModelCount > 0" class="model-code model-code--more">+{{ hiddenModelCount }}</li>
      </ul>
    </section>

    <footer class="card-footer">
      <div class="price-block">
        <template v-if="minPrice">
          <span class="price-label">起</span>
          <span class="price-value">{{ priceText(minPrice.price, minPrice.currency) }}</span>
        </template>
        <template v-else>
          <span class="price-pay-as-you-go">按量计费</span>
        </template>
        <span v-if="recommendedPackage" class="price-pkg" :title="recommendedPackage.name">
          {{ recommendedPackage.name }}
        </span>
      </div>

      <div class="actions">
        <a
          v-if="provider.websiteUrl"
          class="btn-ghost"
          :href="provider.websiteUrl"
          target="_blank"
          rel="noopener"
        >
          官网
          <span aria-hidden="true">↗</span>
        </a>
        <button class="btn-primary" type="button" @click="emit('view-detail', provider)">详情</button>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1.1rem 1.15rem 1rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  height: 100%;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.product-card:hover {
  border-color: color-mix(in srgb, var(--color-accent) 38%, var(--color-border));
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

/* head */
.card-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.7rem;
}

.logo {
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 0.7rem;
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

.head-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.provider-name {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 0.12rem 0.5rem;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 600;
  line-height: 1.5;
  border: 1px solid transparent;
  white-space: nowrap;
}

.tag-vendor {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 25%, transparent);
}

.tag-billing {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  border-color: var(--color-border);
}

.score-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 3rem;
  padding: 0.35rem 0.55rem;
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--color-accent) 12%, var(--color-bg-canvas));
  border: 1px solid color-mix(in srgb, var(--color-accent) 28%, transparent);
  line-height: 1;
  flex-shrink: 0;
}

.score-num {
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--color-accent-text);
}

.score-label {
  margin-top: 0.2rem;
  font-size: 0.62rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  font-weight: 600;
}

/* description */
.provider-desc {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

/* time meta */
.time-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 0.85rem;
  margin: 0;
}

.time-meta__item {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: 0.7rem;
  line-height: 1;
}

.time-meta__item dt {
  margin: 0;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.time-meta__item dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
  font-weight: 600;
}

/* models */
.models {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-top: auto;
}

.section-label {
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.model-codes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.model-code {
  display: inline-flex;
  align-items: center;
  padding: 0.18rem 0.5rem;
  border-radius: 0.4rem;
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  letter-spacing: -0.01em;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-code--more {
  font-family: inherit;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

/* footer */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.6rem;
  padding-top: 0.85rem;
  border-top: 1px dashed var(--color-border);
}

.price-block {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  min-width: 0;
  flex: 1 1 auto;
}

.price-label {
  font-size: 0.65rem;
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
  flex: 0 1 auto;
  min-width: 0;
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
  padding: 0.42rem 0.85rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
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

@media (max-width: 480px) {
  .product-card {
    padding: 0.95rem 1rem;
  }

  .logo {
    width: 2.4rem;
    height: 2.4rem;
  }

  .price-value {
    font-size: 1rem;
  }
}
</style>
