<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { toast } from '../../composables/useToast'

const icons = {
  ok: 'lucide:circle-check',
  error: 'lucide:circle-alert',
  info: 'lucide:info',
} as const
</script>

<template>
  <Teleport to="body">
    <div class="toasts" role="status" aria-live="polite">
      <transition-group name="toast">
        <div v-for="item in toast.state.items" :key="item.id" class="toast" :class="`toast--${item.type}`">
          <Icon :icon="icons[item.type]" class="toast__icon" />
          <span class="toast__text">{{ item.text }}</span>
          <button
            v-if="item.action"
            class="toast__action"
            type="button"
            @click="item.action.run(); toast.dismiss(item.id)"
          >
            {{ item.action.label }}
          </button>
        </div>
      </transition-group>
    </div>
  </Teleport>
</template>

<style scoped>
.toasts {
  position: fixed;
  left: 50%;
  bottom: calc(1.25rem + env(safe-area-inset-bottom, 0px));
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  max-width: min(32rem, calc(100vw - 2rem));
  padding: 0.6rem 0.9rem;
  border-radius: var(--radius-lg);
  background: var(--color-bg-elevated);
  color: #f3f4f6;
  box-shadow: var(--shadow-lg);
  font-size: 0.9rem;
  pointer-events: auto;
}

.toast__icon {
  flex: none;
  width: 1.05rem;
  height: 1.05rem;
}

.toast--ok .toast__icon {
  color: #5eead4;
}

.toast--error .toast__icon {
  color: #f87171;
}

.toast--info .toast__icon {
  color: #a5b4fc;
}

.toast__action {
  margin-left: 0.4rem;
  border: 0;
  background: none;
  color: #a5b4fc;
  font-weight: 700;
  cursor: pointer;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s var(--ease-soft);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
