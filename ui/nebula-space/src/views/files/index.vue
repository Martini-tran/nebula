<script setup lang="ts">
/**
 * 文件柜：证件扫描件、合同、发票这类「要用时找不到」的文件放一处。
 * 左栏：我的文件、最近、我分享的、最近删除（30 天可恢复）、按类型筛选、来自其他模块的附件；底部容量条按类型分色。
 * 中间网格：拖文件进来就上传，进度就地显示；单击在右栏预览，双击全屏（文件夹双击进入）。
 * 地址栏：?v=mine|recent|shared|trash|kind:image|source:notes &folder=<id> &f=<选中的文件>
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import SideNav, { type SideNavGroup } from '../../components/layout/SideNav.vue'
import StateBlock from '../../components/StateBlock.vue'
import BaseDialog from '../../components/base/BaseDialog.vue'
import NameDialog from '../bookmarks/components/NameDialog.vue'
import ShareDialog from './components/ShareDialog.vue'
import {
  createFileFolder,
  downloadFile,
  fetchFileUsage,
  fetchFiles,
  fetchShares,
  purgeFile,
  restoreFile,
  revokeShare,
  saveBlob,
  shareUrl,
  trashFile,
  updateFile,
  uploadFile,
  type FileQuery,
} from '../../api/files'
import { useMockFor } from '../../api/mock'
import { confirm } from '../../composables/useConfirm'
import { errorText, toast } from '../../composables/useToast'
import { diffDays, monthDay, relativeDay, todayYmd, ymdOf } from '../../utils/date'
import { canPreview, extOf, formatSize, kindOf, KIND_META } from '../../utils/files'
import { FILE_SOURCES, type FileKind, type FileSource, type FileUsage, type Share, type SpaceFile } from '../../types/files'
import type { EntityId } from '../../types/space'

const route = useRoute()
const router = useRouter()
const today = todayYmd()
/** 演示模式：示例文件没有实际内容 */
const demo = useMockFor('files')

// ── 视图与地址栏 ──

const view = computed(() => (typeof route.query.v === 'string' && route.query.v ? route.query.v : 'mine'))
const folderId = computed(() => (view.value === 'mine' && typeof route.query.folder === 'string' ? route.query.folder : null))
const selectedId = computed(() => (typeof route.query.f === 'string' ? route.query.f : null))

const go = (query: Record<string, string | undefined>) => router.push({ query })
const selectView = (key: string) => go({ v: key === 'mine' ? undefined : key })
const openFolder = (id: EntityId | null) => go({ folder: id === null ? undefined : String(id) })
const select = (f: SpaceFile | null) => router.replace({ query: { ...route.query, f: f ? String(f.id) : undefined } })

const queryOf = (): FileQuery => {
  const v = view.value
  if (v === 'recent') return { view: 'recent' }
  if (v === 'trash') return { view: 'trash' }
  if (v.startsWith('kind:')) return { view: 'kind', kind: v.slice(5) as FileKind }
  if (v.startsWith('source:')) return { view: 'source', source: v.slice(7) as Exclude<FileSource, 'upload'> }
  return { view: 'folder', folderId: folderId.value }
}

// ── 数据 ──

const list = ref<SpaceFile[]>([])
const all = ref<SpaceFile[]>([])
const trashCount = ref(0)
const shares = ref<Share[]>([])
const usage = ref<FileUsage | null>(null)
const loading = ref(true)
const loadError = ref('')

const loadSide = async () => {
  const [everything, trash, s, u] = await Promise.all([fetchFiles({ view: 'all' }), fetchFiles({ view: 'trash' }), fetchShares(), fetchFileUsage()])
  all.value = everything
  trashCount.value = trash.length
  shares.value = s
  usage.value = u
}

