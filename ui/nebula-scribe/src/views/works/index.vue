<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { fetchWorks } from '../../api/work'
import StateBlock from '../../components/StateBlock.vue'
import WorkCard from './components/WorkCard.vue'
import CreateWorkDialog from './components/CreateWorkDialog.vue'
import {
  WORK_SORT_OPTIONS,
  WORK_STATUS_LABEL,
  type WorkListItem,
  type WorkStatus,
} from '../../types/work'

const router = useRouter()

const works = ref<WorkListItem[]>([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)

const keyword = ref('')
const status = ref<WorkStatus | ''>('')
const sort = ref('recent')
const dialogOpen = ref(false)

const statusFilters: Array<{ value: WorkStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  ...(Object.keys(WORK_STATUS_LABEL) as WorkStatus[]).map((value) => ({
    value,
    label: WORK_STATUS_LABEL[value],
  })),
]

const load = async () => {
  loading.value = true
  failed.value = false
  try {
    const page = await fetchWorks({
      pageNum: 1,
      pageSize: 24,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
      sort: sort.value,
    })
    works.value = page.records
    total.value = page.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 搜索输入做 300ms 防抖，筛选与排序立即生效
let timer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(load, 300)
})
watch([status, sort], load)

const onCreated = (work: WorkListItem) => {
  dialogOpen.value = false
  router.push(`/works/${work.id}`)
}
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1 class="page-title">我的作品</h1>
        <p class="page-subtitle">共 {{ total }} 部</p>
      </div>
      <button class="btn btn--primary" type="button" @click="dialogOpen = true">
        <Icon icon="lucide:plus" />
        新建作品
      </button>
    </header>

    <div class="toolbar">
      <div class="search">
        <Icon icon="lucide:search" class="search__icon" />
        <input
          v-model="keyword"
          class="search__input"
          type="search"
          placeholder="搜索标题或简介"
          aria-label="搜索作品"
        />
      </div>

      <div class="filters" role="group" aria-label="按状态筛选">
        <button
          v-for="item in statusFilters"
          :key="item.value"
          type="button"
          class="chip"
          :class="{ 'chip--active': status === item.value }"
          @click="status = item.value"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="sorts" role="group" aria-label="排序方式">
        <button
          v-for="item in WORK_SORT_OPTIONS"
          :key="item.value"
          type="button"
          class="chip"
          :class="{ 'chip--active': sort === item.value }"
          @click="sort = item.value"
        >
          <Icon :icon="item.icon" />
          {{ item.label }}
        </button>
      </div>
    </div>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="failed" state="error" description="稍后重试，或检查后端服务是否已启动。" />
    <StateBlock
      v-else-if="works.length === 0"
      state="empty"
      :title="keyword || status ? '没有匹配的作品' : '还没有作品'"
      :description="
        keyword || status ? '换个关键词或筛选条件试试。' : '从一句话立意开始，创建你的第一本书。'
      "
      :action-label="keyword || status ? '' : '新建作品'"
      @action="dialogOpen = true"
    />
    <div v-else class="grid">
      <WorkCard v-for="item in works" :key="item.id" :work="item" />
    </div>

    <CreateWorkDialog :open="dialogOpen" @close="dialogOpen = false" @created="onCreated" />
  </div>
</template>

<style scoped lang="scss">
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.head svg {
  width: 1rem;
  height: 1rem;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.search {
  position: relative;
  flex: 1 1 16rem;
  min-width: 12rem;
}

.search__icon {
  position: absolute;
  left: 0.7rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1rem;
  height: 1rem;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.search__input {
  width: 100%;
  padding: 0.5rem 0.75rem 0.5rem 2.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.search__input:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.filters,
.sorts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.chip svg {
  width: 0.9rem;
  height: 0.9rem;
}

.chip:hover {
  color: var(--color-text-primary);
  border-color: var(--color-brand);
}

.chip--active {
  color: var(--color-brand);
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: 1rem;
}
</style>
