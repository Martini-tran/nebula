<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
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

const expandedIds = ref<Set<number>>(new Set())

function isExpanded(id: number) {
  return expandedIds.value.has(id)
}

function toggleExpand(id: number) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

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
              v-if="item.providerLogoUrl"
              :alt="item.providerName"
              :src="item.providerLogoUrl"
              class="logo-img"
            />
            <span v-else class="logo-text">
              {{ item.providerLogoText || 'AI' }}
            </span>
          </div>
          <div class="head-info">
            <h3 class="provider-name">
              <a
                v-if="item.websiteUrl"
                :href="item.websiteUrl"
                target="_blank"
                rel="noopener"
              >
                {{ item.providerName }}
              </a>
              <span v-else>{{ item.providerName }}</span>
              <span v-if="item.websiteUrl" class="site-link" aria-hidden="true">↗</span>
            </h3>
            <p v-if="item.providerDescription" class="provider-desc">
              {{ item.providerDescription }}
            </p>
            <div v-if="item.useScenario" class="scenario-row">
              <span class="scenario-label">适用场景</span>
              <span class="scenario-text">{{ item.useScenario }}</span>
            </div>
          </div>
          <div v-if="formatScore(item.reviewScore)" class="score">
            <span class="score-num">{{ formatScore(item.reviewScore) }}</span>
            <span class="score-unit">/ 10</span>
            <span class="score-tag">个人评分</span>
          </div>
        </header>

        <section class="reason-block">
          <span class="block-label">推荐理由</span>
          <p class="reason">{{ item.recommendReason }}</p>
        </section>

        <section v-if="item.reviewContent" class="review-block">
          <button
            type="button"
            class="review-toggle"
            :class="{ expanded: isExpanded(item.id) }"
            :aria-expanded="isExpanded(item.id)"
            @click="toggleExpand(item.id)"
          >
            <span class="block-label">详细测评</span>
            <span class="toggle-hint">
              {{ isExpanded(item.id) ? '收起' : '展开阅读' }}
              <span class="toggle-arrow" aria-hidden="true">▾</span>
            </span>
          </button>
          <div v-show="isExpanded(item.id)" class="review-body">
            <MdPreview
              :model-value="item.reviewContent"
              :editor-id="`review-md-${item.id}`"
              class="review-md"
              preview-theme="default"
            />
          </div>
        </section>

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
          <div class="stat-grid">
            <div v-if="item.rechargeCount != null" class="stat-cell">
              <span class="stat-label">充值次数</span>
              <span class="stat-value">
                {{ item.rechargeCount }}
                <span class="stat-unit">次</span>
              </span>
            </div>
            <div
              v-if="item.totalCnyAmount != null && Number(item.totalCnyAmount) > 0"
              class="stat-cell"
            >
              <span class="stat-label">累计金额</span>
              <span class="stat-value money">{{ formatMoney(item.totalCnyAmount) }}</span>
            </div>
            <div v-if="item.lastRechargeTime" class="stat-cell">
              <span class="stat-label">最近充值</span>
              <span class="stat-value">{{ formatDate(item.lastRechargeTime) }}</span>
            </div>
            <div v-if="item.firstUseTime" class="stat-cell">
              <span class="stat-label">首次使用</span>
              <span class="stat-value">{{ formatDate(item.firstUseTime) }}</span>
            </div>
            <div v-if="item.reviewTime" class="stat-cell">
              <span class="stat-label">测评时间</span>
              <span class="stat-value">{{ formatDate(item.reviewTime) }}</span>
            </div>
            <div v-if="item.recommendTime" class="stat-cell">
              <span class="stat-label">推荐时间</span>
              <span class="stat-value">{{ formatDate(item.recommendTime) }}</span>
            </div>
          </div>

          <div class="foot-actions">
            <a
              v-if="item.websiteUrl"
              class="action primary"
              :href="item.websiteUrl"
              target="_blank"
              rel="noopener"
            >
              访问官网
            </a>
            <router-link
              :to="`/reviews/detail/${item.providerId}`"
              class="action ghost"
            >
              查看详情
            </router-link>
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
  grid-template-columns: 1fr;
  gap: 1.25rem;
}

.rec-card {
  display: grid;
  gap: 1.1rem;
  padding: 1.6rem 1.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.rec-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  box-shadow: var(--shadow-md, 0 10px 28px rgba(0, 0, 0, 0.08));
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 1.1rem;
  padding-bottom: 1.1rem;
  border-bottom: 1px solid var(--color-border);
}

