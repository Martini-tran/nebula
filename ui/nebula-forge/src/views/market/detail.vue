<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import {
  downloadLatest,
  downloadVersion,
  fetchPluginDetail,
  fetchPluginPermissions,
  fetchPluginVersions,
} from '../../api/plugins'
import {
  PLUGIN_TYPE_LABEL,
  PRICING_TYPE_LABEL,
  RISK_LEVEL_LABEL,
} from '../../types/plugin'
import type {
  PluginDetail,
  PluginPermission,
  PluginVersion,
} from '../../types/plugin'
import { useThemeStore } from '../../stores/theme'
import { formatBytes, formatCount, formatDate, formatRating } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()

const pluginId = computed(() => String(route.params.id))

const detail = shallowRef<PluginDetail | null>(null)
const versions = ref<PluginVersion[]>([])
const loading = ref(true)
const notFound = ref(false)

const downloading = ref<string | null>(null) // 'latest' | versionId
const downloadError = ref('')

// 各版本权限：versionId -> 权限列表；展开状态
const permissionsMap = ref<Record<number, PluginPermission[]>>({})
const permissionsLoading = ref<number | null>(null)
const expandedVersionId = ref<number | null>(null)

const previewTheme = computed(() => (themeStore.isDark ? 'dark' : 'light'))

const iconFailed = ref(false)
const showIcon = computed(() => !!detail.value?.icon_url && !iconFailed.value)

const priceLabel = computed(() => {
  const p = detail.value
  if (!p) return ''
  if (p.price_text) return p.price_text
  if (p.pricing_type != null) return PRICING_TYPE_LABEL[p.pricing_type] ?? '—'
  return '免费'
})

const isExternalPurchase = computed(() => detail.value?.pricing_type === 4)

const typeLabel = computed(() => {
  const t = detail.value?.type
  return t ? PLUGIN_TYPE_LABEL[t] ?? t : ''
})

const hasRating = computed(
  () => (detail.value?.rating_count ?? 0) > 0 && (detail.value?.rating_score ?? 0) > 0,
)

const triggerBrowserDownload = (url: string) => {
  // 直链/预签名 URL：新开标签触发下载，避免被 SPA 路由拦截
  window.open(url, '_blank', 'noopener')
}

const handleDownload = async (versionId?: number) => {
  if (!detail.value) return

  // 外部购买：跳转购买地址
  if (isExternalPurchase.value && detail.value.purchase_url) {
    window.open(detail.value.purchase_url, '_blank', 'noopener')
    return
  }

  const key = versionId == null ? 'latest' : String(versionId)
  downloading.value = key
  downloadError.value = ''
  try {
    const res =
      versionId == null
        ? await downloadLatest(detail.value.id)
        : await downloadVersion(detail.value.id, versionId)
    if (res?.package_url) {
      triggerBrowserDownload(res.package_url)
    } else {
      downloadError.value = '该版本暂无可下载的安装包。'
    }
  } catch {
    downloadError.value = '下载失败，请稍后重试。'
  } finally {
    downloading.value = null
  }
}

const togglePermissions = async (version: PluginVersion) => {
  if (expandedVersionId.value === version.id) {
    expandedVersionId.value = null
    return
  }
  expandedVersionId.value = version.id
  if (!permissionsMap.value[version.id]) {
    permissionsLoading.value = version.id
    try {
      const list = await fetchPluginPermissions(detail.value!.id, version.id)
      permissionsMap.value = { ...permissionsMap.value, [version.id]: list ?? [] }
    } catch {
      permissionsMap.value = { ...permissionsMap.value, [version.id]: [] }
    } finally {
      permissionsLoading.value = null
    }
  }
}

const riskClass = (level?: number | null) => {
  switch (level) {
    case 3:
      return 'perm--high'
    case 2:
      return 'perm--mid'
    default:
      return 'perm--low'
  }
}

