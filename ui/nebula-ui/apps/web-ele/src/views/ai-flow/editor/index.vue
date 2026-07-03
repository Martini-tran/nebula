<script lang="ts" setup>
import type { Node } from '@antv/x6';

import type { FlowMeta } from './codec';
import type { StartInputParam } from './start-input';

import type { AiFlowApi } from '#/api';

import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { preferences, updatePreferences } from '@nebula/preferences';

import { ElMessage } from 'element-plus';

import { flowToGraph, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH } from './codec';
import AgentConfigDialog from './components/AgentConfigDialog.vue';
import FlowMetaDrawer from './components/FlowMetaDrawer.vue';
import FlowToolbar from './components/FlowToolbar.vue';
import IfConfigDialog from './components/IfConfigDialog.vue';
import JoinConfigDialog from './components/JoinConfigDialog.vue';
import LlmConfigDialog from './components/LlmConfigDialog.vue';
import LoopConfigDialog from './components/LoopConfigDialog.vue';
import NodeContextMenu from './components/NodeContextMenu.vue';
import NodePalette from './components/NodePalette.vue';
import PropertyPanel from './components/PropertyPanel.vue';
import RunPanel from './components/RunPanel.vue';
import StartConfigDialog from './components/StartConfigDialog.vue';
import ToolConfigDialog from './components/ToolConfigDialog.vue';
import { useFlowGraph } from './composables/useFlowGraph';
import { useFlowPersistence } from './composables/useFlowPersistence';
import {
  createLoopFromSelection,
  dissolveLoop,
} from './composables/useLoopGroup';
import { useRunHighlight } from './composables/useRunHighlight';
import { DEFAULT_NODE_TYPE } from './constants';
import { applyIfNodeShape, defaultIfConfig, normalizeIfConfig } from './if-config';
import { refreshNodeCard } from './shapes/registerShapes';
import { normalizeStartInputs } from './start-input';

defineOptions({ name: 'AiFlowEditor' });

const route = useRoute();
const router = useRouter();

const initialFlowCode = (route.query.flowCode as string) || '';
const isEdit = ref(Boolean(initialFlowCode));

/** 流程头部元信息 */
const meta = reactive<FlowMeta>({
  flowCode: initialFlowCode,
  name: '',
  description: '',
  version: 1,
  defaultProfileCode: '',
});

const containerRef = ref<HTMLDivElement>();
const minimapRef = ref<HTMLDivElement>();
const propertyPanelRef = ref<InstanceType<typeof PropertyPanel>>();
const metaDrawerRef = ref<InstanceType<typeof FlowMetaDrawer>>();
const runPanelRef = ref<InstanceType<typeof RunPanel>>();
const startConfigRef = ref<InstanceType<typeof StartConfigDialog>>();
const llmConfigRef = ref<InstanceType<typeof LlmConfigDialog>>();
const toolConfigRef = ref<InstanceType<typeof ToolConfigDialog>>();
const agentConfigRef = ref<InstanceType<typeof AgentConfigDialog>>();
const ifConfigRef = ref<InstanceType<typeof IfConfigDialog>>();
const joinConfigRef = ref<InstanceType<typeof JoinConfigDialog>>();
const loopConfigRef = ref<InstanceType<typeof LoopConfigDialog>>();
const loopMenuRef = ref<InstanceType<typeof NodeContextMenu>>();
const runVisible = ref(false);

/** FOR 循环空白右键菜单项（框选后弹出） */
const LOOP_MENU_ITEMS = [{ key: 'create-loop', label: 'For 循环（圈选为循环）' }];
/** 待圈成循环的选中节点（onBlankContextMenu 暂存，菜单确认时消费） */
let pendingLoopSelection: Node[] = [];

let nodeSeq = 0;
function genNodeCode() {
  nodeSeq += 1;
  return `node_${nodeSeq}`;
}

