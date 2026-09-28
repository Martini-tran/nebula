<script setup lang="ts">
/**
 * 链接检查：由服务器逐个访问书签网址，打不开的标为失效，失效的又能打开时恢复正常。
 * 前端分批提交（每批 16 条、两批并行）显示进度，随时可以停；已经查过的结果立即生效。
 *
 * 服务器查不清的（超时、连接被重置等，多是境外站点从服务器上访问不到），再用用户自己的浏览器探一次：
 * 浏览器连得上的说明站点还在；两边都连不上的列出来，由用户勾选后标为失效。
 * 实测带 Cloudflare 人机验证等防护的站点在浏览器探测里也会报错，和真打不开的分不出来，所以默认不勾选。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { checkBookmarkLinks, countBookmarks, fetchAllBookmarks, updateBookmarkStatus } from '../../../api/space'
import { errorText } from '../../../composables/useToast'
import { canProbeInBrowser, probeInBrowser } from '../browserProbe'
import { BookmarkStatus, type Bookmark, type LinkCheckResult } from '../../../types/space'

type Scope = 'all' | 'broken'

const BATCH = 16
const WORKERS = 2
/** 浏览器同时探测的网址数 */
const PROBES = 8

interface Unsure {
  bookmark: Bookmark
  reason: string
  /** 浏览器复查：ok 连得上 / fail 也连不上 / skip 不适合用浏览器探（内网、https 页面里的 http 地址等） */
  browser: 'pending' | 'ok' | 'fail' | 'skip'
}

const props = defineProps<{ open: boolean; initialScope?: Scope }>()
const emit = defineEmits<{
  close: []
  /** 查过了（状态、检查时间或原因有变），列表需要刷新 */
  changed: []
  viewBroken: []
}>()

const phase = ref<'ready' | 'running' | 'done'>('ready')
/** running 时在哪一步：服务器检查，或浏览器复查 */
const stage = ref<'server' | 'browser'>('server')
const scope = ref<Scope>('all')
const counts = ref<{ normal: number; broken: number } | null>(null)
const loadError = ref('')

const total = ref(0)
const checked = ref(0)
/** 这次判为打不开的（含之前就失效的），fresh = 这次才变成失效 */
const dead = ref<{ bookmark: Bookmark; reason: string; fresh: boolean }[]>([])
const freshDead = computed(() => dead.value.filter((d) => d.fresh).length)
const recovered = ref(0)
const unsure = ref<Unsure[]>([])
const probed = ref(0)
const probeTotal = ref(0)
const failedBatches = ref(0)
const lastError = ref('')
const stopped = ref(false)
let stopRequested = false

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    phase.value = 'ready'
    scope.value = props.initialScope ?? 'all'
    counts.value = null
    loadError.value = ''
    try {
      const [normal, broken] = await Promise.all([
        countBookmarks({ status: BookmarkStatus.NORMAL }),
        countBookmarks({ status: BookmarkStatus.BROKEN }),
      ])
      counts.value = { normal, broken }
    } catch (error) {
      loadError.value = errorText(error, '统计书签失败')
    }
  },
)

const scopeCount = computed(() =>
  counts.value ? (scope.value === 'all' ? counts.value.normal + counts.value.broken : counts.value.broken) : 0,
)
const percent = computed(() => {
  const [done, all] = stage.value === 'browser' ? [probed.value, probeTotal.value] : [checked.value, total.value]
  return all ? Math.round((done / all) * 100) : 0
})

/** 服务器和浏览器都连不上、目前还是正常状态的：嫌疑失效，等用户确认 */
const suspects = computed(() =>
  unsure.value.filter((u) => u.browser === 'fail' && u.bookmark.status === BookmarkStatus.NORMAL),
)
const reachable = computed(() => unsure.value.filter((u) => u.browser === 'ok'))
const undecided = computed(() => unsure.value.filter((u) => !suspects.value.includes(u) && u.browser !== 'ok'))

const tally = (results: LinkCheckResult[], byId: Map<string, Bookmark>) => {
  for (const r of results) {
    const bookmark = byId.get(String(r.id))
    if (!bookmark) continue
    if (r.verdict === 'dead') dead.value.push({ bookmark, reason: r.reason ?? '', fresh: r.changed })
    else if (r.verdict === 'alive' && r.changed) recovered.value += 1
    else if (r.verdict === 'unknown') unsure.value.push({ bookmark, reason: r.reason ?? '无法确定', browser: 'pending' })
  }
}

