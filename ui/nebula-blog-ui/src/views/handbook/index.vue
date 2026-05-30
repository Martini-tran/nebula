<script setup lang="ts">
const handbookSections = [
  {
    title: '开发速查',
    desc: '常用命令、排错路径、配置模板和那些一查就该有答案的小问题。',
    tag: 'Cheatsheet',
  },
  {
    title: '架构清单',
    desc: '服务拆分、缓存、消息、索引、观测性等主题的决策检查项。',
    tag: 'Checklist',
  },
  {
    title: '实践范式',
    desc: '把反复验证有效的方法沉淀成可复用的步骤，而不是每次从零开始。',
    tag: 'Playbook',
  },
]
</script>

<template>
  <section class="handbook-page">
    <div class="handbook-hero">
      <p>Handbook</p>
      <h2>宝典</h2>
      <span>这里放高频知识、排错口诀、工程经验和可复用清单。目标不是写得长，而是关键时刻能救命。</span>
    </div>

    <div class="handbook-grid">
      <article v-for="item in handbookSections" :key="item.title">
        <span>{{ item.tag }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.handbook-page {
  display: grid;
  gap: 1rem;
}

.handbook-hero {
  border-radius: 1.5rem;
  border: 1px solid var(--color-border);
  padding: clamp(1.35rem, 4vw, 2.6rem);
  background:
    radial-gradient(circle at 82% 18%, color-mix(in srgb, var(--color-accent) 18%, transparent), transparent 32%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  box-shadow: var(--shadow-sm);
}

.handbook-hero p {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.handbook-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.06em;
}

.handbook-hero span {
  display: block;
  max-width: 42rem;
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.handbook-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.handbook-grid article {
  border-radius: 1.2rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.15rem;
  box-shadow: var(--shadow-sm);
}

.handbook-grid article > span {
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.handbook-grid h3 {
  margin: 0.5rem 0 0;
  color: var(--color-text-primary);
}

.handbook-grid p {
  margin: 0.45rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

@media (min-width: 768px) {
  .handbook-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
