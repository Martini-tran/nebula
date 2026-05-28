<script setup lang="ts">
const features = [
  {
    title: '官方 vs 中转',
    desc: '同一模型在官方与各中转之间的延迟、成功率、价格倍率对比。',
    icon: '⚖️',
  },
  {
    title: '上下文 / 输出限额',
    desc: '不同套餐下的最大上下文 token、单次输出长度、流式表现。',
    icon: '📐',
  },
  {
    title: '缓存命中',
    desc: '高频提示词在不同中转的缓存命中率与节省成本。',
    icon: '🧊',
  },
]
</script>

<template>
  <section class="placeholder-page">
    <header class="hero">
      <p class="eyebrow">Model Compare</p>
      <h1>模型比较</h1>
      <p class="lead">
        把同一个 AI 模型，在不同中转、不同套餐下的真实表现摆到同一张表上。
        延迟、可用率、缓存命中、上下文限额，一眼看清。
      </p>
      <span class="badge-coming">即将上线</span>
    </header>

    <ul class="feature-list">
      <li v-for="item in features" :key="item.title">
        <span class="feature-icon" aria-hidden="true">{{ item.icon }}</span>
        <div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </div>
      </li>
    </ul>

    <p class="footnote">
      数据来源：编辑长期监控 + 用户提交的真实账户日志，按周更新。
    </p>
  </section>
</template>

<style scoped>
.placeholder-page {
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

.badge-coming {
  display: inline-flex;
  align-items: center;
  margin-top: 1.2rem;
  padding: 0.3rem 0.8rem;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.feature-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 0.85rem;
}

.feature-list li {
  display: flex;
  gap: 0.85rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.feature-icon {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.feature-list h3 {
  margin: 0 0 0.3rem;
  font-size: 0.95rem;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}

.feature-list p {
  margin: 0;
  font-size: 0.84rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.footnote {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  text-align: center;
  padding: 1rem 0.5rem 0;
}
</style>
