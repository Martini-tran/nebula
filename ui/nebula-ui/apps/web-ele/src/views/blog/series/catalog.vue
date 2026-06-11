<script lang="ts" setup>
import type { BlogArticleApi, BlogSeriesApi } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';
import { IconifyIcon } from '@nebula/icons';

import {
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElScrollbar,
  ElSelect,
  ElTag,
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

/** 节点类型：0 目录 / 1 文章集合（含文章的目录） / 2 链接 */
const NODE_TYPE_DIR = 0;
const NODE_TYPE_POSTS = 1;
const NODE_TYPE_LINK = 2;

function isLink(node?: BlogSeriesApi.CatalogNode | null) {
  return node?.nodeType === NODE_TYPE_LINK;
}

function nodeIcon(node: BlogSeriesApi.CatalogNode) {
  if (node.nodeType === NODE_TYPE_LINK) return 'tabler:external-link';
  if (node.nodeType === NODE_TYPE_POSTS) return 'tabler:folder-filled';
  return 'tabler:folder';
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
    // 默认展开顶层
    const top = data.map((n) => n.id);
    expandedKeys.value = [...new Set([...expandedKeys.value, ...top])];
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
  currentNodeId.value = null;
  loadSeriesName();
  loadTree();
});

// -------------------- 树查找辅助 --------------------

function findNode(
  id: number | string,
  list: BlogSeriesApi.CatalogNode[] = treeData.value,
): BlogSeriesApi.CatalogNode | null {
  for (const n of list) {
    if (String(n.id) === String(id)) return n;
    if (n.children?.length) {
      const found = findNode(id, n.children);
      if (found) return found;
    }
  }
  return null;
}

/** 从根到目标节点的祖先链（含自身），用于面包屑 */
function findPath(id: number | string): BlogSeriesApi.CatalogNode[] {
  const result: BlogSeriesApi.CatalogNode[] = [];
  function walk(
    list: BlogSeriesApi.CatalogNode[],
    trail: BlogSeriesApi.CatalogNode[],
  ): boolean {
    for (const n of list) {
      const next = [...trail, n];
      if (String(n.id) === String(id)) {
        result.push(...next);
        return true;
      }
      if (n.children?.length && walk(n.children, next)) return true;
    }
    return false;
  }
  walk(treeData.value, []);
  return result;
}

/** targetId 是否在 sourceId 的子树中 */
function isDescendant(targetId: number | string, sourceId: number | string) {
  const source = findNode(sourceId);
  if (!source) return false;
  return !!findNode(targetId, source.children ?? []);
}

// -------------------- 当前目录（右侧内容） --------------------

const currentNodeId = ref<null | number | string>(null);

const currentNode = computed<BlogSeriesApi.CatalogNode | null>(() =>
  currentNodeId.value == null ? null : findNode(currentNodeId.value),
);

/** 当前目录的直接子节点；根目录时为顶层节点 */
const currentChildren = computed<BlogSeriesApi.CatalogNode[]>(() =>
  currentNode.value ? (currentNode.value.children ?? []) : treeData.value,
);

const childFolders = computed(() =>
  currentChildren.value.filter((n) => n.nodeType !== NODE_TYPE_LINK),
);
const childLinks = computed(() =>
  currentChildren.value.filter((n) => n.nodeType === NODE_TYPE_LINK),
);

const breadcrumb = computed(() =>
  currentNodeId.value == null ? [] : findPath(currentNodeId.value),
);

/** 当前节点可否承载文章 / 子节点（链接节点不行；根可建目录但不能加文章） */
const canHoldPosts = computed(
  () => currentNode.value != null && !isLink(currentNode.value),
);

function selectNode(id: null | number | string) {
  currentNodeId.value = id;
  if (id != null) {
    // 同步展开到该节点
    const path = findPath(id).map((n) => n.id);
    expandedKeys.value = [...new Set([...expandedKeys.value, ...path])];
    treeRef.value?.setCurrentKey(id);
  } else {
    treeRef.value?.setCurrentKey(undefined as any);
  }
}

function handleTreeNodeClick(data: BlogSeriesApi.CatalogNode) {
  selectNode(data.id);
}

// -------------------- 已绑定文章 --------------------

const boundPosts = ref<BlogSeriesApi.CatalogPost[]>([]);
const postsLoading = ref(false);