const start = async () => {
  phase.value = 'running'
  stage.value = 'server'
  stopRequested = false
  stopped.value = false
  checked.value = 0
  marked.value = 0
  dead.value = []
  recovered.value = 0
  unsure.value = []
  failedBatches.value = 0
  lastError.value = ''
  let list: Bookmark[]
  try {
    list =
      scope.value === 'all'
        ? [
            ...(await fetchAllBookmarks({ status: BookmarkStatus.NORMAL })),
            ...(await fetchAllBookmarks({ status: BookmarkStatus.BROKEN })),
          ]
        : await fetchAllBookmarks({ status: BookmarkStatus.BROKEN })
  } catch (error) {
    lastError.value = errorText(error, '读取书签失败')
    phase.value = 'done'
    return
  }
  total.value = list.length
  const byId = new Map(list.map((b) => [String(b.id), b]))
  const queue: Bookmark[][] = []
  for (let i = 0; i < list.length; i += BATCH) queue.push(list.slice(i, i + BATCH))

  const worker = async () => {
    while (!stopRequested && queue.length) {
      const batch = queue.shift()!
      try {
        tally(await checkBookmarkLinks(batch.map((b) => b.id)), byId)
      } catch (error) {
        failedBatches.value += 1
        lastError.value = errorText(error, '检查失败')
      }
      checked.value += batch.length
    }
  }
  await Promise.all(Array.from({ length: WORKERS }, worker))
  // 状态没变的也更新了检查时间和原因，列表照样要刷新
  if (checked.value) emit('changed')
  if (!stopRequested) await probeUnsure()
  stopped.value = stopRequested
  phase.value = 'done'
  picked.value = new Set()
}

/** 服务器查不清的，用浏览器再探一次 */
const probeUnsure = async () => {
  const queue = unsure.value.filter((u) => {
    if (canProbeInBrowser(u.bookmark.url)) return true
    u.browser = 'skip'
    return false
  })
  if (!queue.length) return
  stage.value = 'browser'
  probed.value = 0
  probeTotal.value = queue.length
  const worker = async () => {
    while (!stopRequested && queue.length) {
      const item = queue.shift()!
      item.browser = (await probeInBrowser(item.bookmark.url)) ? 'ok' : 'fail'
      probed.value += 1
    }
  }
  await Promise.all(Array.from({ length: PROBES }, worker))
  // 停在半路的，没探到的按不适合处理
  unsure.value.filter((u) => u.browser === 'pending').forEach((u) => (u.browser = 'skip'))
}

// ── 嫌疑失效：用户确认后标为失效 ──

const picked = ref(new Set<string>())
const marking = ref(false)
const marked = ref(0)
const markError = ref('')

const pickedCount = computed(() => suspects.value.filter((u) => picked.value.has(String(u.bookmark.id))).length)
const allPicked = computed(() => suspects.value.length > 0 && pickedCount.value === suspects.value.length)
const togglePickAll = () => {
  picked.value = allPicked.value ? new Set() : new Set(suspects.value.map((u) => String(u.bookmark.id)))
}

