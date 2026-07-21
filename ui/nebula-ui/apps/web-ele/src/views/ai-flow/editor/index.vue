<script lang="ts" setup>
import type { Node } from '@antv/x6';

import type { FlowMeta } from './codec';
import type { StartInputParam } from './start-input';

import type { AiFlowApi } from '#/api';

import { nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ElMessage } from 'element-plus';

import { flowToGraph, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH } from './codec';
import DeriveAgentDialog from './components/DeriveAgentDialog.vue';
import EditorSideDock from './components/EditorSideDock.vue';
import FlowMetaDrawer from './components/FlowMetaDrawer.vue';
import FlowToolbar from './components/FlowToolbar.vue';
import NodeContextMenu from './components/NodeContextMenu.vue';
import NodePalette from './components/NodePalette.vue';
import PropertyPanel from './components/PropertyPanel.vue';
import RunPanel from './components/RunPanel.vue';
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
  engineType: 'DAG',
  maxTransitions: 100,
});

const containerRef = ref<HTMLDivElement>();
const minimapRef = ref<HTMLDivElement>();
const propertyPanelRef = ref<InstanceType<typeof PropertyPanel>>();
const metaDrawerRef = ref<InstanceType<typeof FlowMetaDrawer>>();
const runPanelRef = ref<InstanceType<typeof RunPanel>>();
const loopMenuRef = ref<InstanceType<typeof NodeContextMenu>>();
const deriveAgentRef = ref<InstanceType<typeof DeriveAgentDialog>>();
// 右侧分区容器（SplitLayout 托管的「节点配置 + AI 对话」两面板）；
// 转发了原 NodeConfigPanel 的 open/close/openEdge/scrollToSection，调用点方法名不变。
const sideDockRef = ref<InstanceType<typeof EditorSideDock>>();
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
  // 当前执行内核（响应式 getter）：STATE_MACHINE 放开自环，供连线校验读取
  engineType: () => meta.engineType ?? 'DAG',
  onSelectNode: (node) => {
    // 有专属配置的 8 类节点：选中即在右侧分栏面板展开配置（所见即所得）。
    // 面板内部按 nodeType 渲染对应 embedded 配置组件。
    const nodeType = node.getData<AiFlowApi.FlowNodeRaw>()?.nodeType;
    if (
      nodeType === 'START' ||
      nodeType === 'END' ||
      nodeType === 'LLM' ||
      nodeType === 'TOOL' ||
      nodeType === 'AGENT_REACT' ||
      nodeType === 'AGENT' ||
      nodeType === 'IF' ||
      nodeType === 'JOIN' ||
      nodeType === 'LOOP'
    ) {
      propertyPanelRef.value?.close();
      sideDockRef.value?.open(node);
      return;
    }
    // 其余类型（PROMPT 及占位类型）：面板回空态，走通用属性面板弹窗
    sideDockRef.value?.close();
    propertyPanelRef.value?.openNode(node);
  },
  onSelectEdge: (edge) => {
    // 连线属性收进右侧面板（条件表达式 + 事件名），不再弹居中弹窗
    propertyPanelRef.value?.close();
    sideDockRef.value?.openEdge(edge);
  },
  onClearSelection: () => {
    sideDockRef.value?.close();
    propertyPanelRef.value?.close();
  },
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
 * 右键菜单项 → 右侧面板的滚动锚点。
 * LLM 菜单 key 即 section（basic/advanced），TOOL 同理（tool/io/error）；
 * AGENT_REACT 的菜单 key 带 react- 前缀（与其它类型区分），这里剥掉前缀还原成
 * 弹窗认的 section（basic/prompt）。其余节点类型只有单「配置」项，返回空串。
 */
function mapKeyToSection(nodeType?: string, key?: string): string {
  if (nodeType === 'LLM') return key ?? 'basic';
  if (nodeType === 'TOOL') return key ?? 'tool';
  if (nodeType === 'AGENT_REACT') {
    return (key ?? 'react-basic').replace(/^react-/, '');
  }
  return '';
}

/**
 * 节点右键菜单分发：配置类菜单项统一收编到右侧分栏面板（不再弹独立弹窗）。
 *
 * 选中节点 → selection:changed → onSelectNode → 面板 open(node) 完成 hydrate，
 * 面板只负责随后的小节滚动定位，避免走两条 hydrate 路径。未选中时直接右键也
 * 因 resetSelection 先触发选中而正确工作。
 *
 * 「解散循环」是非配置动作（改图结构），不进面板，单独置顶处理。
 */