async function loadBoundPosts() {
  const node = currentNode.value;
  if (!node || isLink(node)) {
    boundPosts.value = [];
    return;
  }
  postsLoading.value = true;
  try {
    boundPosts.value = await getBlogSeriesCatalogPostsApi(node.id);
  } finally {
    postsLoading.value = false;
  }
}

watch(currentNodeId, () => {
  loadBoundPosts();
});

/**
 * 全量写入某节点的文章绑定，并自动维护 node_type：
 * 有文章 -> 1（文章集合，读者端才会渲染）；无文章 -> 0（纯目录）。
 */
async function persistBind(
  node: BlogSeriesApi.CatalogNode,
  postIds: Array<number | string>,
  primaryId?: null | number | string,
) {
  await bindBlogSeriesCatalogPostsApi(node.id, {
    post_ids: postIds,
    primary_post_id: primaryId ?? undefined,
  });
  const hasPosts = postIds.length > 0;
  if (hasPosts && node.nodeType !== NODE_TYPE_POSTS) {
    await updateBlogSeriesCatalogApi(node.id, { node_type: NODE_TYPE_POSTS });
  } else if (!hasPosts && node.nodeType === NODE_TYPE_POSTS) {
    await updateBlogSeriesCatalogApi(node.id, { node_type: NODE_TYPE_DIR });
  }
  await loadTree();
  await loadBoundPosts();
}

async function removePost(post: BlogSeriesApi.CatalogPost) {
  const node = currentNode.value;
  if (!node) return;
  const ids = boundPosts.value
    .filter((p) => String(p.postId) !== String(post.postId))
    .map((p) => p.postId);
  const primary =
    boundPosts.value.find(
      (p) => p.isPrimary && String(p.postId) !== String(post.postId),
    )?.postId ?? null;
  try {
    await persistBind(node, ids, primary);
    ElMessage.success('已移除');
  } catch {
    ElMessage.error('移除失败');
  }
}

async function setPrimary(post: BlogSeriesApi.CatalogPost) {
  const node = currentNode.value;
  if (!node) return;
  const ids = boundPosts.value.map((p) => p.postId);
  try {
    await persistBind(node, ids, post.postId);
    ElMessage.success('已设为主推');
  } catch {
    ElMessage.error('操作失败');
  }
}

// -------------------- 新建目录（就地输入） --------------------

const showNewFolder = ref(false);
const newFolderName = ref('');

function startNewFolder() {
  showNewFolder.value = true;
  newFolderName.value = '';
}

async function confirmNewFolder() {
  const title = newFolderName.value.trim();
  if (!title) {
    showNewFolder.value = false;
    return;
  }
  try {
    await createBlogSeriesCatalogApi({
      series_id: seriesId.value,
      parent_id: currentNodeId.value ?? null,
      title,
      node_type: NODE_TYPE_DIR,
      sort_order: currentChildren.value.length,
    });
    newFolderName.value = '';
    showNewFolder.value = false;
    await loadTree();
    ElMessage.success('已创建目录');
  } catch {
    ElMessage.error('创建失败');
  }
}

// -------------------- 重命名弹窗 --------------------

const renameVisible = ref(false);
const renameValue = ref('');
const renameTargetId = ref<null | number | string>(null);

function openRename(node: BlogSeriesApi.CatalogNode) {
  renameTargetId.value = node.id;
  renameValue.value = node.title ?? '';
  renameVisible.value = true;
}

async function confirmRename() {
  const title = renameValue.value.trim();
  if (!title || renameTargetId.value == null) {
    renameVisible.value = false;
    return;
  }
  try {
    await updateBlogSeriesCatalogApi(renameTargetId.value, { title });
    renameVisible.value = false;
    await loadTree();
    ElMessage.success('已重命名');
  } catch {
    ElMessage.error('重命名失败');
  }
}

// -------------------- 链接弹窗（新建 / 编辑） --------------------

const linkVisible = ref(false);
const linkMode = ref<'create' | 'edit'>('create');
const linkForm = reactive<{
  id: null | number | string;
  title: string;
  url: string;
  target: string;
}>({ id: null, title: '', url: '', target: '_blank' });

const linkTargetOptions = [
  { label: '新窗口（_blank）', value: '_blank' },
  { label: '当前窗口（_self）', value: '_self' },
];

function openCreateLink() {
  linkMode.value = 'create';
  linkForm.id = null;
  linkForm.title = '';
  linkForm.url = '';
  linkForm.target = '_blank';
  linkVisible.value = true;
}

