<script lang="ts" setup>
/**
 * 节点配置分栏面板：编辑器右侧的独立栏位，与左侧节点面板、中间画布并列成三栏。
 *
 * 取代原 NodeConfigDrawer（ElDrawer 浮层会盖住画布右侧）：本组件是普通 flex
 * 兄弟节点，占位而非遮挡——画布 flex-1 自动让宽，X6 autoResize 接住尺寸变化。
 *
 * 三态：
 * - 折叠态：收成窄条 + 展开箭头（不做宽度 0，否则找不到展开入口）
 * - 空态：未选中任何 cell 时的占位提示
 * - 配置态：按当前节点 nodeType 渲染对应 <XxxConfigDialog embedded>，或渲染连线属性
 *
 * 沿用弹窗那套数据流：命令式 open(node) hydrate 草稿，子组件 handleConfirm 里
 * 直接 setData 写回节点 + refreshNodeCard，本组件不 emit 任何事件。
 */
import type { Edge, Node } from '@antv/x6';

import { nextTick, reactive, ref, shallowRef } from 'vue';

import { ElButton, ElForm } from 'element-plus';

import AgentConfigDialog from './AgentConfigDialog.vue';
import AgentReactConfigDialog from './AgentReactConfigDialog.vue';
import EdgePropertyPanel from './EdgePropertyPanel.vue';
import EndConfigDialog from './EndConfigDialog.vue';
import IfConfigDialog from './IfConfigDialog.vue';
import JoinConfigDialog from './JoinConfigDialog.vue';
import LlmConfigDialog from './LlmConfigDialog.vue';
import LoopConfigDialog from './LoopConfigDialog.vue';
import StartConfigDialog from './StartConfigDialog.vue';
import ToolConfigDialog from './ToolConfigDialog.vue';

defineOptions({ name: 'NodeConfigPanel' });

/** 节点/连线配置应用后上抛，由外层（编辑器主页面）触发流程保存接口 */
const emit = defineEmits<{ apply: [] }>();

/** 支持内嵌配置的节点类型（其余类型退回空态，由 PropertyPanel 弹窗兜底） */
const SUPPORTED = new Set([
  'AGENT',
  'AGENT_REACT',
  'END',
  'IF',
  'JOIN',
  'LLM',
  'LOOP',
  'START',
  'TOOL',
]);

/** 面板内容形态：空态 / 节点配置 / 连线属性 */
type PanelMode = 'edge' | 'empty' | 'node';

const mode = ref<PanelMode>('empty');
const currentType = ref<string>('');
const currentNode = shallowRef<Node>();

// 各类型配置组件引用（embedded 模式）
const startRef = ref<InstanceType<typeof StartConfigDialog>>();
const llmRef = ref<InstanceType<typeof LlmConfigDialog>>();
const toolRef = ref<InstanceType<typeof ToolConfigDialog>>();
const agentReactRef = ref<InstanceType<typeof AgentReactConfigDialog>>();
const agentRef = ref<InstanceType<typeof AgentConfigDialog>>();
const ifRef = ref<InstanceType<typeof IfConfigDialog>>();
const joinRef = ref<InstanceType<typeof JoinConfigDialog>>();
const endRef = ref<InstanceType<typeof EndConfigDialog>>();
const loopRef = ref<InstanceType<typeof LoopConfigDialog>>();

/** 按 nodeType 取对应组件的 open 方法并 hydrate 目标节点 */
function hydrate(type: string, node: Node) {
  switch (type) {
    case 'AGENT': {
      agentRef.value?.open(node);
      break;
    }
    case 'AGENT_REACT': {
      agentReactRef.value?.open(node);
      break;
    }
    case 'END': {
      endRef.value?.open(node);
      break;
    }
    case 'IF': {
      ifRef.value?.open(node);
      break;
    }
    case 'JOIN': {
      joinRef.value?.open(node);
      break;
    }
    case 'LLM': {
      llmRef.value?.open(node);
      break;
    }
    case 'LOOP': {
      loopRef.value?.open(node);
      break;
    }
    case 'START': {
      startRef.value?.open(node);
      break;
    }
    case 'TOOL': {
      toolRef.value?.open(node);
      break;
    }
    // No default
  }
}

/**
 * 当前类型的配置组件实例中可用的 focusSection（仅 LLM/TOOL/AGENT_REACT 实现）。
 * 各自的 section 字面量类型不同，这里统一收敛为 string 入参。
 */
function currentFocusSection(): ((section: string) => void) | undefined {
  switch (currentType.value) {
    case 'AGENT_REACT': {
      return agentReactRef.value?.focusSection as
        | ((s: string) => void)
        | undefined;
    }
    case 'LLM': {
      return llmRef.value?.focusSection as ((s: string) => void) | undefined;
    }
    case 'TOOL': {
      return toolRef.value?.focusSection as ((s: string) => void) | undefined;
    }
    default: {
      return undefined;
    }
  }
}

