<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchWorks } from '../../../api/work'
import StateBlock from '../../../components/StateBlock.vue'
import WorkCard from '../../works/components/WorkCard.vue'
import type { WorkListItem } from '../../../types/work'

const router = useRouter()

const works = ref<WorkListItem[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    const page = await fetchWorks({ pageNum: 1, pageSize: 3, sort: 'recent' })
    works.value = page.records
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="recent page">
    <header class="recent__head">
      <div>
        <h2 class="page-title">继续未完成的</h2>
        <p class="page-subtitle">上次停笔的地方还在等你。</p>
      </div>
      <button class="btn btn--ghost" type="button" @click="router.push('/works')">
        全部作品
        <Icon icon="lucide:arrow-right" />
      </button>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="failed" state="error" description="稍后重试，或检查后端服务是否已启动。" />
    <StateBlock
      v-else-if="works.length === 0"
      state="empty"
      title="还没有作品"
      description="从一句话立意开始，创建你的第一本书。"
      action-label="新建作品"
      @action="router.push('/works')"
    />
    <div v-else class="recent__grid">
      <WorkCard v-for="item in works" :key="item.id" :work="item" />
    </div>
  </section>
</template>

<style scoped lang="scss">
.recent__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.recent__head svg {
  width: 1rem;
  height: 1rem;
}

.recent__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: 1rem;
}
</style>
