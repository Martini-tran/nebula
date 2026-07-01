<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import {
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import ProfileSelector from '../selectors/ProfileSelector.vue';

defineOptions({ name: 'PromptNodeForm' });

/** 表单模型：PROMPT 节点全部字段（直写 FlowNodeRaw 顶层） */
const model = defineModel<AiFlowApi.FlowNodeRaw>({ required: true });
</script>

<template>
  <div>
    <ElFormItem label="系统提示">
      <ElInput
        v-model="model.system_prompt"
        :rows="2"
        placeholder="system prompt"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="提示词模板">
      <ElInput
        v-model="model.prompt_template"
        :rows="4"
        placeholder="支持 #{变量} 占位"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="模型档案">
      <ProfileSelector
        v-model="model.profile_code"
        placeholder="引用模型档案（留空用流程默认）"
      />
    </ElFormItem>
    <ElFormItem label="provider">
      <ElInput v-model="model.provider" placeholder="覆盖档案，如 openai" />
    </ElFormItem>
    <ElFormItem label="model">
      <ElInput v-model="model.model" placeholder="覆盖档案，如 gpt-4o-mini" />
    </ElFormItem>
    <ElFormItem label="base_url">
      <ElInput v-model="model.base_url" placeholder="可选，覆盖档案" />
    </ElFormItem>
    <ElFormItem label="api_key">
      <ElInput
        v-model="model.api_key"
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
    <ElFormItem label="max_tokens">
      <ElInputNumber
        v-model="model.max_tokens"
        :min="1"
        controls-position="right"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="top_p">
      <ElInputNumber
        v-model="model.top_p"
        :max="1"
        :min="0"
        :step="0.05"
        controls-position="right"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="输出键">
      <ElInput
        v-model="model.output_key"
        placeholder="结果写入上下文的键名（留空用节点编码）"
      />
    </ElFormItem>
    <ElFormItem label="输出模式">
      <ElSelect v-model="model.output_mode" style="width: 100%">
        <ElOption label="TEXT（整段写入）" value="TEXT" />
        <ElOption label="JSON（解析后逐键展开）" value="JSON" />
      </ElSelect>
    </ElFormItem>
  </div>
</template>
