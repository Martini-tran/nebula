<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchLatestRelease } from '../../../api/releases'
import { product } from '../../../data/product'
import type { Release } from '../../../types/release'

const router = useRouter()
const latest = ref<Release | null>(null)

onMounted(async () => {
  latest.value = await fetchLatestRelease()
})

const version = computed(() => latest.value?.version ?? product.currentVersion)
const date = computed(() => latest.value?.date ?? '')
const winAsset = computed(
  () => latest.value?.assets.find((a) => a.platform === 'windows') ?? null,
)
</script>

<template>
  <section id="download" class="download">
    <div class="download__inner">
      <div class="download__card">
        <div class="download__left">
          <span class="download__eyebrow">立即开始</span>
          <h2 class="download__title">下载 {{ product.name }}，提速每一次操作</h2>
          <ul class="download__meta">
            <li><Icon icon="lucide:tag" /> 最新版本 v{{ version }}</li>
            <li v-if="date"><Icon icon="lucide:calendar" /> 发布于 {{ date }}</li>
            <li><Icon icon="lucide:monitor" /> {{ product.platform }}</li>
          </ul>
        </div>

        <div class="download__right">
          <a
            v-if="winAsset"
            class="download__btn"
            :href="winAsset.url"
            download
          >
            <Icon icon="lucide:download" class="download__btn-icon" />
            <span class="download__btn-text">
              <strong>{{ winAsset.label }}</strong>
              <small v-if="winAsset.size">{{ winAsset.size }}</small>
            </span>
          </a>
          <button class="download__alt" type="button" @click="router.push('/versions')">
            <Icon icon="lucide:history" />
            历史版本与更新日志
          </button>
          <p class="download__note">
            <Icon icon="lucide:info" />
            {{ product.license }}，仅限非商业用途。
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.download {
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x) clamp(4rem, 8vw, 6rem);
}

.download__inner {
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.download__card {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  align-items: center;
  gap: 2.5rem;
  padding: clamp(2rem, 4vw, 3.25rem);
  border-radius: var(--radius-2xl);
  background:
    var(--gradient-hero),
    var(--color-bg-elevated);
  color: #eef2ff;
  box-shadow: var(--shadow-lg);
}

.download__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.download__title {
  margin-top: 0.7rem;
  font-size: clamp(1.6rem, 3.5vw, 2.4rem);
  font-weight: 800;
  line-height: 1.15;
}

.download__meta {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 1.25rem;
  margin: 1.4rem 0 0;
  padding: 0;
  color: #b9c6ec;
  font-size: 0.92rem;
}

.download__meta li {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.download__meta svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-brand);
}

.download__right {
  display: grid;
  gap: 0.85rem;
}

.download__btn {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1rem 1.4rem;
  border-radius: var(--radius-lg);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-weight: 700;
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}

.download__btn:hover {
  background: var(--color-brand-hover);
  transform: translateY(-2px);
}

.download__btn-icon {
  width: 1.6rem;
  height: 1.6rem;
}

.download__btn-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.download__btn-text small {
  font-weight: 500;
  opacity: 0.85;
}

.download__alt {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem 1.2rem;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: #eef2ff;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.download__alt:hover {
  border-color: var(--color-brand);
}

.download__alt svg {
  width: 1.1rem;
  height: 1.1rem;
}

.download__note {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: #8ea0c9;
}

.download__note svg {
  width: 0.95rem;
  height: 0.95rem;
  flex-shrink: 0;
}

@media (max-width: 820px) {
  .download__card {
    grid-template-columns: 1fr;
  }
}
</style>
