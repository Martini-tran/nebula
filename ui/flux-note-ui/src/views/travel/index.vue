<script setup lang="ts">
const travelCards = [
  { city: '城市漫游', note: '路线、咖啡馆、书店和适合发呆的角落。' },
  { city: '山海之间', note: '把风景、交通、住宿和体力消耗讲清楚。' },
  { city: '短途周末', note: '两三天能完成的小旅行，不折腾但有记忆点。' },
]
</script>

<template>
  <section class="travel-page">
    <div class="travel-hero">
      <p>Travel Log</p>
      <h2>旅行</h2>
      <span>旅行不是逃离工作流，而是换一种方式读世界。这里准备放路线、攻略、照片和一些现场感。</span>
    </div>

    <div class="travel-grid">
      <article v-for="item in travelCards" :key="item.city">
        <h3>{{ item.city }}</h3>
        <p>{{ item.note }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.travel-page {
  display: grid;
  gap: 1rem;
}

.travel-hero {
  min-height: 18rem;
  border-radius: 1.5rem;
  border: 1px solid var(--color-border);
  padding: clamp(1.35rem, 4vw, 2.6rem);
  background:
    radial-gradient(circle at 78% 20%, rgba(14, 165, 233, 0.18), transparent 32%),
    radial-gradient(circle at 18% 80%, color-mix(in srgb, var(--color-accent) 20%, transparent), transparent 34%),
    linear-gradient(135deg, var(--color-bg-surface), var(--color-bg-soft));
  box-shadow: var(--shadow-sm);
}

.travel-hero p {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.travel-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.06em;
}

.travel-hero span {
  display: block;
  max-width: 38rem;
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.travel-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.travel-grid article {
  min-height: 10rem;
  border-radius: 1.2rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.15rem;
  box-shadow: var(--shadow-sm);
}

.travel-grid h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.travel-grid p {
  margin: 0.5rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

@media (min-width: 768px) {
  .travel-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
