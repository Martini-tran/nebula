<script setup lang="ts">
/** 新建 / 编辑目标：数值型选数据来源（不用自己填进度），关键结果型列几条勾选项。 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import IconPicker from '../../../components/base/IconPicker.vue'
import { GOAL_ICONS, GOAL_ICON_DEFAULT, iconOr } from '../../../config/icons'
import type { Goal, GoalSaveRequest, GoalSource, KeyResult } from '../../../types/goals'
import type { Habit } from '../../../types/habits'
import type { TaskList } from '../../../types/tasks'

const props = defineProps<{ open: boolean; goal: Goal | null; year: number; habits: Habit[]; lists: TaskList[] }>()
const emit = defineEmits<{ close: []; save: [body: GoalSaveRequest & { title: string; year: number }] }>()

const SOURCES: { key: GoalSource; label: string; hint: string }[] = [
  { key: 'habit', label: '习惯打卡', hint: '打卡一次折算多少' },
  { key: 'reading', label: '稍后读', hint: '当年读完的篇数' },
  { key: 'ledger', label: '记账结余', hint: '当年收入减支出' },
  { key: 'task_list', label: '任务清单', hint: '当年完成的任务数' },
  { key: 'manual', label: '手动记', hint: '自己点 +1 更新' },
]

const icon = ref(GOAL_ICON_DEFAULT)
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
/** 点过一次提交后才标红，打开就一片红太吓人 */
const submitted = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    submitted.value = false
    const g = props.goal
    icon.value = iconOr(g?.icon, GOAL_ICON_DEFAULT)
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

/** 第一条没填好的，提交时显示在按钮上方 */
const problem = computed(() => {
  if (!title.value.trim()) return '写一下目标是什么'
  if (kind.value === 'milestone') return krs.value.some((k) => k.title.trim()) ? '' : '至少写一条关键结果'
  if (!(Number(target.value) > 0)) return '目标值要大于 0'
  if (source.value === 'habit' && !sourceId.value) return '选一个习惯'
  if (source.value === 'task_list' && !sourceId.value) return '选一个任务清单'
  return ''
})

