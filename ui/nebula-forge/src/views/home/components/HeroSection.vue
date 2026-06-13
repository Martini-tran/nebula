<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import LauncherMockup from './LauncherMockup.vue'
import { fetchLatestRelease } from '../../../api/releases'
import { product } from '../../../data/product'

const router = useRouter()
const latestVersion = ref(product.currentVersion)

onMounted(async () => {
  const latest = await fetchLatestRelease()
  if (latest) latestVersion.value = latest.version
})

const scrollTo = (id: string) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <section id="top" class="hero">
    <div class="hero__bg" aria-hidden="true" />
    <div class="hero__inner">
      <div class="hero__content">
        <span class="hero__badge">
          <Icon icon="lucide:sparkles" class="hero__badge-icon" />
          {{ product.eyebrow }} · v{{ latestVersion }}
        </span>
        <h1 class="hero__title">
          {{ product.tagline }}
        </h1>
        <p class="hero__summary">{{ product.summary }}</p>

        <div class="hero__actions">
          <button class="btn btn--primary" type="button" @click="scrollTo('download')">
            <Icon icon="lucide:download" class="btn__icon" />
            免费下载 {{ product.name }}
          </button>
          <button class="btn btn--ghost" type="button" @click="router.push('/versions')">
            <Icon icon="lucide:history" class="btn__icon" />
            查看版本记录
          </button>
        </div>

        <ul class="hero__meta">
          <li><Icon icon="lucide:monitor-check" /> {{ product.platform }}</li>
          <li><Icon icon="lucide:zap" /> 即开即用 · 毫秒响应</li>
          <li><Icon icon="lucide:shield-check" /> 本地运行 · 数据不外传</li>
        </ul>
      </div>

      <div class="hero__visual">
        <LauncherMockup />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.hero {
  position: relative;
  overflow: hidden;
  padding: clamp(3rem, 8vw, 6rem) var(--space-page-x) clamp(3rem, 7vw, 5rem);
}

.hero__bg {
  position: absolute;
  inset: 0;
  background: var(--gradient-hero);
  pointer-events: none;
}

.hero__inner {
  position: relative;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  align-items: center;
  gap: clamp(2rem, 5vw, 4rem);
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-brand);
  font-size: 0.85rem;
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.hero__badge-icon {
  width: 1rem;
  height: 1rem;
}

.hero__title {
  margin-top: 1.25rem;
  font-size: clamp(2.25rem, 5.5vw, 3.75rem);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.hero__summary {
  margin-top: 1.1rem;
  max-width: 34rem;
  font-size: 1.1rem;
  color: var(--color-text-secondary);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  margin-top: 1.8rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-size: 1rem;
  font-weight: 700;
  padding: 0.75rem 1.3rem;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.btn__icon {
  width: 1.15rem;
  height: 1.15rem;
}

.btn--primary {
  background: var(--color-brand);
  color: var(--color-on-brand);
  box-shadow: var(--shadow-md);
}

.btn--primary:hover {
  background: var(--color-brand-hover);
  transform: translateY(-2px);
}

.btn--ghost {
  background: var(--color-bg-surface);
  border-color: var(--color-border);
  color: var(--color-text-primary);
}

.btn--ghost:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
  transform: translateY(-2px);
}

.hero__meta {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 1.25rem;
  margin: 2rem 0 0;
  padding: 0;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.hero__meta li {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.hero__meta svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-brand);
}

.hero__visual {
  display: grid;
  place-items: center;
}

@media (max-width: 900px) {
  .hero__inner {
    grid-template-columns: 1fr;
  }

  .hero__visual {
    order: -1;
  }
}
</style>
