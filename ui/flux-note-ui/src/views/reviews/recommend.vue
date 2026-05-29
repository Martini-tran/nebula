<script setup lang="ts">
const picks = [
  {
    title: '编辑首推',
    desc: '综合稳定性、价格与服务态度的当期主推中转。',
    tag: '推荐',
  },
  {
    title: '高性价比',
    desc: '价格友好、可用率达标的入门档选择。',
    tag: '性价比',
  },
  {
    title: '稳定优先',
    desc: '面向生产环境与团队协作的高可用方案。',
    tag: '稳定',
  },
]
</script>

<template>
  <section class="placeholder-page">
    <header class="hero">
      <p class="eyebrow">Editor&apos;s Pick</p>
      <h1>中转站推荐</h1>
      <p class="lead">
        基于长期使用记录与读者反馈，按场景挑选当前最值得入手的中转方案。
        排序会随价格、稳定性和服务质量动态更新。
      </p>
      <span class="badge-coming">即将上线</span>
    </header>

    <ul class="pick-list">
      <li v-for="item in picks" :key="item.title">
        <span class="pick-tag">{{ item.tag }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
      </li>
    </ul>

    <p class="footnote">
      推荐榜会定期刷新，欢迎在「收录中转站」中查看完整名单。
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

.pick-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
  gap: 0.85rem;
}

.pick-list li {
  display: grid;
  gap: 0.5rem;
  padding: 1.1rem 1.2rem;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.pick-tag {
  width: fit-content;
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.pick-list h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.pick-list p {
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
