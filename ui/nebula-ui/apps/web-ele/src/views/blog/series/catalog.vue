<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { BlogArticleApi, BlogSeriesApi } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
  ElTransfer,
  ElTree,
} from 'element-plus';

import {
  bindBlogSeriesCatalogPostsApi,
  createBlogSeriesCatalogApi,
  deleteBlogSeriesCatalogApi,
  getBlogArticlePageApi,
  getBlogSeriesCatalogPostsApi,
  getBlogSeriesCatalogTreeApi,
  getBlogSeriesDetailApi,
  updateBlogSeriesCatalogApi,
} from '#/api';

defineOptions({ name: 'BlogSeriesCatalog' });

const route = useRoute();
const router = useRouter();

const seriesId = computed<number | string>(() => {
  const id = route.params.id;
  return Array.isArray(id) ? (id[0] ?? '') : (id ?? '');
});

const seriesName = ref<string>('');

const nodeTypeOptions = [
  { label: '目录', value: 0, tagType: 'info' as const },
  { label: '文章集合', value: 1, tagType: 'primary' as const },
  { label: '链接', value: 2, tagType: 'warning' as const },
];

const linkTargetOptions = [
  { label: '新窗口（_blank）', value: '_blank' },
  { label: '当前窗口（_self）', value: '_self' },
];

function getNodeTypeLabel(value?: number) {
  return nodeTypeOptions.find((n) => n.value === value)?.label ?? '-';
}

function getNodeTypeTagType(value?: number) {
  return nodeTypeOptions.find((n) => n.value === value)?.tagType ?? 'info';
}

// -------------------- 目录树 --------------------

const treeData = ref<BlogSeriesApi.CatalogNode[]>([]);
const treeLoading = ref(false);
const treeRef = ref<InstanceType<typeof ElTree>>();
const expandedKeys = ref<Array<number | string>>([]);

async function loadTree() {
  if (!seriesId.value) return;
  treeLoading.value = true;
  try {
    const data = await getBlogSeriesCatalogTreeApi(seriesId.value);
    treeData.value = data;
    // 默认展开一层
    expandedKeys.value = data.map((n) => n.id);
  } finally {
    treeLoading.value = false;
  }
}

async function loadSeriesName() {
  if (route.query.name) {
    seriesName.value = String(route.query.name);
    return;
  }
  if (!seriesId.value) return;
  try {
    const detail = await getBlogSeriesDetailApi(seriesId.value);
    seriesName.value = detail.name;
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  loadSeriesName();
  loadTree();
});

watch(seriesId, () => {
  loadSeriesName();
  loadTree();
});

// 拍平树供"父节点选择器"使用
const flatNodes = computed(() => {
  const result: Array<{ id: number | string; label: string; depth: number }> = [];
  function walk(list: BlogSeriesApi.CatalogNode[], depth = 0) {
    for (const n of list) {
      result.push({
        id: n.id,
        label: `${'--'.repeat(depth)}${depth > 0 ? ' ' : ''}${n.title}`,
        depth,
      });
      if (n.children?.length) walk(n.children, depth + 1);
    }
  }
  walk(treeData.value);
  return result;
});

function isDescendant(targetId: number | string, sourceId: number | string) {
  // 在 treeData 中找 sourceId 节点，并判断 targetId 是否在其子树中
  function walk(list: BlogSeriesApi.CatalogNode[]): boolean {
    for (const n of list) {
      if (String(n.id) === String(sourceId)) {
        return containsId(n.children ?? [], targetId);
      }
      if (n.children?.length) {
        const found = walk(n.children);
        if (found) return true;
      }
    }
    return false;
  }
  function containsId(list: BlogSeriesApi.CatalogNode[], id: number | string): boolean {
    for (const n of list) {
      if (String(n.id) === String(id)) return true;
      if (n.children?.length && containsId(n.children, id)) return true;
    }
    return false;
  }
  return walk(treeData.value);
}

// -------------------- 节点编辑弹窗 --------------------

type EditMode = 'create' | 'edit';

const nodeDialogVisible = ref(false);
const nodeMode = ref<EditMode>('create');
const editingNodeId = ref<null | number | string>(null);
const nodeLoading = ref(false);
const nodeFormRef = ref<FormInstance>();

