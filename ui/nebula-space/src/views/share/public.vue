<script setup lang="ts">
/** 公开主页 /@{handle}：不需要登录的只读页面。导入合集时才需要登录。 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import PublicView from './components/PublicView.vue'
import { countCollectionView, countImport, fetchPublicPage } from '../../api/profile'
import { useMockFor } from '../../api/mock'
import { useAuthStore } from '../../stores/auth'
import { errorText, toast } from '../../composables/useToast'
import { importCollection } from './importCollection'
import type { PublicPage } from '../../types/profile'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const handle = String(route.params.handle)

const page = ref<PublicPage | null>(null)
const loading = ref(true)
const error = ref('')
const importing = ref<string | null>(null)

onMounted(async () => {
  try {
    page.value = await fetchPublicPage(handle)
    document.title = `${page.value.nickname} · Space`
  } catch (err) {
    error.value = errorText(err, '这个主页不存在')
  } finally {
    loading.value = false
  }
})

const onView = (id: string) => {
  countCollectionView(handle, id).catch(() => undefined)
}

const onImport = async (id: string) => {
  if (!page.value) return
  if (!auth.isLoggedIn) {
    toast.info('登录后才能导入到你的 Space')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  importing.value = id
  try {
    const r = await importCollection(page.value, id)
    await countImport(handle, id).catch(() => undefined)
    toast.ok(`已导入「${r.title}」${r.added} 个${r.skipped ? `，${r.skipped} 个你已经收藏过` : ''}`, {
      action: { label: '去看看', run: () => router.push('/bookmarks') },
    })
  } catch (err) {
    toast.error(errorText(err, '导入失败'))
  } finally {
    importing.value = null
  }
}
</script>

<template>
  <div class="public">
    <div v-if="loading" class="state"><Icon icon="lucide:loader-circle" class="spin" />正在打开…</div>
    <div v-else-if="error" class="state">
      <Icon icon="lucide:user-x" />
      <b>{{ error }}</b>
      <small>地址是 /@短名，检查一下有没有拼错。</small>
    </div>
    <PublicView v-else-if="page" :page="page" :importing="importing" @import="onImport" @view="onView" />
    <p v-if="useMockFor('profile')" class="demo">演示模式：公开页在作者的浏览器里拼出来，别的设备打不开；接通后端后任何人都能访问。</p>
  </div>
</template>

<style scoped>
.public {
  min-height: 100vh;
  background: var(--color-bg-canvas);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 6rem 1rem;
  color: var(--color-text-secondary);
  text-align: center;
}

.state svg {
  width: 1.8rem;
  height: 1.8rem;
}

.state b {
  color: var(--color-text-primary);
}

.demo {
  padding: 0 1rem 1.5rem;
  font-size: 0.74rem;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
