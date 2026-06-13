<script setup lang="ts">
import { Icon } from '@iconify/vue'

/**
 * 纯 CSS 还原 orccode 命令面板（暗色圆角卡片 + 搜索输入 + 结果列表 + 快捷键提示）。
 * 参考 D:\orccode\forge\src\renderer\components\ui\command.tsx 的结构与样式。
 */
const results = [
  { icon: 'lucide:app-window', name: 'Visual Studio Code', hint: '应用', active: true },
  { icon: 'lucide:chrome', name: 'Google Chrome', hint: '应用' },
  { icon: 'lucide:clipboard-list', name: '剪贴板历史', hint: '插件' },
  { icon: 'lucide:folder', name: '打开下载文件夹', hint: '文件' },
]
</script>

<template>
  <div class="mock" aria-hidden="true">
    <div class="mock__bar">
      <span class="mock__dot mock__dot--r" />
      <span class="mock__dot mock__dot--y" />
      <span class="mock__dot mock__dot--g" />
    </div>

    <div class="mock__input">
      <Icon icon="lucide:search" class="mock__search" />
      <span class="mock__query">vsc<span class="mock__caret" /></span>
      <span class="mock__kbd">esc</span>
    </div>

    <ul class="mock__list">
      <li
        v-for="item in results"
        :key="item.name"
        class="mock__row"
        :class="{ 'mock__row--active': item.active }"
      >
        <Icon :icon="item.icon" class="mock__icon" />
        <span class="mock__name">{{ item.name }}</span>
        <span class="mock__hint">{{ item.hint }}</span>
      </li>
    </ul>

    <div class="mock__footer">
      <span><span class="mock__key">↑</span><span class="mock__key">↓</span> 选择</span>
      <span><span class="mock__key">↵</span> 启动</span>
      <span class="mock__brand">orccode</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mock {
  width: 100%;
  max-width: 30rem;
  border-radius: var(--radius-xl);
  background: var(--color-bg-elevated);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  color: #e7ecff;
  font-family: var(--font-mono);
}

.mock__bar {
  display: flex;
  gap: 0.4rem;
  padding: 0.75rem 0.9rem;
}

.mock__dot {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 50%;
  opacity: 0.85;
}
.mock__dot--r { background: #ff5f57; }
.mock__dot--y { background: #febc2e; }
.mock__dot--g { background: #28c840; }

.mock__input {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0 1rem;
  height: 3rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.mock__search {
  width: 1.1rem;
  height: 1.1rem;
  color: #8ea0c9;
}

.mock__query {
  flex: 1;
  font-size: 0.95rem;
  display: inline-flex;
  align-items: center;
}

.mock__caret {
  display: inline-block;
  width: 2px;
  height: 1.05rem;
  margin-left: 2px;
  background: var(--color-brand);
  animation: blink 1.1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.mock__kbd {
  font-size: 0.7rem;
  color: #8ea0c9;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 0.3rem;
  padding: 0.1rem 0.4rem;
}

.mock__list {
  list-style: none;
  margin: 0;
  padding: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.mock__row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-md);
  font-size: 0.9rem;
}

.mock__row--active {
  background: color-mix(in srgb, var(--color-brand) 28%, transparent);
}

.mock__icon {
  width: 1.15rem;
  height: 1.15rem;
  color: #b9c6ec;
}

.mock__row--active .mock__icon {
  color: #fff;
}

.mock__name {
  flex: 1;
}

.mock__hint {
  font-size: 0.7rem;
  color: #8ea0c9;
}

.mock__footer {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.6rem 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 0.72rem;
  color: #8ea0c9;
}

.mock__brand {
  margin-left: auto;
  font-weight: 700;
  color: var(--color-brand);
}

.mock__key {
  display: inline-grid;
  place-items: center;
  min-width: 1.1rem;
  height: 1.1rem;
  margin-right: 0.2rem;
  padding: 0 0.2rem;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 0.3rem;
  font-size: 0.7rem;
}
</style>
