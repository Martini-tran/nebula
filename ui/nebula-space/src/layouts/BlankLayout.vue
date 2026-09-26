<script setup lang="ts">
/** 无导航外壳：登录页、无权限页。 */
</script>

<template>
  <main class="blank">
    <router-view />
  </main>
</template>

<style scoped>
.blank {
  min-height: 100vh;
}
</style>
