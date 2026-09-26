<script setup lang="ts">
/**
 * 规划中模块的占位页：说明这个模块做什么、预计哪一批上线，避免导航点进去是空白。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { findModule, MODULES } from '../../config/modules'

const route = useRoute()
const mod = computed(() => (route.meta.module ? findModule(route.meta.module) : undefined))
const ready = MODULES.filter((m) => m.status === 'ready')
</script>

<template>
  <section v-if="mod" class="ph page">
    <span class="ph__ico"><Icon :icon="mod.icon" /></span>
    <h1 class="page-title">{{ mod.label }}</h1>
    <p class="ph__desc">{{ mod.description }}</p>
    <span class="tag tag--brand">规划中 · 第 {{ mod.batch }} 批实现</span>
    <div class="ph__go">
      <span>现在可以用：</span>
      <router-link v-for="m in ready" :key="m.key" :to="m.path" class="btn btn--ghost">
        <Icon :icon="m.icon" />{{ m.label }}
      </router-link>
    </div>
  </section>
</template>

<style scoped>
.ph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding-top: clamp(3rem, 12vh, 7rem);
  text-align: center;
}

.ph__ico {
  display: grid;
  place-items: center;
  width: 4rem;
  height: 4rem;
  border-radius: var(--radius-xl);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 1.8rem;
}

.ph__desc {
  max-width: 30rem;
  color: var(--color-text-secondary);
  line-height: 1.75;
}

.ph__go {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1rem;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}
</style>