const load = async () => {
  loadError.value = ''
  try {
    if (view.value !== 'shared') list.value = await fetchFiles(queryOf())
    await loadSide()
  } catch (error) {
    loadError.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}
watch(
  () => [view.value, folderId.value],
  () => {
    loading.value = true
    load()
  },
)

const byId = (id: EntityId | null | undefined) => (id === null || id === undefined ? undefined : all.value.find((f) => String(f.id) === String(id)))
const itemsIn = (id: EntityId) => all.value.filter((f) => String(f.folderId) === String(id)).length

const activeShares = computed(() => shares.value.filter((s) => !s.revoked && (!s.expireAt || s.expireAt >= `${today} 00:00:00`) && (s.maxDownloads === null || s.downloads < s.maxDownloads)))
const sharedIds = computed(() => new Set(activeShares.value.map((s) => String(s.fileId))))

const groups = computed<SideNavGroup[]>(() => {
  const mine = all.value.filter((f) => f.source === 'upload' && !f.isFolder).length
  const count = (source: FileSource) => all.value.filter((f) => f.source === source).length
  const kinds: FileKind[] = ['image', 'pdf', 'doc', 'sheet', 'zip']
  return [
    {
      key: 'main',
      items: [
        { key: 'mine', label: '我的文件', icon: 'lucide:folder', count: mine },
        { key: 'recent', label: '最近', icon: 'lucide:clock' },
        { key: 'shared', label: '我分享的', icon: 'lucide:share-2', count: activeShares.value.length },
        { key: 'trash', label: '最近删除', icon: 'lucide:trash-2', count: trashCount.value },
      ],
    },
    {
      key: 'kind',
      title: '类型',
      items: kinds.map((k) => ({ key: `kind:${k}`, label: KIND_META[k].label, dot: KIND_META[k].color, count: all.value.filter((f) => !f.isFolder && kindOf(f.name, f.mime) === k).length })),
    },
    {
      key: 'source',
      title: '来自其他模块',
      items: (Object.keys(FILE_SOURCES) as (keyof typeof FILE_SOURCES)[]).map((s) => ({ key: `source:${s}`, label: FILE_SOURCES[s].label, icon: FILE_SOURCES[s].icon, count: count(s) })),
    },
  ]
})

const title = computed(() => {
  const v = view.value
  if (v === 'recent') return '最近'
  if (v === 'shared') return '我分享的'
  if (v === 'trash') return '最近删除'
  if (v.startsWith('kind:')) return KIND_META[v.slice(5) as FileKind].label
  if (v.startsWith('source:')) return FILE_SOURCES[v.slice(7) as keyof typeof FILE_SOURCES].label
  return '我的文件'
})

/** 面包屑：我的文件 / 证件与合同 / … */
const crumbs = computed(() => {
  const chain: SpaceFile[] = []
  let cur = byId(folderId.value)
  while (cur) {
    chain.unshift(cur)
    cur = byId(cur.folderId)
  }
  return chain
})

// ── 缩略图：图片直接取原图显示，太大的不取（以后可由服务端生成缩略图） ──

const THUMB_MAX = 3 * 1024 * 1024
const thumbs = ref<Record<string, string>>({})
const loadThumbs = async () => {
  const images = list.value.filter((f) => !f.isFolder && kindOf(f.name, f.mime) === 'image' && f.size <= THUMB_MAX && !thumbs.value[String(f.id)]).slice(0, 24)
  for (const f of images) {
    try {
      const blob = await downloadFile(f.id)
      if (blob.type.startsWith('image/')) thumbs.value = { ...thumbs.value, [String(f.id)]: URL.createObjectURL(blob) }
    } catch {
      // 没有缩略图就显示类型色块
    }
  }
}
watch(list, loadThumbs)

// ── 选中与预览 ──

const selected = computed(() => all.value.concat(list.value).find((f) => String(f.id) === selectedId.value) ?? null)
const previewUrl = ref('')
const previewText = ref('')
const previewNone = ref(false)
let previewFor = ''

const loadPreview = async () => {
  const f = selected.value
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewText.value = ''
  previewNone.value = false
  if (!f || f.isFolder || f.deleteTime || !canPreview(f.name, f.mime)) return
  const key = (previewFor = String(f.id))
  try {
    const blob = await downloadFile(f.id)
    if (key !== previewFor) return
    const kind = kindOf(f.name, f.mime)
    // 示例文件下载下来是一段说明文字，没法当图片 / PDF 显示
    if ((kind === 'image' && !blob.type.startsWith('image/')) || (kind === 'pdf' && blob.type !== 'application/pdf')) {
      previewNone.value = true
      return
    }
    if (kind === 'image' || kind === 'pdf') previewUrl.value = URL.createObjectURL(blob)
    else previewText.value = (await blob.text()).slice(0, 4000)
  } catch {
    previewNone.value = true
  }
}
watch(() => selected.value?.id, loadPreview)

const fullscreen = ref(false)
const openItem = (f: SpaceFile) => {
  if (f.deleteTime) return
  if (f.isFolder) {
    if (view.value === 'mine') openFolder(f.id)
    else go({ folder: String(f.id) })
    return
  }
  select(f)
  if (canPreview(f.name, f.mime)) fullscreen.value = true
  else download(f)
}

const locationOf = (f: SpaceFile) => {
  if (f.source !== 'upload') return FILE_SOURCES[f.source].label
  const names: string[] = []
  let cur = byId(f.folderId)
  while (cur) {
    names.unshift(cur.name)
    cur = byId(cur.folderId)
  }
  return ['我的文件', ...names].join(' / ')
}

const sharesOf = (f: SpaceFile) => activeShares.value.filter((s) => String(s.fileId) === String(f.id))

// ── 上传 ──

interface Upload {
  key: number
  name: string
  size: number
  progress: number
  error: string
}
const uploads = ref<Upload[]>([])
/** 取消用的控制器放在响应式数据外面（被代理后调用 abort 会报错） */
const controllers = new Map<number, AbortController>()
let seq = 0
const fileInput = ref<HTMLInputElement | null>(null)
const canUpload = computed(() => view.value === 'mine' || view.value === 'recent')

/** 上传前先挡掉传不了的，不用等传完才报错 */
const cannotUpload = (file: File) => {
  const max = usage.value?.maxFileSize
  if (!file.size) return '是空文件，传不了'
  if (max && file.size > max) return `超过 ${formatSize(max)}，传不了`
  return ''
}

const upload = async (fileList: FileList | File[]) => {
  const target = view.value === 'mine' ? folderId.value : null
  const items = [...fileList]
  if (!items.length) return
  await Promise.all(
    items.map(async (file) => {
      const key = ++seq
      const problem = cannotUpload(file)
      if (problem) {
        uploads.value = [...uploads.value, { key, name: file.name, size: file.size, progress: 0, error: problem }]
        return
      }
      const controller = new AbortController()
      controllers.set(key, controller)
      uploads.value = [...uploads.value, { key, name: file.name, size: file.size, progress: 0, error: '' }]
      const patch = (p: Partial<Upload>) => (uploads.value = uploads.value.map((x) => (x.key === key ? { ...x, ...p } : x)))
      try {
        await uploadFile(file, target, (ratio) => patch({ progress: ratio }), controller.signal)
        uploads.value = uploads.value.filter((x) => x.key !== key)
      } catch (error) {
        if (controller.signal.aborted) uploads.value = uploads.value.filter((x) => x.key !== key)
        else patch({ error: errorText(error, '上传失败') })
      } finally {
        controllers.delete(key)
      }
    }),
  )
  const failed = uploads.value.filter((x) => x.error).length
  const done = items.length - failed
  if (done) toast.ok(`已上传 ${done} 个文件${target ? `到「${byId(target)?.name}」` : ''}`)
  load()
}
const cancelUpload = (u: Upload) => controllers.get(u.key)?.abort()
const dismissUpload = (u: Upload) => (uploads.value = uploads.value.filter((x) => x.key !== u.key))
const pick = () => fileInput.value?.click()
const onPicked = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files) upload(input.files)
  input.value = ''
}

