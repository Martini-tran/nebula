<script setup lang="ts">
/**
 * 稍后读：书签解决「存下来」，这里解决「真的读完」。
 * 按「未读 / 在读 / 读完」分页，每篇显示预计时长与进度；放了 30 天没动的标黄问「还读吗？」，一键归档——
 * 稍后读列表最大的问题是只进不出。右栏是本月阅读目标。?tab=unread|reading|done|archived
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { createReadingItem, deleteReadingItem, fetchHighlights, fetchReadingItems, updateReadingItem } from '../../api/reading'
import { favColor } from '../../api/search'
import { useSettingsStore } from '../../stores/settings'
import { useDeferredDelete } from '../../composables/useDeferredDelete'
import { errorText, toast } from '../../composables/useToast'
import { diffDays, relativeDay, todayYmd, ymdOf } from '../../utils/date'
import { formatMinutes } from '../../utils/format'
import type { Highlight, ReadingItem, ReadStatus } from '../../types/reading'

const route = useRoute()
const router = useRouter()
const settings = useSettingsStore()
const today = todayYmd()

type Tab = ReadStatus | 'archived'
const TABS: { key: Tab; label: string }[] = [
  { key: 'unread', label: '未读' },
  { key: 'reading', label: '在读' },
  { key: 'done', label: '读完' },
]
const tab = computed<Tab>(() => {
  const t = route.query.tab
  return t === 'unread' || t === 'done' || t === 'archived' || t === 'reading' ? t : 'reading'
})
const setTab = (t: Tab) => router.replace({ query: { ...route.query, tab: t === 'reading' ? undefined : t } })

const sort = ref<'added' | 'short'>('added')

const items = ref<ReadingItem[]>([])
const archived = ref<ReadingItem[]>([])
const highlights = ref<Highlight[]>([])
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    const [live, arch, hls] = await Promise.all([fetchReadingItems(), fetchReadingItems({ archived: true }), fetchHighlights()])
    items.value = live
    archived.value = arch
    highlights.value = hls
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const deferred = useDeferredDelete({ remove: deleteReadingItem, onCommitted: load })

const counts = computed(() => {
  const map: Record<string, number> = { unread: 0, reading: 0, done: 0 }
  items.value.forEach((i) => (map[i.status] = (map[i.status] ?? 0) + 1))
  return map
})

const list = computed(() => {
  const source = tab.value === 'archived' ? archived.value : items.value.filter((i) => i.status === tab.value)
  const rows = source.filter((i) => !deferred.isHidden(i.id))
  if (tab.value === 'done') return [...rows].sort((a, b) => String(b.doneTime).localeCompare(String(a.doneTime)))
  if (sort.value === 'short') return [...rows].sort((a, b) => (a.minutes || 99) - (b.minutes || 99))
  return rows
})

const hlCount = (item: ReadingItem) => highlights.value.filter((h) => String(h.itemId) === String(item.id)).length

/** 最后一次碰它（加入或读）过去多少天；未读完且超过 30 天就问还读吗 */
const idleDays = (item: ReadingItem) => diffDays(ymdOf(item.lastReadTime ?? item.addTime), today)
const isStale = (item: ReadingItem) => item.status !== 'done' && !item.archived && idleDays(item) >= 30

const whenText = (stamp: string | null) => (stamp ? relativeDay(ymdOf(stamp)) : '')

// ── 加入 ──

