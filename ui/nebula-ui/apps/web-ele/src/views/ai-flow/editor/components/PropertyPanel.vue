<script lang="ts" setup>
/**
 * 通用节点属性弹窗：只服务 PROMPT 与其余占位类型（后端暂无执行器的节点）。
 *
 * 8 类核心节点（START/LLM/TOOL/AGENT/IF/JOIN/END/LOOP）与连线属性都已收进
 * 右侧分栏面板 NodeConfigPanel，不再经由本弹窗。
 */
import type { Node } from '@antv/x6';

import type { AiFlowApi } from '#/api';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { agentMetaOf, FLOW_DIALOG, THEME_COLORS } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import PromptNodeForm from './node-forms/PromptNodeForm.vue';

defineOptions({ name: 'PropertyPanel' });

const isPromptType = computed(() => nodeForm.nodeType === 'PROMPT');
/** 当前节点类型元信息（类型只读展示，不可修改） */
const currentTypeMeta = computed(() => agentMetaOf(nodeForm.nodeType));
const typeBorderColor = computed(
  () => THEME_COLORS[currentTypeMeta.value.theme].border,
);

const visible = ref(false);

let currentNode: Node | undefined;

/** 公共头 + PROMPT 全字段共用一个 FlowNodeRaw 模型 */
const nodeForm = reactive<AiFlowApi.FlowNodeRaw>({
  nodeCode: '',
  name: '',
  nodeType: 'PROMPT',
  outputMode: 'TEXT',
});

/** PROMPT 节点关联的 MCP 服务编码（↔ nodeConfig.mcpServerCodes，与顶层字段解耦） */
const promptMcpServerCodes = ref<string[]>([]);

/** 打开节点属性 */
function openNode(node: Node) {
  currentNode = node;
  const data =
    node.getData<AiFlowApi.FlowNodeRaw>() ?? ({} as AiFlowApi.FlowNodeRaw);

  // 填公共头 + PROMPT 字段
  Object.assign(nodeForm, {
    nodeCode: node.id,
    name: data.name ?? '',
    nodeType: data.nodeType ?? 'PROMPT',
    systemPrompt: data.systemPrompt ?? '',
    promptTemplate: data.promptTemplate ?? '',
    profileCode: data.profileCode ?? '',
    provider: data.provider ?? '',
    model: data.model ?? '',
    baseUrl: data.baseUrl ?? '',
    apiKey: data.apiKey ?? '',
    temperature: data.temperature ?? null,
    maxTokens: data.maxTokens ?? null,
    topP: data.topP ?? null,
    outputKey: data.outputKey ?? '',
    outputMode: data.outputMode ?? 'TEXT',
  });

  // 填 PROMPT 关联的 MCP 服务（从 nodeConfig.mcpServerCodes 读回）
  const mcpCodes = data.nodeConfig?.mcpServerCodes;
  promptMcpServerCodes.value = Array.isArray(mcpCodes) ? [...mcpCodes] : [];

  visible.value = true;
}

function close() {
  visible.value = false;
}