.logo-wrap {
  flex-shrink: 0;
  width: 3.6rem;
  height: 3.6rem;
  border-radius: var(--radius-lg);
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--color-accent) 25%, transparent);
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.logo-text {
  font-size: 1.2rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.head-info {
  flex: 1;
  min-width: 0;
}

.provider-name {
  margin: 0;
  font-size: 1.45rem;
  font-weight: 800;
  color: var(--color-text-primary);
  letter-spacing: -0.015em;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.provider-name a {
  color: inherit;
  text-decoration: none;
}

.provider-name a:hover {
  color: var(--color-accent-text);
  text-decoration: underline;
}

.site-link {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.provider-desc {
  margin: 0.4rem 0 0;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scenario-row {
  margin-top: 0.55rem;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.scenario-label {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.scenario-text {
  font-size: 0.82rem;
  color: var(--color-text-primary);
  line-height: 1.5;
}

.score {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.15rem;
  padding: 0.65rem 1rem;
  border-radius: var(--radius-lg);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-accent) 22%, transparent),
    color-mix(in srgb, var(--color-accent) 6%, transparent)
  );
  border: 1px solid color-mix(in srgb, var(--color-accent) 25%, transparent);
  min-width: 5rem;
}

.score-num {
  font-size: 1.85rem;
  font-weight: 800;
  color: var(--color-accent-text);
  letter-spacing: -0.03em;
  line-height: 1;
}

.score-unit {
  font-size: 0.72rem;
  color: var(--color-text-secondary);
  margin-top: 0.1rem;
}

.score-tag {
  font-size: 0.66rem;
  color: var(--color-text-secondary);
  letter-spacing: 0.08em;
  margin-top: 0.15rem;
}

.block-label {
  display: inline-block;
  padding: 0.18rem 0.65rem;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  margin-bottom: 0.7rem;
}

.reason-block,
.review-block {
  display: block;
}

.reason {
  margin: 0;
  font-size: 1.1rem;
  line-height: 1.85;
  color: var(--color-text-primary);
  font-weight: 500;
  white-space: pre-wrap;
}

.review-block .block-label {
  background: color-mix(in srgb, #6366f1 14%, transparent);
  color: #6366f1;
  margin: 0;
}

.review-toggle {
  appearance: none;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.review-toggle:hover {
  border-color: color-mix(in srgb, #6366f1 35%, var(--color-border));
  background: color-mix(in srgb, #6366f1 6%, var(--color-bg-soft));
}

.review-toggle.expanded {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  border-bottom-color: transparent;
}

.toggle-hint {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.toggle-arrow {
  display: inline-block;
  transition: transform 0.2s ease;
  font-size: 0.85rem;
}

.review-toggle.expanded .toggle-arrow {
  transform: rotate(180deg);
}

.review-body {
  border: 1px solid var(--color-border);
  border-top: none;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  background: var(--color-bg-soft);
  padding: 0.4rem 0.6rem 0.4rem 1rem;
  border-left: 3px solid color-mix(in srgb, var(--color-accent) 45%, transparent);
}

.review-md {
  --md-bg-color: var(--color-bg-soft);
}

.review-md :deep(.md-editor-preview-wrapper) {
  padding: 0.6rem 0.4rem;
}

.review-md :deep(.md-editor-preview) {
  font-size: 0.95rem;
  line-height: 1.85;
  color: var(--color-text-secondary);
  background: transparent;
}

.review-md :deep(.md-editor-preview h1),
.review-md :deep(.md-editor-preview h2),
.review-md :deep(.md-editor-preview h3),
.review-md :deep(.md-editor-preview h4) {
  color: var(--color-text-primary);
  margin-top: 1rem;
}

.review-md :deep(.md-editor-preview p) {
  margin: 0.5rem 0;
}

.review-md :deep(.md-editor-preview code) {
  background: var(--color-bg);
}

.review-md :deep(.md-editor-preview a) {
  color: var(--color-accent-text);
}

.pros-cons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 0.85rem;
}

.pc-block {
  padding: 0.85rem 1rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
}

.pc-block.pros {
  background: color-mix(in srgb, #16a34a 8%, var(--color-bg-soft));
  border-color: color-mix(in srgb, #16a34a 22%, var(--color-border));
}

.pc-block.cons {
  background: color-mix(in srgb, #f97316 8%, var(--color-bg-soft));
  border-color: color-mix(in srgb, #f97316 22%, var(--color-border));
}

.pc-title {
  display: block;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin-bottom: 0.45rem;
}

.pros .pc-title {
  color: #16a34a;
}

.cons .pc-title {
  color: #d97706;
}

.pc-block ul {
  margin: 0;
  padding-left: 1.1rem;
  display: grid;
  gap: 0.3rem;
}

.pc-block li {
  font-size: 0.85rem;
  line-height: 1.65;
  color: var(--color-text-primary);
}

.card-foot {
  margin-top: 0.4rem;
  border-top: 1px dashed var(--color-border);
  padding-top: 1rem;
  display: grid;
  gap: 1rem;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: 0.5rem 0.85rem;
}

.stat-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.55rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
}

.stat-label {
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.stat-value {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.stat-value.money {
  color: var(--color-accent-text);
}

.stat-unit {
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-left: 0.1rem;
}

.foot-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  justify-content: flex-end;
}

.action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1.1rem;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid var(--color-border);
}

.action.primary {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff;
}

.action.primary:hover {
  background: color-mix(in srgb, var(--color-accent) 88%, #000);
}

.action.ghost {
  background: transparent;
  color: var(--color-text-primary);
}

.action.ghost:hover {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}

@media (max-width: 640px) {
  .rec-card {
    padding: 1.15rem 1.1rem;
  }

  .card-head {
    flex-wrap: wrap;
  }

  .score {
    flex-direction: row;
    align-items: baseline;
    padding: 0.4rem 0.7rem;
    min-width: 0;
  }

  .score-tag {
    display: none;
  }

  .reason {
    font-size: 1rem;
  }
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
