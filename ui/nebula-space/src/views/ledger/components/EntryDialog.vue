<script setup lang="ts">
/** 改一笔账：金额、收支、分类、日期、备注；也可以删。 */
import { computed, ref, watch } from 'vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import type { Direction, LedgerCategory, LedgerEntry, LedgerSaveRequest } from '../../../types/ledger'

const props = defineProps<{ entry: LedgerEntry | null; categories: LedgerCategory[] }>()
const emit = defineEmits<{ close: []; save: [body: Partial<LedgerSaveRequest>]; remove: [] }>()

const amount = ref('')
const direction = ref<Direction>('out')
const categoryId = ref('')
const date = ref('')
const note = ref('')

watch(
  () => props.entry,
  (e) => {
    if (!e) return
    amount.value = (e.amount / 100).toFixed(2).replace(/\.00$/, '')
    direction.value = e.direction
    categoryId.value = String(e.categoryId)
    date.value = e.date
    note.value = e.note
  },
  { immediate: true },
)

const options = computed(() => props.categories.filter((c) => c.kind === direction.value))
watch(direction, () => {
  if (!options.value.some((c) => String(c.id) === categoryId.value)) categoryId.value = String(options.value.at(-1)?.id ?? '')
})

const cents = computed(() => Math.round(Number(amount.value) * 100))
const valid = computed(() => Number.isFinite(cents.value) && cents.value > 0 && Boolean(categoryId.value) && /^\d{4}-\d{2}-\d{2}$/.test(date.value))

const submit = () => {
  if (!valid.value) return
  emit('save', { amount: cents.value, direction: direction.value, categoryId: categoryId.value, date: date.value, note: note.value.trim() })
}
</script>

<template>
  <BaseDialog :open="Boolean(entry)" title="修改这笔账" width="26rem" @close="emit('close')">
    <form class="form" @submit.prevent="submit">
      <div class="seg" role="radiogroup" aria-label="收支">
        <button type="button" role="radio" :aria-checked="direction === 'out'" :class="{ on: direction === 'out' }" @click="direction = 'out'">支出</button>
        <button type="button" role="radio" :aria-checked="direction === 'in'" :class="{ on: direction === 'in' }" @click="direction = 'in'">收入</button>
      </div>
      <label class="field">
        <span class="field__label">金额（元）</span>
        <input v-model="amount" class="field__input" type="number" min="0.01" step="0.01" inputmode="decimal" />
      </label>
      <label class="field">
        <span class="field__label">分类</span>
        <select v-model="categoryId" class="field__input">
          <option v-for="c in options" :key="c.id" :value="String(c.id)">{{ c.icon }} {{ c.name }}</option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">日期</span>
        <input v-model="date" class="field__input" type="date" />
      </label>
      <label class="field">
        <span class="field__label">备注</span>
        <input v-model="note" class="field__input" type="text" maxlength="60" />
      </label>
      <p v-if="entry?.recurringId" class="hint">这笔由周期账单自动生成；改了只影响这一笔。</p>
    </form>
    <template #footer>
      <button class="btn btn--quiet danger" type="button" @click="emit('remove')">删除</button>
      <span class="grow" />
      <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!valid" @click="submit">保存</button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.form {
  gap: 0.9rem;
  padding: 0;
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 1rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.hint {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.grow {
  flex: 1;
}

.danger:hover {
  color: var(--color-danger);
}
</style>
