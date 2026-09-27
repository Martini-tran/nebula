<script setup lang="ts">
/**
 * 便签：底色由用户挑，正文渲染轻量 Markdown（待办可直接勾），网址变成可存进书签的 chip。
 * 底部显示剩余寿命，只剩 1 天的标红。悬停出现：置顶、转为任务、复制、删除。
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { extractUrls, renderMarkdown, shortUrl } from '../../../utils/markdown'
import { lifeLabel } from '../noteLife'
import { shortStamp } from '../../../utils/date'
import type { Note } from '../../../types/notes'

const props = defineProps<{ note: Note }>()
const emit = defineEmits<{
  open: []
  togglePin: []
  toTask: []
  copy: []
  remove: []
  restore: []
  toggleTodo: [line: number]
  saveLink: [url: string]
}>()

/** 单独成行的网址由下方 chip 展示（带「存书签」），正文里不再重复 */
const html = computed(() => renderMarkdown(props.note.content, { hideBareUrls: true }))
const links = computed(() => extractUrls(props.note.content).slice(0, 3))
const life = computed(() => lifeLabel(props.note))

const onClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (target instanceof HTMLInputElement && target.dataset.line) {
    event.stopPropagation()
    emit('toggleTodo', Number(target.dataset.line))
    return
  }
  if (target.closest('a, button')) return
  emit('open')
}
</script>

<template>
  <article class="nc" :class="`nc--${note.color}`" tabindex="0" @click="onClick" @keydown.enter.self="emit('open')">
    <div class="nc__body md" v-html="html" />

    <div v-if="links.length" class="nc__links">
      <span v-for="url in links" :key="url" class="nc__link">
        <Icon icon="lucide:link" />
        <a :href="url" target="_blank" rel="noopener noreferrer">{{ shortUrl(url, 28) }}</a>
        <button type="button" title="存进书签" @click.stop="emit('saveLink', url)"><Icon icon="lucide:bookmark-plus" /></button>
      </span>
    </div>

    <div v-if="note.tags.length" class="nc__tags">
      <span v-for="tag in note.tags" :key="tag" class="nc__tag">#{{ tag }}</span>
    </div>

    <footer class="nc__foot">
      <span class="nc__life" :class="`nc__life--${life.tone}`">
        <Icon :icon="life.icon" />{{ life.text }}
      </span>
      <span class="nc__time">{{ shortStamp(note.updateTime) }}</span>
    </footer>

    <div class="nc__acts" @click.stop>
      <template v-if="note.archived">
        <button type="button" title="恢复为临时笔记" @click="emit('restore')"><Icon icon="lucide:archive-restore" /></button>
      </template>
      <template v-else>
        <button type="button" :title="note.pinned ? '取消置顶（变回临时笔记）' : '置顶（转为长期笔记）'" @click="emit('togglePin')">
          <Icon :icon="note.pinned ? 'lucide:pin-off' : 'lucide:pin'" />
        </button>
        <button type="button" title="转为任务" @click="emit('toTask')"><Icon icon="lucide:square-check-big" /></button>
      </template>
      <button type="button" title="复制内容" @click="emit('copy')"><Icon icon="lucide:copy" /></button>
      <button type="button" title="删除" class="danger" @click="emit('remove')"><Icon icon="lucide:trash-2" /></button>
    </div>
  </article>
</template>

<style scoped>
.nc {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 0.85rem;
  padding: 0.9rem 1rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--note-plain);
  box-shadow: var(--shadow-sm);
  break-inside: avoid;
  cursor: pointer;
  transition:
    box-shadow 0.2s ease,
    transform 0.2s var(--ease-soft);
}

.nc:hover,
.nc:focus-visible {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
  outline: none;
}

.nc--yellow {
  background: var(--note-yellow);
  border-color: color-mix(in srgb, var(--note-yellow) 70%, var(--color-border));
}

.nc--green {
  background: var(--note-green);
  border-color: color-mix(in srgb, var(--note-green) 70%, var(--color-border));
}

.nc--blue {
  background: var(--note-blue);
  border-color: color-mix(in srgb, var(--note-blue) 70%, var(--color-border));
}

.nc--pink {
  background: var(--note-pink);
  border-color: color-mix(in srgb, var(--note-pink) 70%, var(--color-border));
}

.nc--purple {
  background: var(--note-purple);
  border-color: color-mix(in srgb, var(--note-purple) 70%, var(--color-border));
}

.nc__body {
  max-height: 16rem;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, #000 85%, transparent);
}

.nc__links {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.nc__link {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 100%;
  padding: 0.25rem 0.3rem 0.25rem 0.5rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-surface) 70%, transparent);
  font-size: 0.8rem;
}

.nc__link > svg {
  flex: none;
  color: var(--color-text-secondary);
}

.nc__link a {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-brand);
}

.nc__link button {
  display: inline-grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.nc__link button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.nc__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.nc__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.nc__life {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 600;
}

.nc__life svg {
  width: 0.85rem;
  height: 0.85rem;
}

.nc__life--pinned {
  color: var(--color-brand);
}

.nc__life--danger {
  color: var(--color-danger);
}

.nc__acts {
  position: absolute;
  top: 0.45rem;
  right: 0.45rem;
  display: flex;
  padding: 0.15rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.nc:hover .nc__acts,
.nc:focus-within .nc__acts {
  opacity: 1;
  pointer-events: auto;
}

@media (hover: none) {
  .nc__acts {
    position: static;
    align-self: flex-end;
    opacity: 1;
    pointer-events: auto;
  }
}

.nc__acts button {
  display: inline-grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.nc__acts button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.nc__acts .danger:hover {
  color: var(--color-danger);
}
</style>