const togglePick = (id: string) => {
  const next = new Set(picked.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  picked.value = next
}

const markDead = async () => {
  const items = suspects.value.filter((u) => picked.value.has(String(u.bookmark.id)))
  if (!items.length || marking.value) return
  marking.value = true
  markError.value = ''
  const done: Unsure[] = []
  for (let i = 0; i < items.length; i += 5) {
    const chunk = items.slice(i, i + 5)
    const results = await Promise.allSettled(chunk.map((u) => updateBookmarkStatus(u.bookmark.id, BookmarkStatus.BROKEN)))
    results.forEach((r, j) => {
      if (r.status === 'fulfilled') done.push(chunk[j]!)
      else markError.value ||= errorText(r.reason, '标记失败')
    })
  }
  // 挪进「打不开」的列表
  dead.value = [...dead.value, ...done.map((u) => ({ bookmark: u.bookmark, reason: `${u.reason}，浏览器也连不上`, fresh: true }))]
  unsure.value = unsure.value.filter((u) => !done.includes(u))
  marked.value += done.length
  marking.value = false
  if (done.length) emit('changed')
}

const stop = () => {
  stopRequested = true
}

// 页面被切走时别在后台接着查
onBeforeUnmount(stop)

const showReachable = ref(false)
const showUndecided = ref(false)
</script>

<template>
  <BaseDialog :open="open" title="检查链接" width="34rem" :locked="phase === 'running'" @close="emit('close')">
    <div class="lc">
      <template v-if="phase === 'ready'">
        <p v-if="loadError" class="form__error">{{ loadError }}</p>
        <p v-else-if="!counts" class="lc__muted"><Icon icon="lucide:loader-circle" class="spin" />正在统计书签…</p>
        <fieldset v-else class="lc__opts">
          <legend>检查哪些书签？</legend>
          <label class="opt" :class="{ on: scope === 'all' }">
            <input v-model="scope" type="radio" value="all" />
            <span>
              <b>全部书签（{{ counts.normal + counts.broken }} 条）</b>
              <small>正常的和已失效的都查一遍；已归档的不查。</small>
            </span>
          </label>
          <label class="opt" :class="{ on: scope === 'broken' }">
            <input v-model="scope" type="radio" value="broken" :disabled="!counts.broken" />
            <span>
              <b>只复查失效的（{{ counts.broken }} 条）</b>
              <small>看看之前打不开的站点是不是恢复了，恢复的改回正常。</small>
            </span>
          </label>
        </fieldset>
        <p class="lc__muted lc__note">
          由服务器逐个访问，每 100 条大约十几秒到半分钟。域名不存在、连接被拒绝、页面不存在、源站宕机、证书过期的直接标为失效；
          服务器连不上的再用你的浏览器试一次，两边都打不开的列出来由你确认。
        </p>
      </template>

      <template v-else>
        <div class="lc__progress" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
          <i :style="{ width: `${percent}%` }" />
        </div>
        <p class="lc__muted">
          <Icon v-if="phase === 'running'" icon="lucide:loader-circle" class="spin" />
          <template v-if="phase === 'running' && stage === 'server'">服务器检查 {{ checked }} / {{ total }}</template>
          <template v-else-if="phase === 'running'">服务器连不上的，用你的浏览器复查 {{ probed }} / {{ probeTotal }}</template>
          <template v-else-if="stopped">已停止，检查了 {{ checked }} / {{ total }} 条</template>
          <template v-else>检查完成，共 {{ total }} 条</template>
        </p>
        <div class="lc__stats">
          <span class="lc__stat lc__stat--dead"><b>{{ dead.length }}</b>打不开{{ freshDead ? `（新发现 ${freshDead}）` : '' }}</span>
          <span class="lc__stat"><b>{{ recovered }}</b>恢复正常</span>
          <span class="lc__stat"><b>{{ unsure.length }}</b>查不清</span>
        </div>

        <ul v-if="dead.length" class="lc__list" aria-label="打不开的链接">
          <li v-for="item in dead.slice(0, 50)" :key="item.bookmark.id">
            <span class="lc__title">
              <em v-if="item.fresh" class="lc__new">新</em>{{ item.bookmark.title }}
            </span>
            <span class="lc__reason">{{ item.reason }}</span>
          </li>
          <li v-if="dead.length > 50" class="lc__more">还有 {{ dead.length - 50 }} 条</li>
        </ul>

        <p v-if="marked" class="lc__marked"><Icon icon="lucide:check" />已把 {{ marked }} 条标为失效</p>

        <section v-if="phase === 'done' && suspects.length" class="lc__suspects">
          <header>
            <b>服务器和你的浏览器都连不上 · {{ suspects.length }} 条</b>
            <small>可能已经失效，也可能是站点的防护拦下了探测（Cloudflare 人机验证的站点常这样）。点开确认后勾选，再标为失效。</small>
          </header>
          <ul class="lc__list lc__list--pick" aria-label="疑似失效的链接">
            <li v-for="item in suspects" :key="item.bookmark.id">
              <label class="lc__pick">
                <input
                  type="checkbox"
                  :checked="picked.has(String(item.bookmark.id))"
                  :disabled="marking"
                  @change="togglePick(String(item.bookmark.id))"
                />
                <span class="lc__title">{{ item.bookmark.title }}</span>
              </label>
              <span class="lc__reason lc__reason--quiet">{{ item.reason }}</span>
              <a class="lc__open" :href="item.bookmark.url" target="_blank" rel="noopener noreferrer" title="打开看看">
                <Icon icon="lucide:external-link" />
              </a>
            </li>
          </ul>
          <div class="lc__suspects-foot">
            <button type="button" class="lc__all" :disabled="marking" @click="togglePickAll">{{ allPicked ? '全不选' : '全选' }}</button>
            <p v-if="markError" class="form__error">{{ markError }}</p>
            <button class="btn btn--ghost btn--sm" type="button" :disabled="marking || !pickedCount" @click="markDead">
              <Icon :icon="marking ? 'lucide:loader-circle' : 'lucide:link-2-off'" :class="{ spin: marking }" />标为失效（{{ pickedCount }} 条）
            </button>
          </div>
        </section>

        <template v-if="phase === 'done' && reachable.length">
          <button type="button" class="lc__toggle" :aria-expanded="showReachable" @click="showReachable = !showReachable">
            <Icon :icon="showReachable ? 'lucide:chevron-down' : 'lucide:chevron-right'" />{{ reachable.length }} 条你的浏览器连得上，多半只是服务器那边访问不到，状态没动
          </button>
          <ul v-if="showReachable" class="lc__list lc__list--quiet">
            <li v-for="item in reachable.slice(0, 50)" :key="item.bookmark.id">
              <span class="lc__title">{{ item.bookmark.title }}</span>
              <span class="lc__reason">服务器：{{ item.reason }}</span>
            </li>
            <li v-if="reachable.length > 50" class="lc__more">还有 {{ reachable.length - 50 }} 条</li>
          </ul>
        </template>

        <template v-if="phase === 'done' && undecided.length">
          <button type="button" class="lc__toggle" :aria-expanded="showUndecided" @click="showUndecided = !showUndecided">
            <Icon :icon="showUndecided ? 'lucide:chevron-down' : 'lucide:chevron-right'" />{{ undecided.length }} 条查不清，状态没动
          </button>
          <ul v-if="showUndecided" class="lc__list lc__list--quiet">
            <li v-for="item in undecided.slice(0, 50)" :key="item.bookmark.id">
              <span class="lc__title">{{ item.bookmark.title }}</span>
              <span class="lc__reason">{{ item.reason }}</span>
            </li>
            <li v-if="undecided.length > 50" class="lc__more">还有 {{ undecided.length - 50 }} 条</li>
          </ul>
        </template>

        <p v-if="failedBatches" class="form__error">{{ failedBatches * BATCH }} 条左右没检查成功：{{ lastError }}</p>
        <p v-else-if="lastError" class="form__error">{{ lastError }}</p>
      </template>
    </div>

    <template #footer>
      <template v-if="phase === 'ready'">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="button" :disabled="!scopeCount" @click="start">
          <Icon icon="lucide:radar" />开始检查
        </button>
      </template>
      <button v-else-if="phase === 'running'" class="btn btn--ghost" type="button" @click="stop">
        <Icon icon="lucide:square" />停止
      </button>
      <template v-else>
        <button class="btn btn--ghost" type="button" :disabled="marking" @click="emit('close')">关闭</button>
        <button v-if="dead.length" class="btn btn--primary" type="button" @click="emit('viewBroken')">
          查看失效链接
        </button>
      </template>
    </template>
  </BaseDialog>
</template>

<style scoped>
.lc {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.1rem 1.35rem 0.25rem;
  font-size: 0.92rem;
}

.lc__muted {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--color-text-secondary);
}

