<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'

const router = useRouter()
const question = ref('')

/** 与对话页的推荐问题同源：都是写作中真实会卡住的地方 */
const samples = ['怎么写一个抓人的开头？', '帮我立一个有记忆点的人物', '卡文了，写不下去怎么办']

/** 带着问题进对话页，由对话页自动发出；空输入只是打开对话页 */
const open = (text = question.value) => {
  const q = text.trim()
  router.push(q ? { name: 'chat', query: { q } } : { name: 'chat' })
}
</script>

<template>
  <section class="page">
    <div class="entry surface">
      <div class="entry__intro">
        <span class="entry__icon" aria-hidden="true">
          <Icon icon="lucide:messages-square" />
        </span>
        <div>
          <h2 class="entry__title">问问 AI 写作助手</h2>
          <p class="entry__desc">情节卡住、人物立不住、伏笔不知道怎么埋——直接问。</p>
        </div>
      </div>

      <form class="entry__form" @submit.prevent="open()">
        <label class="sr-only" for="ai-entry-input">向 AI 写作助手提问</label>
        <input
          id="ai-entry-input"
          v-model="question"
          class="entry__input"
          type="text"
          maxlength="2000"
          placeholder="说说你正在写的东西…"
          autocomplete="off"
        />
        <button class="btn btn--primary" type="submit">
          <Icon icon="lucide:send-horizontal" />
          问问看
        </button>
      </form>

      <div class="entry__samples" role="group" aria-label="示例问题">
        <button v-for="item in samples" :key="item" class="entry__sample" type="button" @click="open(item)">
          {{ item }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.entry {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.5rem;
}

.entry__intro {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}

.entry__icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.entry__icon svg {
  width: 1.4rem;
  height: 1.4rem;
}

.entry__title {
  font-size: 1.15rem;
  font-weight: 700;
}

.entry__desc {
  margin-top: 0.2rem;
  font-size: 0.92rem;
  color: var(--color-text-secondary);
}

.entry__form {
  display: flex;
  gap: 0.6rem;
}

.entry__input {
  flex: 1;
  min-width: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.entry__input::placeholder {
  color: var(--color-text-secondary);
  opacity: 0.7;
}

.entry__input:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.entry__form .btn svg {
  width: 1rem;
  height: 1rem;
}

.entry__samples {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.entry__sample {
  padding: 0.35rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.entry__sample:hover {
  color: var(--color-brand);
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 560px) {
  .entry__form {
    flex-direction: column;
  }
}
</style>