// ---------------- 画布 ----------------
const {
  graph,
  canUndo,
  canRedo,
  init: initGraph,
  mountMinimap,
  redo,
  undo,
  withoutHistory,
} = useFlowGraph({
  containerRef,
  minimapRef,
  onSelectNode: (node) => {
    // 开始 / LLM / 工具节点不走通用属性面板：它们有专属配置弹窗（右键菜单打开）。
    // 选中时只关闭可能残留的面板，避免与其独立弹窗并存。
    const nodeType = node.getData<AiFlowApi.FlowNodeRaw>()?.nodeType;
    if (
      nodeType === 'START' ||
      nodeType === 'LLM' ||
      nodeType === 'TOOL' ||
      nodeType === 'AGENT' ||
      nodeType === 'IF' ||
      nodeType === 'JOIN' ||
      nodeType === 'LOOP'
    ) {
      propertyPanelRef.value?.close();
      return;
    }
    propertyPanelRef.value?.openNode(node);
  },
  onSelectEdge: (edge) => propertyPanelRef.value?.openEdge(edge),
  onClearSelection: () => propertyPanelRef.value?.close(),
  onStartMenu: handleStartMenu,
  onBlankContextMenu: (pos, selected) => {
    // 框选节点后空白右键：暂存选中项，弹「For 循环」菜单
    pendingLoopSelection = selected;
    loopMenuRef.value?.open(pos.x, pos.y);
  },
});

/** For 循环菜单点击：把暂存的选中节点圈成一个循环容器 */
function onLoopMenuSelect(key: string) {
  const g = graph.value;
  if (!g || key !== 'create-loop') return;
  const loop = createLoopFromSelection(g, pendingLoopSelection, genNodeCode);
  pendingLoopSelection = [];
  if (loop) {
    g.cleanSelection();
    refreshNodeCard(loop);
  }
}

/**
 * 开始 / LLM / 工具节点右键菜单分发：按节点类型选对应弹窗。
 * - START：仅「配置」一项 → 开始节点配置弹窗。
 * - LLM：菜单按模块拆分（basic/model/prompt），key 即目标 section，
 *   直接传给 LlmConfigDialog 打开对应配置块。
 * - TOOL：菜单按模块拆分（tool/io/error），key 即目标 section，
 *   直接传给 ToolConfigDialog 打开对应配置块。
 * - AGENT：单「配置」项（key=agent-config），打开 AgentConfigDialog
 *   （选被调 Agent + JSON 调用参数）。
 */
function handleStartMenu(node: Node, key: string) {
  const nodeType = node.getData<AiFlowApi.FlowNodeRaw>()?.nodeType;
  if (nodeType === 'LLM') {
    llmConfigRef.value?.open(node, key as any);
    return;
  }
  if (nodeType === 'TOOL') {
    toolConfigRef.value?.open(node, key as any);
    return;
  }
  if (nodeType === 'AGENT') {
    agentConfigRef.value?.open(node);
    return;
  }
  if (nodeType === 'IF') {
    ifConfigRef.value?.open(node);
    return;
  }
  if (nodeType === 'JOIN') {
    joinConfigRef.value?.open(node);
    return;
  }
  if (nodeType === 'LOOP') {
    if (key === 'loop-config') loopConfigRef.value?.open(node);
    else if (key === 'loop-dissolve') {
      const g = graph.value;
      if (g) dissolveLoop(g, node);
    }
    return;
  }
  if (key === 'config') startConfigRef.value?.open(node);
}

const persistence = useFlowPersistence({
  getGraph: () => graph.value,
  meta,
});

const { applyRunResult, clearHighlight } = useRunHighlight({
  getGraph: () => graph.value,
  withoutHistory,
});

// ---------------- 节点增删 ----------------
function addNode(type: string, position?: { x: number; y: number }) {
  const g = graph.value;
  if (!g) return;
  const code = genNodeCode();
  const data: AiFlowApi.FlowNodeRaw = {
    nodeCode: code,
    name: '',
    nodeType: type || DEFAULT_NODE_TYPE,
    outputMode: 'TEXT',
  };
  // IF 节点：预置默认分支配置，卡片才有分支行、输出端口才按分支生成
  if (data.nodeType === 'IF') {
    data.nodeConfig = { ...data.nodeConfig, if: defaultIfConfig() };
  }
  const node = g.addNode({
    id: code,
    shape: NODE_SHAPE,
    x: position?.x ?? 160,
    y: position?.y ?? 120,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    data,
  });
  // IF 节点按分支数同步尺寸 + 动态输出端口
  if (data.nodeType === 'IF') {
    applyIfNodeShape(node, normalizeIfConfig(data.nodeConfig?.if).branches);
  }
  refreshNodeCard(node);
}

