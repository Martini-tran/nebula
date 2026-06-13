<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { useThemeStore } from '../../../stores/theme'

/**
 * 带窗口边框的产品截图框。
 *
 * 截图放在 public/screenshots/ 下，支持主题变体：
 *   <name>-dark.<ext> / <name>-light.<ext>  → 跟随网站明暗主题自动切换
 *   <name>.<ext>                            → 无主题变体时的通用图
 * 三级回退：主题变体 → 通用图 → 占位块。截图缺失时显示精致占位，不破坏布局。
 */
const props = withDefaults(
  defineProps<{
    /** 截图基名，对应 public/screenshots/<name>。 */
    name: string
    /** 占位/无障碍文本。 */
    label: string
    /** 文件扩展名，GIF 动图传 'gif'。 */
    ext?: string
    /** 顶部是否显示 mac 风格窗口控制条。 */
    chrome?: boolean
    /** 画面宽高比，如 '16 / 10'。auto 模式下仅用于占位/加载阶段保持布局稳定。 */
    aspect?: string
    /**
     * 图片适配方式：
     *   auto    — 框跟随图片真实比例，不裁切、不留白（默认，适合尺寸不一的截图）
     *   contain — 固定 aspect 比例，完整显示整图，比例不符时留白边
     *   cover   — 固定 aspect 比例，裁切填满，比例不符时切掉边缘
     */
    fit?: 'auto' | 'contain' | 'cover'
  }>(),
  { ext: 'png', chrome: true, aspect: '16 / 10', fit: 'auto' },
)

const themeStore = useThemeStore()
const variant = computed(() => (themeStore.currentTheme === 'dark' ? 'dark' : 'light'))

/** 候选图源，按优先级回退。 */
const candidates = computed(() => [
  `/screenshots/${props.name}-${variant.value}.${props.ext}`,
  `/screenshots/${props.name}.${props.ext}`,
])

const index = ref(0)
const failed = ref(false)
const loaded = ref(false)
const current = computed(() => candidates.value[index.value])

// 主题切换时重新尝试加载对应变体。
watch(variant, () => {
  index.value = 0
  failed.value = false
  loaded.value = false
})

/** auto 模式且图片已加载时取消固定比例，让框跟随图片真实尺寸。 */
const stageStyle = computed(() =>
  props.fit === 'auto' && loaded.value ? {} : { aspectRatio: props.aspect },
)

const onLoad = () => {
  loaded.value = true
}

const onError = () => {
  loaded.value = false
  if (index.value < candidates.value.length - 1) {
    index.value += 1
  } else {
    failed.value = true
  }
}

defineSlots<{ placeholder?: () => unknown }>()
</script>

<template>
  <figure class="frame" :class="{ 'frame--bare': failed && !!$slots.placeholder }">
    <!-- 自定义占位（如 Hero 用 CSS mockup 兜底） -->
    <slot v-if="failed && $slots.placeholder" name="placeholder" />

    <template v-else>
      <div v-if="chrome" class="frame__bar" aria-hidden="true">
        <span class="frame__dot frame__dot--r" />
        <span class="frame__dot frame__dot--y" />
        <span class="frame__dot frame__dot--g" />
      </div>

      <div class="frame__stage" :class="`frame__stage--${fit}`" :style="stageStyle">
        <img
          v-if="!failed"
          :src="current"
          :alt="label"
          class="frame__img"
          :class="`frame__img--${fit}`"
          loading="lazy"
          @load="onLoad"
          @error="onError"
        />

        <!-- 默认占位块 -->
        <div v-else class="frame__placeholder">
          <Icon icon="lucide:image" class="frame__placeholder-icon" />
          <p class="frame__placeholder-title">{{ label }}</p>
          <p class="frame__placeholder-hint">截图占位 · 放入 /screenshots/{{ name }}.{{ ext }}</p>
        </div>
      </div>
    </template>
  </figure>
</template>

<style scoped lang="scss">
.frame {
  margin: 0;
  width: 100%;
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

/* 使用自定义占位时不加边框/底色，交给插槽内容自己呈现。 */
.frame--bare {
  background: none;
  border: 0;
  box-shadow: none;
  border-radius: 0;
  overflow: visible;
}

.frame__bar {
  display: flex;
  gap: 0.4rem;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.frame__dot {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 50%;
  opacity: 0.9;
}
.frame__dot--r { background: #ff5f57; }
.frame__dot--y { background: #febc2e; }
.frame__dot--g { background: #28c840; }

.frame__stage {
  position: relative;
  width: 100%;
  background: var(--color-bg-soft);
}

/* auto：框跟随图片真实比例，图片按自然高度铺满宽度。 */
.frame__img--auto {
  display: block;
  width: 100%;
  height: auto;
}

/* contain / cover：图片绝对定位铺满固定比例的 stage。 */
.frame__stage--contain .frame__img,
.frame__stage--cover .frame__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.frame__img--contain {
  object-fit: contain;
}

.frame__img--cover {
  object-fit: cover;
  object-position: top center;
}

.frame__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1.5rem;
  text-align: center;
  border: 1px dashed var(--color-border);
  color: var(--color-text-secondary);
  background:
    repeating-linear-gradient(
      135deg,
      transparent,
      transparent 11px,
      color-mix(in srgb, var(--color-border) 35%, transparent) 11px,
      color-mix(in srgb, var(--color-border) 35%, transparent) 12px
    );
}

.frame__placeholder-icon {
  width: 2rem;
  height: 2rem;
  color: var(--color-brand);
  opacity: 0.8;
}

.frame__placeholder-title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.frame__placeholder-hint {
  font-family: var(--font-mono);
  font-size: 0.75rem;
}
</style>
