<script lang="ts" setup>
/**
 * 画布节点卡片（X6 vue-shape 渲染组件），对齐官方 AgentFlow 示例卡片。
 *
 * 结构：header（图标块 iconText + 标题 + 删除按钮）+ body（按类型分发的只读摘要）。
 * 按 data.nodeType 反查 AGENT_NODE_TYPES 取主题/图标/标题。编辑走右侧属性面板，
 * 卡片只读展示摘要。运行态 data.__run_state 驱动边框配色。start/end 不可删。
 */
import type { Node } from '@antv/x6';

import type { RunState } from '../constants';

import type { AiFlowApi } from '#/api';

import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue';

import { nebulaContextMenu as NebulaContextMenu } from '@nebula/common-ui';

import { ElMessage } from 'element-plus';

import {
  agentMetaOf,
  NODE_HEIGHT,
  NODE_WIDTH,
  RUN_STATE_COLOR,
  THEME_COLORS,
} from '../constants';
import { normalizeStartInputs } from '../start-input';
import StartNodeCard from './nodes/StartNodeCard.vue';

defineOptions({ name: 'FlowNodeCard' });

type NodeData = AiFlowApi.FlowNodeRaw & { __run_state?: RunState };

const getNode = inject<() => Node>('getNode');

const data = ref<NodeData>({} as NodeData);
let node: Node | undefined;

function sync() {
  data.value = (node?.getData<NodeData>() ?? {}) as NodeData;
}

onMounted(() => {
  node = getNode?.();
  sync();
  node?.on('change:data', sync);
});

onBeforeUnmount(() => {
  node?.off('change:data', sync);
});

const meta = computed(() => agentMetaOf(data.value.nodeType));
const theme = computed(() => THEME_COLORS[meta.value.theme]);

const isStart = computed(() => data.value.nodeType === 'START');
const isLlm = computed(() => data.value.nodeType === 'PROMPT');
const isTool = computed(() => data.value.nodeType === 'TOOL');
const canDelete = computed(
  () => !['END', 'START'].includes(data.value.nodeType ?? ''),
);

/** 运行态边框覆盖主题色 */
const runColor = computed(() =>
  data.value.__run_state ? RUN_STATE_COLOR[data.value.__run_state] : '',
);
const borderColor = computed(() => runColor.value || theme.value.border);
const dimmed = computed(() => data.value.__run_state === 'skipped');

const title = computed(() => data.value.name || meta.value.title);

/** 开始节点入参摘要（node.data.nodeConfig.inputs，JSON 对象归一化后展示） */
const startInputs = computed(() =>
  normalizeStartInputs(data.value.nodeConfig?.inputs),
);

/** LLM 摘要：模型档案（或 provider/model），MCP×N */
const modelSummary = computed(() => {
  const d = data.value;
  if (d.profileCode) return d.profileCode;
  const pm = [d.provider, d.model].filter(Boolean).join('/');
  return pm || '未选模型档案';
});
const mcpCount = computed(() => {
  const codes = data.value.nodeConfig?.mcpServerCodes;
  return Array.isArray(codes) ? codes.length : 0;
});

/** 工具摘要 */
const toolSummary = computed(
  () => (data.value.nodeConfig?.toolCode as string) || '未选工具',
);

/** 输出键 · 模式 */
const outputSummary = computed(() => {
  const key = data.value.outputKey || data.value.nodeCode || '节点编码';
  const mode = data.value.outputMode || 'TEXT';
  return `${key} · ${mode}`;
});

function onDelete(e: MouseEvent) {
  e.stopPropagation();
  if (!canDelete.value) return;
  node?.remove();
}

/**
 * 开始节点右键菜单「配置」：抛一个图级自定义事件，交给编辑器主页面的独立弹窗
 * 处理。走 graph.trigger 而非 Vue emit——vue-shape 卡片渲染在 X6 独立树里，
 * emit 不会冒泡到编辑器组件；图事件是卡片与外层唯一可靠的桥。
 */
function onStartConfig() {
  if (!node) return;
  node.model?.graph?.trigger('start:config', { node });
}

/** 未落地的菜单项占位提示 */
function todoMenu(name: string) {
  ElMessage.info(`「${name}」功能开发中`);
}

/** 开始节点右键菜单（替代原卡片上的「配置」徽标入口） */
function startMenus() {
  return [
    { key: 'config', text: '配置', handler: onStartConfig },
    { key: 'memory', text: '全局记忆', handler: () => todoMenu('全局记忆') },
    { key: 'data', text: '接入数据', handler: () => todoMenu('接入数据') },
    { key: 'tool', text: '添加工具', handler: () => todoMenu('添加工具') },
    { key: 'mcp', text: 'MCP', handler: () => todoMenu('MCP') },
  ];
}
</script>

<template>
  <!-- 开始节点：独立结构化摘要卡片，右键弹配置菜单 -->
  <NebulaContextMenu v-if="isStart" :menus="startMenus">
    <div :style="{ width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` }">
      <StartNodeCard
        :title="title"
        :inputs="startInputs"
        :run-border-color="runColor"
        :dimmed="dimmed"
      />
    </div>
  </NebulaContextMenu>

  <!-- 其余类型：通用卡片 -->
  <div
    v-else
    class="agent-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor, width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` }"
  >
    <!-- header -->
    <div class="header">
      <div
        class="icon"
        :style="{ background: theme.iconBg, color: theme.iconColor }"
      >
        {{ meta.iconText }}
      </div>
      <div class="title" :title="title">{{ title }}</div>
      <div v-if="canDelete" class="actions">
        <span class="op" title="删除节点" @click="onDelete">✕</span>
      </div>
    </div>

    <!-- body：按类型分发摘要 -->
    <div class="body">
      <template v-if="isLlm">
        <span class="row-text" :title="modelSummary">{{ modelSummary }}</span>
        <span v-if="mcpCount > 0" class="badge">MCP×{{ mcpCount }}</span>
        <span class="muted out">{{ outputSummary }}</span>
      </template>
      <template v-else-if="isTool">
        <span class="row-text" :title="toolSummary">{{ toolSummary }}</span>
        <span class="muted out">{{ outputSummary }}</span>
      </template>
      <template v-else>
        <span class="row-text muted" :title="meta.desc">{{ meta.desc }}</span>
        <span v-if="!meta.runnable" class="badge badge-todo">未就绪</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.agent-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
  padding: 12px;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: #fff;
  border: 1.5px solid #5f95ff;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(0 0 0 / 8%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.agent-card:hover {
  box-shadow: 0 3px 12px rgb(0 0 0 / 12%);
}

.agent-card.is-dimmed {
  opacity: 0.5;
}

.header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 8px;
}

.title {
  flex: 1;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #141414;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  margin-left: auto;
  color: #bfbfbf;
}

.op {
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s;
}

.op:hover {
  color: #ff4d4f;
}

.body {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  font-size: 12px;
  color: #595959;
}

.row-text {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  color: #8c8c8c;
}

.out {
  flex-shrink: 0;
  margin-left: auto;
}

.badge {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  color: #08979c;
  background: #e6fffb;
  border-radius: 4px;
}

.badge-todo {
  color: #fa8c16;
  background: #fff7e6;
}
</style>
