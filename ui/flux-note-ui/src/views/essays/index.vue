<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchEssays, searchEssays, type PostListItem } from '../../api/post'

const essays = ref<PostListItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref<string | null>(null)
const nextCursor = ref<string | null>(null)
const searchInput = ref('')
const searchKeyword = ref('')

const PAGE_SIZE = 10

const fetcher = computed(() => (searchKeyword.value ? searchEssays : fetchEssays))

const loadEssays = async () => {
  loading.value = true
  error.value = null
  try {
    const data = await fetcher.value({ keyword: searchKeyword.value, limit: PAGE_SIZE })
    essays.value = data?.items ?? []
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    essays.value = []
    nextCursor.value = null
    error.value = '加载随笔失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const data = await fetcher.value({
      keyword: searchKeyword.value,
      cursor: nextCursor.value,
      limit: PAGE_SIZE,
    })
    if (data?.items?.length) essays.value = [...essays.value, ...data.items]
    nextCursor.value = data?.next_cursor ?? null
  } catch {
    error.value = '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

const submitSearch = () => {
  const kw = searchInput.value.trim()
  if (searchKeyword.value === kw) return
  searchKeyword.value = kw
  loadEssays()
}

const clearSearch = () => {
  if (!searchInput.value && !searchKeyword.value) return
  searchInput.value = ''
  searchKeyword.value = ''
  loadEssays()
}

const formatDate = (val: string) => {
  const d = new Date(val.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return val
  return `${d.getFullYear()} · ${String(d.getMonth() + 1).padStart(2, '0')} · ${String(d.getDate()).padStart(2, '0')}`
}

onMounted(loadEssays)
</script>

<template>
  <div class="essay-page">

    <!-- ── 搜索面板（header + 搜索合并）── -->
    <section class="search-panel" aria-label="随笔搜索">
      <div>
        <p class="search-eyebrow">Essays</p>
        <h2 class="section-title">
          {{ searchKeyword ? '搜索结果' : '随笔' }}
        </h2>
      </div>
      <form class="search-form" @submit.prevent="submitSearch">
        <label class="search-input-wrap">
          <span class="search-icon" aria-hidden="true">⌕</span>
          <input
            v-model="searchInput"
            class="search-input"
            type="search"
            placeholder="搜索随笔标题或摘要"
            autocomplete="off"
          >
        </label>
        <button type="submit" class="search-button">搜索</button>
        <button
          v-if="searchKeyword"
          type="button"
          class="search-clear"
          @click="clearSearch"
        >
          清空
        </button>
      </form>
      <p v-if="searchKeyword" class="search-meta">
        正在搜索「{{ searchKeyword }}」
      </p>
    </section>

    <!-- ── 状态 ── -->
    <div v-if="loading" class="state-block">
      <span class="state-block__spinner" aria-hidden="true" />
      正在加载…
    </div>
    <div v-else-if="error" class="state-block state-block--error">{{ error }}</div>
    <div v-else-if="essays.length === 0" class="state-block">
      {{ searchKeyword ? '没有找到相关随笔' : '还没有随笔，敬请期待' }}
    </div>

    <!-- ── 随笔列表 ── -->
    <ol v-else class="essay-list">
      <li v-for="item in essays" :key="item.id">
        <RouterLink
          :to="{ path: '/article', query: { slug: item.slug } }"
          class="essay-card"
        >
          <!-- 左侧竖线装饰 -->
          <span class="essay-card__stripe" aria-hidden="true" />

          <!-- 主体 -->
          <div class="essay-card__body">
            <time class="essay-card__date" :datetime="item.published_at">
              {{ formatDate(item.published_at) }}
            </time>
            <h3 class="essay-card__title">{{ item.title }}</h3>
            <p v-if="item.summary" class="essay-card__summary">{{ item.summary }}</p>
            <div v-if="item.tags.length" class="essay-card__tags">
              <span
                v-for="tag in item.tags.slice(0, 4)"
                :key="tag.id"
                class="tag"
              ># {{ tag.name }}</span>
            </div>
          </div>

          <!-- 阅读箭头 -->
          <span class="essay-card__arrow" aria-hidden="true">→</span>
        </RouterLink>
      </li>
    </ol>

    <!-- ── 加载更多 ── -->
    <button
      v-if="nextCursor && !loading"
      type="button"
      class="load-more"
      :disabled="loadingMore"
      @click="loadMore"
    >
      {{ loadingMore ? '加载中…' : '加载更多' }}
    </button>

  </div>
</template>

<style scoped>
/* ── 整体 ── */
.essay-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 760px;
  margin: 0 auto;
  width: 100%;
}

/* ── 搜索面板（与 articles 一致） ── */
.search-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background:
    radial-gradient(circle at 12% 10%, color-mix(in srgb, var(--color-accent) 16%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  padding: 1rem;
  box-shadow: 0 18px 45px color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.search-eyebrow {
  margin: 0 0 0.25rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.625rem;
  margin-top: 0.875rem;
}

@media (min-width: 580px) {
  .search-panel {
    padding: 1.1rem 1.2rem;
  }
  .search-form {
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
  }
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg-surface) 82%, transparent);
  padding: 0.15rem 0.875rem;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.search-input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--color-accent) 50%, var(--color-border));
  background: var(--color-bg-surface);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-accent) 14%, transparent);
}