const nodeForm = reactive<{
  title: string;
  nodeType: number;
  parentId: null | number | string;
  linkUrl: string;
  linkTarget: string;
  sortOrder: number;
}>({
  title: '',
  nodeType: 0,
  parentId: null,
  linkUrl: '',
  linkTarget: '_blank',
  sortOrder: 0,
});

const nodeRules: FormRules = {
  title: [
    { required: true, message: '请输入节点标题', trigger: 'blur' },
    { max: 200, message: '最多 200 个字符', trigger: 'blur' },
  ],
  linkUrl: [
    {
      validator: (_rule, value, callback) => {
        if (nodeForm.nodeType === 2 && !value) {
          callback(new Error('链接节点必须填写 URL'));
          return;
        }
        if (value && value.length > 500) {
          callback(new Error('最多 500 个字符'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

function resetNodeForm() {
  nodeForm.title = '';
  nodeForm.nodeType = 0;
  nodeForm.parentId = null;
  nodeForm.linkUrl = '';
  nodeForm.linkTarget = '_blank';
  nodeForm.sortOrder = 0;
  nodeFormRef.value?.clearValidate();
}

function openCreateNode(parent?: BlogSeriesApi.CatalogNode) {
  nodeMode.value = 'create';
  editingNodeId.value = null;
  resetNodeForm();
  nodeForm.parentId = parent?.id ?? null;
  nodeDialogVisible.value = true;
}

function openEditNode(node: BlogSeriesApi.CatalogNode) {
  nodeMode.value = 'edit';
  editingNodeId.value = node.id;
  resetNodeForm();
  nodeForm.title = node.title ?? '';
  nodeForm.nodeType = node.nodeType ?? 0;
  nodeForm.parentId = node.parentId ?? null;
  nodeForm.linkUrl = node.linkUrl ?? '';
  nodeForm.linkTarget = node.linkTarget ?? '_blank';
  nodeForm.sortOrder = node.sortOrder ?? 0;
  nodeDialogVisible.value = true;
}

async function submitNode() {
  if (!nodeFormRef.value) return;
  const valid = await nodeFormRef.value.validate().catch(() => false);
  if (!valid) return;

  // 编辑模式下校验父节点变更不能成环
  if (
    nodeMode.value === 'edit' &&
    editingNodeId.value != null &&
    nodeForm.parentId != null &&
    String(nodeForm.parentId) !== ''
  ) {
    if (
      String(nodeForm.parentId) === String(editingNodeId.value) ||
      isDescendant(nodeForm.parentId, editingNodeId.value)
    ) {
      ElMessage.error('不能将节点移动到自身或其后代下');
      return;
    }
  }

  nodeLoading.value = true;
  try {
    if (nodeMode.value === 'create') {
      await createBlogSeriesCatalogApi({
        series_id: seriesId.value,
        parent_id: nodeForm.parentId ?? null,
        title: nodeForm.title,
        node_type: nodeForm.nodeType,
        link_url: nodeForm.nodeType === 2 ? nodeForm.linkUrl : undefined,
        link_target: nodeForm.nodeType === 2 ? nodeForm.linkTarget : undefined,
        sort_order: nodeForm.sortOrder,
      });
      ElMessage.success('创建成功');
    } else if (editingNodeId.value != null) {
      await updateBlogSeriesCatalogApi(editingNodeId.value, {
        // 当用户选择"顶层"（null）时传 0 作为提升信号
        parent_id: nodeForm.parentId ?? 0,
        title: nodeForm.title,
        node_type: nodeForm.nodeType,
        link_url: nodeForm.nodeType === 2 ? nodeForm.linkUrl : undefined,
        link_target: nodeForm.nodeType === 2 ? nodeForm.linkTarget : undefined,
        sort_order: nodeForm.sortOrder,
      });
      ElMessage.success('保存成功');
    }
    nodeDialogVisible.value = false;
    await loadTree();
  } finally {
    nodeLoading.value = false;
  }
}

async function deleteNode(node: BlogSeriesApi.CatalogNode) {
  try {
    await ElMessageBox.confirm(
      `确认删除节点「${node.title}」？其下所有子节点与文章关联将被一并清除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogSeriesCatalogApi(node.id);
  ElMessage.success('删除成功');
  await loadTree();
}

// -------------------- 绑定文章弹窗 --------------------

const postDialogVisible = ref(false);
const postLoading = ref(false);
const postCatalog = ref<BlogSeriesApi.CatalogNode | null>(null);
/** 已选文章 ID（el-transfer 右侧） */
const selectedPostIds = ref<Array<number | string>>([]);
/** 主目录文章 ID */
const primaryPostId = ref<null | number | string>(null);
/** 文章候选列表（el-transfer 左侧） */
const postCandidates = ref<BlogArticleApi.ArticleListItem[]>([]);
const postKeyword = ref<string>('');

const transferData = computed(() =>
  postCandidates.value.map((p) => ({
    key: p.id,
    label: `${p.title} (${p.slug})`,
    disabled: false,
  })),
);

async function loadPostCandidates(keyword: string = '') {
  // 只列已发布的文章作为可选项；如需草稿/归档可放宽
  const result = await getBlogArticlePageApi({
    pageNum: 1,
    pageSize: 200,
    keyword: keyword || undefined,
    status: 'published',
  });
  postCandidates.value = result.records ?? [];
}

async function openPostDialog(node: BlogSeriesApi.CatalogNode) {
  postCatalog.value = node;
  postDialogVisible.value = true;
  postLoading.value = true;
  try {
    const [, posts] = await Promise.all([
      loadPostCandidates(),
      getBlogSeriesCatalogPostsApi(node.id),
    ]);
    selectedPostIds.value = posts.map((p) => p.postId);
    primaryPostId.value = posts.find((p) => p.isPrimary)?.postId ?? null;

    // 把已绑定但不在候选列表里的文章补进来（避免穿梭框找不到）
    const existingIds = new Set(postCandidates.value.map((p) => String(p.id)));
    for (const p of posts) {
      if (!existingIds.has(String(p.postId))) {
        postCandidates.value.push({
          id: p.postId,
          title: p.postTitle ?? '(已删除或不可见)',
          slug: p.postSlug ?? '',
          status: p.postStatus ?? '',
          visibility: 'public',
        });
      }
    }
  } finally {
    postLoading.value = false;
  }
}

async function searchPosts() {
  postLoading.value = true;
  try {
    await loadPostCandidates(postKeyword.value);
  } finally {
    postLoading.value = false;
  }
}

async function submitBind() {
  if (!postCatalog.value) return;
  if (
    primaryPostId.value != null &&
    !selectedPostIds.value.includes(primaryPostId.value)
  ) {
    ElMessage.error('主目录文章必须在已选文章中');
    return;
  }
  postLoading.value = true;
  try {
    await bindBlogSeriesCatalogPostsApi(postCatalog.value.id, {
      post_ids: selectedPostIds.value,
      primary_post_id: primaryPostId.value ?? undefined,
    });
    ElMessage.success('已保存');
    postDialogVisible.value = false;
    await loadTree();
  } finally {
    postLoading.value = false;
  }
}

function back() {
  router.push({ name: 'BlogSeries' });
}
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never">
      <template #header>
        <div class="catalog-header">
          <div class="catalog-header__left">
            <ElButton link @click="back">← 返回系列列表</ElButton>
            <span class="catalog-title">系列目录：{{ seriesName || '加载中...' }}</span>
          </div>
          <div class="catalog-header__right">
            <ElButton
              v-access:code="'blog:series:edit'"
              type="primary"
              @click="openCreateNode()"
            >
              新增顶级节点
            </ElButton>
          </div>
        </div>
      </template>

      <div v-loading="treeLoading" class="catalog-tree-wrap">
        <ElEmpty
          v-if="!treeLoading && treeData.length === 0"
          description="暂无目录节点，点击「新增顶级节点」开始构建"
        />
        <ElTree
          v-else
          ref="treeRef"
          :data="treeData"
          :default-expanded-keys="expandedKeys"
          :props="{ label: 'title', children: 'children' }"
          node-key="id"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="tree-node__main">
                <ElTag
                  :type="getNodeTypeTagType(data.nodeType)"
                  class="tree-node__type"
                  size="small"
                >
                  {{ getNodeTypeLabel(data.nodeType) }}
                </ElTag>
                <span class="tree-node__title">{{ data.title }}</span>
                <span v-if="data.nodeType === 2 && data.linkUrl" class="tree-node__link">
                  → {{ data.linkUrl }}
                </span>
              </div>
              <div class="tree-node__actions">
                <ElButton
                  v-access:code="'blog:series:edit'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openCreateNode(data)"
                >
                  新增子节点
                </ElButton>
                <ElButton
                  v-if="data.nodeType === 1"
                  v-access:code="'blog:series:edit'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openPostDialog(data)"
                >
                  绑定文章
                </ElButton>
                <ElButton
                  v-access:code="'blog:series:edit'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openEditNode(data)"
                >
                  编辑
                </ElButton>
                <ElButton
                  v-access:code="'blog:series:edit'"
                  link
                  type="danger"
                  size="small"
                  @click.stop="deleteNode(data)"
                >
                  删除
                </ElButton>
              </div>
            </div>
          </template>
        </ElTree>
      </div>
    </ElCard>

    <!-- 节点创建/编辑 -->
    <ElDialog
      v-model="nodeDialogVisible"
      :close-on-click-modal="false"
      :title="nodeMode === 'create' ? '新增目录节点' : '编辑目录节点'"
      width="520"
    >
      <ElForm
        ref="nodeFormRef"
        :model="nodeForm"
        :rules="nodeRules"
        label-width="90px"
      >
        <ElFormItem label="节点类型">
          <ElSelect v-model="nodeForm.nodeType" style="width: 100%">
            <ElOption
              v-for="item in nodeTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="节点标题" prop="title">
          <ElInput v-model="nodeForm.title" placeholder="请输入节点标题" />
        </ElFormItem>

        <ElFormItem label="父节点">
          <ElSelect
            v-model="nodeForm.parentId"
            placeholder="顶层（不选父节点）"
            clearable
            style="width: 100%"
          >
            <ElOption
              v-for="item in flatNodes"
              :key="item.id"
              :label="item.label"
              :value="item.id"
              :disabled="
                editingNodeId != null &&
                (String(item.id) === String(editingNodeId) ||
                  isDescendant(item.id, editingNodeId))
              "
            />
          </ElSelect>
        </ElFormItem>

        <template v-if="nodeForm.nodeType === 2">
          <ElFormItem label="链接 URL" prop="linkUrl">
            <ElInput
              v-model="nodeForm.linkUrl"
              placeholder="https://example.com/path"
            />
          </ElFormItem>
          <ElFormItem label="打开方式">
            <ElSelect v-model="nodeForm.linkTarget" style="width: 100%">
              <ElOption
                v-for="item in linkTargetOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </template>

        <ElFormItem label="排序">
          <ElInputNumber
            v-model="nodeForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="nodeDialogVisible = false">取消</ElButton>
        <ElButton :loading="nodeLoading" type="primary" @click="submitNode">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 文章绑定 -->
    <ElDialog
      v-model="postDialogVisible"
      :close-on-click-modal="false"
      :title="`绑定文章 — ${postCatalog?.title ?? ''}`"
      width="780"
    >
      <div v-loading="postLoading" class="bind-wrap">
        <div class="bind-toolbar">
          <ElInput
            v-model="postKeyword"
            placeholder="按标题/Slug 搜索文章"
            clearable
            style="width: 280px"
            @keyup.enter="searchPosts"
          />
          <ElButton type="primary" @click="searchPosts">搜索</ElButton>
          <span class="bind-hint">
            穿梭至右侧的文章会按当前顺序保存为 sort_order
          </span>
        </div>

        <ElTransfer
          v-model="selectedPostIds"
          :data="transferData"
          :titles="['可选文章', '已选文章']"
          filterable
          target-order="push"
        />

        <div class="primary-row">
          <span class="primary-label">主目录文章：</span>
          <ElSelect
            v-model="primaryPostId"
            placeholder="可选，标记主推文章"
            clearable
            style="width: 320px"
          >
            <ElOption
              v-for="id in selectedPostIds"
              :key="id"
              :label="
                postCandidates.find((p) => String(p.id) === String(id))
                  ?.title ?? String(id)
              "
              :value="id"
            />
          </ElSelect>
        </div>
      </div>

      <template #footer>
        <ElButton @click="postDialogVisible = false">取消</ElButton>
        <ElButton :loading="postLoading" type="primary" @click="submitBind">
          保存绑定
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.catalog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.catalog-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.catalog-title {
  font-size: 16px;
  font-weight: 600;
}

.catalog-tree-wrap {
  min-height: 320px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 8px;
}

.tree-node__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.tree-node__type {
  flex-shrink: 0;
}

.tree-node__title {
  font-weight: 500;
}

.tree-node__link {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.tree-node__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.bind-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bind-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bind-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.primary-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.primary-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
