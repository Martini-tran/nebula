<script lang="ts" setup>
/**
 * 编辑器工作区容器：用 vue-code-layout 的 CodeLayout 做 VSCode 式布局——
 * 中心区放画布、右侧二级边栏放「配置 / AI 生成」两个可停靠面板、最右侧活动栏
 * 放这两个面板的开关图标。
 *
 * 交互对齐 VSCode：
 * - 右侧活动栏点图标 → 开/关对应面板；面板可拖成上下 / 左右 / 互相 tab 化。
 * - 面板可关闭（closeType:'close'）；关掉后活动栏图标还在，点回来再开。
 * - 画布放 centerArea（常驻区，不随面板 tab 卸载 → X6 挂载/minimap 安全）。
 *   画布 DOM 仍归 index.vue 掌控（X6 逻辑不动），这里只透传一个 #center 插槽承接。
 *
 * 布局（分区尺寸/面板位置/开关态）用 saveLayout/loadLayout 落 localStorage，刷新恢复。
 *
 * ref 转发：index.vue 通过 sideDockRef.open/close/openEdge/scrollToSection 驱动配置
 * 面板，这里用 defineExpose 把内部 NodeConfigPanel 的方法转发出去，并在选中节点/连线时
 * 自动 openPanel('config') 把配置面板顶到前台。
 */
import type { Edge, Node } from '@antv/x6';

import type {
  CodeLayoutConfig,
  CodeLayoutInstance,
  CodeLayoutPanel,
} from 'vue-code-layout';

import { h, nextTick, onMounted, reactive, ref, shallowRef } from 'vue';

import {
  CodeLayout,
  CodeLayoutRootGrid,
  defaultCodeLayoutConfig,
} from 'vue-code-layout';

import AiChatPanel from './AiChatPanel.vue';
import NodeConfigPanel from './NodeConfigPanel.vue';

defineOptions({ name: 'EditorSideDock' });

/** 节点/连线配置应用后上抛，由 index.vue 触发流程保存 */
const emit = defineEmits<{ apply: [] }>();

const layoutRef = ref<CodeLayoutInstance>();
const nodeConfigRef = ref<InstanceType<typeof NodeConfigPanel>>();

/**
 * CodeLayout 渲染时读 layoutData.root，必须传一个 root grid 实例。
 * 用 shallowRef：这是带方法的类实例，深度响应式代理会剥离其私有字段（导致类型/运行时问题）。
 */
const layoutData = shallowRef(new CodeLayoutRootGrid());

/** 布局配置：只保留右侧二级边栏 + 其活动栏，关掉标题栏/主活动栏/左栏/底栏/状态栏 */
const config = reactive<CodeLayoutConfig>({
  ...defaultCodeLayoutConfig,
  titleBar: false,
  menuBar: false,
  statusBar: false,
  activityBar: false,
  primarySideBar: false,
  secondarySideBar: true,
  secondaryActivityBarPosition: 'side',
  bottomPanel: false,
  secondarySideBarWidth: 34, // 右侧栏初始占比（%），画布拿剩余
});

const LAYOUT_KEY = 'ai-flow:editor-codelayout';

/** 面板图标（活动栏用）：简洁字符图标，避免额外引图标库 */
function icon(char: string) {
  return () => h('span', { style: 'font-size:16px;line-height:1' }, char);
}

/**
 * 建默认布局：右侧栏一个组，组内两个面板——config 默认打开、ai 默认收起。
 * closeType:'close' → 面板可关闭；tabStyle 'text' 让两个面板以 tab 形式并存/切换。
 */
function buildDefaultLayout() {
  const inst = layoutRef.value;
  if (!inst) return;
  const group = inst.addGroup(
    { name: 'side-group', title: '工作面板', tabStyle: 'text' },
    'secondarySideBar',
  );
  group.addPanel({
    name: 'config',
    title: '配置',
    tooltip: '节点 / 连线配置',
    iconLarge: icon('⚙'),
    closeType: 'close',
    startOpen: true,
  });
  group.addPanel({
    name: 'ai',
    title: 'AI 生成',
    tooltip: 'AI 对话辅助编排',
    iconLarge: icon('✨'),
    closeType: 'close',
  });
}

/** loadLayout 回填：只按 name 补回展示元信息，内容由 panelRender 现渲染 */
function instantiatePanel(panel: CodeLayoutPanel): CodeLayoutPanel {
  if (panel.name === 'config') {
    panel.title = '配置';
    panel.tooltip = '节点 / 连线配置';
    panel.iconLarge = icon('⚙');
  } else if (panel.name === 'ai') {
    panel.title = 'AI 生成';
    panel.tooltip = 'AI 对话辅助编排';
    panel.iconLarge = icon('✨');
  }
  panel.closeType = 'close';
  return panel;
}

onMounted(async () => {
  await nextTick();
  const inst = layoutRef.value;
  if (!inst) return;
  const raw = localStorage.getItem(LAYOUT_KEY);
  let restored = false;
  if (raw) {
    try {
      inst.loadLayout(JSON.parse(raw), instantiatePanel);
      restored = true;
    } catch {
      restored = false;
    }
  }
  if (!restored) {
    buildDefaultLayout();
  }
});

/** 布局变化（拖拽/开关/关闭）后落库 */
function persistLayout() {
  const inst = layoutRef.value;
  if (!inst) return;
  try {
    localStorage.setItem(LAYOUT_KEY, JSON.stringify(inst.saveLayout()));
  } catch {
    // 写失败不阻断交互
  }
}

// ---------------- 对外转发 NodeConfigPanel 的命令式方法（index.vue 调用点零改动） ----------------
/** 选中节点/连线时确保配置面板已打开并置前，否则用户看不到刚载入的配置 */
function ensureConfigOpen() {
  const panel = layoutRef.value?.getPanelByName('config');
  panel?.openPanel?.(false);
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
  <div class="editor-dock">
    <CodeLayout
      ref="layoutRef"
      :layout-config="config"
      :layout-data="layoutData"
      @base-layout-change="persistLayout"
      @end-drag="persistLayout"
    >
      <!-- 中心区：承接 index.vue 传进来的画布（X6 容器仍归 index.vue 掌控） -->
      <template #centerArea>
        <slot name="center"></slot>
      </template>

      <!-- 面板内容：按 name 分发。两个组件都渲染、v-show 控显隐 → 切面板不卸载、
           配置草稿 / SSE 流不丢 -->
      <template #panelRender="{ panel }">
        <div class="dock-pane">
          <NodeConfigPanel
            v-show="panel.name === 'config'"
            ref="nodeConfigRef"
            @apply="emit('apply')"
          />
          <AiChatPanel v-show="panel.name === 'ai'" />
        </div>
      </template>
    </CodeLayout>
  </div>
</template>

<style scoped>
/**
 * 作为 index.vue 主体 flex 行里唯一的可伸缩 item：占满剩余宽高。
 * CodeLayout 是整页级布局，内部自管中心区 + 右侧栏的分配。
 */
.editor-dock {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.editor-dock :deep(.code-layout-root) {
  width: 100%;
  height: 100%;
}

/* 面板内容撑满 */
.dock-pane {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.dock-pane > * {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