function openEditLink(node: BlogSeriesApi.CatalogNode) {
  linkMode.value = 'edit';
  linkForm.id = node.id;
  linkForm.title = node.title ?? '';
  linkForm.url = node.linkUrl ?? '';
  linkForm.target = node.linkTarget ?? '_blank';
  linkVisible.value = true;
}

async function submitLink() {
  const title = linkForm.title.trim();
  const url = linkForm.url.trim();
  if (!title) {
    ElMessage.warning('请输入链接标题');
    return;
  }
  if (!url) {
    ElMessage.warning('请输入链接 URL');
    return;
  }
  try {
    if (linkMode.value === 'create') {
      await createBlogSeriesCatalogApi({
        series_id: seriesId.value,
        parent_id: currentNodeId.value ?? null,
        title,
        node_type: NODE_TYPE_LINK,
        link_url: url,
        link_target: linkForm.target,
        sort_order: currentChildren.value.length,
      });
    } else if (linkForm.id != null) {
      await updateBlogSeriesCatalogApi(linkForm.id, {
        title,
        link_url: url,
        link_target: linkForm.target,
      });
    }
    linkVisible.value = false;
    await loadTree();
    ElMessage.success('已保存');
  } catch {
    ElMessage.error('保存失败');
  }
}

// -------------------- 删除 --------------------

async function deleteNode(node: BlogSeriesApi.CatalogNode) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${node.title}」？其下所有子节点与文章关联将被一并清除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await deleteBlogSeriesCatalogApi(node.id);
    // 若删除的是当前节点或其祖先，回退到父级
    if (
      currentNodeId.value != null &&
      (String(currentNodeId.value) === String(node.id) ||
        isDescendant(currentNodeId.value, node.id))
    ) {
      currentNodeId.value = node.parentId ?? null;
    }
    await loadTree();
    ElMessage.success('删除成功');
  } catch {
    ElMessage.error('删除失败');
  }
}

// -------------------- 添加文章抽屉 --------------------

const drawerVisible = ref(false);
const drawerLoading = ref(false);
const postKeyword = ref('');
const postCandidates = ref<BlogArticleApi.ArticleListItem[]>([]);
const checkedPostIds = ref<Array<number | string>>([]);
const drawerPrimaryId = ref<null | number | string>(null);

async function loadPostCandidates(keyword = '') {
  const result = await getBlogArticlePageApi({
    pageNum: 1,
    pageSize: 200,
    keyword: keyword || undefined,
    status: 'published',
  });
  postCandidates.value = result.records ?? [];
  // 把已绑定但不在候选里的文章补进来，避免勾选项缺失
  const existing = new Set(postCandidates.value.map((p) => String(p.id)));
  for (const p of boundPosts.value) {
    if (!existing.has(String(p.postId))) {
      postCandidates.value.unshift({
        id: p.postId,
        title: p.postTitle ?? '(已删除或不可见)',
        slug: p.postSlug ?? '',
        status: p.postStatus ?? '',
        visibility: 'public',
      });
    }
  }
}

async function openAddPosts() {
  if (!canHoldPosts.value || !currentNode.value) return;
  drawerVisible.value = true;
  drawerLoading.value = true;
  try {
    await loadBoundPosts();
    checkedPostIds.value = boundPosts.value.map((p) => p.postId);
    drawerPrimaryId.value =
      boundPosts.value.find((p) => p.isPrimary)?.postId ?? null;
    await loadPostCandidates();
  } finally {
    drawerLoading.value = false;
  }
}

async function searchPosts() {
  drawerLoading.value = true;
  try {
    await loadPostCandidates(postKeyword.value);
  } finally {
    drawerLoading.value = false;
  }
}

async function submitAddPosts() {
  const node = currentNode.value;
  if (!node) return;
  const ids = [...checkedPostIds.value];
  let primary = drawerPrimaryId.value;
  if (primary != null && !ids.some((id) => String(id) === String(primary))) {
    primary = null;
  }
  drawerLoading.value = true;
  try {
    await persistBind(node, ids, primary);
    drawerVisible.value = false;
    ElMessage.success('已保存文章关联');
  } catch {
    ElMessage.error('保存失败');
  } finally {
    drawerLoading.value = false;
  }
}

function postLabel(id: number | string) {
  return (
    postCandidates.value.find((p) => String(p.id) === String(id))?.title ??
    String(id)
  );
}

// -------------------- 拖拽移动 / 排序 --------------------

