<script setup lang="ts">
const scenarios = [
  {
    title: 'Coding 长上下文',
    desc: '日均 500K token、需要 64K 上下文，重视延迟和缓存命中。',
    tag: '编程',
  },
  {
    title: '内容创作脚本',
    desc: '稳定的批量调用，价格敏感，可接受按量计费。',
    tag: '写作',
  },
  {
    title: '中小团队共享',
    desc: '5-10 人席位、需要日志审计和配额告警。',
    tag: '团队',
  },
]
</script>

<template>
  <section class="placeholder-page">
    <header class="hero">
      <p class="eyebrow">Buyer&apos;s Guide</p>
      <h1>比价选站</h1>
      <p class="lead">
        告诉我你的使用场景，给出最具性价比的中转 + 套餐组合。
        不止看单价，把可用率、退款政策、支付方式全部纳入推荐逻辑。
      </p>
      <span class="badge-coming">即将上线</span>
    </header>

    <ul class="scenario-list">
      <li v-for="item in scenarios" :key="item.title">
        <span class="scenario-tag">{{ item.tag }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
      </li>
    </ul>

    <p class="footnote">
      场景模板会逐步开放，敬请期待。也可以在「中转测评」中先按当前推荐分挑选。
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

.scenario-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
  gap: 0.85rem;
}

.scenario-list li {
  display: grid;
  gap: 0.5rem;
  padding: 1.1rem 1.2rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.scenario-tag {
  width: fit-content;
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.scenario-list h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.scenario-list p {
  margin: 0;
  font-size: 0.85rem;
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
