<script lang="ts" setup>
/**
 * 画布节点卡片（X6 vue-shape 渲染组件），对齐官方 AgentFlow 示例卡片。
 *
 * 结构：header（图标块 iconText + 标题 + 删除按钮）+ body（按类型分发的只读摘要）。
 * 按 data.nodeType 反查 AGENT_NODE_TYPES 取主题/图标/标题。编辑走右侧属性面板，
 * 卡片只读展示摘要。运行态 data.__run_state 驱动边框配色。start/end 不可删。
 */
import type { Node } from '@antv/x6';

import type { NodeMenuItem } from '../components/NodeContextMenu.vue';
import type { RunState } from '../constants';

import type { AiFlowApi } from '#/api';

import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue';

import NodeContextMenu from '../components/NodeContextMenu.vue';
import {
  agentMetaOf,
  NODE_HEIGHT,
  NODE_WIDTH,
  RUN_STATE_COLOR,
  THEME_COLORS,
} from '../constants';
import { normalizeStartInputs } from '../start-input';
import LlmNodeCard from './nodes/LlmNodeCard.vue';
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
/** PROMPT「文本大模型」节点（走通用卡片） */
const isLlm = computed(() => data.value.nodeType === 'PROMPT');
/** 独立 LLM 节点（专属卡片 + 右键配置弹窗，仿开始节点） */
const isLlmNode = computed(() => data.value.nodeType === 'LLM');
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

/**
 * 开始节点能力指示点：目前仅「入参」，按 nodeConfig.inputs 是否有定义亮/暗。
 * 入参在右键菜单「配置」弹窗（StartConfigDialog）里编辑。
 */
const startFeatures = computed(() => {
  const cfg = data.value.nodeConfig ?? {};
  return [
    {
      key: 'inputs',
      label: '入参',
      active: normalizeStartInputs(cfg.inputs).length > 0,
    },
  ];
});

/**
 * LLM 节点能力指示点：Model / Prompt / Output 是否已配置。
 * 读 nodeConfig.llm（配置弹窗 LlmConfigDialog 落库），任一关键字段有值即亮。
 */
const llmFeatures = computed(() => {
  const llm = (data.value.nodeConfig?.llm ?? {}) as Record<string, any>;
  const model = llm.model ?? {};
  const prompt = llm.prompt ?? {};
  const output = llm.output ?? {};
  return [
    { key: 'model', label: 'Model', active: Boolean(model.provider || model.model) },
    {
      key: 'prompt',
      label: 'Prompt',
      active: Boolean(prompt.systemPrompt || prompt.userPromptTemplate),
    },
    { key: 'output', label: 'Output', active: Boolean(output.type && output.type !== 'TEXT') },
  ];
});

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

/** 开始节点右键菜单项（仅「配置」，动作在编辑器主页面分发） */
const START_MENU_ITEMS: NodeMenuItem[] = [{ key: 'config', label: '配置' }];

/**
 * LLM 节点右键菜单项：按配置模块拆分，每项打开对应的独立配置弹窗
 * （key 即 LlmConfigDialog 的 section）。
 * - 基础配置：名称 + 上下文
 * - 模型配置：模型 + 调用参数 + 输出
 * - 提示词配置：System / User Prompt + 变量
 */
const LLM_MENU_ITEMS: NodeMenuItem[] = [
  { key: 'basic', label: '基础配置' },
  { key: 'model', label: '模型配置', divided: true },
  { key: 'prompt', label: '提示词配置' },
];

const startMenuRef = ref<InstanceType<typeof NodeContextMenu>>();

function onStartContextMenu(e: MouseEvent) {
  startMenuRef.value?.open(e.clientX, e.clientY);
}

/**
 * 菜单项点击：抛图级自定义事件 start:menu，交给编辑器主页面按节点类型 + key
 * 分发（开始节点 → StartConfigDialog，LLM 节点 → LlmConfigDialog 对应 section）。
 * 走 graph.trigger 而非 Vue emit——vue-shape 卡片渲染在 X6 独立树里，
 * emit 不会冒泡到编辑器组件；图事件是卡片与外层唯一可靠的桥。
 */
function onStartMenuSelect(key: string) {
  if (!node) return;
  const item = [...START_MENU_ITEMS, ...LLM_MENU_ITEMS].find(
    (it) => it.key === key,
  );
  node.model?.graph?.trigger('start:menu', {
    node,
    key,
    label: item?.label ?? key,
  });
}
</script>

<template>
  <!-- 开始节点：独立结构化摘要卡片，右键弹配置菜单 -->
  <div
    v-if="isStart"
    :style="{ width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` }"
    @contextmenu.prevent.stop="onStartContextMenu"
  >
    <StartNodeCard
      :title="title"
      :features="startFeatures"
      :run-border-color="runColor"
      :dimmed="dimmed"
    />
    <NodeContextMenu
      ref="startMenuRef"
      :items="START_MENU_ITEMS"
      @select="onStartMenuSelect"
    />
  </div>

  <!-- LLM 节点：独立结构化摘要卡片，右键弹配置菜单（仿开始节点） -->
  <div
    v-else-if="isLlmNode"
    :style="{ width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` }"
    @contextmenu.prevent.stop="onStartContextMenu"
  >
    <LlmNodeCard
      :title="title"
      :features="llmFeatures"
      :run-border-color="runColor"
      :dimmed="dimmed"
    />
    <NodeContextMenu
      ref="startMenuRef"
      :items="LLM_MENU_ITEMS"
      @select="onStartMenuSelect"
    />
  </div>

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
