<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProviderCard from './components/ProviderCard.vue'
import { fetchRelayProviderDetail, type RelayProvider } from '../../api/aiRelay'

const route = useRoute()
const router = useRouter()

const provider = ref<RelayProvider | null>(null)
const loading = ref(false)
const errorMsg = ref('')

const providerId = computed(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? raw[0] : raw
})

async function load(id: string | undefined) {
  if (!id) {
    errorMsg.value = '缺少中转站 ID'
    provider.value = null
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await fetchRelayProviderDetail(id)
    provider.value = data ?? null
    if (!data) {
      errorMsg.value = '未找到该中转站'
    }
  } catch (e) {
    provider.value = null
    errorMsg.value = e instanceof Error ? e.message : '加载详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load(providerId.value))
watch(providerId, (id) => load(id))

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/reviews/directory')
  }
}
</script>

<template>
  <section class="detail-page">
    <header class="detail-toolbar">
      <button type="button" class="back-btn" @click="goBack">
        <span aria-hidden="true">←</span>
        <span>返回列表</span>
      </button>
    </header>

    <div v-if="loading && !provider" class="state-card">加载中…</div>
    <div v-else-if="errorMsg" class="state-card state-card--error">
      <p>{{ errorMsg }}</p>
      <button type="button" class="state-action" @click="load(providerId)">重试</button>
    </div>
    <ProviderCard v-else-if="provider" :provider="provider" />
  </section>
</template>

<style scoped>
.detail-page {
  display: grid;
  gap: 1rem;
}

.detail-toolbar {
  display: flex;
  align-items: center;
}

.back-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.back-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.state-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 3rem 1.5rem;
  text-align: center;
  color: var(--color-text-secondary);
  box-shadow: var(--shadow-sm);
  display: grid;
  gap: 0.8rem;
  justify-items: center;
}

.state-card--error {
  color: #b45309;
}

.state-action {
  appearance: none;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-primary);
  padding: 0.4rem 1rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.state-action:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
</style>