/** 选中节点：支持的类型载入配置，否则退回空态（面板常驻，不隐藏） */
function open(node: Node) {
  const type = (node.getData<{ nodeType?: string }>()?.nodeType ?? '').toString();
  if (!SUPPORTED.has(type)) {
    close();
    return;
  }
  mode.value = 'node';
  currentType.value = type;
  currentNode.value = node;
  // 等对应组件 v-if 渲染出来再 hydrate
  nextTick(() => hydrate(type, node));
}

/** 回到空态（清空选择 / 选中不支持的类型） */
function close() {
  mode.value = 'empty';
  currentType.value = '';
  currentNode.value = undefined;
  currentEdge = undefined;
}

/**
 * 右键菜单定位：切换/滚动到当前节点配置的指定分区（LLM 的 basic/advanced 切 Tab、
 * TOOL 的 tool/io/error 滚动）。面板折叠时先展开，等 DOM 就绪再定位。
 */
async function scrollToSection(section: string) {
  if (!section) return;
  if (collapsed.value) setCollapsed(false);
  // 等两拍：切换节点类型时子组件 v-if 重新挂载，data-section 锚点才存在
  await nextTick();
  await nextTick();
  currentFocusSection()?.(section);
}

// ---------------- 连线属性（原 PropertyPanel 的 edge 分支） ----------------
let currentEdge: Edge | undefined;
const edgeForm = reactive<{ conditionExpr: string; eventName: string }>({
  conditionExpr: '',
  eventName: '',
});

/** 选中连线：载入条件表达式 / 事件名 */
function openEdge(edge: Edge) {
  currentEdge = edge;
  currentNode.value = undefined;
  currentType.value = '';
  const data = edge.getData<{ conditionExpr?: string; eventName?: string }>() ?? {};
  edgeForm.conditionExpr = data.conditionExpr ?? '';
  edgeForm.eventName = data.eventName ?? '';
  mode.value = 'edge';
}

/**
 * 应用连线属性：只写 conditionExpr + eventName，
 * 必须保留源端口已承载的 branchId（IF 出边的分支标识，丢了分支连线会错挂端口）。
 */
function applyEdge() {
  if (!currentEdge) return;
  const expr = edgeForm.conditionExpr || '';
  const prev = currentEdge.getData<{ branchId?: string }>() ?? {};
  currentEdge.setData(
    {
      branchId: prev.branchId,
      conditionExpr: expr,
      eventName: edgeForm.eventName || undefined,
    },
    { overwrite: true },
  );
  currentEdge.setLabels(expr ? [{ attrs: { label: { text: expr } } }] : []);
  emit('apply');
}

// ---------------- 面板宽度：可拖拽调整 + 记住偏好 ----------------
const MIN_WIDTH = 360;
const MAX_WIDTH = 1000;
const DEFAULT_WIDTH = 560;
const WIDTH_KEY = 'ai-flow:node-drawer-width';
const COLLAPSED_KEY = 'ai-flow:node-panel-collapsed';

/** 读取上次拖拽保存的宽度（越界或非法回默认） */
function loadWidth(): number {
  const raw = Number(localStorage.getItem(WIDTH_KEY));
  if (!Number.isFinite(raw) || raw < MIN_WIDTH || raw > MAX_WIDTH) {
    return DEFAULT_WIDTH;
  }
  return raw;
}

const panelWidth = ref(loadWidth());
const resizing = ref(false);
const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === '1');

function setCollapsed(next: boolean) {
  collapsed.value = next;
  localStorage.setItem(COLLAPSED_KEY, next ? '1' : '0');
}

function toggleCollapse() {
  setCollapsed(!collapsed.value);
}

/** 左边缘手柄按下：进入拖拽，监听全局 move/up */
function startResize(e: MouseEvent) {
  e.preventDefault();
  resizing.value = true;
  const startX = e.clientX;
  const startWidth = panelWidth.value;

  const onMove = (ev: MouseEvent) => {
    // 面板靠右：向左拖（clientX 变小）加宽
    const next = startWidth + (startX - ev.clientX);
    panelWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, next));
  };
  const onUp = () => {
    resizing.value = false;
    localStorage.setItem(WIDTH_KEY, String(Math.round(panelWidth.value)));
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.removeProperty('user-select');
  };
  // 拖拽期间禁选中文本，避免选到画布/表单文字
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

defineExpose({ close, open, openEdge, scrollToSection });
</script>

