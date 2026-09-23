<script setup lang="ts">
/** 占位页：写作台独占全屏，尚未实现。 */
import StateBlock from '../../components/StateBlock.vue'
</script>

<template>
  <main>
    <StateBlock state="empty" title="写作台" description="页面开发中，敬请期待。" />
    <p style="text-align: center">
      <RouterLink to="/works" class="btn">返回我的作品</RouterLink>
    </p>
  </main>
</template>