const dragSaving = ref(false);

function allowDrop(draggingNode: any, dropNode: any, type: string) {
  // 不能拖到链接节点内部
  if (type === 'inner' && dropNode.data?.nodeType === NODE_TYPE_LINK) {
    return false;
  }
  // 不能拖到自身或后代内部
  if (type === 'inner') {
    if (String(dropNode.data?.id) === String(draggingNode.data?.id)) {
      return false;
    }
    if (isDescendant(dropNode.data?.id, draggingNode.data?.id)) return false;
  }
  return true;
}

function allowDrag() {
  return true;
}

async function onDrop(
  draggingNode: any,
  dropNode: any,
  dropType: 'after' | 'before' | 'inner',
) {
  const dragId = draggingNode.data?.id;
  const newParentId =
    dropType === 'inner'
      ? dropNode.data?.id
      : (dropNode.data?.parentId ?? null);

  // ElTree 已在内存中移动好节点，取新同级分组按当前顺序持久化
  const siblings =
    newParentId == null
      ? treeData.value
      : (findNode(newParentId)?.children ?? []);

  dragSaving.value = true;
  try {
    for (let i = 0; i < siblings.length; i++) {
      const sib = siblings[i];
      if (!sib) continue;
      const payload: BlogSeriesApi.CatalogUpdateParams = { sort_order: i };
      if (String(sib.id) === String(dragId)) {
        // parent_id=0 表示提升为顶层
        payload.parent_id = newParentId == null ? 0 : newParentId;
      }
      await updateBlogSeriesCatalogApi(sib.id, payload);
    }
    await loadTree();
  } catch {
    ElMessage.error('移动失败，已恢复');
    await loadTree();
  } finally {
    dragSaving.value = false;
  }
}

function back() {
  router.push('/blog/series');
}
</script>

