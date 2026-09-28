<script setup lang="ts">
/**
 * 生成分享链接：默认 7 天过期、需要提取码，可限制下载次数。身份证这类敏感文件额外提醒。
 * 生成后给出「链接 + 提取码 + 有效期」一段话，直接复制发给对方；下方列出这个文件还能用的链接。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import ToggleSwitch from '../../../components/base/ToggleSwitch.vue'
import { createShare, fetchShares, revokeShare, shareUrl } from '../../../api/files'
import { errorText, toast } from '../../../composables/useToast'
import { monthDay, nowStamp, ymdOf } from '../../../utils/date'
import { formatSize, isSensitive, kindOf, KIND_META } from '../../../utils/files'
import type { FileKind, Share, SpaceFile } from '../../../types/files'

const props = defineProps<{ file: SpaceFile | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const DAYS: { value: number | null; label: string }[] = [
  { value: 1, label: '1 天' },
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
  { value: null, label: '永久' },
]
const LIMITS = [1, 3, 10]

const days = ref<number | null>(7)
const withPassword = ref(true)
const limitOn = ref(false)
const limit = ref(3)
const created = ref<Share | null>(null)
const existing = ref<Share[]>([])
const saving = ref(false)
const copied = ref(false)

/** 还能用的链接：没取消、没过期、次数没用完 */
const usable = (s: Share) => !s.revoked && (!s.expireAt || s.expireAt >= nowStamp()) && (s.maxDownloads === null || s.downloads < s.maxDownloads)

watch(
  () => props.file,
  async (file) => {
    if (!file) return
    days.value = 7
    withPassword.value = true
    limitOn.value = isSensitive(file.name)
    limit.value = isSensitive(file.name) ? 1 : 3
    created.value = null
    copied.value = false
    existing.value = []
    try {
      existing.value = (await fetchShares()).filter((s) => String(s.fileId) === String(file.id) && usable(s))
    } catch {
      existing.value = []
    }
  },
  { immediate: true },
)

const sensitive = computed(() => Boolean(props.file && isSensitive(props.file.name)))
const KIND_ICON: Record<FileKind, string> = {
  pdf: 'lucide:file-text',
  image: 'lucide:image',
  doc: 'lucide:file-text',
  sheet: 'lucide:sheet',
  zip: 'lucide:file-archive',
  other: 'lucide:file',
}
const kind = computed(() => kindOf(props.file?.name ?? '', props.file?.mime))
const color = computed(() => (props.file?.isFolder ? '#f59e0b' : KIND_META[kind.value].color))
const icon = computed(() => (props.file?.isFolder ? 'lucide:folder' : KIND_ICON[kind.value]))

const expireText = (s: Share) => (s.expireAt ? `${monthDay(ymdOf(s.expireAt))}前有效` : '永久有效')
const message = (s: Share) => `${shareUrl(s.code)}${s.password ? ` 提取码 ${s.password}，` : ' '}${expireText(s)}`

const create = async () => {
  if (!props.file) return
  saving.value = true
  try {
    created.value = await createShare({ fileId: props.file.id, days: days.value, withPassword: withPassword.value, maxDownloads: limitOn.value ? limit.value : null })
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '生成失败'))
  } finally {
    saving.value = false
  }
}

const copy = async (s: Share) => {
  try {
    await navigator.clipboard.writeText(message(s))
    if (created.value?.id === s.id) {
      copied.value = true
      setTimeout(() => (copied.value = false), 2000)
    } else {
      toast.ok('已复制链接')
    }
  } catch {
    toast.error('复制失败，请手动选中复制')
  }
}

