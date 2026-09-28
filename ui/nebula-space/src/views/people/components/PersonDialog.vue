<script setup lang="ts">
/**
 * 添加 / 编辑人物：头像颜色、姓名、称呼、其他叫法；分组、生日（只记月日）、联系频率；一句话介绍和几条手填的信息。
 * 姓名必填，提交时才标红；生日月、日要么都选要么都不选。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { PERSON_GROUPS, type Person, type PersonFact, type PersonSaveRequest } from '../../../types/people'

const props = defineProps<{ open: boolean; person: Person | null; groups: string[] }>()
const emit = defineEmits<{ close: []; save: [body: PersonSaveRequest & { name: string }] }>()

const COLORS = ['#0d9488', '#2563eb', '#db2777', '#d97706', '#7c3aed', '#65a30d', '#dc2626', '#4f46e5']
const EVERY: { value: number | null; label: string }[] = [
  { value: null, label: '不提醒' },
  { value: 7, label: '每周' },
  { value: 14, label: '两周' },
  { value: 30, label: '每月' },
  { value: 60, label: '两个月' },
  { value: 90, label: '三个月' },
]
/** 每月最多几天；2 月按 29 天，闰年出生的也能记 */
const DAYS_IN = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

const name = ref('')
const alias = ref('')
const extra = ref('')
const group = ref('同事')
const customGroup = ref(false)
const color = ref(COLORS[0]!)
const bMonth = ref<number | ''>('')
const bDay = ref<number | ''>('')
const contactEvery = ref<number | null>(null)
const intro = ref('')
const facts = ref<PersonFact[]>([])
const submitted = ref(false)
const nameInput = ref<HTMLInputElement | null>(null)
const groupInput = ref<HTMLInputElement | null>(null)

const groupOptions = computed(() => [...new Set([...PERSON_GROUPS, ...props.groups])])

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    const p = props.person
    name.value = p?.name ?? ''
    alias.value = p?.alias ?? ''
    extra.value = p?.extraNames.join('、') ?? ''
    group.value = p?.group ?? '同事'
    customGroup.value = !groupOptions.value.includes(group.value)
    color.value = p?.color ?? COLORS[Math.floor(Math.random() * COLORS.length)]!
    bMonth.value = p?.birthday ? Number(p.birthday.slice(0, 2)) : ''
    bDay.value = p?.birthday ? Number(p.birthday.slice(3, 5)) : ''
    contactEvery.value = p?.contactEvery ?? null
    intro.value = p?.intro ?? ''
    facts.value = p ? (JSON.parse(JSON.stringify(p.facts)) as PersonFact[]) : []
    submitted.value = false
    await nextTick()
    if (!p) nameInput.value?.focus()
  },
)

// ── 分组：常用的点选，也可以新起一个 ──

const pickGroup = (g: string) => {
  group.value = g
  customGroup.value = false
}
const startCustomGroup = async () => {
  customGroup.value = true
  group.value = ''
  await nextTick()
  groupInput.value?.focus()
}

// ── 生日：只记月日 ──

const days = computed(() => (bMonth.value === '' ? 31 : DAYS_IN[bMonth.value - 1]!))
watch(bMonth, (m) => {
  if (m === '') bDay.value = ''
  else if (bDay.value !== '' && bDay.value > days.value) bDay.value = days.value
})
const pad = (n: number) => String(n).padStart(2, '0')

// ── 其他叫法：一个字的容易认错人，不存 ──

const extraList = computed(() => extra.value.split(/[、,，\s]+/).map((s) => s.trim()).filter(Boolean))
const shortNames = computed(() => extraList.value.filter((s) => s.length < 2))

const initial = computed(() => (alias.value.trim() || name.value.trim()).charAt(0))

const problem = computed(() => {
  if (!name.value.trim()) return '写一下姓名'
  if (bMonth.value !== '' && bDay.value === '') return '生日还差选哪一天'
  return ''
})