const draft = ref('')
const adding = ref(false)
const add = async () => {
  let url = draft.value.trim()
  if (!url || adding.value) return
  if (!/^https?:\/\//i.test(url)) url = `https://${url}`
  adding.value = true
  try {
    const item = await createReadingItem({ url })
    draft.value = ''
    toast.ok(item.content ? `已加入「${item.title}」` : '已加入。阅读版需要后端抓取正文，现在只能打开原文')
    if (tab.value !== 'unread' && item.status === 'unread') setTab('unread')
    load()
  } catch (error) {
    toast.error(errorText(error, '加入失败'))
  } finally {
    adding.value = false
  }
}

// ── 操作 ──

const open = (item: ReadingItem) => router.push(`/reading/${item.id}`)

const archive = async (item: ReadingItem, on = true) => {
  try {
    await updateReadingItem(item.id, { archived: on })
    toast.ok(on ? `已归档「${item.title}」` : '已放回队列', on ? { action: { label: '撤销', run: () => archive(item, false) } } : undefined)
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const markDone = async (item: ReadingItem) => {
  try {
    await updateReadingItem(item.id, { status: 'done', progress: 1, doneTime: `${today} ${new Date().toTimeString().slice(0, 8)}` })
    toast.ok(`读完「${item.title}」`)
    load()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}

const remove = (item: ReadingItem) => deferred.schedule(item.id, `已删除「${item.title}」和它的划线`)

// ── 本月 ──

const month = today.slice(0, 7)
const monthDone = computed(() => items.value.concat(archived.value).filter((i) => i.status === 'done' && i.doneTime?.startsWith(month)))
const monthStats = computed(() => ({
  done: monthDone.value.length,
  minutes: monthDone.value.reduce((sum, i) => sum + i.minutes, 0),
  highlights: highlights.value.filter((h) => h.createTime.startsWith(month)).length,
}))
const goalPct = computed(() => Math.min(100, Math.round((monthStats.value.done / Math.max(1, settings.data.readingGoal)) * 100)))
const staleCount = computed(() => items.value.filter(isStale).length)

onMounted(load)
</script>

<template>
  <div class="reading page">
    <header class="head">
      <h1 class="page-title">稍后读</h1>
      <div class="seg" role="tablist" aria-label="阅读状态">
        <button v-for="t in TABS" :key="t.key" type="button" role="tab" :aria-selected="tab === t.key" :class="{ on: tab === t.key }" @click="setTab(t.key)">
          {{ t.label }}<span class="n">{{ counts[t.key] }}</span>
        </button>
      </div>
      <router-link class="btn btn--ghost head__lib" to="/reading/highlights"><Icon icon="lucide:highlighter" />摘录库</router-link>
      <label v-if="tab !== 'done' && tab !== 'archived'" class="sort">
        <Icon icon="lucide:arrow-down-up" />
        <select v-model="sort" aria-label="排序">
          <option value="added">按加入时间</option>
          <option value="short">先读短的</option>
        </select>
      </label>
    </header>

    <div class="rl">
      <section class="main">
        <form class="add" @submit.prevent="add">
          <Icon icon="lucide:link" />
          <input v-model="draft" type="text" placeholder="粘贴网址，回车加入稍后读" aria-label="加入稍后读的网址" />
          <button class="btn btn--primary" type="submit" :disabled="!draft.trim() || adding">
            <Icon v-if="adding" icon="lucide:loader-circle" class="spin" />加入
          </button>
        </form>

        <p v-if="tab === 'archived'" class="banner">
          <Icon icon="lucide:archive" />归档的文章不在队列里，划线还在摘录库。
          <button type="button" @click="setTab('reading')">回到队列</button>
        </p>
        <p v-else-if="staleCount && tab !== 'done'" class="banner banner--warn">
          <Icon icon="lucide:hourglass" />有 {{ staleCount }} 篇放了 30 天以上没动。读不完不丢人，归档了还能在「已归档」里找到。
        </p>

        <StateBlock v-if="loading" state="loading" />
        <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />
        <StateBlock
          v-else-if="!list.length"
          state="empty"
          :title="tab === 'done' ? '还没有读完的文章' : tab === 'archived' ? '没有归档的文章' : tab === 'reading' ? '没有正在读的' : '队列空了'"
          :description="tab === 'unread' || tab === 'reading' ? '在上面粘贴网址加入，或者从书签详情里点「稍后读」。' : ''"
        />

        <article v-for="item in list" v-else :key="item.id" class="art surface" :class="{ 'art--stale': isStale(item) }">
          <div class="art__body" role="link" tabindex="0" @click="open(item)" @keydown.enter="open(item)">
            <div class="art__src">
              <span class="fav" :style="{ background: favColor(item.domain) }">{{ item.domain.charAt(0).toUpperCase() }}</span>
              {{ item.domain }}<template v-if="item.minutes"> · {{ item.minutes }} 分钟</template>
              <span v-if="!item.content" class="tag">未存档</span>
              <span v-if="isStale(item)" class="stale">放了 {{ idleDays(item) }} 天 · 还读吗？</span>
            </div>
            <h3>{{ item.title }}</h3>
            <p v-if="item.status === 'done' && item.thought" class="thought">“{{ item.thought }}”</p>
            <p v-else-if="item.excerpt" class="excerpt">{{ item.excerpt }}…</p>
            <div class="prog">
              <template v-if="item.status === 'done'">
                <Icon icon="lucide:check" class="ok" />读完 · {{ whenText(item.doneTime) }}<template v-if="hlCount(item)"> · {{ hlCount(item) }} 处划线</template>
              </template>
              <template v-else>
                <span class="prog__bar"><i :style="{ width: `${Math.round(item.progress * 100)}%` }" /></span>
                <template v-if="item.progress">读到 {{ Math.round(item.progress * 100) }}% · </template>
                <template v-if="hlCount(item)">{{ hlCount(item) }} 处划线 · </template>
                {{ item.lastReadTime ? whenText(item.lastReadTime) : `${whenText(item.addTime)}加入` }}
              </template>
            </div>
          </div>
          <div class="art__side">
            <span class="art__thumb" :style="{ color: favColor(item.domain) }">{{ item.title.charAt(0).toUpperCase() }}</span>
            <div class="art__acts">
              <template v-if="isStale(item)">
                <button class="btn btn--quiet btn--sm" type="button" @click="archive(item)">归档</button>
                <button class="btn btn--ghost btn--sm" type="button" @click="open(item)">继续读</button>
              </template>
              <template v-else>
                <a class="icon" :href="item.url" target="_blank" rel="noopener noreferrer" title="打开原文" aria-label="打开原文"><Icon icon="lucide:external-link" /></a>
                <button v-if="item.status !== 'done'" class="icon" type="button" title="标记读完" aria-label="标记读完" @click="markDone(item)"><Icon icon="lucide:check-check" /></button>
                <button v-if="tab !== 'archived'" class="icon" type="button" title="归档" aria-label="归档" @click="archive(item)"><Icon icon="lucide:archive" /></button>
                <button v-else class="icon" type="button" title="放回队列" aria-label="放回队列" @click="archive(item, false)"><Icon icon="lucide:archive-restore" /></button>
                <button class="icon icon--danger" type="button" title="删除" aria-label="删除" @click="remove(item)"><Icon icon="lucide:trash-2" /></button>
              </template>
            </div>
          </div>
        </article>
      </section>

      <aside class="side">
        <section class="box surface">
          <h2>{{ Number(month.slice(5)) }} 月阅读</h2>
          <div class="goal"><b>{{ monthStats.done }}</b><span>/ {{ settings.data.readingGoal }} 篇</span></div>
          <div class="goal__bar"><i :style="{ width: `${goalPct}%` }" /></div>
          <p class="meta">
            <span>共 {{ formatMinutes(monthStats.minutes) }}</span><span>划线 {{ monthStats.highlights }} 处</span>
          </p>
          <router-link class="box__link" to="/settings?s=reading">改目标</router-link>
        </section>
        <section class="box surface">
          <h2>整理</h2>
          <button class="side__row" type="button" @click="setTab('archived')">
            <Icon icon="lucide:archive" />已归档<span>{{ archived.length }}</span>
          </button>
          <router-link class="side__row" to="/reading/highlights"><Icon icon="lucide:highlighter" />摘录库<span>{{ highlights.length }}</span></router-link>
        </section>
        <section class="box surface">
          <h2>浏览器扩展 <span class="tag">规划中</span></h2>
          <div class="ext"><kbd>Alt S</kbd><span>当前页加入稍后读</span></div>
          <div class="ext"><kbd>Alt B</kbd><span>只存为书签</span></div>
          <div class="ext"><kbd>Alt N</kbd><span>选中文字存为随手记，附原网址</span></div>
          <p class="meta">扩展是独立工程，调用同一套 /space/me 接口；在那之前可以在这里粘贴网址。</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.1rem;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.85rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.seg .n {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.head__lib {
  margin-left: auto;
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.sort {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.sort select {
  border: 0;
  background: none;
  font-size: 0.82rem;
}

.rl {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 17rem;
  gap: 1.25rem;
  align-items: start;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.add {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.45rem 0.5rem 0.45rem 0.9rem;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
}

.add input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.add .btn {
  padding: 0.35rem 0.9rem;
  font-size: 0.84rem;
}

.banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 0.85rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.banner button {
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.84rem;
  cursor: pointer;
}

.banner--warn {
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
}

.art {
  display: flex;
  gap: 1rem;
  padding: 1rem 1.1rem;
  border-radius: var(--radius-lg);
  transition: border-color 0.15s ease;
}

.art:hover {
  border-color: color-mix(in srgb, var(--color-brand) 40%, var(--color-border));
}

.art--stale {
  border-color: color-mix(in srgb, #f59e0b 45%, var(--color-border));
  background: color-mix(in srgb, #f59e0b 6%, var(--color-bg-surface));
}

.art__body {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.art__src {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.fav {
  display: grid;
  place-items: center;
  width: 1.3rem;
  height: 1.3rem;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.66rem;
  font-weight: 800;
}

.tag {
  padding: 0 0.4rem;
  font-size: 0.68rem;
}

.stale {
  padding: 0 0.45rem;
  border-radius: 999px;
  background: color-mix(in srgb, #f59e0b 18%, transparent);
  color: var(--color-warn, #b45309);
  font-weight: 600;
}

.art h3 {
  margin: 0.3rem 0 0.2rem;
  font-size: 1.02rem;
  font-weight: 700;
}

.excerpt,
.thought {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.86rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.thought {
  font-style: italic;
}

.prog {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.55rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.prog .ok {
  color: var(--color-accent);
}

.prog__bar {
  width: 6rem;
  height: 0.3rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.prog__bar i {
  display: block;
  height: 100%;
  background: var(--color-brand);
}

.art__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.5rem;
  flex: none;
}

.art__thumb {
  display: grid;
  place-items: center;
  width: 5.5rem;
  height: 4rem;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-bg-soft), color-mix(in srgb, currentColor 14%, var(--color-bg-soft)));
  font-size: 1.5rem;
  font-weight: 800;
}

.art__acts {
  display: flex;
  gap: 0.15rem;
}

.art__acts .icon {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  opacity: 0;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.art:hover .icon,
.art .icon:focus-visible {
  opacity: 1;
}

.art__acts .icon:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.art__acts .icon--danger:hover {
  color: var(--color-danger);
}

.btn--sm {
  padding: 0.3rem 0.65rem;
  font-size: 0.8rem;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
}

.box {
  padding: 0.9rem 1rem;
  border-radius: var(--radius-lg);
}

.box h2 {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.55rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.goal b {
  font-size: 1.7rem;
  font-weight: 800;
}

.goal span {
  margin-left: 0.3rem;
  color: var(--color-text-secondary);
}

.goal__bar {
  height: 0.4rem;
  margin: 0.4rem 0 0.55rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.goal__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.8rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.box__link {
  display: inline-block;
  margin-top: 0.4rem;
  font-size: 0.76rem;
  color: var(--color-brand);
}

.side__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.4rem 0.3rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: inherit;
  font-size: 0.86rem;
  cursor: pointer;
}

.side__row:hover {
  background: var(--color-bg-soft);
}

.side__row span {
  margin-left: auto;
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.ext {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-bottom: 0.4rem;
  font-size: 0.82rem;
}

kbd {
  flex: none;
  padding: 0.02rem 0.35rem;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono, monospace);
  font-size: 0.68rem;
  color: var(--color-text-secondary);
}

@media (max-width: 960px) {
  .rl {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
  }
}

@media (max-width: 560px) {
  .art__thumb {
    display: none;
  }

  .art__acts .icon {
    opacity: 1;
  }

  .head__lib {
    margin-left: 0;
  }
}
</style>