const revoke = async (s: Share) => {
  try {
    await revokeShare(s.id)
    existing.value = existing.value.filter((x) => x.id !== s.id)
    toast.ok('链接已失效，拿到它的人打不开了')
    emit('changed')
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}
</script>

<template>
  <BaseDialog :open="Boolean(file)" :title="file?.isFolder ? '分享文件夹' : '分享文件'" width="31rem" :locked="saving" @close="emit('close')">
    <div v-if="file" class="sd">
      <div class="target">
        <span class="target__icon" :style="{ '--k': color }"><Icon :icon="icon" /></span>
        <span class="target__text">
          <b :title="file.name">{{ file.name }}</b>
          <small>{{ file.isFolder ? '对方下载到的是整个文件夹的压缩包' : formatSize(file.size) }}</small>
        </span>
      </div>

      <p v-if="sensitive && !created" class="warn"><Icon icon="lucide:shield-alert" /><span>这像是证件类的敏感文件。建议只发给需要的人，设短一点的有效期，并限制下载 1 次。</span></p>

      <!-- 设置 -->
      <div v-if="!created" class="opts">
        <div class="opt">
          <span class="opt__text"><b>有效期</b><small>到期那天 23:59 失效</small></span>
          <div class="seg" role="radiogroup" aria-label="有效期">
            <button v-for="d in DAYS" :key="String(d.value)" type="button" role="radio" :aria-checked="days === d.value" :class="{ on: days === d.value }" @click="days = d.value">{{ d.label }}</button>
          </div>
        </div>
        <div class="opt">
          <span class="opt__text"><b>提取码</b><small>{{ withPassword ? '生成 4 位提取码，随链接一起发给对方' : '拿到链接的人都能下载' }}</small></span>
          <ToggleSwitch v-model="withPassword" label="需要提取码" />
        </div>
        <div class="opt">
          <span class="opt__text"><b>限制下载次数</b><small>{{ limitOn ? `下载 ${limit} 次后自动失效` : '不限次数' }}</small></span>
          <ToggleSwitch v-model="limitOn" label="限制下载次数" />
        </div>
        <div v-if="limitOn" class="opt opt--sub">
          <span class="opt__text"><small>最多</small></span>
          <div class="seg" role="radiogroup" aria-label="最多下载次数">
            <button v-for="n in LIMITS" :key="n" type="button" role="radio" :aria-checked="limit === n" :class="{ on: limit === n }" @click="limit = n">{{ n }} 次</button>
          </div>
        </div>
      </div>

      <!-- 生成结果 -->
      <div v-else class="done">
        <p class="done__ok"><Icon icon="lucide:circle-check" />链接已生成，{{ expireText(created) }}{{ created.maxDownloads ? `，可下载 ${created.maxDownloads} 次` : '' }}</p>
        <dl class="done__box">
          <dt>链接</dt>
          <dd><code>{{ shareUrl(created.code) }}</code></dd>
          <template v-if="created.password">
            <dt>提取码</dt>
            <dd><b class="done__pw">{{ created.password }}</b></dd>
          </template>
        </dl>
        <p class="done__hint">点「复制」会连同提取码和有效期一起复制，直接粘贴发给对方即可。</p>
      </div>

      <!-- 已有的链接 -->
      <section v-if="existing.length && !created" class="exist">
        <h4>这个{{ file.isFolder ? '文件夹' : '文件' }}还有 {{ existing.length }} 个能用的链接</h4>
        <ul>
          <li v-for="s in existing" :key="s.id" class="exist__row">
            <Icon icon="lucide:link" />
            <span class="exist__main">
              <code>/s/{{ s.code }}</code>
              <small>{{ expireText(s) }} · 已下载 {{ s.downloads }} 次{{ s.password ? ' · 有提取码' : '' }}</small>
            </span>
            <button type="button" class="btn btn--quiet btn--xs" @click="copy(s)">复制</button>
            <button type="button" class="btn btn--quiet btn--xs exist__off" @click="revoke(s)">失效</button>
          </li>
        </ul>
      </section>
    </div>

    <template #footer>
      <template v-if="!created">
        <button class="btn btn--ghost" type="button" :disabled="saving" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="button" :disabled="saving" @click="create">
          <Icon :icon="saving ? 'lucide:loader-circle' : 'lucide:link'" :class="{ spin: saving }" />生成链接
        </button>
      </template>
      <template v-else>
        <span v-if="copied" class="copied" role="status"><Icon icon="lucide:check" />已复制</span>
        <button class="btn btn--ghost" type="button" @click="emit('close')">完成</button>
        <button class="btn btn--primary" type="button" @click="copy(created)"><Icon icon="lucide:copy" />复制{{ created.password ? '链接和提取码' : '链接' }}</button>
      </template>
    </template>
  </BaseDialog>
</template>

<style scoped>
.sd {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.2rem 1.35rem 0.35rem;
}

/* 分享的是哪个文件 */
.target {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.target__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--k) 14%, transparent);
  color: var(--k);
}

.target__icon svg {
  width: 1.25rem;
  height: 1.25rem;
}

.target__text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.target__text b {
  overflow: hidden;
  font-size: 0.95rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target__text small {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.warn {
  display: flex;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, #d97706 11%, transparent);
  color: #b45309;
  font-size: 0.82rem;
  line-height: 1.6;
}

.warn svg {
  flex: none;
  width: 1rem;
  height: 1rem;
  margin-top: 0.2rem;
}

/* 设置项：一组带分隔线的行 */
.opts {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0.9rem;
}

.opt + .opt {
  border-top: 1px solid var(--color-border);
}

.opt--sub {
  padding-top: 0;
  border-top: 0 !important;
}

.opt__text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.opt__text b {
  font-size: 0.88rem;
  font-weight: 600;
}

.opt__text small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

/* 分段选择 */
.seg {
  display: inline-flex;
  flex: none;
  padding: 0.15rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  min-width: 2.9rem;
  padding: 0.28rem 0.6rem;
  border: 0;
  border-radius: calc(var(--radius-md) - 2px);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.seg button:hover {
  color: var(--color-text-primary);
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-brand);
  box-shadow: var(--shadow-sm);
}

.seg button:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 1px;
}

/* 生成结果 */
.done {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.done__ok {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-accent-text);
  font-size: 0.88rem;
  font-weight: 600;
}

.done__ok svg {
  width: 1.05rem;
  height: 1.05rem;
}

.done__box {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.5rem 0.9rem;
  align-items: baseline;
  margin: 0;
  padding: 0.8rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-soft);
}

.done__box dt {
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.done__box dd {
  margin: 0;
}

.done__box code {
  font-family: var(--font-mono, monospace);
  font-size: 0.86rem;
  word-break: break-all;
}

.done__pw {
  font-family: var(--font-mono, monospace);
  font-size: 1.05rem;
  letter-spacing: 0.12em;
  color: var(--color-brand);
}

.done__hint {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

/* 已有的链接 */
.exist h4 {
  margin-bottom: 0.4rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.exist ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.exist__row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.45rem 0.2rem;
  font-size: 0.8rem;
}

.exist__row + .exist__row {
  border-top: 1px dashed var(--color-border);
}

.exist__row > svg {
  flex: none;
  color: var(--color-text-secondary);
}

.exist__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.exist code {
  font-family: var(--font-mono, monospace);
  font-size: 0.82rem;
}

.exist small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.btn--xs {
  padding: 0.22rem 0.55rem;
  font-size: 0.78rem;
}

.exist__off:not(:disabled):hover {
  color: var(--color-danger);
}

.copied {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-right: auto;
  color: var(--color-accent-text);
  font-size: 0.84rem;
  font-weight: 600;
}

@media (max-width: 480px) {
  .opt {
    flex-wrap: wrap;
  }
}
</style>
