<script setup lang="ts">
import { Icon } from '@iconify/vue'
import BaseDialog from './BaseDialog.vue'
import { confirmState, settleConfirm } from '../../composables/useConfirm'
</script>

<template>
  <BaseDialog :open="confirmState.open" :title="confirmState.options.title" width="26rem" @close="settleConfirm(false)">
    <div class="confirm">
      <Icon
        :icon="confirmState.options.danger ? 'lucide:triangle-alert' : 'lucide:circle-help'"
        class="confirm__icon"
        :class="{ 'confirm__icon--danger': confirmState.options.danger }"
      />
      <p class="confirm__msg">{{ confirmState.options.message }}</p>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="settleConfirm(false)">
        {{ confirmState.options.cancelText ?? '取消' }}
      </button>
      <button
        class="btn"
        :class="confirmState.options.danger ? 'btn--danger' : 'btn--primary'"
        type="button"
        @click="settleConfirm(true)"
      >
        {{ confirmState.options.confirmText ?? '确定' }}
      </button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.confirm {
  display: flex;
  gap: 0.75rem;
  padding: 1.15rem 1.35rem 0.5rem;
}

.confirm__icon {
  flex: none;
  width: 1.3rem;
  height: 1.3rem;
  margin-top: 0.1rem;
  color: var(--color-brand);
}

.confirm__icon--danger {
  color: var(--color-danger);
}

.confirm__msg {
  font-size: 0.92rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
  white-space: pre-line;
}
</style>
