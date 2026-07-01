<script lang="ts" setup>
import type { FlowMeta } from './codec';

import type { AiFlowApi } from '#/api';

import { nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ElMessage } from 'element-plus';

import { getFlowNodeTypesApi } from '#/api';

import { flowToGraph, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH } from './codec';
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

const nodeTypes = ref<AiFlowApi.NodeTypeMeta[]>([]);

const containerRef = ref<HTMLDivElement>();
const minimapRef = ref<HTMLDivElement>();
const propertyPanelRef = ref<InstanceType<typeof PropertyPanel>>();
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
  try {
    nodeTypes.value = await getFlowNodeTypesApi();
  } catch {
    nodeTypes.value = [{ type: 'PROMPT', name: '提示词节点' }];
  }
  if (nodeTypes.value.length === 0) {
    nodeTypes.value = [{ type: 'PROMPT', name: '提示词节点' }];
  }

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

function goBack() {
  router.push({ name: 'AiFlowList' });
}

onMounted(async () => {
  await nextTick();
  initGraph();
  await nextTick();
  mountMinimap();
  await loadData();
});
</script>

<template>
  <div class="flex h-full flex-col">
    <FlowToolbar
      v-model:default-profile-code="meta.defaultProfileCode"
      v-model:flow-code="meta.flowCode"
      v-model:name="meta.name"
      :can-redo="canRedo"
      :can-undo="canUndo"
      :is-edit="isEdit"
      :saving="saving"
      @back="goBack"
      @redo="redo"
      @run="openRun"
      @save="handleSave"
      @undo="undo"
    />

    <div class="flex min-h-0 flex-1">
      <NodePalette :node-types="nodeTypes" @add="addNode" />

      <!-- 画布 -->
      <div
        class="relative min-w-0 flex-1"
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

    <PropertyPanel ref="propertyPanelRef" :node-types="nodeTypes" />

    <RunPanel
      ref="runPanelRef"
      v-model:visible="runVisible"
      @execute="handleExecute"
      @resume="handleResume"
      @run-finished="applyRunResult"
    />
  </div>
</template>
