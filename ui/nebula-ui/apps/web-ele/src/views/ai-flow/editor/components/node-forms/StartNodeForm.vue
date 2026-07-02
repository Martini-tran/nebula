<script lang="ts" setup>
/**
 * 开始节点表单：流程入参定义。
 *
 * START 是流程唯一入口，本表单只维护入参列表（node.data.nodeConfig.inputs）。
 * 与 PromptNodeForm/ToolNodeForm 同构，由 PropertyPanel 按类型分发；入参模型
 * 的读回/写回（nodeConfig.inputs ↔ 本模型）由 PropertyPanel 负责互转。
 */
import type { StartInputParam } from '../StartInputsEditor.vue';

import { ElAlert, ElFormItem } from 'element-plus';

import StartInputsEditor from '../StartInputsEditor.vue';

defineOptions({ name: 'StartNodeForm' });

const model = defineModel<StartInputParam[]>({ default: () => [] });
</script>

<template>
  <div>
    <section class="prop-section">
      <div class="prop-section-title">流程入参</div>
      <div class="prop-grid">
        <ElFormItem class="span-2" label-width="0">
          <div class="w-full">
            <StartInputsEditor v-model="model" />
            <ElAlert
              class="mt-2"
              :closable="false"
              title="入参在流程启动时注入上下文，可被下游节点通过标识（key）引用"
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
