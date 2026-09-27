<script setup lang="ts">
/** 今天的会议时间线：红线标出「现在」，进行中的会议高亮，一键进入记录或准备议程 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Icon } from '@iconify/vue'
import { endTime, summaryOf } from '../../meetings/meetingInfo'
import { useMyNames } from '../../../composables/useMeetingSync'
import type { Meeting } from '../../../types/meetings'

const props = defineProps<{ meetings: Meeting[] }>()

const myNames = useMyNames()
const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => (timer = setInterval(() => (now.value = new Date()), 30_000)))
onBeforeUnmount(() => clearInterval(timer))

const nowHm = computed(() => `${String(now.value.getHours()).padStart(2, '0')}:${String(now.value.getMinutes()).padStart(2, '0')}`)

/** 会议与「现在」红线按时间排在一起 */
const rows = computed(() => {
  const list: ({ kind: 'meeting'; meeting: Meeting; state: 'past' | 'now' | 'next' } | { kind: 'now' })[] = []
  let placed = false
  for (const m of [...props.meetings].sort((a, b) => a.startTime.localeCompare(b.startTime))) {
    const end = endTime(m)
    const running = m.status === 'live' || (m.startTime <= nowHm.value && end > nowHm.value && m.status !== 'done')
    if (!placed && m.startTime > nowHm.value) {
      list.push({ kind: 'now' })
      placed = true
    }
    list.push({ kind: 'meeting', meeting: m, state: running ? 'now' : end <= nowHm.value || m.status === 'done' ? 'past' : 'next' })
  }
  if (!placed) list.push({ kind: 'now' })
  return list
})
</script>

<template>
  <ol class="tl">
    <template v-for="(row, i) in rows" :key="row.kind === 'now' ? 'now' : row.meeting.id">
      <li v-if="row.kind === 'now'" class="tl__now" :class="{ 'tl__now--last': i === rows.length - 1 }">
        <span>现在 {{ nowHm }}</span>
      </li>
      <li v-else class="tl__item" :class="`tl__item--${row.state}`">
        <span class="tl__time">{{ row.meeting.startTime }}</span>
        <router-link :to="{ name: 'meeting', params: { id: String(row.meeting.id) } }" class="tl__card">
          <b>{{ row.meeting.title }}</b>
          <small>
            <span class="faces"><i v-for="a in row.meeting.attendees.slice(0, 4)" :key="a.name" :class="{ me: a.me }">{{ a.name.charAt(0) }}</i></span>
            {{ row.meeting.status === 'done' ? `已记录 · ${summaryOf(row.meeting, myNames)}` : summaryOf(row.meeting, myNames) }}
          </small>
          <span v-if="row.meeting.status === 'live' || row.state === 'now'" class="tl__go tl__go--live"><Icon icon="lucide:mic" />进入记录</span>
          <span v-else-if="row.state === 'next'" class="tl__go">准备议程</span>
        </router-link>
      </li>
    </template>
  </ol>
</template>

<style scoped>
.tl {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.tl__item {
  display: flex;
  gap: 0.75rem;
}

.tl__time {
  flex: none;
  width: 2.8rem;
  padding-top: 0.55rem;
  font-size: 0.82rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.tl__card {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-brand);
  border-radius: var(--radius-md);
}

.tl__card:hover {
  border-color: var(--color-brand);
}

.tl__item--past .tl__card {
  border-left-color: var(--color-border);
  opacity: 0.75;
}

.tl__item--now .tl__card {
  border-left-color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 6%, transparent);
}

.tl__card b {
  font-size: 0.9rem;
  padding-right: 5rem;
}

.tl__card small {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.faces {
  display: inline-flex;
}

.faces i {
  display: grid;
  place-items: center;
  width: 1.15rem;
  height: 1.15rem;
  margin-right: -0.25rem;
  border: 2px solid var(--color-bg-surface);
  border-radius: 50%;
  background: var(--color-bg-soft);
  font-size: 0.56rem;
  font-style: normal;
  font-weight: 700;
}

.faces i.me {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.tl__go {
  position: absolute;
  top: 0.5rem;
  right: 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-brand);
}

.tl__go--live {
  color: var(--color-danger);
}

.tl__now {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--color-danger);
}

.tl__now::before,
.tl__now::after {
  content: '';
  height: 2px;
  background: var(--color-danger);
}

.tl__now::before {
  width: 0.5rem;
}

.tl__now::after {
  flex: 1;
}
</style>
