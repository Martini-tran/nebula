<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import {
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import McpServerSelector from '../selectors/McpServerSelector.vue';
import ProfileSelector from '../selectors/ProfileSelector.vue';

defineOptions({ name: 'PromptNodeForm' });

/** 表单模型：PROMPT 节点全部字段（直写 FlowNodeRaw 顶层） */
const model = defineModel<AiFlowApi.FlowNodeRaw>({ required: true });

/**
 * 关联的 MCP 服务编码列表。落库到 nodeConfig.mcpServerCodes（由 PropertyPanel 读写），
 * 与顶层字段解耦，故单独用一个 model 承载。
 * 注：后端运行时暂未消费该配置，当前仅落库/回显。
 */
const mcpServerCodes = defineModel<string[]>('mcpServerCodes', {
  default: () => [],
});
</script>

<template>
  <div>
    <ElFormItem label="系统提示">
      <ElInput
        v-model="model.systemPrompt"
        :rows="2"
        placeholder="system prompt"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="提示词模板">
      <ElInput
        v-model="model.promptTemplate"
        :rows="4"
        placeholder="支持 #{变量} 占位"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="模型档案">
      <ProfileSelector
        v-model="model.profileCode"
        placeholder="引用模型档案（留空用流程默认）"
      />
    </ElFormItem>
    <ElFormItem label="关联MCP">
      <McpServerSelector v-model="mcpServerCodes" />
    </ElFormItem>
    <ElFormItem label="provider">
      <ElInput v-model="model.provider" placeholder="覆盖档案，如 openai" />
    </ElFormItem>
    <ElFormItem label="model">
      <ElInput v-model="model.model" placeholder="覆盖档案，如 gpt-4o-mini" />
    </ElFormItem>
    <ElFormItem label="baseUrl">
      <ElInput v-model="model.baseUrl" placeholder="可选，覆盖档案" />
    </ElFormItem>
    <ElFormItem label="apiKey">
      <ElInput
        v-model="model.apiKey"
        placeholder="可选，覆盖档案"
        show-password
        type="password"
      />
    </ElFormItem>
    <ElFormItem label="temperature">
      <ElInputNumber
        v-model="model.temperature"
        :max="2"
        :min="0"
        :step="0.1"
        controls-position="right"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="maxTokens">
      <ElInputNumber
        v-model="model.maxTokens"
        :min="1"
        controls-position="right"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="topP">
      <ElInputNumber
        v-model="model.topP"
        :max="1"
        :min="0"
        :step="0.05"
        controls-position="right"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="输出键">
      <ElInput
        v-model="model.outputKey"
        placeholder="结果写入上下文的键名（留空用节点编码）"
      />
    </ElFormItem>
    <ElFormItem label="输出模式">
      <ElSelect v-model="model.outputMode" style="width: 100%">
        <ElOption label="TEXT（整段写入）" value="TEXT" />
        <ElOption label="JSON（解析后逐键展开）" value="JSON" />
      </ElSelect>
    </ElFormItem>
  </div>
</template>
