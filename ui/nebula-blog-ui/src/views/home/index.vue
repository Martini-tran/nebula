<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ArticleCard from './components/ArticleCard.vue'
import { fetchArticles, type PostListItem } from '../../api/post'

const recentArticles = ref<PostListItem[]>([])
const articlesLoading = ref(false)

const loadRecent = async () => {
  articlesLoading.value = true
  try {
    const data = await fetchArticles({ limit: 6 })
    recentArticles.value = data?.items ?? []
  } catch {
    recentArticles.value = []
  } finally {
    articlesLoading.value = false
  }
}

onMounted(loadRecent)
</script>

<template>
  <div class="landing">

    <!-- ── Hero ── -->
    <section class="hero-card">
      <div class="hero-body">
        <p class="hero-eyebrow">FluxNote</p>
        <h1 class="hero-title">记录技术<br>沉淀经验</h1>
        <p class="hero-desc">
          后端开发、系统设计、数据库工程与工作流思考。<br>
          把值得写清楚的东西放在这里，慢慢积累。
        </p>
        <div class="hero-actions">
          <RouterLink to="/articles" class="btn-primary">浏览文章</RouterLink>
          <RouterLink to="/essays" class="btn-ghost">随笔</RouterLink>
        </div>
      </div>
      <!-- 装饰点阵 -->
      <div class="hero-dots" aria-hidden="true"></div>
    </section>

    <!-- ── 频道快捷入口 ── -->
    <div class="channel-row">
      <RouterLink to="/articles" class="channel-chip">
        <span class="channel-chip__icon">📝</span>
        <span>文章</span>
      </RouterLink>
      <RouterLink to="/essays" class="channel-chip">
        <span class="channel-chip__icon">✍️</span>
        <span>随笔</span>
      </RouterLink>
      <RouterLink to="/travel" class="channel-chip">
        <span class="channel-chip__icon">🗺️</span>
        <span>旅行</span>
      </RouterLink>
      <RouterLink to="/reviews" class="channel-chip">
        <span class="channel-chip__icon">🔀</span>
        <span>中转站测评</span>
      </RouterLink>
    </div>

    <!-- ── 最近更新 ── -->
    <section class="recent-section">
      <div class="recent-header">
        <div>
          <p class="section-eyebrow">Latest Posts</p>
          <h2 class="section-title">最近更新</h2>
        </div>
        <RouterLink to="/articles" class="see-all">
          查看全部 <span aria-hidden="true">→</span>
        </RouterLink>
      </div>

      <!-- 加载中 -->
      <div v-if="articlesLoading" class="state-card">正在加载...</div>

      <!-- 无文章 -->
      <div v-else-if="recentArticles.length === 0" class="state-card">
        暂无文章，敬请期待
      </div>

      <!-- 文章网格 -->
      <div v-else class="recent-grid">
        <ArticleCard
          v-for="item in recentArticles"
          :key="item.id"
          :slug="item.slug"
          :title="item.title"
          :post-type="item.post_type"
          :summary="item.summary"
          :categories="item.categories"
          :tags="item.tags"
          :published-at="item.published_at"
          :cover-url="item.cover_url"
        />
      </div>

      <div v-if="!articlesLoading && recentArticles.length > 0" class="more-row">
        <RouterLink to="/articles" class="more-link">
          还有更多文章，前往浏览 →
        </RouterLink>
      </div>
    </section>

  </div>
</template>

<style scoped>
/* ── 整体布局 ── */
.landing {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* ── Hero 卡片 ── */
.hero-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1.5rem;
  background:
    radial-gradient(ellipse at 0% 0%, color-mix(in srgb, var(--color-accent) 22%, transparent) 0%, transparent 55%),
    radial-gradient(ellipse at 100% 100%, color-mix(in srgb, var(--color-accent) 10%, transparent) 0%, transparent 40%),
    linear-gradient(145deg, var(--color-bg-surface), var(--color-bg-soft));
  box-shadow:
    0 1px 0 color-mix(in srgb, var(--color-accent) 12%, transparent) inset,
    var(--shadow-sm);
  padding: clamp(2rem, 5vw, 3.5rem) clamp(1.5rem, 4vw, 3rem);
}

.hero-body {
  position: relative;
  z-index: 1;
  max-width: 42rem;
}

.hero-eyebrow {
  margin: 0 0 0.75rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent-text);
}

.hero-title {
  margin: 0;
  font-size: clamp(2.25rem, 6vw, 3.75rem);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.05em;
  color: var(--color-text-primary);
}

.hero-desc {
  margin: 1.1rem 0 0;
  font-size: clamp(0.9rem, 1.5vw, 1.05rem);
  line-height: 1.8;
  color: var(--color-text-secondary);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.625rem;
  margin-top: 1.75rem;
}

/* 按钮 */
.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  padding: 0.65rem 1.35rem;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.btn-primary {
  background: var(--color-text-primary);
  color: var(--color-bg-surface);
  box-shadow: 0 4px 12px -4px color-mix(in srgb, var(--color-text-primary) 30%, transparent);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px -6px color-mix(in srgb, var(--color-text-primary) 35%, transparent);
}

.btn-ghost {
  background: color-mix(in srgb, var(--color-bg-surface) 70%, transparent);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  backdrop-filter: blur(4px);
}

.btn-ghost:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  transform: translateY(-2px);
}

/* 装饰点阵 */
.hero-dots {
  position: absolute;
  right: -1rem;
  bottom: -1rem;
  width: 14rem;
  height: 14rem;
  background-image: radial-gradient(
    circle,
    color-mix(in srgb, var(--color-text-secondary) 18%, transparent) 1px,
    transparent 1px
  );
  background-size: 1.25rem 1.25rem;
  mask-image: radial-gradient(ellipse at 80% 80%, black 30%, transparent 75%);
  pointer-events: none;
}

/* ── 频道快捷入口 ── */
.channel-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.625rem;
}

.channel-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.5rem 1rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-decoration: none;
  box-shadow: var(--shadow-sm);
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.channel-chip:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 30%, transparent);
  transform: translateY(-1px);
}

.channel-chip__icon {
  font-size: 1rem;
  line-height: 1;
}

/* ── 最近更新区 ── */
.recent-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.recent-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.section-eyebrow {
  margin: 0 0 0.2rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.section-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--color-text-primary);
}

.see-all {
  flex-shrink: 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-accent-text);
  text-decoration: none;
  transition: gap 0.15s ease, opacity 0.15s ease;
}

.see-all:hover {
  opacity: 0.75;
}

/* 文章网格 */
.recent-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
}

@media (min-width: 640px) {
  .recent-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .recent-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

/* ── 底部更多链接 ── */
.more-row {
  text-align: center;
  padding: 0.25rem 0;
}

.more-link {
  display: inline-block;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-decoration: none;
  padding: 0.625rem 1.5rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.more-link:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
  border-color: color-mix(in srgb, var(--color-text-secondary) 30%, var(--color-border));
  transform: translateY(-1px);
}

/* ── 通用状态卡片 ── */
.state-card {
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.5rem;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}
</style>
