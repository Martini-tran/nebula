<script setup lang="ts">
/** 书签图标：有 favicon 就用，加载失败或没有时退回首字母方块，避免破图 */
import { computed, ref, watch } from 'vue'
import type { Bookmark } from '../../../types/space'

const props = withDefaults(defineProps<{ bookmark: Bookmark; size?: string }>(), { size: '2.25rem' })

const broken = ref(false)
watch(
  () => props.bookmark.faviconUrl,
  () => (broken.value = false),
)

const initial = computed(() => {
  const text = props.bookmark.title || props.bookmark.domain || props.bookmark.url
  return text.trim().charAt(0).toUpperCase()
})
</script>

<template>
  <span class="fav" :style="{ width: size, height: size, fontSize: `calc(${size} * 0.42)` }" aria-hidden="true">
    <img v-if="bookmark.faviconUrl && !broken" :src="bookmark.faviconUrl" alt="" loading="lazy" @error="broken = true" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.fav {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 800;
  overflow: hidden;
}

.fav img {
  width: 60%;
  height: 60%;
  object-fit: contain;
}
</style>
