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
    <!-- 工具 -->
    <section class="prop-section">
      <div class="prop-section-title">工具</div>
      <div class="prop-grid">
        <ElFormItem label="工具">
          <ToolSelector v-model="model.toolCode" placeholder="选择要调用的工具" />
        </ElFormItem>
        <ElFormItem label="输出键">
          <ElInput v-model="model.outputKey" placeholder="留空用节点编码" />
        </ElFormItem>

        <ElFormItem class="span-2" label="入参映射">
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
      </div>
    </section>
  </div>
</template>

<style scoped>
.prop-section {
  padding: 4px 0 2px;
}

.prop-section-title {
  margin: 6px 0 10px;
  padding-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-left: 3px solid var(--type-color, var(--el-color-primary));
}

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
</style>