function handleStartMenu(node: Node, key: string) {
  const nodeType = node.getData<AiFlowApi.FlowNodeRaw>()?.nodeType;

  if (nodeType === 'LOOP' && key === 'loop-dissolve') {
    const g = graph.value;
    if (g) dissolveLoop(g, node);
    return;
  }

  graph.value?.resetSelection(node);
  const section = mapKeyToSection(nodeType, key);
  if (section) {
    nextTick(() => sideDockRef.value?.scrollToSection(section));
  }
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
  meta.engineType = def.engineType ?? 'DAG';
  meta.maxTransitions = def.maxTransitions ?? 100;

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

/**
 * 派生智能体：以当前流程为编排图创建 ai_agent 定义。
 * 先落库当前画布，确保 Agent 引用的 flowCode/flowVersion 指向最新的图，
 * 再弹派生对话框预填元信息。
 */
async function handleDeriveAgent() {
  if (!meta.flowCode) {
    ElMessage.warning('请先填写流程编码并保存');
    return;
  }
  saving.value = true;
  try {
    await persistence.save();
    isEdit.value = true;
  } finally {
    saving.value = false;
  }
  deriveAgentRef.value?.open({
    flowCode: meta.flowCode,
    name: meta.name,
    description: meta.description,
    version: meta.version,
    defaultProfileCode: meta.defaultProfileCode,
  });
}

function goBack() {
  router.push({ name: 'AiFlowList' });
}

// 全屏布局（full-content）的进出统一由路由守卫处理（router/guard.ts 的
// setupFlowEditorLayoutGuard）：组件可能被 KeepAlive 缓存、生命周期不可靠，
// 组件内不再操作 preferences 布局。

/**
 * 等画布挂载点（containerRef）真正进入 DOM 后再 resolve。
 *
 * 画布现在放在 EditorSideDock → CodeLayout 的 centerArea 插槽里，CodeLayout
 * 会晚于本组件 onMounted 才把中心区内容渲进 DOM。若直接 initGraph()，此刻
 * containerRef.value 还是 undefined，useFlowGraph.init 里 `if (!containerRef.value) return`
 * 会静默跳过 → X6 永不初始化 → 画布空白。这里用 rAF 轮询等它就绪（带帧数上限兜底，
 * 避免异常入口下死等）。
 */
function waitForContainer(maxFrames = 60): Promise<boolean> {
  return new Promise((resolve) => {
    let frames = 0;
    const tick = () => {
      if (containerRef.value) {
        resolve(true);
        return;
      }
      if (frames >= maxFrames) {
        resolve(false);
        return;
      }
      frames += 1;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
}

onMounted(async () => {
  // 编辑器只服务「编辑已存在流程」；新建走独立向导页（AiFlowCreate）先落基础信息。
  // 无 flowCode 属异常入口（如直接输入 URL），提示并退回列表。
  if (!initialFlowCode) {
    ElMessage.warning('请先从列表新建流程');
    router.replace({ name: 'AiFlowList' });
    return;
  }
  // 必须等 CodeLayout 中心区把画布挂载点渲进 DOM，X6 才有容器可挂
  await waitForContainer();
  initGraph();
  await nextTick();
  mountMinimap();
  await loadData();
});
</script>

<template>
  <!--
    absolute inset-0 而非 h-full：布局的 <main> 只有 flex:1、没有 min-height:0，
    内容超高时会被撑大，h-full（height:100%）跟着失去可用高度基准，右侧配置面板
    里的 overflow-y:auto 就永不触发滚动。编辑器是全屏页，直接填满 relative 的 <main>。
  -->
  <div class="absolute inset-0 flex flex-col">
    <FlowToolbar
      :can-redo="canRedo"
      :can-undo="canUndo"
      :flow-code="meta.flowCode"
      :is-edit="isEdit"
      :name="meta.name || ''"
      :saving="saving"
      @back="goBack"
      @derive-agent="handleDeriveAgent"
      @edit-meta="openMeta"
      @redo="redo"
      @run="openRun"
      @save="handleSave"
      @undo="undo"
    />

    <div class="flex min-h-0 flex-1">
      <NodePalette @add="addNode" />

      <!--
        工作区：CodeLayout（VSCode 式）接管「画布 + 右侧配置/AI 面板 + 活动栏」。
        画布放进 CodeLayout 中心区（#center 插槽）——DOM 仍在此掌控，X6 挂载/minimap
        逻辑完全不动；配置/AI 面板停靠右侧栏，可拖成上下/左右/tab、可开关关闭。
        sideDockRef 转发了原 NodeConfigPanel 的命令式方法（open/openEdge/scrollToSection）。
      -->
      <EditorSideDock ref="sideDockRef" @apply="handleSave">
        <template #center>
          <div
            class="relative h-full w-full bg-gray-50 dark:bg-[#141414]"
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
        </template>
      </EditorSideDock>
    </div>

    <!-- PROMPT 及占位类型的节点属性弹窗（8 类核心节点与连线已收进右侧面板） -->
    <PropertyPanel ref="propertyPanelRef" />

    <DeriveAgentDialog ref="deriveAgentRef" />

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
      v-model:engine-type="meta.engineType"
      v-model:flow-code="meta.flowCode"
      v-model:max-transitions="meta.maxTransitions"
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
