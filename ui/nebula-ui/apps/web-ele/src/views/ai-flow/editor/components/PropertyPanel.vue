<script lang="ts" setup>
import type { Edge, Node } from '@antv/x6';

import type { ToolNodeModel } from './node-forms/ToolNodeForm.vue';

import type { AiFlowApi } from '#/api';

import { reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { nodeMetaOf } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import EdgePropertyPanel from './EdgePropertyPanel.vue';
import PromptNodeForm from './node-forms/PromptNodeForm.vue';
import ToolNodeForm from './node-forms/ToolNodeForm.vue';

defineOptions({ name: 'PropertyPanel' });

defineProps<Props>();

type SelectionKind = 'edge' | 'node' | null;

interface Props {
  nodeTypes: AiFlowApi.NodeTypeMeta[];
}

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
      // TOOL：清 PROMPT 专有脏字段，toolCode 落到 nodeConfig.toolCode
      next = {
        nodeCode: currentNode.id,
        name: nodeForm.name || undefined,
        nodeType: 'TOOL',
        inputMapping: toolForm.inputMapping,
        outputKey: toolForm.outputKey || undefined,
        nodeConfig: {
          ...prev.nodeConfig,
          toolCode: toolForm.toolCode || undefined,
        },
        // 保留坐标等已有 nodeConfig，__x6 在 nodeConfig 里已被展开保留
      };
    } else {
      // PROMPT：写全字段，清 TOOL 专有的 nodeConfig.toolCode
      const nodeConfig = { ...prev.nodeConfig };
      delete nodeConfig.toolCode;
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
  <ElDrawer
    v-model="visible"
    :size="440"
    :title="selectionKind === 'node' ? '节点属性' : '连线属性'"
    direction="rtl"
  >
    <!-- 节点：公共头 + 按类型分发 -->
    <ElForm v-if="selectionKind === 'node'" label-width="92px">
      <ElFormItem label="节点编码">
        <ElInput v-model="nodeForm.nodeCode" disabled />
      </ElFormItem>
      <ElFormItem label="名称">
        <ElInput v-model="nodeForm.name" placeholder="节点展示名" />
      </ElFormItem>
      <ElFormItem label="类型">
        <ElSelect v-model="nodeForm.nodeType" style="width: 100%">
          <ElOption
            v-for="nt in nodeTypes"
            :key="nt.type"
            :label="nt.name"
            :value="nt.type"
          />
        </ElSelect>
      </ElFormItem>

      <div
        class="mb-3 border-l-2 pl-3"
        :style="{ borderColor: nodeMetaOf(nodeForm.nodeType).color }"
      >
        <PromptNodeForm
          v-if="nodeForm.nodeType !== 'TOOL'"
          v-model="nodeForm"
        />
        <ToolNodeForm v-else v-model="toolForm" />
      </div>
    </ElForm>

    <!-- 边：条件表达式 -->
    <ElForm v-else-if="selectionKind === 'edge'" label-width="92px">
      <EdgePropertyPanel v-model="edgeForm" />
    </ElForm>

    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" @click="apply">应用</ElButton>
    </template>
  </ElDrawer>
</template>
