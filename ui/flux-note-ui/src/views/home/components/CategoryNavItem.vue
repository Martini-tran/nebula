<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import type { CategoryNode } from '../../../api/category'

const props = defineProps<{
  node: CategoryNode
  level: number
  activeId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

const expanded = ref(false)
const hasChildren = computed(() => props.node.children.length > 0)
const isActive = computed(() => props.activeId === props.node.id)

const handleRowClick = () => {
  if (hasChildren.value) {
    expanded.value = !expanded.value
  }
  emit('select', props.node.id)
}

const handleChevronClick = (event: MouseEvent) => {
  event.stopPropagation()
  if (hasChildren.value) {
    expanded.value = !expanded.value
  }
}

const setStyles = (el: Element, styles: Record<string, string>) => {
  const target = el as HTMLElement
  for (const [key, value] of Object.entries(styles)) {
    target.style.setProperty(key, value)
  }
}

const onBeforeEnter = (el: Element) => {
  setStyles(el, { height: '0px', opacity: '0', overflow: 'hidden' })
}

const onEnter = (el: Element, done: () => void) => {
  const target = el as HTMLElement
  void target.offsetHeight
  setStyles(el, {
    transition: 'height 240ms cubic-bezier(0.4, 0, 0.2, 1), opacity 200ms ease 40ms',
    height: `${target.scrollHeight}px`,
    opacity: '1',
  })
  const handle = () => {
    target.removeEventListener('transitionend', handle)
    done()
  }
  target.addEventListener('transitionend', handle)
}

const onAfterEnter = (el: Element) => {
  setStyles(el, { transition: '', height: '', opacity: '', overflow: '' })
}

const onBeforeLeave = (el: Element) => {
  const target = el as HTMLElement
  setStyles(el, {
    height: `${target.scrollHeight}px`,
    opacity: '1',
    overflow: 'hidden',
  })
}

const onLeave = (el: Element, done: () => void) => {
  const target = el as HTMLElement
  void target.offsetHeight
  setStyles(el, {
    transition: 'height 220ms cubic-bezier(0.4, 0, 0.2, 1), opacity 160ms ease',
    height: '0px',
    opacity: '0',
  })
  const handle = () => {
    target.removeEventListener('transitionend', handle)
    done()
  }
  target.addEventListener('transitionend', handle)
}

const onAfterLeave = (el: Element) => {
  setStyles(el, { transition: '', height: '', opacity: '', overflow: '' })
}
</script>

<template>
  <li class="nav-node">
    <div
      class="nav-row"
      :class="{ 'nav-row--active': isActive, 'nav-row--branch': hasChildren }"
      @click="handleRowClick"
    >
      <button
        v-if="hasChildren"
        type="button"
        class="nav-row__chevron"
        :class="{ 'is-expanded': expanded }"
        :aria-label="expanded ? '收起' : '展开'"
        @click="handleChevronClick"
      >
        <Icon icon="lucide:chevron-right" width="14" height="14" />
      </button>
      <span v-else class="nav-row__bullet" aria-hidden="true"></span>

      <span class="nav-row__name">{{ node.name }}</span>

      <span v-if="hasChildren" class="nav-row__count">{{ node.children.length }}</span>
    </div>

    <transition
      :css="false"
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @after-enter="onAfterEnter"
      @before-leave="onBeforeLeave"
      @leave="onLeave"
      @after-leave="onAfterLeave"
    >
      <ul v-if="hasChildren && expanded" class="nav-children">
        <CategoryNavItem
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :level="level + 1"
          :active-id="activeId"
          @select="(id) => emit('select', id)"
        />
      </ul>
    </transition>
  </li>
</template>

<style scoped>
.nav-node {
  list-style: none;
}

.nav-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4375rem 0.75rem;
  border-radius: 0.625rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.nav-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.4375rem;
  bottom: 0.4375rem;
  width: 2px;
  border-radius: 2px;
  background: transparent;
  transition: background 0.15s ease;
}

.nav-row:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.nav-row--active {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  font-weight: 600;
}

.nav-row--active:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.nav-row--active::before {
  background: var(--color-accent);
}

.nav-row__chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.125rem;
  height: 1.125rem;
  padding: 0;
  border: 0;
  border-radius: 0.375rem;
  background: transparent;
  color: inherit;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.18s ease, background 0.15s ease;
}

.nav-row__chevron:hover {
  background: color-mix(in srgb, var(--color-text-primary) 8%, transparent);
}

.nav-row__chevron.is-expanded {
  transform: rotate(90deg);
}

.nav-row__bullet {
  display: inline-block;
  width: 1.125rem;
  height: 1.125rem;
  flex-shrink: 0;
  position: relative;
}

.nav-row__bullet::before {
  content: '';
  position: absolute;
  inset: 0;
  margin: auto;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.35;
}

.nav-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.005em;
}

.nav-row__count {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.375rem;
  padding: 0 0.4375rem;
  height: 1.125rem;
  border-radius: 9999px;
  background: color-mix(in srgb, var(--color-text-secondary) 12%, transparent);
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  line-height: 1;
}

.nav-row--active .nav-row__count {
  background: color-mix(in srgb, var(--color-accent) 22%, transparent);
  color: var(--color-accent-text);
}

.nav-children {
  position: relative;
  margin: 0.25rem 0 0.125rem 0.875rem;
  padding-left: 0.625rem;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  list-style: none;
  border-left: 1px dashed var(--color-border);
}

@media (prefers-reduced-motion: reduce) {
  .nav-children {
    transition: none !important;
  }
}
</style>