const load = async () => {
  loading.value = true
  notFound.value = false
  detail.value = null
  versions.value = []
  permissionsMap.value = {}
  expandedVersionId.value = null
  try {
    const [d, v] = await Promise.all([
      fetchPluginDetail(pluginId.value),
      fetchPluginVersions(pluginId.value).catch(() => [] as PluginVersion[]),
    ])
    detail.value = d
    versions.value = v ?? []
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(pluginId, load)

const goBack = () => router.push('/market')
</script>

<template>
  <div class="detail">
    <div class="detail__container">
      <button type="button" class="detail__back" @click="goBack">
        <Icon icon="lucide:arrow-left" />
        返回插件市场
      </button>

      <p v-if="loading" class="detail__state">
        <Icon icon="lucide:loader-circle" class="spin" />
        正在加载…
      </p>

      <div v-else-if="notFound || !detail" class="detail__state detail__state--empty">
        <Icon icon="lucide:package-x" />
        插件不存在或已下架。
        <button type="button" class="detail__retry" @click="goBack">返回市场</button>
      </div>

      <template v-else>
        <!-- 头部 -->
        <header class="hero">
          <div class="hero__icon">
            <img
              v-if="showIcon"
              :src="detail.icon_url ?? ''"
              :alt="detail.name"
              @error="iconFailed = true"
            />
            <Icon v-else icon="lucide:puzzle" />
          </div>

          <div class="hero__main">
            <div class="hero__title-row">
              <h1 class="hero__name">{{ detail.name }}</h1>
              <span v-if="detail.is_featured === 1" class="hero__featured">
                <Icon icon="lucide:sparkles" /> 推荐
              </span>
            </div>
            <p class="hero__summary">{{ detail.summary || '暂无简介' }}</p>

            <div class="hero__meta">
              <span v-if="detail.author_name" class="hero__meta-item">
                <Icon icon="lucide:user" />
                {{ detail.author_name }}
              </span>
              <span v-if="detail.latest_version" class="hero__meta-item">
                <Icon icon="lucide:tag" />
                v{{ detail.latest_version }}
              </span>
              <span class="hero__meta-item">
                <Icon icon="lucide:download" />
                {{ formatCount(detail.download_count) }} 次下载
              </span>
              <span v-if="hasRating" class="hero__meta-item">
                <Icon icon="lucide:star" class="hero__star" />
                {{ formatRating(detail.rating_score) }}（{{ detail.rating_count }}）
              </span>
              <span v-if="typeLabel" class="hero__meta-item">
                <Icon icon="lucide:box" />
                {{ typeLabel }}
              </span>
            </div>

            <div class="hero__tags" v-if="detail.category_names?.length">
              <span v-for="name in detail.category_names" :key="name" class="hero__tag">
                {{ name }}
              </span>
            </div>
          </div>

          <div class="hero__action">
            <span class="hero__price" :class="{ 'hero__price--free': priceLabel === '免费' }">
              {{ priceLabel }}
            </span>
            <button
              type="button"
              class="hero__download"
              :disabled="downloading === 'latest'"
              @click="handleDownload()"
            >
              <Icon
                :icon="downloading === 'latest' ? 'lucide:loader-circle' : (isExternalPurchase ? 'lucide:external-link' : 'lucide:download')"
                :class="{ spin: downloading === 'latest' }"
              />
              {{ isExternalPurchase ? '前往购买' : '下载最新版' }}
            </button>
            <p v-if="downloadError" class="hero__download-err">{{ downloadError }}</p>
          </div>
        </header>

        <div class="detail__body">
          <!-- 主内容 -->
          <main class="detail__content">
            <!-- 详情描述 -->
            <section class="panel">
              <h2 class="panel__title">插件介绍</h2>
              <MdPreview
                v-if="detail.description"
                :model-value="detail.description"
                :theme="previewTheme"
                preview-theme="default"
                class="detail__md"
              />
              <p v-else class="panel__empty">暂无详细介绍。</p>
            </section>

            <!-- 版本 -->
            <section class="panel">
              <h2 class="panel__title">版本记录</h2>
              <p v-if="!versions.length" class="panel__empty">暂无已发布版本。</p>
              <ul v-else class="versions">
                <li v-for="ver in versions" :key="ver.id" class="version">
                  <div class="version__head">
                    <div class="version__title">
                      <span class="version__num">v{{ ver.version }}</span>
                      <span v-if="ver.channel" class="version__channel">{{ ver.channel }}</span>
                    </div>
                    <div class="version__sub">
                      <span v-if="ver.published_time">
                        <Icon icon="lucide:calendar" />
                        {{ formatDate(ver.published_time) }}
                      </span>
                      <span v-if="ver.package_size">
                        <Icon icon="lucide:hard-drive" />
                        {{ formatBytes(ver.package_size) }}
                      </span>
                      <span>
                        <Icon icon="lucide:download" />
                        {{ formatCount(ver.download_count) }}
                      </span>
                    </div>
                  </div>

                  <p v-if="ver.changelog" class="version__changelog">{{ ver.changelog }}</p>

                  <div class="version__actions">
                    <button
                      type="button"
                      class="version__btn"
                      :disabled="downloading === String(ver.id)"
                      @click="handleDownload(ver.id)"
                    >
                      <Icon
                        :icon="downloading === String(ver.id) ? 'lucide:loader-circle' : 'lucide:download'"
                        :class="{ spin: downloading === String(ver.id) }"
                      />
                      下载此版本
                    </button>
                    <button
                      type="button"
                      class="version__btn version__btn--ghost"
                      @click="togglePermissions(ver)"
                    >
                      <Icon icon="lucide:shield" />
                      权限声明
                      <Icon
                        :icon="expandedVersionId === ver.id ? 'lucide:chevron-up' : 'lucide:chevron-down'"
                      />
                    </button>
                  </div>

                  <!-- 权限 -->
                  <div v-if="expandedVersionId === ver.id" class="perms">
                    <p v-if="permissionsLoading === ver.id" class="perms__state">
                      <Icon icon="lucide:loader-circle" class="spin" />
                      加载权限…
                    </p>
                    <p
                      v-else-if="!permissionsMap[ver.id]?.length"
                      class="perms__state"
                    >
                      该版本未声明任何权限。
                    </p>
                    <ul v-else class="perms__list">
                      <li
                        v-for="perm in permissionsMap[ver.id]"
                        :key="perm.permission_code"
                        class="perm"
                      >
                        <div class="perm__head">
                          <span class="perm__name">
                            {{ perm.permission_name || perm.permission_code }}
                          </span>
                          <span class="perm__badges">
                            <span class="perm__risk" :class="riskClass(perm.risk_level)">
                              {{ RISK_LEVEL_LABEL[perm.risk_level ?? 1] ?? '低' }}风险
                            </span>
                            <span v-if="perm.required === 1" class="perm__required">必需</span>
                          </span>
                        </div>
                        <code class="perm__code">{{ perm.permission_code }}</code>
                        <p v-if="perm.description" class="perm__desc">{{ perm.description }}</p>
                      </li>
                    </ul>
                  </div>
                </li>
              </ul>
            </section>
          </main>

          <!-- 侧栏 -->
          <aside class="detail__aside">
            <div class="info">
              <h3 class="info__title">插件信息</h3>
              <dl class="info__list">
                <div v-if="detail.plugin_key" class="info__row">
                  <dt>标识</dt>
                  <dd><code>{{ detail.plugin_key }}</code></dd>
                </div>
                <div v-if="detail.latest_version" class="info__row">
                  <dt>最新版本</dt>
                  <dd>v{{ detail.latest_version }}</dd>
                </div>
                <div v-if="detail.license" class="info__row">
                  <dt>许可证</dt>
                  <dd>{{ detail.license }}</dd>
                </div>
                <div v-if="(detail.favorite_count ?? 0) > 0" class="info__row">
                  <dt>收藏</dt>
                  <dd>{{ formatCount(detail.favorite_count) }}</dd>
                </div>
                <div v-if="(detail.install_count ?? 0) > 0" class="info__row">
                  <dt>安装</dt>
                  <dd>{{ formatCount(detail.install_count) }}</dd>
                </div>
                <div v-if="detail.update_time" class="info__row">
                  <dt>更新</dt>
                  <dd>{{ formatDate(detail.update_time) }}</dd>
                </div>
              </dl>

              <div class="info__links">
                <a
                  v-if="detail.homepage_url"
                  class="info__link"
                  :href="detail.homepage_url"
                  target="_blank"
                  rel="noopener"
                >
                  <Icon icon="lucide:globe" />
                  插件主页
                </a>
                <a
                  v-if="detail.repo_url"
                  class="info__link"
                  :href="detail.repo_url"
                  target="_blank"
                  rel="noopener"
                >
                  <Icon icon="lucide:git-branch" />
                  源码仓库
                </a>
              </div>
            </div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.detail__container {
  max-width: var(--container-max-width);
  margin: 0 auto;
  padding: clamp(1.5rem, 4vw, 2.5rem) var(--space-page-x) clamp(3rem, 6vw, 5rem);
}

.detail__back {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0.4rem 0;
  color: var(--color-text-secondary);
  font-weight: 600;
  transition: color 0.2s ease;
}

.detail__back:hover {
  color: var(--color-brand);
}

.detail__back svg {
  width: 1.05rem;
  height: 1.05rem;
}

.detail__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 4rem;
  color: var(--color-text-secondary);
}

.detail__state svg {
  width: 1.3rem;
  height: 1.3rem;
}

.detail__state--empty {
  flex-direction: column;
}

.detail__retry {
  padding: 0.4rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-brand);
  font-weight: 600;
  cursor: pointer;
}

