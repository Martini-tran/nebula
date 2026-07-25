<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiKnowledgeApi } from '#/api';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAiKnowledgeDocumentApi,
  getAiKnowledgeDocumentPageApi,
  importAiKnowledgeDocumentApi,
  searchAiKnowledgeApi,
} from '#/api';

defineOptions({ name: 'DocumentDrawer' });

/** 文档状态：0=索引中 1=完成 2=失败 */
const DOC_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '索引中', type: 'warning' },
  1: { text: '完成', type: 'success' },
  2: { text: '失败', type: 'danger' },
};

/** 文档状态 → 展示（含未知兜底） */
function docStatus(status?: number) {
  return (
    DOC_STATUS[status as number] ?? { text: '未知', type: 'info' as const }
  );
}

const visible = ref(false);
/** 当前操作的知识库 */
const kb = ref<AiKnowledgeApi.KbItem>();
const kbCode = computed(() => kb.value?.kbCode ?? '');

// —— 文档列表 ——

const gridOptions: VxeTableGridOptions<AiKnowledgeApi.DocItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'docId', title: '文档标识', minWidth: 140 },
    { field: 'title', title: '标题', minWidth: 160 },
    { field: 'sourceType', title: '类型', width: 90, align: 'center' },
    { field: 'charCount', title: '字符数', width: 90, align: 'right' },
    { field: 'chunkCount', title: '切块数', width: 90, align: 'right' },
    {
      field: 'status',
      title: '状态',
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'createTime',
      title: '导入时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      width: 90,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  height: 420,
  keepSource: true,
  pagerConfig: { pageSize: 10 },
  proxyConfig: {
    autoLoad: false,
    response: { result: 'records', total: 'total' },
    ajax: {
      query: async ({ page }) => {
        if (!kbCode.value) {
          return { records: [], total: 0 };
        }
        return await getAiKnowledgeDocumentPageApi(kbCode.value, {
          pageNum: page.currentPage,
          pageSize: page.pageSize,
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  toolbarConfig: {
    custom: false,
    refresh: { code: 'query' },
    search: false,
    zoom: false,
  },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });

function reloadDocs() {
  gridApi.query();
}

async function open(row: AiKnowledgeApi.KbItem) {
  kb.value = row;
  visible.value = true;
  searchHits.value = null;
  searchKeyword.value = '';
  // 抽屉渲染后再拉取，避免容器尚未挂载导致高度异常
  await Promise.resolve();
  reloadDocs();
}

// —— 导入弹窗 ——

const importVisible = ref(false);
const importLoading = ref(false);
const importFormRef = ref<FormInstance>();

const importForm = reactive<{
  content: string;
  docId: string;
  sourceType: string;
  sourceUri: string;
  title: string;
}>({
  content: '',
  docId: '',
  sourceType: '',
  sourceUri: '',
  title: '',
});

const importRules: FormRules = {
  content: [
    { required: true, message: '请输入正文文本', trigger: 'blur' },
    { min: 1, message: '正文不能为空', trigger: 'blur' },
  ],
  docId: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function resetImportForm() {
  importForm.content = '';
  importForm.docId = '';
  importForm.sourceType = '';
  importForm.sourceUri = '';
  importForm.title = '';
  importFormRef.value?.clearValidate();
}

function openImport() {
  resetImportForm();
  importVisible.value = true;
}

async function submitImport() {
  if (!importFormRef.value || !kbCode.value) return;
  const valid = await importFormRef.value.validate().catch(() => false);
  if (!valid) return;

  importLoading.value = true;
  try {
    const chunks = await importAiKnowledgeDocumentApi(kbCode.value, {
      content: importForm.content,
      docId: importForm.docId || undefined,
      title: importForm.title || undefined,
      sourceType: importForm.sourceType || undefined,
      sourceUri: importForm.sourceUri || undefined,
    });
    ElMessage.success(`导入成功，生成 ${chunks} 个切块`);
    importVisible.value = false;
    reloadDocs();
  } finally {
    importLoading.value = false;
  }
}

async function handleDeleteDoc(row: AiKnowledgeApi.DocItem) {
  if (!kbCode.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除文档「${row.title || row.docId}」？其切片与向量将一并清除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteAiKnowledgeDocumentApi(kbCode.value, row.docId);
  ElMessage.success('删除成功');
  reloadDocs();
}

// —— 检索预览 ——

const searchKeyword = ref('');
const searchTopK = ref<number>(5);
const searchLoading = ref(false);
const searchHits = ref<AiKnowledgeApi.SearchHit[] | null>(null);

async function runSearch() {
  if (!kbCode.value) return;
  const q = searchKeyword.value.trim();
  if (!q) {
    ElMessage.warning('请输入检索关键词');
    return;
  }
  searchLoading.value = true;
  try {
    searchHits.value = await searchAiKnowledgeApi(
      kbCode.value,
      q,
      searchTopK.value,
    );
  } finally {
    searchLoading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDrawer
    v-model="visible"
    :size="880"
    :title="`知识库文档 · ${kb?.name || kb?.kbCode || ''}`"
    direction="rtl"
  >
    <div class="flex h-full flex-col gap-4">
      <!-- 文档列表 -->
      <section>
        <div class="mb-2 flex items-center justify-between">
          <span class="text-sm font-medium">文档</span>
          <ElButton
            v-access:code="'manager:ai-knowledge:import'"
            size="small"
            type="primary"
            @click="openImport"
          >
            导入文档
          </ElButton>
        </div>
        <Grid>
          <template #status="{ row }">
            <ElTag :type="(docStatus(row.status).type as any)" size="small">
              {{ docStatus(row.status).text }}
            </ElTag>
          </template>
          <template #action="{ row }">
            <ElButton
              v-access:code="'manager:ai-knowledge:import'"
              link
              type="danger"
              @click="handleDeleteDoc(row)"
            >
              删除
            </ElButton>
          </template>
        </Grid>
      </section>

      <!-- 检索预览 -->
      <section
        v-access:code="'manager:ai-knowledge:query'"
        class="flex min-h-0 flex-1 flex-col"
      >
        <div class="mb-2 text-sm font-medium">检索预览</div>
        <div class="mb-3 flex items-center gap-2">
          <ElInput
            v-model="searchKeyword"
            class="flex-1"
            clearable
            placeholder="输入查询语句，验证召回效果"
            @keyup.enter="runSearch"
          />
          <ElInputNumber
            v-model="searchTopK"
            :max="20"
            :min="1"
            controls-position="right"
          />
          <ElButton
            :loading="searchLoading"
            type="primary"
            @click="runSearch"
          >
            检索
          </ElButton>
        </div>

        <div class="min-h-0 flex-1 overflow-auto">
          <template v-if="searchHits && searchHits.length > 0">
            <div
              v-for="(hit, idx) in searchHits"
              :key="idx"
              class="mb-2 rounded border border-gray-200 p-3 dark:border-gray-700"
            >
              <div class="mb-1 flex items-center justify-between">
                <span class="text-xs text-gray-400">#{{ idx + 1 }}</span>
                <ElTag size="small" type="success">
                  {{ (hit.score ?? 0).toFixed(4) }}
                </ElTag>
              </div>
              <div class="whitespace-pre-wrap text-sm">{{ hit.content }}</div>
            </div>
          </template>
          <ElEmpty
            v-else-if="searchHits"
            :image-size="80"
            description="无命中结果"
          />
          <ElEmpty
            v-else
            :image-size="80"
            description="输入查询语句检索预览"
          />
        </div>
      </section>
    </div>

    <!-- 导入文档弹窗 -->
    <ElDialog
      v-model="importVisible"
      :close-on-click-modal="false"
      append-to-body
      title="导入文档"
      width="640"
    >
      <ElForm
        ref="importFormRef"
        :model="importForm"
        :rules="importRules"
        label-width="90px"
      >
        <ElFormItem label="文档标识" prop="docId">
          <ElInput
            v-model="importForm.docId"
            placeholder="留空由服务端生成；同标识重复导入将覆盖"
          />
        </ElFormItem>
        <ElFormItem label="标题">
          <ElInput v-model="importForm.title" placeholder="可选" />
        </ElFormItem>
        <ElFormItem label="来源类型">
          <ElInput
            v-model="importForm.sourceType"
            placeholder="text / markdown / url，缺省 text"
          />
        </ElFormItem>
        <ElFormItem label="来源地址">
          <ElInput v-model="importForm.sourceUri" placeholder="可选" />
        </ElFormItem>
        <ElFormItem label="正文" prop="content">
          <ElInput
            v-model="importForm.content"
            :rows="10"
            placeholder="粘贴文档正文，导入时将自动切块并向量化"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="importVisible = false">取消</ElButton>
        <ElButton
          :loading="importLoading"
          type="primary"
          @click="submitImport"
        >
          导入
        </ElButton>
      </template>
    </ElDialog>
  </ElDrawer>
</template>
