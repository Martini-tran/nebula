<script setup lang="ts">
/** 新会议：主题、时间、时长、模板（带议程结构）、参会人（逗号或空格分隔，「我」自动加入） */
import { computed, nextTick, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { createMeeting } from '../../../api/meetings'
import { errorText } from '../../../composables/useToast'
import { todayYmd } from '../../../utils/date'
import { MEETING_TEMPLATES, type Meeting } from '../../../types/meetings'

const props = defineProps<{ open: boolean; template?: string | null; date?: string | null }>()
const emit = defineEmits<{ close: []; created: [meeting: Meeting] }>()

const title = ref('')
const date = ref(todayYmd())
const startTime = ref('10:00')
const duration = ref(30)
const template = ref('')
const people = ref('')
const saving = ref(false)
const error = ref('')
const submitted = ref(false)
const titleInput = ref<HTMLInputElement | null>(null)

/** 默认开始时间：下一个整点或半点 */
const nextSlot = () => {
  const d = new Date(Date.now() + 30 * 60_000)
  const minute = d.getMinutes() < 30 ? '00' : '30'
  return `${String(d.getHours()).padStart(2, '0')}:${minute}`
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    const tpl = MEETING_TEMPLATES.find((t) => t.key === props.template)
    title.value = tpl?.name ?? ''
    date.value = props.date ?? todayYmd()
    startTime.value = nextSlot()
    template.value = tpl?.key ?? ''
    duration.value = tpl?.durationMin ?? 30
    people.value = ''
    error.value = ''
    submitted.value = false
    await nextTick()
    titleInput.value?.focus()
    titleInput.value?.select()
  },
)

const selectedTemplate = computed(() => MEETING_TEMPLATES.find((t) => t.key === template.value))
watch(template, () => {
  if (selectedTemplate.value) duration.value = selectedTemplate.value.durationMin
})

const submit = async () => {
  submitted.value = true
  if (!title.value.trim() || saving.value) return
  saving.value = true
  error.value = ''
  const names = [...new Set(people.value.split(/[,，、\s]+/).map((n) => n.trim()).filter((n) => n && n !== '我'))]
  try {
    const meeting = await createMeeting({
      title: title.value.trim(),
      date: date.value,
      startTime: startTime.value,
      durationMin: duration.value,
      template: template.value || null,
      attendees: [{ name: '我', me: true }, ...names.map((name) => ({ name }))],
      agenda: (selectedTemplate.value?.agenda ?? []).map((a, i) => ({ id: `a${i + 1}`, title: a.title, budgetMin: a.budgetMin, usedSec: 0 })),
    })
    emit('created', meeting)
  } catch (err) {
    error.value = errorText(err, '创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseDialog :open="open" title="新会议" width="30rem" :locked="saving" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="mt-title">主题 <span class="field__required">*</span></label>
        <input id="mt-title" ref="titleInput" v-model="title" class="field__input" :class="{ 'field__input--invalid': submitted && !title.trim() }" maxlength="100" />
        <p v-if="submitted && !title.trim()" class="field__error">请填写会议主题</p>
      </div>
      <div class="row">
        <div class="field">
          <label class="field__label" for="mt-date">日期</label>
          <input id="mt-date" v-model="date" class="field__input" type="date" required />
        </div>
        <div class="field">
          <label class="field__label" for="mt-time">开始</label>
          <input id="mt-time" v-model="startTime" class="field__input" type="time" required />
        </div>
        <div class="field">
          <label class="field__label" for="mt-dur">时长</label>
          <select id="mt-dur" v-model.number="duration" class="field__input">
            <option v-for="m in [15, 30, 45, 60, 90, 120]" :key="m" :value="m">{{ m }} 分钟</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label class="field__label" for="mt-tpl">模板</label>
        <select id="mt-tpl" v-model="template" class="field__input">
          <option value="">不用模板</option>
          <option v-for="t in MEETING_TEMPLATES" :key="t.key" :value="t.key">{{ t.name }}</option>
        </select>
        <p v-if="selectedTemplate" class="field__hint">议程：{{ selectedTemplate.agenda.map((a) => `${a.title} ${a.budgetMin} 分钟`).join(' · ') }}</p>
      </div>
      <div class="field">
        <label class="field__label" for="mt-people">参会人</label>
        <input id="mt-people" v-model="people" class="field__input" placeholder="陈工、张工（你自己会自动加入）" />
      </div>
      <p v-if="error" class="form__error">{{ error }}</p>
      <div class="form__actions">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="submit" :disabled="saving">
          <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />创建
        </button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 0.6rem;
}

@media (max-width: 520px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
