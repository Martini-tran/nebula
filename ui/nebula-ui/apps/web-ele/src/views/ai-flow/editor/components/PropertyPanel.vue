<script lang="ts" setup>
import type { Edge, Node } from '@antv/x6';

import type { StartInputParam } from './StartInputsEditor.vue';
import type { ToolNodeModel } from './node-forms/ToolNodeForm.vue';

import type { AiFlowApi } from '#/api';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { agentMetaOf, THEME_COLORS } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import EdgePropertyPanel from './EdgePropertyPanel.vue';
import PromptNodeForm from './node-forms/PromptNodeForm.vue';
import StartNodeForm from './node-forms/StartNodeForm.vue';
import ToolNodeForm from './node-forms/ToolNodeForm.vue';

defineOptions({ name: 'PropertyPanel' });

type SelectionKind = 'edge' | 'node' | null;

const isPromptType = computed(() => nodeForm.nodeType === 'PROMPT');
const isToolType = computed(() => nodeForm.nodeType === 'TOOL');
const isStartType = computed(() => nodeForm.nodeType === 'START');
/** 当前节点类型元信息（类型只读展示，不可修改） */
const currentTypeMeta = computed(() => agentMetaOf(nodeForm.nodeType));
const typeBorderColor = computed(
  () => THEME_COLORS[currentTypeMeta.value.theme].border,
);

const visible = ref(false);
const selectionKind = ref<SelectionKind>(null);

let currentNode: Node | undefined;
let currentEdge: Edge | undefined;

/** 公共头 + PROMPT 全字段共用一个 FlowNodeRaw 模型 */
const nodeForm = reactive<AiFlowApi.FlowNodeRaw>({
  nodeCode: '',
  name: '',
  nodeType: 'PROMPT',
  outputMode: 'TEXT',
});

/** TOOL 专用 UI 模型（toolCode ↔ nodeConfig.toolCode 由本组件互转） */
const toolForm = reactive<ToolNodeModel>({
  toolCode: '',
  inputMapping: {},
  outputKey: '',
});

/** PROMPT 节点关联的 MCP 服务编码（↔ nodeConfig.mcpServerCodes，与顶层字段解耦） */
const promptMcpServerCodes = ref<string[]>([]);

/** START 节点入参列表（↔ nodeConfig.inputs） */
const startInputs = ref<StartInputParam[]>([]);

const edgeForm = reactive<{ conditionExpr: string }>({ conditionExpr: '' });

/** 打开节点属性 */
function openNode(node: Node) {
  currentNode = node;
  currentEdge = undefined;
  selectionKind.value = 'node';
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

  // 填 TOOL 字段（从 nodeConfig.toolCode 读回）
  toolForm.toolCode = (data.nodeConfig?.toolCode as string) ?? '';
  toolForm.inputMapping = { ...data.inputMapping };
  toolForm.outputKey = data.outputKey ?? '';

  // 填 PROMPT 关联的 MCP 服务（从 nodeConfig.mcpServerCodes 读回）
  const mcpCodes = data.nodeConfig?.mcpServerCodes;
  promptMcpServerCodes.value = Array.isArray(mcpCodes) ? [...mcpCodes] : [];

  // 填 START 入参（从 nodeConfig.inputs 读回，深拷贝避免直接改动 node.data）
  const inputs = data.nodeConfig?.inputs as StartInputParam[] | undefined;
  startInputs.value = Array.isArray(inputs)
    ? inputs.map((it) => ({ ...it, validation: { ...it.validation } }))
    : [];

  visible.value = true;
}

/** 打开边属性 */
function openEdge(edge: Edge) {
  currentEdge = edge;
  currentNode = undefined;
  selectionKind.value = 'edge';
  const data = edge.getData<{ conditionExpr?: string }>() ?? {};
  edgeForm.conditionExpr = data.conditionExpr ?? '';
  visible.value = true;
}

function close() {
  visible.value = false;
}

/** 应用：把表单写回选中 cell 的 data */
function apply() {
  if (selectionKind.value === 'node' && currentNode) {
    const prev =
      currentNode.getData<AiFlowApi.FlowNodeRaw>() ??
      ({} as AiFlowApi.FlowNodeRaw);
    const type = nodeForm.nodeType || 'PROMPT';

    let next: AiFlowApi.FlowNodeRaw;
    if (type === 'TOOL') {
      // TOOL：清 PROMPT 专有脏字段（含关联 MCP），toolCode 落到 nodeConfig.toolCode
      const toolNodeConfig = { ...prev.nodeConfig };
      delete toolNodeConfig.mcpServerCodes;
      next = {
        nodeCode: currentNode.id,
        name: nodeForm.name || undefined,
        nodeType: 'TOOL',
        inputMapping: toolForm.inputMapping,
        outputKey: toolForm.outputKey || undefined,
        nodeConfig: {
          ...toolNodeConfig,
          toolCode: toolForm.toolCode || undefined,
        },
        // 保留坐标等已有 nodeConfig，__x6 在 nodeConfig 里已被展开保留
      };
    } else if (type === 'PROMPT') {
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
        nodeConfig: nodeConfig,
      };
    } else if (type === 'START') {
      // START：唯一入口，只维护入参列表（落到 nodeConfig.inputs）。
      // 过滤掉名称与标识都为空的行，避免落库脏数据；清两类专有脏字段。
      const startConfig = { ...prev.nodeConfig };
      delete startConfig.toolCode;
      delete startConfig.mcpServerCodes;
      const inputs = startInputs.value.filter((it) => it.name || it.key);
      if (inputs.length > 0) startConfig.inputs = inputs;
      else delete startConfig.inputs;
      next = {
        ...prev,
        nodeCode: currentNode.id,
        name: nodeForm.name || undefined,
        nodeType: 'START',
        nodeConfig: startConfig,
      };
    } else {
      // 占位类型（END/CODE/BRANCH/LOOP/KB/MCP/DB）：后端暂无执行器。
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
  } else if (selectionKind.value === 'edge' && currentEdge) {
    const expr = edgeForm.conditionExpr || '';
    currentEdge.setData({ conditionExpr: expr }, { overwrite: true });
    currentEdge.setLabels(
      expr ? [{ attrs: { label: { text: expr } } }] : [],
    );
  }
  close();
}

/** 切换类型时的提示（脏字段在 apply 时按类型清理，这里无需即时清） */
watch(
  () => nodeForm.nodeType,
  () => {
    // 类型切换后，若切到 TOOL 且 tool 模型为空，outputKey 同步一下公共值
    if (nodeForm.nodeType === 'TOOL' && !toolForm.outputKey) {
      toolForm.outputKey = nodeForm.outputKey ?? '';
    }
  },
);

defineExpose({ openEdge, openNode, close });
</script>

<template>
  <ElDialog
    v-model="visible"
    append-to-body
    class="flow-prop-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :title="selectionKind === 'node' ? '节点属性' : '连线属性'"
    top="5vh"
    width="820px"
  >
    <!-- 节点：基础信息 + 按类型分发 -->
    <ElForm v-if="selectionKind === 'node'" label-width="84px" class="prop-form">
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
        <ToolNodeForm v-else-if="isToolType" v-model="toolForm" />
        <StartNodeForm v-else-if="isStartType" v-model="startInputs" />
        <!-- 占位类型：仅通用输出配置 -->
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

    <!-- 边：条件表达式 -->
    <ElForm v-else-if="selectionKind === 'edge'" label-width="84px" class="prop-form">
      <EdgePropertyPanel v-model="edgeForm" />
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
