<script setup lang="ts">
defineProps<{
  name: string
  bio: string
  avatar?: string
}>()
</script>

<template>
  <div class="card text-center">
    <div class="avatar">{{ avatar ?? '👨‍💻' }}</div>
    <h3 class="author-name">{{ name }}</h3>
    <p class="author-bio">{{ bio }}</p>
    <div class="mt-5 flex justify-center gap-2">
      <button class="social-btn">GitHub</button>
      <button class="social-btn">Email</button>
    </div>
  </div>
</template>

<style scoped>
.card {
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  padding: 1.25rem;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-border) 40%, transparent);
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 1rem;
  background: var(--color-accent-soft);
  font-size: 1.75rem;
  box-shadow: 0 4px 12px -4px color-mix(in srgb, var(--color-accent) 25%, transparent);
}

.author-name {
  margin-top: 1rem;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.author-bio {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  line-height: 1.65;
  color: var(--color-text-secondary);
}

.social-btn {
  padding: 0.4rem 1rem;
  border-radius: 9999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.social-btn:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border-color: color-mix(in srgb, var(--color-accent) 30%, transparent);
}
</style>
