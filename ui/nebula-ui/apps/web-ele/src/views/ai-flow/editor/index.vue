<script lang="ts" setup>
import type { Edge, Graph as GraphType, Node } from '@antv/x6';

import type { AiFlowApi } from '#/api';
import type { FlowMeta, X6GraphJson } from './codec';

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { Graph } from '@antv/x6';

import {
  getFlowDetailApi,
  getFlowNodeTypesApi,
  runFlowApi,
  saveFlowApi,
} from '#/api';

import {
  EDGE_SHAPE,
  flowToGraph,
  graphToFlow,
  NODE_HEIGHT,
  NODE_SHAPE,
  NODE_WIDTH,
} from './codec';

defineOptions({ name: 'AiFlowEditor' });

const route = useRoute();
const router = useRouter();

const initialFlowCode = (route.query.flowCode as string) || '';
const isEdit = ref(Boolean(initialFlowCode));

/** 流程头部元信息（工具栏） */
const meta = reactive<FlowMeta>({
  flow_code: initialFlowCode,
  name: '',
  description: '',
  version: 1,
  default_profile_code: '',
});

const nodeTypes = ref<AiFlowApi.NodeTypeMeta[]>([]);

const containerRef = ref<HTMLDivElement>();
const graph = shallowRef<GraphType>();

let nodeSeq = 0;

/** 注册一次自定义节点/边形状 */
let shapesRegistered = false;
function registerShapes() {
  if (shapesRegistered) return;
  shapesRegistered = true;

  Graph.registerNode(
    NODE_SHAPE,
    {
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'badge' },
      ],
      attrs: {
        body: {
          rx: 6,
          ry: 6,
          fill: '#ffffff',
          stroke: '#409eff',
          strokeWidth: 1,
        },
        label: {
          refX: 0.5,
          refY: 0.42,
          textAnchor: 'middle',
          textVerticalAnchor: 'middle',
          fontSize: 13,
          fill: '#303133',
        },
        badge: {
          refX: 0.5,
          refY: 0.74,
          textAnchor: 'middle',
          textVerticalAnchor: 'middle',
          fontSize: 11,
          fill: '#909399',
        },
      },
      ports: {
        groups: {
          in: {
            position: 'top',
            attrs: {
              circle: {
                r: 4,
                magnet: true,
                stroke: '#409eff',
                strokeWidth: 1,
                fill: '#fff',
              },
            },
          },
          out: {
            position: 'bottom',
            attrs: {
              circle: {
                r: 4,
                magnet: true,
                stroke: '#409eff',
                strokeWidth: 1,
                fill: '#fff',
              },
            },
          },
        },
        items: [
          { id: 'in', group: 'in' },
          { id: 'out', group: 'out' },
        ],
      },
    },
    true,
  );

  Graph.registerEdge(
    EDGE_SHAPE,
    {
      attrs: {
        line: {
          stroke: '#a0a0a0',
          strokeWidth: 1.5,
          targetMarker: { name: 'block', size: 8 },
        },
      },
    },
    true,
  );
}

/** 用节点 data 刷新节点显示文本 */
function refreshNodeLabel(node: Node) {
  const data = node.getData<AiFlowApi.FlowNodeRaw>() ?? ({} as AiFlowApi.FlowNodeRaw);
  node.setAttrByPath('label/text', data.name || node.id);
  node.setAttrByPath('badge/text', data.node_type || 'PROMPT');
}

// ---------------- 属性抽屉 ----------------
type SelectionKind = 'edge' | 'node' | null;

const drawerVisible = ref(false);
const selectionKind = ref<SelectionKind>(null);
const selectedCellId = ref<string>('');

const nodeForm = reactive<AiFlowApi.FlowNodeRaw>({
  node_code: '',
  name: '',
  node_type: 'PROMPT',
  system_prompt: '',
  prompt_template: '',
  profile_code: '',
  provider: '',
  model: '',
  base_url: '',
  api_key: '',
  temperature: null,
  max_tokens: null,
  top_p: null,
  output_key: '',
  output_mode: 'TEXT',
});