const dragging = ref(false)
let dragDepth = 0
const onDragEnter = (event: DragEvent) => {
  if (!canUpload.value || !event.dataTransfer?.types.includes('Files')) return
  dragDepth += 1
  dragging.value = true
}
const onDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (!dragDepth) dragging.value = false
}
const onDrop = (event: DragEvent) => {
  dragDepth = 0
  dragging.value = false
  if (!canUpload.value || !event.dataTransfer?.files.length) return
  upload(event.dataTransfer.files)
}

// ── 文件操作 ──

const download = async (f: SpaceFile) => {
  try {
    saveBlob(await downloadFile(f.id), f.name)
  } catch (error) {
    toast.error(errorText(error, '下载失败'))
  }
}

const trash = async (f: SpaceFile) => {
  try {
    await trashFile(f.id)
    if (selectedId.value === String(f.id)) select(null)
    toast.ok(`「${f.name}」已移到最近删除，30 天内可恢复`, {
      action: {
        label: '撤销',
        run: async () => {
          await restoreFile(f.id)
          load()
        },
      },
    })
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const restore = async (f: SpaceFile) => {
  try {
    await restoreFile(f.id)
    toast.ok(`已恢复「${f.name}」`)
    load()
  } catch (error) {
    toast.error(errorText(error, '恢复失败'))
  }
}

const purge = async (f: SpaceFile) => {
  const ok = await confirm({ title: `彻底删除「${f.name}」？`, message: '彻底删除后无法恢复。', confirmText: '彻底删除', danger: true })
  if (!ok) return
  try {
    await purgeFile(f.id)
    load()
  } catch (error) {
    toast.error(errorText(error, '删除失败'))
  }
}

const purgeLeft = (f: SpaceFile) => Math.max(0, 30 - diffDays(ymdOf(f.deleteTime!), today))

// 新建文件夹 / 重命名：保存成功才关弹窗，失败时弹窗里就地显示原因
const naming = ref<{ title: string; label: string; initial: string; save: (name: string) => Promise<void> } | null>(null)
const newFolder = () =>
  (naming.value = {
    title: '新建文件夹',
    label: '文件夹名',
    initial: '',
    save: async (name) => {
      await createFileFolder({ name, folderId: folderId.value })
      naming.value = null
      load()
    },
  })
const rename = (f: SpaceFile) =>
  (naming.value = {
    title: f.isFolder ? '重命名文件夹' : '重命名文件',
    label: f.isFolder ? '文件夹名' : '文件名',
    initial: f.name,
    save: async (name) => {
      if (name !== f.name) {
        await updateFile(f.id, { name })
        toast.ok(`已改名为「${name}」`)
        load()
      }
      naming.value = null
    },
  })

// 移动
const moving = ref<SpaceFile | null>(null)
const moveTarget = ref<string>('')
/** 可以移过去的文件夹（按层级排好）；移动的是文件夹时，它自己和里面的都不列 */
const folderOptions = computed(() => {
  const out: { id: string; name: string; depth: number }[] = [{ id: '', name: '我的文件', depth: 0 }]
  const walk = (parent: string | null, depth: number) => {
    all.value
      .filter((f) => f.isFolder && f.source === 'upload' && String(f.folderId ?? '') === String(parent ?? '') && String(f.id) !== String(moving.value?.id))
      .sort((a, b) => a.name.localeCompare(b.name, 'zh'))
      .forEach((f) => {
        out.push({ id: String(f.id), name: f.name, depth })
        walk(String(f.id), depth + 1)
      })
  }
  walk(null, 1)
  return out
})
const movingFrom = computed(() => String(moving.value?.folderId ?? ''))
const startMove = (f: SpaceFile) => {
  moving.value = f
  moveTarget.value = String(f.folderId ?? '')
}
const doMove = async () => {
  const f = moving.value
  if (!f) return
  try {
    await updateFile(f.id, { folderId: moveTarget.value || null })
    toast.ok(`已移到「${folderOptions.value.find((o) => o.id === moveTarget.value)?.name}」`)
    moving.value = null
    load()
  } catch (error) {
    toast.error(errorText(error, '移动失败'))
  }
}

// 分享
const sharing = ref<SpaceFile | null>(null)
const revoke = async (s: Share) => {
  try {
    await revokeShare(s.id)
    toast.ok('链接已失效')
    loadSide()
  } catch (error) {
    toast.error(errorText(error, '操作失败'))
  }
}
const shareStatus = (s: Share) => {
  if (s.revoked) return '已取消'
  if (s.expireAt && s.expireAt < `${today} 00:00:00`) return '已过期'
  if (s.maxDownloads !== null && s.downloads >= s.maxDownloads) return '已达下载次数'
  return ''
}
const copyShare = async (s: Share) => {
  try {
    await navigator.clipboard.writeText(`${shareUrl(s.code)}${s.password ? ` 提取码 ${s.password}` : ''}`)
    toast.ok('已复制链接')
  } catch {
    toast.error('复制失败')
  }
}

// ── 容量 ──

const usageBars = computed(() => {
  const u = usage.value
  if (!u) return []
  return (Object.keys(u.byKind) as FileKind[])
    .filter((k) => u.byKind[k])
    .map((k) => ({ key: k, pct: (u.byKind[k] / u.total) * 100, ...KIND_META[k], size: u.byKind[k] }))
    .sort((a, b) => b.size - a.size)
})

// ── 键盘 ──

const onKeydown = (event: KeyboardEvent) => {
  const target = event.target as HTMLElement | null
  if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return
  if (document.querySelector('.dialog-mask, .sp-mask, .qc-mask')) return
  const f = selected.value
  if (!f) return
  if (event.key === 'Delete' && !f.deleteTime) trash(f)
  else if (event.key === 'Enter') openItem(f)
  else if (event.key === 'F2' && !f.deleteTime) rename(f)
  else if (event.key === 'Escape') select(null)
}

const dateText = (stamp: string) => {
  const ymd = ymdOf(stamp)
  return ymd.slice(0, 4) === today.slice(0, 4) ? relativeDay(ymd) : ymd.slice(0, 4)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  Object.values(thumbs.value).forEach((u) => URL.revokeObjectURL(u))
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="files page" :class="{ 'files--prev': selected && view !== 'shared' }">
    <aside class="side">
      <h1 class="page-title">文件柜</h1>
      <SideNav :groups="groups" :active="view" @select="selectView" />
      <div v-if="usage" class="quota">
        <p>已用 <b>{{ formatSize(usage.used) }}</b> / {{ formatSize(usage.total) }}</p>
        <div class="quota__bar" role="img" :aria-label="`已用 ${formatSize(usage.used)}，共 ${formatSize(usage.total)}`">
          <i v-for="b in usageBars" :key="b.key" :style="{ width: `${Math.max(0.6, b.pct)}%`, background: b.color }" />
        </div>
        <p class="quota__legend">
          <span v-for="b in usageBars.slice(0, 4)" :key="b.key"><i :style="{ background: b.color }" />{{ b.label }} {{ formatSize(b.size) }}</span>
        </p>
      </div>
    </aside>

    <section class="main" @dragenter.prevent="onDragEnter" @dragover.prevent @dragleave="onDragLeave" @drop.prevent="onDrop">
      <header class="bar">
        <nav v-if="view === 'mine'" class="crumb" aria-label="位置">
          <button type="button" :class="{ on: !crumbs.length }" @click="openFolder(null)">我的文件</button>
          <template v-for="c in crumbs" :key="c.id">
            <Icon icon="lucide:chevron-right" />
            <button type="button" :class="{ on: c.id === crumbs.at(-1)?.id }" @click="openFolder(c.id)">{{ c.name }}</button>
          </template>
        </nav>
        <h2 v-else class="bar__title">{{ title }}</h2>
        <div class="bar__acts">
          <button v-if="view === 'mine'" class="btn btn--ghost" type="button" @click="newFolder"><Icon icon="lucide:folder-plus" />新建文件夹</button>
          <button v-if="canUpload" class="btn btn--primary" type="button" @click="pick"><Icon icon="lucide:upload" />上传</button>
          <input ref="fileInput" type="file" multiple hidden @change="onPicked" />
        </div>
      </header>

      <p v-if="view === 'trash'" class="hint"><Icon icon="lucide:info" />删除的文件在这里保留 30 天，之后自动彻底删除。</p>
      <p v-else-if="view.startsWith('source:')" class="hint"><Icon icon="lucide:info" />这些是其他模块里的附件，在这里统一看空间占用；删掉后原处也看不到了。</p>

      <div v-for="u in uploads" :key="u.key" class="upl" :class="{ 'upl--err': u.error }">
        <Icon :icon="u.error ? 'lucide:circle-alert' : 'lucide:upload'" />
        <div class="upl__main">
          <span>{{ u.error ? `${u.name}：${u.error}` : `正在上传 ${u.name}` }} <small v-if="!u.error">{{ formatSize(u.size * u.progress) }} / {{ formatSize(u.size) }}</small></span>
          <div v-if="!u.error" class="upl__bar"><i :style="{ width: `${Math.round(u.progress * 100)}%` }" /></div>
        </div>
        <button class="btn btn--quiet btn--sm" type="button" @click="u.error ? dismissUpload(u) : cancelUpload(u)">{{ u.error ? '知道了' : '取消' }}</button>
      </div>

      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="loadError" state="error" :description="loadError" action-label="重试" @action="load" />

      <!-- 我分享的 -->
      <template v-else-if="view === 'shared'">
        <StateBlock v-if="!shares.length" state="empty" title="还没有分享过文件" description="选中一个文件，点右栏的「分享」生成链接。" />
        <table v-else class="shares surface">
          <thead>
            <tr><th>文件</th><th>链接</th><th>下载</th><th>有效期</th><th /></tr>
          </thead>
          <tbody>
            <tr v-for="s in shares" :key="s.id" :class="{ ended: shareStatus(s) }">
              <td><b>{{ s.fileName }}</b><small>{{ s.isFolder ? '文件夹 · ' : '' }}{{ s.password ? '提取码' : '无提取码' }}{{ s.maxDownloads ? ` · 最多 ${s.maxDownloads} 次` : '' }}</small></td>
              <td><code>/s/{{ s.code }}</code></td>
              <td>{{ s.downloads }} 次</td>
              <td>{{ shareStatus(s) || (s.expireAt ? `${monthDay(ymdOf(s.expireAt))}过期` : '永久') }}</td>
              <td class="shares__acts">
                <template v-if="!shareStatus(s)">
                  <button class="btn btn--quiet btn--sm" type="button" @click="copyShare(s)">复制</button>
                  <button class="btn btn--ghost btn--sm" type="button" @click="revoke(s)">失效</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </template>

      <StateBlock
        v-else-if="!list.length && !uploads.length"
        state="empty"
        :title="view === 'trash' ? '最近删除是空的' : view === 'mine' ? '这里还没有文件' : '没有文件'"
        :description="canUpload ? '把文件拖进来，或者点「上传」。证件、合同、发票都可以放。' : ''"
      />

      <div v-else class="grid" role="listbox" :aria-label="title">
        <div
          v-for="f in list"
          :key="f.id"
          class="file"
          :class="{ on: selectedId === String(f.id), 'file--trash': f.deleteTime }"
          role="option"
          :aria-selected="selectedId === String(f.id)"
          tabindex="0"
          :title="f.name"
          @click="select(f)"
          @dblclick="openItem(f)"
          @keydown.enter="openItem(f)"
        >
          <div v-if="f.isFolder" class="thumb thumb--folder"><Icon icon="lucide:folder" /></div>
          <div v-else-if="thumbs[String(f.id)]" class="thumb thumb--img"><img :src="thumbs[String(f.id)]" alt="" /></div>
          <div v-else class="thumb" :style="{ '--k': KIND_META[kindOf(f.name, f.mime)].color }">{{ (extOf(f.name) || '文件').toUpperCase().slice(0, 4) }}</div>
          <b>{{ f.name }}</b>
          <small>
            <template v-if="f.deleteTime">{{ purgeLeft(f) }} 天后彻底删除</template>
            <template v-else>
              <span>{{ f.isFolder ? `${itemsIn(f.id)} 项` : formatSize(f.size) }}</span>
              <span v-if="sharedIds.has(String(f.id))" class="shared">已分享</span>
              <span v-else>{{ dateText(f.updateTime) }}</span>
            </template>
          </small>
          <div v-if="f.deleteTime" class="file__trash">
            <button class="btn btn--ghost btn--sm" type="button" @click.stop="restore(f)">恢复</button>
            <button class="btn btn--quiet btn--sm" type="button" @click.stop="purge(f)">彻底删除</button>
          </div>
        </div>
      </div>

      <div v-if="dragging" class="dropzone">
        <Icon icon="lucide:upload-cloud" />
        <b>松开上传到「{{ view === 'mine' && crumbs.length ? crumbs.at(-1)!.name : '我的文件' }}」</b>
      </div>
    </section>

    <aside v-if="selected && view !== 'shared'" class="prev surface">
      <button class="btn btn--quiet prev__x" type="button" aria-label="关闭预览" @click="select(null)"><Icon icon="lucide:x" /></button>
      <div class="prev__view" :class="{ 'prev__view--click': previewUrl || previewText }" @dblclick="!selected.isFolder && (previewUrl || previewText) && (fullscreen = true)">
        <img v-if="previewUrl && kindOf(selected.name, selected.mime) === 'image'" :src="previewUrl" :alt="selected.name" />
        <iframe v-else-if="previewUrl" :src="previewUrl" :title="selected.name" />
        <pre v-else-if="previewText">{{ previewText }}</pre>
        <div v-else class="prev__ph" :style="{ '--k': selected.isFolder ? '#f59e0b' : KIND_META[kindOf(selected.name, selected.mime)].color }">
          <Icon :icon="selected.isFolder ? 'lucide:folder' : 'lucide:file'" />
          <small v-if="previewNone">{{ demo ? '示例文件没有实际内容；自己上传的图片、PDF、文本能在这里预览' : '预览没加载出来，下载后查看' }}</small>
          <small v-else-if="!selected.isFolder">这种格式不能在浏览器里预览，下载后查看</small>
        </div>
      </div>
      <div class="prev__body">
        <h3>{{ selected.name }}</h3>
        <dl class="kv">
          <dt>{{ selected.isFolder ? '内容' : '大小' }}</dt>
          <dd>{{ selected.isFolder ? `${itemsIn(selected.id)} 项` : formatSize(selected.size) }}</dd>
          <dt>位置</dt><dd>{{ locationOf(selected) }}</dd>
          <dt>上传</dt><dd>{{ ymdOf(selected.createTime) }}</dd>
          <template v-if="sharesOf(selected).length">
            <dt>分享</dt>
            <dd class="shared">{{ sharesOf(selected).length }} 个链接<template v-if="sharesOf(selected)[0]!.expireAt"> · {{ monthDay(ymdOf(sharesOf(selected)[0]!.expireAt!)) }}过期</template></dd>
          </template>
          <template v-if="selected.deleteTime">
            <dt>删除</dt><dd>{{ ymdOf(selected.deleteTime) }}，{{ purgeLeft(selected) }} 天后彻底删除</dd>
          </template>
        </dl>
        <div v-if="selected.deleteTime" class="acts">
          <button class="btn btn--primary acts__main" type="button" @click="restore(selected)"><Icon icon="lucide:undo-2" />恢复</button>
          <button class="btn btn--ghost acts__main acts__purge" type="button" @click="purge(selected)"><Icon icon="lucide:trash-2" />彻底删除</button>
        </div>
        <div v-else class="acts">
          <button v-if="selected.isFolder" class="btn btn--primary acts__main" type="button" @click="openItem(selected)"><Icon icon="lucide:folder-open" />打开</button>
          <button v-else class="btn btn--primary acts__main" type="button" @click="download(selected)"><Icon icon="lucide:download" />下载<small>{{ formatSize(selected.size) }}</small></button>
          <div class="tools">
            <button class="tool" type="button" @click="sharing = selected"><Icon icon="lucide:share-2" /><span>分享</span></button>
            <button v-if="selected.source === 'upload'" class="tool" type="button" @click="startMove(selected)"><Icon icon="lucide:folder-input" /><span>移动</span></button>
            <button class="tool" type="button" title="F2" @click="rename(selected)"><Icon icon="lucide:pencil-line" /><span>重命名</span></button>
            <button class="tool tool--danger" type="button" title="Delete" @click="trash(selected)"><Icon icon="lucide:trash-2" /><span>删除</span></button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 全屏预览 -->
    <Teleport to="body">
      <div v-if="fullscreen && selected" class="full" role="dialog" aria-modal="true" :aria-label="selected.name" @click.self="fullscreen = false">
        <header class="full__bar">
          <b>{{ selected.name }}</b>
          <button class="btn btn--ghost btn--sm" type="button" @click="download(selected)"><Icon icon="lucide:download" />下载</button>
          <button class="btn btn--ghost btn--sm" type="button" aria-label="关闭" @click="fullscreen = false"><Icon icon="lucide:x" /></button>
        </header>
        <img v-if="previewUrl && kindOf(selected.name, selected.mime) === 'image'" :src="previewUrl" :alt="selected.name" @click.self="fullscreen = false" />
        <iframe v-else-if="previewUrl" :src="previewUrl" :title="selected.name" />
        <pre v-else-if="previewText">{{ previewText }}</pre>
        <p v-else class="full__none">{{ previewNone ? (demo ? '示例文件没有实际内容' : '预览没加载出来，下载后查看') : '这个文件不能预览' }}</p>
      </div>
    </Teleport>

    <NameDialog
      :open="Boolean(naming)"
      :title="naming?.title ?? ''"
      :label="naming?.label ?? '名称'"
      :initial="naming?.initial"
      :maxlength="255"
      select-base
      :save="naming?.save ?? (async () => {})"
      @close="naming = null"
    />

    <BaseDialog :open="Boolean(moving)" :title="moving?.isFolder ? '移动文件夹' : '移动文件'" width="28rem" @close="moving = null">
      <div v-if="moving" class="mv">
        <p class="mv__sub">把「<b>{{ moving.name }}</b>」移到：</p>
        <div class="mv__list" role="radiogroup" aria-label="目标文件夹">
          <label v-for="o in folderOptions" :key="o.id" class="mv__opt" :class="{ on: moveTarget === o.id }" :style="{ '--d': o.depth }">
            <input v-model="moveTarget" type="radio" name="move-target" :value="o.id" />
            <Icon :icon="o.id ? (moveTarget === o.id ? 'lucide:folder-open' : 'lucide:folder') : 'lucide:house'" class="mv__icon" />
            <span class="mv__name">{{ o.name }}</span>
            <small v-if="o.id === movingFrom" class="mv__here">当前位置</small>
            <Icon v-else-if="moveTarget === o.id" icon="lucide:check" class="mv__check" />
          </label>
        </div>
        <p v-if="folderOptions.length === 1" class="mv__empty">还没有别的文件夹，先在「我的文件」里新建一个。</p>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="moving = null">取消</button>
        <button class="btn btn--primary" type="button" :disabled="moveTarget === movingFrom" @click="doMove">移到这里</button>
      </template>
    </BaseDialog>

    <ShareDialog :file="sharing" @close="sharing = null" @changed="loadSide" />
  </div>
</template>

<style scoped>
.files {
  display: grid;
  grid-template-columns: 14rem minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.files--prev {
  grid-template-columns: 14rem minmax(0, 1fr) 19rem;
}

.side {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.quota {
  padding: 0.8rem 0.3rem 0;
  border-top: 1px solid var(--color-border);
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.quota b {
  color: var(--color-text-primary);
}

.quota__bar {
  display: flex;
  height: 0.45rem;
  margin: 0.45rem 0;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.quota__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.7rem;
  font-size: 0.72rem;
}

.quota__legend i {
  display: inline-block;
  width: 0.5rem;
  height: 0.5rem;
  margin-right: 0.25rem;
  border-radius: 50%;
}

.main {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
  min-height: 60vh;
}

.bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  min-height: 2.5rem;
}

.bar__title {
  font-size: 1.1rem;
  font-weight: 800;
}

.crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.2rem;
  color: var(--color-text-secondary);
}

.crumb button {
  padding: 0.2rem 0.4rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: inherit;
  font-size: 0.95rem;
  cursor: pointer;
}

.crumb button:hover {
  background: var(--color-bg-soft);
}

.crumb button.on {
  color: var(--color-text-primary);
  font-size: 1.1rem;
  font-weight: 800;
}

.bar__acts {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

.bar__acts .btn {
  padding: 0.4rem 0.8rem;
  white-space: nowrap;
  font-size: 0.86rem;
}

.hint {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.upl {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.9rem;
  border: 1px dashed var(--color-brand);
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  font-size: 0.84rem;
}

.upl--err {
  border-color: var(--color-danger);
  background: var(--color-danger-soft, #fdecec);
  color: var(--color-danger);
}

.upl__main {
  flex: 1;
  min-width: 0;
}

.upl small {
  color: var(--color-text-secondary);
}

.upl__bar {
  height: 0.3rem;
  margin-top: 0.35rem;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-brand) 18%, transparent);
}

.upl__bar i {
  display: block;
  height: 100%;
  background: var(--color-brand);
  transition: width 0.15s linear;
}

.btn--sm {
  padding: 0.28rem 0.6rem;
  font-size: 0.8rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: 0.75rem;
}

.file {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  cursor: pointer;
  user-select: none;
}

.file:hover {
  border-color: color-mix(in srgb, var(--color-brand) 40%, var(--color-border));
}

.file.on {
  border-color: var(--color-brand);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-brand) 25%, transparent);
}

.file b {
  overflow: hidden;
  font-size: 0.84rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file small {
  display: flex;
  justify-content: space-between;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.shared {
  color: var(--color-accent-text) !important;
}

.thumb {
  display: grid;
  place-items: center;
  height: 5.5rem;
  margin-bottom: 0.35rem;
  overflow: hidden;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--k) 12%, var(--color-bg-soft));
  color: var(--k);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.thumb--folder {
  background: color-mix(in srgb, #f59e0b 14%, var(--color-bg-soft));
  color: #f59e0b;
}

.thumb--folder svg {
  width: 2.2rem;
  height: 2.2rem;
}

.thumb--img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file--trash {
  opacity: 0.85;
}

.file__trash {
  display: flex;
  gap: 0.3rem;
  margin-top: 0.3rem;
}

.file__trash .btn {
  flex: 1;
  padding: 0.2rem 0.3rem;
  font-size: 0.74rem;
}

.dropzone {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 2px dashed var(--color-brand);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--color-brand-soft) 88%, transparent);
  color: var(--color-brand);
  pointer-events: none;
}

.dropzone svg {
  width: 2.4rem;
  height: 2.4rem;
}

.shares {
  width: 100%;
  border-collapse: collapse;
  border-radius: var(--radius-lg);
  font-size: 0.86rem;
}

.shares th {
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.74rem;
  font-weight: 600;
  text-align: left;
  color: var(--color-text-secondary);
}

.shares td {
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid var(--color-border);
}

.shares td small {
  display: block;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.shares code {
  font-family: var(--font-mono, monospace);
  font-size: 0.8rem;
}

.shares tr.ended td {
  color: var(--color-text-secondary);
}

.shares tr.ended b {
  font-weight: 400;
  text-decoration: line-through;
}

.shares__acts {
  text-align: right;
  white-space: nowrap;
}

.prev {
  position: sticky;
  top: calc(var(--header-height) + 1.25rem);
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.prev__x {
  position: absolute;
  top: 0.4rem;
  right: 0.4rem;
  z-index: 2;
  padding: 0.3rem;
  background: var(--color-bg-surface);
}

.prev__view {
  display: grid;
  place-items: center;
  height: 13rem;
  background: var(--color-bg-soft);
}

.prev__view--click {
  cursor: zoom-in;
}

.prev__view img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.prev__view iframe {
  width: 100%;
  height: 100%;
  border: 0;
}

.prev__view pre {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0.7rem;
  overflow: auto;
  font-size: 0.72rem;
  white-space: pre-wrap;
}

.prev__ph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  color: var(--k);
  text-align: center;
}

.prev__ph svg {
  width: 2.6rem;
  height: 2.6rem;
}

.prev__ph small {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.prev__body {
  padding: 0.9rem 1rem 1rem;
}

.prev__body h3 {
  margin-bottom: 0.6rem;
  font-size: 0.95rem;
  word-break: break-all;
}

.kv {
  display: grid;
  grid-template-columns: 3rem 1fr;
  gap: 0.35rem 0.6rem;
  margin: 0;
  font-size: 0.8rem;
}

.kv dt {
  color: var(--color-text-secondary);
}

.kv dd {
  margin: 0;
  word-break: break-all;
}

.acts {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1rem;
}

.acts__main {
  width: 100%;
  padding: 0.5rem 0.9rem;
  font-size: 0.88rem;
}

.acts__main svg {
  width: 1rem;
  height: 1rem;
}

.acts__main small {
  font-size: 0.76rem;
  font-weight: 500;
  opacity: 0.8;
}

.acts__purge:not(:disabled):hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

/* 次要操作：等宽的图标按钮 */
.tools {
  display: grid;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: 0.4rem;
}

.tool {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.28rem;
  padding: 0.55rem 0.2rem 0.45rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.tool svg {
  width: 1.05rem;
  height: 1.05rem;
  color: var(--color-text-primary);
  transition: color 0.15s ease;
}

.tool:hover {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.tool:hover svg {
  color: var(--color-brand);
}

.tool--danger:hover {
  border-color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 8%, transparent);
  color: var(--color-danger);
}

.tool--danger:hover svg {
  color: var(--color-danger);
}

.tool:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.full {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3.5rem 1.5rem 1.5rem;
  background: rgba(8, 10, 16, 0.86);
}

.full__bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  color: #fff;
}

.full__bar b {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.full img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.full iframe {
  width: min(60rem, 100%);
  height: 100%;
  border: 0;
  background: #fff;
}

.full pre {
  width: min(50rem, 100%);
  max-height: 100%;
  overflow: auto;
  padding: 1rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  white-space: pre-wrap;
}

.full__none {
  color: #fff;
}

/* 移动弹窗 */
.mv {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 1.1rem 1.35rem 0.3rem;
}

.mv__sub {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.mv__sub b {
  font-weight: 600;
  color: var(--color-text-primary);
  word-break: break-all;
}

.mv__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 18rem;
  overflow-y: auto;
  padding: 0.3rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.mv__opt {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.65rem 0.5rem calc(0.65rem + var(--d) * 1.15rem);
  border-radius: var(--radius-md);
  font-size: 0.88rem;
  cursor: pointer;
}

.mv__opt input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.mv__opt:hover {
  background: var(--color-bg-soft);
}

.mv__opt:has(input:focus-visible) {
  outline: 2px solid var(--color-brand);
  outline-offset: -2px;
}

.mv__icon {
  flex: none;
  width: 1.05rem;
  height: 1.05rem;
  color: #f59e0b;
}

.mv__opt:first-child .mv__icon {
  color: var(--color-text-secondary);
}

.mv__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mv__here {
  flex: none;
  font-size: 0.72rem;
  color: var(--color-text-secondary);
}

.mv__check {
  flex: none;
  width: 1rem;
  height: 1rem;
}

.mv__opt.on {
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 600;
}

.mv__opt.on .mv__icon {
  color: var(--color-brand);
}

.mv__empty {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

@media (max-width: 1200px) {
  .files--prev {
    grid-template-columns: 14rem minmax(0, 1fr);
  }

  .prev {
    position: fixed;
    top: auto;
    right: 1rem;
    bottom: 1rem;
    z-index: 30;
    width: min(20rem, calc(100% - 2rem));
    box-shadow: var(--shadow-lg);
  }
}

@media (max-width: 820px) {
  .files,
  .files--prev {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    position: static;
    min-width: 0;
  }

  .side :deep(.sn) {
    display: flex;
    gap: 0.5rem;
    overflow-x: auto;
  }

  .side :deep(.sn__group) {
    display: flex;
    flex-direction: row;
    gap: 0.2rem;
  }

  .side :deep(.sn__head) {
    display: none;
  }

  .side :deep(.sn__item) {
    flex: none;
    width: auto;
  }

  .shares th:nth-child(2),
  .shares td:nth-child(2) {
    display: none;
  }
}
</style>