/* 头部 */
.hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 1.25rem;
  margin-top: 1rem;
  padding: 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.hero__icon {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  overflow: hidden;
}

.hero__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero__icon svg {
  width: 2.4rem;
  height: 2.4rem;
}

.hero__main {
  flex: 1;
  min-width: 14rem;
}

.hero__title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.hero__name {
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: 800;
  color: var(--color-text-primary);
}

.hero__featured {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.hero__featured svg {
  width: 0.85rem;
  height: 0.85rem;
}

.hero__summary {
  margin-top: 0.5rem;
  color: var(--color-text-secondary);
  font-size: 1rem;
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 0.9rem;
}

.hero__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.hero__meta-item svg {
  width: 1rem;
  height: 1rem;
}

.hero__star {
  color: #f5a623;
}

.hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.9rem;
}

.hero__tag {
  padding: 0.18rem 0.55rem;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  background: var(--color-bg-soft);
}

.hero__action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.6rem;
  min-width: 12rem;
}

.hero__price {
  font-size: 1.3rem;
  font-weight: 800;
  color: var(--color-brand);
}

.hero__price--free {
  color: var(--color-text-secondary);
}

.hero__download {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.7rem 1.4rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: background 0.2s ease, transform 0.2s ease;
}

.hero__download:hover:not(:disabled) {
  background: var(--color-brand-hover);
  transform: translateY(-1px);
}

