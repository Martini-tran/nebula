<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  fetchRelayRecommends,
  type RelayRecommend,
} from '../../api/aiRelay'

type SortKey = 'score' | 'time' | 'sort'

const sortKey = ref<SortKey>('score')
const keyword = ref('')
const keywordInput = ref('')

const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const list = ref<RelayRecommend[]>([])
const loading = ref(false)
const errorMsg = ref('')

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'score', label: '评分优先' },
  { key: 'time', label: '最新推荐' },
  { key: 'sort', label: '编辑排序' },
]

const totalPages = computed(() =>
  total.value > 0 ? Math.max(1, Math.ceil(total.value / pageSize.value)) : 1,
)

function splitLines(input?: null | string) {
  if (!input) return []
  return input
    .split(/[\n;；]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function formatScore(score?: null | number) {
  if (score == null) return null
  return Number(score).toFixed(1)
}

function formatMoney(amount?: null | number) {
  if (amount == null) return null
  return `¥${Number(amount).toFixed(2)}`
}

function formatDate(input?: null | string) {
  if (!input) return ''
  return input.replace('T', ' ').slice(0, 10)
}

async function loadList() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchRelayRecommends({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      sortBy: sortKey.value,
    })
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function applySearch() {
  keyword.value = keywordInput.value.trim()
  pageNum.value = 1
  loadList()
}

function changeSort(key: SortKey) {
  if (sortKey.value === key) return
  sortKey.value = key
  pageNum.value = 1
  loadList()
}

function changePage(next: number) {
  if (next < 1 || next > totalPages.value || next === pageNum.value) return
  pageNum.value = next
  loadList()
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

watch(pageSize, () => {
  pageNum.value = 1
  loadList()
})

onMounted(loadList)
</script>

<template>
  <section class="recommend-page">
    <header class="hero">
      <p class="eyebrow">Editor&apos;s Pick</p>
      <h1>中转站推荐</h1>
      <p class="lead">
        每一条推荐都来自我自己长期使用并真实充值过的中转。
        附上推荐原因、个人测评、优缺点、使用场景与累计充值，方便你决定要不要试。
      </p>
    </header>

    <div class="toolbar">
      <div class="sort-bar">
        <span class="bar-label">排序</span>
        <button
          v-for="opt in sortOptions"
          :key="opt.key"
          :class="['chip', { active: sortKey === opt.key }]"
          type="button"
          @click="changeSort(opt.key)"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="search-bar">
        <input
          v-model="keywordInput"
          class="search-input"
          placeholder="搜索推荐原因 / 测评 / 场景"
          type="search"
          @keyup.enter="applySearch"
        />
        <button class="search-btn" type="button" @click="applySearch">
          搜索
        </button>
      </div>
    </div>

    <div v-if="errorMsg" class="state state-error">{{ errorMsg }}</div>
    <div v-else-if="loading && list.length === 0" class="state">加载中…</div>
    <div v-else-if="list.length === 0" class="state">暂无推荐</div>

    <ul v-else class="card-list">
      <li v-for="item in list" :key="item.id" class="rec-card">
        <header class="card-head">
          <div class="logo-wrap">
            <img
              v-if="item.provider_logo_url"
              :alt="item.provider_name"
              :src="item.provider_logo_url"
              class="logo-img"
            />
            <span v-else class="logo-text">
              {{ item.provider_logo_text || 'AI' }}
            </span>
          </div>
          <div class="head-info">
            <h3 class="provider-name">
              <a
                v-if="item.website_url"
                :href="item.website_url"
                target="_blank"
                rel="noopener"
              >
                {{ item.provider_name }}
              </a>
              <span v-else>{{ item.provider_name }}</span>
            </h3>
            <p v-if="item.use_scenario" class="scenario">
              <span class="scenario-label">场景</span>
              {{ item.use_scenario }}
            </p>
          </div>
          <div v-if="formatScore(item.review_score)" class="score">
            <span class="score-num">{{ formatScore(item.review_score) }}</span>
            <span class="score-unit">/10</span>
          </div>
        </header>

        <p class="reason">{{ item.recommend_reason }}</p>

        <div
          v-if="splitLines(item.pros).length || splitLines(item.cons).length"
          class="pros-cons"
        >
          <div v-if="splitLines(item.pros).length" class="pc-block pros">
            <span class="pc-title">优点</span>
            <ul>
              <li v-for="(p, idx) in splitLines(item.pros)" :key="idx">
                {{ p }}
              </li>
            </ul>
          </div>
          <div v-if="splitLines(item.cons).length" class="pc-block cons">
            <span class="pc-title">缺点</span>
            <ul>
              <li v-for="(c, idx) in splitLines(item.cons)" :key="idx">
                {{ c }}
              </li>
            </ul>
          </div>
        </div>

        <footer class="card-foot">
          <div class="meta-group">
            <span v-if="item.recharge_count != null" class="meta">
              <span class="meta-label">已充值</span>
              <span class="meta-value">
                {{ item.recharge_count }} 次
                <template
                  v-if="item.total_cny_amount != null && Number(item.total_cny_amount) > 0"
                >
                  · {{ formatMoney(item.total_cny_amount) }}
                </template>
              </span>
            </span>
            <span v-if="item.first_use_time" class="meta">
              <span class="meta-label">首次使用</span>
              <span class="meta-value">{{ formatDate(item.first_use_time) }}</span>
            </span>
            <span v-if="item.recommend_time" class="meta">
              <span class="meta-label">推荐于</span>
              <span class="meta-value">{{ formatDate(item.recommend_time) }}</span>
            </span>
          </div>
        </footer>
      </li>
    </ul>

    <div v-if="totalPages > 1" class="pager">
      <button
        :disabled="pageNum <= 1"
        class="page-btn"
        type="button"
        @click="changePage(pageNum - 1)"
      >
        上一页
      </button>
      <span class="page-info">
        {{ pageNum }} / {{ totalPages }}
      </span>
      <button
        :disabled="pageNum >= totalPages"
        class="page-btn"
        type="button"
        @click="changePage(pageNum + 1)"
      >
        下一页
      </button>
    </div>

    <p class="footnote">
      推荐均为本人实际使用并充值，不接广告。
      榜单会随我自己的使用体验滚动更新。
    </p>
  </section>
</template>

<style scoped>
.recommend-page {
  display: grid;
  gap: 1.25rem;
}

.hero {
  position: relative;
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  padding: clamp(1.4rem, 4vw, 2.4rem);
  background:
    radial-gradient(900px 240px at 100% 0%, color-mix(in srgb, var(--color-accent) 16%, transparent), transparent 60%),
    var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3rem);
  letter-spacing: -0.05em;
  color: var(--color-text-primary);
  line-height: 1.05;
}

.lead {
  margin: 1rem 0 0;
  max-width: 38rem;
  color: var(--color-text-secondary);
  line-height: 1.85;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.sort-bar,
.search-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.bar-label {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
}

.chip {
  padding: 0.32rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.chip.active {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
  color: var(--color-accent-text);
  font-weight: 600;
}

.search-input {
  width: clamp(12rem, 28vw, 18rem);
  padding: 0.4rem 0.8rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  outline: none;
}

.search-input:focus {
  border-color: var(--color-accent);
}

.search-btn {
  padding: 0.4rem 0.95rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-accent);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}

.search-btn:hover {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
}

.state {
  padding: 2.5rem 1rem;
  text-align: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.state-error {
  color: var(--color-danger, #d33);
  border-color: color-mix(in srgb, var(--color-danger, #d33) 35%, transparent);
}

.card-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr));
  gap: 1rem;
}

.rec-card {
  display: grid;
  gap: 0.85rem;
  padding: 1.1rem 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.rec-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md, 0 6px 18px rgba(0, 0, 0, 0.08));
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
}

.logo-wrap {
  flex-shrink: 0;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: var(--radius-md);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.logo-text {
  font-size: 0.95rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.head-info {
  flex: 1;
  min-width: 0;
}

.provider-name {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.provider-name a {
  color: inherit;
  text-decoration: none;
}

.provider-name a:hover {
  color: var(--color-accent-text);
  text-decoration: underline;
}

.scenario {
  margin: 0.3rem 0 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.scenario-label {
  display: inline-block;
  padding: 0.05rem 0.45rem;
  margin-right: 0.4rem;
  border-radius: 4px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  letter-spacing: 0.05em;
}

.score {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  gap: 0.1rem;
  padding: 0.2rem 0.55rem;
  border-radius: var(--radius-md);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-accent) 18%, transparent),
    color-mix(in srgb, var(--color-accent) 6%, transparent)
  );
}

.score-num {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--color-accent-text);
  letter-spacing: -0.02em;
}

.score-unit {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.reason {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.7;
  color: var(--color-text-primary);
}

.pros-cons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: 0.6rem;
}

.pc-block {
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
}

.pc-block.pros {
  background: color-mix(in srgb, #16a34a 8%, var(--color-bg-soft));
  border-color: color-mix(in srgb, #16a34a 20%, var(--color-border));
}

.pc-block.cons {
  background: color-mix(in srgb, #f97316 8%, var(--color-bg-soft));
  border-color: color-mix(in srgb, #f97316 20%, var(--color-border));
}

.pc-title {
  display: block;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin-bottom: 0.3rem;
}

.pros .pc-title {
  color: #16a34a;
}

.cons .pc-title {
  color: #d97706;
}

.pc-block ul {
  margin: 0;
  padding-left: 1rem;
  display: grid;
  gap: 0.2rem;
}

.pc-block li {
  font-size: 0.78rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.card-foot {
  margin-top: 0.2rem;
  border-top: 1px dashed var(--color-border);
  padding-top: 0.6rem;
}

.meta-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem 1rem;
}

.meta {
  display: inline-flex;
  align-items: baseline;
  gap: 0.25rem;
  font-size: 0.74rem;
}

.meta-label {
  color: var(--color-text-secondary);
}

.meta-value {
  color: var(--color-text-primary);
  font-weight: 600;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  padding: 0.75rem;
}

.page-btn {
  padding: 0.4rem 0.9rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn:not(:disabled):hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

.page-info {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.05em;
}

.footnote {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-align: center;
  padding: 1rem 0.5rem 0;
}
</style>
