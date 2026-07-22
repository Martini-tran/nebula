<script lang="ts" setup>
/**
 * 编辑器工作区容器：画布（centerArea 语义，走 #center 插槽）+ 右侧自研 dock。
 *
 * 为什么自研而非用 vue-code-layout 的 CodeLayout：CodeLayout 是全屏深色 IDE 停靠系统，
 * 其活动栏在本项目的裁剪配置下会把面板图标折叠成汉堡溢出菜单（无法平铺），面板头部也
 * 不渲染关闭按钮（closeType 只对 SplitLayout 生效），与「右侧列两个菜单 + 点击开面板 +
 * 头部可关闭 + 主题贴合 element-plus」的诉求持续冲突。改为一套轻量自研 dock，交互与主题
 * 完全可控。
 *
 * 结构（从右到左）：活动栏（竖排两图标：配置/AI 生成，点击 toggle 面板）｜面板区（可拖宽）。
 * 面板区两个面板：
 * - 都关：面板区收起，只留活动栏 + 画布占满。
 * - 开一个：单面板铺满面板区，头部带标题 + 关闭 X。
 * - 开两个：默认 tab（头部两个 tab + 关闭），可切「上下分屏」（split）同时可见。
 *
 * 布局（宽度 / 开关 / 排列 / 激活 tab）持久化到 localStorage，刷新恢复。
 *
 * ref 转发：index.vue 通过 sideDockRef.open/close/openEdge/scrollToSection 驱动配置面板，
 * 这里 defineExpose 转发内部 NodeConfigPanel 的方法，并在选中节点/连线时自动打开配置面板。
 */
import type { Edge, Node } from '@antv/x6';

import type { CopilotApi } from '#/api';

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import AiChatPanel from './AiChatPanel.vue';
import NodeConfigPanel from './NodeConfigPanel.vue';

defineOptions({ name: 'EditorSideDock' });

/**
 * apply：节点/连线配置应用后上抛，由 index.vue 触发流程保存。
 * flowGenerated：AI 生成流程落库后上抛，由 index.vue 把流程回显到画布。
 */
const emit = defineEmits<{
  apply: [];
  flowGenerated: [payload: CopilotApi.FlowEvent];
}>();

const nodeConfigRef = ref<InstanceType<typeof NodeConfigPanel>>();

type PanelKey = 'config' | 'ai';
type Arrangement = 'split' | 'tab';

interface PanelDef {
  key: PanelKey;
  title: string;
  icon: string;
  tooltip: string;
}

const PANELS: PanelDef[] = [
  { key: 'config', title: '配置', icon: '⚙', tooltip: '节点 / 连线配置' },
  { key: 'ai', title: 'AI 生成', icon: '✨', tooltip: 'AI 对话辅助编排' },
];

const LAYOUT_KEY = 'ai-flow:editor-sidedock';
const MIN_WIDTH = 280;
const MAX_WIDTH = 720;
const DEFAULT_WIDTH = 360;

interface DockState {
  /** 打开的面板（保序：先开的在前，split 时决定上下顺序） */
  open: PanelKey[];
  /** 两面板都开时的排列方式 */
  arrangement: Arrangement;
  /** tab 模式下激活的面板 */
  activeTab: PanelKey;
  /** 面板区宽度（px） */
  width: number;
}

const state = reactive<DockState>({
  open: [],
  arrangement: 'tab',
  activeTab: 'config',
  width: DEFAULT_WIDTH,
});

// ---------------- 派生 ----------------
const isOpen = (key: PanelKey) => state.open.includes(key);
const bothOpen = computed(() => state.open.length === 2);
const panelAreaVisible = computed(() => state.open.length > 0);

/** tab 模式下按 activeTab 显示单面板；split 模式两面板都可见 */
function paneVisible(key: PanelKey): boolean {
  if (!isOpen(key)) return false;
  if (state.arrangement === 'split' && bothOpen.value) return true;
  return state.activeTab === key;
}

// ---------------- 开关逻辑 ----------------
function openPanel(key: PanelKey) {
  if (!isOpen(key)) state.open.push(key);
  state.activeTab = key;
}

function closePanel(key: PanelKey) {
  const idx = state.open.indexOf(key);
  if (idx !== -1) state.open.splice(idx, 1);
  // 关掉激活面板后，激活态落到剩余面板
  if (state.activeTab === key && state.open.length > 0) {
    state.activeTab = state.open[0]!;
  }
}

function togglePanel(key: PanelKey) {
  if (isOpen(key)) {
    // 已开：若是当前激活（或 split）则关闭，否则切到它
    if (state.arrangement === 'split' && bothOpen.value) {
      closePanel(key);
    } else if (state.activeTab === key) {
      closePanel(key);
    } else {
      state.activeTab = key;
    }
  } else {
    openPanel(key);
  }
}

