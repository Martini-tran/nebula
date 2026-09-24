<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { deleteWork, fetchWorkDetail, updateWork } from '../../api/work'
import StateBlock from '../../components/StateBlock.vue'
import { formatCount, formatRelative } from '../../utils/format'
import {
  WORK_AUDIENCE_LABEL,
  WORK_STATUS_LABEL,
  type WorkAudience,
  type WorkDetail,
  type WorkStatus,
  type WorkUpdateRequest,
} from '../../types/work'

const route = useRoute()
const router = useRouter()

const workId = computed(() => String(route.params.id))

const work = ref<WorkDetail | null>(null)
const loading = ref(true)
const loadError = ref('')

/** 表单用纯字符串承载，数组字段以逗号分隔编辑，提交时再拆 */
interface FormState {
  title: string
  status: WorkStatus
  summary: string
  logline: string
  intro: string
  audience: WorkAudience | ''
  genre: string
  tags: string
  protagonists: string
  targetWordCount: number | null
}

const form = reactive<FormState>({
  title: '',
  status: 'draft',
  summary: '',
  logline: '',
  intro: '',
  audience: '',
  genre: '',
  tags: '',
  protagonists: '',
  targetWordCount: null,
})
/** 上次加载/保存后的表单快照，用于判断有没有改动 */
const snapshot = ref('')

const saving = ref(false)
const saveError = ref('')
const savedHint = ref(false)
const submitted = ref(false)
let savedTimer: ReturnType<typeof setTimeout> | undefined

const confirmingDelete = ref(false)
const deleting = ref(false)
const deleteError = ref('')

const statusOptions = Object.entries(WORK_STATUS_LABEL) as Array<[WorkStatus, string]>
const audienceOptions = Object.entries(WORK_AUDIENCE_LABEL) as Array<[WorkAudience, string]>

const titleMissing = computed(() => submitted.value && !form.title.trim())
const dirty = computed(() => JSON.stringify(form) !== snapshot.value)

const joinList = (values?: string[] | null) => (values ?? []).join('，')
/** 中英文逗号、顿号都算分隔符 */
const splitList = (value: string) =>
  value
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)

