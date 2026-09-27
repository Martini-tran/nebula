<script setup lang="ts">
/**
 * 摘录库：所有划线汇成卡片墙，按文章、颜色筛选、按文字搜。
 * 顶部「今天回顾一条」随机翻出一条旧摘录（每天固定一条，可以换；设置里可关）——
 * 划过的线大多再也不会看第二眼，这是让它们被重新看见的最低成本方式。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import { deleteHighlight, fetchHighlights, fetchReadingItems, updateHighlight } from '../../api/reading'
import { createNote } from '../../api/notes'
import { favColor } from '../../api/search'
import { useSettingsStore } from '../../stores/settings'
import { useDeferredDelete } from '../../composables/useDeferredDelete'
import { errorText, toast } from '../../composables/useToast'
import { relativeDay, todayYmd, ymdOf, monthDay } from '../../utils/date'
import { HIGHLIGHT_COLORS, type Highlight, type HighlightColor, type ReadingItem } from '../../types/reading'

const router = useRouter()
const settings = useSettingsStore()

const highlights = ref<Highlight[]>([])
const items = ref<ReadingItem[]>([])
const loading = ref(true)
const loadError = ref('')

const load = async () => {
  loadError.value = ''
  try {
    const [hls, live, arch] = await Promise.all([fetchHighlights(), fetchReadingItems(), fetchReadingItems({ archived: true })])
    highlights.value = hls
    items.value = [...live, ...arch]
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const deferred = useDeferredDelete({ remove: deleteHighlight, onCommitted: load })
/** 书名号：标题本身带了就不再加 */
const bookTitle = (title?: string) => (!title ? '已删除的文章' : title.startsWith('《') ? title : `《${title}》`)
const itemOf = (h: Highlight) => items.value.find((i) => String(i.id) === String(h.itemId))

// ── 筛选 ──

const article = ref('')
const color = ref<HighlightColor | ''>('')
const keyword = ref('')

const articles = computed(() =>
  items.value
    .map((i) => ({ id: String(i.id), title: i.title, n: highlights.value.filter((h) => String(h.itemId) === String(i.id)).length }))
    .filter((a) => a.n)
    .sort((a, b) => b.n - a.n),
)

const list = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return highlights.value
    .filter((h) => !deferred.isHidden(h.id))
    .filter((h) => !article.value || String(h.itemId) === article.value)
    .filter((h) => !color.value || h.color === color.value)
    .filter((h) => !kw || `${h.text} ${h.note} ${itemOf(h)?.title ?? ''}`.toLowerCase().includes(kw))
})

// ── 今天回顾一条：按日期挑一条（同一天总是同一条），「换一条」随机 ──

const pick = ref<Highlight | null>(null)
const daily = computed(() => {
  if (!settings.data.dailyQuote) return null
  if (pick.value) return pick.value
  const old = highlights.value.filter((h) => ymdOf(h.createTime) < todayYmd())
  if (!old.length) return null
  const seed = [...todayYmd()].reduce((sum, ch) => sum * 31 + ch.charCodeAt(0), 7)
  return old[Math.abs(seed) % old.length]!
})
const another = () => {
  const pool = highlights.value.filter((h) => h.id !== daily.value?.id)
  if (pool.length) pick.value = pool[Math.floor(Math.random() * pool.length)]!
}

// ── 操作 ──

const openAt = (h: Highlight) => router.push({ path: `/reading/${h.itemId}`, query: { hl: String(h.id) } })

const toNote = async (h: Highlight) => {
  if (h.noteId) {
    router.push(`/notes/${h.noteId}`)
    return
  }
  const item = itemOf(h)
  try {
    const note = await createNote({
      content: `> ${h.text}\n\n${h.note ? `${h.note}\n\n` : ''}—— [${item?.title ?? '原文'}](${item?.url ?? ''})`,
      tags: ['摘录'],
    })
    const updated = await updateHighlight(h.id, { noteId: note.id })
    highlights.value = highlights.value.map((x) => (x.id === h.id ? updated : x))
    toast.ok('已存为随手记（#摘录）', { action: { label: '打开', run: () => router.push(`/notes/${note.id}`) } })
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  }
}

const remove = (h: Highlight) => deferred.schedule(h.id, '已删除这条划线')

onMounted(load)
</script>

