<script setup lang="ts">
/**
 * 生成分享链接：默认 7 天过期、需要提取码，可限制下载次数。身份证这类敏感文件额外提醒。
 * 生成后给出「链接 + 提取码 + 有效期」一段话，直接复制发给对方。
 */
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import BaseDialog from '../../../components/base/BaseDialog.vue'
import ToggleSwitch from '../../../components/base/ToggleSwitch.vue'
import { createShare, fetchShares, revokeShare, shareUrl } from '../../../api/files'
import { errorText, toast } from '../../../composables/useToast'
import { monthDay, ymdOf } from '../../../utils/date'
import { isSensitive } from '../../../utils/files'
import type { Share, SpaceFile } from '../../../types/files'

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
    try {
      existing.value = (await fetchShares()).filter((s) => String(s.fileId) === String(file.id) && !s.revoked)
    } catch {
      existing.value = []
    }
  },
  { immediate: true },
)

const sensitive = computed(() => Boolean(props.file && isSensitive(props.file.name)))

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
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
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
  <BaseDialog :open="Boolean(file)" :title="`分享「${file?.name ?? ''}」`" width="30rem" @close="emit('close')">
    <div v-if="file" class="sd">
      <p class="sd__sub">拿到链接{{ withPassword ? '和提取码' : '' }}的人可以查看和下载{{ file.isFolder ? '这个文件夹里的文件' : '' }}。</p>

      <p v-if="sensitive" class="warn"><Icon icon="lucide:shield-alert" />这像是证件类的敏感文件。建议只给需要的人、设短有效期并限制下载 1 次。</p>

      <template v-if="!created">
        <div class="row row--col">
          <span>有效期</span>
          <div class="chips" role="radiogroup" aria-label="有效期">
            <button v-for="d in DAYS" :key="String(d.value)" type="button" role="radio" :aria-checked="days === d.value" :class="{ on: days === d.value }" @click="days = d.value">{{ d.label }}</button>
          </div>
        </div>
        <div class="row">
          <span>需要提取码<small>生成后随链接一起给对方</small></span>
          <ToggleSwitch v-model="withPassword" label="需要提取码" />
        </div>
        <div class="row">
          <span>最多下载次数</span>
          <span class="row__r">
            <span v-if="limitOn" class="chips" role="radiogroup" aria-label="最多下载次数">
              <button v-for="n in LIMITS" :key="n" type="button" role="radio" :aria-checked="limit === n" :class="{ on: limit === n }" @click="limit = n">{{ n }} 次</button>
            </span>
            <ToggleSwitch v-model="limitOn" label="限制下载次数" />
          </span>
        </div>
      </template>

      <div v-else class="done">
        <div class="url">
          <code>{{ shareUrl(created.code) }}</code>
          <b v-if="created.password">提取码 {{ created.password }}</b>
        </div>
        <p class="meta">复制内容：{{ message(created) }}</p>
      </div>

      <div v-if="existing.length && !created" class="exist">
        <h4>这个文件已有的链接</h4>
        <div v-for="s in existing" :key="s.id" class="exist__row">
          <span><code>/s/{{ s.code }}</code> · {{ expireText(s) }} · 下载 {{ s.downloads }} 次</span>
          <button type="button" class="btn btn--quiet btn--sm" @click="copy(s)">复制</button>
          <button type="button" class="btn btn--ghost btn--sm" @click="revoke(s)">失效</button>
        </div>
      </div>
    </div>

    <template #footer>
      <template v-if="!created">
        <button class="btn btn--ghost" type="button" @click="emit('close')">取消</button>
        <button class="btn btn--primary" type="button" :disabled="saving" @click="create">
          <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />生成链接
        </button>
      </template>
      <template v-else>
        <span v-if="copied" class="copied"><Icon icon="lucide:check" />已复制</span>
        <button class="btn btn--ghost" type="button" @click="emit('close')">完成</button>
        <button class="btn btn--primary" type="button" @click="copy(created)"><Icon icon="lucide:copy" />复制链接{{ created.password ? '和提取码' : '' }}</button>
      </template>
    </template>
  </BaseDialog>
</template>

<style scoped>
.sd {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.sd__sub {
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.warn {
  display: flex;
  gap: 0.45rem;
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-warn-soft, #fdf3e2);
  color: var(--color-warn, #b45309);
  font-size: 0.82rem;
  line-height: 1.6;
}

.warn svg {
  flex: none;
  margin-top: 0.2rem;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  font-size: 0.88rem;
}

.row--col {
  flex-direction: column;
  align-items: flex-start;
  gap: 0.45rem;
}

.row > span:first-child {
  display: flex;
  flex-direction: column;
}

.row small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.row__r {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.chips {
  display: flex;
  gap: 0.3rem;
}

.chips button {
  padding: 0.2rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  font-size: 0.82rem;
  cursor: pointer;
}

.chips button.on {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.done .url {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.8rem;
  padding: 0.7rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.done code {
  font-family: var(--font-mono, monospace);
  font-size: 0.84rem;
  word-break: break-all;
}

.done b {
  font-family: var(--font-mono, monospace);
  font-size: 0.84rem;
  color: var(--color-brand);
}

.meta {
  margin-top: 0.45rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  word-break: break-all;
}

.exist {
  padding-top: 0.7rem;
  border-top: 1px solid var(--color-border);
}

.exist h4 {
  margin-bottom: 0.4rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.exist__row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
}

.exist__row > span {
  flex: 1;
  min-width: 0;
}

.exist code {
  font-family: var(--font-mono, monospace);
}

.btn--sm {
  padding: 0.25rem 0.6rem;
  font-size: 0.78rem;
}

.copied {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-right: auto;
  color: var(--color-accent-text);
  font-size: 0.82rem;
  font-weight: 600;
}
</style>
