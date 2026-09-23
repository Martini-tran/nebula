<script setup lang="ts">
const designSource = 'docs/ui设计/AI工作流'
</script>

<template>
  <main class="app-shell">
    <p class="eyebrow">Nebula Flow</p>
    <h1>AI 工作流项目骨架</h1>
    <p class="description">
      页面实现将基于 <code>{{ designSource }}</code> 中的原型继续展开。
    </p>
  </main>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  min-width: 320px;
  color: #1e2528;
  background: #f4f1ea;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

.app-shell {
  width: min(720px, calc(100vw - 48px));
  margin: 16vh auto 0;
  padding: 48px;
  border: 1px solid #dcded8;
  border-radius: 24px;
  background: #fffefa;
  box-shadow: 0 16px 36px rgb(35 42 44 / 8%);
}

.eyebrow {
  margin: 0 0 12px;
  color: #e56f55;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(30px, 5vw, 48px);
  line-height: 1.15;
}

.description {
  margin: 24px 0 0;
  color: #697274;
  font-size: 16px;
  line-height: 1.8;
}

code {
  padding: 2px 6px;
  border-radius: 6px;
  color: #a65342;
  background: #f8e7df;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 0.9em;
}
</style>

