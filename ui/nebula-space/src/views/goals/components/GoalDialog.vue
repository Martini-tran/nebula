<script setup lang="ts">
/** 新建 / 编辑目标：数值型选数据来源（不用自己填进度），关键结果型列几条勾选项。 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import type { Goal, GoalSaveRequest, GoalSource, KeyResult } from '../../../types/goals'
import type { Habit } from '../../../types/habits'
import type { TaskList } from '../../../types/tasks'

const props = defineProps<{ open: boolean; goal: Goal | null; year: number; habits: Habit[]; lists: TaskList[] }>()
const emit = defineEmits<{ close: []; save: [body: GoalSaveRequest & { title: string; year: number }] }>()

const ICONS = ['🎯', '🏃', '📚', '💰', '🚀', '🧘', '📷', '✈️', '🎸', '💪', '🌱', '✍️']
const SOURCES: { key: GoalSource; label: string; hint: string }[] = [
  { key: 'habit', label: '习惯打卡', hint: '打卡一次折算多少' },
  { key: 'reading', label: '稍后读', hint: '当年读完的篇数' },
  { key: 'ledger', label: '记账结余', hint: '当年收入减支出' },
  { key: 'task_list', label: '任务清单', hint: '当年完成的任务数' },
  { key: 'manual', label: '手动记', hint: '自己点 +1 更新' },
]

const icon = ref('🎯')
const title = ref('')
const kind = ref<Goal['kind']>('metric')
const target = ref('')
const unit = ref('')
const source = ref<GoalSource>('habit')
const sourceId = ref('')
const factor = ref('1')
const baseline = ref('')
const manualValue = ref('')
const krs = ref<KeyResult[]>([])

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const g = props.goal
    icon.value = g?.icon ?? '🎯'
    title.value = g?.title ?? ''
    kind.value = g?.kind ?? 'metric'
    target.value = g ? String(g.target || '') : ''
    unit.value = g?.unit ?? ''
    source.value = g?.source ?? 'habit'
    sourceId.value = g?.sourceId !== null && g?.sourceId !== undefined ? String(g.sourceId) : String(props.habits[0]?.id ?? '')
    factor.value = String(g?.factor ?? 1)
    baseline.value = g?.baseline ? String(g.baseline) : ''
    manualValue.value = g?.manualValue ? String(g.manualValue) : ''
    krs.value = g ? (JSON.parse(JSON.stringify(g.krs)) as KeyResult[]) : [{ id: `k${Date.now()}`, title: '', done: false, doneDate: null, listId: null }]
  },
)

watch(source, (s) => {
  if (s === 'habit' && !props.habits.some((h) => String(h.id) === sourceId.value)) sourceId.value = String(props.habits[0]?.id ?? '')
  if (s === 'task_list' && !props.lists.some((l) => String(l.id) === sourceId.value)) sourceId.value = String(props.lists[0]?.id ?? '')
  if (s === 'ledger' && !unit.value) unit.value = '¥'
  if (s === 'reading' && !unit.value) unit.value = '篇'
})

const habit = computed(() => props.habits.find((h) => String(h.id) === sourceId.value))

const addKr = () => krs.value.push({ id: `k${Date.now()}`, title: '', done: false, doneDate: null, listId: null })
const removeKr = (id: string) => (krs.value = krs.value.filter((k) => k.id !== id))

const valid = computed(() => {
  if (!title.value.trim()) return false
  if (kind.value === 'metric') return Number(target.value) > 0 && (!['habit', 'task_list'].includes(source.value) || Boolean(sourceId.value))
  return krs.value.some((k) => k.title.trim())
})

const submit = () => {
  if (!valid.value) return
  const metric = kind.value === 'metric'
  emit('save', {
    year: props.goal?.year ?? props.year,
    icon: icon.value,
    title: title.value.trim(),
    kind: kind.value,
    target: metric ? Number(target.value) : 0,
    unit: metric ? unit.value.trim() : '',
    source: metric ? source.value : 'manual',
    sourceId: metric && ['habit', 'task_list'].includes(source.value) ? sourceId.value : null,
    factor: Number(factor.value) > 0 ? Number(factor.value) : 1,
    baseline: Number(baseline.value) || 0,
    manualValue: Number(manualValue.value) || 0,
    krs: metric ? [] : krs.value.filter((k) => k.title.trim()).map((k) => ({ ...k, title: k.title.trim() })),
  })
}
</script>

<template>
  <BaseDialog :open="open" :title="goal ? '编辑目标' : `新的 ${year} 年目标`" width="32rem" @close="emit('close')">
    <form class="form gd" @submit.prevent="submit">
      <div class="gd__name">
        <select v-model="icon" class="gd__icon" aria-label="图标">
          <option v-for="i in ICONS" :key="i" :value="i">{{ i }}</option>
        </select>
        <input v-model="title" class="field__input" type="text" placeholder="例如：跑满 1000 公里" aria-label="目标" maxlength="40" />
      </div>

      <div class="seg" role="radiogroup" aria-label="类型">
        <button type="button" role="radio" :aria-checked="kind === 'metric'" :class="{ on: kind === 'metric' }" @click="kind = 'metric'">数值 · 自动取进度</button>
        <button type="button" role="radio" :aria-checked="kind === 'milestone'" :class="{ on: kind === 'milestone' }" @click="kind = 'milestone'">关键结果 · 勾选</button>
      </div>

      <template v-if="kind === 'metric'">
        <div class="row2">
          <label class="field">
            <span class="field__label">目标值</span>
            <input v-model="target" class="field__input" type="number" min="1" />
          </label>
          <label class="field">
            <span class="field__label">单位</span>
            <input v-model="unit" class="field__input" type="text" placeholder="km、本、¥" maxlength="6" />
          </label>
        </div>

        <div class="field">
          <span class="field__label">进度从哪来</span>
          <div class="chips" role="radiogroup" aria-label="进度来源">
            <button v-for="s in SOURCES" :key="s.key" type="button" role="radio" :aria-checked="source === s.key" :class="{ on: source === s.key }" :title="s.hint" @click="source = s.key">{{ s.label }}</button>
          </div>
        </div>

        <div v-if="source === 'habit'" class="row2">
          <label class="field">
            <span class="field__label">哪个习惯</span>
            <select v-model="sourceId" class="field__input">
              <option v-for="h in habits" :key="h.id" :value="String(h.id)">{{ h.icon }} {{ h.name }}</option>
            </select>
          </label>
          <label class="field">
            <span class="field__label">{{ habit?.kind === 'check' ? '打卡一次算' : `每 1 ${habit?.unit || '单位'}算` }}</span>
            <input v-model="factor" class="field__input" type="number" min="0.1" step="0.1" />
          </label>
        </div>
        <label v-else-if="source === 'task_list'" class="field">
          <span class="field__label">哪个清单</span>
          <select v-model="sourceId" class="field__input">
            <option v-for="l in lists" :key="l.id" :value="String(l.id)">{{ l.name }}</option>
          </select>
        </label>

        <label v-if="source === 'manual'" class="field">
          <span class="field__label">现在已经有</span>
          <input v-model="manualValue" class="field__input" type="number" min="0" />
        </label>
        <label v-else class="field">
          <span class="field__label">开始用 Space 之前已经有<small>（会加在自动统计上）</small></span>
          <input v-model="baseline" class="field__input" type="number" min="0" placeholder="0" />
        </label>
      </template>

      <div v-else class="field">
        <span class="field__label">关键结果</span>
        <div v-for="(k, i) in krs" :key="k.id" class="kr">
          <input v-model="k.title" class="field__input" type="text" :placeholder="`第 ${i + 1} 条，例如：上线某功能`" :aria-label="`关键结果 ${i + 1}`" />
          <select v-model="k.listId" class="field__input kr__list" :aria-label="`关键结果 ${i + 1} 关联清单`">
            <option :value="null">不关联清单</option>
            <option v-for="l in lists" :key="l.id" :value="l.id">清单：{{ l.name }}</option>
          </select>
          <button type="button" class="btn btn--quiet kr__x" :aria-label="`删掉第 ${i + 1} 条`" @click="removeKr(k.id)"><Icon icon="lucide:x" /></button>
        </div>
        <button type="button" class="btn btn--ghost btn--sm add" @click="addKr"><Icon icon="lucide:plus" />再加一条</button>
      </div>
    </form>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!valid" @click="submit">{{ goal ? '保存' : '创建' }}</button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.gd {
  gap: 1rem;
  padding: 0;
}

.gd__name {
  display: flex;
  gap: 0.5rem;
}

.gd__icon {
  width: 3.4rem;
  padding: 0 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  font-size: 1.2rem;
}

.seg {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.3rem 0.8rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.8rem;
}

.field__label small {
  font-weight: 400;
  color: var(--color-text-secondary);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.chips button {
  padding: 0.25rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.82rem;
  cursor: pointer;
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.kr {
  display: flex;
  gap: 0.4rem;
}

.kr__list {
  width: 9rem;
  flex: none;
}

.kr__x {
  padding: 0.3rem;
}

.btn--sm {
  padding: 0.28rem 0.6rem;
  font-size: 0.8rem;
}

.add {
  align-self: flex-start;
}
</style>
