<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { product } from '../../../data/product'

const router = useRouter()
</script>

<template>
  <section class="hero">
    <div class="hero__inner">
      <div class="hero__copy">
        <span class="tag tag--brand hero__eyebrow">
          <Icon icon="lucide:sparkles" />
          AI 辅助 · 长篇连载
        </span>
        <h1 class="hero__title">
          写到第三百章，<br />
          依然记得第三章埋下的那把刀。
        </h1>
        <p class="hero__desc">{{ product.description }}</p>

        <div class="hero__actions">
          <button class="btn btn--primary" type="button" @click="router.push('/works')">
            <Icon icon="lucide:pen-line" />
            进入写作台
          </button>
          <button class="btn btn--ghost" type="button" @click="router.push('/discover')">
            <Icon icon="lucide:lightbulb" />
            先找找灵感
          </button>
        </div>

        <dl class="hero__stats">
          <div>
            <dt>AI 协作方式</dt>
            <dd>8 种</dd>
          </div>
          <div>
            <dt>设定分类</dt>
            <dd>人物 / 地点 / 势力 / 道具 / 世界观</dd>
          </div>
          <div>
            <dt>写作主题</dt>
            <dd>浅色 · 深色 · 纸张</dd>
          </div>
        </dl>
      </div>

      <!-- 写作台预览：纯 CSS 静态还原，不引入真实编辑器以免拖慢首屏 -->
      <div class="mockup" aria-hidden="true">
        <div class="mockup__bar">
          <span class="dot" /><span class="dot" /><span class="dot" />
          <span class="mockup__file">断刀记 · 第三章 空屋里的灯</span>
        </div>
        <div class="mockup__body">
          <aside class="mockup__aside">
            <p class="mockup__aside-title">卷章</p>
            <span class="mockup__row" />
            <span class="mockup__row" />
            <span class="mockup__row mockup__row--active" />
            <span class="mockup__row" />
            <p class="mockup__aside-title">设定</p>
            <span class="mockup__chip">沈砚</span>
            <span class="mockup__chip">鼓楼</span>
            <span class="mockup__chip">更漏规则</span>
          </aside>
          <div class="mockup__paper">
            <p>他推开门。屋里没有人，桌上的茶还温着，杯沿留着一道极浅的口脂痕。</p>
            <p class="mockup__selected">窗是从里面闩上的，可窗台的灰上印着半只脚印——朝外。</p>
            <p class="mockup__ghost">
              <Icon icon="lucide:sparkles" class="mockup__ai-icon" />
              AI 续写：沈砚在桌边坐下，把断刀横放在膝上……
            </p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.hero {
  background: var(--gradient-hero);
  border-bottom: 1px solid var(--color-border);
}

.hero__inner {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  align-items: center;
  gap: 3rem;
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(3rem, 7vw, 5.5rem) var(--space-page-x);
}

.hero__eyebrow {
  font-size: 0.8rem;
}

.hero__title {
  margin-top: 1rem;
  font-size: clamp(1.9rem, 4.2vw, 3rem);
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.hero__desc {
  margin-top: 1.1rem;
  max-width: 34rem;
  font-size: 1.02rem;
  color: var(--color-text-secondary);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.75rem;
}

.hero__actions svg {
  width: 1.05rem;
  height: 1.05rem;
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 2rem;
  margin: 2.25rem 0 0;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-border);
}

.hero__stats dt {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.hero__stats dd {
  margin: 0.25rem 0 0;
  font-size: 0.95rem;
  font-weight: 700;
}

.mockup {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.mockup__bar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}

.dot {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: var(--color-border);
}

.mockup__file {
  margin-left: 0.6rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.mockup__body {
  display: grid;
  grid-template-columns: 8.5rem 1fr;
  min-height: 19rem;
}

.mockup__aside {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.9rem 0.75rem;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-canvas);
}

.mockup__aside-title {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-text-secondary);
  text-transform: uppercase;
}

.mockup__aside-title:not(:first-child) {
  margin-top: 0.7rem;
}

.mockup__row {
  height: 0.55rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.mockup__row:nth-child(3) {
  width: 80%;
}

.mockup__row:nth-child(5) {
  width: 65%;
}

.mockup__row--active {
  background: var(--color-brand);
  opacity: 0.65;
}

.mockup__chip {
  align-self: flex-start;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  font-size: 0.68rem;
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.mockup__paper {
  padding: 1.4rem 1.3rem;
  font-family: var(--font-serif);
  font-size: 0.86rem;
  line-height: 2;
}

.mockup__paper p {
  text-indent: 2em;
  margin-bottom: 0.6rem;
}

.mockup__selected {
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  box-shadow: 0 0 0 0.3rem var(--color-brand-soft);
}

.mockup__ghost {
  color: var(--color-text-secondary);
  opacity: 0.75;
  font-style: italic;
}

.mockup__ai-icon {
  width: 0.85rem;
  height: 0.85rem;
  vertical-align: -0.1rem;
  color: var(--color-brand);
}

@media (max-width: 940px) {
  .hero__inner {
    grid-template-columns: 1fr;
  }

  .mockup {
    order: -1;
  }
}
</style>