const fillForm = (detail: WorkDetail) => {
  form.title = detail.title
  form.status = detail.status
  form.summary = detail.summary ?? ''
  form.logline = detail.logline ?? ''
  form.intro = detail.intro ?? ''
  form.audience = detail.audience ?? ''
  form.genre = detail.genre ?? ''
  form.tags = joinList(detail.tags)
  form.protagonists = joinList(detail.protagonists)
  form.targetWordCount = detail.targetWordCount ?? null
  snapshot.value = JSON.stringify(form)
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  confirmingDelete.value = false
  try {
    const detail = await fetchWorkDetail(workId.value)
    work.value = detail
    fillForm(detail)
  } catch (error) {
    work.value = null
    loadError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(workId, load, { immediate: true })

const buildRequest = (): WorkUpdateRequest => ({
  title: form.title.trim(),
  status: form.status,
  summary: form.summary.trim() || undefined,
  logline: form.logline.trim() || undefined,
  intro: form.intro.trim() || undefined,
  audience: form.audience || undefined,
  genre: form.genre.trim() || undefined,
  tags: splitList(form.tags),
  protagonists: splitList(form.protagonists),
  // v-model.number 清空输入框时会得到空串
  targetWordCount: typeof form.targetWordCount === 'number' ? form.targetWordCount : undefined,
})

const save = async () => {
  submitted.value = true
  saveError.value = ''
  if (!form.title.trim()) {
    return
  }

  saving.value = true
  try {
    const detail = await updateWork(workId.value, buildRequest())
    work.value = detail
    fillForm(detail)
    submitted.value = false
    savedHint.value = true
    clearTimeout(savedTimer)
    savedTimer = setTimeout(() => (savedHint.value = false), 2400)
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

const resetForm = () => {
  if (work.value) {
    fillForm(work.value)
  }
  submitted.value = false
  saveError.value = ''
}

const remove = async () => {
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteWork(workId.value)
    await router.replace('/works')
  } catch (error) {
    deleteError.value = error instanceof Error ? error.message : '删除失败，请稍后重试'
  } finally {
    deleting.value = false
  }
}

onBeforeUnmount(() => clearTimeout(savedTimer))
</script>

<template>
  <div class="page">
    <button class="btn btn--quiet back" type="button" @click="router.push('/works')">
      <Icon icon="lucide:arrow-left" />
      我的作品
    </button>

    <StateBlock v-if="loading" state="loading" />
    <StateBlock
      v-else-if="!work"
      state="error"
      :title="loadError || '加载失败'"
      description="作品可能已被删除，或不属于当前账号。"
      action-label="回到作品列表"
      @action="router.push('/works')"
    />

    <template v-else>
      <header class="head">
        <div class="head__main">
          <h1 class="page-title">{{ work.title }}</h1>
          <p class="meta">
            <span class="tag" :class="{ 'tag--brand': work.status === 'serializing', 'tag--accent': work.status === 'finished' }">
              {{ WORK_STATUS_LABEL[work.status] }}
            </span>
            <span>{{ formatCount(work.wordCount) }} 字</span>
            <span>{{ work.chapterCount }} 章</span>
            <span v-if="work.updateTime">{{ formatRelative(work.updateTime) }}更新</span>
          </p>
        </div>
      </header>

      <div class="layout">
        <section class="panel surface" aria-labelledby="info-title">
          <h2 id="info-title" class="panel__title">作品信息</h2>

          <form class="form" novalidate @submit.prevent="save">
            <div class="field">
              <label class="field__label" for="work-title">
                标题
                <span class="field__required" aria-hidden="true">*</span>
              </label>
              <input
                id="work-title"
                v-model="form.title"
                class="field__input"
                :class="{ 'field__input--invalid': titleMissing }"
                type="text"
                maxlength="100"
                autocomplete="off"
                :aria-invalid="titleMissing"
              />
              <p v-if="titleMissing" class="field__error">请填写标题</p>
            </div>

            <div class="grid-2">
              <div class="field">
                <label class="field__label" for="work-status">状态</label>
                <select id="work-status" v-model="form.status" class="field__input">
                  <option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option>
                </select>
              </div>
              <div class="field">
                <label class="field__label" for="work-audience">目标读者</label>
                <select id="work-audience" v-model="form.audience" class="field__input">
                  <option value="">暂不设定</option>
                  <option v-for="[value, label] in audienceOptions" :key="value" :value="value">{{ label }}</option>
                </select>
              </div>
            </div>

            <div class="grid-2">
              <div class="field">
                <label class="field__label" for="work-genre">题材</label>
                <input
                  id="work-genre"
                  v-model="form.genre"
                  class="field__input"
                  type="text"
                  maxlength="32"
                  placeholder="如：古代悬疑 / 东方玄幻"
                  autocomplete="off"
                />
              </div>
              <div class="field">
                <label class="field__label" for="work-target">目标字数</label>
                <input
                  id="work-target"
                  v-model.number="form.targetWordCount"
                  class="field__input"
                  type="number"
                  min="0"
                  step="10000"
                  placeholder="留空表示暂不设定"
                />
              </div>
            </div>

            <div class="field">
              <label class="field__label" for="work-summary">一句话简介</label>
              <input
                id="work-summary"
                v-model="form.summary"
                class="field__input"
                type="text"
                maxlength="200"
                placeholder="展示在作品卡片上"
                autocomplete="off"
              />
            </div>

            <div class="field">
              <label class="field__label" for="work-logline">一句话立意</label>
              <textarea
                id="work-logline"
                v-model="form.logline"
                class="field__input field__input--area"
                rows="2"
                maxlength="300"
                placeholder="用一句话说清这本书讲什么"
              />
              <p class="field__hint">只给 AI 当创作依据，不会对外展示。</p>
            </div>

            <div class="field">
              <label class="field__label" for="work-intro">作品简介</label>
              <textarea
                id="work-intro"
                v-model="form.intro"
                class="field__input field__input--area"
                rows="5"
                maxlength="5000"
                placeholder="发布到平台时的默认简介，纯文本"
              />
            </div>

            <div class="grid-2">
              <div class="field">
                <label class="field__label" for="work-tags">标签</label>
                <input
                  id="work-tags"
                  v-model="form.tags"
                  class="field__input"
                  type="text"
                  placeholder="悬疑，慢热"
                  autocomplete="off"
                />
                <p class="field__hint">用逗号分隔，最多 10 个。</p>
              </div>
              <div class="field">
                <label class="field__label" for="work-protagonists">主角</label>
                <input
                  id="work-protagonists"
                  v-model="form.protagonists"
                  class="field__input"
                  type="text"
                  placeholder="沈砚"
                  autocomplete="off"
                />
                <p class="field__hint">用逗号分隔，最多 5 个。</p>
              </div>
            </div>

            <p v-if="saveError" class="form__error" role="alert">{{ saveError }}</p>

            <footer class="form__actions">
              <span v-if="savedHint" class="saved" role="status">
                <Icon icon="lucide:check" />
                已保存
              </span>
              <button class="btn btn--ghost" type="button" :disabled="!dirty || saving" @click="resetForm">
                撤销修改
              </button>
              <button class="btn btn--primary" type="submit" :disabled="!dirty || saving">
                <Icon v-if="saving" icon="lucide:loader-circle" class="spin" />
                {{ saving ? '保存中…' : '保存' }}
              </button>
            </footer>
          </form>
        </section>

        <aside class="side">
          <section class="panel surface" aria-labelledby="toc-title">
            <h2 id="toc-title" class="panel__title">卷章目录</h2>
            <StateBlock
              v-if="work.volumes.length === 0"
              state="empty"
              title="还没有章节"
              description="卷与章节将在下一步开放。"
            />
          </section>

          <section class="panel surface danger" aria-labelledby="danger-title">
            <h2 id="danger-title" class="panel__title">删除作品</h2>
            <p class="danger__desc">作品会移入回收站，不会立即清除。</p>
            <p v-if="deleteError" class="form__error" role="alert">{{ deleteError }}</p>
            <div v-if="!confirmingDelete">
              <button class="btn btn--ghost danger__btn" type="button" @click="confirmingDelete = true">
                <Icon icon="lucide:trash-2" />
                删除这部作品
              </button>
            </div>
            <div v-else class="danger__confirm">
              <p class="danger__ask">确定删除《{{ work.title }}》？</p>
              <div class="danger__actions">
                <button class="btn btn--ghost" type="button" :disabled="deleting" @click="confirmingDelete = false">
                  取消
                </button>
                <button class="btn danger__btn danger__btn--solid" type="button" :disabled="deleting" @click="remove">
                  <Icon v-if="deleting" icon="lucide:loader-circle" class="spin" />
                  {{ deleting ? '删除中…' : '确认删除' }}
                </button>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.back {
  margin-bottom: 1rem;
  padding-inline: 0.4rem;
}

.back svg {
  width: 1rem;
  height: 1rem;
}

.head {
  margin-bottom: 1.5rem;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 1rem;
  margin-top: 0.5rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20rem;
  gap: 1.5rem;
  align-items: start;
}

.panel__title {
  padding: 1.1rem 1.35rem 0;
  font-size: 1.05rem;
  font-weight: 700;
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.15rem;
}

.form__actions {
  align-items: center;
}

.saved {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-right: auto;
  font-size: 0.88rem;
  color: var(--color-accent-text);
}

.saved svg {
  width: 1rem;
  height: 1rem;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.danger {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-bottom: 1.35rem;
}

.danger > :not(.panel__title) {
  margin-inline: 1.35rem;
}

.danger__desc,
.danger__ask {
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.danger__ask {
  color: var(--color-text-primary);
  font-weight: 600;
  margin-bottom: 0.6rem;
}

.danger__actions {
  display: flex;
  gap: 0.6rem;
}

.danger__btn {
  color: #dc2626;
}

.danger__btn svg {
  width: 1rem;
  height: 1rem;
}

.danger__btn--solid {
  color: #fff;
  background: #dc2626;
}

.danger__btn--solid:not(:disabled):hover {
  background: #b91c1c;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .grid-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
