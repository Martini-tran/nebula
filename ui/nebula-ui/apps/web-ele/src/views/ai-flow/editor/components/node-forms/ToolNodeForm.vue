<script lang="ts" setup>
import { ElAlert, ElFormItem, ElInput } from 'element-plus';

import InputMappingEditor from '../InputMappingEditor.vue';
import ToolSelector from '../selectors/ToolSelector.vue';

defineOptions({ name: 'ToolNodeForm' });

/** TOOL 节点 UI 表单模型（toolCode 落到 nodeConfig.toolCode，见 PropertyPanel） */
export interface ToolNodeModel {
  toolCode: string;
  inputMapping: Record<string, string>;
  outputKey: string;
}

const model = defineModel<ToolNodeModel>({ required: true });
</script>

<template>
  <div>
    <ElFormItem label="工具">
      <ToolSelector v-model="model.toolCode" placeholder="选择要调用的工具" />
    </ElFormItem>

    <ElFormItem label="入参映射">
      <div class="w-full">
        <InputMappingEditor
          v-model="model.inputMapping"
          key-placeholder="工具入参名"
          value-placeholder="上下文键"
        />
        <ElAlert
          class="mt-2"
          :closable="false"
          title="留空则将全部上下文作为入参传入工具"
          type="info"
        />
      </div>
    </ElFormItem>

    <ElFormItem label="输出键">
      <ElInput
        v-model="model.outputKey"
        placeholder="工具结果写入上下文的键名（留空用节点编码）"
      />
    </ElFormItem>
  </div>
</template>
