<script setup lang="ts">
/**
 * 书签详情抽屉：点卡片空白处从右侧滑出，工作台不跳走。
 * 展示卡片放不下的：完整网址、目录路径、来源、访问、备注。E 编辑 · M 移动 · Esc 关闭。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import FaviconMark from './FaviconMark.vue'
import { useSpaceStore } from '../../../stores/space'
import { formatDate, formatRelative } from '../../../utils/format'
import { toast } from '../../../composables/useToast'
import { BookmarkStatus, type Bookmark, type EntityId } from '../../../types/space'

const props = defineProps<{ bookmark: Bookmark | null }>()
const emit = defineEmits<{
  close: []
  edit: [bookmark: Bookmark]
  move: [bookmark: Bookmark]
  toggleArchive: [bookmark: Bookmark]
  remove: [bookmark: Bookmark]
  openFolder: [id: EntityId]
  openTag: [id: EntityId]
}>()

const space = useSpaceStore()
const panel = ref<HTMLElement | null>(null)

const SOURCE_LABEL: Record<string, string> = { manual: '手动添加', chrome: 'Chrome 导入', import: '导入' }

const b = computed(() => props.bookmark)
const archived = computed(() => b.value?.status === BookmarkStatus.ARCHIVED)
const broken = computed(() => b.value?.status === BookmarkStatus.BROKEN)
const inFolder = computed(() => b.value && String(b.value.folderId) !== '0')

const copyLink = async () => {
  if (!b.value) return
  try {
    await navigator.clipboard.writeText(b.value.url)
    toast.ok('链接已复制')
  } catch {
    toast.error('复制失败，请手动选择网址复制')
  }
}

const onKeydown = (event: KeyboardEvent) => {
  if (!b.value) return
  const target = event.target as HTMLElement | null
  if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return
  // 抽屉上面叠了弹窗时，快捷键交给弹窗
  if (document.querySelector('.dialog-mask')) return
  if (event.ctrlKey || event.metaKey || event.altKey) return
  const key = event.key.toLowerCase()
  if (key === 'escape') emit('close')
  else if (key === 'e') emit('edit', b.value)
  else if (key === 'm') emit('move', b.value)
}

watch(
  b,
  (value, old) => {
    if (value && !old) {
      window.addEventListener('keydown', onKeydown)
      requestAnimationFrame(() => panel.value?.focus())
    }
    if (!value) window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <transition name="drawer">
      <div v-if="b" class="drawer-mask" @click.self="emit('close')">
        <aside ref="panel" class="drawer" role="dialog" aria-modal="true" :aria-label="`书签详情：${b.title}`" tabindex="-1">
          <header class="drawer__head">
            <FaviconMark :bookmark="b" size="2.6rem" />
            <div class="drawer__heading">
              <h2>{{ b.title }}</h2>
              <a :href="b.url" target="_blank" rel="noopener noreferrer" class="drawer__url">{{ b.url }}</a>
            </div>
            <button class="btn btn--quiet drawer__close" type="button" aria-label="关闭" @click="emit('close')">
              <Icon icon="lucide:x" />
            </button>
          </header>

          <div class="drawer__quick">
            <a class="btn btn--primary" :href="b.url" target="_blank" rel="noopener noreferrer">
              <Icon icon="lucide:external-link" />打开
            </a>
            <button class="btn btn--ghost" type="button" @click="copyLink"><Icon icon="lucide:copy" />复制链接</button>
          </div>

          <p v-if="archived" class="drawer__notice">已归档：不出现在「全部书签」和目录里，也不会被导出。</p>
          <p v-if="broken" class="drawer__notice drawer__notice--warn">
            链接检查发现这个网址打不开了。可能是站点临时维护，确认失效后再归档或删除。
          </p>

          <dl class="drawer__meta">
            <dt>目录</dt>
            <dd>
              <button v-if="inFolder" type="button" class="drawer__link" @click="emit('openFolder', b.folderId)">
                <Icon icon="lucide:folder" />{{ space.folderPath(b.folderId) }}
              </button>
              <span v-else>未分类</span>
            </dd>
            <dt>标签</dt>
            <dd class="drawer__tags">
              <button v-for="tag in b.tags ?? []" :key="tag.id" type="button" class="tag" @click="emit('openTag', tag.id)">
                <span class="dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />{{ tag.name }}
              </button>
              <span v-if="!b.tags?.length" class="muted">—</span>
            </dd>
            <dt>来源</dt>
            <dd>{{ SOURCE_LABEL[b.source ?? ''] ?? b.source ?? '—' }}</dd>
            <dt>收藏于</dt>
            <dd>{{ formatDate(b.createTime) || '—' }}</dd>
            <dt>最近访问</dt>
            <dd>
              <template v-if="b.lastVisitTime">{{ formatRelative(b.lastVisitTime) }} · 共 {{ b.visitCount ?? 0 }} 次</template>
              <span v-else class="muted">还没有访问记录</span>
            </dd>
          </dl>

          <section v-if="b.description" class="drawer__block">
            <h3>描述</h3>
            <p>{{ b.description }}</p>
          </section>
          <section class="drawer__block">
            <h3>备注</h3>
            <p v-if="b.remark" class="drawer__remark">{{ b.remark }}</p>
            <button v-else type="button" class="drawer__add" @click="emit('edit', b)">
              <Icon icon="lucide:plus" />写一句为什么收藏它
            </button>
          </section>

          <footer class="drawer__foot">
            <button class="btn btn--ghost" type="button" @click="emit('edit', b)"><Icon icon="lucide:pencil" />编辑<kbd>E</kbd></button>
            <button class="btn btn--ghost" type="button" @click="emit('move', b)"><Icon icon="lucide:folder-input" />移动<kbd>M</kbd></button>
            <button class="btn btn--ghost" type="button" @click="emit('toggleArchive', b)">
              <Icon :icon="archived ? 'lucide:archive-restore' : 'lucide:archive'" />{{ archived ? '恢复' : '归档' }}
            </button>
            <button class="btn btn--ghost drawer__danger" type="button" @click="emit('remove', b)">
              <Icon icon="lucide:trash-2" />删除
            </button>
          </footer>
        </aside>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(8, 10, 16, 0.25);
}

.drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  width: min(26rem, 100%);
  padding: 1.25rem 1.35rem calc(1.1rem + env(safe-area-inset-bottom, 0px));
  padding-top: calc(1.25rem + env(safe-area-inset-top, 0px));
  overflow-y: auto;
  background: var(--color-bg-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
  outline: none;
}

.drawer__head {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
}

.drawer__heading {
  flex: 1;
  min-width: 0;
}

.drawer__heading h2 {
  font-size: 1.1rem;
  font-weight: 800;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.drawer__url {
  display: block;
  margin-top: 0.2rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}

.drawer__url:hover {
  color: var(--color-brand);
}

.drawer__close {
  padding: 0.35rem;
}

.drawer__quick {
  display: flex;
  gap: 0.5rem;
}

.drawer__quick .btn {
  flex: 1;
}

.drawer__notice {
  padding: 0.6rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 0.84rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.drawer__notice--warn {
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
  color: var(--color-danger);
}

.drawer__meta {
  display: grid;
  grid-template-columns: 4.5rem 1fr;
  gap: 0.6rem 0.75rem;
  margin: 0;
  font-size: 0.88rem;
}

.drawer__meta dt {
  color: var(--color-text-secondary);
}

.drawer__meta dd {
  margin: 0;
  min-width: 0;
}

.drawer__link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  text-align: left;
  cursor: pointer;
}

.drawer__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.drawer__tags button {
  border: 0;
  cursor: pointer;
}

.dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
}

.muted {
  color: var(--color-text-secondary);
}

.drawer__block h3 {
  margin-bottom: 0.35rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.drawer__block p {
  font-size: 0.9rem;
  line-height: 1.7;
  white-space: pre-wrap;
}

.drawer__remark {
  padding: 0.6rem 0.75rem;
  border-left: 3px solid var(--color-accent);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-accent-soft);
}

.drawer__add {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  cursor: pointer;
}

.drawer__add:hover {
  color: var(--color-brand);
}

.drawer__foot {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
  margin-top: auto;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.drawer__foot kbd {
  margin-left: 0.2rem;
}

.drawer__danger:not(:disabled):hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.drawer-enter-active,
.drawer-leave-active {
  transition: background var(--duration-panel) ease;
}

.drawer-enter-active .drawer,
.drawer-leave-active .drawer {
  transition: transform var(--duration-panel) var(--ease-soft);
}

.drawer-enter-from,
.drawer-leave-to {
  background: transparent;
}

.drawer-enter-from .drawer,
.drawer-leave-to .drawer {
  transform: translateX(100%);
}

@media (prefers-reduced-motion: reduce) {
  .drawer-enter-active .drawer,
  .drawer-leave-active .drawer {
    transition: none;
  }
}
</style>
