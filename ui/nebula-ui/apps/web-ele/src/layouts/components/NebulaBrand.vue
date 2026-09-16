<script lang="ts" setup>
import { preferences } from '@nebula/preferences';

defineProps<{ markOnly?: boolean }>();
</script>

<template>
  <svg
    v-if="markOnly"
    viewBox="0 0 36 36"
    fill="none"
    class="size-9"
    role="img"
    :aria-label="preferences.app.name"
  >
    <path
      d="M6 28V8l12 15V8h12v20L18 13v15H6Z"
      stroke="currentColor"
      stroke-width="2.5"
      stroke-linejoin="round"
    />
  </svg>
  <div v-else class="wordmark">
    <strong>{{ preferences.app.name }}<span>.</span></strong><small>BUILD SOMETHING SMART</small>
  </div>
</template>

<style scoped>
.wordmark strong {
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.8px;
}

.wordmark strong span {
  color: hsl(var(--primary));
}

.wordmark small {
  display: block;
  margin-top: 4px;
  font-size: 9px;
  letter-spacing: 1.3px;
  white-space: nowrap;
  opacity: 0.65;
}
</style>