const submit = () => {
  submitted.value = true
  if (problem.value) {
    if (!name.value.trim()) nameInput.value?.focus()
    return
  }
  emit('save', {
    name: name.value.trim(),
    alias: alias.value.trim(),
    extraNames: extraList.value.filter((s) => s.length >= 2),
    group: group.value.trim() || '同事',
    color: color.value,
    birthday: bMonth.value !== '' && bDay.value !== '' ? `${pad(bMonth.value)}-${pad(bDay.value)}` : null,
    contactEvery: contactEvery.value,
    intro: intro.value.trim(),
    facts: facts.value.filter((f) => f.label.trim() && f.value.trim()).map((f) => ({ label: f.label.trim(), value: f.value.trim() })),
  })
}
</script>

<template>
  <BaseDialog :open="open" :title="person ? `编辑「${person.alias || person.name}」` : '添加人物'" width="34rem" @close="emit('close')">
    <form class="form pd" novalidate @submit.prevent="submit">
      <div class="who">
        <span class="who__av" :style="{ background: color }" aria-hidden="true">
          <template v-if="initial">{{ initial }}</template>
          <Icon v-else icon="lucide:user" />
        </span>
        <div class="who__main">
          <div class="row2">
            <div class="field">
              <label class="field__label" for="pd-name">姓名 <span class="field__required">*</span></label>
              <input
                id="pd-name"
                ref="nameInput"
                v-model="name"
                class="field__input"
                :class="{ 'field__input--invalid': submitted && !name.trim() }"
                type="text"
                maxlength="20"
                placeholder="张立"
                :aria-invalid="submitted && !name.trim()"
              />
            </div>
            <div class="field">
              <label class="field__label" for="pd-alias">平时怎么称呼</label>
              <input id="pd-alias" v-model="alias" class="field__input" type="text" maxlength="20" placeholder="张工" />
            </div>
          </div>
          <div class="colors" role="radiogroup" aria-label="头像颜色">
            <button
              v-for="c in COLORS"
              :key="c"
              type="button"
              role="radio"
              :aria-checked="color === c"
              :aria-label="`颜色 ${c}`"
              :class="{ on: color === c }"
              :style="{ background: c }"
              @click="color = c"
            />
          </div>
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="pd-extra">其他叫法</label>
        <input id="pd-extra" v-model="extra" class="field__input" type="text" placeholder="王姐、蕾姐" />
        <span v-if="shortNames.length" class="field__error">「{{ shortNames.join('、') }}」只有一个字，容易认错人，不会保存</span>
        <span v-else class="field__hint">会议、随手记里写成这些也算他，用顿号分隔</span>
      </div>

      <div class="field">
        <span id="pd-group" class="field__label">分组</span>
        <div class="chips" role="radiogroup" aria-labelledby="pd-group">
          <button
            v-for="g in groupOptions"
            :key="g"
            type="button"
            role="radio"
            :aria-checked="!customGroup && group === g"
            :class="{ on: !customGroup && group === g }"
            @click="pickGroup(g)"
          >
            {{ g }}
          </button>
          <input v-if="customGroup" ref="groupInput" v-model="group" class="chips__input" type="text" maxlength="10" placeholder="新分组名" aria-label="新分组名" />
          <button v-else type="button" class="chips__add" @click="startCustomGroup"><Icon icon="lucide:plus" />新分组</button>
        </div>
      </div>

      <div class="row2">
        <div class="field">
          <label class="field__label" for="pd-bmonth">生日 <span class="field__note">只记月日</span></label>
          <div class="bday">
            <select id="pd-bmonth" v-model="bMonth" class="field__input" aria-label="生日月份">
              <option value="">不记</option>
              <option v-for="m in 12" :key="m" :value="m">{{ m }} 月</option>
            </select>
            <select
              v-model="bDay"
              class="field__input"
              :class="{ 'field__input--invalid': submitted && bMonth !== '' && bDay === '' }"
              :disabled="bMonth === ''"
              aria-label="生日日期"
            >
              <option value="">哪天</option>
              <option v-for="d in days" :key="d" :value="d">{{ d }} 日</option>
            </select>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="pd-every">多久没联系就提醒</label>
          <select id="pd-every" v-model="contactEvery" class="field__input">
            <option v-for="e in EVERY" :key="String(e.value)" :value="e.value">{{ e.label }}</option>
          </select>
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="pd-intro">一句话介绍</label>
        <input id="pd-intro" v-model="intro" class="field__input" type="text" maxlength="40" placeholder="后端 / 基础设施 · 认识于 2024 年 3 月" />
      </div>

      <div class="field">
        <div class="facts__head">
          <span class="field__label">信息</span>
          <button type="button" class="btn btn--quiet facts__add" @click="facts.push({ label: '', value: '' })"><Icon icon="lucide:plus" />加一条</button>
        </div>
        <p v-if="!facts.length" class="field__hint">团队、偏好、家里人……记几条下次见面用得上的。</p>
        <div v-for="(f, i) in facts" :key="i" class="fact">
          <input v-model="f.label" class="field__input fact__k" type="text" placeholder="偏好" maxlength="8" :aria-label="`第 ${i + 1} 条信息的名称`" />
          <input v-model="f.value" class="field__input" type="text" placeholder="喝美式不加糖" maxlength="60" :aria-label="`第 ${i + 1} 条信息的内容`" />
          <button type="button" class="btn btn--quiet fact__x" :aria-label="`删掉第 ${i + 1} 条`" @click="facts.splice(i, 1)"><Icon icon="lucide:x" /></button>
        </div>
      </div>

      <p v-if="submitted && problem" class="form__error" role="alert">{{ problem }}</p>

      <div class="form__actions">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="submit">{{ person ? '保存' : '添加' }}</button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.pd {
  gap: 1.1rem;
}