<template>
  <!-- 折叠态：窄条 + 展开入口 -->
  <div v-if="collapsed" class="config-panel collapsed">
    <button class="collapsed-bar" title="展开配置面板" @click="toggleCollapse">
      <span class="chevron">‹</span>
      <span class="collapsed-text">配置</span>
    </button>
  </div>

  <!-- 展开态：拖拽手柄 + 头部 + 内容区 -->
  <div
    v-else
    class="config-panel"
    :class="{ 'is-resizing': resizing }"
    :style="{ width: `${panelWidth}px` }"
  >
    <div
      class="resize-handle"
      title="拖动调整宽度"
      @mousedown="startResize"
    ></div>

    <div class="panel-header">
      <button class="collapse-btn" title="收起配置面板" @click="toggleCollapse">
        ›
      </button>
    </div>

    <div class="panel-body">
      <!-- 空态 -->
      <div v-if="mode === 'empty'" class="panel-empty">
        选择画布上的节点或连线以查看配置
      </div>

      <!-- 连线属性 -->
      <ElForm v-else-if="mode === 'edge'" label-width="84px" @submit.prevent>
        <EdgePropertyPanel v-model="edgeForm" />
        <div class="edge-footer">
          <ElButton type="primary" @click="applyEdge">应用</ElButton>
        </div>
      </ElForm>

      <!-- 节点配置：按 nodeType 渲染对应 embedded 配置组件 -->
      <template v-else>
        <StartConfigDialog
          v-if="currentType === 'START'"
          ref="startRef"
          embedded
          @apply="emit('apply')"
        />
        <LlmConfigDialog
          v-else-if="currentType === 'LLM'"
          ref="llmRef"
          embedded
          @apply="emit('apply')"
        />
        <ToolConfigDialog
          v-else-if="currentType === 'TOOL'"
          ref="toolRef"
          embedded
          @apply="emit('apply')"
        />
        <AgentReactConfigDialog
          v-else-if="currentType === 'AGENT_REACT'"
          ref="agentReactRef"
          embedded
          @apply="emit('apply')"
        />
        <AgentConfigDialog
          v-else-if="currentType === 'AGENT'"
          ref="agentRef"
          embedded
          @apply="emit('apply')"
        />
        <IfConfigDialog
          v-else-if="currentType === 'IF'"
          ref="ifRef"
          embedded
          @apply="emit('apply')"
        />
        <JoinConfigDialog
          v-else-if="currentType === 'JOIN'"
          ref="joinRef"
          embedded
          @apply="emit('apply')"
        />
        <EndConfigDialog
          v-else-if="currentType === 'END'"
          ref="endRef"
          embedded
          @apply="emit('apply')"
        />
        <LoopConfigDialog
          v-else-if="currentType === 'LOOP'"
          ref="loopRef"
          embedded
          @apply="emit('apply')"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
/**
 * 不写 height:100%：那会依赖祖先链每一层都有确定高度，一旦某层是 auto 就退化成
 * 内容高度，把面板撑开、内部滚动失效。作为 flex 行的 item，靠 align-self:stretch
 * （flex 默认）拿高度，再用 min-height:0 允许被压缩到小于内容高度。
 * overflow:hidden 兜底：即便祖先真的没有确定高度，也不让内容溢出面板边界。
 */
.config-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  align-self: stretch;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-light);
}

/* 拖拽期间关掉宽度过渡，避免与 X6 autoResize 的 ResizeObserver 叠加抖动 */
.config-panel.is-resizing {
  transition: none !important;
}

/* ---- 折叠态：窄条 ---- */
.config-panel.collapsed {
  width: 40px;
}

.collapsed-bar {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 0;
  padding-top: 14px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: none;
}

.collapsed-bar:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

.collapsed-text {
  writing-mode: vertical-rl;
  letter-spacing: 2px;
}

.chevron {
  font-size: 14px;
  line-height: 1;
}

/* ---- 展开态：拖拽手柄 ---- */
.resize-handle {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
  width: 6px;
  height: 100%;
  cursor: ew-resize;
  background: transparent;
}

.resize-handle::after {
  position: absolute;
  top: 50%;
  left: 1px;
  width: 3px;
  height: 40px;
  content: '';
  background: var(--el-border-color);
  border-radius: 3px;
  transform: translateY(-50%);
  transition: background-color 0.15s;
}

.resize-handle:hover::after,
.is-resizing .resize-handle::after {
  background: var(--el-color-primary);
}

/* ---- 展开态：头部 ---- */
.panel-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 12px 0 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
}

.collapse-btn:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

/* ---- 展开态：内容区 ---- */
.panel-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px 12px 20px;
  overflow: hidden;
}

.panel-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.edge-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