.lc__note {
  font-size: 0.82rem;
  line-height: 1.65;
}

.lc__opts {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.lc__opts legend {
  margin-bottom: 0.45rem;
  font-weight: 600;
}

.opt {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.opt input {
  margin-top: 0.25rem;
  accent-color: var(--color-brand);
}

.opt span {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.opt small {
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.opt.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.lc__progress {
  height: 0.45rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.lc__progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 0.3s ease;
}

.lc__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.lc__stat {
  display: flex;
  flex-direction: column;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.lc__stat b {
  font-size: 1.3rem;
  color: var(--color-text-primary);
}

.lc__stat--dead b {
  color: var(--color-danger);
}

.lc__list {
  display: flex;
  flex-direction: column;
  max-height: 14rem;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  list-style: none;
}

.lc__list li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.45rem 0.7rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.lc__list li:last-child {
  border-bottom: 0;
}

.lc__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lc__new {
  margin-right: 0.35rem;
  padding: 0 0.3rem;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 700;
}

.lc__reason {
  flex: none;
  font-size: 0.78rem;
  color: var(--color-danger);
}

.lc__list--quiet .lc__reason {
  color: var(--color-text-secondary);
}

.lc__more {
  justify-content: center !important;
  color: var(--color-text-secondary);
}

.lc__marked {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.86rem;
  color: var(--color-brand);
}

.lc__suspects {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
  border-radius: var(--radius-md);
}

.lc__suspects header {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.lc__suspects header small {
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.lc__list--pick {
  max-height: 12rem;
}

.lc__list--pick li {
  align-items: center;
}

.lc__pick {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  cursor: pointer;
}

.lc__pick input {
  flex: none;
  accent-color: var(--color-danger);
}

.lc__reason--quiet {
  color: var(--color-text-secondary);
}

.lc__open {
  display: inline-grid;
  flex: none;
  place-items: center;
  color: var(--color-text-secondary);
}

.lc__open:hover {
  color: var(--color-brand);
}

.lc__suspects-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.lc__suspects-foot .form__error {
  flex: 1;
}

.lc__all {
  margin-right: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
}

.btn--sm {
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
}

.lc__toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  cursor: pointer;
}

.lc__toggle:hover {
  color: var(--color-text-primary);
}
</style>