/** 头部：切换 tab / split 排列（仅两面板都开时有意义） */
function toggleArrangement() {
  state.arrangement = state.arrangement === 'tab' ? 'split' : 'tab';
}

function panelTitle(key: PanelKey): string {
  return PANELS.find((p) => p.key === key)?.title ?? '';
}

// ---------------- 拖拽调宽 ----------------
const resizing = ref(false);
let startX = 0;
let startWidth = 0;

function onResizeStart(e: MouseEvent) {
  resizing.value = true;
  startX = e.clientX;
  startWidth = state.width;
  window.addEventListener('mousemove', onResizeMove);
  window.addEventListener('mouseup', onResizeEnd);
  e.preventDefault();
}
function onResizeMove(e: MouseEvent) {
  // 面板在右侧，向左拖（clientX 减小）变宽
  const next = startWidth + (startX - e.clientX);
  state.width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, next));
}
function onResizeEnd() {
  resizing.value = false;
  window.removeEventListener('mousemove', onResizeMove);
  window.removeEventListener('mouseup', onResizeEnd);
}

// ---------------- 持久化 ----------------
function persist() {
  try {
    localStorage.setItem(
      LAYOUT_KEY,
      JSON.stringify({
        open: state.open,
        arrangement: state.arrangement,
        activeTab: state.activeTab,
        width: state.width,
      }),
    );
  } catch {
    // 忽略写失败
  }
}

function restore() {
  try {
    const raw = localStorage.getItem(LAYOUT_KEY);
    if (!raw) return;
    const data = JSON.parse(raw) as Partial<DockState>;
    if (Array.isArray(data.open)) {
      state.open = data.open.filter((k): k is PanelKey =>
        PANELS.some((p) => p.key === k),
      );
    }
    if (data.arrangement === 'tab' || data.arrangement === 'split') {
      state.arrangement = data.arrangement;
    }
    if (data.activeTab === 'config' || data.activeTab === 'ai') {
      state.activeTab = data.activeTab;
    }
    if (typeof data.width === 'number') {
      state.width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, data.width));
    }
    // 激活态兜底：activeTab 必须在 open 内
    if (state.open.length > 0 && !state.open.includes(state.activeTab)) {
      state.activeTab = state.open[0]!;
    }
  } catch {
    // 解析失败用默认态
  }
}

watch(
  () => [state.open.slice(), state.arrangement, state.activeTab, state.width],
  persist,
  { deep: true },
);

onMounted(restore);
onBeforeUnmount(onResizeEnd);

// ---------------- 对外转发 NodeConfigPanel 命令式方法（index.vue 调用点零改动） ----------------
/** 选中节点/连线时确保配置面板已打开并置前 */
function ensureConfigOpen() {
  openPanel('config');
}
function open(node: Node) {
  ensureConfigOpen();
  nodeConfigRef.value?.open(node);
}
function close() {
  nodeConfigRef.value?.close();
}
function openEdge(edge: Edge) {
  ensureConfigOpen();
  nodeConfigRef.value?.openEdge(edge);
}
function scrollToSection(section: string) {
  nodeConfigRef.value?.scrollToSection(section);
}

defineExpose({ close, open, openEdge, scrollToSection });
</script>

