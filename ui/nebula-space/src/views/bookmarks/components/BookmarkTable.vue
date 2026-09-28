<script setup lang="ts">
/**
 * 列表视图：一行一条、信息密度高，整理大量导入书签时用。
 * 行首复选框可多选，Shift 点击连选；点行（非链接、按钮处）打开详情。
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import FaviconMark from './FaviconMark.vue'
import { useSpaceStore } from '../../../stores/space'
import { formatDate } from '../../../utils/format'
import { BookmarkStatus, type Bookmark } from '../../../types/space'

const props = defineProps<{
  bookmarks: Bookmark[]
  selectedIds: Set<string>
  /** 当前筛选就是某个目录时，目录列没有信息量，隐藏 */
  showFolder: boolean
}>()
const emit = defineEmits<{
  open: [bookmark: Bookmark]
  select: [bookmark: Bookmark, range: boolean]
  toggleAll: []
  edit: [bookmark: Bookmark]
  move: [bookmark: Bookmark]
  toggleArchive: [bookmark: Bookmark]
  remove: [bookmark: Bookmark]
}>()

const space = useSpaceStore()
const SOURCE_LABEL: Record<string, string> = { manual: '手动', chrome: 'Chrome 导入', import: '导入' }

const allChecked = computed(() => props.bookmarks.length > 0 && props.bookmarks.every((b) => props.selectedIds.has(String(b.id))))
const someChecked = computed(() => !allChecked.value && props.bookmarks.some((b) => props.selectedIds.has(String(b.id))))

const onRowClick = (event: MouseEvent, bookmark: Bookmark) => {
  if ((event.target as HTMLElement).closest('a, button, input')) return
  if (props.selectedIds.size || event.shiftKey || event.ctrlKey || event.metaKey) emit('select', bookmark, event.shiftKey)
  else emit('open', bookmark)
}

const host = (b: Bookmark) => b.domain || b.url.replace(/^\w+:\/\//, '').split('/')[0]
</script>

<template>
  <div class="tbl-wrap surface">
    <table class="tbl">
      <thead>
        <tr>
          <th class="tbl__check">
            <input
              type="checkbox"
              :checked="allChecked"
              :indeterminate="someChecked"
              aria-label="选择本页全部"
              @change="emit('toggleAll')"
            />
          </th>
          <th>标题</th>
          <th v-if="showFolder" class="tbl__folder">目录</th>
          <th class="tbl__tags">标签</th>
          <th class="tbl__src">来源</th>
          <th class="tbl__time">添加时间</th>
          <th class="tbl__acts"><span class="sr-only">操作</span></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="b in bookmarks"
          :key="b.id"
          :class="{
            'tbl__row--on': selectedIds.has(String(b.id)),
            'tbl__row--muted': b.status !== BookmarkStatus.NORMAL,
          }"
          @click="onRowClick($event, b)"
        >
          <td class="tbl__check">
            <input
              type="checkbox"
              :checked="selectedIds.has(String(b.id))"
              :aria-label="`选择 ${b.title}`"
              @click.stop="emit('select', b, ($event as MouseEvent).shiftKey)"
            />
          </td>
          <td>
            <div class="tbl__title">
              <FaviconMark :bookmark="b" size="1.6rem" />
              <span class="tbl__text">
                <a :href="b.url" target="_blank" rel="noopener noreferrer" :title="b.url">{{ b.title }}</a>
                <small>
                  {{ host(b) }}
                  <em v-if="b.status === BookmarkStatus.BROKEN" class="tbl__broken" :title="b.checkResult ?? undefined">已失效</em>
                </small>
              </span>
            </div>
          </td>
          <td v-if="showFolder" class="tbl__folder">
            <span class="tbl__ellipsis" :title="space.folderPath(b.folderId)">{{ space.folderPath(b.folderId) }}</span>
          </td>
          <td class="tbl__tags">
            <span v-for="tag in b.tags ?? []" :key="tag.id" class="tag">
              <span class="dot" :style="{ background: tag.color || 'var(--color-text-secondary)' }" />{{ tag.name }}
            </span>
            <span v-if="!b.tags?.length" class="muted">—</span>
          </td>
          <td class="tbl__src">{{ SOURCE_LABEL[b.source ?? ''] ?? b.source ?? '—' }}</td>
          <td class="tbl__time">{{ formatDate(b.createTime) }}</td>
          <td class="tbl__acts">
            <span class="tbl__btns">
              <button type="button" title="编辑" @click="emit('edit', b)"><Icon icon="lucide:pencil" /></button>
              <button type="button" title="移动到…" @click="emit('move', b)"><Icon icon="lucide:folder-input" /></button>
              <button
                type="button"
                :title="b.status === BookmarkStatus.ARCHIVED ? '恢复' : '归档'"
                @click="emit('toggleArchive', b)"
              >
                <Icon :icon="b.status === BookmarkStatus.ARCHIVED ? 'lucide:archive-restore' : 'lucide:archive'" />
              </button>
              <button type="button" title="删除" class="danger" @click="emit('remove', b)"><Icon icon="lucide:trash-2" /></button>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.tbl-wrap {
  overflow-x: auto;
  border-radius: var(--radius-lg);
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.tbl th {
  padding: 0.6rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-align: left;
  white-space: nowrap;
}

.tbl td {
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
}

.tbl tbody tr:last-child td {
  border-bottom: 0;
}

.tbl tbody tr {
  cursor: pointer;
}

.tbl tbody tr:hover {
  background: var(--color-bg-soft);
}

.tbl__row--on,
.tbl__row--on:hover {
  background: var(--color-brand-soft) !important;
}

.tbl__row--muted .tbl__title {
  opacity: 0.7;
}

.tbl__check {
  width: 2.4rem;
  padding-left: 0.9rem !important;
}

.tbl__check input {
  width: 1rem;
  height: 1rem;
  margin: 0;
  cursor: pointer;
}

.tbl__title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 16rem;
}

.tbl__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.tbl__text a {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 28rem;
}

.tbl__text a:hover {
  color: var(--color-brand);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.tbl__text small {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.tbl__broken {
  margin-left: 0.3rem;
  font-style: normal;
  color: var(--color-danger);
}

.tbl__folder {
  max-width: 12rem;
  color: var(--color-text-secondary);
}

.tbl__ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tbl__tags {
  min-width: 7rem;
}

.tbl__tags .tag {
  margin: 0.1rem 0.25rem 0.1rem 0;
}

.dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
}

.muted {
  color: var(--color-text-secondary);
}

.tbl__src,
.tbl__time {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.tbl__acts {
  width: 8rem;
  text-align: right;
}

.tbl__btns {
  display: inline-flex;
  opacity: 0;
}

tr:hover .tbl__btns,
tr:focus-within .tbl__btns {
  opacity: 1;
}

@media (hover: none) {
  .tbl__btns {
    opacity: 1;
  }
}

.tbl__btns button {
  display: inline-grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.tbl__btns button:hover {
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.tbl__btns .danger:hover {
  color: var(--color-danger);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

@media (max-width: 1100px) {
  .tbl__src,
  .tbl__folder {
    display: none;
  }
}

@media (max-width: 760px) {
  .tbl__time,
  .tbl__tags {
    display: none;
  }

  .tbl__title {
    min-width: 0;
  }

  .tbl__text a {
    max-width: 55vw;
  }
}
</style>
