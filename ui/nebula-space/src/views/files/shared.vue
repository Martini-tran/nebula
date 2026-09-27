<script setup lang="ts">
/**
 * 分享下载页：别人拿到 /s/{code} 链接后打开的页面，不需要登录。
 * 输入提取码后下载；过期、撤销、次数用完都在这里说清楚。
 */
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import BrandMark from '../../components/BrandMark.vue'
import { downloadShared, fetchSharePublic, saveBlob } from '../../api/files'
import { useMockFor } from '../../api/mock'
import { errorText } from '../../composables/useToast'
import { monthDay, ymdOf } from '../../utils/date'
import { formatSize, kindOf, KIND_META } from '../../utils/files'
import type { SharePublic } from '../../types/files'

const route = useRoute()
const code = String(route.params.code)

const info = ref<SharePublic | null>(null)
const loading = ref(true)
const loadError = ref('')
const password = ref('')
const error = ref('')
const downloading = ref(false)
const done = ref(false)

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    info.value = await fetchSharePublic(code)
  } catch (err) {
    loadError.value = errorText(err, '链接打不开')
  } finally {
    loading.value = false
  }
}

const download = async () => {
  if (!info.value || downloading.value) return
  if (info.value.needPassword && !password.value.trim()) {
    error.value = '请输入提取码'
    return
  }
  downloading.value = true
  error.value = ''
  try {
    saveBlob(await downloadShared(code, info.value.needPassword ? password.value : null), info.value.fileName)
    done.value = true
    info.value = await fetchSharePublic(code)
  } catch (err) {
    error.value = errorText(err, '下载失败')
  } finally {
    downloading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="sh">
    <div class="card surface">
      <div class="brand"><BrandMark :size="28" /><b>Space</b><span>文件分享</span></div>

      <div v-if="loading" class="state"><Icon icon="lucide:loader-circle" class="spin" />正在打开…</div>

      <div v-else-if="loadError" class="state state--err">
        <Icon icon="lucide:link-2-off" />
        <b>{{ loadError }}</b>
        <small>检查一下链接是否完整，或者请分享人重新发一个。</small>
      </div>

      <template v-else-if="info">
        <div class="file">
          <span class="thumb" :style="{ '--k': info.isFolder ? '#f59e0b' : KIND_META[kindOf(info.fileName)].color }">
            <Icon :icon="info.isFolder ? 'lucide:folder' : 'lucide:file'" />
          </span>
          <div>
            <b>{{ info.fileName }}</b>
            <small>{{ info.owner }} 分享 · {{ formatSize(info.size) }}<template v-if="info.expireAt"> · {{ monthDay(ymdOf(info.expireAt)) }}前有效</template></small>
          </div>
        </div>

        <div v-if="info.unavailable" class="state state--err state--inline">
          <Icon icon="lucide:circle-slash" />
          <b>{{ info.unavailable }}</b>
          <small>这个链接已经不能下载了，请分享人重新分享。</small>
        </div>

        <form v-else class="form" @submit.prevent="download">
          <label v-if="info.needPassword" class="field">
            <span class="field__label">提取码</span>
            <input v-model="password" class="field__input" type="text" maxlength="8" autocomplete="off" autofocus placeholder="4 位，随链接一起给你的" @input="error = ''" />
          </label>
          <p v-if="error" class="err" role="alert">{{ error }}</p>
          <p v-if="done && !error" class="ok"><Icon icon="lucide:check" />已开始下载</p>
          <button class="btn btn--primary" type="submit" :disabled="downloading">
            <Icon :icon="downloading ? 'lucide:loader-circle' : 'lucide:download'" :class="{ spin: downloading }" />下载
          </button>
        </form>
      </template>

      <p v-if="useMockFor('files')" class="demo">演示模式：分享数据存在分享人的浏览器里，只有在同一个浏览器里打开链接才能下载。</p>
    </div>
  </main>
</template>

<style scoped>
.sh {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 1.5rem 1rem;
  background: var(--color-bg-canvas);
}

.card {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  width: min(26rem, 100%);
  padding: 1.5rem;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.brand span {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.file {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.file > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.file b {
  word-break: break-all;
}

.file small {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.thumb {
  display: grid;
  place-items: center;
  flex: none;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--k) 14%, var(--color-bg-soft));
  color: var(--k);
}

.thumb svg {
  width: 1.5rem;
  height: 1.5rem;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  padding: 1rem 0;
  text-align: center;
  color: var(--color-text-secondary);
}

.state svg {
  width: 1.6rem;
  height: 1.6rem;
}

.state--err b {
  color: var(--color-text-primary);
}

.state--inline {
  padding: 0.8rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.state small {
  font-size: 0.8rem;
}

.form {
  padding: 0;
}

.form .btn {
  width: 100%;
  justify-content: center;
}

.err {
  font-size: 0.82rem;
  color: var(--color-danger);
}

.ok {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.82rem;
  color: var(--color-accent-text);
}

.demo {
  font-size: 0.74rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}
</style>
