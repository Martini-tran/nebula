<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchLatestRelease } from '../../../api/releases'
import { product } from '../../../data/product'

const router = useRouter()
const version = ref<string>(product.currentVersion)
const url = ref<string>('')

onMounted(async () => {
  const latest = await fetchLatestRelease()
  if (latest) {
    version.value = latest.version
    url.value = latest.assets.find((a) => a.platform === 'windows')?.url ?? ''
  }
})

const goVersions = () => router.push('/versions')
</script>

<template>
  <section id="download" class="cta">
    <div class="cta__inner">
      <Icon icon="lucide:rocket" class="cta__glyph" />
      <h2 class="cta__title">现在就让启动快起来</h2>
      <p class="cta__sub">
        免费下载，适用于 {{ product.platform }}。当前最新版本 v{{ version }}。
      </p>

      <div class="cta__actions">
        <a
          v-if="url"
          class="cta__btn"
          :href="url"
          target="_blank"
          rel="noopener"
        >
          <Icon icon="lucide:download" class="cta__btn-icon" />
          下载 Windows 安装包
        </a>
        <button v-else class="cta__btn" type="button" @click="goVersions">
          <Icon icon="lucide:download" class="cta__btn-icon" />
          前往下载
        </button>

        <button class="cta__ghost" type="button" @click="goVersions">
          查看全部版本
          <Icon icon="lucide:arrow-right" class="cta__ghost-icon" />
        </button>
      </div>

      <p class="cta__meta">
        <Icon icon="lucide:scale" class="cta__meta-icon" />
        {{ product.license }}（仅限非商业用途）
      </p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.cta {
  padding: clamp(3.5rem, 8vw, 6rem) var(--space-page-x);
  background: var(--gradient-hero);
}

.cta__inner {
  max-width: 44rem;
  margin: 0 auto;
  text-align: center;
}

.cta__glyph {
  width: 2.6rem;
  height: 2.6rem;
  color: var(--color-brand);
}

.cta__title {
  margin-top: 0.8rem;
  font-size: clamp(1.7rem, 3.5vw, 2.5rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.cta__sub {
  margin-top: 0.8rem;
  font-size: 1.05rem;
  color: var(--color-text-secondary);
}

.cta__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1.75rem;
}

.cta__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.8rem 1.6rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}

.cta__btn:hover {
  background: var(--color-brand-hover);
  transform: translateY(-2px);
}

.cta__btn-icon {
  width: 1.15rem;
  height: 1.15rem;
}

.cta__ghost {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.8rem 1.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.cta__ghost:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.cta__ghost-icon {
  width: 1.05rem;
  height: 1.05rem;
}

.cta__meta {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 1.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.cta__meta-icon {
  width: 1rem;
  height: 1rem;
}
</style>