function onCanvasDrop(event: DragEvent) {
  const g = graph.value;
  if (!g) return;
  const type = event.dataTransfer?.getData('text/plain');
  if (!type) return;
  const local = g.clientToLocal(event.clientX, event.clientY);
  addNode(type, { x: local.x - NODE_WIDTH / 2, y: local.y - NODE_HEIGHT / 2 });
}

// ---------------- 加载 / 保存 / 运行 ----------------
async function loadData() {
  // 节点类型改用前端常量表（AGENT_NODE_TYPES），不再依赖后端 node-types 接口
  if (!isEdit.value) return;

  const def = await persistence.loadDefinition(initialFlowCode);
  meta.flowCode = def.flowCode;
  meta.name = def.name ?? '';
  meta.description = def.description ?? '';
  meta.version = def.version ?? 1;
  meta.defaultProfileCode = def.defaultProfileCode ?? '';

  const json = flowToGraph(def);
  const g = graph.value;
  if (!g) return;
  // 回显不进历史栈，避免首个 undo 清空画布
  withoutHistory(() => {
    g.fromJSON(json as any);
    g.getNodes().forEach((node) => {
      // IF 节点回显：按落库分支数重建尺寸 + 动态输出端口，端口才能承接已存的连边
      const nd = node.getData<AiFlowApi.FlowNodeRaw>();
      if (nd?.nodeType === 'IF') {
        applyIfNodeShape(node, normalizeIfConfig(nd.nodeConfig?.if).branches);
      }
      refreshNodeCard(node);
      const m = /^node_(\d+)$/.exec(node.id);
      if (m) nodeSeq = Math.max(nodeSeq, Number(m[1]));
    });
  });
}

/**
 * 保存前的 JOIN 汇聚校验（提示不阻断，草稿也允许保存）：
 * JOIN 承载并行 fan-in，入边不足 2 条时汇聚没有意义，提醒用户补齐连线。
 */
function warnJoinFanIn() {
  const g = graph.value;
  if (!g) return;
  const lacking = g
    .getNodes()
    .filter(
      (n) => n.getData<AiFlowApi.FlowNodeRaw>()?.nodeType === 'JOIN',
    )
    .filter((n) => (g.getIncomingEdges(n) ?? []).length < 2)
    .map((n) => n.getData<AiFlowApi.FlowNodeRaw>()?.name || n.id);
  if (lacking.length > 0) {
    ElMessage.warning(`汇总节点入边不足 2 条：${lacking.join('、')}`);
  }
}

const saving = ref(false);
async function handleSave() {
  if (!meta.flowCode) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  warnJoinFanIn();
  saving.value = true;
  try {
    await persistence.save();
    ElMessage.success('保存成功');
    isEdit.value = true;
  } finally {
    saving.value = false;
  }
}

/** 运行面板打开时快照的开始节点入参定义（驱动动态表单） */
const startInputs = ref<StartInputParam[]>([]);

function openRun() {
  clearHighlight();
  // 从画布提取开始节点的入参（JSON 对象归一化为定义列表）；
  // 无开始节点/未定义入参时运行面板退回 JSON 模式
  const startNode = graph.value
    ?.getNodes()
    .find((n) => n.getData<AiFlowApi.FlowNodeRaw>()?.nodeType === 'START');
  startInputs.value = normalizeStartInputs(
    startNode?.getData<AiFlowApi.FlowNodeRaw>()?.nodeConfig?.inputs,
  );
  runVisible.value = true;
}

async function handleExecute(input: Record<string, any>) {
  if (!meta.flowCode) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  runPanelRef.value?.setRunning(true);
  try {
    await persistence.save(); // 运行前先保存，确保用最新图
    const res = await persistence.run(input);
    runPanelRef.value?.setResult(res);
    ElMessage.success('运行完成');
  } finally {
    runPanelRef.value?.setRunning(false);
  }
}

