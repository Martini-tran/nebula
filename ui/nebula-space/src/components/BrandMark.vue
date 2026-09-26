<script setup lang="ts">
/**
 * Space 品牌标识：靛蓝圆底 + 一枚书签，签面上一颗星点。
 */
withDefaults(defineProps<{ size?: number }>(), { size: 32 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 64 64" role="img" aria-label="Space logo" class="brand-mark">
    <circle cx="32" cy="32" r="30" fill="var(--color-brand)" />
    <path d="M22 14h20a2 2 0 0 1 2 2v34l-12-8-12 8V16a2 2 0 0 1 2-2z" fill="#fff" />
    <circle cx="32" cy="26" r="4" fill="var(--color-brand)" />
  </svg>
</template>

<style scoped>
.brand-mark {
  display: block;
  border-radius: 50%;
}
</style>
