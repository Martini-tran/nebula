<script setup lang="ts">
/**
 * 人物卡：会议里反复出现的「张工」、随手记里的「王姐」——把这些提及收拢到一个人名下：
 * 和他开过哪些会、谁欠谁什么、上次聊了什么、生日是哪天。只是自己的备忘，对方看不到。
 * 左栏按最近互动排序，太久没联系的标黄「该联系了」；右侧：互相的承诺、往来时间线、下次可以聊、信息、备忘。?id=
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import BaseDialog from '../../components/base/BaseDialog.vue'
import PersonDialog from './components/PersonDialog.vue'
import { createPerson, deletePerson, fetchPeople, updatePerson } from '../../api/people'
import { createMeeting, fetchMeetings, updateMeeting } from '../../api/meetings'
import { fetchNotes } from '../../api/notes'
import { fetchTasks } from '../../api/tasks'
import { useMyNames } from '../../composables/useMeetingSync'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { addDays, diffDays, monthDay, nextWeekday, nowStamp, relativeDay, todayYmd } from '../../utils/date'
import { birthdayIn, commitmentsOf, dueText, lastInteraction, namesOf, timelineOf, type Commitment, type PeopleSource } from './personData'
import { MEETING_TEMPLATES } from '../../types/meetings'
import type { Person, PersonSaveRequest } from '../../types/people'

const route = useRoute()
const router = useRouter()
const myNames = useMyNames()
const today = todayYmd()

const people = ref<Person[]>([])
const src = ref<PeopleSource>({ meetings: [], notes: [], tasks: [], myNames })
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    const [p, meetings, notes, archived, tasks] = await Promise.all([
      fetchPeople(),
      fetchMeetings().catch(() => []),
      fetchNotes({ view: 'all' }).catch(() => []),
      fetchNotes({ view: 'archived' }).catch(() => []),
      fetchTasks({ view: 'all' }).catch(() => []),
    ])
    people.value = p
    src.value = { meetings, notes: [...notes, ...archived], tasks, myNames }
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

// ── 列表 ──

const keyword = ref('')
const group = ref('')

interface Row {
  p: Person
  last: string | null
  idle: number | null
  stale: boolean
  sub: string
}

const rows = computed<Row[]>(() =>
  people.value.map((p) => {
    const last = lastInteraction(p, src.value, today)
    const idle = last ? diffDays(last, today) : null
    const stale = p.contactEvery !== null && (idle === null || idle > p.contactEvery)
    const { theirs } = commitmentsOf(p, src.value)
    const bday = birthdayIn(p, today)
    const recentMeetings = src.value.meetings
      .filter((m) => m.date === last && m.attendees.some((a) => !a.me && namesOf(p).includes(a.name)))
      .map((m) => m.title)
    const parts = [
      last ? (idle === 0 ? '今天' : relativeDay(last)) : '还没有往来',
      bday !== null && bday <= 30 ? `生日还有 ${bday} 天` : '',
      recentMeetings.length ? recentMeetings.slice(0, 2).join('、') : '',
      theirs.length ? `欠我 ${theirs.length} 项` : '',
      !last && p.intro ? p.intro : '',
    ]
    return { p, last, idle, stale, sub: parts.filter(Boolean).join(' · ') }
  }),
)

const groups = computed(() => {
  const map = new Map<string, number>()
  people.value.forEach((p) => map.set(p.group, (map.get(p.group) ?? 0) + 1))
  return [...map.entries()]
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value
    .filter((r) => !group.value || r.p.group === group.value)
    .filter((r) => !kw || `${r.p.name} ${r.p.alias} ${r.p.extraNames.join(' ')} ${r.p.intro} ${r.p.memo} ${r.p.facts.map((f) => f.value).join(' ')}`.toLowerCase().includes(kw))
})
const byRecent = (a: Row, b: Row) => String(b.last ?? '').localeCompare(String(a.last ?? ''))
const recent = computed(() => filtered.value.filter((r) => !r.stale).sort(byRecent))
const stale = computed(() => filtered.value.filter((r) => r.stale).sort((a, b) => (b.idle ?? 999) - (a.idle ?? 999)))

const selectedId = computed(() => (typeof route.query.id === 'string' ? route.query.id : null))
const selected = computed(() => rows.value.find((r) => String(r.p.id) === selectedId.value) ?? recent.value[0] ?? stale.value[0] ?? null)
const select = (p: Person) => router.replace({ query: { id: String(p.id) } })

const label = (p: Person) => (p.alias && p.alias !== p.name ? `${p.alias} · ${p.name}` : p.name)
const initial = (p: Person) => (p.alias || p.name).charAt(0)

// ── 当前人物 ──

const person = computed(() => selected.value?.p ?? null)
const timeline = computed(() => (person.value ? timelineOf(person.value, src.value, today) : []))
const commits = computed(() => (person.value ? commitmentsOf(person.value, src.value) : { theirs: [], mine: [] }))
const upcoming = computed(() => timeline.value.find((e) => e.kind === 'upcoming'))
const upcomingMeeting = computed(() => (upcoming.value ? src.value.meetings.find((m) => `/meetings/${m.id}` === upcoming.value!.to) : undefined))
const SHOW_EVENTS = 8
const allEvents = ref(false)
watch(selectedId, () => {
  allEvents.value = false
  addingPromise.value = null
})

/** 下次见面可以聊：他答应还没给的、我还欠着的；议程里只写事项本身 */
const talkAbout = computed(() => [
  ...commits.value.theirs.map((c) => ({ text: c.text, note: `他答应的${c.due && c.due < today ? `，已过 ${diffDays(c.due, today)} 天` : ''}` })),
  ...commits.value.mine.map((c) => ({ text: c.text, note: `我答应的${c.due ? `，${dueText(c.due, today)}` : ''}` })),
])