async function handleResume(runId: string) {
  runPanelRef.value?.setRunning(true);
  try {
    const res = await persistence.resume(runId);
    runPanelRef.value?.setResult(res);
    ElMessage.success('续跑完成');
  } finally {
    runPanelRef.value?.setRunning(false);
  }
}

function openMeta() {
  metaDrawerRef.value?.open();
}

function goBack() {
  router.push({ name: 'AiFlowList' });
}

// ---------------- 全屏（脱离默认布局） ----------------
// 进入编辑器切 full-content（隐藏侧边/顶栏/tab），离开时恢复原布局。
let prevLayout: typeof preferences.app.layout | undefined;
function enterFullscreen() {
  prevLayout = preferences.app.layout;
  if (prevLayout !== 'full-content') {
    updatePreferences({ app: { layout: 'full-content' } });
  }
}
function exitFullscreen() {
  if (prevLayout && prevLayout !== 'full-content') {
    updatePreferences({ app: { layout: prevLayout } });
  }
}

onMounted(async () => {
  // 编辑器只服务「编辑已存在流程」；新建走独立向导页（AiFlowCreate）先落基础信息。
  // 无 flowCode 属异常入口（如直接输入 URL），提示并退回列表。
  if (!initialFlowCode) {
    ElMessage.warning('请先从列表新建流程');
    router.replace({ name: 'AiFlowList' });
    return;
  }
  enterFullscreen();
  await nextTick();
  initGraph();
  await nextTick();
  mountMinimap();
  await loadData();
});

onBeforeUnmount(() => {
  exitFullscreen();
});
</script>

<template>
  <div class="flex h-full flex-col">
    <FlowToolbar
      :can-redo="canRedo"
      :can-undo="canUndo"
      :flow-code="meta.flowCode"
      :is-edit="isEdit"
      :name="meta.name || ''"
      :saving="saving"
      @back="goBack"
      @edit-meta="openMeta"
      @redo="redo"
      @run="openRun"
      @save="handleSave"
      @undo="undo"
    />

    <div class="flex min-h-0 flex-1">
      <NodePalette @add="addNode" />

      <!-- 画布 -->
      <div
        class="relative min-w-0 flex-1 bg-gray-50 dark:bg-[#141414]"
        @drop.prevent="onCanvasDrop"
        @dragover.prevent
      >
        <div ref="containerRef" class="absolute inset-0"></div>
        <!-- 小地图 -->
        <div
          ref="minimapRef"
          class="absolute right-3 bottom-3 z-10 overflow-hidden rounded border bg-white shadow dark:bg-[#1d1e1f]"
        ></div>
      </div>
    </div>

    <PropertyPanel ref="propertyPanelRef" />

    <StartConfigDialog ref="startConfigRef" />

    <LlmConfigDialog ref="llmConfigRef" />

    <ToolConfigDialog ref="toolConfigRef" />

    <AgentConfigDialog ref="agentConfigRef" />

    <IfConfigDialog ref="ifConfigRef" />

    <JoinConfigDialog ref="joinConfigRef" />

    <LoopConfigDialog ref="loopConfigRef" />

    <!-- FOR 循环空白右键菜单（框选后弹出，把选中节点圈成循环） -->
    <NodeContextMenu
      ref="loopMenuRef"
      :items="LOOP_MENU_ITEMS"
      @select="onLoopMenuSelect"
    />

    <FlowMetaDrawer
      ref="metaDrawerRef"
      v-model:default-profile-code="meta.defaultProfileCode"
      v-model:description="meta.description"
      v-model:flow-code="meta.flowCode"
      v-model:name="meta.name"
      :is-edit="isEdit"
    />

    <RunPanel
      ref="runPanelRef"
      v-model:visible="runVisible"
      :start-inputs="startInputs"
      @execute="handleExecute"
      @resume="handleResume"
      @run-finished="applyRunResult"
    />
  </div>
</template>

<style>
/* x6-vue-shape 节点内容包在内嵌 XHTML <body>（foreignObject > body > div）里，
   全局样式 body { min-height: 100vh }（tailwind theme.css）会命中它，把按
   100% 撑高的卡片（IF 动态高卡片、FOR 容器）垂直拉到视口高，这里重置。 */
.x6-graph foreignObject body {
  min-height: 0;
}
</style>