<template>
  <div class="lib page">
    <header class="head">
      <router-link class="btn btn--quiet back" to="/reading"><Icon icon="lucide:arrow-left" />稍后读</router-link>
      <h1 class="page-title">摘录库</h1>
      <span class="count">{{ highlights.length }} 条划线</span>
    </header>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <template v-else>
      <div v-if="daily" class="daily">
        <Icon icon="lucide:quote" class="daily__ico" />
        <blockquote>
          {{ daily.text }}
          <small>今天回顾一条 · {{ bookTitle(itemOf(daily)?.title) }} · {{ monthDay(ymdOf(daily.createTime)) }}划线</small>
        </blockquote>
        <div class="daily__acts">
          <button class="btn btn--ghost btn--sm" type="button" @click="another">换一条</button>
          <button class="btn btn--quiet btn--sm" type="button" @click="openAt(daily)">回到原文</button>
        </div>
      </div>

      <div class="filters">
        <label class="search">
          <Icon icon="lucide:search" />
          <input v-model="keyword" type="text" placeholder="搜划线、批注、文章" aria-label="搜索摘录" />
        </label>
        <select v-model="article" class="sel" aria-label="按文章筛选">
          <option value="">全部文章</option>
          <option v-for="a in articles" :key="a.id" :value="a.id">{{ a.title }}（{{ a.n }}）</option>
        </select>
        <div class="colors" role="radiogroup" aria-label="按颜色筛选">
          <button type="button" role="radio" :aria-checked="!color" :class="{ on: !color }" @click="color = ''">全部</button>
          <button v-for="(c, key) in HIGHLIGHT_COLORS" :key="key" type="button" role="radio" :aria-checked="color === key" :class="{ on: color === key }" @click="color = key">
            <i :class="`sw sw--${key}`" />{{ c.label }}
          </button>
        </div>
      </div>

      <StateBlock
        v-if="!list.length"
        state="empty"
        :title="highlights.length ? '没有符合条件的划线' : '还没有划线'"
        :description="highlights.length ? '' : '在阅读模式里选中文字就能划线，划线会汇到这里。'"
      />

      <div v-else class="wall">
        <div v-for="h in list" :key="h.id" class="q surface" :class="`q--${h.color}`">
          <blockquote>{{ h.text }}</blockquote>
          <p v-if="h.note" class="q__note">{{ h.note }}</p>
          <p v-if="h.taskTitle" class="q__note q__note--link">→ 任务「{{ h.taskTitle }}」</p>
          <div class="q__src">
            <span class="fav" :style="{ background: favColor(itemOf(h)?.domain ?? '?') }">{{ (itemOf(h)?.domain ?? '?').charAt(0).toUpperCase() }}</span>
            <button type="button" class="q__title" @click="openAt(h)">{{ itemOf(h)?.title ?? '已删除的文章' }}</button>
            <span>{{ relativeDay(ymdOf(h.createTime)) }}</span>
          </div>
          <div class="q__acts">
            <button type="button" :title="h.noteId ? '打开随手记' : '存为随手记'" :aria-label="h.noteId ? '打开随手记' : '存为随手记'" @click="toNote(h)">
              <Icon :icon="h.noteId ? 'lucide:sticky-note' : 'lucide:pencil-line'" />
            </button>
            <button type="button" title="回到原文" aria-label="回到原文" @click="openAt(h)"><Icon icon="lucide:book-open" /></button>
            <button type="button" class="danger" title="删除" aria-label="删除" @click="remove(h)"><Icon icon="lucide:trash-2" /></button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1rem;
}

.back {
  padding: 0.35rem 0.6rem;
  font-size: 0.86rem;
}

.count {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.daily {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
  padding: 1rem 1.2rem;
  border: 1px solid color-mix(in srgb, var(--color-brand) 30%, var(--color-border));
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
}

.daily__ico {
  flex: none;
  width: 1.6rem;
  height: 1.6rem;
  color: var(--color-brand);
}

.daily blockquote {
  flex: 1;
  margin: 0;
  font-family: Georgia, 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 1.02rem;
  line-height: 1.8;
}

.daily small {
  display: block;
  margin-top: 0.2rem;
  font-family: var(--font-sans);
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.daily__acts {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  flex: none;
}

.btn--sm {
  padding: 0.3rem 0.65rem;
  font-size: 0.8rem;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1rem;
}

.search {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex: 1;
  min-width: 12rem;
  max-width: 22rem;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  color: var(--color-text-primary);
}

.sel {
  max-width: 16rem;
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  font-size: 0.86rem;
}

.colors {
  display: inline-flex;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.colors button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.28rem 0.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
}

.colors button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.sw {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 50%;
}

.sw--yellow {
  background: #fde047;
}

.sw--blue {
  background: #93c5fd;
}

.wall {
  columns: 3 18rem;
  column-gap: 1rem;
}

.q {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 0.95rem 1rem 0.8rem;
  border-left: 3px solid #eab308;
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  break-inside: avoid;
}

.q--blue {
  border-left-color: #3b82f6;
}

.q blockquote {
  margin: 0;
  font-family: Georgia, 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 0.96rem;
  line-height: 1.8;
}

.q__note {
  padding: 0.35rem 0.55rem;
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
  font-size: 0.8rem;
  line-height: 1.6;
}

.q__note--link {
  background: none;
  padding: 0;
  color: var(--color-text-secondary);
}

.q__src {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.q__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  border: 0;
  background: none;
  color: inherit;
  font-size: 0.74rem;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.q__title:hover {
  color: var(--color-brand);
}

.fav {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.1rem;
  height: 1.1rem;
  border-radius: 0.25rem;
  color: #fff;
  font-size: 0.58rem;
  font-weight: 800;
}

.q__acts {
  position: absolute;
  top: 0.4rem;
  right: 0.4rem;
  display: flex;
  gap: 0.1rem;
  padding: 0.1rem;
  border-radius: var(--radius-sm);
  background: var(--color-bg-surface);
  opacity: 0;
  transition: opacity 0.15s ease;
}

.q:hover .q__acts,
.q__acts:focus-within {
  opacity: 1;
}

.q__acts button {
  display: grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.q__acts button:hover {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.q__acts .danger:hover {
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .daily {
    flex-direction: column;
    align-items: stretch;
  }

  .daily__acts {
    flex-direction: row;
  }

  .q__acts {
    opacity: 1;
  }
}
</style>