/* 信息多了弹窗会滚动：按钮贴在底部，不用滚到最后才能保存 */
.pd .form__actions {
  position: sticky;
  bottom: -1.35rem;
  margin: 0 -1.35rem -1.35rem;
  padding: 0.85rem 1.35rem 1.1rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.who {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.who__av {
  display: grid;
  flex: none;
  place-items: center;
  width: 3.4rem;
  height: 3.4rem;
  margin-top: 1.55rem;
  border-radius: 50%;
  color: #fff;
  font-size: 1.35rem;
  font-weight: 800;
  transition: background 0.2s ease;
}

.who__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.65rem;
  min-width: 0;
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.8rem;
}

.colors {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.colors button {
  width: 1.35rem;
  height: 1.35rem;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.colors button:hover {
  transform: scale(1.12);
}

.colors button.on {
  outline: 2px solid var(--color-text-primary);
  outline-offset: 2px;
}

.colors button:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.field__note {
  margin-left: 0.35rem;
  font-size: 0.78rem;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.chips button,
.chips__input {
  height: 2rem;
  padding: 0 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-size: 0.84rem;
}

.chips button {
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.chips button:hover {
  border-color: color-mix(in srgb, var(--color-brand) 45%, var(--color-border));
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.chips .chips__add {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border-style: dashed;
  color: var(--color-text-secondary);
}

.chips__input {
  width: 7.5rem;
  border-color: var(--color-brand);
  outline: none;
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.bday {
  display: flex;
  gap: 0.5rem;
}

.bday select {
  flex: 1;
  min-width: 0;
}

.bday select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.facts__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.facts__add {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.5rem;
  font-size: 0.82rem;
}

.fact {
  display: flex;
  gap: 0.45rem;
}

.fact__k {
  width: 6.5rem;
  flex: none;
}

.fact__x {
  flex: none;
  padding: 0.35rem 0.45rem;
}

@media (max-width: 560px) {
  .who__av {
    width: 2.6rem;
    height: 2.6rem;
    font-size: 1.05rem;
  }

  .row2 {
    grid-template-columns: 1fr;
  }
}
</style>
