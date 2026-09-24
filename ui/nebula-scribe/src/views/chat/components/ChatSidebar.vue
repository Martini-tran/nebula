<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { Conversations } from 'vue-element-plus-x'
import type { ConversationItem, ConversationMenu, ConversationMenuCommand } from 'vue-element-plus-x/types/Conversations'
import ThemeToggle from '../../../components/ThemeToggle.vue'
import { useAuthStore } from '../../../stores/auth'
import type { DialogListItem } from '../../../types/dialog'
import type { EntityId } from '../../../types/work'

const props = defineProps<{
  dialogs: DialogListItem[]
  activeId: EntityId | null
  loading: boolean
}>()

const keyword = defineModel<string>('keyword', { required: true })

const emit = defineEmits<{
  select: [id: EntityId]
  create: []
  rename: [item: DialogListItem]
  remove: [item: DialogListItem]
  collapse: []
}>()

const router = useRouter()
const authStore = useAuthStore()

const GROUP_ORDER = ['今天', '昨天', '最近 7 天', '更早']

const groupOf = (time: string) => {
  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  const ts = new Date(time).getTime()
  const day = 24 * 3600_000
  if (ts >= startOfToday.getTime()) return '今天'
  if (ts >= startOfToday.getTime() - day) return '昨天'
  if (ts >= startOfToday.getTime() - 7 * day) return '最近 7 天'
  return '更早'
}

type Row = ConversationItem<DialogListItem & { key: string }>

/** Conversations 按 key 比较选中项，统一转成字符串，避免数字/字符串 id 对不上 */
const items = computed<Row[]>(() =>
  props.dialogs.map((item) => ({
    ...item,
    key: String(item.id),
    label: item.title,
    group: groupOf(item.updateTime),
  })),
)

const active = computed(() => (props.activeId == null ? undefined : String(props.activeId)))

const groupable = {
  sort: (a: string, b: string) => GROUP_ORDER.indexOf(a) - GROUP_ORDER.indexOf(b),
}

/** 组件默认内联 width:280px + 白底 + 内边距，会撑出侧栏并横向滚动，这里整体替换（与默认值浅合并） */
const listStyle = { backgroundColor: 'transparent', width: '100%', padding: '0', borderRadius: '0' }

const menu: ConversationMenu[] = [
  { label: '重命名', key: 'rename', command: 'rename' },
  { label: '删除', key: 'delete', command: 'delete', menuItemStyle: { color: '#dc2626' } },
]

const onChange = (item: ConversationItem) => {
  emit('select', (item as Row).id)
}

const onMenu = (command: ConversationMenuCommand, item: ConversationItem) => {
  const row = props.dialogs.find((dialog) => String(dialog.id) === (item as Row).key)
  if (!row) return
  if (command === 'rename') emit('rename', row)
  if (command === 'delete') emit('remove', row)
}
</script>

<template>
  <aside class="side" aria-label="会话列表">
    <div class="side__top">
      <button class="side__brand" type="button" title="回到 Scribe 首页" @click="router.push('/')">
        <Icon icon="lucide:chevron-left" />
        Scribe
      </button>
      <button class="icon-btn side__collapse" type="button" title="收起侧栏" aria-label="收起侧栏" @click="emit('collapse')">
        <Icon icon="lucide:panel-left-close" />
      </button>
    </div>

    <div class="side__actions">
      <button class="btn btn--primary side__new" type="button" @click="emit('create')">
        <Icon icon="lucide:plus" />
        新对话
      </button>
      <label class="search">
        <Icon icon="lucide:search" class="search__icon" />
        <input v-model="keyword" class="search__input" type="search" placeholder="搜索对话" aria-label="搜索对话" />
      </label>
    </div>

    <div class="side__list">
      <p v-if="loading && dialogs.length === 0" class="side__empty">加载中…</p>
      <p v-else-if="dialogs.length === 0" class="side__empty">{{ keyword ? '没有匹配的对话' : '还没有对话' }}</p>
      <Conversations
        v-else
        :active="active"
        :items="items"
        row-key="key"
        :groupable="groupable"
        :menu="menu"
        show-built-in-menu
        :label-max-width="170"
        :label-height="38"
        :show-tooltip="false"
        :style="listStyle"
        @change="onChange"
        @menu-command="onMenu"
      >
        <template #label="{ item }">
          <span class="conv">
            <span class="conv__title">{{ item.label }}</span>
            <span v-if="item.workTitle" class="conv__work">《{{ item.workTitle }}》</span>
          </span>
        </template>
      </Conversations>
    </div>

    <div class="side__foot">
      <span class="user" :title="authStore.user?.username">
        <span class="user__avatar" aria-hidden="true">{{ authStore.displayName.slice(0, 1) || '我' }}</span>
        <span class="user__name">{{ authStore.displayName }}</span>
      </span>
      <ThemeToggle />
    </div>
  </aside>
</template>

<style scoped lang="scss">
.side {
  display: flex;
  flex-direction: column;
  width: 16rem;
  height: 100%;
  background: var(--color-bg-soft);
  border-right: 1px solid var(--color-border);
}

.side__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.8rem 0.8rem 0.4rem;
}

.side__brand {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.45rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  font-weight: 800;
  cursor: pointer;
}

.side__brand:hover {
  background: var(--color-bg-surface);
}

.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
}

.icon-btn svg,
.side__brand svg {
  width: 1.1rem;
  height: 1.1rem;
}

.side__actions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.4rem 0.8rem 0.6rem;
}

.side__new {
  justify-content: center;
}

.side__new svg {
  width: 1rem;
  height: 1rem;
}

.search {
  position: relative;
}

.search__icon {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  width: 1rem;
  height: 1rem;
  transform: translateY(-50%);
  color: var(--color-text-secondary);
}

.search__input {
  width: 100%;
  padding: 0.45rem 0.6rem 0.45rem 2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}

.search__input:focus {
  outline: none;
  border-color: var(--color-brand);
}

.side__list {
  flex: 1;
  min-height: 0;
  padding: 0 0.4rem 0.4rem;
}

/* 颜色覆盖在 styles/element-bridge.scss；这里只调侧栏里的尺寸与选中标记 */
.side__list :deep(.elx-conversations) {
  /* 分组标题吸顶时的底色，要与侧栏一致，否则滚动时会和列表项叠在一起 */
  --elx-conversations-list-auto-bg-color: var(--color-bg-soft);
  width: 100%;
  background: transparent;
  box-shadow: none;
}

.side .side__list :deep(.elx-conversations-item) {
  margin-right: 0.2rem;
  padding: 0.55rem 0.6rem;
}

.side .side__list :deep(.elx-conversations-item--active) {
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.side__empty {
  padding: 1.5rem 0.8rem;
  font-size: 0.85rem;
  text-align: center;
  color: var(--color-text-secondary);
}

.conv {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.conv__title,
.conv__work {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv__title {
  font-size: 0.9rem;
}

.conv__work {
  margin-top: 0.1rem;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.side__foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.7rem 0.8rem;
  border-top: 1px solid var(--color-border);
}

.user {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  font-size: 0.9rem;
  font-weight: 600;
}

.user__avatar {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.85rem;
}

.user__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