<template>
  <Page auto-content-height>
    <div v-loading="dragSaving" class="catalog-layout">
      <!-- 顶部 -->
      <div class="catalog-topbar">
        <div class="catalog-topbar__left">
          <ElButton link @click="back">← 返回系列列表</ElButton>
          <span class="catalog-title">
            系列目录：{{ seriesName || '加载中...' }}
          </span>
        </div>
      </div>

      <div class="explorer">
        <!-- 左：目录树 -->
        <aside class="explorer__tree">
          <div class="explorer__tree-head">
            <span class="explorer__tree-title">目录结构</span>
            <ElButton
              v-access:code="'blog:series:edit'"
              link
              type="primary"
              size="small"
              @click="selectNode(null)"
            >
              <IconifyIcon icon="tabler:home" class="btn-icon" />
              根目录
            </ElButton>
          </div>
          <ElScrollbar class="explorer__tree-body">
            <div
              v-loading="treeLoading"
              class="tree-inner"
              :class="{ 'tree-inner--root': currentNodeId == null }"
            >
              <ElEmpty
                v-if="!treeLoading && treeData.length === 0"
                description="暂无目录，去右侧新建"
                :image-size="60"
              />
              <ElTree
                v-else
                ref="treeRef"
                :data="treeData"
                :default-expanded-keys="expandedKeys"
                :props="{ label: 'title', children: 'children' }"
                :allow-drop="allowDrop"
                :allow-drag="allowDrag"
                :expand-on-click-node="false"
                draggable
                highlight-current
                node-key="id"
                @node-click="handleTreeNodeClick"
                @node-drop="onDrop"
              >
                <template #default="{ data }">
                  <span class="tnode">
                    <IconifyIcon :icon="nodeIcon(data)" class="tnode__icon" />
                    <span class="tnode__title">{{ data.title }}</span>
                  </span>
                </template>
              </ElTree>
            </div>
          </ElScrollbar>
        </aside>

        <!-- 右：当前目录内容 -->
        <section class="explorer__content">
          <!-- 面包屑 -->
          <div class="content-head">
            <ElBreadcrumb separator="/" class="content-crumb">
              <ElBreadcrumbItem>
                <a class="crumb-link" @click="selectNode(null)">
                  {{ seriesName || '系列' }}
                </a>
              </ElBreadcrumbItem>
              <ElBreadcrumbItem v-for="node in breadcrumb" :key="node.id">
                <a class="crumb-link" @click="selectNode(node.id)">
                  {{ node.title }}
                </a>
              </ElBreadcrumbItem>
            </ElBreadcrumb>
          </div>

          <!-- 工具栏 -->
          <div class="content-toolbar">
            <template v-if="isLink(currentNode)">
              <ElButton
                v-access:code="'blog:series:edit'"
                type="primary"
                size="small"
                @click="openEditLink(currentNode!)"
              >
                <IconifyIcon icon="tabler:edit" class="btn-icon" />
                编辑链接
              </ElButton>
              <ElButton
                v-access:code="'blog:series:edit'"
                type="danger"
                size="small"
                plain
                @click="deleteNode(currentNode!)"
              >
                <IconifyIcon icon="tabler:trash" class="btn-icon" />
                删除
              </ElButton>
            </template>
            <template v-else>
              <ElButton
                v-access:code="'blog:series:edit'"
                type="primary"
                size="small"
                @click="startNewFolder"
              >
                <IconifyIcon icon="tabler:folder-plus" class="btn-icon" />
                新建目录
              </ElButton>
              <ElButton
                v-if="canHoldPosts"
                v-access:code="'blog:series:edit'"
                type="primary"
                size="small"
                plain
                @click="openAddPosts"
              >
                <IconifyIcon icon="tabler:file-plus" class="btn-icon" />
                添加文章
              </ElButton>
              <ElButton
                v-access:code="'blog:series:edit'"
                size="small"
                @click="openCreateLink"
              >
                <IconifyIcon icon="tabler:link" class="btn-icon" />
                新建链接
              </ElButton>
              <template v-if="currentNode">
                <ElButton
                  v-access:code="'blog:series:edit'"
                  size="small"
                  @click="openRename(currentNode)"
                >
                  <IconifyIcon icon="tabler:edit" class="btn-icon" />
                  重命名
                </ElButton>
                <ElButton
                  v-access:code="'blog:series:edit'"
                  type="danger"
                  size="small"
                  plain
                  @click="deleteNode(currentNode)"
                >
                  <IconifyIcon icon="tabler:trash" class="btn-icon" />
                  删除
                </ElButton>
              </template>
            </template>
          </div>

          <ElScrollbar class="content-body">
            <!-- 链接节点详情 -->
            <div v-if="isLink(currentNode)" class="link-detail">
              <IconifyIcon icon="tabler:external-link" class="link-detail__icon" />
              <div class="link-detail__info">
                <div class="link-detail__title">{{ currentNode?.title }}</div>
                <a
                  :href="currentNode?.linkUrl"
                  target="_blank"
                  rel="noopener"
                  class="link-detail__url"
                >
                  {{ currentNode?.linkUrl }}
                </a>
                <div class="link-detail__target">
                  打开方式：{{ currentNode?.linkTarget || '_blank' }}
                </div>
              </div>
            </div>

            <template v-else>
              <!-- 子目录 -->
              <div class="sec-title">
                <IconifyIcon icon="tabler:folders" class="sec-icon" />
                子目录
                <span class="sec-count">{{ childFolders.length }}</span>
              </div>

              <!-- 就地新建目录输入 -->
              <div v-if="showNewFolder" class="new-folder-row">
                <IconifyIcon icon="tabler:folder-plus" class="row-icon" />
                <ElInput
                  v-model="newFolderName"
                  placeholder="输入目录名称，回车创建"
                  size="small"
                  autofocus
                  @keyup.enter="confirmNewFolder"
                  @blur="confirmNewFolder"
                />
              </div>

              <div v-if="childFolders.length" class="grid">
                <div
                  v-for="folder in childFolders"
                  :key="folder.id"
                  class="card"
                  @dblclick="selectNode(folder.id)"
                >
                  <div class="card__main" @click="selectNode(folder.id)">
                    <IconifyIcon :icon="nodeIcon(folder)" class="card__icon" />
                    <span class="card__title">{{ folder.title }}</span>
                  </div>
                  <div class="card__meta">
                    <ElTag
                      v-if="folder.nodeType === NODE_TYPE_POSTS"
                      size="small"
                      type="success"
                    >
                      含文章
                    </ElTag>
                    <ElTag
                      v-if="folder.children?.length"
                      size="small"
                      type="info"
                    >
                      {{ folder.children.length }} 子项
                    </ElTag>
                  </div>
                  <div class="card__actions">
                    <ElButton
                      v-access:code="'blog:series:edit'"
                      link
                      size="small"
                      @click.stop="openRename(folder)"
                    >
                      重命名
                    </ElButton>
                    <ElButton
                      v-access:code="'blog:series:edit'"
                      link
                      type="danger"
                      size="small"
                      @click.stop="deleteNode(folder)"
                    >
                      删除
                    </ElButton>
                  </div>
                </div>
              </div>
              <ElEmpty
                v-else-if="!showNewFolder"
                description="暂无子目录"
                :image-size="50"
              />

              <!-- 文章 -->
              <template v-if="canHoldPosts">
                <div class="sec-title sec-title--mt">
                  <IconifyIcon icon="tabler:files" class="sec-icon" />
                  已关联文章
                  <span class="sec-count">{{ boundPosts.length }}</span>
                </div>
                <div v-loading="postsLoading">
                  <div
                    v-for="post in boundPosts"
                    :key="post.postId"
                    class="file-row"
                  >
                    <IconifyIcon icon="tabler:file-text" class="file-row__icon" />
                    <span class="file-row__title">{{ post.postTitle }}</span>
                    <span class="file-row__slug">{{ post.postSlug }}</span>
                    <ElTag
                      v-if="post.isPrimary"
                      size="small"
                      type="warning"
                      class="file-row__primary"
                    >
                      主推
                    </ElTag>
                    <div class="file-row__actions">
                      <ElButton
                        v-if="!post.isPrimary"
                        v-access:code="'blog:series:edit'"
                        link
                        size="small"
                        @click="setPrimary(post)"
                      >
                        设为主推
                      </ElButton>
                      <ElButton
                        v-access:code="'blog:series:edit'"
                        link
                        type="danger"
                        size="small"
                        @click="removePost(post)"
                      >
                        移除
                      </ElButton>
                    </div>
                  </div>
                  <ElEmpty
                    v-if="!postsLoading && boundPosts.length === 0"
                    description="暂无关联文章，点「添加文章」"
                    :image-size="50"
                  />
                </div>
              </template>

              <!-- 链接子节点 -->
              <template v-if="childLinks.length">
                <div class="sec-title sec-title--mt">
                  <IconifyIcon icon="tabler:link" class="sec-icon" />
                  链接
                  <span class="sec-count">{{ childLinks.length }}</span>
                </div>
                <div
                  v-for="link in childLinks"
                  :key="link.id"
                  class="file-row"
                >
                  <IconifyIcon
                    icon="tabler:external-link"
                    class="file-row__icon"
                  />
                  <span class="file-row__title">{{ link.title }}</span>
                  <a
                    :href="link.linkUrl"
                    target="_blank"
                    rel="noopener"
                    class="file-row__slug file-row__link"
                  >
                    {{ link.linkUrl }}
                  </a>
                  <div class="file-row__actions">
                    <ElButton
                      v-access:code="'blog:series:edit'"
                      link
                      size="small"
                      @click="openEditLink(link)"
                    >
                      编辑
                    </ElButton>
                    <ElButton
                      v-access:code="'blog:series:edit'"
                      link
                      type="danger"
                      size="small"
                      @click="deleteNode(link)"
                    >
                      删除
                    </ElButton>
                  </div>
                </div>
              </template>
            </template>
          </ElScrollbar>
        </section>
      </div>
    </div>

    <!-- 重命名 -->
    <ElDialog
      v-model="renameVisible"
      title="重命名"
      width="420"
      append-to-body
      destroy-on-close
    >
      <ElInput
        v-model="renameValue"
        placeholder="请输入新名称"
        maxlength="200"
        show-word-limit
        @keyup.enter="confirmRename"
      />
      <template #footer>
        <ElButton @click="renameVisible = false">取消</ElButton>
        <ElButton type="primary" @click="confirmRename">确认</ElButton>
      </template>
    </ElDialog>

    <!-- 链接新建 / 编辑 -->
    <ElDialog
      v-model="linkVisible"
      :title="linkMode === 'create' ? '新建链接' : '编辑链接'"
      width="520"
      append-to-body
      destroy-on-close
    >
      <ElForm :model="linkForm" label-width="80px">
        <ElFormItem label="标题">
          <ElInput v-model="linkForm.title" placeholder="链接显示名称" />
        </ElFormItem>
        <ElFormItem label="URL">
          <ElInput v-model="linkForm.url" placeholder="https://example.com/path" />
        </ElFormItem>
        <ElFormItem label="打开方式">
          <ElSelect v-model="linkForm.target" style="width: 100%">
            <ElOption
              v-for="item in linkTargetOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="linkVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitLink">确认</ElButton>
      </template>
    </ElDialog>

    <!-- 添加文章抽屉 -->
    <ElDrawer
      v-model="drawerVisible"
      :title="`添加文章 — ${currentNode?.title ?? ''}`"
      size="460px"
    >
      <div v-loading="drawerLoading" class="drawer-body">
        <div class="drawer-search">
          <ElInput
            v-model="postKeyword"
            placeholder="按标题 / Slug 搜索已发布文章"
            clearable
            @keyup.enter="searchPosts"
          />
          <ElButton type="primary" @click="searchPosts">搜索</ElButton>
        </div>

        <div class="drawer-hint">
          已选 {{ checkedPostIds.length }} 篇（勾选 = 关联，取消 = 移除）
        </div>

        <ElScrollbar class="drawer-list">
          <ElCheckboxGroup v-model="checkedPostIds">
            <ElCheckbox
              v-for="p in postCandidates"
              :key="p.id"
              :value="p.id"
              class="drawer-item"
            >
              <span class="drawer-item__title">{{ p.title }}</span>
              <span class="drawer-item__slug">{{ p.slug }}</span>
            </ElCheckbox>
          </ElCheckboxGroup>
          <ElEmpty
            v-if="postCandidates.length === 0"
            description="无匹配文章"
            :image-size="50"
          />
        </ElScrollbar>

        <div class="drawer-primary">
          <span class="drawer-primary__label">主推文章：</span>
          <ElSelect
            v-model="drawerPrimaryId"
            placeholder="可选"
            clearable
            style="flex: 1"
          >
            <ElOption
              v-for="id in checkedPostIds"
              :key="id"
              :label="postLabel(id)"
              :value="id"
            />
          </ElSelect>
        </div>
      </div>

      <template #footer>
        <ElButton @click="drawerVisible = false">取消</ElButton>
        <ElButton
          :loading="drawerLoading"
          type="primary"
          @click="submitAddPosts"
        >
          保存
        </ElButton>
      </template>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.catalog-layout {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.catalog-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.catalog-topbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.catalog-title {
  font-size: 16px;
  font-weight: 600;
}

.explorer {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

/* 左侧树 */
.explorer__tree {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.explorer__tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.explorer__tree-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.explorer__tree-body {
  flex: 1;
  min-height: 0;
}

.tree-inner {
  padding: 8px;
  min-height: 200px;
}

.tnode {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tnode__icon {
  width: 16px;
  height: 16px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.tnode__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧内容 */
.explorer__content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.content-head {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.crumb-link {
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.crumb-link:hover {
  color: var(--el-color-primary);
}

.content-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.content-body {
  flex: 1;
  min-height: 0;
  padding: 12px 16px;
}

.sec-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.sec-title--mt {
  margin-top: 22px;
}

.sec-icon {
  width: 16px;
  height: 16px;
  color: var(--el-color-primary);
}

.sec-count {
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

.new-folder-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.row-icon {
  width: 18px;
  height: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  transition: all 0.15s;
  background: var(--el-fill-color-blank);
}

.card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.card__main {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  min-width: 0;
}

.card__icon {
  width: 22px;
  height: 22px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.card__title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__meta {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  min-height: 22px;
}

.card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.file-row__icon {
  width: 18px;
  height: 18px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.file-row__title {
  font-weight: 500;
  flex-shrink: 0;
}

.file-row__slug {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.file-row__link {
  cursor: pointer;
}

.file-row__link:hover {
  color: var(--el-color-primary);
}

.file-row__primary {
  flex-shrink: 0;
}

.file-row__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

/* 链接节点详情 */
.link-detail {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.link-detail__icon {
  width: 32px;
  height: 32px;
  color: var(--el-color-warning);
}

.link-detail__title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}

.link-detail__url {
  color: var(--el-color-primary);
  word-break: break-all;
}

.link-detail__target {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.btn-icon {
  width: 14px;
  height: 14px;
  margin-right: 2px;
  vertical-align: -2px;
}

/* 抽屉 */
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.drawer-search {
  display: flex;
  gap: 8px;
}

.drawer-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-list {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
}

.drawer-item {
  display: flex;
  width: 100%;
  margin-right: 0;
  height: auto;
  padding: 4px 0;
}

.drawer-item__title {
  font-weight: 500;
  margin-right: 8px;
}

.drawer-item__slug {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.drawer-primary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-primary__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}
</style>