const edgeForm = reactive<{ condition_expr: string }>({ condition_expr: '' });

function fillNodeForm(data: AiFlowApi.FlowNodeRaw) {
  nodeForm.node_code = data.node_code ?? '';
  nodeForm.name = data.name ?? '';
  nodeForm.node_type = data.node_type ?? 'PROMPT';
  nodeForm.system_prompt = data.system_prompt ?? '';
  nodeForm.prompt_template = data.prompt_template ?? '';
  nodeForm.profile_code = data.profile_code ?? '';
  nodeForm.provider = data.provider ?? '';
  nodeForm.model = data.model ?? '';
  nodeForm.base_url = data.base_url ?? '';
  nodeForm.api_key = data.api_key ?? '';
  nodeForm.temperature = data.temperature ?? null;
  nodeForm.max_tokens = data.max_tokens ?? null;
  nodeForm.top_p = data.top_p ?? null;
  nodeForm.output_key = data.output_key ?? '';
  nodeForm.output_mode = data.output_mode ?? 'TEXT';
}

function openNodeDrawer(node: Node) {
  selectionKind.value = 'node';
  selectedCellId.value = node.id;
  const data = node.getData<AiFlowApi.FlowNodeRaw>() ?? ({} as AiFlowApi.FlowNodeRaw);
  fillNodeForm({ ...data, node_code: node.id });
  drawerVisible.value = true;
}

function openEdgeDrawer(edge: Edge) {
  selectionKind.value = 'edge';
  selectedCellId.value = edge.id;
  const data = edge.getData<{ condition_expr?: string }>() ?? {};
  edgeForm.condition_expr = data.condition_expr ?? '';
  drawerVisible.value = true;
}

/** 抽屉「应用」：表单写回到选中 cell 的 data */
function applyDrawer() {
  const g = graph.value;
  if (!g) return;

  if (selectionKind.value === 'node') {
    const node = g.getCellById(selectedCellId.value) as Node | undefined;
    if (!node) return;
    const prev = node.getData<AiFlowApi.FlowNodeRaw>() ?? ({} as AiFlowApi.FlowNodeRaw);
    const next: AiFlowApi.FlowNodeRaw = {
      ...prev,
      node_code: node.id,
      name: nodeForm.name || undefined,
      node_type: nodeForm.node_type || 'PROMPT',
      system_prompt: nodeForm.system_prompt || undefined,
      prompt_template: nodeForm.prompt_template || undefined,
      profile_code: nodeForm.profile_code || undefined,
      provider: nodeForm.provider || undefined,
      model: nodeForm.model || undefined,
      base_url: nodeForm.base_url || undefined,
      api_key: nodeForm.api_key || undefined,
      temperature: nodeForm.temperature ?? undefined,
      max_tokens: nodeForm.max_tokens ?? undefined,
      top_p: nodeForm.top_p ?? undefined,
      output_key: nodeForm.output_key || undefined,
      output_mode: nodeForm.output_mode || 'TEXT',
    };
    node.setData(next, { overwrite: true });
    refreshNodeLabel(node);
  } else if (selectionKind.value === 'edge') {
    const edge = g.getCellById(selectedCellId.value) as Edge | undefined;
    if (!edge) return;
    const expr = edgeForm.condition_expr || '';
    edge.setData({ condition_expr: expr }, { overwrite: true });
    edge.setLabels(expr ? [{ attrs: { label: { text: expr } } }] : []);
  }
  drawerVisible.value = false;
}

// ---------------- 画布操作 ----------------
function genNodeCode() {
  nodeSeq += 1;
  return `node_${nodeSeq}`;
}