<template>
  <div class="editor-dock" :class="{ 'is-resizing': resizing }">
    <!-- 画布：撑满剩余空间（centerArea 语义），X6 容器仍归 index.vue 掌控 -->
    <div class="dock-center">
      <slot name="center"></slot>
    </div>

    <!-- 面板区：可拖宽；都关时不占位 -->
    <div
      v-show="panelAreaVisible"
      class="dock-panels"
      :style="{ width: `${state.width}px` }"
    >
      <!-- 拖宽手柄（面板左边缘） -->
      <div class="dock-resizer" @mousedown="onResizeStart"></div>

      <!-- 头部：tab（两面板都开时显示两个 tab）+ 排列切换 + 关闭 -->
      <div class="dock-header">
        <div class="dock-tabs">
          <template v-if="state.arrangement === 'tab' || !bothOpen">
            <button
              v-for="key in state.open"
              :key="key"
              class="dock-tab"
              :class="{ active: state.activeTab === key }"
              @click="state.activeTab = key"
            >
              {{ panelTitle(key) }}
            </button>
          </template>
          <span v-else class="dock-split-title">上下分屏</span>
        </div>
        <div class="dock-header-actions">
          <button
            v-if="bothOpen"
            class="dock-icon-btn"
            :title="state.arrangement === 'tab' ? '上下分屏' : '标签模式'"
            @click="toggleArrangement"
          >
            {{ state.arrangement === 'tab' ? '⬍' : '⬒' }}
          </button>
          <button
            class="dock-icon-btn"
            title="关闭当前面板"
            @click="closePanel(state.arrangement === 'split' && bothOpen ? state.open[0]! : state.activeTab)"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- 面板主体：两组件都渲染、v-show 控显隐 → 切换/收起不卸载，配置草稿/SSE 流不丢 -->
      <div class="dock-body" :class="`arrange-${bothOpen ? state.arrangement : 'single'}`">
        <div v-show="paneVisible('config')" class="dock-pane">
          <div v-if="state.arrangement === 'split' && bothOpen" class="dock-pane-title">
            配置
            <button class="dock-icon-btn" title="关闭" @click="closePanel('config')">✕</button>
          </div>
          <div class="dock-pane-content">
            <NodeConfigPanel ref="nodeConfigRef" @apply="emit('apply')" />
          </div>
        </div>
        <div v-show="paneVisible('ai')" class="dock-pane">
          <div v-if="state.arrangement === 'split' && bothOpen" class="dock-pane-title">
            AI 生成
            <button class="dock-icon-btn" title="关闭" @click="closePanel('ai')">✕</button>
          </div>
          <div class="dock-pane-content">
            <AiChatPanel @flow-generated="emit('flowGenerated', $event)" />
          </div>
        </div>
      </div>
    </div>

    <!-- 活动栏：竖排图标，点击 toggle 面板 -->
    <div class="dock-activity">
      <button
        v-for="p in PANELS"
        :key="p.key"
        class="dock-activity-item"
        :class="{ active: isOpen(p.key) }"
        :title="p.tooltip"
        @click="togglePanel(p.key)"
      >
        <span class="dock-activity-icon">{{ p.icon }}</span>
        <span class="dock-activity-label">{{ p.title }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 主体 flex 行里唯一的可伸缩 item：占满剩余宽高，内部横排「画布 | 面板 | 活动栏」 */
.editor-dock {
  position: relative;
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.editor-dock.is-resizing {
  cursor: ew-resize;
  user-select: none;
}

/* 画布：吃掉剩余空间 */
.dock-center {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

/* ---------------- 活动栏 ---------------- */
.dock-activity {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: 52px;
  border-left: 1px solid var(--el-border-color, #dcdfe6);
  background: var(--el-bg-color-page, #f5f7fa);
}
.dock-activity-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: 100%;
  padding: 8px 2px;
  border: none;
  border-left: 2px solid transparent;
  background: transparent;
  color: var(--el-text-color-regular, #606266);
  font-size: 11px;
  line-height: 1.2;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.dock-activity-item:hover {
  background: var(--el-fill-color-light, #f2f6fc);
  color: var(--el-text-color-primary, #303133);
}
.dock-activity-item.active {
  border-left-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.dock-activity-icon {
  font-size: 18px;
}
.dock-activity-label {
  transform: scale(0.92);
  white-space: nowrap;
}

/* ---------------- 面板区 ---------------- */
.dock-panels {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  min-width: 0;
  border-left: 1px solid var(--el-border-color, #dcdfe6);
  background: var(--el-bg-color, #fff);
}
.dock-resizer {
  position: absolute;
  top: 0;
  left: -3px;
  z-index: 5;
  width: 6px;
  height: 100%;
  cursor: ew-resize;
  background: transparent;
}
.dock-resizer:hover {
  background: var(--el-color-primary-light-7, #a0cfff);
}

/* 头部 */
.dock-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  height: 38px;
  padding: 0 4px 0 8px;
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  background: var(--el-bg-color-page, #f5f7fa);
}
.dock-tabs {
  display: flex;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}
.dock-tab {
  height: 100%;
  padding: 0 12px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--el-text-color-regular, #606266);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.dock-tab:hover {
  color: var(--el-text-color-primary, #303133);
}
.dock-tab.active {
  color: var(--el-color-primary, #409eff);
  border-bottom-color: var(--el-color-primary, #409eff);
}
.dock-split-title {
  display: flex;
  align-items: center;
  padding: 0 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}
.dock-header-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}
.dock-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.dock-icon-btn:hover {
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-text-color-primary, #303133);
}

/* 主体 */
.dock-body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.dock-body.arrange-split {
  flex-direction: column;
}
.dock-body.arrange-tab,
.dock-body.arrange-single {
  flex-direction: row;
}
.dock-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
/* split 模式两面板间的分隔 */
.dock-body.arrange-split > .dock-pane + .dock-pane {
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
}
.dock-pane-title {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  padding: 0 4px 0 10px;
  background: var(--el-fill-color-lighter, #fafafa);
  color: var(--el-text-color-regular, #606266);
  font-size: 12px;
}
.dock-pane-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.dock-pane-content > * {
  height: 100%;
}
</style>