const submit = () => {
  submitted.value = true
  if (problem.value) return
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
  <BaseDialog :open="open" :title="goal ? '编辑目标' : `新的 ${year} 年目标`" width="34rem" @close="emit('close')">
    <form class="form gd" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="gd-title">目标 <span class="field__required">*</span></label>
        <input
          id="gd-title"
          v-model="title"
          class="field__input"
          :class="{ 'field__input--invalid': submitted && !title.trim() }"
          type="text"
          placeholder="例如：跑满 1000 公里"
          maxlength="40"
        />
      </div>

      <div class="field">
        <span class="field__label">图标</span>
        <IconPicker v-model="icon" :icons="GOAL_ICONS" aria-label="目标图标" />
      </div>

      <div class="field">
        <span class="field__label">怎么算完成</span>
        <div class="opts opts--2" role="radiogroup" aria-label="类型">
          <label :class="{ on: kind === 'metric' }"><input v-model="kind" type="radio" value="metric" /><b>数值</b><small>到一个数，进度自动取</small></label>
          <label :class="{ on: kind === 'milestone' }"><input v-model="kind" type="radio" value="milestone" /><b>关键结果</b><small>几件事，逐条勾选</small></label>
        </div>
      </div>

      <template v-if="kind === 'metric'">
        <div class="row2">
          <div class="field">
            <label class="field__label" for="gd-target">目标值 <span class="field__required">*</span></label>
            <input id="gd-target" v-model="target" class="field__input" :class="{ 'field__input--invalid': submitted && !(Number(target) > 0) }" type="number" min="1" />
          </div>
          <div class="field">
            <label class="field__label" for="gd-unit">单位</label>
            <input id="gd-unit" v-model="unit" class="field__input" type="text" placeholder="km、本、¥" maxlength="6" />
          </div>
        </div>

        <div class="field">
          <span class="field__label">进度从哪来</span>
          <div class="opts opts--3" role="radiogroup" aria-label="进度来源">
            <label v-for="s in SOURCES" :key="s.key" :class="{ on: source === s.key }">
              <input v-model="source" type="radio" :value="s.key" /><b>{{ s.label }}</b><small>{{ s.hint }}</small>
            </label>
          </div>
        </div>

        <div v-if="source === 'habit'" class="row2">
          <div class="field">
            <label class="field__label" for="gd-habit">哪个习惯</label>
            <select v-if="habits.length" id="gd-habit" v-model="sourceId" class="field__input">
              <option v-for="h in habits" :key="h.id" :value="String(h.id)">{{ h.name }}</option>
            </select>
            <span v-else class="field__hint">还没有习惯，先去「习惯」建一个</span>
          </div>
          <div class="field">
            <label class="field__label" for="gd-factor">{{ habit?.kind === 'check' ? '打卡一次算' : `每 1 ${habit?.unit || '单位'}算` }}</label>
            <input id="gd-factor" v-model="factor" class="field__input" type="number" min="0.1" step="0.1" />
          </div>
        </div>
        <div v-else-if="source === 'task_list'" class="field">
          <label class="field__label" for="gd-list">哪个清单</label>
          <select v-if="lists.length" id="gd-list" v-model="sourceId" class="field__input">
            <option v-for="l in lists" :key="l.id" :value="String(l.id)">{{ l.name }}</option>
          </select>
          <span v-else class="field__hint">还没有任务清单，先去「任务」建一个</span>
        </div>

        <div v-if="source === 'manual'" class="field">
          <label class="field__label" for="gd-manual">现在已经有</label>
          <input id="gd-manual" v-model="manualValue" class="field__input" type="number" min="0" placeholder="0" />
        </div>
        <div v-else class="field">
          <label class="field__label" for="gd-baseline">开始用 Space 之前已经有</label>
          <input id="gd-baseline" v-model="baseline" class="field__input" type="number" min="0" placeholder="0" />
          <span class="field__hint">会加在自动统计的数上</span>
        </div>
      </template>

      <div v-else class="field">
        <span class="field__label">关键结果 <span class="field__required">*</span></span>
        <div v-for="(k, i) in krs" :key="k.id" class="kr">
          <input v-model="k.title" class="field__input" type="text" :placeholder="`第 ${i + 1} 条，例如：上线某功能`" :aria-label="`关键结果 ${i + 1}`" />
          <select v-model="k.listId" class="field__input kr__list" :aria-label="`关键结果 ${i + 1} 关联清单`">
            <option :value="null">不关联清单</option>
            <option v-for="l in lists" :key="l.id" :value="l.id">清单：{{ l.name }}</option>
          </select>
          <button type="button" class="btn btn--quiet kr__x" :aria-label="`删掉第 ${i + 1} 条`" @click="removeKr(k.id)"><Icon icon="lucide:x" /></button>
        </div>
        <button type="button" class="btn btn--ghost kr__add" @click="addKr"><Icon icon="lucide:plus" />再加一条</button>
      </div>

      <p v-if="submitted && problem" class="form__error">{{ problem }}</p>

      <div class="form__actions">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="submit">{{ goal ? '保存' : '创建' }}</button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.gd {
  gap: 1rem;
}

.opts {
  display: grid;
  gap: 0.4rem;
}

.opts--2 {
  grid-template-columns: repeat(2, 1fr);
}

.opts--3 {
  grid-template-columns: repeat(3, 1fr);
}

.opts label {
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.opts label.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.opts input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.opts b {
  font-size: 0.9rem;
}

.opts small {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.8rem;
}

.kr {
  display: flex;
  gap: 0.4rem;
}

.kr__list {
  width: 9.5rem;
  flex: none;
}

.kr__x {
  flex: none;
  padding: 0.35rem;
}

.kr__add {
  align-self: flex-start;
  padding: 0.3rem 0.7rem;
  font-size: 0.84rem;
}

@media (max-width: 560px) {
  .opts--3 {
    grid-template-columns: repeat(2, 1fr);
  }

  .kr {
    flex-wrap: wrap;
  }

  .kr__list {
    width: auto;
    flex: 1;
  }
}
</style>