function addNode(type: string, position?: { x: number; y: number }) {
  const g = graph.value;
  if (!g) return;
  const code = genNodeCode();
  const data: AiFlowApi.FlowNodeRaw = {
    node_code: code,
    name: '',
    node_type: type || 'PROMPT',
    output_mode: 'TEXT',
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
  refreshNodeLabel(node);
}

function onPaletteDragStart(event: DragEvent, type: string) {
  event.dataTransfer?.setData('text/plain', type);
}

function onCanvasDrop(event: DragEvent) {
  const g = graph.value;
  if (!g) return;
  const type = event.dataTransfer?.getData('text/plain');
  if (!type) return;
  const local = g.clientToLocal(event.clientX, event.clientY);
  addNode(type, { x: local.x - NODE_WIDTH / 2, y: local.y - NODE_HEIGHT / 2 });
}

// ---------------- 初始化画布 ----------------
function initGraph() {
  if (!containerRef.value) return;
  registerShapes();

  const g = new Graph({
    container: containerRef.value,
    autoResize: true,
    background: { color: '#f7f8fa' },
    grid: { size: 10, visible: true },
    panning: { enabled: true, eventTypes: ['rightMouseDown'] },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowMulti: false,
      router: 'manhattan',
      connector: 'rounded',
      snap: true,
      createEdge() {
        return this.createEdge({ shape: EDGE_SHAPE });
      },
      validateConnection({ sourceCell, targetCell }) {
        return Boolean(sourceCell) && Boolean(targetCell) && sourceCell !== targetCell;
      },
    },
    highlighting: {
      magnetAdsorbed: {
        name: 'stroke',
        args: { attrs: { stroke: '#409eff', strokeWidth: 2 } },
      },
    },
  });

  // 选择 / 删除依赖插件较重，这里用原生事件实现最小可用
  g.on('node:click', ({ node }) => {
    openNodeDrawer(node);
  });
  g.on('edge:click', ({ edge }) => {
    openEdgeDrawer(edge);
  });

  graph.value = g;
}

/** 键盘删除选中（X6 selection 插件未引时，靠手动记录最后点击 cell） */
const lastClickedCellId = ref<string>('');
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Delete' && e.key !== 'Backspace') return;
  const target = e.target as HTMLElement;
  // 避免在输入框里误删
  if (
    target &&
    (target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.isContentEditable)
  ) {
    return;
  }
  const g = graph.value;
  if (!g || !lastClickedCellId.value) return;
  const cell = g.getCellById(lastClickedCellId.value);
  if (cell) {
    g.removeCell(cell);
    lastClickedCellId.value = '';
  }
}

async function loadData() {
  // 节点类型面板
  try {
    nodeTypes.value = await getFlowNodeTypesApi();
  } catch {
    nodeTypes.value = [{ type: 'PROMPT', name: '提示词节点' }];
  }

  if (!isEdit.value) return;

  const def = await getFlowDetailApi(initialFlowCode);
  meta.flow_code = def.flow_code;
  meta.name = def.name ?? '';
  meta.description = def.description ?? '';
  meta.version = def.version ?? 1;
  meta.default_profile_code = def.default_profile_code ?? '';

  const json = flowToGraph(def);
  const g = graph.value;
  if (!g) return;
  g.fromJSON(json as any);
  // 回显后刷新文本 + 记录已用 node 序号上界
  g.getNodes().forEach((node) => {
    refreshNodeLabel(node);
    const m = /^node_(\d+)$/.exec(node.id);
    if (m) nodeSeq = Math.max(nodeSeq, Number(m[1]));
  });
}

// ---------------- 保存 / 运行 ----------------
function buildDefinition(): AiFlowApi.FlowDefinitionRaw {
  const g = graph.value;
  const json = (g ? g.toJSON() : { cells: [] }) as any;
  const cells: any[] = json.cells ?? [];
  const nodes = cells.filter((c) => c.shape === NODE_SHAPE);
  const edges = cells.filter((c) => c.shape === EDGE_SHAPE);
  const graphJson: X6GraphJson = {
    nodes: nodes.map((n) => ({
      id: n.id,
      shape: n.shape,
      x: n.position?.x ?? 0,
      y: n.position?.y ?? 0,
      width: n.size?.width ?? NODE_WIDTH,
      height: n.size?.height ?? NODE_HEIGHT,
      data: n.data ?? {},
    })),
    edges: edges.map((e) => ({
      id: e.id,
      shape: e.shape,
      source: { cell: e.source?.cell },
      target: { cell: e.target?.cell },
      data: e.data ?? {},
    })),
  };
  return graphToFlow(graphJson, { ...meta });
}

