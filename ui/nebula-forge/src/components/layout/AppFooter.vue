<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../BrandMark.vue'
import { product } from '../../data/product'

const router = useRouter()

const productLinks = [
  { label: '功能特性', href: '#features' },
  { label: '工作流', href: '#workflow' },
  { label: '插件系统', href: '#plugins' },
  { label: '下载安装', href: '#download' },
]

const resourceLinks = [
  { label: '版本记录', href: '/versions' },
  { label: '代码仓库', href: product.repoUrl, external: true },
]

const scrollToAnchor = (hash: string) => {
  const id = hash.replace('#', '')
  const el = document.getElementById(id)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const onLink = (link: { href: string; external?: boolean }) => {
  if (link.external) {
    window.open(link.href, '_blank', 'noopener')
    return
  }
  if (link.href.startsWith('#')) {
    if (router.currentRoute.value.path !== '/') {
      router.push('/').then(() =>
        requestAnimationFrame(() => requestAnimationFrame(() => scrollToAnchor(link.href))),
      )
    } else {
      scrollToAnchor(link.href)
    }
    return
  }
  router.push(link.href)
}
</script>

<template>
  <footer class="app-footer">
    <div class="app-footer__inner">
      <div class="brand-col">
        <div class="brand-col__head">
          <BrandMark :size="34" />
          <span class="brand-col__name">{{ product.name }}</span>
        </div>
        <p class="brand-col__desc">{{ product.tagline }}</p>
        <p class="brand-col__meta">支持平台：{{ product.platform }}</p>
      </div>

      <div class="links">
        <h3 class="links__title">产品</h3>
        <button
          v-for="link in productLinks"
          :key="link.href"
          type="button"
          class="links__item"
          @click="onLink(link)"
        >
          {{ link.label }}
        </button>
      </div>

      <div class="links">
        <h3 class="links__title">资源</h3>
        <button
          v-for="link in resourceLinks"
          :key="link.href"
          type="button"
          class="links__item"
          @click="onLink(link)"
        >
          {{ link.label }}
          <Icon v-if="link.external" icon="lucide:external-link" class="links__ext" />
        </button>
      </div>
    </div>

    <div class="app-footer__bottom">
      <p>© {{ product.year }} {{ product.author }} · {{ product.name }}</p>
      <p class="license">
        <Icon icon="lucide:scale" class="license__icon" />
        {{ product.license }}（仅限非商业用途）
      </p>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.app-footer {
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.app-footer__inner {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr;
  gap: 2.5rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: 3rem var(--space-page-x);
}

.brand-col__head {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
}

.brand-col__name {
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--color-text-primary);
}

.brand-col__desc {
  margin-top: 0.85rem;
  max-width: 22rem;
  color: var(--color-text-secondary);
}

.brand-col__meta {
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.links {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.links__title {
  font-size: 0.85rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-primary);
  margin-bottom: 0.5rem;
}

.links__item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  align-self: flex-start;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0.3rem 0;
  color: var(--color-text-secondary);
  font-size: 0.95rem;
  transition: color 0.2s ease;
}

.links__item:hover {
  color: var(--color-brand);
}

.links__ext {
  width: 0.9rem;
  height: 0.9rem;
}

.app-footer__bottom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: 1.25rem var(--space-page-x);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.license {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.license__icon {
  width: 1rem;
  height: 1rem;
}

@media (max-width: 720px) {
  .app-footer__inner {
    grid-template-columns: 1fr 1fr;
  }

  .brand-col {
    grid-column: 1 / -1;
  }
}
</style>
