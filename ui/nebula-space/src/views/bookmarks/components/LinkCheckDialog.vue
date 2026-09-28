<script setup lang="ts">
/**
 * 链接检查：由服务器逐个访问书签网址，打不开的标为失效，失效的又能打开时恢复正常。
 * 前端分批提交（每批 16 条、两批并行）显示进度，随时可以停；已经查过的结果立即生效。
 * 超时、内网地址这类查不清的不改状态，只在结果里列出来。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import { checkBookmarkLinks, countBookmarks, fetchAllBookmarks } from '../../../api/space'
import { errorText } from '../../../composables/useToast'
import { BookmarkStatus, type Bookmark, type LinkCheckResult } from '../../../types/space'

type Scope = 'all' | 'broken'

const BATCH = 16
const WORKERS = 2

const props = defineProps<{ open: boolean; initialScope?: Scope }>()
const emit = defineEmits<{
  close: []
  /** 查过了（状态、检查时间或原因有变），列表需要刷新 */
  changed: []
  viewBroken: []
}>()

const phase = ref<'ready' | 'running' | 'done'>('ready')
const scope = ref<Scope>('all')
const counts = ref<{ normal: number; broken: number } | null>(null)
const loadError = ref('')

const total = ref(0)
const checked = ref(0)
/** 这次判为打不开的（含之前就失效的），fresh = 这次才变成失效 */
const dead = ref<{ bookmark: Bookmark; reason: string; fresh: boolean }[]>([])
const freshDead = computed(() => dead.value.filter((d) => d.fresh).length)
const recovered = ref(0)
const unsure = ref<{ bookmark: Bookmark; reason: string }[]>([])
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
const percent = computed(() => (total.value ? Math.round((checked.value / total.value) * 100) : 0))

const tally = (results: LinkCheckResult[], byId: Map<string, Bookmark>) => {
  for (const r of results) {
    const bookmark = byId.get(String(r.id))
    if (!bookmark) continue
    if (r.verdict === 'dead') dead.value.push({ bookmark, reason: r.reason ?? '', fresh: r.changed })
    else if (r.verdict === 'alive' && r.changed) recovered.value += 1
    else if (r.verdict === 'unknown') unsure.value.push({ bookmark, reason: r.reason ?? '无法确定' })
  }
}

const start = async () => {
  phase.value = 'running'
  stopRequested = false
  stopped.value = false
  checked.value = 0
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
  stopped.value = stopRequested
  phase.value = 'done'
  // 状态没变的也更新了检查时间和原因，列表照样要刷新
  if (checked.value) emit('changed')
}

const stop = () => {
  stopRequested = true
}

// 页面被切走时别在后台接着查
onBeforeUnmount(stop)

const showUnsure = ref(false)
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
          由服务器逐个访问，每 100 条大约十几秒。只有「域名不存在」「连接被拒绝」「页面不存在」算失效；
          超时、内网地址这类查不清的不会改动。
        </p>
      </template>

      <template v-else>
        <div class="lc__progress" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
          <i :style="{ width: `${percent}%` }" />
        </div>
        <p class="lc__muted">
          <Icon v-if="phase === 'running'" icon="lucide:loader-circle" class="spin" />
          <template v-if="phase === 'running'">已检查 {{ checked }} / {{ total }}</template>
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

        <template v-if="phase === 'done' && unsure.length">
          <button type="button" class="lc__toggle" :aria-expanded="showUnsure" @click="showUnsure = !showUnsure">
            <Icon :icon="showUnsure ? 'lucide:chevron-down' : 'lucide:chevron-right'" />{{ unsure.length }} 条查不清，状态没动
          </button>
          <ul v-if="showUnsure" class="lc__list lc__list--quiet">
            <li v-for="item in unsure.slice(0, 50)" :key="item.bookmark.id">
              <span class="lc__title">{{ item.bookmark.title }}</span>
              <span class="lc__reason">{{ item.reason }}</span>
            </li>
            <li v-if="unsure.length > 50" class="lc__more">还有 {{ unsure.length - 50 }} 条</li>
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
        <button class="btn btn--ghost" type="button" @click="emit('close')">关闭</button>
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
