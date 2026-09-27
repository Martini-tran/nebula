<script setup lang="ts">
/** 添加 / 编辑人物：姓名、称呼、其他叫法、分组、生日、联系频率，和几条手填的信息。 */
import { computed, ref, watch } from 'vue'
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

const name = ref('')
const alias = ref('')
const extra = ref('')
const group = ref('同事')
const color = ref(COLORS[0]!)
const birthday = ref('')
const contactEvery = ref<number | null>(null)
const intro = ref('')
const facts = ref<PersonFact[]>([])

watch(
  () => props.open,
  (open) => {
    if (!open) return
    const p = props.person
    name.value = p?.name ?? ''
    alias.value = p?.alias ?? ''
    extra.value = p?.extraNames.join('、') ?? ''
    group.value = p?.group ?? '同事'
    color.value = p?.color ?? COLORS[Math.floor(Math.random() * COLORS.length)]!
    birthday.value = p?.birthday ? `2000-${p.birthday}` : ''
    contactEvery.value = p?.contactEvery ?? null
    intro.value = p?.intro ?? ''
    facts.value = p ? (JSON.parse(JSON.stringify(p.facts)) as PersonFact[]) : [{ label: '', value: '' }]
  },
)

const groupOptions = computed(() => [...new Set([...PERSON_GROUPS, ...props.groups])])
const valid = computed(() => Boolean(name.value.trim()))

const submit = () => {
  if (!valid.value) return
  emit('save', {
    name: name.value.trim(),
    alias: alias.value.trim(),
    extraNames: extra.value.split(/[、,，\s]+/).map((s) => s.trim()).filter(Boolean),
    group: group.value.trim() || '同事',
    color: color.value,
    birthday: birthday.value ? birthday.value.slice(5) : null,
    contactEvery: contactEvery.value,
    intro: intro.value.trim(),
    facts: facts.value.filter((f) => f.label.trim() && f.value.trim()).map((f) => ({ label: f.label.trim(), value: f.value.trim() })),
  })
}
</script>

<template>
  <BaseDialog :open="open" :title="person ? `编辑「${person.alias || person.name}」` : '添加人物'" width="32rem" @close="emit('close')">
    <form class="form pd" @submit.prevent="submit">
      <div class="row2">
        <label class="field">
          <span class="field__label">姓名</span>
          <input v-model="name" class="field__input" type="text" maxlength="20" placeholder="张立" />
        </label>
        <label class="field">
          <span class="field__label">平时怎么称呼</span>
          <input v-model="alias" class="field__input" type="text" maxlength="20" placeholder="张工" />
        </label>
      </div>
      <label class="field">
        <span class="field__label">其他叫法 <small>会议、随手记里写成这些也算他；至少两个字</small></span>
        <input v-model="extra" class="field__input" type="text" placeholder="用顿号分隔，例如：王姐、蕾姐" />
      </label>
      <div class="row2">
        <label class="field">
          <span class="field__label">分组</span>
          <input v-model="group" class="field__input" list="person-groups" type="text" maxlength="10" />
          <datalist id="person-groups"><option v-for="g in groupOptions" :key="g" :value="g" /></datalist>
        </label>
        <label class="field">
          <span class="field__label">生日</span>
          <input v-model="birthday" class="field__input" type="date" />
        </label>
      </div>
      <div class="field">
        <span class="field__label">多久没联系就提醒</span>
        <div class="chips">
          <button v-for="e in EVERY" :key="String(e.value)" type="button" :class="{ on: contactEvery === e.value }" @click="contactEvery = e.value">{{ e.label }}</button>
        </div>
      </div>
      <label class="field">
        <span class="field__label">一句话</span>
        <input v-model="intro" class="field__input" type="text" maxlength="40" placeholder="后端 / 基础设施 · 认识于 2024 年 3 月" />
      </label>
      <div class="field">
        <span class="field__label">信息</span>
        <div v-for="(f, i) in facts" :key="i" class="fact">
          <input v-model="f.label" class="field__input fact__k" type="text" placeholder="偏好" maxlength="8" :aria-label="`第 ${i + 1} 条信息的名称`" />
          <input v-model="f.value" class="field__input" type="text" placeholder="喝美式不加糖" maxlength="60" :aria-label="`第 ${i + 1} 条信息的内容`" />
          <button type="button" class="btn btn--quiet fact__x" :aria-label="`删掉第 ${i + 1} 条`" @click="facts.splice(i, 1)"><Icon icon="lucide:x" /></button>
        </div>
        <button type="button" class="btn btn--ghost btn--sm add" @click="facts.push({ label: '', value: '' })"><Icon icon="lucide:plus" />加一条</button>
      </div>
      <div class="field">
        <span class="field__label">颜色</span>
        <div class="colors">
          <button v-for="c in COLORS" :key="c" type="button" :class="{ on: color === c }" :style="{ background: c }" :aria-label="`颜色 ${c}`" @click="color = c" />
        </div>
      </div>
    </form>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
      <button class="btn btn--primary" type="button" :disabled="!valid" @click="submit">{{ person ? '保存' : '添加' }}</button>
    </template>
  </BaseDialog>
</template>

<style scoped>
.pd {
  gap: 0.9rem;
  padding: 0;
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
  padding: 0.22rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.8rem;
  cursor: pointer;
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.fact {
  display: flex;
  gap: 0.4rem;
}

.fact__k {
  width: 6rem;
  flex: none;
}

.fact__x {
  padding: 0.3rem;
}

.btn--sm {
  padding: 0.28rem 0.6rem;
  font-size: 0.8rem;
}

.add {
  align-self: flex-start;
}

.colors {
  display: flex;
  gap: 0.4rem;
}

.colors button {
  width: 1.5rem;
  height: 1.5rem;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.colors button.on {
  outline: 2px solid var(--color-text-primary);
  outline-offset: 2px;
}
</style>
