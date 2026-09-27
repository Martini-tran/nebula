<script setup lang="ts">
/**
 * 导入 / 导出记录：回答「上次什么时候导的、导了多少」。
 * 失败行可展开看原因；待处理与处理中的任务可取消；导出记录可按同样范围再导出一次。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import StateBlock from '../../../components/StateBlock.vue'
import {
  cancelExportTask,
  cancelImportTask,
  exportChromeBookmarks,
  fetchExportTasks,
  fetchImportTasks,
} from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import { confirm } from '../../../composables/useConfirm'
import {
  TASK_STATUS_LABEL,
  TaskStatus,
  type EntityId,
  type ExportScope,
  type ExportTask,
  type ImportTask,
} from '../../../types/space'

const emit = defineEmits<{ import: [] }>()

const PAGE_SIZE = 10
const space = useSpaceStore()

const kind = ref<'import' | 'export'>('import')
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const imports = ref<ImportTask[]>([])
const exports = ref<ExportTask[]>([])
const expanded = ref<string | null>(null)
const busyId = ref<string | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

let seq = 0
const load = async () => {
  const current = ++seq
  loading.value = true
  error.value = ''
  try {
    if (kind.value === 'import') {
      const res = await fetchImportTasks({ pageNum: page.value, pageSize: PAGE_SIZE }, true)
      if (current !== seq) return
      imports.value = res?.records ?? []
      total.value = Number(res?.total ?? 0)
    } else {
      const res = await fetchExportTasks({ pageNum: page.value, pageSize: PAGE_SIZE }, true)
      if (current !== seq) return
      exports.value = res?.records ?? []
      total.value = Number(res?.total ?? 0)
    }
  } catch (err) {
    if (current === seq) error.value = errorText(err, '加载失败')
  } finally {
    if (current === seq) loading.value = false
  }
}

watch(kind, () => {
  page.value = 1
  expanded.value = null
  load()
})

onMounted(load)

const statusClass = (status: number) =>
  ({ 0: 'st--wait', 1: 'st--run', 2: 'st--ok', 3: 'st--fail' })[status] ?? ''

const time = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '—')
const num = (value?: number | null, status?: number) =>
  status === TaskStatus.SUCCESS ? (value ?? 0).toLocaleString() : '—'

const cancellable = (status: number) => status === TaskStatus.PENDING || status === TaskStatus.PROCESSING

const cancel = async (id: EntityId) => {
  const ok = await confirm({ title: '取消这个任务？', message: '已经写入的部分不会回滚。', confirmText: '取消任务', cancelText: '不了' })
  if (!ok) return
  busyId.value = String(id)
  try {
    if (kind.value === 'import') await cancelImportTask(id)
    else await cancelExportTask(id)
    toast.ok('任务已取消')
    load()
  } catch (err) {
    toast.error(errorText(err, '取消失败'))
  } finally {
    busyId.value = null
  }
}

const scopeLabel = (task: ExportTask) => {
  if (task.scopeType === 'folder') return `目录 · ${space.findFolder(task.scopeId ?? '')?.name ?? '（已删除）'}`
  if (task.scopeType === 'tag') return `标签 · ${space.findTag(task.scopeId ?? '')?.name ?? '（已删除）'}`
  return '全部书签'
}

const scopeAlive = (task: ExportTask) =>
  task.scopeType === 'folder'
    ? Boolean(space.findFolder(task.scopeId ?? ''))
    : task.scopeType === 'tag'
      ? Boolean(space.findTag(task.scopeId ?? ''))
      : true

const exportAgain = async (task: ExportTask) => {
  const scope: ExportScope =
    task.scopeType === 'folder' || task.scopeType === 'tag'
      ? { scopeType: task.scopeType, scopeId: task.scopeId! }
      : { scopeType: 'all' }
  busyId.value = String(task.id)
  try {
    await exportChromeBookmarks(scope)
    toast.ok('已开始下载')
    load()
  } catch (err) {
    toast.error(errorText(err, '导出失败'))
  } finally {
    busyId.value = null
  }
}

const goPage = (next: number) => {
  page.value = Math.min(Math.max(1, next), totalPages.value)
  load()
}

defineExpose({ reload: load })
</script>

<template>
  <div class="tr">
    <div class="tr__bar">
      <div class="seg" role="tablist" aria-label="记录类型">
        <button type="button" role="tab" :aria-selected="kind === 'import'" :class="{ on: kind === 'import' }" @click="kind = 'import'">导入记录</button>
        <button type="button" role="tab" :aria-selected="kind === 'export'" :class="{ on: kind === 'export' }" @click="kind = 'export'">导出记录</button>
      </div>
      <button class="btn btn--ghost" type="button" :disabled="loading" @click="load">
        <Icon icon="lucide:refresh-cw" :class="{ spin: loading }" />刷新
      </button>
    </div>

    <StateBlock v-if="error" state="error" :description="error" action-label="重试" @action="load" />

    <div v-else class="tr__wrap surface" :class="{ 'tr__wrap--loading': loading }">
      <table v-if="kind === 'import'" class="tbl">
        <thead>
          <tr>
            <th>时间</th>
            <th>来源</th>
            <th>状态</th>
            <th class="n">总数</th>
            <th class="n">新增</th>
            <th class="n">重复</th>
            <th class="n">失败</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <template v-for="t in imports" :key="t.id">
            <tr>
              <td class="nowrap">{{ time(t.createTime) }}</td>
              <td>{{ t.source === 'chrome' ? 'Chrome / Edge HTML' : (t.source ?? '—') }}</td>
              <td><span class="st" :class="statusClass(t.status)">{{ TASK_STATUS_LABEL[t.status] ?? t.status }}</span></td>
              <td class="n">{{ t.totalCount != null ? t.totalCount.toLocaleString() : '—' }}</td>
              <td class="n">{{ num(t.successCount, t.status) }}</td>
              <td class="n">{{ num(t.duplicateCount, t.status) }}</td>
              <td class="n">{{ num(t.failCount, t.status) }}</td>
              <td class="acts">
                <button v-if="cancellable(t.status)" type="button" class="link" :disabled="busyId === String(t.id)" @click="cancel(t.id)">取消</button>
                <button
                  v-else-if="t.errorMsg"
                  type="button"
                  class="link"
                  :aria-expanded="expanded === String(t.id)"
                  @click="expanded = expanded === String(t.id) ? null : String(t.id)"
                >
                  {{ expanded === String(t.id) ? '收起' : '详情' }}
                </button>
              </td>
            </tr>
            <tr v-if="expanded === String(t.id)" class="detail">
              <td colspan="8"><b>失败原因：</b>{{ t.errorMsg }}</td>
            </tr>
          </template>
        </tbody>
      </table>

      <table v-else class="tbl">
        <thead>
          <tr>
            <th>时间</th>
            <th>范围</th>
            <th>格式</th>
            <th>状态</th>
            <th class="n">条数</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <template v-for="t in exports" :key="t.id">
            <tr>
              <td class="nowrap">{{ time(t.createTime) }}</td>
              <td>{{ scopeLabel(t) }}</td>
              <td>{{ t.exportType === 'chrome' ? 'Chrome HTML' : (t.exportType ?? '—') }}</td>
              <td><span class="st" :class="statusClass(t.status)">{{ TASK_STATUS_LABEL[t.status] ?? t.status }}</span></td>
              <td class="n">{{ num(t.totalCount, t.status) }}</td>
              <td class="acts">
                <button v-if="cancellable(t.status)" type="button" class="link" :disabled="busyId === String(t.id)" @click="cancel(t.id)">取消</button>
                <button
                  v-else-if="t.status === TaskStatus.FAIL && t.errorMsg"
                  type="button"
                  class="link"
                  @click="expanded = expanded === String(t.id) ? null : String(t.id)"
                >
                  {{ expanded === String(t.id) ? '收起' : '详情' }}
                </button>
                <button
                  v-if="scopeAlive(t)"
                  type="button"
                  class="link"
                  :disabled="busyId === String(t.id)"
                  title="按同样范围再导出一次"
                  @click="exportAgain(t)"
                >
                  再导出
                </button>
              </td>
            </tr>
            <tr v-if="expanded === String(t.id)" class="detail">
              <td colspan="6"><b>失败原因：</b>{{ t.errorMsg }}</td>
            </tr>
          </template>
        </tbody>
      </table>

      <div v-if="!loading && !(kind === 'import' ? imports : exports).length" class="tr__empty">
        <p>{{ kind === 'import' ? '还没有导入过书签' : '还没有导出过书签' }}</p>
        <button v-if="kind === 'import'" class="btn btn--primary" type="button" @click="emit('import')">
          <Icon icon="lucide:upload" />导入浏览器书签
        </button>
      </div>
    </div>

    <nav v-if="totalPages > 1" class="pager" aria-label="分页">
      <button class="btn btn--ghost" type="button" :disabled="page <= 1" @click="goPage(page - 1)"><Icon icon="lucide:chevron-left" />上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button class="btn btn--ghost" type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页<Icon icon="lucide:chevron-right" /></button>
    </nav>
  </div>
</template>

<style scoped>
.tr {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.tr__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.tr__bar .btn {
  padding: 0.4rem 0.8rem;
  font-size: 0.86rem;
}

.seg {
  display: inline-flex;
  padding: 0.2rem;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.seg button {
  padding: 0.35rem 0.9rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}

.seg button.on {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-sm);
}

.tr__wrap {
  overflow-x: auto;
  border-radius: var(--radius-lg);
  transition: opacity 0.2s ease;
}

.tr__wrap--loading {
  opacity: 0.6;
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.tbl th {
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-align: left;
  white-space: nowrap;
}

.tbl td {
  padding: 0.55rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.tbl .n {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.nowrap {
  white-space: nowrap;
}

.st {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.1rem 0.55rem;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 600;
  white-space: nowrap;
}

.st::before {
  content: '';
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: currentColor;
}

.st--wait {
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
}

.st--run {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.st--ok {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.st--fail {
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
}

.acts {
  text-align: right;
  white-space: nowrap;
}

.link {
  margin-left: 0.6rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-weight: 600;
  cursor: pointer;
}

.link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.detail td {
  background: color-mix(in srgb, var(--color-danger) 6%, transparent);
  font-size: 0.84rem;
  line-height: 1.6;
  color: var(--color-danger);
}

.tr__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2.5rem 1rem;
  color: var(--color-text-secondary);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}
</style>
