<script lang="ts" setup>
/**
 * 画布节点卡片（X6 vue-shape 渲染组件）。
 *
 * 通过 inject('getNode') 拿到 X6 Node，读 node.getData() 渲染；监听 change:data 同步刷新。
 * 卡片按 nodeType 区分「模型调用（蓝）/工具调用（绿）」两种样式，展示所选模型/档案、
 * 关联 MCP 数量、输出键/模式等摘要。运行态（data.__run_state）驱动边框配色。
 */
import type { Node } from '@antv/x6';

import type { RunState } from '../constants';

import type { AiFlowApi } from '#/api';

import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue';

import { createIconifyIcon } from '@nebula/icons';

import { nodeMetaOf, RUN_STATE_COLOR } from '../constants';

defineOptions({ name: 'FlowNodeCard' });

type NodeData = { __run_state?: RunState } & AiFlowApi.FlowNodeRaw;

const ModelIcon = createIconifyIcon('lucide:sparkles');
const ToolIcon = createIconifyIcon('lucide:wrench');
const McpIcon = createIconifyIcon('lucide:plug');
const OutputIcon = createIconifyIcon('lucide:arrow-right-to-line');

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

const isTool = computed(() => data.value.nodeType === 'TOOL');

/** 类型主题色（无运行态时用），运行态覆盖边框色 */
const themeColor = computed(() => nodeMetaOf(data.value.nodeType).color);
const runColor = computed(() =>
  data.value.__run_state ? RUN_STATE_COLOR[data.value.__run_state] : '',
);
const borderColor = computed(() => runColor.value || themeColor.value);
const dimmed = computed(() => data.value.__run_state === 'skipped');

const title = computed(
  () => data.value.name || (isTool.value ? '工具调用' : '模型调用'),
);
const typeLabel = computed(() => (isTool.value ? '工具调用' : '模型调用'));

/** 模型摘要：优先档案码，回退 provider/model */
const modelSummary = computed(() => {
  const d = data.value;
  if (d.profileCode) return d.profileCode;
  const pm = [d.provider, d.model].filter(Boolean).join('/');
  return pm || '未选模型档案';
});

/** 关联 MCP 数量 */
const mcpCount = computed(() => {
  const codes = data.value.nodeConfig?.mcpServerCodes;
  return Array.isArray(codes) ? codes.length : 0;
});

/** 工具摘要：工具码 */
const toolSummary = computed(
  () => (data.value.nodeConfig?.toolCode as string) || '未选工具',
);

/** 输出键 + 模式 */
const outputSummary = computed(() => {
  const key = data.value.outputKey || data.value.nodeCode || '节点编码';
  const mode = data.value.outputMode || 'TEXT';
  return `${key} · ${mode}`;
});
</script>

<template>
  <div
    class="flow-node-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor, '--theme': themeColor }"
  >
    <span class="type-bar" :style="{ backgroundColor: themeColor }"></span>

    <!-- 顶部：类型图标 + 名称 + 类型标 -->
    <div class="card-head">
      <span class="type-icon" :style="{ color: themeColor }">
        <ToolIcon v-if="isTool" />
        <ModelIcon v-else />
      </span>
      <span class="card-title" :title="title">{{ title }}</span>
      <span class="type-tag" :style="{ color: themeColor, borderColor: themeColor }">
        {{ typeLabel }}
      </span>
    </div>

    <!-- 摘要区 -->
    <div class="card-body">
      <template v-if="isTool">
        <div class="row">
          <ToolIcon class="row-icon" />
          <span class="row-text" :title="toolSummary">{{ toolSummary }}</span>
        </div>
      </template>
      <template v-else>
        <div class="row">
          <ModelIcon class="row-icon" />
          <span class="row-text" :title="modelSummary">{{ modelSummary }}</span>
          <span v-if="mcpCount > 0" class="mcp-badge">
            <McpIcon class="mcp-icon" />MCP×{{ mcpCount }}
          </span>
        </div>
      </template>
      <div class="row row-muted">
        <OutputIcon class="row-icon" />
        <span class="row-text" :title="outputSummary">{{ outputSummary }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-node-card {
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 8px 10px 8px 14px;
  overflow: hidden;
  font-family:
    -apple-system, blinkmacsystemfont, 'Segoe UI', roboto, sans-serif;
  background: #fff;
  border: 1.5px solid #409eff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 10%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.flow-node-card.is-dimmed {
  opacity: 0.5;
}

.type-bar {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 4px;
  width: 4px;
  border-radius: 2px;
}

.card-head {
  display: flex;
  gap: 6px;
  align-items: center;
}

.type-icon {
  display: inline-flex;
  flex-shrink: 0;
  font-size: 16px;
}

.card-title {
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  font-size: 11px;
  line-height: 16px;
  border: 1px solid;
  border-radius: 4px;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.row {
  display: flex;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: #606266;
}

.row-muted {
  color: #909399;
}

.row-icon {
  flex-shrink: 0;
  font-size: 13px;
  opacity: 0.7;
}

.row-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mcp-badge {
  display: inline-flex;
  flex-shrink: 0;
  gap: 2px;
  align-items: center;
  padding: 0 5px;
  font-size: 11px;
  line-height: 16px;
  color: var(--theme);
  background: color-mix(in srgb, var(--theme) 12%, transparent);
  border-radius: 4px;
}

.mcp-icon {
  font-size: 11px;
}
</style>
