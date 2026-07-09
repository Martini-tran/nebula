<script lang="ts" setup>
/**
 * 节点配置常驻抽屉：选中节点即在右侧展开其配置，画布与配置同屏（所见即所得），
 * 取代「右键 → 弹窗 → 切小节」的割裂交互。
 *
 * 复用现有各 XxxConfigDialog 的 embedded 模式（去 ElDialog 外壳、只渲表单体 + 应用按钮），
 * 草稿/写回逻辑完全沿用弹窗那套。按当前节点 nodeType 渲染对应配置组件，选中变化时
 * 调其 open(node) 重新 hydrate。右键弹窗路径保留不动，两者并存、可回退。
 *
 * :modal=false 不遮画布；选中节点 → open、切节点 → 换内容、清空/多选 → 关闭。
 */
import type { Node } from '@antv/x6';

import { nextTick, ref, shallowRef } from 'vue';

import { ElDrawer } from 'element-plus';

import AgentConfigDialog from './AgentConfigDialog.vue';
import EndConfigDialog from './EndConfigDialog.vue';
import IfConfigDialog from './IfConfigDialog.vue';
import JoinConfigDialog from './JoinConfigDialog.vue';
import LlmConfigDialog from './LlmConfigDialog.vue';
import LoopConfigDialog from './LoopConfigDialog.vue';
import StartConfigDialog from './StartConfigDialog.vue';
import ToolConfigDialog from './ToolConfigDialog.vue';

defineOptions({ name: 'NodeConfigDrawer' });

/** 支持内嵌配置的节点类型（其余类型不弹抽屉） */
const SUPPORTED = new Set([
  'START',
  'LLM',
  'TOOL',
  'AGENT',
  'IF',
  'JOIN',
  'END',
  'LOOP',
]);

const TITLES: Record<string, string> = {
  START: '开始节点',
  LLM: 'LLM 节点',
  TOOL: '工具节点',
  AGENT: 'Agent 调用',
  IF: '条件判断',
  JOIN: '汇总节点',
  END: '结束节点',
  LOOP: '循环节点',
};

const visible = ref(false);
const currentType = ref<string>('');
const currentNode = shallowRef<Node>();

// 各类型配置组件引用（embedded 模式）
const startRef = ref<InstanceType<typeof StartConfigDialog>>();
const llmRef = ref<InstanceType<typeof LlmConfigDialog>>();
const toolRef = ref<InstanceType<typeof ToolConfigDialog>>();
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

/** 选中节点：支持的类型展开抽屉并载入配置，否则关闭 */
function open(node: Node) {
  const type = (node.getData<{ nodeType?: string }>()?.nodeType ?? '').toString();
  if (!SUPPORTED.has(type)) {
    close();
    return;
  }
  currentType.value = type;
  currentNode.value = node;
  visible.value = true;
  // 等对应组件 v-if 渲染出来再 hydrate
  nextTick(() => hydrate(type, node));
}

function close() {
  visible.value = false;
  currentNode.value = undefined;
}

// ---------------- 抽屉宽度：可拖拽调整 + 记住偏好 ----------------
const MIN_WIDTH = 360;
const MAX_WIDTH = 1000;
const DEFAULT_WIDTH = 560;
const STORAGE_KEY = 'ai-flow:node-drawer-width';

/** 读取上次拖拽保存的宽度（越界或非法回默认） */
function loadWidth(): number {
  const raw = Number(localStorage.getItem(STORAGE_KEY));
  if (!Number.isFinite(raw) || raw < MIN_WIDTH || raw > MAX_WIDTH) {
    return DEFAULT_WIDTH;
  }
  return raw;
}

const drawerWidth = ref(loadWidth());
const resizing = ref(false);

/** 左边缘手柄按下：进入拖拽，监听全局 move/up */
function startResize(e: MouseEvent) {
  e.preventDefault();
  resizing.value = true;
  const startX = e.clientX;
  const startWidth = drawerWidth.value;

  const onMove = (ev: MouseEvent) => {
    // rtl 抽屉靠右：向左拖（clientX 变小）加宽
    const next = startWidth + (startX - ev.clientX);
    drawerWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, next));
  };
  const onUp = () => {
    resizing.value = false;
    localStorage.setItem(STORAGE_KEY, String(Math.round(drawerWidth.value)));
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.removeProperty('user-select');
  };
  // 拖拽期间禁选中文本，避免选到画布/表单文字
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

defineExpose({ open, close });
</script>

<template>
  <ElDrawer
    v-model="visible"
    :append-to-body="false"
    class="node-config-drawer"
    :class="{ 'is-resizing': resizing }"
    :close-on-click-modal="false"
    direction="rtl"
    :modal="false"
    :size="drawerWidth"
    :title="`${TITLES[currentType] ?? '节点'}配置`"
    :with-header="true"
  >
    <!-- 左边缘拖拽手柄：按住左右拖动调节抽屉宽度 -->
    <div
      class="resize-handle"
      title="拖动调整宽度"
      @mousedown="startResize"
    ></div>

    <StartConfigDialog v-if="currentType === 'START'" ref="startRef" embedded />
    <LlmConfigDialog v-else-if="currentType === 'LLM'" ref="llmRef" embedded />
    <ToolConfigDialog
      v-else-if="currentType === 'TOOL'"
      ref="toolRef"
      embedded
    />
    <AgentConfigDialog
      v-else-if="currentType === 'AGENT'"
      ref="agentRef"
      embedded
    />
    <IfConfigDialog v-else-if="currentType === 'IF'" ref="ifRef" embedded />
    <JoinConfigDialog
      v-else-if="currentType === 'JOIN'"
      ref="joinRef"
      embedded
    />
    <EndConfigDialog v-else-if="currentType === 'END'" ref="endRef" embedded />
    <LoopConfigDialog
      v-else-if="currentType === 'LOOP'"
      ref="loopRef"
      embedded
    />
  </ElDrawer>
</template>

<style scoped>
:deep(.el-drawer__body) {
  position: relative;
  padding: 12px 16px 12px 20px;
  overflow: hidden;
}

/* 拖拽期间关掉抽屉宽度过渡，避免跟手卡顿 */
.node-config-drawer.is-resizing :deep(.el-drawer) {
  transition: none !important;
}

/* 左边缘拖拽手柄：贴 body 左侧，hover/拖拽时高亮 */
.resize-handle {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
  width: 6px;
  height: 100%;
  cursor: ew-resize;
  background: transparent;
  transition: background-color 0.15s;
}

.resize-handle::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 1px;
  width: 3px;
  height: 40px;
  background: var(--el-border-color);
  border-radius: 3px;
  transform: translateY(-50%);
  transition: background-color 0.15s;
}

.resize-handle:hover::after,
.is-resizing .resize-handle::after {
  background: var(--el-color-primary);
}
</style>
