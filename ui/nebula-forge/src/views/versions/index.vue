<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Icon } from '@iconify/vue'
import ReleaseItem from './components/ReleaseItem.vue'
import { fetchReleases } from '../../api/releases'
import { product } from '../../data/product'
import type { Release } from '../../types/release'

const releases = ref<Release[]>([])
const loading = ref(true)

const latest = computed(
  () => releases.value.find((r) => r.isLatest) ?? releases.value[0] ?? null,
)

onMounted(async () => {
  try {
    releases.value = await fetchReleases()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="versions">
    <header class="versions__hero">
      <div class="versions__hero-inner">
        <p class="versions__eyebrow">版本管理</p>
        <h1 class="versions__title">{{ product.name }} 更新记录</h1>
        <p class="versions__sub">
          每个版本的新功能、改进与修复，以及对应的下载入口都汇总在这里。
        </p>

        <div v-if="latest" class="versions__latest">
          <span class="versions__latest-tag">
            <Icon icon="lucide:tag" />
            当前最新 v{{ latest.version }}
          </span>
          <span class="versions__latest-date">
            <Icon icon="lucide:calendar" />
            {{ latest.date }}
          </span>
          <a
            v-if="latest.assets[0]"
            class="versions__latest-btn"
            :href="latest.assets[0].url"
            download
          >
            <Icon icon="lucide:download" />
            下载最新版
          </a>
        </div>
      </div>
    </header>

    <section class="versions__body">
      <div class="versions__list">
        <p v-if="loading" class="versions__state">
          <Icon icon="lucide:loader-circle" class="spin" />
          正在加载版本信息…
        </p>

        <p v-else-if="!releases.length" class="versions__state">
          <Icon icon="lucide:inbox" />
          暂无版本发布记录。
        </p>

        <template v-else>
          <ReleaseItem
            v-for="release in releases"
            :key="release.version"
            :release="release"
          />
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.versions__hero {
  background: var(--gradient-hero);
  border-bottom: 1px solid var(--color-border);
}

.versions__hero-inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(2.5rem, 6vw, 4.5rem) var(--space-page-x) clamp(2rem, 4vw, 3rem);
}

.versions__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.versions__title {
  margin-top: 0.6rem;
  font-size: clamp(2rem, 4.5vw, 3rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.versions__sub {
  margin-top: 0.8rem;
  max-width: 36rem;
  color: var(--color-text-secondary);
  font-size: 1.05rem;
}

.versions__latest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1.6rem;
}

.versions__latest-tag,
.versions__latest-date {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.versions__latest-tag {
  font-weight: 700;
  color: var(--color-brand);
}

.versions__latest svg {
  width: 1.05rem;
  height: 1.05rem;
}

.versions__latest-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 1.1rem;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}

.versions__latest-btn:hover {
  background: var(--color-brand-hover);
  transform: translateY(-1px);
}

.versions__body {
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(2.5rem, 5vw, 4rem) var(--space-page-x);
}

.versions__list {
  max-width: 52rem;
}

.versions__state {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--color-text-secondary);
  font-size: 1rem;
}

.versions__state svg {
  width: 1.2rem;
  height: 1.2rem;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
