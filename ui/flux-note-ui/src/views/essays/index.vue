<script setup lang="ts">
const essayPrompts = ['日常观察', '读书摘记', '灵感碎片', '生活复盘']
</script>

<template>
  <section class="essay-page">
    <div class="essay-hero">
      <p>Essays</p>
      <h2>随笔</h2>
      <span>不一定完整，但尽量诚实。这里放一些路上想到的、读完留下的、以及一时半会儿还没归档成文章的东西。</span>
    </div>

    <div class="essay-board">
      <article v-for="item in essayPrompts" :key="item">
        <strong>{{ item }}</strong>
        <p>先留一个入口，后面可以按标签或专题接入真实随笔内容。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.essay-page {
  display: grid;
  gap: 1rem;
}

.essay-hero {
  border-radius: 1.5rem;
  border: 1px solid var(--color-border);
  padding: clamp(1.35rem, 4vw, 2.6rem);
  background:
    linear-gradient(120deg, color-mix(in srgb, var(--color-accent-soft) 70%, transparent), transparent),
    var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.essay-hero p {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.essay-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.06em;
}

.essay-hero span {
  display: block;
  max-width: 42rem;
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.essay-board {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.85rem;
}

.essay-board article {
  border-radius: 1.1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.1rem;
}

.essay-board strong {
  color: var(--color-text-primary);
}

.essay-board p {
  margin: 0.45rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

@media (min-width: 720px) {
  .essay-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