const patch = async (p: Person, body: PersonSaveRequest) => {
  try {
    const updated = await updatePerson(p.id, body)
    people.value = people.value.map((x) => (x.id === p.id ? updated : x))
    return updated
  } catch (error) {
    toast.error(errorText(error, '没存上'))
    return null
  }
}

// 备忘：离开输入框就存
const memo = ref('')
watch(person, (p) => (memo.value = p?.memo ?? ''), { immediate: true })
const saveMemo = () => {
  if (person.value && memo.value.trim() !== person.value.memo) patch(person.value, { memo: memo.value.trim() })
}

// 承诺
const addingPromise = ref<{ who: 'me' | 'them'; text: string; due: string } | null>(null)
const startPromise = (who: 'me' | 'them') => (addingPromise.value = { who, text: '', due: '' })
const savePromise = async () => {
  const p = person.value
  const d = addingPromise.value
  if (!p || !d || !d.text.trim()) return
  const promises = [...p.promises, { id: `pr${Date.now()}`, who: d.who, text: d.text.trim(), due: d.due || null, done: false, createTime: nowStamp() }]
  if (await patch(p, { promises })) addingPromise.value = null
}
const finishPromise = async (c: Commitment) => {
  const p = person.value
  if (!p || !c.promiseId) return
  const promises = p.promises.map((x) => (x.id === c.promiseId ? { ...x, done: true } : x))
  if (await patch(p, { promises })) {
    toast.ok(`「${c.text}」已了结`, {
      action: { label: '撤销', run: () => patch(p, { promises: p.promises }) },
    })
  }
}

// 联系过
const contactOpen = ref(false)
const contactDate = ref(today)
const contactNote = ref('')
const openContact = () => {
  contactDate.value = today
  contactNote.value = ''
  contactOpen.value = true
}
const saveContact = async () => {
  const p = person.value
  if (!p) return
  const contacts = [{ date: contactDate.value, note: contactNote.value.trim() || '联系了一次' }, ...p.contacts]
  if (await patch(p, { contacts })) {
    contactOpen.value = false
    toast.ok(`记下了和${p.alias || p.name}的联系`)
  }
}

// 约 1:1：建一场会，议程带上要跟进的事
const scheduling = ref(false)
const scheduleOneOnOne = async () => {
  const p = person.value
  if (!p || scheduling.value) return
  scheduling.value = true
  try {
    const tpl = MEETING_TEMPLATES.find((t) => t.key === 'one-on-one')!
    const follow = talkAbout.value.slice(0, 3).map((t, i) => ({ id: `f${i}`, title: `跟进：${t.text}`, budgetMin: 5, usedSec: 0 }))
    const date = new Date().getHours() < 16 ? addDays(today, 1) : nextWeekday(1)
    const meeting = await createMeeting({
      title: `1:1 · ${p.alias || p.name}`,
      date,
      startTime: '10:00',
      durationMin: tpl.durationMin + follow.length * 5,
      template: tpl.key,
      attendees: [{ name: '我', me: true }, { name: p.alias || p.name }],
      agenda: [...tpl.agenda.map((a, i) => ({ id: `a${i}`, title: a.title, budgetMin: a.budgetMin, usedSec: 0 })), ...follow],
    })
    toast.ok(`已约 ${relativeDay(meeting.date)} 10:00，可以在会议里改时间`)
    router.push(`/meetings/${meeting.id}`)
  } catch (error) {
    toast.error(errorText(error, '没约上'))
  } finally {
    scheduling.value = false
  }
}

