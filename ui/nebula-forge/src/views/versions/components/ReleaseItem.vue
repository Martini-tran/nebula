<script setup lang="ts">
import { Icon } from '@iconify/vue'
import type { Release } from '../../../types/release'

defineProps<{ release: Release }>()

const groups = [
  { key: 'added', label: '新增', icon: 'lucide:plus-circle', tone: 'added' },
  { key: 'changed', label: '改进', icon: 'lucide:refresh-cw', tone: 'changed' },
  { key: 'fixed', label: '修复', icon: 'lucide:wrench', tone: 'fixed' },
] as const
</script>

<template>
  <article class="release">
    <div class="release__rail" aria-hidden="true">
      <span class="release__dot" :class="{ 'release__dot--latest': release.isLatest }" />
    </div>

    <div class="release__body">
      <header class="release__head">
        <h2 class="release__version">v{{ release.version }}</h2>
        <span
          class="release__channel"
          :class="`release__channel--${release.channel}`"
        >
          {{ release.channel === 'stable' ? '稳定版' : '测试版' }}
        </span>
        <span v-if="release.isLatest" class="release__latest">最新</span>
        <span class="release__date">
          <Icon icon="lucide:calendar" />
          {{ release.date }}
        </span>
      </header>

      <ul v-if="release.highlights.length" class="release__highlights">
        <li v-for="point in release.highlights" :key="point">
          <Icon icon="lucide:sparkles" />
          {{ point }}
        </li>
      </ul>

      <div class="release__changelog">
        <template v-for="group in groups" :key="group.key">
          <div v-if="release.changelog[group.key]?.length" class="changelog">
            <h3 class="changelog__title" :class="`changelog__title--${group.tone}`">
              <Icon :icon="group.icon" />
              {{ group.label }}
            </h3>
            <ul class="changelog__list">
              <li v-for="line in release.changelog[group.key]" :key="line">{{ line }}</li>
            </ul>
          </div>
        </template>
      </div>

      <div class="release__downloads">
        <a
          v-for="asset in release.assets"
          :key="asset.url"
          class="download-chip"
          :href="asset.url"
          target="_blank"
          rel="noopener"
        >
          <Icon icon="lucide:download" />
          <span>{{ asset.label }}</span>
          <small v-if="asset.size">{{ asset.size }}</small>
        </a>
      </div>
    </div>
  </article>
</template>

<style scoped lang="scss">
.release {
  display: grid;
  grid-template-columns: 2rem 1fr;
  gap: 1rem;
}

.release__rail {
  display: flex;
  justify-content: center;
  position: relative;
}

.release__rail::before {
  content: '';
  position: absolute;
  top: 0.5rem;
  bottom: -2.5rem;
  width: 2px;
  background: var(--color-border);
}

.release__dot {
  position: relative;
  z-index: 1;
  width: 0.9rem;
  height: 0.9rem;
  margin-top: 0.4rem;
  border-radius: 50%;
  background: var(--color-bg-canvas);
  border: 3px solid var(--color-border);
}

.release__dot--latest {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 4px var(--color-brand-soft);
}

.release__body {
  padding-bottom: 2.5rem;
}

.release__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.7rem;
}

.release__version {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-text-primary);
}

.release__channel {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
}

.release__channel--stable {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.release__channel--beta {
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.release__latest {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-on-brand);
  background: var(--color-brand);
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
}

.release__date {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.release__date svg {
  width: 1rem;
  height: 1rem;
}

.release__highlights {
  list-style: none;
  margin: 1.1rem 0 0;
  padding: 1rem 1.2rem;
  border-radius: var(--radius-lg);
  background: var(--color-bg-soft);
  display: grid;
  gap: 0.5rem;
}

.release__highlights li {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--color-text-primary);
}

.release__highlights svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-brand);
  flex-shrink: 0;
}

.release__changelog {
  display: grid;
  gap: 1.1rem;
  margin-top: 1.4rem;
}

.changelog__title {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.95rem;
  font-weight: 700;
  margin-bottom: 0.4rem;
}

.changelog__title svg {
  width: 1.05rem;
  height: 1.05rem;
}

.changelog__title--added { color: #16a34a; }
.changelog__title--changed { color: var(--color-brand); }
.changelog__title--fixed { color: #d97706; }

.changelog__list {
  margin: 0;
  padding-left: 1.6rem;
  color: var(--color-text-secondary);
  display: grid;
  gap: 0.3rem;
  line-height: 1.6;
}

.release__downloads {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  margin-top: 1.5rem;
}

.download-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 600;
  font-size: 0.92rem;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.download-chip:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
  transform: translateY(-1px);
}

.download-chip svg {
  width: 1.1rem;
  height: 1.1rem;
}

.download-chip small {
  color: var(--color-text-secondary);
  font-weight: 500;
}
</style>