.search-icon {
  color: var(--color-text-muted);
  font-size: 1.05rem;
  line-height: 1;
  flex-shrink: 0;
}

.search-input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 0.65rem 0;
  color: var(--color-text-primary);
  font-size: 0.9rem;
}

.search-input::placeholder {
  color: var(--color-text-muted);
}

.search-button,
.search-clear {
  border: 0;
  border-radius: 999px;
  padding: 0.72rem 1rem;
  font-size: 0.875rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, background 0.15s ease;
}

.search-button {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
}

.search-clear {
  background: transparent;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.search-button:hover,
.search-clear:hover {
  transform: translateY(-1px);
}

.search-meta {
  margin: 0.75rem 0 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

/* ── 状态卡片 ── */
.state-block {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  padding: 2rem 1.5rem;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.state-block--error {
  color: #b91c1c;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-block__spinner {
  display: inline-block;
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ══════════════════════════════
   随笔列表
══════════════════════════════ */
.essay-list {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  list-style: none;
  padding: 0;
  margin: 0;
}

/* ══════════════════════════════
   随笔卡片
══════════════════════════════ */
.essay-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem 1.25rem 1.25rem 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-bg-surface);
  text-decoration: none;
  color: inherit;
  overflow: hidden;
  transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}

.essay-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px -8px color-mix(in srgb, var(--color-text-primary) 12%, transparent);
  border-color: color-mix(in srgb, var(--color-accent) 30%, var(--color-border));
}

/* 左侧竖线装饰 */
.essay-card__stripe {
  position: absolute;
  left: 0;
  top: 1rem;
  bottom: 1rem;
  width: 3px;
  border-radius: 3px;
  background: var(--color-accent);
  opacity: 0;
  transform: scaleY(0.4);
  transform-origin: center;
  transition: opacity 0.2s ease, transform 0.25s ease;
}

.essay-card:hover .essay-card__stripe {
  opacity: 1;
  transform: scaleY(1);
}

/* 主体文字区 */
.essay-card__body {
  flex: 1;
  min-width: 0;
}

/* 日期 */
.essay-card__date {
  display: block;
  margin-bottom: 0.4rem;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 标题 */
.essay-card__title {
  margin: 0;
  font-size: 1.0625rem;
  font-weight: 700;
  line-height: 1.45;
  color: var(--color-text-primary);
  transition: color 0.15s ease;
}

.essay-card:hover .essay-card__title {
  color: var(--color-accent-text);
}

/* 摘要 */
.essay-card__summary {
  margin: 0.4rem 0 0;
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 标签 */
.essay-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-top: 0.75rem;
}

.tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-accent-text);
  opacity: 0.8;
  letter-spacing: 0.02em;
}

/* 阅读箭头 */
.essay-card__arrow {
  flex-shrink: 0;
  align-self: center;
  font-size: 1rem;
  color: var(--color-text-muted);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.essay-card:hover .essay-card__arrow {
  opacity: 1;
  transform: translateX(0);
}

/* ══════════════════════════════
   加载更多
══════════════════════════════ */
.load-more {
  display: block;
  width: 100%;
  padding: 0.8rem 1rem;
  border-radius: 0.875rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, transform 0.15s;
}

.load-more:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  transform: translateY(-1px);
}

.load-more:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
