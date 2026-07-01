<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import type { PluginListItem } from '../../../types/plugin'
import { PRICING_TYPE_LABEL } from '../../../types/plugin'
import { formatCount, formatRating } from '../../../utils/format'

const props = defineProps<{ plugin: PluginListItem }>()

const iconFailed = ref(false)

const priceLabel = computed(() => {
  const p = props.plugin
  if (p.priceText) return p.priceText
  if (p.pricingType != null) return PRICING_TYPE_LABEL[p.pricingType] ?? '—'
  return '免费'
})

const isFree = computed(
  () => !props.plugin.priceText && (props.plugin.pricingType ?? 1) === 1,
)

const showIcon = computed(() => !!props.plugin.iconUrl && !iconFailed.value)

const hasRating = computed(
  () => (props.plugin.ratingCount ?? 0) > 0 && (props.plugin.ratingScore ?? 0) > 0,
)
</script>

<template>
  <article class="card">
    <div class="card__top">
      <div class="card__icon">
        <img
          v-if="showIcon"
          :src="plugin.iconUrl ?? ''"
          :alt="plugin.name"
          loading="lazy"
          @error="iconFailed = true"
        />
        <Icon v-else icon="lucide:puzzle" />
      </div>
      <div class="card__heading">
        <h3 class="card__name" :title="plugin.name">{{ plugin.name }}</h3>
        <p v-if="plugin.authorName" class="card__author">
          <Icon icon="lucide:user" />
          {{ plugin.authorName }}
        </p>
      </div>
      <span v-if="plugin.isFeatured === 1" class="card__featured">
        <Icon icon="lucide:sparkles" />
        推荐
      </span>
    </div>

    <p class="card__summary">{{ plugin.summary || '暂无简介' }}</p>

    <div v-if="plugin.categoryNames?.length" class="card__tags">
      <span v-for="name in plugin.categoryNames.slice(0, 3)" :key="name" class="card__tag">
        {{ name }}
      </span>
    </div>

    <div class="card__footer">
      <div class="card__stats">
        <span class="card__stat" title="下载次数">
          <Icon icon="lucide:download" />
          {{ formatCount(plugin.downloadCount) }}
        </span>
        <span v-if="hasRating" class="card__stat" title="评分">
          <Icon icon="lucide:star" class="card__stat--star" />
          {{ formatRating(plugin.ratingScore) }}
        </span>
      </div>
      <span class="card__price" :class="{ 'card__price--free': isFree }">
        {{ priceLabel }}
      </span>
    </div>
  </article>
</template>

<style scoped lang="scss">
.card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  height: 100%;
  padding: 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.card:hover {
  border-color: var(--color-brand);
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

.card__top {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.card__icon {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  overflow: hidden;
}

.card__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card__icon svg {
  width: 1.6rem;
  height: 1.6rem;
}

.card__heading {
  flex: 1;
  min-width: 0;
}

.card__name {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card__author {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.2rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.card__author svg {
  width: 0.9rem;
  height: 0.9rem;
}

.card__featured {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.card__featured svg {
  width: 0.85rem;
  height: 0.85rem;
}

.card__summary {
  flex: 1;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.card__tag {
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  background: var(--color-bg-soft);
}

.card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
}

.card__stats {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}

.card__stat {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.card__stat svg {
  width: 0.95rem;
  height: 0.95rem;
}

.card__stat--star {
  color: #f5a623;
}

.card__price {
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--color-brand);
}

.card__price--free {
  color: var(--color-text-secondary);
}
</style>
