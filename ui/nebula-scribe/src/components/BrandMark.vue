<script setup lang="ts">
/**
 * Scribe 品牌标识：赭石圆底 + 一支落笔的钢笔尖，笔锋下带一道墨痕。
 */
withDefaults(defineProps<{ size?: number }>(), { size: 36 })
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    role="img"
    aria-label="Scribe logo"
    class="brand-mark"
  >
    <circle cx="32" cy="32" r="30" fill="var(--color-brand)" />
    <!-- 笔尖：上宽下尖的等腰梯形收成一点 -->
    <path d="M24 14 L40 14 L36 40 L32 46 L28 40 Z" fill="#fff" />
    <!-- 笔尖中缝与气孔 -->
    <path d="M32 20 L32 40" stroke="var(--color-brand)" stroke-width="2.4" stroke-linecap="round" />
    <circle cx="32" cy="26" r="3" fill="var(--color-brand)" />
    <!-- 落笔后的墨痕 -->
    <path
      d="M20 52 C26 46, 38 46, 44 52"
      fill="none"
      stroke="#fff"
      stroke-width="3"
      stroke-linecap="round"
      opacity="0.75"
    />
  </svg>
</template>

<style scoped>
.brand-mark {
  display: block;
  border-radius: 50%;
}
</style>
