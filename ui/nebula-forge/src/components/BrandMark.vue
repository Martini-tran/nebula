<script setup lang="ts">
/**
 * orccode 品牌标识：宝蓝圆底 + 白色节点图谱（中心节点连接三个卫星节点）。
 * 还原 D:\orccode\forge\assets\logo.png 的节点图形。
 */
withDefaults(defineProps<{ size?: number }>(), { size: 36 })
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    role="img"
    aria-label="orccode logo"
    class="brand-mark"
  >
    <circle cx="32" cy="32" r="30" fill="var(--color-brand)" />
    <g stroke="#fff" stroke-width="4" stroke-linecap="round">
      <line x1="32" y1="34" x2="32" y2="20" />
      <line x1="32" y1="34" x2="18" y2="38" />
      <line x1="32" y1="34" x2="46" y2="38" />
    </g>
    <g fill="#fff">
      <circle cx="32" cy="34" r="6" />
      <circle cx="32" cy="18" r="4" />
      <circle cx="17" cy="38" r="4" />
      <circle cx="47" cy="38" r="4" />
    </g>
  </svg>
</template>

<style scoped>
.brand-mark {
  display: block;
  border-radius: 50%;
}
</style>
