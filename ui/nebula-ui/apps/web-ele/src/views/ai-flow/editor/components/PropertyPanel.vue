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
  node_code: '',
  name: '',
  node_type: 'PROMPT',
  output_mode: 'TEXT',
});

/** TOOL 专用 UI 模型（tool_code ↔ node_config.toolCode 由本组件互转） */
const toolForm = reactive<ToolNodeModel>({
  tool_code: '',
  input_mapping: {},
  output_key: '',
});

const edgeForm = reactive<{ condition_expr: string }>({ condition_expr: '' });

/** 打开节点属性 */
function openNode(node: Node) {
  currentNode = node;
  currentEdge = undefined;
  selectionKind.value = 'node';
  const data =
    node.getData<AiFlowApi.FlowNodeRaw>() ?? ({} as AiFlowApi.FlowNodeRaw);

  // 填公共头 + PROMPT 字段
  Object.assign(nodeForm, {
    node_code: node.id,
    name: data.name ?? '',
    node_type: data.node_type ?? 'PROMPT',
    system_prompt: data.system_prompt ?? '',
    prompt_template: data.prompt_template ?? '',
    profile_code: data.profile_code ?? '',
    provider: data.provider ?? '',
    model: data.model ?? '',
    base_url: data.base_url ?? '',
    api_key: data.api_key ?? '',
    temperature: data.temperature ?? null,
    max_tokens: data.max_tokens ?? null,
    top_p: data.top_p ?? null,
    output_key: data.output_key ?? '',
    output_mode: data.output_mode ?? 'TEXT',
  });

  // 填 TOOL 字段（从 node_config.toolCode 读回）
  toolForm.tool_code = (data.node_config?.toolCode as string) ?? '';
  toolForm.input_mapping = { ...data.input_mapping };
  toolForm.output_key = data.output_key ?? '';

  visible.value = true;
}

/** 打开边属性 */
function openEdge(edge: Edge) {
  currentEdge = edge;
  currentNode = undefined;
  selectionKind.value = 'edge';
  const data = edge.getData<{ condition_expr?: string }>() ?? {};
  edgeForm.condition_expr = data.condition_expr ?? '';
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
    const type = nodeForm.node_type || 'PROMPT';

    let next: AiFlowApi.FlowNodeRaw;
    if (type === 'TOOL') {
      // TOOL：清 PROMPT 专有脏字段，tool_code 落到 node_config.toolCode
      next = {
        node_code: currentNode.id,
        name: nodeForm.name || undefined,
        node_type: 'TOOL',
        input_mapping: toolForm.input_mapping,
        output_key: toolForm.output_key || undefined,
        node_config: {
          ...prev.node_config,
          toolCode: toolForm.tool_code || undefined,
        },
        // 保留坐标等已有 node_config，__x6 在 node_config 里已被展开保留
      };
    } else {
      // PROMPT：写全字段，清 TOOL 专有的 node_config.toolCode
      const nodeConfig = { ...prev.node_config };
      delete nodeConfig.toolCode;
      next = {
        ...prev,
        node_code: currentNode.id,
        name: nodeForm.name || undefined,
        node_type: 'PROMPT',
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
        node_config: nodeConfig,
      };
    }

    currentNode.setData(next, { overwrite: true });
    refreshNodeCard(currentNode);
  } else if (selectionKind.value === 'edge' && currentEdge) {
    const expr = edgeForm.condition_expr || '';
    currentEdge.setData({ condition_expr: expr }, { overwrite: true });
    currentEdge.setLabels(
      expr ? [{ attrs: { label: { text: expr } } }] : [],
    );
  }
  close();
}

/** 切换类型时的提示（脏字段在 apply 时按类型清理，这里无需即时清） */
watch(
  () => nodeForm.node_type,
  () => {
    // 类型切换后，若切到 TOOL 且 tool 模型为空，output_key 同步一下公共值
    if (nodeForm.node_type === 'TOOL' && !toolForm.output_key) {
      toolForm.output_key = nodeForm.output_key ?? '';
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

      <div
        class="mb-3 border-l-2 pl-3"
        :style="{ borderColor: nodeMetaOf(nodeForm.node_type).color }"
      >
        <PromptNodeForm
          v-if="nodeForm.node_type !== 'TOOL'"
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
