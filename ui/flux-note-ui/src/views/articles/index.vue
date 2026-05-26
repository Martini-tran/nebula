<script setup lang="ts">
const articleLanes = [
  {
    title: '技术长文',
    desc: '沉淀后端、架构、数据库与工程实践，适合慢慢读。',
    count: '持续更新',
  },
  {
    title: '问题复盘',
    desc: '记录踩坑现场、排查路径和最后的修复方式。',
    count: '按主题整理',
  },
  {
    title: '工具与效率',
    desc: '把好用的工作流、脚本和开发习惯收拢到一起。',
    count: '实用优先',
  },
]
</script>

<template>
  <section class="channel-page channel-page--articles">
    <div class="channel-hero">
      <p class="channel-kicker">Articles</p>
      <h2>文章</h2>
      <p>把值得展开讲清楚的内容放在这里：技术方案、系统设计、项目复盘，还有那些后来证明很有用的经验。</p>
    </div>

    <div class="channel-grid">
      <article v-for="item in articleLanes" :key="item.title" class="channel-card">
        <span>{{ item.count }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.channel-page {
  display: grid;
  gap: 1rem;
}

.channel-hero,
.channel-card {
  border: 1px solid var(--color-border);
  border-radius: 1.35rem;
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.channel-hero {
  overflow: hidden;
  padding: clamp(1.35rem, 4vw, 2.5rem);
  background:
    radial-gradient(circle at 12% 18%, color-mix(in srgb, var(--color-accent) 18%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
}

.channel-kicker {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.channel-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.06em;
}

.channel-hero p:last-child {
  max-width: 42rem;
  margin: 0.8rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.channel-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.channel-card {
  padding: 1.2rem;
}

.channel-card span {
  color: var(--color-accent-text);
  font-size: 0.78rem;
  font-weight: 800;
}

.channel-card h3 {
  margin: 0.45rem 0 0;
  color: var(--color-text-primary);
  font-size: 1.1rem;
}

.channel-card p {
  margin: 0.45rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

@media (min-width: 768px) {
  .channel-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
