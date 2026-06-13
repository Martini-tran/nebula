<script setup lang="ts">
import { Icon } from '@iconify/vue'

const points = [
  { icon: 'lucide:package-plus', text: '插件按需发现与安装，能力即插即用' },
  { icon: 'lucide:app-window-mac', text: '独立插件窗口，互不干扰' },
  { icon: 'lucide:shield', text: '运行时隔离，安全可控' },
]
</script>

<template>
  <section id="plugins" class="plugins">
    <div class="plugins__inner">
      <div class="plugins__text">
        <p class="section-head__eyebrow">插件生态</p>
        <h2 class="plugins__title">不止是启动器，更是可生长的平台</h2>
        <p class="plugins__desc">
          orccode 内建插件系统：每个插件运行在独立窗口与运行时中，互不影响。
          首发即附带「剪贴板历史」插件，更多能力可按需扩展。
        </p>

        <ul class="plugins__list">
          <li v-for="point in points" :key="point.text">
            <Icon :icon="point.icon" class="plugins__list-icon" />
            <span>{{ point.text }}</span>
          </li>
        </ul>
      </div>

      <div class="plugin-card">
        <div class="plugin-card__head">
          <div class="plugin-card__icon">
            <Icon icon="lucide:clipboard-list" />
          </div>
          <div>
            <h3 class="plugin-card__name">剪贴板历史</h3>
            <p class="plugin-card__tag">内置插件 · 已启用</p>
          </div>
          <span class="plugin-card__badge">Built-in</span>
        </div>
        <p class="plugin-card__body">
          自动记录复制内容，命令面板中快速检索并回填，再也不怕剪贴板被覆盖。
        </p>
        <div class="plugin-card__foot">
          <span><Icon icon="lucide:history" /> 历史回溯</span>
          <span><Icon icon="lucide:search" /> 即时搜索</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.plugins {
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x);
}

.plugins__inner {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  align-items: center;
  gap: clamp(2rem, 5vw, 4rem);
  max-width: var(--container-max-width);
  margin: 0 auto;
}

.section-head__eyebrow {
  color: var(--color-brand);
  font-weight: 700;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.plugins__title {
  margin-top: 0.6rem;
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.plugins__desc {
  margin-top: 1rem;
  color: var(--color-text-secondary);
  font-size: 1.05rem;
  line-height: 1.7;
  max-width: 32rem;
}

.plugins__list {
  list-style: none;
  margin: 1.5rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.85rem;
}

.plugins__list li {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  color: var(--color-text-primary);
  font-weight: 500;
}

.plugins__list-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: var(--color-brand);
  flex-shrink: 0;
}

.plugin-card {
  padding: 1.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-md);
}

.plugin-card__head {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.plugin-card__icon {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.plugin-card__icon svg {
  width: 1.6rem;
  height: 1.6rem;
}

.plugin-card__name {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.plugin-card__tag {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.plugin-card__badge {
  margin-left: auto;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-brand);
  background: var(--color-brand-soft);
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
}

.plugin-card__body {
  margin-top: 1.1rem;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.plugin-card__foot {
  display: flex;
  gap: 1.25rem;
  margin-top: 1.25rem;
  padding-top: 1.1rem;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.plugin-card__foot span {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.plugin-card__foot svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-brand);
}

@media (max-width: 900px) {
  .plugins__inner {
    grid-template-columns: 1fr;
  }
}
</style>
