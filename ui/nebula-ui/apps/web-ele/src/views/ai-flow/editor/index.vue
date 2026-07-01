<script lang="ts" setup>
import type { FlowMeta } from './codec';

import type { AiFlowApi } from '#/api';

import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { preferences, updatePreferences } from '@nebula/preferences';

import { ElMessage } from 'element-plus';

import { flowToGraph, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH } from './codec';
import FlowMetaDrawer from './components/FlowMetaDrawer.vue';
import FlowToolbar from './components/FlowToolbar.vue';
import NodePalette from './components/NodePalette.vue';
import PropertyPanel from './components/PropertyPanel.vue';
import RunPanel from './components/RunPanel.vue';
import { useFlowGraph } from './composables/useFlowGraph';
import { useFlowPersistence } from './composables/useFlowPersistence';
import { useRunHighlight } from './composables/useRunHighlight';
import { refreshNodeCard } from './shapes/registerShapes';

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
const runVisible = ref(false);

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
  onSelectNode: (node) => propertyPanelRef.value?.openNode(node),
  onSelectEdge: (edge) => propertyPanelRef.value?.openEdge(edge),
  onClearSelection: () => propertyPanelRef.value?.close(),
});

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
    nodeType: type || 'PROMPT',
    outputMode: 'TEXT',
  };
  const node = g.addNode({
    id: code,
    shape: NODE_SHAPE,
    x: position?.x ?? 160,
    y: position?.y ?? 120,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    data,
  });
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
      refreshNodeCard(node);
      const m = /^node_(\d+)$/.exec(node.id);
      if (m) nodeSeq = Math.max(nodeSeq, Number(m[1]));
    });
  });
}

const saving = ref(false);
async function handleSave() {
  if (!meta.flowCode) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  saving.value = true;
  try {
    await persistence.save();
    ElMessage.success('保存成功');
    isEdit.value = true;
  } finally {
    saving.value = false;
  }
}

function openRun() {
  clearHighlight();
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
      @execute="handleExecute"
      @resume="handleResume"
      @run-finished="applyRunResult"
    />
  </div>
</template>
