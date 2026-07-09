<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'AuthenticationFormView',
});

const props = defineProps<{
  dataSide?: 'bottom' | 'left' | 'right' | 'top';
}>();

/**
 * @zh_CN 中心卡片模式（bottom）由外层容器负责滚动；
 * 侧边面板（left/right/top）自身作为滚动容器，内容超出时从顶部开始滚动，避免被裁剪。
 */
const isSidePanel = computed(() => props.dataSide !== 'bottom');
</script>

<template>
  <div
    :class="isSidePanel ? 'h-full overflow-y-auto' : ''"
    class="relative bg-background lg:flex-initial dark:bg-background-deep"
  >
    <!-- min-h-full 让内容不足一屏时垂直居中，超出一屏时从顶部开始可滚动 -->
    <div
      class="flex min-h-full w-full flex-col items-center justify-center px-6 py-10 lg:px-8"
    >
      <slot></slot>
      <!-- Router View with Transition and KeepAlive -->
      <RouterView v-slot="{ Component, route }">
        <Transition appear mode="out-in" name="slide-right">
          <KeepAlive :include="['Login']">
            <component
              :is="Component"
              :key="route.fullPath"
              class="side-content mt-6 w-full sm:mx-auto md:max-w-md"
              :data-side="dataSide"
            />
          </KeepAlive>
        </Transition>
      </RouterView>
    </div>

    <!-- Footer Copyright -->
    <div
      class="absolute bottom-3 flex w-full justify-center text-center text-xs text-muted-foreground"
    >
      <slot name="copyright"> </slot>
    </div>
  </div>
</template>