.hero__download:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.hero__download svg {
  width: 1.1rem;
  height: 1.1rem;
}

.hero__download-err {
  font-size: 0.8rem;
  color: #e05252;
}

/* 主体布局 */
.detail__body {
  display: grid;
  grid-template-columns: 1fr 18rem;
  gap: 1.5rem;
  margin-top: 1.5rem;
  align-items: start;
}

.detail__content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}

.panel {
  padding: 1.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.panel__title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 1rem;
}

.panel__empty {
  color: var(--color-text-secondary);
  font-size: 0.92rem;
}

.detail__md {
  background: transparent;
}

/* 版本 */
.versions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.version {
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
}

.version__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.version__title {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.version__num {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.version__channel {
  padding: 0.1rem 0.45rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.version__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
}

.version__sub span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.version__sub svg {
  width: 0.9rem;
  height: 0.9rem;
}

.version__changelog {
  margin-top: 0.7rem;
  font-size: 0.88rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
  white-space: pre-line;
}

.version__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.9rem;
}

.version__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.version__btn:hover:not(:disabled) {
  background: var(--color-brand-hover);
}

.version__btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.version__btn svg {
  width: 0.95rem;
  height: 0.95rem;
}

.version__btn--ghost {
  background: var(--color-bg-surface);
  border-color: var(--color-border);
  color: var(--color-text-secondary);
}

.version__btn--ghost:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
  background: var(--color-bg-surface);
}

/* 权限 */
.perms {
  margin-top: 0.9rem;
  padding-top: 0.9rem;
  border-top: 1px dashed var(--color-border);
}

.perms__state {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.perms__list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.perm {
  padding: 0.7rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
}

.perm__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.perm__name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-primary);
}

.perm__badges {
  display: inline-flex;
  gap: 0.4rem;
}

.perm__risk {
  padding: 0.1rem 0.45rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  font-weight: 600;
}

.perm--low {
  color: #2f855a;
  background: rgba(72, 187, 120, 0.15);
}

.perm--mid {
  color: #b7791f;
  background: rgba(237, 137, 54, 0.15);
}

.perm--high {
  color: #c53030;
  background: rgba(245, 101, 101, 0.15);
}

.perm__required {
  padding: 0.1rem 0.45rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.perm__code {
  display: inline-block;
  margin-top: 0.35rem;
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--color-text-secondary);
}

.perm__desc {
  margin-top: 0.35rem;
  font-size: 0.83rem;
  color: var(--color-text-secondary);
}

/* 侧栏 */
.detail__aside {
  position: sticky;
  top: 5rem;
}

.info {
  padding: 1.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.info__title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 1rem;
}

.info__list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.info__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.88rem;
}

.info__row dt {
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.info__row dd {
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}

.info__row code {
  font-family: var(--font-mono);
  font-size: 0.8rem;
}

.info__links {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.2rem;
  padding-top: 1.2rem;
  border-top: 1px solid var(--color-border);
}

.info__link {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: 0.88rem;
  font-weight: 600;
  transition: all 0.2s ease;
}

.info__link:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.info__link svg {
  width: 1.05rem;
  height: 1.05rem;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .detail__body {
    grid-template-columns: 1fr;
  }

  .detail__aside {
    position: static;
  }
}

@media (max-width: 640px) {
  .hero__action {
    align-items: flex-start;
    width: 100%;
  }
}
</style>
