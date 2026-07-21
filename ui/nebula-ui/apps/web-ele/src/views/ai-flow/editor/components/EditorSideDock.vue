<script lang="ts" setup>
/**
 * 编辑器右侧分区容器：用 vue-code-layout 的 SplitLayout 托管「节点配置」与
 * 「AI 对话」两个面板，取代原先两个各自手写折叠/拖拽/localStorage 的 flex 兄弟节点。
 *
 * 为什么画布不进 SplitLayout：X6 图靠 containerRef 真实 DOM 挂载 + MiniMap 插件
 * （需容器布局完成后再挂），而 SplitLayout 的面板在 tab 切换时非活动内容会被
 * 卸载/隐藏——画布若塞进面板会丢挂载点或尺寸归零。故只把右侧两栏交给 SplitLayout，
 * 画布仍是 index.vue 里独立的 flex 兄弟节点，X6 挂载假设完全不动。
 *
 * 状态常驻：tabContentRender 只渲染当前激活面板，但配置面板的 hydrate 草稿、对话
 * 面板的 SSE 流一旦卸载就丢。故 render 内两个子组件都渲染、用 v-show 控显隐，
 * 两个实例始终常驻，切 tab 不重建。
 *
 * ref 转发：index.vue 通过 nodeConfigPanelRef.open/close/openEdge/scrollToSection
 * 驱动配置面板，这里用 defineExpose 把内部 NodeConfigPanel 的这些方法原样转发出去，
 * index.vue 的调用点零改动（只把 ref 变量指向本组件）。
 */
import type { Edge, Node } from '@antv/x6';

import type {
  CodeLayoutSplitNInstance,
  CodeLayoutSplitNPanel,
} from 'vue-code-layout';

import { onMounted, ref, shallowRef } from 'vue';

import {
  CodeLayoutSplitNRootGrid,
  SplitLayout,
  useLocalStorage,
} from 'vue-code-layout';

import AiChatPanel from './AiChatPanel.vue';
import NodeConfigPanel from './NodeConfigPanel.vue';

defineOptions({ name: 'EditorSideDock' });

/** 节点/连线配置应用后上抛，由 index.vue 触发流程保存 */
const emit = defineEmits<{ apply: [] }>();

const splitRef = ref<CodeLayoutSplitNInstance>();
const nodeConfigRef = ref<InstanceType<typeof NodeConfigPanel>>();

/** SplitLayout 根网格：作为 layoutData 传入，承载两个面板的分区/tab 结构 */
const layoutData = shallowRef(new CodeLayoutSplitNRootGrid());

/** 布局持久化 key（沿用项目 ai-flow: 前缀） */
const LAYOUT_KEY = 'ai-flow:editor-dock-layout';

/**
 * 建默认布局：右侧竖排两分区（上=节点配置，下=AI 对话），两者可同时可见，
 * 用户可拖分割线调高、或把面板拖成同分区双 tab。closeType:'none' → 面板不可关闭。
 */
function buildDefaultLayout() {
  const root = layoutData.value;
  root.direction = 'vertical';
  const configGrid = root.addGrid({ name: 'config-grid', size: 55 });
  configGrid.addPanel({
    name: 'config',
    title: '节点配置',
    tooltip: '当前选中节点/连线的配置',
    closeType: 'none',
  });
  const chatGrid = root.addGrid({ name: 'chat-grid', size: 45 });
  chatGrid.addPanel({
    name: 'chat',
    title: 'AI 对话',
    tooltip: '与 AI 对话辅助编排',
    closeType: 'none',
  });
}

/**
 * loadLayout 回填：本地存档只存结构（分区尺寸/面板位置），面板内容由
 * tabContentRender 现渲染。这里按 name 补回 title/closeType 等元信息即可，
 * 不序列化任何组件实例或函数。
 */
function instantiatePanel(panel: CodeLayoutSplitNPanel): CodeLayoutSplitNPanel {
  if (panel.name === 'config') {
    panel.title = '节点配置';
    panel.tooltip = '当前选中节点/连线的配置';
  } else if (panel.name === 'chat') {
    panel.title = 'AI 对话';
    panel.tooltip = '与 AI 对话辅助编排';
  }
  panel.closeType = 'none';
  return panel;
}

// 布局 localStorage：有存档走 loadLayout 恢复分区，无存档建默认布局。
// onLoad 在组件 ready 时触发，此时 layoutData 已可安全 addGrid/addPanel。
const { saveData } = useLocalStorage(
  LAYOUT_KEY,
  null,
  (data) => {
    if (data) {
      layoutData.value.loadLayout(data, instantiatePanel);
    } else {
      buildDefaultLayout();
    }
  },
  () => (layoutData.value.childGrid.length > 0 ? layoutData.value.saveLayout() : null),
);

onMounted(() => {
  // childGrid 为空说明 onLoad 尚未建过布局（存档为空且回调未跑），兜底建默认。
  if (layoutData.value.childGrid.length === 0) {
    buildDefaultLayout();
  }
});

// 拖拽调整分区后立即落库，刷新页面可恢复
function persistLayout() {
  saveData();
}

// ---------------- 对外转发 NodeConfigPanel 的命令式方法（index.vue 调用点零改动） ----------------
function open(node: Node) {
  nodeConfigRef.value?.open(node);
  // 选中节点时把配置面板激活到前台，避免它被折进 tab 背后看不到
  splitRef.value?.activePanel('config');
}
function close() {
  nodeConfigRef.value?.close();
}
function openEdge(edge: Edge) {
  nodeConfigRef.value?.openEdge(edge);
  splitRef.value?.activePanel('config');
}
function scrollToSection(section: string) {
  nodeConfigRef.value?.scrollToSection(section);
}

defineExpose({ close, open, openEdge, scrollToSection });
</script>

<template>
  <div class="side-dock">
    <SplitLayout
      ref="splitRef"
      :layout-data="layoutData"
      @panel-drop="persistLayout"
    >
      <!--
        两个面板都常驻渲染、v-show 控显隐（见组件头注释）：
        NodeConfigPanel 的 hydrate 草稿、AiChatPanel 的 SSE 流切 tab 不丢。
      -->
      <template #tabContentRender="{ panel }">
        <div class="dock-pane">
          <NodeConfigPanel
            v-show="panel.name === 'config'"
            ref="nodeConfigRef"
            @apply="emit('apply')"
          />
          <AiChatPanel v-show="panel.name === 'chat'" />
        </div>
      </template>
    </SplitLayout>
  </div>
</template>

<style scoped>
/**
 * 与原两栏同款高度约束：作为 index.vue 主体 flex 行的 item，靠 align-self:stretch
 * 拿高度、min-height:0 允许压缩、overflow:hidden 兜底。宽度给一个可拖的默认值——
 * SplitLayout 内部竖排分区的高度自适应，横向宽度由本容器固定。
 */
.side-dock {
  position: relative;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  align-self: stretch;
  width: 560px;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-light);
}

/* SplitLayout 需要一个撑满的定位容器承载它的绝对定位子层 */
.side-dock :deep(.code-layout-split-root) {
  width: 100%;
  height: 100%;
}

/* 每个面板内容区撑满 grid，交给子组件自己排版 */
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
