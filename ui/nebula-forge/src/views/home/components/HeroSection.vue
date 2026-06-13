<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import LauncherMockup from './LauncherMockup.vue'
import ScreenshotFrame from './ScreenshotFrame.vue'
import { fetchLatestRelease } from '../../../api/releases'
import { product } from '../../../data/product'

const router = useRouter()
const version = ref<string>(product.currentVersion)
const size = ref('')

onMounted(async () => {
  const latest = await fetchLatestRelease()
  if (latest) {
    version.value = latest.version
    size.value = latest.assets.find((a) => a.platform === 'windows')?.size ?? ''
  }
})

const goDownload = () => router.push('/versions')
const goDocs = () => {
  window.open('/docs/', '_blank', 'noopener')
}
</script>

<template>
  <section id="top" class="hero">
    <div class="hero__inner">
      <div class="hero__content">
        <p class="hero__kicker">
          <span class="hero__badge">v{{ version }}</span>
          {{ product.eyebrow }}
        </p>
        <h1 class="hero__title">{{ product.headline }}</h1>
        <p class="hero__summary">{{ product.summary }}</p>

        <div class="hero__actions">
          <button class="btn" type="button" @click="goDownload">
            <Icon icon="lucide:download" class="btn__icon" />
            下载（Windows）
          </button>
          <button class="textlink" type="button" @click="goDocs">查看文档 →</button>
        </div>

        <p class="hero__note">
          <Icon icon="lucide:monitor" class="hero__note-icon" />
          支持 {{ product.platform }}<template v-if="size"> · 约 {{ size }}</template>
        </p>
      </div>

      <div class="hero__visual">
        <ScreenshotFrame name="hero" label="orccode 命令面板" aspect="16 / 11">
          <template #placeholder>
            <LauncherMockup />
          </template>
        </ScreenshotFrame>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.hero {
  border-bottom: 1px solid var(--color-border);
  background: var(--gradient-hero);
}

.hero__inner {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: clamp(2rem, 5vw, 4.5rem);
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x);
}

.hero__kicker {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-mono);
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.hero__badge {
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 700;
}

.hero__title {
  margin-top: 0.9rem;
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.hero__summary {
  margin-top: 1rem;
  max-width: 30rem;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.hero__actions {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  margin-top: 1.75rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: 0.98rem;
  font-weight: 600;
  padding: 0.7rem 1.3rem;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition:
    background 0.15s ease,
    transform 0.15s ease;
}

.btn:hover {
  background: var(--color-brand-hover);
  transform: translateY(-1px);
}

.btn__icon {
  width: 1.05rem;
  height: 1.05rem;
}

.textlink {
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 0.98rem;
  font-weight: 600;
  color: var(--color-text-primary);
  padding: 0;
}

.textlink:hover {
  color: var(--color-brand);
}

.hero__note {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 1.75rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.hero__note-icon {
  width: 1rem;
  height: 1rem;
}

.hero__visual {
  display: flex;
  justify-content: center;
}

@media (max-width: 880px) {
  .hero__inner {
    grid-template-columns: 1fr;
  }

  .hero__visual {
    order: -1;
    justify-content: flex-start;
  }
}
</style>