const saving = ref(false);
async function handleSave() {
  if (!meta.flow_code) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  saving.value = true;
  try {
    const def = buildDefinition();
    await saveFlowApi(def);
    ElMessage.success('保存成功');
    isEdit.value = true;
  } finally {
    saving.value = false;
  }
}

// 运行
const runVisible = ref(false);
const runInputText = ref('{\n  \n}');
const runResult = ref<AiFlowApi.FlowRunResultRaw>();
const running = ref(false);

function openRun() {
  runResult.value = undefined;
  runVisible.value = true;
}

async function handleRun() {
  if (!meta.flow_code) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  let input: Record<string, any> = {};
  try {
    input = runInputText.value.trim() ? JSON.parse(runInputText.value) : {};
  } catch {
    ElMessage.error('input 不是合法 JSON');
    return;
  }
  running.value = true;
  try {
    // 运行前先保存，确保用最新图
    await saveFlowApi(buildDefinition());
    runResult.value = await runFlowApi(meta.flow_code, { input });
    ElMessage.success('运行完成');
  } finally {
    running.value = false;
  }
}

const runResultText = computed(() =>
  runResult.value ? JSON.stringify(runResult.value, null, 2) : '',
);

function goBack() {
  router.push({ name: 'AiFlowList' });
}

onMounted(async () => {
  await nextTick();
  initGraph();
  window.addEventListener('keydown', onKeydown);
  // 记录最后点击 cell 用于键盘删除
  graph.value?.on('cell:click', ({ cell }) => {
    lastClickedCellId.value = cell.id;
  });
  await loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
  graph.value?.dispose();
});
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 顶部工具栏 -->
    <div
      class="flex flex-wrap items-center gap-2 border-b bg-white px-4 py-2 dark:bg-[#1d1e1f]"
    >
      <ElInput
        v-model="meta.flow_code"
        :disabled="isEdit"
        class="!w-44"
        placeholder="流程编码"
        size="small"
      />
      <ElInput
        v-model="meta.name"
        class="!w-44"
        placeholder="流程名称"
        size="small"
      />
      <ElInput
        v-model="meta.default_profile_code"
        class="!w-40"
        placeholder="默认档案码"
        size="small"
      />
      <div class="flex-1"></div>
      <ElButton size="small" @click="goBack">返回</ElButton>
      <ElButton size="small" @click="openRun">运行</ElButton>
      <ElButton
        :loading="saving"
        size="small"
        type="primary"
        @click="handleSave"
      >
        保存
      </ElButton>
    </div>

    <div class="flex min-h-0 flex-1">
      <!-- 左侧节点面板 -->
      <div class="w-40 shrink-0 border-r bg-white p-3 dark:bg-[#1d1e1f]">
        <div class="mb-2 text-xs text-gray-400">拖拽到画布</div>
        <div
          v-for="nt in nodeTypes"
          :key="nt.type"
          class="mb-2 cursor-move rounded border border-dashed border-[#409eff] px-3 py-2 text-center text-sm text-[#409eff] select-none"
          draggable="true"
          @dragstart="onPaletteDragStart($event, nt.type)"
          @dblclick="addNode(nt.type)"
        >
          {{ nt.name }}
        </div>
        <div class="mt-4 text-[11px] leading-5 text-gray-400">
          · 右键拖动平移<br />
          · Ctrl/⌘+滚轮缩放<br />
          · 选中后 Del 删除<br />
          · 拖端点连线
        </div>
      </div>

      <!-- 中间画布 -->
      <div
        class="relative min-w-0 flex-1"
        @drop.prevent="onCanvasDrop"
        @dragover.prevent
      >
        <div ref="containerRef" class="absolute inset-0"></div>
      </div>
    </div>

    <!-- 右侧属性抽屉 -->
    <ElDrawer
      v-model="drawerVisible"
      :size="420"
      :title="selectionKind === 'node' ? '节点属性' : '连线属性'"
      direction="rtl"
    >
      <ElForm v-if="selectionKind === 'node'" label-width="92px">
        <ElFormItem label="节点编码">
          <ElInput v-model="nodeForm.node_code" disabled />
        </ElFormItem>
        <ElFormItem label="名称">
          <ElInput v-model="nodeForm.name" placeholder="节点展示名" />
        </ElFormItem>
        <ElFormItem label="类型">
          <ElSelect v-model="nodeForm.node_type" style="width: 100%">
            <ElOption
              v-for="nt in nodeTypes"
              :key="nt.type"
              :label="nt.name"
              :value="nt.type"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="系统提示">
          <ElInput
            v-model="nodeForm.system_prompt"
            :rows="2"
            placeholder="system prompt"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="提示词模板">
          <ElInput
            v-model="nodeForm.prompt_template"
            :rows="4"
            placeholder="支持 {{变量}} 占位"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="档案码">
          <ElInput
            v-model="nodeForm.profile_code"
            placeholder="引用模型档案（留空用默认）"
          />
        </ElFormItem>
        <ElFormItem label="provider">
          <ElInput v-model="nodeForm.provider" placeholder="openai / ..." />
        </ElFormItem>
        <ElFormItem label="model">
          <ElInput v-model="nodeForm.model" placeholder="gpt-4o-mini / ..." />
        </ElFormItem>
        <ElFormItem label="base_url">
          <ElInput v-model="nodeForm.base_url" placeholder="可选，覆盖档案" />
        </ElFormItem>
        <ElFormItem label="api_key">
          <ElInput
            v-model="nodeForm.api_key"
            placeholder="可选，覆盖档案"
            show-password
            type="password"
          />
        </ElFormItem>
        <ElFormItem label="temperature">
          <ElInputNumber
            v-model="nodeForm.temperature"
            :max="2"
            :min="0"
            :step="0.1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="max_tokens">
          <ElInputNumber
            v-model="nodeForm.max_tokens"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="top_p">
          <ElInputNumber
            v-model="nodeForm.top_p"
            :max="1"
            :min="0"
            :step="0.05"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="输出键">
          <ElInput
            v-model="nodeForm.output_key"
            placeholder="结果写入上下文的键名"
          />
        </ElFormItem>
        <ElFormItem label="输出模式">
          <ElSelect v-model="nodeForm.output_mode" style="width: 100%">
            <ElOption label="TEXT" value="TEXT" />
            <ElOption label="JSON" value="JSON" />
          </ElSelect>
        </ElFormItem>
      </ElForm>

      <ElForm v-else label-width="92px">
        <ElFormItem label="条件表达式">
          <ElInput
            v-model="edgeForm.condition_expr"
            :rows="3"
            placeholder="SpEL，留空表示无条件直连"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="drawerVisible = false">取消</ElButton>
        <ElButton type="primary" @click="applyDrawer">应用</ElButton>
      </template>
    </ElDrawer>

    <!-- 运行抽屉 -->
    <ElDrawer
      v-model="runVisible"
      :size="520"
      direction="rtl"
      title="运行流程"
    >
      <ElForm label-width="60px">
        <ElFormItem label="input">
          <ElInput
            v-model="runInputText"
            :rows="6"
            placeholder="JSON 对象，作为初始上下文输入"
            type="textarea"
          />
        </ElFormItem>
      </ElForm>
      <ElButton
        :loading="running"
        class="mb-3"
        type="primary"
        @click="handleRun"
      >
        执行
      </ElButton>
      <div v-if="runResult" class="text-xs">
        <div class="mb-1 font-medium">运行结果</div>
        <pre
          class="max-h-[60vh] overflow-auto rounded bg-[#f5f5f5] p-3 dark:bg-[#2a2a2a]"
          >{{ runResultText }}</pre
        >
      </div>
    </ElDrawer>
  </div>
</template>