const addToAgenda = async () => {
  const m = upcomingMeeting.value
  if (!m) return
  const titles = new Set(m.agenda.map((a) => a.title))
  const add = talkAbout.value.map((t) => `跟进：${t.text}`).filter((t) => !titles.has(t))
  if (!add.length) {
    toast.info('这些已经在议程里了')
    return
  }
  try {
    await updateMeeting(m.id, { agenda: [...m.agenda, ...add.map((title, i) => ({ id: `f${Date.now()}${i}`, title, budgetMin: 5, usedSec: 0 }))] })
    toast.ok(`已加进「${m.title}」（${relativeDay(m.date)}）的议程`, { action: { label: '打开', run: () => router.push(`/meetings/${m.id}`) } })
    load()
  } catch (error) {
    toast.error(errorText(error, '没加上'))
  }
}

// 编辑 / 删除
const dialogOpen = ref(false)
const editing = ref<Person | null>(null)
const openDialog = (p: Person | null) => {
  editing.value = p
  dialogOpen.value = true
}
const savePerson = async (body: PersonSaveRequest & { name: string }) => {
  try {
    if (editing.value) {
      await patch(editing.value, body)
    } else {
      const p = await createPerson(body)
      people.value = [...people.value, p]
      select(p)
    }
    dialogOpen.value = false
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}
const remove = async (p: Person) => {
  const ok = await confirm({ title: `删除「${label(p)}」的人物卡？`, message: '只删这张卡片，会议和随手记里的内容不受影响。', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await deletePerson(p.id)
    people.value = people.value.filter((x) => x.id !== p.id)
    router.replace({ query: {} })
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const EV_ICON: Record<string, string> = { meeting: 'lucide:users', note: 'lucide:pencil-line', task: 'lucide:check', contact: 'lucide:phone', upcoming: 'lucide:calendar-clock' }
const evDate = (d: string) => (d === today ? '今天' : diffDays(d, today) === 1 ? '昨天' : `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`)

onMounted(load)
</script>

<template>
  <div class="people page">
    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <div v-else class="pl">
      <aside class="plist surface">
        <div class="plist__top">
          <h1>人物 <button class="btn btn--ghost btn--sm" type="button" @click="openDialog(null)"><Icon icon="lucide:plus" />添加</button></h1>
          <label class="search">
            <Icon icon="lucide:search" />
            <input v-model="keyword" type="text" placeholder="搜索姓名、备注、信息" aria-label="搜索人物" />
          </label>
          <div class="filters">
            <button type="button" :class="{ on: !group }" @click="group = ''">全部 {{ people.length }}</button>
            <button v-for="[g, n] in groups" :key="g" type="button" :class="{ on: group === g }" @click="group = g">{{ g }} {{ n }}</button>
          </div>
        </div>
        <p v-if="!filtered.length" class="plist__empty">{{ people.length ? '没有找到' : '还没有人物卡。会议里常出现的人，可以先加进来。' }}</p>
        <template v-if="recent.length">
          <div class="pgroup">最近互动</div>
          <button v-for="r in recent" :key="r.p.id" type="button" class="prow" :class="{ on: person?.id === r.p.id }" @click="select(r.p)">
            <span class="av" :style="{ background: r.p.color }">{{ initial(r.p) }}</span>
            <span class="prow__main"><b>{{ label(r.p) }}</b><small>{{ r.sub }}</small></span>
          </button>
        </template>
        <template v-if="stale.length">
          <div class="pgroup">该联系了</div>
          <button v-for="r in stale" :key="r.p.id" type="button" class="prow" :class="{ on: person?.id === r.p.id }" @click="select(r.p)">
            <span class="av" :style="{ background: r.p.color }">{{ initial(r.p) }}</span>
            <span class="prow__main"><b>{{ label(r.p) }}</b><small>{{ r.sub }}</small></span>
            <span class="nudge">{{ r.idle === null ? '从没' : `${r.idle} 天` }}</span>
          </button>
        </template>
      </aside>

      <section v-if="person" class="pcard surface">
        <div class="phead">
          <span class="av av--lg" :style="{ background: person.color }">{{ initial(person) }}</span>
          <div class="phead__t">
            <h2>{{ label(person) }}</h2>
            <p>{{ person.group }}<template v-if="person.intro"> · {{ person.intro }}</template></p>
          </div>
          <div class="acts">
            <button class="btn btn--ghost btn--sm" type="button" @click="openContact"><Icon icon="lucide:phone" />联系过</button>
            <button v-if="person.group !== '家人'" class="btn btn--ghost btn--sm" type="button" :disabled="scheduling" @click="scheduleOneOnOne"><Icon icon="lucide:calendar-plus" />约 1:1</button>
            <button class="btn btn--quiet btn--sm" type="button" :aria-label="`编辑${person.name}`" @click="openDialog(person)"><Icon icon="lucide:pencil" /></button>
            <button class="btn btn--quiet btn--sm danger" type="button" :aria-label="`删除${person.name}`" @click="remove(person)"><Icon icon="lucide:trash-2" /></button>
          </div>
        </div>
        <p v-if="selected?.stale" class="nudge-bar">
          <Icon icon="lucide:hourglass" />{{ selected.idle === null ? '还没有记录过往来' : `已经 ${selected.idle} 天没联系了` }}（你设的是 {{ person.contactEvery }} 天一次）
        </p>

        <div class="pgrid">
          <div class="col">
            <div class="sec">
              <h3>互相的承诺</h3>
              <div class="owe">
                <div class="owe__head"><span>我答应他的</span><button type="button" @click="startPromise('me')"><Icon icon="lucide:plus" /></button></div>
                <p v-if="!commits.mine.length" class="none">没有欠着的</p>
                <div v-for="c in commits.mine" :key="c.key" class="owe__row">
                  <button v-if="c.promiseId" type="button" class="ck" :aria-label="`了结：${c.text}`" @click="finishPromise(c)" />
                  <span v-else class="ck ck--me" />
                  <div>
                    <router-link v-if="c.to" :to="c.to">{{ c.text }}</router-link><template v-else>{{ c.text }}</template>
                    <small><span :class="{ over: c.due && c.due < today }">{{ c.due ? `${monthDay(c.due)} · ${dueText(c.due, today)}` : '没定日子' }}</span> · 来自{{ c.from }}</small>
                  </div>
                </div>
                <div class="owe__head owe__head--them"><span>他答应我的</span><button type="button" @click="startPromise('them')"><Icon icon="lucide:plus" /></button></div>
                <p v-if="!commits.theirs.length" class="none">没有等他的</p>
                <div v-for="c in commits.theirs" :key="c.key" class="owe__row owe__row--them">
                  <button v-if="c.promiseId" type="button" class="ck" :aria-label="`了结：${c.text}`" @click="finishPromise(c)" />
                  <span v-else class="ck" />
                  <div>
                    <router-link v-if="c.to" :to="c.to">{{ c.text }}</router-link><template v-else>{{ c.text }}</template>
                    <small><span :class="{ over: c.due && c.due < today }">{{ c.due ? `${monthDay(c.due)} · ${dueText(c.due, today)}` : '没说什么时候' }}</span> · 来自{{ c.from }}</small>
                  </div>
                </div>
                <form v-if="addingPromise" class="owe__add" @submit.prevent="savePromise">
                  <span>{{ addingPromise.who === 'me' ? '我答应：' : '他答应：' }}</span>
                  <input v-model="addingPromise.text" type="text" placeholder="什么事" aria-label="承诺内容" autofocus />
                  <input v-model="addingPromise.due" type="date" aria-label="什么时候" />
                  <button class="btn btn--primary btn--sm" type="submit" :disabled="!addingPromise.text.trim()">记下</button>
                  <button class="btn btn--quiet btn--sm" type="button" @click="addingPromise = null">取消</button>
                </form>
              </div>
            </div>

            <div class="sec">
              <h3>往来 <span class="muted">共 {{ timeline.length }} 条</span></h3>
              <p v-if="!timeline.length" class="none">还没有往来。会议参会人、随手记、任务里写到 {{ namesOf(person).join('、') }} 会自动出现在这里。</p>
              <div class="tl">
                <component
                  :is="e.to ? 'router-link' : 'div'"
                  v-for="(e, i) in allEvents ? timeline : timeline.slice(0, SHOW_EVENTS)"
                  :key="i"
                  :to="e.to"
                  class="ev"
                  :class="`ev--${e.kind}`"
                >
                  <time>{{ e.kind === 'upcoming' ? relativeDay(e.date) : evDate(e.date) }}</time>
                  <span class="ev__ico"><Icon :icon="EV_ICON[e.kind]!" /></span>
                  <div>{{ e.title }}<small v-if="e.detail">{{ e.detail }}</small></div>
                </component>
              </div>
              <button v-if="timeline.length > SHOW_EVENTS && !allEvents" class="btn btn--quiet btn--sm" type="button" @click="allEvents = true">再看 {{ timeline.length - SHOW_EVENTS }} 条</button>
            </div>
          </div>

          <div class="col">
            <div v-if="talkAbout.length" class="remind">
              <b>下次{{ upcomingMeeting ? `（${upcomingMeeting.title} · ${relativeDay(upcomingMeeting.date)}）` : '见面' }}可以聊：</b>
              <ul><li v-for="t in talkAbout" :key="t.text">{{ t.text }}<small>（{{ t.note }}）</small></li></ul>
              <button v-if="upcomingMeeting" class="btn btn--ghost btn--sm" type="button" @click="addToAgenda">加进下次议程</button>
            </div>
            <div class="sec">
              <h3>信息</h3>
              <div class="facts">
                <div v-if="person.birthday"><span>生日</span><span>{{ Number(person.birthday.slice(0, 2)) }} 月 {{ Number(person.birthday.slice(3)) }} 日<template v-if="(birthdayIn(person, today) ?? 99) <= 30">（还有 {{ birthdayIn(person, today) }} 天）</template></span></div>
                <div v-for="f in person.facts" :key="f.label"><span>{{ f.label }}</span><span>{{ f.value }}</span></div>
                <div v-if="person.contactEvery"><span>联系</span><span>每 {{ person.contactEvery }} 天提醒一次</span></div>
                <p v-if="!person.birthday && !person.facts.length" class="none">点右上角的笔添加生日、偏好这些。</p>
              </div>
            </div>
            <div class="sec">
              <h3>备忘</h3>
              <textarea v-model="memo" class="memo" rows="4" placeholder="和他打交道要注意什么…" aria-label="备忘" @blur="saveMemo" />
            </div>
            <p class="privacy"><Icon icon="lucide:lock" />人物卡只有你能看见，不会出现在任何分享、公开主页或周报里。</p>
          </div>
        </div>
      </section>
      <StateBlock v-else state="empty" title="还没有人物卡" description="会议里常出现的人，可以先加进来。" action-label="添加" @action="openDialog(null)" />
    </div>

    <PersonDialog :open="dialogOpen" :person="editing" :groups="groups.map(([g]) => g)" @close="dialogOpen = false" @save="savePerson" />

    <BaseDialog :open="contactOpen" :title="`和${person?.alias || person?.name || ''}的联系`" width="24rem" @close="contactOpen = false">
      <div class="form contact">
        <label class="field">
          <span class="field__label">哪天</span>
          <input v-model="contactDate" class="field__input" type="date" :max="today" />
        </label>
        <label class="field">
          <span class="field__label">聊了什么（可以不写）</span>
          <input v-model="contactNote" class="field__input" type="text" maxlength="60" placeholder="电话，说国庆回家" @keydown.enter="saveContact" />
        </label>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="contactOpen = false">取消</button>
        <button class="btn btn--primary" type="button" @click="saveContact">记下</button>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.pl {
  display: grid;
  grid-template-columns: 19rem minmax(0, 1fr);
  gap: 1.1rem;
  align-items: start;
}

.plist {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  max-height: calc(100vh - var(--header-height) - 2.5rem);
  overflow-y: auto;
  border-radius: var(--radius-lg);
}

.plist__top {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 0.9rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
}

.plist__top h1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 1.15rem;
  font-weight: 800;
}

.search {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.38rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  color: var(--color-text-secondary);
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
  font-size: 0.86rem;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.filters button {
  padding: 0.15rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.76rem;
  cursor: pointer;
}

.filters button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.plist__empty {
  padding: 1rem;
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.pgroup {
  padding: 0.7rem 0.9rem 0.3rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.prow {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.55rem 0.9rem;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.prow:hover {
  background: var(--color-bg-soft);
}

.prow.on {
  background: var(--color-brand-soft);
}

.av {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 50%;
  color: #fff;
  font-size: 0.9rem;
  font-weight: 800;
}

.av--lg {
  width: 3.2rem;
  height: 3.2rem;
  font-size: 1.3rem;
}

.prow__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.prow__main b {
  font-size: 0.88rem;
}

.prow__main small {
  overflow: hidden;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nudge {
  flex: none;
  padding: 0 0.45rem;
  border-radius: 999px;
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
  font-size: 0.72rem;
  font-weight: 600;
}

.pcard {
  padding: 1.2rem 1.3rem 1.4rem;
  border-radius: var(--radius-lg);
}

.phead {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.9rem;
}

.phead__t {
  flex: 1;
  min-width: 12rem;
}

.phead h2 {
  font-size: 1.3rem;
  font-weight: 800;
}

.phead p {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.acts {
  display: flex;
  gap: 0.3rem;
}

.btn--sm {
  padding: 0.3rem 0.6rem;
  font-size: 0.8rem;
}

.danger:hover {
  color: var(--color-danger);
}

.nudge-bar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.9rem;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
  font-size: 0.82rem;
}

.pgrid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 1.4rem;
  margin-top: 1.2rem;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  min-width: 0;
}

.sec h3 {
  margin-bottom: 0.55rem;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
}

.muted {
  font-weight: 500;
  letter-spacing: 0;
}

.none {
  padding: 0.25rem 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.owe {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.owe__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 0.3rem;
  font-size: 0.78rem;
  font-weight: 700;
}

.owe__head--them {
  margin-top: 0.8rem;
}

.owe__head button {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.owe__head button:hover {
  background: var(--color-bg-soft);
  color: var(--color-brand);
}

.owe__row {
  display: flex;
  gap: 0.55rem;
  padding: 0.45rem 0.6rem;
  border-left: 3px solid var(--color-brand);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-brand-soft);
  font-size: 0.86rem;
}

.owe__row--them {
  border-left-color: #d97706;
  background: color-mix(in srgb, #d97706 9%, var(--color-bg-surface));
}

.owe__row a:hover {
  text-decoration: underline;
}

.owe__row small {
  display: block;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.owe__row small .over {
  color: var(--color-danger);
}

.ck {
  flex: none;
  width: 1rem;
  height: 1rem;
  margin-top: 0.15rem;
  padding: 0;
  border: 1.8px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-bg-surface);
}

button.ck {
  cursor: pointer;
}

button.ck:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.ck--me {
  border-color: #d97706;
}

.owe__add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.5rem;
  font-size: 0.82rem;
}

.owe__add input {
  padding: 0.3rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-canvas);
  font-size: 0.84rem;
}

.owe__add input[type='text'] {
  flex: 1;
  min-width: 8rem;
}

.tl {
  display: flex;
  flex-direction: column;
}

.ev {
  display: grid;
  grid-template-columns: 2.8rem 1.6rem minmax(0, 1fr);
  gap: 0.5rem;
  padding: 0.45rem 0.3rem;
  border-radius: var(--radius-sm);
  font-size: 0.86rem;
  color: inherit;
}

a.ev:hover {
  background: var(--color-bg-soft);
}

.ev time {
  padding-top: 0.1rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  text-align: right;
}

.ev__ico {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.ev__ico svg {
  width: 0.8rem;
  height: 0.8rem;
}

.ev--meeting .ev__ico {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.ev--upcoming .ev__ico {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.ev--upcoming time {
  color: var(--color-accent-text);
  font-weight: 600;
}

.ev small {
  display: block;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.remind {
  padding: 0.8rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--color-accent) 35%, var(--color-border));
  border-radius: var(--radius-lg);
  background: var(--color-accent-soft);
  font-size: 0.84rem;
  line-height: 1.6;
}

.remind small {
  color: var(--color-text-secondary);
}

.remind ul {
  margin: 0.3rem 0 0.5rem;
  padding-left: 1.1rem;
}

.facts {
  display: flex;
  flex-direction: column;
}

.facts div {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr);
  gap: 0.6rem;
  padding: 0.4rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.facts div span:first-child {
  color: var(--color-text-secondary);
}

.memo {
  width: 100%;
  padding: 0.6rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, #facc15 8%, var(--color-bg-surface));
  font-size: 0.86rem;
  line-height: 1.7;
  resize: vertical;
}

.memo:focus {
  outline: none;
  border-color: var(--color-brand);
}

.privacy {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.contact {
  gap: 0.9rem;
  padding: 0;
}

@media (max-width: 1100px) {
  .pgrid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .pl {
    grid-template-columns: minmax(0, 1fr);
  }

  .plist {
    position: static;
    max-height: 22rem;
  }
}
</style>