/** 应用：把表单写回选中节点的 data */
function apply() {
  if (!currentNode) {
    close();
    return;
  }
  const prev =
    currentNode.getData<AiFlowApi.FlowNodeRaw>() ??
    ({} as AiFlowApi.FlowNodeRaw);
  const type = nodeForm.nodeType || 'PROMPT';

  let next: AiFlowApi.FlowNodeRaw;
  if (type === 'PROMPT') {
    // PROMPT：写全字段，清 TOOL 专有的 nodeConfig.toolCode，写回关联 MCP
    const nodeConfig = { ...prev.nodeConfig };
    delete nodeConfig.toolCode;
    if (promptMcpServerCodes.value.length > 0) {
      nodeConfig.mcpServerCodes = [...promptMcpServerCodes.value];
    } else {
      delete nodeConfig.mcpServerCodes;
    }
    next = {
      ...prev,
      nodeCode: currentNode.id,
      name: nodeForm.name || undefined,
      nodeType: 'PROMPT',
      systemPrompt: nodeForm.systemPrompt || undefined,
      promptTemplate: nodeForm.promptTemplate || undefined,
      profileCode: nodeForm.profileCode || undefined,
      provider: nodeForm.provider || undefined,
      model: nodeForm.model || undefined,
      baseUrl: nodeForm.baseUrl || undefined,
      apiKey: nodeForm.apiKey || undefined,
      temperature: nodeForm.temperature ?? undefined,
      maxTokens: nodeForm.maxTokens ?? undefined,
      topP: nodeForm.topP ?? undefined,
      outputKey: nodeForm.outputKey || undefined,
      outputMode: nodeForm.outputMode || 'TEXT',
      nodeConfig,
    };
  } else {
    // 占位类型（CODE/BRANCH/KB/MCP/DB）：后端暂无执行器。
    // 只保留通用字段（name/输出键）与已有 nodeConfig，清两类专有脏字段。
    const placeholderConfig = { ...prev.nodeConfig };
    delete placeholderConfig.toolCode;
    delete placeholderConfig.mcpServerCodes;
    next = {
      ...prev,
      nodeCode: currentNode.id,
      name: nodeForm.name || undefined,
      nodeType: type,
      outputKey: nodeForm.outputKey || undefined,
      outputMode: nodeForm.outputMode || 'TEXT',
      nodeConfig: placeholderConfig,
    };
  }

  currentNode.setData(next, { overwrite: true });
  refreshNodeCard(currentNode);
  close();
}

defineExpose({ close, openNode });
</script>

<template>
  <ElDialog
    v-model="visible"
    append-to-body
    :class="FLOW_DIALOG.class"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    title="节点属性"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <!-- 基础信息 + 按类型分发 -->
    <ElForm label-width="84px" class="prop-form">
      <section class="prop-section">
        <div class="prop-section-title">基础信息</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput v-model="nodeForm.name" placeholder="节点展示名" />
          </ElFormItem>
          <ElFormItem label="类型">
            <ElInput :model-value="currentTypeMeta.title" disabled />
          </ElFormItem>
        </div>
      </section>

      <div :style="{ '--type-color': typeBorderColor }">
        <PromptNodeForm
          v-if="isPromptType"
          v-model="nodeForm"
          v-model:mcp-server-codes="promptMcpServerCodes"
        />
        <!-- 占位类型：仅通用输出配置（START/LLM/TOOL 不进本面板，走独立配置弹窗） -->
        <section v-else class="prop-section">
          <div class="prop-section-title">输出</div>
          <div class="prop-grid">
            <ElFormItem label="输出键">
              <ElInput
                v-model="nodeForm.outputKey"
                placeholder="留空用节点编码"
              />
            </ElFormItem>
            <ElFormItem label="输出模式">
              <ElSelect v-model="nodeForm.outputMode" style="width: 100%">
                <ElOption label="TEXT（整段写入）" value="TEXT" />
                <ElOption label="JSON（解析后逐键展开）" value="JSON" />
              </ElSelect>
            </ElFormItem>
          </div>
        </section>
      </div>
    </ElForm>

    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" @click="apply">应用</ElButton>
    </template>
  </ElDialog>
</template>

<style>
/* 弹框整体收敛内边距，避免属性过于拥挤 */
.flow-prop-dialog .el-dialog__body {
  max-height: 78vh;
  padding: 8px 24px 4px;
  overflow-y: auto;
}
</style>

<style scoped>
.prop-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 两列网格：每行两个字段；.span-2 的项占满整行 */
.prop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.prop-grid :deep(.el-form-item) {
  margin-bottom: 12px;
  min-width: 0;
}

.prop-grid :deep(.span-2) {
  grid-column: 1 / -1;
}

/* 属性分类小节 */
.prop-section {
  padding: 4px 0 2px;
}

.prop-section + .prop-section {
  margin-top: 4px;
}

.prop-section-title {
  margin: 6px 0 10px;
  padding-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-left: 3px solid var(--type-color, var(--el-color-primary));
}
</style>
