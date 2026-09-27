<script setup lang="ts">
/**
 * 公开主页设置：整体一个总开关（默认关），开启后逐块勾选、调整顺序；书签合集逐个选择公开哪些目录。
 * 右侧写清永远不会公开的内容、地址和访问量。改完先「以访客身份预览」，确认无误再保存。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { Icon } from '@iconify/vue'
import StateBlock from '../../components/StateBlock.vue'
import ToggleSwitch from '../../components/base/ToggleSwitch.vue'
import PublicView from './components/PublicView.vue'
import { checkHandle, fetchProfile, handleProblem, saveProfile } from '../../api/profile'
import { fetchHighlights, fetchReadingItems } from '../../api/reading'
import { useSpaceStore } from '../../stores/space'
import { useAuthStore } from '../../stores/auth'
import { useSettingsStore } from '../../stores/settings'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { monthDay, todayYmd, ymdOf } from '../../utils/date'
import { buildPublicPage } from './publicData'
import { BLOCKS, type BlockKey, type PublicPage, type PublicProfile } from '../../types/profile'
import type { Highlight, ReadingItem } from '../../types/reading'

const space = useSpaceStore()
const auth = useAuthStore()
const settings = useSettingsStore()

const saved = ref<PublicProfile | null>(null)
const draft = ref<PublicProfile | null>(null)
const reading = ref<ReadingItem[]>([])
const highlights = ref<Highlight[]>([])
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)

const load = async () => {
  loadError.value = ''
  try {
    const [p, done, hls] = await Promise.all([
      fetchProfile(),
      fetchReadingItems({ status: 'done' }).catch(() => []),
      fetchHighlights().catch(() => []),
      space.reload().catch(() => undefined),
    ])
    saved.value = p
    draft.value = structuredClone(p)
    reading.value = done.sort((a, b) => String(b.doneTime).localeCompare(String(a.doneTime))).slice(0, 10)
    highlights.value = hls
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const dirty = computed(() => Boolean(draft.value && saved.value && JSON.stringify(draft.value) !== JSON.stringify(saved.value)))

// ── 区块 ──

const expanded = ref<BlockKey | null>(null)
const move = (i: number, delta: number) => {
  const list = draft.value!.blocks
  const j = i + delta
  if (j < 0 || j >= list.length) return
  ;[list[i], list[j]] = [list[j]!, list[i]!]
}

const blockDetail = (key: BlockKey) => {
  const d = draft.value!
  if (key === 'intro') return d.bio ? `「${d.bio.slice(0, 24)}${d.bio.length > 24 ? '…' : ''}」` : '还没写介绍'
  if (key === 'links') return d.links.length ? d.links.map((l) => l.label).join('、') : '还没有链接'
  if (key === 'now') return d.nowUpdated ? `上次更新 ${monthDay(d.nowUpdated)}` : '还没写'
  if (key === 'collections') return d.collections.length ? `已公开 ${d.collections.length} 个：${d.collections.map((c) => c.title).join('、')}` : '还没选目录'
  if (key === 'reading') return `最近读完 ${reading.value.length} 篇${d.hiddenReading.length ? `，隐藏了 ${d.hiddenReading.length} 篇` : ''}`
  if (key === 'quotes') return d.quoteIds.length ? `挑了 ${d.quoteIds.length} 条` : '还没挑'
  return ''
}

// Now：改了就更新日期
watch(
  () => draft.value?.now,
  (now, before) => {
    if (draft.value && before !== undefined && now !== before) draft.value.nowUpdated = todayYmd()
  },
)

// 书签合集：勾选目录
const folders = computed(() => space.flat.map((f) => ({ id: String(f.id), path: f.path, depth: f.depth, name: f.folder.name })))
const isPublic = (id: string) => draft.value!.collections.some((c) => String(c.folderId) === id)
const toggleFolder = (f: { id: string; name: string }) => {
  const d = draft.value!
  d.collections = isPublic(f.id) ? d.collections.filter((c) => String(c.folderId) !== f.id) : [...d.collections, { folderId: f.id, title: f.name, description: '' }]
}

const toggleIn = (list: (string | number)[], id: string | number) => {
  const i = list.map(String).indexOf(String(id))
  if (i >= 0) list.splice(i, 1)
  else list.push(id)
}

// ── 地址 ──

const handleState = ref<{ ok: boolean; text: string }>({ ok: true, text: '' })
let handleTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => draft.value?.handle,
  (h) => {
    if (h === undefined) return
    clearTimeout(handleTimer)
    const local = handleProblem(h)
    if (local) {
      handleState.value = { ok: false, text: local }
      return
    }
    if (h === saved.value?.handle) {
      handleState.value = { ok: true, text: '' }
      return
    }
    handleTimer = setTimeout(async () => {
      const r = await checkHandle(h).catch(() => ({ available: true, reason: '' }))
      handleState.value = r.available ? { ok: true, text: '可用' } : { ok: false, text: r.reason ?? '已被占用' }
    }, 300)
  },
)
const host = location.host
const publicUrl = computed(() => `${host}/@${draft.value?.handle ?? ''}`)

// ── 预览与保存 ──

const preview = ref<PublicPage | null>(null)
const previewing = ref(false)
const openPreview = async () => {
  if (!draft.value) return
  previewing.value = true
  try {
    preview.value = await buildPublicPage(draft.value, auth.displayName || '我')
  } catch (error) {
    toast.error(errorText(error, '预览失败'))
  } finally {
    previewing.value = false
  }
}

const save = async () => {
  if (!draft.value || !handleState.value.ok) return
  saving.value = true
  try {
    // 草稿是响应式对象，发出去之前转成纯数据
    const body = JSON.parse(JSON.stringify(draft.value)) as PublicProfile
    saved.value = await saveProfile(body)
    draft.value = JSON.parse(JSON.stringify(saved.value)) as PublicProfile
    toast.ok(saved.value.enabled ? '已保存，公开主页已更新' : '已保存（公开主页是关闭的）')
  } catch (error) {
    toast.error(errorText(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (!dirty.value) return true
  return confirm({ title: '公开设置还没保存', message: '离开后这次的修改会丢失。', confirmText: '不保存，离开', danger: true })
})

const NEVER = ['随手记、日记、摘录批注', '任务、会议记录、周报', '记账、人物卡、习惯明细', '文件柜（只能单独生成分享链接）', '未放进公开合集的书签']

onMounted(load)
</script>

<template>
  <div class="share page">
    <StateBlock v-if="loading" state="loading" />
    <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

    <div v-else-if="draft" class="ps">
      <section>
        <header class="head">
          <h1 class="page-title">公开主页</h1>
          <ToggleSwitch v-model="draft.enabled" label="公开主页总开关" />
          <span class="status" :class="{ on: draft.enabled }">{{ draft.enabled ? '开启' : '关闭 · 所有内容都是私有的' }}</span>
          <span v-if="dirty" class="unsaved">未保存</span>
          <span class="head__acts">
            <button class="btn btn--ghost" type="button" :disabled="previewing" @click="openPreview"><Icon icon="lucide:eye" />以访客身份预览</button>
            <button class="btn btn--primary" type="button" :disabled="!dirty || saving || !handleState.ok" @click="save">保存</button>
          </span>
        </header>

        <p v-if="!draft.enabled" class="off-hint">关闭时 /@{{ draft.handle }} 对任何人都打不开。下面的设置可以先改好，打开开关后生效。</p>

        <div class="blocks" :class="{ dim: !draft.enabled }">
          <div v-for="(b, i) in draft.blocks" :key="b.key" class="blk" :class="{ off: !b.on, open: expanded === b.key }">
            <div class="blk__row">
              <span class="blk__order">
                <button type="button" :disabled="i === 0" :aria-label="`${BLOCKS[b.key].label}上移`" @click="move(i, -1)"><Icon icon="lucide:chevron-up" /></button>
                <button type="button" :disabled="i === draft.blocks.length - 1" :aria-label="`${BLOCKS[b.key].label}下移`" @click="move(i, 1)"><Icon icon="lucide:chevron-down" /></button>
              </span>
              <button type="button" class="blk__main" :aria-expanded="expanded === b.key" @click="expanded = expanded === b.key ? null : b.key">
                <b>{{ BLOCKS[b.key].label }}</b>
                <small>{{ BLOCKS[b.key].hint }}<template v-if="blockDetail(b.key)"> · {{ blockDetail(b.key) }}</template></small>
              </button>
              <button type="button" class="blk__edit" @click="expanded = expanded === b.key ? null : b.key">{{ expanded === b.key ? '收起' : '设置' }}</button>
              <ToggleSwitch v-model="b.on" :label="`公开${BLOCKS[b.key].label}`" />
            </div>

            <div v-if="expanded === b.key" class="blk__cfg">
              <template v-if="b.key === 'intro'">
                <textarea v-model="draft.bio" class="field__input field__input--area" rows="2" maxlength="120" placeholder="一句话介绍自己" aria-label="介绍" />
                <small class="note">昵称用账号的：{{ auth.displayName }}</small>
              </template>

              <template v-else-if="b.key === 'links'">
                <div v-for="(l, j) in draft.links" :key="j" class="link">
                  <input v-model="l.label" class="field__input link__k" type="text" placeholder="名称" maxlength="12" :aria-label="`第 ${j + 1} 个链接的名称`" />
                  <input v-model="l.url" class="field__input" type="text" placeholder="https://… 或邮箱" :aria-label="`第 ${j + 1} 个链接的地址`" />
                  <button type="button" class="btn btn--quiet" :aria-label="`删掉第 ${j + 1} 个链接`" @click="draft.links.splice(j, 1)"><Icon icon="lucide:x" /></button>
                </div>
                <button type="button" class="btn btn--ghost btn--sm" @click="draft.links.push({ label: '', url: '' })"><Icon icon="lucide:plus" />加一个</button>
                <small class="note">邮箱在公开页上要点一下才显示，防爬虫。</small>
              </template>

              <template v-else-if="b.key === 'now'">
                <textarea v-model="draft.now" class="field__input field__input--area" rows="4" placeholder="最近在做什么，一行一条" aria-label="Now" />
                <small class="note">一行一条。改了会自动记下更新日期。</small>
              </template>

              <template v-else-if="b.key === 'collections'">
                <p v-if="!folders.length" class="note">还没有书签目录，或者书签服务连不上。</p>
                <div v-for="f in folders" :key="f.id" class="fold" :style="{ paddingLeft: `${f.depth * 1.1}rem` }">
                  <label class="fold__pick">
                    <input type="checkbox" :checked="isPublic(f.id)" @change="toggleFolder(f)" />
                    <Icon icon="lucide:folder" />{{ f.name }}
                  </label>
                  <template v-for="c in draft.collections.filter((x) => String(x.folderId) === f.id)" :key="c.folderId">
                    <input v-model="c.title" class="field__input" type="text" placeholder="合集名" maxlength="20" :aria-label="`${f.name}的合集名`" />
                    <input v-model="c.description" class="field__input" type="text" placeholder="一句话介绍（可选）" maxlength="60" :aria-label="`${f.name}的介绍`" />
                  </template>
                </div>
                <small class="note">只公开目录里直接放的书签，子目录要单独勾。</small>
              </template>

              <template v-else-if="b.key === 'reading'">
                <p v-if="!reading.length" class="note">还没有读完的文章。</p>
                <label v-for="r in reading" :key="r.id" class="pick">
                  <input type="checkbox" :checked="!draft.hiddenReading.map(String).includes(String(r.id))" @change="toggleIn(draft.hiddenReading, r.id)" />
                  <span>{{ r.title }}</span><small>{{ r.doneTime ? monthDay(ymdOf(r.doneTime)) : '' }}</small>
                </label>
                <small class="note">只显示标题与原网址，不带划线和读后感。取消勾选的不公开。</small>
              </template>

              <template v-else-if="b.key === 'goals'">
                <small class="note">公开今年的目标名与进度条；存钱这类金额相关的目标不会出现。<router-link to="/goals">去看目标</router-link></small>
              </template>

              <template v-else-if="b.key === 'quotes'">
                <p v-if="!highlights.length" class="note">还没有划线。</p>
                <label v-for="h in highlights.slice(0, 20)" :key="h.id" class="pick">
                  <input type="checkbox" :checked="draft.quoteIds.map(String).includes(String(h.id))" @change="toggleIn(draft.quoteIds, h.id)" />
                  <span>{{ h.text }}</span>
                </label>
                <small class="note">只公开划线原文，批注永远不公开。</small>
              </template>
            </div>
          </div>
        </div>
      </section>

      <aside class="side">
        <div class="box surface">
          <h2>地址</h2>
          <label class="addr">
            <span>{{ host }}/@</span>
            <input v-model.trim="draft.handle" type="text" maxlength="20" aria-label="短名" />
          </label>
          <small v-if="handleState.text" :class="handleState.ok ? 'ok' : 'bad'">{{ handleState.text }}</small>
          <a v-if="saved?.enabled && !dirty" class="open" :href="`/@${saved.handle}`" target="_blank" rel="noopener">打开 {{ publicUrl }} ↗</a>
          <div class="stat3">
            <div><b>{{ saved?.stats.visits ?? 0 }}</b>近 30 天访问</div>
            <div><b>{{ saved?.stats.collectionViews ?? 0 }}</b>合集浏览</div>
            <div><b>{{ saved?.stats.imports ?? 0 }}</b>被导入</div>
          </div>
        </div>
        <div class="box surface never">
          <h2><Icon icon="lucide:shield-check" />永远不会公开</h2>
          <ul><li v-for="n in NEVER" :key="n">{{ n }}</li></ul>
          <small v-if="settings.isEnabled('people')">人物卡连「导出全部」之外的任何外发都不参与。</small>
        </div>
      </aside>
    </div>

    <!-- 访客视角预览 -->
    <Teleport to="body">
      <div v-if="preview" class="pv" role="dialog" aria-modal="true" aria-label="以访客身份预览">
        <div class="pv__bar">
          <Icon icon="lucide:eye" />
          <span class="pv__url">{{ publicUrl }}</span>
          <span class="pv__hint">访客视角 · 未登录 · {{ dirty ? '这是还没保存的草稿' : '和已保存的一致' }}</span>
          <button class="btn btn--ghost btn--sm" type="button" @click="preview = null">关闭预览</button>
          <button v-if="dirty" class="btn btn--primary btn--sm" type="button" :disabled="!handleState.ok" @click="save().then(() => (preview = null))">没问题，保存</button>
        </div>
        <div class="pv__body">
          <p v-if="!draft?.enabled" class="pv__off">总开关是关的：保存后访客打开会看到「主页不存在」。下面是打开后的样子。</p>
          <PublicView :page="preview" preview />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ps {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 19rem;
  gap: 1.25rem;
  align-items: start;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  margin-bottom: 1rem;
}

.status {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.status.on {
  color: var(--color-accent-text);
  font-weight: 700;
}

.unsaved {
  padding: 0 0.45rem;
  border-radius: 999px;
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
  font-size: 0.74rem;
}

.head__acts {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

.head__acts .btn {
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.off-hint {
  margin-bottom: 0.8rem;
  padding: 0.55rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.blocks {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.blocks.dim .blk {
  opacity: 0.75;
}

.blk {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.blk.off .blk__main b {
  color: var(--color-text-secondary);
}

.blk.open {
  border-color: color-mix(in srgb, var(--color-brand) 45%, var(--color-border));
}

.blk__row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.65rem 0.9rem 0.65rem 0.5rem;
}

.blk__order {
  display: flex;
  flex-direction: column;
}

.blk__order button {
  display: grid;
  place-items: center;
  width: 1.4rem;
  height: 1rem;
  border: 0;
  border-radius: 0.2rem;
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.blk__order button:hover:not(:disabled) {
  background: var(--color-bg-soft);
  color: var(--color-text-primary);
}

.blk__order button:disabled {
  opacity: 0.3;
  cursor: default;
}

.blk__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.blk__main b {
  font-size: 0.92rem;
}

.blk__main small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.blk__edit {
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.8rem;
  cursor: pointer;
}

.blk__cfg {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.2rem 1rem 1rem 2.4rem;
}

.note {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.note a {
  margin-left: 0.3rem;
  color: var(--color-brand);
}

.link {
  display: flex;
  gap: 0.4rem;
}

.link__k {
  width: 7rem;
  flex: none;
}

.link .btn {
  padding: 0.3rem;
}

.btn--sm {
  padding: 0.28rem 0.6rem;
  font-size: 0.8rem;
}

.blk__cfg > .btn--sm {
  align-self: flex-start;
}

.fold {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.fold__pick {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 10rem;
  font-size: 0.86rem;
  cursor: pointer;
}

.fold .field__input {
  flex: 1;
  min-width: 8rem;
  padding: 0.3rem 0.55rem;
  font-size: 0.84rem;
}

.pick {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.86rem;
  cursor: pointer;
}

.pick span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pick small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
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

.addr {
  display: flex;
  align-items: center;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  font-size: 0.86rem;
}

.addr span {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.addr input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: none;
  font-weight: 700;
}

.ok {
  color: var(--color-accent-text);
  font-size: 0.76rem;
}

.bad {
  color: var(--color-danger);
  font-size: 0.76rem;
}

.open {
  display: block;
  margin-top: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-brand);
  word-break: break-all;
}

.stat3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.4rem;
  margin-top: 0.8rem;
  text-align: center;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.stat3 b {
  display: block;
  font-size: 1.15rem;
  color: var(--color-text-primary);
}

.never ul {
  margin: 0 0 0.5rem;
  padding-left: 1.1rem;
  font-size: 0.84rem;
  line-height: 1.9;
}

.never small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.pv {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-canvas);
}

.pv__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 0.84rem;
}

.pv__url {
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--color-bg-soft);
  font-family: var(--font-mono, monospace);
  font-size: 0.78rem;
}

.pv__hint {
  flex: 1;
  color: var(--color-text-secondary);
}

.pv__body {
  flex: 1;
  overflow-y: auto;
}

.pv__off {
  margin: 1rem auto 0;
  width: min(44rem, calc(100% - 2rem));
  padding: 0.55rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
  font-size: 0.82rem;
}

@media (max-width: 960px) {
  .ps {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
  }
}
</style>
