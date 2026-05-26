<script setup lang="ts">
const reviewItems = [
  { title: '机场体验', desc: '中转动线、休息区、餐饮和登机效率。' },
  { title: '航司服务', desc: '座位、餐食、准点率和服务稳定性。' },
  { title: '酒店与休息室', desc: '短停时最关心的睡眠、洗漱和安静程度。' },
]
</script>

<template>
  <section class="review-page">
    <div class="review-hero">
      <p>Transit Reviews</p>
      <h2>中转站测评</h2>
      <span>给赶路的人看的实用测评：能不能睡、好不好走、值不值得多留几个小时。</span>
    </div>

    <div class="review-list">
      <article v-for="(item, index) in reviewItems" :key="item.title">
        <span>{{ String(index + 1).padStart(2, '0') }}</span>
        <div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.review-page {
  display: grid;
  gap: 1rem;
}

.review-hero {
  border-radius: 1.5rem;
  border: 1px solid var(--color-border);
  padding: clamp(1.35rem, 4vw, 2.6rem);
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.08), transparent),
    var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.review-hero p {
  margin: 0 0 0.55rem;
  color: var(--color-accent-text);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.review-hero h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.06em;
}

.review-hero span {
  display: block;
  max-width: 42rem;
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.review-list {
  display: grid;
  gap: 0.8rem;
}

.review-list article {
  display: flex;
  gap: 1rem;
  border-radius: 1.15rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1rem;
}

.review-list article > span {
  color: var(--color-accent-text);
  font-weight: 900;
  letter-spacing: -0.04em;
}

.review-list h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.review-list p {
  margin: 0.4rem 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}
</style>
